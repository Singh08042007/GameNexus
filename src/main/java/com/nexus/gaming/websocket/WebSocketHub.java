package com.nexus.gaming.websocket;

import com.nexus.gaming.model.User;
import com.nexus.gaming.repository.DataRepository;
import com.nexus.gaming.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * RFC 6455 Real-Time WebSocket Server and Room Broadcast Hub.
 * Manages WebSocket upgrades, room subscriptions, real-time move/state broadcasts,
 * and player disconnect notifications.
 */
public class WebSocketHub {

    private static final String WS_MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final DataRepository repository;
    private final Map<String, Set<WebSocketConnection>> roomSubscribers = new ConcurrentHashMap<>();
    private final Set<WebSocketConnection> allConnections = new CopyOnWriteArraySet<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "nexus-ws-worker");
        t.setDaemon(true);
        return t;
    });
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private Consumer<WebSocketConnection> disconnectListener;
    private BiConsumer<WebSocketConnection, Map<String, Object>> messageListener;

    public WebSocketHub(int port, DataRepository repository) {
        this.port = port;
        this.repository = repository;
    }

    public void setDisconnectListener(Consumer<WebSocketConnection> disconnectListener) {
        this.disconnectListener = disconnectListener;
    }

    public void setMessageListener(BiConsumer<WebSocketConnection, Map<String, Object>> messageListener) {
        this.messageListener = messageListener;
    }

    public void start() throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.running = true;
        executor.submit(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executor.submit(() -> handleClientSocket(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[WebSocketHub] Accept error: " + e.getMessage());
                    }
                }
            }
        });
    }

    public void stop() {
        this.running = false;
        for (WebSocketConnection conn : allConnections) {
            conn.close();
        }
        allConnections.clear();
        roomSubscribers.clear();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void broadcastToRoom(String roomCode, String eventType, Object payload) {
        if (roomCode == null) return;
        String normalizedCode = roomCode.trim().toUpperCase();
        Set<WebSocketConnection> subscribers = roomSubscribers.get(normalizedCode);
        if (subscribers == null || subscribers.isEmpty()) return;

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event", eventType);
        envelope.put("roomCode", normalizedCode);
        envelope.put("data", payload);
        envelope.put("timestamp", Instant.now().toString());
        String json = JsonUtil.toJson(envelope);

        for (WebSocketConnection conn : subscribers) {
            if (conn.isOpen()) {
                conn.sendText(json);
            }
        }
    }

    public void broadcastLobbyUpdate(String eventType, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event", eventType);
        envelope.put("data", payload);
        envelope.put("timestamp", Instant.now().toString());
        String json = JsonUtil.toJson(envelope);

        for (WebSocketConnection conn : allConnections) {
            if (conn.isOpen()) {
                conn.sendText(json);
            }
        }
    }

    public void subscribeConnectionToRoom(WebSocketConnection conn, String newRoomCode) {
        if (conn.getRoomCode() != null) {
            Set<WebSocketConnection> oldSet = roomSubscribers.get(conn.getRoomCode());
            if (oldSet != null) {
                oldSet.remove(conn);
            }
        }
        if (newRoomCode != null && !newRoomCode.trim().isEmpty()) {
            String normalized = newRoomCode.trim().toUpperCase();
            conn.setRoomCode(normalized);
            roomSubscribers.computeIfAbsent(normalized, k -> new CopyOnWriteArraySet<>()).add(conn);
        } else {
            conn.setRoomCode(null);
        }
    }

    public boolean hasActiveRoomConnection(String roomCode, String userId) {
        if (roomCode == null || userId == null) return false;
        Set<WebSocketConnection> subs = roomSubscribers.get(roomCode.toUpperCase());
        if (subs == null) return false;
        for (WebSocketConnection conn : subs) {
            if (conn.isOpen() && userId.equals(conn.getUserId())) {
                return true;
            }
        }
        return false;
    }

    private void handleClientSocket(Socket socket) {
        WebSocketConnection wsConn = null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || !requestLine.startsWith("GET ")) {
                socket.close();
                return;
            }

            String[] parts = requestLine.split(" ");
            String pathAndQuery = parts.length > 1 ? parts[1] : "/";
            Map<String, String> queryParams = parseQueryParams(pathAndQuery);

            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    headers.put(headerLine.substring(0, colon).trim().toLowerCase(),
                                headerLine.substring(colon + 1).trim());
                }
            }

            String wsKey = headers.get("sec-websocket-key");
            if (wsKey == null) {
                socket.close();
                return;
            }

            // Compute RFC 6455 Sec-WebSocket-Accept
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] acceptDigest = sha1.digest((wsKey + WS_MAGIC_GUID).getBytes(StandardCharsets.UTF_8));
            String acceptKey = Base64.getEncoder().encodeToString(acceptDigest);

            OutputStream out = socket.getOutputStream();
            String handshakeResponse = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            out.write(handshakeResponse.getBytes(StandardCharsets.UTF_8));
            out.flush();

            wsConn = new WebSocketConnection(socket);
            String token = queryParams.get("token");
            if (token != null) {
                Optional<User> userOpt = repository.findUserByToken(token);
                if (userOpt.isPresent()) {
                    wsConn.setUserId(userOpt.get().getId());
                    wsConn.setUsername(userOpt.get().getUsername());
                }
            }

            allConnections.add(wsConn);

            String initialRoom = queryParams.get("roomCode");
            if (initialRoom != null && !initialRoom.trim().isEmpty()) {
                subscribeConnectionToRoom(wsConn, initialRoom);
            }

            // Send initial CONNECTED acknowledgement
            Map<String, Object> welcome = new LinkedHashMap<>();
            welcome.put("event", "CONNECTED");
            welcome.put("userId", wsConn.getUserId());
            welcome.put("username", wsConn.getUsername());
            welcome.put("roomCode", wsConn.getRoomCode());
            welcome.put("timestamp", Instant.now().toString());
            wsConn.sendText(JsonUtil.toJson(welcome));

            String textFrame;
            while ((textFrame = wsConn.readNextTextFrame()) != null) {
                Map<String, Object> msg = JsonUtil.parseObject(textFrame);
                String action = JsonUtil.getString(msg, "action");
                if ("SUBSCRIBE_ROOM".equalsIgnoreCase(action)) {
                    String roomCode = JsonUtil.getString(msg, "roomCode");
                    subscribeConnectionToRoom(wsConn, roomCode);
                    if (messageListener != null) {
                        messageListener.accept(wsConn, msg);
                    }
                } else if ("LEAVE_ROOM_CHANNEL".equalsIgnoreCase(action)) {
                    subscribeConnectionToRoom(wsConn, null);
                } else if ("PING".equalsIgnoreCase(action)) {
                    Map<String, Object> pong = new LinkedHashMap<>();
                    pong.put("event", "PONG");
                    pong.put("timestamp", Instant.now().toString());
                    wsConn.sendText(JsonUtil.toJson(pong));
                } else if (messageListener != null) {
                    messageListener.accept(wsConn, msg);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (wsConn != null) {
                allConnections.remove(wsConn);
                String roomCode = wsConn.getRoomCode();
                if (roomCode != null) {
                    Set<WebSocketConnection> subs = roomSubscribers.get(roomCode);
                    if (subs != null) {
                        subs.remove(wsConn);
                    }
                }
                wsConn.close();
                if (disconnectListener != null) {
                    disconnectListener.accept(wsConn);
                }
            } else {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private Map<String, String> parseQueryParams(String pathAndQuery) {
        Map<String, String> params = new HashMap<>();
        int qIdx = pathAndQuery.indexOf('?');
        if (qIdx < 0 || qIdx == pathAndQuery.length() - 1) {
            return params;
        }
        String query = pathAndQuery.substring(qIdx + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try {
                    String k = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                    String v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    params.put(k, v);
                } catch (UnsupportedEncodingException ignored) {
                }
            }
        }
        return params;
    }
}

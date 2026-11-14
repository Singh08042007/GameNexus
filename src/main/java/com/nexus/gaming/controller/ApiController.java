package com.nexus.gaming.controller;

import com.nexus.gaming.config.AppConfig;
import com.nexus.gaming.dto.DtoMapper;
import com.nexus.gaming.exception.NexusException;
import com.nexus.gaming.model.Room;
import com.nexus.gaming.model.User;
import com.nexus.gaming.repository.SupabaseClient;
import com.nexus.gaming.service.AuthService;
import com.nexus.gaming.service.GameService;
import com.nexus.gaming.service.RoomService;
import com.nexus.gaming.util.JsonUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pure Java Socket-based HTTP/1.1 Controller & Static Asset Server.
 * Handles REST API endpoints, CORS preflight requests, token authentication,
 * centralized exception handling, and static frontend delivery.
 */
public class ApiController {

    private final AppConfig config;
    private final AuthService authService;
    private final RoomService roomService;
    private final GameService gameService;
    private final SupabaseClient supabaseClient;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "nexus-http-worker");
        t.setDaemon(true);
        return t;
    });
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public ApiController(AppConfig config,
                         AuthService authService,
                         RoomService roomService,
                         GameService gameService,
                         SupabaseClient supabaseClient) {
        this.config = config;
        this.authService = authService;
        this.roomService = roomService;
        this.gameService = gameService;
        this.supabaseClient = supabaseClient;
    }

    public void start() throws IOException {
        this.serverSocket = new ServerSocket(config.getHttpPort());
        this.running = true;
        executor.submit(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    executor.submit(() -> handleSocketConnection(client));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("[ApiController] Accept error: " + e.getMessage());
                    }
                }
            }
        });
    }

    public void stop() {
        this.running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleSocketConnection(Socket socket) {
        try {
            socket.setSoTimeout(10000);
            InputStream rawIn = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            String requestLine = readLine(rawIn);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return;
            }
            String method = parts[0].trim().toUpperCase();
            String rawTarget = parts[1].trim();
            String path;
            try {
                path = URI.create(rawTarget).getPath();
                if (path == null || path.isEmpty()) path = "/";
            } catch (Exception e) {
                int q = rawTarget.indexOf('?');
                path = (q >= 0) ? rawTarget.substring(0, q) : rawTarget;
            }

            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = readLine(rawIn)) != null && !headerLine.isEmpty()) {
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    headers.put(
                            headerLine.substring(0, colon).trim().toLowerCase(),
                            headerLine.substring(colon + 1).trim()
                    );
                }
            }

            int contentLength = 0;
            if (headers.containsKey("content-length")) {
                try {
                    contentLength = Integer.parseInt(headers.get("content-length"));
                } catch (NumberFormatException ignored) {
                }
            }

            String body = "";
            if (contentLength > 0 && contentLength <= 1048576) {
                byte[] bodyBytes = readFixedBytes(rawIn, contentLength);
                body = new String(bodyBytes, StandardCharsets.UTF_8);
            }

            if ("OPTIONS".equals(method)) {
                writeHttpResponse(out, 204, "text/plain; charset=utf-8", new byte[0]);
                return;
            }

            HttpRequestContext ctx = new HttpRequestContext(method, path, headers, body);
            try {
                if (path.startsWith("/api/")) {
                    handleApiRequest(ctx, out);
                } else {
                    serveStaticFile(path, out);
                }
            } catch (NexusException ne) {
                sendJson(out, ne.getHttpStatus(), DtoMapper.errorResponse(ne.getMessage(), ne.getErrorCode()));
            } catch (Exception e) {
                System.err.println("[ApiController] Error on " + method + " " + path + ": " + e.getMessage());
                sendJson(out, 500, DtoMapper.errorResponse(
                        "An unexpected server error occurred. Please try again.",
                        "INTERNAL_SERVER_ERROR"
                ));
            }
        } catch (Exception ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleApiRequest(HttpRequestContext ctx, OutputStream out) throws IOException {
        String method = ctx.method;
        String path = ctx.path;

        switch (path) {
            case "/api/health": {
                requireMethod(method, "GET");
                Map<String, Object> health = new LinkedHashMap<>();
                health.put("status", "UP");
                health.put("platform", "NEXUS Real-Time Multiplayer Gaming Hub");
                health.put("javaVersion", System.getProperty("java.version"));
                health.put("httpPort", config.getHttpPort());
                health.put("wsPort", config.getWsPort());
                Map<String, Object> sb = new LinkedHashMap<>();
                sb.put("configured", config.isSupabaseConfigured());
                sb.put("authConnected", supabaseClient.isAuthReachable());
                sb.put("schemaReady", supabaseClient.isSchemaReady());
                health.put("supabase", sb);
                health.put("games", DtoMapper.toGameCatalogDto());
                sendJson(out, 200, DtoMapper.successResponse("NEXUS server is operational.", health));
                break;
            }
            case "/api/games": {
                requireMethod(method, "GET");
                sendJson(out, 200, DtoMapper.successResponse("Game catalog retrieved.", DtoMapper.toGameCatalogDto()));
                break;
            }
            case "/api/auth/register": {
                requireMethod(method, "POST");
                Map<String, Object> body = JsonUtil.parseObject(ctx.body);
                String email = JsonUtil.getString(body, "email");
                String username = JsonUtil.getString(body, "username");
                String password = JsonUtil.getString(body, "password");
                Map<String, Object> authData = authService.register(email, username, password);
                sendJson(out, 201, DtoMapper.successResponse("Registration successful.", authData));
                break;
            }
            case "/api/auth/login": {
                requireMethod(method, "POST");
                Map<String, Object> body = JsonUtil.parseObject(ctx.body);
                String identifier = JsonUtil.getString(body, "identifier");
                if (identifier == null || identifier.trim().isEmpty()) {
                    identifier = JsonUtil.getString(body, "email");
                }
                String password = JsonUtil.getString(body, "password");
                Map<String, Object> authData = authService.login(identifier, password);
                sendJson(out, 200, DtoMapper.successResponse("Login successful.", authData));
                break;
            }
            case "/api/auth/logout": {
                requireMethod(method, "POST");
                String token = extractAuthToken(ctx.headers);
                authService.logout(token);
                sendJson(out, 200, DtoMapper.successResponse("Logged out successfully.", Collections.emptyMap()));
                break;
            }
            case "/api/auth/me": {
                requireMethod(method, "GET");
                User user = authenticateRequest(ctx.headers);
                sendJson(out, 200, DtoMapper.successResponse("Session valid.", DtoMapper.toUserDto(user)));
                break;
            }
            case "/api/rooms": {
                requireMethod(method, "GET");
                User user = authenticateRequest(ctx.headers);
                List<Map<String, Object>> rooms = roomService.listAvailableAndActiveRooms(user.getId());
                sendJson(out, 200, DtoMapper.successResponse("Rooms retrieved.", rooms));
                break;
            }
            case "/api/rooms/create": {
                requireMethod(method, "POST");
                User user = authenticateRequest(ctx.headers);
                Map<String, Object> body = JsonUtil.parseObject(ctx.body);
                String roomName = JsonUtil.getString(body, "roomName");
                String gameType = JsonUtil.getString(body, "gameType");
                Room room = roomService.createRoom(user, roomName, gameType);
                sendJson(out, 201, DtoMapper.successResponse(
                        "Room created successfully.",
                        roomService.buildRoomDto(room)
                ));
                break;
            }
            case "/api/rooms/join": {
                requireMethod(method, "POST");
                User user = authenticateRequest(ctx.headers);
                Map<String, Object> body = JsonUtil.parseObject(ctx.body);
                String roomCode = JsonUtil.getString(body, "roomCode");
                Room room = roomService.joinRoom(user, roomCode);
                sendJson(out, 200, DtoMapper.successResponse(
                        "Joined room " + room.getRoomCode() + ".",
                        roomService.buildRoomDto(room)
                ));
                break;
            }
            case "/api/rooms/chat": {
                requireMethod(method, "POST");
                User user = authenticateRequest(ctx.headers);
                Map<String, Object> body = JsonUtil.parseObject(ctx.body);
                String roomCode = JsonUtil.getString(body, "roomCode");
                String message = JsonUtil.getString(body, "message");
                Map<String, Object> chatPayload = roomService.sendChatMessage(user, roomCode, message);
                sendJson(out, 200, DtoMapper.successResponse("Chat message sent.", chatPayload));
                break;
            }
            case "/api/rooms/leave": {
                requireMethod(method, "POST");
                User user = authenticateRequest(ctx.headers);
                Map<String, Object> body = JsonUtil.parseObject(ctx.body);
                String roomCode = JsonUtil.getString(body, "roomCode");
                Room room = roomService.leaveRoom(user, roomCode);
                sendJson(out, 200, DtoMapper.successResponse(
                        "Left room " + room.getRoomCode() + ".",
                        roomService.buildRoomDto(room)
                ));
                break;
            }
            case "/api/game/move": {
                requireMethod(method, "POST");
                User user = authenticateRequest(ctx.headers);
                Map<String, Object> body = JsonUtil.parseObject(ctx.body);
                String roomCode = JsonUtil.getString(body, "roomCode");
                int row = JsonUtil.getInt(body, "row", -1);
                int col = JsonUtil.getInt(body, "col", -1);
                Map<String, Object> updatedRoom = gameService.submitMove(user, roomCode, row, col);
                sendJson(out, 200, DtoMapper.successResponse("Move validated and applied.", updatedRoom));
                break;
            }
            case "/api/game/rematch": {
                requireMethod(method, "POST");
                User user = authenticateRequest(ctx.headers);
                Map<String, Object> body = JsonUtil.parseObject(ctx.body);
                String roomCode = JsonUtil.getString(body, "roomCode");
                Map<String, Object> updatedRoom = gameService.requestRematch(user, roomCode);
                sendJson(out, 200, DtoMapper.successResponse("Rematch started.", updatedRoom));
                break;
            }
            case "/api/matches/history": {
                requireMethod(method, "GET");
                User user = authenticateRequest(ctx.headers);
                List<Map<String, Object>> history = gameService.getMatchHistoryForUser(user.getId());
                sendJson(out, 200, DtoMapper.successResponse("Match history retrieved.", history));
                break;
            }
            case "/api/leaderboard": {
                requireMethod(method, "GET");
                List<Map<String, Object>> leaderboard = gameService.getLeaderboard();
                sendJson(out, 200, DtoMapper.successResponse("Leaderboard retrieved.", leaderboard));
                break;
            }
            default: {
                if (path.startsWith("/api/rooms/") && path.length() > "/api/rooms/".length()) {
                    requireMethod(method, "GET");
                    authenticateRequest(ctx.headers);
                    String roomCode = path.substring("/api/rooms/".length()).trim();
                    Room room = roomService.getRoomByCode(roomCode);
                    sendJson(out, 200, DtoMapper.successResponse(
                            "Room state retrieved.",
                            roomService.buildRoomDto(room)
                    ));
                } else if (path.startsWith("/api/matches/analysis/") && path.length() > "/api/matches/analysis/".length()) {
                    requireMethod(method, "GET");
                    authenticateRequest(ctx.headers);
                    String matchId = path.substring("/api/matches/analysis/".length()).trim();
                    sendJson(out, 200, DtoMapper.successResponse(
                            "Match analysis retrieved.",
                            gameService.getMatchByIdDto(matchId)
                    ));
                } else {
                    sendJson(out, 404, DtoMapper.errorResponse("API endpoint not found: " + path, "ENDPOINT_NOT_FOUND"));
                }
                break;
            }
        }
    }

    private void serveStaticFile(String requestPath, OutputStream out) throws IOException {
        String cleanPath = (requestPath == null || requestPath.equals("/") || requestPath.trim().isEmpty())
                ? "/index.html"
                : requestPath;

        if (cleanPath.contains("..")) {
            sendJson(out, 403, DtoMapper.errorResponse("Forbidden path.", "FORBIDDEN"));
            return;
        }

        File rootDir = new File(config.getFrontendDir()).getCanonicalFile();
        File targetFile = new File(rootDir, cleanPath).getCanonicalFile();

        if (!targetFile.getPath().startsWith(rootDir.getPath()) || !targetFile.exists() || targetFile.isDirectory()) {
            targetFile = new File(rootDir, "index.html");
            if (!targetFile.exists()) {
                sendJson(out, 404, DtoMapper.errorResponse("Resource not found.", "NOT_FOUND"));
                return;
            }
        }

        byte[] content = Files.readAllBytes(targetFile.toPath());
        writeHttpResponse(out, 200, detectMimeType(targetFile.getName()), content);
    }

    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private User authenticateRequest(Map<String, String> headers) {
        String token = extractAuthToken(headers);
        return authService.requireAuthenticatedUser(token);
    }

    private String extractAuthToken(Map<String, String> headers) {
        String authHeader = headers.get("authorization");
        if (authHeader != null && !authHeader.trim().isEmpty()) {
            return authHeader.toLowerCase().startsWith("bearer ")
                    ? authHeader.substring(7).trim()
                    : authHeader.trim();
        }
        String customHeader = headers.get("x-nexus-token");
        return customHeader != null ? customHeader.trim() : null;
    }

    private void sendJson(OutputStream out, int statusCode, Object payload) throws IOException {
        byte[] bytes = JsonUtil.toJson(payload).getBytes(StandardCharsets.UTF_8);
        writeHttpResponse(out, statusCode, "application/json; charset=utf-8", bytes);
    }

    private void writeHttpResponse(OutputStream out, int statusCode, String contentType, byte[] body) throws IOException {
        String statusText = getStatusText(statusCode);

        String headers = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n"
                + "Access-Control-Allow-Headers: Content-Type, Authorization, X-Nexus-Token\r\n"
                + "Connection: close\r\n\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        if (body.length > 0) {
            out.write(body);
        }
        out.flush();
    }

    private static String getStatusText(int statusCode) {
        switch (statusCode) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 204:
                return "No Content";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 409:
                return "Conflict";
            case 410:
                return "Gone";
            default:
                return "Internal Server Error";
        }
    }

    private static byte[] readFixedBytes(InputStream in, int length) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(length);
        byte[] buffer = new byte[Math.min(length, 4096)];
        int remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) {
                break;
            }
            bos.write(buffer, 0, read);
            remaining -= read;
        }
        return bos.toByteArray();
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        int b;
        boolean sawSomething = false;
        while ((b = in.read()) != -1) {
            sawSomething = true;
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
            }
        }
        if (!sawSomething && buffer.size() == 0) {
            return null;
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private void requireMethod(String actual, String expected) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new NexusException("HTTP method " + actual + " not allowed. Expected " + expected + ".", 405, "METHOD_NOT_ALLOWED");
        }
    }

    private static final class HttpRequestContext {
        final String method;
        final String path;
        final Map<String, String> headers;
        final String body;

        HttpRequestContext(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }
}

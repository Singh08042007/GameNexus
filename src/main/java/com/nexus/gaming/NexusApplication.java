package com.nexus.gaming;

import com.nexus.gaming.config.AppConfig;
import com.nexus.gaming.config.EnvLoader;
import com.nexus.gaming.controller.ApiController;
import com.nexus.gaming.model.User;
import com.nexus.gaming.repository.DataRepository;
import com.nexus.gaming.repository.SupabaseClient;
import com.nexus.gaming.service.AuthService;
import com.nexus.gaming.service.GameService;
import com.nexus.gaming.service.RoomService;
import com.nexus.gaming.util.JsonUtil;
import com.nexus.gaming.websocket.WebSocketHub;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Main entry point for the NEXUS — Real-Time Multiplayer Gaming Hub Java Backend.
 * Initializes configuration, Supabase integration, repository, services, REST HTTP server,
 * and RFC 6455 WebSocket server.
 */
public class NexusApplication {

    private final AppConfig config;
    private final SupabaseClient supabaseClient;
    private final DataRepository repository;
    private final WebSocketHub webSocketHub;
    private final AuthService authService;
    private final RoomService roomService;
    private final GameService gameService;
    private final ApiController apiController;

    public NexusApplication(AppConfig config) {
        this.config = config;
        this.supabaseClient = new SupabaseClient(config);
        this.repository = new DataRepository(config, supabaseClient);
        this.webSocketHub = new WebSocketHub(config.getWsPort(), repository);
        this.authService = new AuthService(repository, supabaseClient);
        this.roomService = new RoomService(repository, webSocketHub);
        this.gameService = new GameService(repository, roomService, webSocketHub);
        this.apiController = new ApiController(config, authService, roomService, gameService, supabaseClient);

        configureWebSocketCallbacks();
    }

    private void configureWebSocketCallbacks() {
        webSocketHub.setDisconnectListener(conn -> {
            if (conn.getRoomCode() != null && conn.getUserId() != null) {
                roomService.handleWebSocketDisconnect(conn.getRoomCode(), conn.getUserId());
            }
        });

        webSocketHub.setMessageListener((conn, msg) -> {
            String action = JsonUtil.getString(msg, "action");
            if (conn.getUserId() == null) {
                return;
            }
            Optional<User> userOpt = repository.findById(conn.getUserId());
            if (!userOpt.isPresent()) {
                return;
            }
            User user = userOpt.get();

            if ("MAKE_MOVE".equalsIgnoreCase(action)) {
                String roomCode = JsonUtil.getString(msg, "roomCode");
                int row = JsonUtil.getInt(msg, "row", -1);
                int col = JsonUtil.getInt(msg, "col", -1);
                try {
                    gameService.submitMove(user, roomCode, row, col);
                } catch (Exception e) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("event", "ERROR");
                    err.put("message", e.getMessage());
                    conn.sendText(JsonUtil.toJson(err));
                }
            } else if ("SEND_CHAT".equalsIgnoreCase(action)) {
                String roomCode = JsonUtil.getString(msg, "roomCode");
                String message = JsonUtil.getString(msg, "message");
                try {
                    roomService.sendChatMessage(user, roomCode, message);
                } catch (Exception e) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("event", "ERROR");
                    err.put("message", e.getMessage());
                    conn.sendText(JsonUtil.toJson(err));
                }
            }
        });
    }

    public void start() throws IOException {
        Map<String, Object> sbStatus = supabaseClient.verifyConnectivity();
        System.out.println("==================================================================");
        System.out.println("  NEXUS - Real-Time Multiplayer Gaming Hub (Java OOP Backend)");
        System.out.println("==================================================================");
        System.out.println("  Supabase URL        : " + (config.getSupabaseUrl().isEmpty() ? "(Local Test Mode)" : config.getSupabaseUrl()));
        System.out.println("  Supabase Auth       : " + (Boolean.TRUE.equals(sbStatus.get("authConnected")) ? "CONNECTED" : "OFFLINE (Local Resilient Mode)"));
        System.out.println("  Supabase DB Schema  : " + (Boolean.TRUE.equals(sbStatus.get("schemaReady")) ? "READY" : "PENDING (Run database/schema.sql in Supabase SQL Editor)"));

        webSocketHub.start();
        System.out.println("  WebSocket Server    : ws://localhost:" + config.getWsPort() + "/ws");

        apiController.start();
        System.out.println("  HTTP & REST Server  : http://localhost:" + config.getHttpPort());
        System.out.println("==================================================================");
    }

    public void stop() {
        apiController.stop();
        webSocketHub.stop();
    }

    public AuthService getAuthService() {
        return authService;
    }

    public RoomService getRoomService() {
        return roomService;
    }

    public GameService getGameService() {
        return gameService;
    }

    public DataRepository getRepository() {
        return repository;
    }

    public static void main(String[] args) throws Exception {
        EnvLoader env = new EnvLoader(".env");
        AppConfig config = new AppConfig(env);
        NexusApplication app = new NexusApplication(config);
        app.start();

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        Thread.currentThread().join();
    }
}

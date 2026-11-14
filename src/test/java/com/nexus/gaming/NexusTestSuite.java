package com.nexus.gaming;

import com.nexus.gaming.config.AppConfig;
import com.nexus.gaming.engine.ConnectFourEngine;
import com.nexus.gaming.engine.GameAnalyzer;
import com.nexus.gaming.engine.GomokuEngine;
import com.nexus.gaming.engine.QuadTacToeEngine;
import com.nexus.gaming.engine.TicTacToeEngine;
import com.nexus.gaming.exception.AuthenticationException;
import com.nexus.gaming.exception.AuthorizationException;
import com.nexus.gaming.exception.InvalidMoveException;
import com.nexus.gaming.exception.ResourceNotFoundException;
import com.nexus.gaming.exception.RoomException;
import com.nexus.gaming.model.GameType;
import com.nexus.gaming.model.MatchOutcome;
import com.nexus.gaming.model.Room;
import com.nexus.gaming.model.RoomStatus;
import com.nexus.gaming.model.User;
import com.nexus.gaming.repository.DataRepository;
import com.nexus.gaming.repository.SupabaseClient;
import com.nexus.gaming.util.JsonUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive Automated Test Suite for NEXUS — Real-Time Multiplayer Gaming Hub.
 * Verifies:
 * 1. Registration, Login, Session validation, and Logout.
 * 2. Room creation with GameType selection, joining, capacity limits, invalid codes, and duplicate join recovery.
 * 3. Server-Authoritative Multi-Game Engines (Tic-Tac-Toe 3x3, Connect Four 6x7, Gomoku 9x9, Quad-Tac-Toe 4x4).
 * 4. Real-Time Room Chat & authorization checks.
 * 5. Tactical Game Analysis Engine (opening classification, accuracy, threat/block detection).
 * 6. Race condition prevention on simultaneous move requests via ReentrantLock.
 * 7. Player disconnection / leave-room forfeit handling & persistence reload.
 */
public class NexusTestSuite {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================================");
        System.out.println("  NEXUS - Automated Verification & Stabilization Suite (M1 - M7)");
        System.out.println("==================================================================");

        String testStorePath = "data/test_nexus_store.json";
        deleteTestStore(testStorePath);

        AppConfig testConfig = new AppConfig(
                18080,
                18081,
                "", // Offline/local mode for deterministic unit/integration tests
                "",
                "",
                "frontend",
                testStorePath
        );

        NexusApplication app = new NexusApplication(testConfig);
        boolean serverStarted = false;
        try {
            app.start();
            serverStarted = true;
        } catch (SocketException se) {
            System.out.println("  [INFO] Socket bind restricted in sandbox; running domain/service tests directly.");
        }

        try {
            testEngineWinAndDrawDetection();
            testMultiGameEnginesAndAnalyzer();
            testAuthenticationLifecycle(app);
            testRoomManagementAndValidation(app);
            testRoomChatAndAuthorization(app);
            testAuthoritativeGameplayAndInvalidMoves(app);
            testSimultaneousMoveRaceCondition(app);
            testPlayerDisconnectionAndForfeit(app);
            testPersistenceReload(testConfig);
            if (serverStarted) {
                testRestApiEndpointsAndErrorResponses();
            }
        } finally {
            app.stop();
            deleteTestStore(testStorePath);
        }

        System.out.println("==================================================================");
        System.out.println("  TEST SUMMARY: " + passed + " PASSED | " + failed + " FAILED");
        System.out.println("==================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testEngineWinAndDrawDetection() {
        // 1. Diagonal Win Test (Tic-Tac-Toe 3x3)
        TicTacToeEngine engine = new TicTacToeEngine('X');
        engine.applyMove("m1", "pX", "Alice", 'X', 0, 0);
        engine.applyMove("m1", "pO", "Bob", 'O', 0, 1);
        engine.applyMove("m1", "pX", "Alice", 'X', 1, 1);
        engine.applyMove("m1", "pO", "Bob", 'O', 0, 2);
        engine.applyMove("m1", "pX", "Alice", 'X', 2, 2);
        assertTrue("Diagonal X win detected", engine.checkOutcome() == MatchOutcome.X_WON && engine.isGameOver());
        assertTrue("Winning line is [0,4,8]", engine.getWinningLine() != null && engine.getWinningLine()[2] == 8);

        // 2. Full 9-Move Draw Test
        engine.reset('X');
        engine.applyMove("m2", "pX", "Alice", 'X', 0, 0);
        engine.applyMove("m2", "pO", "Bob", 'O', 0, 1);
        engine.applyMove("m2", "pX", "Alice", 'X', 0, 2);
        engine.applyMove("m2", "pO", "Bob", 'O', 1, 1);
        engine.applyMove("m2", "pX", "Alice", 'X', 1, 0);
        engine.applyMove("m2", "pO", "Bob", 'O', 1, 2);
        engine.applyMove("m2", "pX", "Alice", 'X', 2, 1);
        engine.applyMove("m2", "pO", "Bob", 'O', 2, 0);
        engine.applyMove("m2", "pX", "Alice", 'X', 2, 2);
        assertTrue("9-move draw detected", engine.checkOutcome() == MatchOutcome.DRAW && engine.isGameOver());
    }

    private static void testMultiGameEnginesAndAnalyzer() {
        // 1. Connect Four gravity drop & vertical 4-in-a-row win
        ConnectFourEngine c4 = new ConnectFourEngine('X');
        c4.applyMove("c4m", "pX", "Alice", 'X', 0, 3); // Drops to row 5, col 3
        c4.applyMove("c4m", "pO", "Bob", 'O', 0, 2);   // Drops to row 5, col 2
        c4.applyMove("c4m", "pX", "Alice", 'X', 0, 3); // Drops to row 4, col 3
        c4.applyMove("c4m", "pO", "Bob", 'O', 0, 2);   // Drops to row 4, col 2
        c4.applyMove("c4m", "pX", "Alice", 'X', 0, 3); // Drops to row 3, col 3
        c4.applyMove("c4m", "pO", "Bob", 'O', 0, 2);   // Drops to row 3, col 2
        c4.applyMove("c4m", "pX", "Alice", 'X', 0, 3); // Drops to row 2, col 3 -> 4-in-a-row!
        assertTrue("Connect Four gravity drop & 4-in-a-row win detected",
                c4.checkOutcome() == MatchOutcome.X_WON && c4.getCell(5, 3) == 'X' && c4.getCell(2, 3) == 'X');

        // 2. Gomoku 9x9 horizontal 5-in-a-row win
        GomokuEngine gomoku = new GomokuEngine('X');
        for (int c = 0; c < 4; c++) {
            gomoku.applyMove("gm", "pX", "Alice", 'X', 4, c);
            gomoku.applyMove("gm", "pO", "Bob", 'O', 5, c);
        }
        gomoku.applyMove("gm", "pX", "Alice", 'X', 4, 4);
        assertTrue("Gomoku 9x9 5-in-a-row win detected",
                gomoku.checkOutcome() == MatchOutcome.X_WON && gomoku.getWinningLine().length == 5);

        // 3. Quad-Tac-Toe 4x4 diagonal 4-in-a-row win
        QuadTacToeEngine quad = new QuadTacToeEngine('X');
        for (int i = 0; i < 3; i++) {
            quad.applyMove("qm", "pX", "Alice", 'X', i, i);
            quad.applyMove("qm", "pO", "Bob", 'O', i, 3);
        }
        quad.applyMove("qm", "pX", "Alice", 'X', 3, 3);
        assertTrue("Quad-Tac-Toe 4x4 diagonal win detected",
                quad.checkOutcome() == MatchOutcome.X_WON && quad.getWinningLine().length == 4);

        // 4. GameAnalyzer telemetry evaluation
        Map<String, Object> analysis = GameAnalyzer.analyze(
                GameType.CONNECT_FOUR,
                c4.getMoveHistory(),
                c4.checkOutcome(),
                "Alice",
                "Bob"
        );
        assertTrue("GameAnalyzer produces opening name, accuracy, and move evaluations",
                analysis.get("openingName") != null
                        && ((Number) analysis.get("accuracyX")).intValue() > 0
                        && ((List<?>) analysis.get("moveEvaluations")).size() == 7);
    }

    private static void testAuthenticationLifecycle(NexusApplication app) {
        Map<String, Object> reg1 = app.getAuthService().register("alice@nexus.gg", "AliceOne", "secret123");
        String token1 = String.valueOf(reg1.get("token"));
        assertTrue("User registration returns token", token1.startsWith("nxs_"));

        // Duplicate email check
        boolean duplicateRejected = false;
        try {
            app.getAuthService().register("alice@nexus.gg", "AliceTwo", "secret123");
        } catch (Exception e) {
            duplicateRejected = true;
        }
        assertTrue("Duplicate email registration rejected", duplicateRejected);

        // Login with username and wrong password check
        boolean wrongPassRejected = false;
        try {
            app.getAuthService().login("AliceOne", "wrongpass");
        } catch (AuthenticationException e) {
            wrongPassRejected = true;
        }
        assertTrue("Wrong password login rejected", wrongPassRejected);

        // Valid login
        Map<String, Object> loginRes = app.getAuthService().login("AliceOne", "secret123");
        String loginToken = String.valueOf(loginRes.get("token"));
        User authUser = app.getAuthService().requireAuthenticatedUser(loginToken);
        assertTrue("Valid login resolves authenticated user", "AliceOne".equals(authUser.getUsername()));

        // Logout invalidates token
        app.getAuthService().logout(loginToken);
        boolean expiredRejected = false;
        try {
            app.getAuthService().requireAuthenticatedUser(loginToken);
        } catch (AuthenticationException e) {
            expiredRejected = true;
        }
        assertTrue("Logged-out token rejected on protected check", expiredRejected);
    }

    private static void testRoomManagementAndValidation(NexusApplication app) {
        Map<String, Object> u1Map = app.getAuthService().register("host@nexus.gg", "HostPlayer", "password123");
        Map<String, Object> u2Map = app.getAuthService().register("guest@nexus.gg", "GuestPlayer", "password123");
        Map<String, Object> u3Map = app.getAuthService().register("third@nexus.gg", "ThirdPlayer", "password123");

        User host = app.getAuthService().requireAuthenticatedUser(String.valueOf(u1Map.get("token")));
        User guest = app.getAuthService().requireAuthenticatedUser(String.valueOf(u2Map.get("token")));
        User third = app.getAuthService().requireAuthenticatedUser(String.valueOf(u3Map.get("token")));

        Room room = app.getRoomService().createRoom(host, "Championship Room", "CONNECT_FOUR");
        assertTrue("Room created in WAITING state with selected GameType CONNECT_FOUR",
                room.getStatus() == RoomStatus.WAITING
                        && room.getCurrentPlayerCount() == 1
                        && room.getGameType() == GameType.CONNECT_FOUR);

        // Invalid room code rejection
        boolean invalidCodeRejected = false;
        try {
            app.getRoomService().joinRoom(guest, "INVALID99");
        } catch (ResourceNotFoundException e) {
            invalidCodeRejected = true;
        }
        assertTrue("Joining non-existent room code rejected", invalidCodeRejected);

        // Duplicate join by host (reconnection) keeps player count at 1
        Room rejoined = app.getRoomService().joinRoom(host, room.getRoomCode());
        assertTrue("Duplicate join by existing member handled gracefully as reconnect",
                rejoined.getCurrentPlayerCount() == 1 && rejoined.getStatus() == RoomStatus.WAITING);

        // Second player joins -> starts match automatically
        Room startedRoom = app.getRoomService().joinRoom(guest, room.getRoomCode());
        assertTrue("Second player joining transitions room to IN_PROGRESS (2/2 players)",
                startedRoom.getCurrentPlayerCount() == 2 && startedRoom.getStatus() == RoomStatus.IN_PROGRESS);

        // Third player rejected when room is full (2/2)
        boolean thirdRejected = false;
        try {
            app.getRoomService().joinRoom(third, room.getRoomCode());
        } catch (RoomException e) {
            thirdRejected = true;
        }
        assertTrue("Third player rejected from full 2-player room", thirdRejected);
    }

    private static void testRoomChatAndAuthorization(NexusApplication app) {
        User px = app.getRepository().findByUsername("HostPlayer").get();
        User po = app.getRepository().findByUsername("GuestPlayer").get();
        User outsider = app.getRepository().findByUsername("ThirdPlayer").get();

        Room room = app.getRoomService().createRoom(px, "Chat Arena", "TIC_TAC_TOE");
        app.getRoomService().joinRoom(po, room.getRoomCode());
        String code = room.getRoomCode();

        Map<String, Object> chatRes = app.getRoomService().sendChatMessage(px, code, "Good luck, have fun!");
        assertTrue("Participant can send live chat message in room",
                chatRes.get("chatMessage") != null && room.getChatMessages().size() >= 3);

        boolean outsiderChatBlocked = false;
        try {
            app.getRoomService().sendChatMessage(outsider, code, "I am not in this room");
        } catch (AuthorizationException e) {
            outsiderChatBlocked = true;
        }
        assertTrue("Non-participant blocked from sending room chat", outsiderChatBlocked);
    }

    private static void testAuthoritativeGameplayAndInvalidMoves(NexusApplication app) {
        User px = app.getRepository().findByUsername("HostPlayer").get();
        User po = app.getRepository().findByUsername("GuestPlayer").get();
        User outsider = app.getRepository().findByUsername("ThirdPlayer").get();

        Room room = app.getRoomService().createRoom(px, "Validation Match");
        app.getRoomService().joinRoom(po, room.getRoomCode());
        String code = room.getRoomCode();

        // 1. Outsider attempts to submit a move (Authorization check)
        boolean outsiderBlocked = false;
        try {
            app.getGameService().submitMove(outsider, code, 0, 0);
        } catch (AuthorizationException e) {
            outsiderBlocked = true;
        }
        assertTrue("Non-member player move blocked by server", outsiderBlocked);

        // 2. Player O attempts to move first on X's turn
        boolean outOfTurnBlocked = false;
        try {
            app.getGameService().submitMove(po, code, 0, 0);
        } catch (InvalidMoveException e) {
            outOfTurnBlocked = true;
        }
        assertTrue("Out-of-turn move blocked by server", outOfTurnBlocked);

        // 3. Valid move by Player X at (0,0)
        app.getGameService().submitMove(px, code, 0, 0);

        // 4. Player O attempts to play on occupied cell (0,0)
        boolean occupiedBlocked = false;
        try {
            app.getGameService().submitMove(po, code, 0, 0);
        } catch (InvalidMoveException e) {
            occupiedBlocked = true;
        }
        assertTrue("Occupied cell move blocked by server", occupiedBlocked);

        // 5. Out-of-bounds coordinates rejected
        boolean boundsBlocked = false;
        try {
            app.getGameService().submitMove(po, code, 3, 0);
        } catch (InvalidMoveException e) {
            boundsBlocked = true;
        }
        assertTrue("Out-of-bounds coordinates blocked by server", boundsBlocked);

        // Complete match with Player X winning top row: (0,0), (0,1), (0,2)
        app.getGameService().submitMove(po, code, 1, 0);
        app.getGameService().submitMove(px, code, 0, 1);
        app.getGameService().submitMove(po, code, 1, 1);
        app.getGameService().submitMove(px, code, 0, 2);

        Room finished = app.getRoomService().getRoomByCode(code);
        assertTrue("Match completes with X_WON",
                finished.getStatus() == RoomStatus.COMPLETED
                        && finished.getGameEngine().checkOutcome() == MatchOutcome.X_WON);

        // 6. Move after match completion rejected
        boolean postGameBlocked = false;
        try {
            app.getGameService().submitMove(po, code, 2, 2);
        } catch (InvalidMoveException e) {
            postGameBlocked = true;
        }
        assertTrue("Move after match completion blocked by server", postGameBlocked);
    }

    private static void testSimultaneousMoveRaceCondition(NexusApplication app) throws InterruptedException {
        User px = app.getRepository().findByUsername("HostPlayer").get();
        User po = app.getRepository().findByUsername("GuestPlayer").get();

        Room room = app.getRoomService().createRoom(px, "Concurrency Stress Room");
        app.getRoomService().joinRoom(po, room.getRoomCode());
        String code = room.getRoomCode();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int col = i % 3;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    app.getGameService().submitMove(px, code, 0, col);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        pool.shutdown();

        assertTrue("Simultaneous move race condition prevented (exactly 1 move accepted)",
                successCount.get() == 1 && failureCount.get() == (threads - 1));
    }

    private static void testPlayerDisconnectionAndForfeit(NexusApplication app) {
        User px = app.getRepository().findByUsername("HostPlayer").get();
        User po = app.getRepository().findByUsername("GuestPlayer").get();

        int poWinsBefore = po.getWins();
        Room room = app.getRoomService().createRoom(px, "Forfeit Test Room");
        app.getRoomService().joinRoom(po, room.getRoomCode());

        // Host leaves mid-game -> Guest awarded forfeit victory
        Room afterLeave = app.getRoomService().leaveRoom(px, room.getRoomCode());
        User poUpdated = app.getRepository().findById(po.getId()).get();

        assertTrue("Leaving active match triggers FORFEIT and awards win to remaining player",
                afterLeave.getStatus() == RoomStatus.COMPLETED
                        && afterLeave.getGameEngine().checkOutcome() == MatchOutcome.FORFEIT
                        && poUpdated.getWins() == poWinsBefore + 1);
    }

    private static void testPersistenceReload(AppConfig testConfig) {
        SupabaseClient sb = new SupabaseClient(testConfig);
        DataRepository reloadedRepo = new DataRepository(testConfig, sb);

        assertTrue("Users persisted and reloaded from store",
                reloadedRepo.findByUsername("HostPlayer").isPresent());
        List<?> history = reloadedRepo.findAllCompletedMatches();
        assertTrue("Completed match history persisted and reloaded", history.size() >= 2);
    }

    private static void testRestApiEndpointsAndErrorResponses() {
        try {
            // 1. Health endpoint
            HttpURLConnection healthConn = (HttpURLConnection) URI.create("http://localhost:18080/api/health").toURL().openConnection();
            healthConn.setRequestMethod("GET");
            int healthStatus = healthConn.getResponseCode();
            String healthBodyStr;
            try (InputStream in = healthConn.getInputStream()) {
                healthBodyStr = readStreamToString(in);
            }
            Map<String, Object> healthBody = JsonUtil.parseObject(healthBodyStr);
            assertTrue("GET /api/health returns 200 and success=true",
                    healthStatus == 200 && Boolean.TRUE.equals(healthBody.get("success")));

            // 2. Protected endpoint without token returns 401
            HttpURLConnection unauthConn = (HttpURLConnection) URI.create("http://localhost:18080/api/rooms").toURL().openConnection();
            unauthConn.setRequestMethod("GET");
            int unauthStatus = unauthConn.getResponseCode();
            assertTrue("Protected endpoint /api/rooms rejects unauthenticated request with 401",
                    unauthStatus == 401);

            // 3. Static frontend index.html served at root /
            HttpURLConnection indexConn = (HttpURLConnection) URI.create("http://localhost:18080/").toURL().openConnection();
            indexConn.setRequestMethod("GET");
            int indexStatus = indexConn.getResponseCode();
            String indexHtml;
            try (InputStream in = indexConn.getInputStream()) {
                indexHtml = readStreamToString(in);
            }
            assertTrue("Root / serves NEXUS frontend HTML",
                    indexStatus == 200 && indexHtml.contains("NEXUS"));
        } catch (SocketException se) {
            System.out.println("  [INFO] Loopback HTTP check skipped due to sandbox socket restriction.");
        } catch (Exception e) {
            assertTrue("REST API endpoint check (" + e.getMessage() + ")", false);
        }
    }

    private static String readStreamToString(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void assertTrue(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + description);
        } else {
            failed++;
            System.err.println("  [FAIL] " + description);
        }
    }

    private static void deleteTestStore(String path) {
        File f = new File(path);
        if (f.exists()) {
            f.delete();
        }
    }
}

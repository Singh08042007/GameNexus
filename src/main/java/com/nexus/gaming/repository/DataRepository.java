package com.nexus.gaming.repository;

import com.nexus.gaming.config.AppConfig;
import com.nexus.gaming.model.GameType;
import com.nexus.gaming.model.Match;
import com.nexus.gaming.model.MatchOutcome;
import com.nexus.gaming.model.Move;
import com.nexus.gaming.model.Room;
import com.nexus.gaming.model.User;
import com.nexus.gaming.util.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread-safe Repository managing Users, Rooms, Matches, and Session Tokens.
 * Synchronizes state with Supabase (PostgreSQL + Auth) via SupabaseClient and maintains
 * a local persistent snapshot on disk so data survives restarts and offline/sandboxed tests.
 */
public class DataRepository implements Repository<User, String> {

    private final AppConfig config;
    private final SupabaseClient supabaseClient;
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, String> userIdByEmail = new ConcurrentHashMap<>();
    private final Map<String, String> userIdByUsername = new ConcurrentHashMap<>();
    private final Map<String, String> userIdByToken = new ConcurrentHashMap<>();
    private final Map<String, Room> roomsByCode = new ConcurrentHashMap<>();
    private final Map<String, Match> matchesById = new ConcurrentHashMap<>();
    private final ExecutorService syncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "nexus-repo-sync");
        t.setDaemon(true);
        return t;
    });

    public DataRepository(AppConfig config, SupabaseClient supabaseClient) {
        this.config = config;
        this.supabaseClient = supabaseClient;
        loadFromDisk();
    }

    @Override
    public User save(User user) {
        if (user == null) return null;
        usersById.put(user.getId(), user);
        if (user.getEmail() != null) {
            userIdByEmail.put(user.getEmail().toLowerCase(), user.getId());
        }
        if (user.getUsername() != null) {
            userIdByUsername.put(user.getUsername().toLowerCase(), user.getId());
        }
        persistToDisk();
        syncExecutor.submit(() -> supabaseClient.upsertProfile(user));
        return user;
    }

    @Override
    public Optional<User> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public List<User> findAll() {
        List<User> list = new ArrayList<>(usersById.values());
        list.sort(Comparator.comparingInt(User::getWins).reversed()
                .thenComparingInt(User::getTotalMatches).reversed());
        return list;
    }

    @Override
    public boolean deleteById(String id) {
        User removed = usersById.remove(id);
        if (removed != null) {
            if (removed.getEmail() != null) userIdByEmail.remove(removed.getEmail().toLowerCase());
            if (removed.getUsername() != null) userIdByUsername.remove(removed.getUsername().toLowerCase());
            persistToDisk();
            return true;
        }
        return false;
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String id = userIdByEmail.get(email.trim().toLowerCase());
        return id == null ? Optional.empty() : findById(id);
    }

    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        String id = userIdByUsername.get(username.trim().toLowerCase());
        return id == null ? Optional.empty() : findById(id);
    }

    public void saveSessionToken(String token, String userId) {
        if (token != null && userId != null) {
            userIdByToken.put(token, userId);
            persistToDisk();
        }
    }

    public void removeSessionToken(String token) {
        if (token != null) {
            userIdByToken.remove(token);
            persistToDisk();
        }
    }

    public Optional<User> findUserByToken(String token) {
        if (token == null || token.trim().isEmpty()) return Optional.empty();
        String userId = userIdByToken.get(token.trim());
        return userId == null ? Optional.empty() : findById(userId);
    }

    // Room Operations
    public Room saveRoom(Room room) {
        if (room == null) return null;
        roomsByCode.put(room.getRoomCode().toUpperCase(), room);
        syncExecutor.submit(() -> supabaseClient.upsertRoom(room));
        return room;
    }

    public Optional<Room> findRoomByCode(String roomCode) {
        if (roomCode == null) return Optional.empty();
        return Optional.ofNullable(roomsByCode.get(roomCode.trim().toUpperCase()));
    }

    public List<Room> findAllRooms() {
        List<Room> rooms = new ArrayList<>(roomsByCode.values());
        rooms.sort(Comparator.comparing(Room::getUpdatedAt).reversed());
        return rooms;
    }

    // Match Operations
    public Match saveMatch(Match match) {
        if (match == null) return null;
        matchesById.put(match.getId(), match);
        persistToDisk();
        syncExecutor.submit(() -> supabaseClient.upsertMatch(match));
        return match;
    }

    public void recordMovePersistence(Match match, Move move) {
        if (match != null) {
            matchesById.put(match.getId(), match);
        }
        persistToDisk();
        syncExecutor.submit(() -> {
            if (match != null) supabaseClient.upsertMatch(match);
            if (move != null) supabaseClient.insertMove(move);
        });
    }

    public Optional<Match> findMatchById(String matchId) {
        if (matchId == null) return Optional.empty();
        return Optional.ofNullable(matchesById.get(matchId));
    }

    public List<Match> findMatchesForUser(String userId) {
        List<Match> result = new ArrayList<>();
        for (Match m : matchesById.values()) {
            if (userId == null
                    || userId.equals(m.getPlayerXId())
                    || userId.equals(m.getPlayerOId())) {
                result.add(m);
            }
        }
        result.sort(Comparator.comparing(Match::getStartedAt).reversed());
        return result;
    }

    public List<Match> findAllCompletedMatches() {
        List<Match> result = new ArrayList<>();
        for (Match m : matchesById.values()) {
            if (m.getOutcome() != MatchOutcome.IN_PROGRESS) {
                result.add(m);
            }
        }
        result.sort(Comparator.comparing(Match::getStartedAt).reversed());
        return result;
    }

    public synchronized void persistToDisk() {
        String storePath = config.getDataStorePath();
        if (storePath == null || storePath.trim().isEmpty()) return;
        try {
            File file = new File(storePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Map<String, Object> root = new LinkedHashMap<>();
            List<Map<String, Object>> serializedUsers = new ArrayList<>();
            for (User u : usersById.values()) {
                Map<String, Object> uMap = new LinkedHashMap<>();
                uMap.put("id", u.getId());
                uMap.put("email", u.getEmail());
                uMap.put("username", u.getUsername());
                uMap.put("passwordHash", u.getPasswordHash());
                uMap.put("wins", u.getWins());
                uMap.put("losses", u.getLosses());
                uMap.put("draws", u.getDraws());
                uMap.put("createdAt", u.getCreatedAt());
                uMap.put("updatedAt", u.getUpdatedAt());
                serializedUsers.add(uMap);
            }
            root.put("users", serializedUsers);

            List<Map<String, Object>> serializedMatches = new ArrayList<>();
            for (Match m : matchesById.values()) {
                Map<String, Object> mMap = new LinkedHashMap<>();
                mMap.put("id", m.getId());
                mMap.put("roomId", m.getRoomId());
                mMap.put("roomCode", m.getRoomCode());
                mMap.put("gameType", m.getGameType() != null ? m.getGameType().name() : GameType.TIC_TAC_TOE.name());
                mMap.put("playerXId", m.getPlayerXId());
                mMap.put("playerXName", m.getPlayerXName());
                mMap.put("playerOId", m.getPlayerOId());
                mMap.put("playerOName", m.getPlayerOName());
                mMap.put("winnerId", m.getWinnerId());
                mMap.put("winnerName", m.getWinnerName());
                mMap.put("outcome", m.getOutcome().name());
                mMap.put("finalBoard", m.getFinalBoard());
                mMap.put("moveCount", m.getMoveCount());
                mMap.put("startedAt", m.getStartedAt());
                mMap.put("endedAt", m.getEndedAt());

                List<Map<String, Object>> serializedMoves = new ArrayList<>();
                for (Move mv : m.getMoves()) {
                    Map<String, Object> mvMap = new LinkedHashMap<>();
                    mvMap.put("matchId", mv.getMatchId());
                    mvMap.put("playerId", mv.getPlayerId());
                    mvMap.put("playerUsername", mv.getPlayerUsername());
                    mvMap.put("symbol", String.valueOf(mv.getSymbol()));
                    mvMap.put("row", mv.getRow());
                    mvMap.put("col", mv.getCol());
                    mvMap.put("moveNumber", mv.getMoveNumber());
                    mvMap.put("boardStateAfter", mv.getBoardStateAfter());
                    mvMap.put("createdAt", mv.getCreatedAt());
                    serializedMoves.add(mvMap);
                }
                mMap.put("moves", serializedMoves);
                serializedMatches.add(mMap);
            }
            root.put("matches", serializedMatches);
            root.put("sessions", new LinkedHashMap<>(userIdByToken));

            Path tempPath = new File(storePath + ".tmp").toPath();
            byte[] jsonBytes = JsonUtil.toJson(root).getBytes(StandardCharsets.UTF_8);
            Files.write(tempPath, jsonBytes);
            Files.move(tempPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[DataRepository] Warning: Failed to save local store: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadFromDisk() {
        String storePath = config.getDataStorePath();
        if (storePath == null || storePath.trim().isEmpty()) return;
        File file = new File(storePath);
        if (!file.exists() || !file.isFile()) return;
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Map<String, Object> root = JsonUtil.parseObject(raw);

            Object usersObj = root.get("users");
            if (usersObj instanceof List<?>) {
                List<?> userList = (List<?>) usersObj;
                for (Object item : userList) {
                    if (item instanceof Map<?, ?>) {
                        Map<String, Object> uMap = (Map<String, Object>) item;
                        User u = new User(
                                JsonUtil.getString(uMap, "id"),
                                JsonUtil.getString(uMap, "email"),
                                JsonUtil.getString(uMap, "username"),
                                JsonUtil.getInt(uMap, "wins", 0),
                                JsonUtil.getInt(uMap, "losses", 0),
                                JsonUtil.getInt(uMap, "draws", 0),
                                JsonUtil.getString(uMap, "createdAt"),
                                JsonUtil.getString(uMap, "updatedAt")
                        );
                        u.setPasswordHash(JsonUtil.getString(uMap, "passwordHash"));
                        usersById.put(u.getId(), u);
                        if (u.getEmail() != null) userIdByEmail.put(u.getEmail().toLowerCase(), u.getId());
                        if (u.getUsername() != null) userIdByUsername.put(u.getUsername().toLowerCase(), u.getId());
                    }
                }
            }

            Object matchesObj = root.get("matches");
            if (matchesObj instanceof List<?>) {
                List<?> matchList = (List<?>) matchesObj;
                for (Object item : matchList) {
                    if (item instanceof Map<?, ?>) {
                        Map<String, Object> mMap = (Map<String, Object>) item;
                        Match m = new Match();
                        m.setId(JsonUtil.getString(mMap, "id"));
                        m.setRoomId(JsonUtil.getString(mMap, "roomId"));
                        m.setRoomCode(JsonUtil.getString(mMap, "roomCode"));
                        m.setGameType(GameType.fromString(JsonUtil.getString(mMap, "gameType")));
                        m.setPlayerXId(JsonUtil.getString(mMap, "playerXId"));
                        m.setPlayerXName(JsonUtil.getString(mMap, "playerXName"));
                        m.setPlayerOId(JsonUtil.getString(mMap, "playerOId"));
                        m.setPlayerOName(JsonUtil.getString(mMap, "playerOName"));
                        m.setWinnerId(JsonUtil.getString(mMap, "winnerId"));
                        m.setWinnerName(JsonUtil.getString(mMap, "winnerName"));
                        String outcomeStr = JsonUtil.getString(mMap, "outcome");
                        if (outcomeStr != null) {
                            try {
                                m.setOutcome(MatchOutcome.valueOf(outcomeStr));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        m.setFinalBoard(JsonUtil.getString(mMap, "finalBoard"));
                        m.setMoveCount(JsonUtil.getInt(mMap, "moveCount", 0));
                        m.setStartedAt(JsonUtil.getString(mMap, "startedAt"));
                        m.setEndedAt(JsonUtil.getString(mMap, "endedAt"));

                        Object movesObj = mMap.get("moves");
                        if (movesObj instanceof List<?>) {
                            List<?> rawMoves = (List<?>) movesObj;
                            List<Move> loadedMoves = new ArrayList<>();
                            for (Object mvItem : rawMoves) {
                                if (mvItem instanceof Map<?, ?>) {
                                    Map<String, Object> mvMap = (Map<String, Object>) mvItem;
                                    String symStr = JsonUtil.getString(mvMap, "symbol");
                                    char sym = (symStr != null && !symStr.isEmpty()) ? symStr.charAt(0) : 'X';
                                    Move mv = new Move(
                                            JsonUtil.getString(mvMap, "matchId"),
                                            JsonUtil.getString(mvMap, "playerId"),
                                            JsonUtil.getString(mvMap, "playerUsername"),
                                            sym,
                                            JsonUtil.getInt(mvMap, "row", 0),
                                            JsonUtil.getInt(mvMap, "col", 0),
                                            JsonUtil.getInt(mvMap, "moveNumber", 1),
                                            JsonUtil.getString(mvMap, "boardStateAfter"),
                                            JsonUtil.getString(mvMap, "createdAt")
                                    );
                                    loadedMoves.add(mv);
                                }
                            }
                            m.setMoves(loadedMoves);
                        }
                        matchesById.put(m.getId(), m);
                    }
                }
            }

            Object sessionsObj = root.get("sessions");
            if (sessionsObj instanceof Map<?, ?>) {
                Map<?, ?> sMap = (Map<?, ?>) sessionsObj;
                for (Map.Entry<?, ?> entry : sMap.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        userIdByToken.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DataRepository] Warning: Could not load store from disk: " + e.getMessage());
        }
    }
}

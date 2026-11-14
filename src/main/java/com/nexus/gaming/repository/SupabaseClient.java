package com.nexus.gaming.repository;

import com.nexus.gaming.config.AppConfig;
import com.nexus.gaming.model.Match;
import com.nexus.gaming.model.Move;
import com.nexus.gaming.model.Room;
import com.nexus.gaming.model.User;
import com.nexus.gaming.util.JsonUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side Supabase HTTP Client for Supabase Auth and PostgREST persistence.
 * Keeps the Supabase secret key strictly on the Java backend; never exposed to the frontend.
 */
public class SupabaseClient {

    private static final int TIMEOUT_MS = 4000;

    private final AppConfig config;
    private volatile boolean authReachable = false;
    private volatile boolean schemaReady = false;

    public SupabaseClient(AppConfig config) {
        this.config = config;
    }

    /**
     * Verifies connectivity to Supabase Auth and checks whether public schema tables exist.
     */
    public Map<String, Object> verifyConnectivity() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("configured", config.isSupabaseConfigured());
        status.put("projectUrl", config.getSupabaseUrl());

        if (!config.isSupabaseConfigured()) {
            status.put("authConnected", false);
            status.put("schemaReady", false);
            status.put("mode", "LOCAL_PERSISTENCE_ONLY");
            return status;
        }

        // 1. Check Supabase Auth endpoint
        try {
            HttpResult authRes = sendRequest(
                    "GET",
                    config.getSupabaseUrl() + "/auth/v1/settings",
                    config.getSupabaseAnonKey(),
                    null,
                    null
            );
            this.authReachable = (authRes.statusCode >= 200 && authRes.statusCode < 300);
        } catch (Exception e) {
            this.authReachable = false;
        }

        // 2. Check PostgREST public.profiles table
        if (this.authReachable) {
            try {
                HttpResult tableRes = sendRequest(
                        "GET",
                        config.getSupabaseUrl() + "/rest/v1/profiles?select=id&limit=1",
                        config.getSupabaseSecretKey(),
                        null,
                        null
                );
                this.schemaReady = (tableRes.statusCode >= 200 && tableRes.statusCode < 300);
            } catch (Exception e) {
                this.schemaReady = false;
            }
        }

        status.put("authConnected", this.authReachable);
        status.put("schemaReady", this.schemaReady);
        status.put("mode", this.schemaReady ? "SUPABASE_FULL"
                : (this.authReachable ? "SUPABASE_AUTH_PLUS_LOCAL_SYNC" : "LOCAL_RESILIENT_FALLBACK"));
        return status;
    }

    /**
     * Registers a user in Supabase Auth using the Admin API (auto-confirms email)
     * and returns the Supabase user UUID if reachable.
     */
    public String registerUserInSupabaseAuth(String email, String password, String username) {
        if (!config.isSupabaseConfigured()) {
            return null;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("email", email);
            payload.put("password", password);
            payload.put("email_confirm", true);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("username", username);
            payload.put("user_metadata", meta);

            HttpResult res = sendRequest(
                    "POST",
                    config.getSupabaseUrl() + "/auth/v1/admin/users",
                    config.getSupabaseSecretKey(),
                    null,
                    JsonUtil.toJson(payload)
            );
            this.authReachable = true;
            if (res.statusCode == 200 || res.statusCode == 201) {
                Map<String, Object> body = JsonUtil.parseObject(res.body);
                return JsonUtil.getString(body, "id");
            }
        } catch (Exception ignored) {
            this.authReachable = false;
        }
        return null;
    }

    /**
     * Authenticates user credentials via Supabase Auth password grant.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> signInWithSupabaseAuth(String email, String password) {
        if (!config.isSupabaseConfigured()) {
            return null;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("email", email);
            payload.put("password", password);

            HttpResult res = sendRequest(
                    "POST",
                    config.getSupabaseUrl() + "/auth/v1/token?grant_type=password",
                    config.getSupabaseAnonKey(),
                    null,
                    JsonUtil.toJson(payload)
            );
            this.authReachable = true;
            if (res.statusCode == 200) {
                Map<String, Object> body = JsonUtil.parseObject(res.body);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("accessToken", JsonUtil.getString(body, "access_token"));
                Object userObj = body.get("user");
                if (userObj instanceof Map<?, ?>) {
                    Map<String, Object> u = (Map<String, Object>) userObj;
                    result.put("id", JsonUtil.getString(u, "id"));
                    result.put("email", JsonUtil.getString(u, "email"));
                    Object metaObj = u.get("user_metadata");
                    if (metaObj instanceof Map<?, ?>) {
                        result.put("username", JsonUtil.getString((Map<String, Object>) metaObj, "username"));
                    }
                }
                return result;
            }
        } catch (Exception ignored) {
            this.authReachable = false;
        }
        return null;
    }

    public void upsertProfile(User user) {
        if (!config.isSupabaseConfigured() || user == null) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("email", user.getEmail());
        row.put("username", user.getUsername());
        row.put("wins", user.getWins());
        row.put("losses", user.getLosses());
        row.put("draws", user.getDraws());
        row.put("updated_at", user.getUpdatedAt());
        postgrestUpsert("profiles", "id", row);
    }

    public void upsertRoom(Room room) {
        if (!config.isSupabaseConfigured() || room == null) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", room.getId());
        row.put("room_code", room.getRoomCode());
        row.put("room_name", room.getRoomName());
        row.put("game_type", room.getGameType().name());
        if (room.getPlayerX() != null) {
            row.put("host_id", room.getPlayerX().getId());
        }
        row.put("guest_id", room.getPlayerO() != null ? room.getPlayerO().getId() : null);
        row.put("status", room.getStatus().name());
        row.put("max_players", room.getMaxPlayers());
        row.put("current_match_id", room.getCurrentMatchId());
        row.put("updated_at", room.getUpdatedAt());
        postgrestUpsert("rooms", "room_code", row);
    }

    public void upsertMatch(Match match) {
        if (!config.isSupabaseConfigured() || match == null) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", match.getId());
        row.put("room_id", match.getRoomId());
        row.put("room_code", match.getRoomCode());
        row.put("game_type", match.getGameType().name());
        row.put("player_x_id", match.getPlayerXId());
        row.put("player_x_name", match.getPlayerXName());
        row.put("player_o_id", match.getPlayerOId());
        row.put("player_o_name", match.getPlayerOName());
        row.put("winner_id", match.getWinnerId());
        row.put("outcome", match.getOutcome().name());
        row.put("final_board", match.getFinalBoard());
        row.put("move_count", match.getMoveCount());
        row.put("started_at", match.getStartedAt());
        row.put("ended_at", match.getEndedAt());
        postgrestUpsert("matches", "id", row);
    }

    public void insertMove(Move move) {
        if (!config.isSupabaseConfigured() || move == null) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("match_id", move.getMatchId());
        row.put("player_id", move.getPlayerId());
        row.put("player_symbol", String.valueOf(move.getSymbol()));
        row.put("row_index", move.getRow());
        row.put("col_index", move.getCol());
        row.put("move_number", move.getMoveNumber());
        row.put("board_state_after", move.getBoardStateAfter());
        row.put("created_at", move.getCreatedAt());
        postgrestInsert("match_moves", row);
    }

    private void postgrestUpsert(String table, String onConflictColumn, Map<String, Object> row) {
        try {
            String url = config.getSupabaseUrl() + "/rest/v1/" + table
                    + "?on_conflict=" + URLEncoder.encode(onConflictColumn, "UTF-8");
            HttpResult res = sendRequest(
                    "POST",
                    url,
                    config.getSupabaseSecretKey(),
                    "resolution=merge-duplicates,return=minimal",
                    JsonUtil.toJson(row)
            );
            if (res.statusCode >= 200 && res.statusCode < 300) {
                this.schemaReady = true;
            }
        } catch (Exception ignored) {
        }
    }

    private void postgrestInsert(String table, Map<String, Object> row) {
        try {
            String url = config.getSupabaseUrl() + "/rest/v1/" + table;
            HttpResult res = sendRequest(
                    "POST",
                    url,
                    config.getSupabaseSecretKey(),
                    "return=minimal",
                    JsonUtil.toJson(row)
            );
            if (res.statusCode >= 200 && res.statusCode < 300) {
                this.schemaReady = true;
            }
        } catch (Exception ignored) {
        }
    }

    public boolean isAuthReachable() {
        return authReachable;
    }

    public boolean isSchemaReady() {
        return schemaReady;
    }

    public List<Object> fetchTableRows(String queryPath) {
        try {
            HttpResult res = sendRequest(
                    "GET",
                    config.getSupabaseUrl() + "/rest/v1/" + queryPath,
                    config.getSupabaseSecretKey(),
                    null,
                    null
            );
            if (res.statusCode == 200) {
                return JsonUtil.parseArray(res.body);
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private HttpResult sendRequest(String method, String url, String apiKey, String preferHeader, String jsonBody)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            conn.setRequestProperty("apikey", apiKey);
        }
        if (preferHeader != null && !preferHeader.trim().isEmpty()) {
            conn.setRequestProperty("Prefer", preferHeader);
        }
        if (jsonBody != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
        }
        int status = conn.getResponseCode();
        InputStream stream = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
        String responseBody = "";
        if (stream != null) {
            try (InputStream in = stream) {
                responseBody = readStreamToString(in);
            }
        }
        conn.disconnect();
        return new HttpResult(status, responseBody);
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

    private static final class HttpResult {
        final int statusCode;
        final String body;

        HttpResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}

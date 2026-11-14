package com.nexus.gaming.config;

/**
 * Immutable configuration object for the NEXUS backend server.
 * Demonstrates OOP Encapsulation with private final fields and read-only getters.
 */
public final class AppConfig {

    private final int httpPort;
    private final int wsPort;
    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final String supabaseSecretKey;
    private final String frontendDir;
    private final String dataStorePath;

    public AppConfig(EnvLoader env) {
        this.httpPort = env.getInt("HTTP_PORT", 8080);
        this.wsPort = env.getInt("WS_PORT", 8081);
        this.supabaseUrl = trimTrailingSlash(env.get("SUPABASE_URL", ""));
        this.supabaseAnonKey = env.get("SUPABASE_ANON_KEY", "");
        this.supabaseSecretKey = env.get("SUPABASE_SECRET_KEY", "");
        this.frontendDir = env.get("FRONTEND_DIR", "frontend");
        this.dataStorePath = env.get("DATA_STORE_PATH", "data/nexus_store.json");
    }

    public AppConfig(int httpPort, int wsPort, String supabaseUrl, String supabaseAnonKey,
                     String supabaseSecretKey, String frontendDir, String dataStorePath) {
        this.httpPort = httpPort;
        this.wsPort = wsPort;
        this.supabaseUrl = trimTrailingSlash(supabaseUrl);
        this.supabaseAnonKey = supabaseAnonKey != null ? supabaseAnonKey : "";
        this.supabaseSecretKey = supabaseSecretKey != null ? supabaseSecretKey : "";
        this.frontendDir = frontendDir;
        this.dataStorePath = dataStorePath;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public int getWsPort() {
        return wsPort;
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public String getSupabaseAnonKey() {
        return supabaseAnonKey;
    }

    public String getSupabaseSecretKey() {
        return supabaseSecretKey;
    }

    public String getFrontendDir() {
        return frontendDir;
    }

    public String getDataStorePath() {
        return dataStorePath;
    }

    public boolean isSupabaseConfigured() {
        return supabaseUrl != null && !supabaseUrl.trim().isEmpty()
                && supabaseSecretKey != null && !supabaseSecretKey.trim().isEmpty();
    }
}

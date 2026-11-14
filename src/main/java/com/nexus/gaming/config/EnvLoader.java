package com.nexus.gaming.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads environment variables from a local .env file and falls back to System.getenv().
 * Ensures sensitive credentials (such as Supabase secret keys) are never hardcoded in source code.
 */
public final class EnvLoader {

    private final Map<String, String> envMap;

    public EnvLoader(String dotenvPath) {
        Map<String, String> loaded = new HashMap<>();
        File file = new File(dotenvPath);
        if (file.exists() && file.isFile()) {
            try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int eqIndex = trimmed.indexOf('=');
                    if (eqIndex > 0) {
                        String key = trimmed.substring(0, eqIndex).trim();
                        String value = trimmed.substring(eqIndex + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        loaded.put(key, value);
                    }
                }
            } catch (IOException e) {
                System.err.println("[EnvLoader] Warning: Could not read .env file: " + e.getMessage());
            }
        }
        // System environment variables override .env file defaults in cloud/container deployments
        loaded.putAll(System.getenv());
        this.envMap = Collections.unmodifiableMap(loaded);
    }

    public String get(String key, String defaultValue) {
        String val = envMap.get(key);
        return (val != null && !val.trim().isEmpty()) ? val : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String val = envMap.get(key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

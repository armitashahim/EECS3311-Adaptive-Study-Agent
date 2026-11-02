package ca.yorku.eecs3311.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local configuration. Values come from a ".env" file and may be overridden
 * by real environment variables. Secrets are never stored in source code.
 */
public class AppConfig {
    public static final String API_KEY = "LLM_API_KEY";
    public static final String BASE_URL = "LLM_BASE_URL";
    public static final String MODEL = "LLM_MODEL";

    static final String PLACEHOLDER_KEY = "YOUR_LOCAL_API_KEY_HERE";
    static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    static final String DEFAULT_MODEL = "deepseek-flash";

    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) {
        this.values = values;
    }

    /** Load ".env" from the working directory plus the real environment. */
    public static AppConfig load() throws IOException {
        return load(Path.of(".env"), System.getenv());
    }

    /** Load from a given file (may be missing) and environment map. Environment wins. */
    public static AppConfig load(Path envFile, Map<String, String> environment)
            throws IOException {
        Map<String, String> merged = new HashMap<>();
        if (envFile != null && Files.exists(envFile)) {
            merged.putAll(parse(Files.readAllLines(envFile, StandardCharsets.UTF_8)));
        }
        if (environment != null) {
            for (String key : List.of(API_KEY, BASE_URL, MODEL)) {
                String value = environment.get(key);
                if (value != null && !value.isBlank()) {
                    merged.put(key, value.strip());
                }
            }
        }
        return new AppConfig(merged);
    }

    static Map<String, String> parse(List<String> lines) {
        Map<String, String> result = new HashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    public boolean hasApiKey() {
        String key = values.get(API_KEY);
        return key != null && !key.isBlank() && !key.equals(PLACEHOLDER_KEY);
    }

    public String getApiKey() {
        if (!hasApiKey()) {
            throw new IllegalStateException(
                    "No API key configured. Copy .env.example to .env and set LLM_API_KEY.");
        }
        return values.get(API_KEY);
    }

    public String getBaseUrl() {
        return valueOrDefault(BASE_URL, DEFAULT_BASE_URL);
    }

    public String getModel() {
        return valueOrDefault(MODEL, DEFAULT_MODEL);
    }

    private String valueOrDefault(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
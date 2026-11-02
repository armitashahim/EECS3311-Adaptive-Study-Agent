package ca.yorku.eecs3311.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {
    @TempDir
    Path directory;

    @Test
    void missingFileUsesDefaultsAndHasNoKey() throws IOException {
        AppConfig config = AppConfig.load(directory.resolve(".env"), Map.of());

        assertFalse(config.hasApiKey());
        assertEquals("https://api.deepseek.com", config.getBaseUrl());
        assertEquals("deepseek-flash", config.getModel());
        assertThrows(IllegalStateException.class, config::getApiKey);
    }

    @Test
    void readsValuesFromEnvFileIgnoringCommentsAndQuotes() throws IOException {
        Path file = directory.resolve(".env");
        Files.writeString(file, """
                # comment
                LLM_API_KEY="abc123"

                LLM_MODEL = custom-model
                """);

        AppConfig config = AppConfig.load(file, Map.of());

        assertTrue(config.hasApiKey());
        assertEquals("abc123", config.getApiKey());
        assertEquals("custom-model", config.getModel());
    }

    @Test
    void placeholderKeyIsTreatedAsMissing() throws IOException {
        Path file = directory.resolve(".env");
        Files.writeString(file, "LLM_API_KEY=YOUR_LOCAL_API_KEY_HERE\n");

        AppConfig config = AppConfig.load(file, Map.of());

        assertFalse(config.hasApiKey());
    }

    @Test
    void environmentVariablesOverrideFile() throws IOException {
        Path file = directory.resolve(".env");
        Files.writeString(file, "LLM_API_KEY=from-file\n");

        AppConfig config = AppConfig.load(file, Map.of("LLM_API_KEY", "from-env"));

        assertEquals("from-env", config.getApiKey());
    }
}
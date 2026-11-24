package ca.yorku.eecs3311.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CliOptionsTest {

    @Test
    void noArgumentsUsesDefaultsAndInteractiveMode() {
        CliOptions options = CliOptions.parse(new String[0]);

        assertEquals(Path.of("data/repository.json"), options.dataFile());
        assertEquals(Path.of(".env"), options.envFile());
        assertNull(options.inputFile());
        assertFalse(options.isScripted());
        assertFalse(options.echo());
        assertFalse(options.noLlm());
        assertFalse(options.help());
    }

    @Test
    void inputFileTurnsOnScriptedModeAndEcho() {
        CliOptions options = CliOptions.parse(new String[] {"--input", "task.txt"});

        assertEquals(Path.of("task.txt"), options.inputFile());
        assertTrue(options.isScripted());
        assertTrue(options.echo());
    }

    @Test
    void dashReadsCommandsFromStandardInput() {
        CliOptions options = CliOptions.parse(new String[] {"-i", "-"});

        assertNull(options.inputFile());
        assertTrue(options.isScripted());
    }

    @Test
    void parsesShortAndLongForms() {
        CliOptions options = CliOptions.parse(new String[] {
                "-i", "task.txt", "-d", "data/demo.json", "--env", "local.env",
                "--api-key", "key", "--llm-api", "https://example.com", "--model", "m"});

        assertEquals(Path.of("data/demo.json"), options.dataFile());
        assertEquals(Path.of("local.env"), options.envFile());
        assertEquals("key", options.apiKey());
        assertEquals("https://example.com", options.baseUrl());
        assertEquals("m", options.model());
    }

    @Test
    void baseUrlIsAnAliasForLlmApi() {
        assertEquals("https://example.com",
                CliOptions.parse(new String[] {"--base-url", "https://example.com"}).baseUrl());
    }

    @Test
    void singlePositionalArgumentIsTheDataFile() {
        assertEquals(Path.of("data/demo.json"),
                CliOptions.parse(new String[] {"data/demo.json"}).dataFile());
    }

    @Test
    void helpAndNoLlmFlags() {
        assertTrue(CliOptions.parse(new String[] {"--help"}).help());
        assertTrue(CliOptions.parse(new String[] {"-h"}).help());
        assertTrue(CliOptions.parse(new String[] {"--no-llm"}).noLlm());
        assertTrue(CliOptions.parse(new String[] {"--echo"}).echo());
    }

    @Test
    void rejectsUnknownOption() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--verbose"}));
    }

    @Test
    void rejectsOptionWithoutValue() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--input"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--api-key", ""}));
    }

    @Test
    void rejectsRepeatedInputOrData() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"-i", "a.txt", "-i", "b.txt"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"-d", "a.json", "b.json"}));
    }

    @Test
    void rejectsNoLlmCombinedWithLlmSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[] {"--no-llm", "--api-key", "key"}));
    }
}
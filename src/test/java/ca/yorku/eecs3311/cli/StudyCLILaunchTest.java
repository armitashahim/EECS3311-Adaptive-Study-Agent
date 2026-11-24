package ca.yorku.eecs3311.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the standalone entry point: java -jar Agent.jar [options]. */
class StudyCLILaunchTest {
    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int launch(String stdin, String... args) {
        return StudyCLI.launch(args,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void runsCommandsFromInputFileAndEchoesThem() throws IOException {
        Path task = directory.resolve("task.txt");
        Files.writeString(task, """
                # set up a course
                course-add "Data Structures"

                courses
                exit
                """);

        int status = launch("", "--no-llm", "--input", task.toString(),
                "--data", directory.resolve("data.json").toString());

        assertEquals(0, status);
        assertTrue(output().contains("# set up a course"));
        assertTrue(output().contains("> course-add \"Data Structures\""));
        assertTrue(output().contains("Course added."));
        assertTrue(output().contains("Data Structures"));
        assertTrue(Files.exists(directory.resolve("data.json")));
    }

    @Test
    void readsCommandsFromStandardInputWithDash() {
        int status = launch("course-add Algebra\ncourses\n", "--no-llm", "-i", "-",
                "-d", directory.resolve("data.json").toString());

        assertEquals(0, status);
        assertTrue(output().contains("Algebra"));
    }

    @Test
    void failingCommandGivesExitStatusOne() {
        int status = launch("tasks 5\ncourses\n", "--no-llm", "-i", "-",
                "-d", directory.resolve("data.json").toString());

        assertEquals(1, status);
        assertTrue(output().contains("Error:"));
    }

    @Test
    void unknownCommandGivesExitStatusOne() {
        assertEquals(1, launch("dance\n", "--no-llm", "-i", "-",
                "-d", directory.resolve("data.json").toString()));
    }

    @Test
    void missingInputFileGivesExitStatusTwo() {
        int status = launch("", "--no-llm", "--input", directory.resolve("none.txt").toString(),
                "--data", directory.resolve("data.json").toString());

        assertEquals(2, status);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("input file not found"));
    }

    @Test
    void invalidOptionGivesExitStatusTwo() {
        assertEquals(2, launch("", "--unknown"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Unknown option"));
    }

    @Test
    void helpPrintsUsageAndDoesNotCreateData() {
        Path data = directory.resolve("data.json");

        assertEquals(0, launch("", "--help", "--data", data.toString()));
        assertTrue(output().contains("Usage: java -jar Agent.jar"));
        assertFalse(Files.exists(data));
    }

    @Test
    void aiCommandThatCannotReachTheModelFails() {
        // The unreachable local URL keeps the test offline even if LLM_API_KEY is set
        // in the real environment.
        int status = launch("plan-generate next\n", "-i", "-",
                "--env", directory.resolve("missing.env").toString(),
                "--llm-api", "http://127.0.0.1:9",
                "-d", directory.resolve("data.json").toString());

        assertEquals(1, status);
        assertTrue(output().contains("> plan-generate next"));
    }
}
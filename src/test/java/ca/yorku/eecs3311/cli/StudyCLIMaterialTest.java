package ca.yorku.eecs3311.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.material.TestPdfs;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyCLIMaterialTest {
    @TempDir
    Path directory;

    private StudyApplication application;
    private Course course;

    @BeforeEach
    void setUp() throws IOException {
        application = new StudyApplication(new LocalRepository(directory.resolve("repository.json")));
        course = new Course("Algorithms");
        application.saveCourse(course);
    }

    private String run(String... lines) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(
                (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        new StudyCLI(application, in, out, Clock.systemDefaultZone()).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void importedMaterialIsListedShownAndRemoved() throws IOException {
        Path pdf = TestPdfs.create(directory.resolve("lecture 1.pdf"), "Quicksort picks a pivot.");

        String out = run("materials 1", "material-import 1 \"Lecture 1\" \"" + pdf + "\"",
                "materials 1", "material-show 1 1", "material-remove 1 1", "materials 1");

        assertTrue(out.contains("No materials in Algorithms."));
        assertTrue(out.contains("Imported 'Lecture 1'"));
        assertTrue(out.contains("1. Lecture 1 ("));
        assertTrue(out.contains("Quicksort picks a pivot."));
        assertTrue(out.contains("Material removed."));
        assertEquals(0, application.getMaterials(course.getId()).size());
    }

    @Test
    void longMaterialsArePreviewedWithTheRemainderCounted() throws IOException {
        Path pdf = TestPdfs.create(directory.resolve("long.pdf"), "y".repeat(1500));

        String out = run("material-import 1 Long \"" + pdf + "\"", "material-show 1 1");

        assertTrue(out.contains("..."));
        assertTrue(out.contains("(700 more characters)"));
    }

    @Test
    void failedImportsExplainWhyAndSaveNothing() throws IOException {
        Path blank = TestPdfs.create(directory.resolve("blank.pdf"), "");
        Path text = directory.resolve("notes.txt");
        Files.writeString(text, "plain", StandardCharsets.UTF_8);

        String out = run("material-import 1 Scanned \"" + blank + "\"",
                "material-import 1 Notes \"" + text + "\"",
                "material-import 1 Missing \"" + directory.resolve("nope.pdf") + "\"");

        assertTrue(out.contains("Import failed: The PDF contains no text."));
        assertTrue(out.contains("Import failed: Only PDF files are supported."));
        assertTrue(out.contains("Import failed: The file could not be found: nope.pdf"));
        assertTrue(out.contains("No material was saved."));
        assertTrue(application.getMaterials(course.getId()).isEmpty());
    }

    @Test
    void badNumbersAndMissingArgumentsAreReported() throws IOException {
        String out = run("materials 9", "material-show 1 4", "material-remove 1 1",
                "material-import 1 OnlyTitle", "material-import 1 \"\" \"" + directory + "\"");

        assertTrue(out.contains("There is no course number 9"));
        assertTrue(out.contains("There is no material number 4"));
        assertTrue(out.contains("Usage: material-import"));
        assertTrue(out.contains("Error:"));
    }

    @Test
    void helpMentionsTheMaterialCommands() throws IOException {
        String out = run("help");

        assertTrue(out.contains("material-import"));
        assertTrue(out.contains("material-show"));
    }
}
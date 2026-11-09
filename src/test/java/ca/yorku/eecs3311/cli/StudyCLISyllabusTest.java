package ca.yorku.eecs3311.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.application.ScriptedLlm;
import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.material.TestPdfs;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyCLISyllabusTest {
    private static final String REPLY = "{\"tasks\":[{\"title\":\"Assignment 1\",\"type\":\"Assignment\","
            + "\"deadline\":\"2030-06-01\"},{\"title\":\"Midterm\",\"type\":\"Exam\",\"deadline\":null}]}";

    @TempDir
    Path directory;

    private LocalRepository repository;
    private Course course;
    private Path pdf;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalRepository(directory.resolve("repository.json"));
        course = new Course("Algorithms");
        repository.saveCourse(course);
        pdf = TestPdfs.create(directory.resolve("syllabus.pdf"), "Assignment 1 due June 1");
    }

    private String run(ScriptedLlm llm, String... lines) throws IOException {
        StudyApplication application = new StudyApplication(repository, Clock.systemDefaultZone(), llm);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(
                (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        new StudyCLI(application, in, out, Clock.systemDefaultZone()).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void draftsAreReviewedCompletedAndThenSaved() throws IOException {
        String out = run(new ScriptedLlm(REPLY), "syllabus-import 1 \"" + pdf + "\"",
                "syllabus-confirm", "draft-set all priority 2", "draft-set all minutes 60",
                "draft-set 2 deadline 2030-06-10 09:00", "drafts", "syllabus-confirm", "tasks 1");

        assertTrue(out.contains("Found 2 possible tasks for Algorithms (not saved yet)."));
        assertTrue(out.contains("1. Assignment 1 [Assignment] due 2030-06-01 23:59, priority ?, duration ?"));
        assertTrue(out.contains("2. Midterm [Exam] due ?, priority ?, duration ?"));
        assertTrue(out.contains("Error: Task 1 ('Assignment 1') still needs: priority, duration."));
        assertTrue(out.contains("Every task is complete. Type syllabus-confirm to save them."));
        assertTrue(out.contains("Saved 2 task(s)."));
        assertTrue(out.contains("1. Assignment 1 [Assignment] due 2030-06-01 23:59, priority 2, "
                + "estimate 60 min, remaining 60 min"));
        assertEquals(2, repository.getTasks().size());
    }

    @Test
    void draftsCanBeEditedRemovedAndCancelled() throws IOException {
        String out = run(new ScriptedLlm(REPLY), "syllabus-import 1 \"" + pdf + "\"",
                "draft-set 1 title \"Assignment One\"", "draft-set 1 type Project",
                "draft-remove 2", "drafts", "syllabus-cancel", "drafts", "syllabus-confirm");

        assertTrue(out.contains("1. Assignment One [Project] due 2030-06-01 23:59"));
        assertTrue(out.contains("Draft removed."));
        assertTrue(out.contains("Proposed tasks discarded. Nothing was saved."));
        assertTrue(out.contains("There are no proposed tasks."));
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void badDraftCommandsAreReported() throws IOException {
        String out = run(new ScriptedLlm(REPLY), "syllabus-import 1 \"" + pdf + "\"",
                "draft-set 9 priority 2", "draft-set 1 priority 7", "draft-set 1 colour red",
                "draft-set 1 deadline soon", "draft-set 1 minutes lots", "draft-set 1",
                "draft-remove 5");

        assertTrue(out.contains("There is no draft number 9"));
        assertTrue(out.contains("Priority must be 1, 2, or 3."));
        assertTrue(out.contains("Unknown field 'colour'"));
        assertTrue(out.contains("Use the date and time format"));
        assertTrue(out.contains("'lots' is not a valid minutes"));
        assertTrue(out.contains("Usage: draft-set"));
        assertTrue(out.contains("There is no draft number 5"));
    }

    @Test
    void aFailingAssistantOrBadPdfExplainsAndSavesNothing() throws IOException {
        Path blank = TestPdfs.create(directory.resolve("blank.pdf"), "");

        String failing = run(new ScriptedLlm(), "syllabus-import 1 \"" + pdf + "\"");
        String scanned = run(new ScriptedLlm(REPLY), "syllabus-import 1 \"" + blank + "\"");
        String noAssistant = run(null, "syllabus-import 1 \"" + pdf + "\"");

        assertTrue(failing.contains("Could not read tasks from the syllabus:"));
        assertTrue(failing.contains("No tasks were saved. You can add tasks yourself with task-add."));
        assertTrue(scanned.contains("Import failed: The PDF contains no text."));
        assertTrue(noAssistant.contains("not configured"));
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void helpMentionsTheSyllabusCommands() throws IOException {
        String out = run(null, "help");

        assertTrue(out.contains("syllabus-import"));
        assertTrue(out.contains("draft-set"));
        assertTrue(out.contains("syllabus-confirm"));
    }
}
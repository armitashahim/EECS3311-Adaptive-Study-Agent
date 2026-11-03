package ca.yorku.eecs3311.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyCLITest {
    // Wednesday noon; the week starts Monday May 13.
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 15, 12, 0);
    private static final LocalDate WEEK = LocalDate.of(2030, 5, 13);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @TempDir
    Path directory;

    private LocalRepository repository;
    private StudyApplication application;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalRepository(directory.resolve("repository.json"));
        application = new StudyApplication(repository, CLOCK);
    }

    // ===== General =====

    @Test
    void helpListsTheCommandGroups() throws IOException {
        String out = run("help");

        assertTrue(out.contains("course-add"));
        assertTrue(out.contains("period-add"));
        assertTrue(out.contains("record"));
        assertTrue(out.contains("duration-accept"));
    }

    @Test
    void unknownCommandsAreReportedAndTheLoopContinues() throws IOException {
        String out = run("dance", "courses");

        assertTrue(out.contains("Unknown command 'dance'"));
        assertTrue(out.contains("No courses yet"));
    }

    @Test
    void exitStopsReadingFurtherCommands() throws IOException {
        String out = run("exit", "course-add Ignored");

        assertTrue(out.contains("Goodbye."));
        assertTrue(application.getCourses().isEmpty());
    }

    @Test
    void endOfInputEndsTheSession() throws IOException {
        String out = run("courses");

        assertTrue(out.contains("No courses yet"));
    }

    @Test
    void constructorRejectsNullDependencies() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        PrintStream out = new PrintStream(new ByteArrayOutputStream());

        assertThrows(IllegalArgumentException.class,
                () -> new StudyCLI(null, in, out, CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyCLI(application, null, out, CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyCLI(application, in, null, CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyCLI(application, in, out, null));
    }

    @Test
    void tokenizerKeepsQuotedTextTogether() {
        assertEquals(List.of("task-add", "1", "Read chapter 3", "Reading"),
                StudyCLI.tokenize("task-add 1 \"Read chapter 3\"   Reading"));
        assertEquals(List.of("a", ""), StudyCLI.tokenize("a \"\""));
        assertTrue(StudyCLI.tokenize("   ").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> StudyCLI.tokenize("a \"b"));
    }

    @Test
    void unclosedQuoteIsReportedAsAnError() throws IOException {
        assertTrue(run("course-add \"Broken").contains("never closed"));
    }

    // ===== F01 =====

    @Test
    void coursesCanBeAddedListedRenamedAndRemoved() throws IOException {
        String out = run("course-add Algorithms", "course-add \"Operating Systems\"", "courses",
                "course-rename 1 \"Advanced Algorithms\"", "courses", "course-remove 2",
                "courses");

        assertTrue(out.contains("1. Algorithms"));
        assertTrue(out.contains("2. Operating Systems"));
        assertTrue(out.contains("1. Advanced Algorithms"));
        assertEquals(1, application.getCourses().size());
    }

    @Test
    void courseNumbersMustExist() throws IOException {
        String out = run("course-add Algorithms", "course-remove 5", "course-remove abc");

        assertTrue(out.contains("There is no course number 5"));
        assertTrue(out.contains("'abc' is not a valid course number"));
        assertEquals(1, application.getCourses().size());
    }

    @Test
    void tasksCanBeAddedListedEditedAndRemoved() throws IOException {
        String out = run("course-add Algorithms",
                "task-add 1 \"Assignment 1\" Coursework 2030-05-30 23:59 2 90",
                "tasks 1",
                "task-edit 1 1 \"Assignment 1b\" Coursework 2030-06-01 12:00 3",
                "tasks 1",
                "task-remove 1 1",
                "tasks 1");

        assertTrue(out.contains("1. Assignment 1 [Coursework] due 2030-05-30 23:59, "
                + "priority 2, estimate 90 min, remaining 90 min"));
        assertTrue(out.contains("1. Assignment 1b [Coursework] due 2030-06-01 12:00, priority 3"));
        assertTrue(out.contains("Task removed."));
        assertTrue(out.contains("No tasks in Algorithms."));
        Course course = application.getCourses().get(0);
        assertTrue(application.getTasks(course.getId()).isEmpty());
    }

    @Test
    void invalidTaskInputIsRejectedWithAMessageAndSavesNothing() throws IOException {
        String out = run("course-add Algorithms",
                "task-add 1 Essay Writing tomorrow 10:00 2 60",
                "task-add 1 Essay Writing 2030-05-30 23:59 two 60",
                "task-add 1 Essay Writing 2030-05-30 23:59 2 0",
                "task-add 1 Essay",
                "task-add 9 Essay Writing 2030-05-30 23:59 2 60");

        assertTrue(out.contains("Use the date and time format YYYY-MM-DD HH:mm"));
        assertTrue(out.contains("'two' is not a valid priority"));
        assertTrue(out.contains("Usage: task-add"));
        assertTrue(out.contains("There is no course number 9"));
        Course course = application.getCourses().get(0);
        assertTrue(application.getTasks(course.getId()).isEmpty());
    }

    @Test
    void courseWithARemovedTaskCannotBeDeleted() throws IOException {
        String out = run("course-add Algorithms",
                "task-add 1 Essay Writing 2030-05-30 23:59 2 60", "task-remove 1 1",
                "course-remove 1");

        assertTrue(out.contains("Error:"));
        assertEquals(1, application.getCourses().size());
    }

    @Test
    void dataSurvivesRestartingTheCli() throws IOException {
        run("course-add Algorithms", "task-add 1 Essay Writing 2030-05-30 23:59 2 60");

        LocalRepository reloaded = new LocalRepository(directory.resolve("repository.json"));
        StudyApplication fresh = new StudyApplication(reloaded, CLOCK);

        assertEquals(1, fresh.getCourses().size());
        assertEquals(1, fresh.getTasks(fresh.getCourses().get(0).getId()).size());
    }

    // ===== F02 =====

    @Test
    void periodsAreListedInWeekOrderAndCanBeRemoved() throws IOException {
        String out = run("period-add availability tue 14:00 16:00",
                "period-add availability Monday 09:00 11:00",
                "period-add commitment mon 10:00 12:00",
                "periods", "period-remove 1", "periods");

        String[] lines = out.split("\n");
        assertTrue(out.contains("1. availability MONDAY 09:00-11:00"));
        assertTrue(out.contains("2. commitment MONDAY 10:00-12:00"));
        assertTrue(out.contains("3. availability TUESDAY 14:00-16:00"));
        assertTrue(lines.length > 5);
        assertEquals(2, application.getPeriods().size());
        assertTrue(out.contains("1. commitment MONDAY 10:00-12:00"));
    }

    @Test
    void overlappingAvailabilityAndBadPeriodInputAreRejected() throws IOException {
        String out = run("period-add availability mon 09:00 11:00",
                "period-add availability mon 10:00 12:00",
                "period-add availability someday 09:00 11:00",
                "period-add availability mon 9am 11:00",
                "period-add availability mon 11:00 09:00",
                "period-add lunch mon 09:00 11:00",
                "period-remove 7");

        assertTrue(out.contains("Error:"));
        assertTrue(out.contains("'someday' is not a day of the week."));
        assertTrue(out.contains("Use the time format HH:mm"));
        assertTrue(out.contains("There is no period number 7"));
        assertEquals(1, application.getPeriods().size());
    }

    // ===== F06 =====

    @Test
    void planCommandShowsNothingWhenThereIsNoPlan() throws IOException {
        assertTrue(run("plan").contains("No plan for the week of 2030-05-13."));
        assertTrue(run("plan 2030-05-17").contains("No plan for the week of 2030-05-13."));
        assertTrue(run("plan not-a-date").contains("Error:"));
    }

    @Test
    void planCommandShowsSessionsFlagsAndUnscheduledWork() throws IOException {
        AcademicTask task = seedTask("Assignment 1", 60);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(session(task, 14, 9));
        plan.addSession(session(task, 16, 9));
        plan.setPriorityExplanation("Assignment 1 is due first.");
        plan.setUnscheduledMinutes(java.util.Map.of(task.getId(), 25));
        plan.accept();
        repository.savePlan(plan);

        String out = run("plan");

        assertTrue(out.contains("Week of 2030-05-13 (accepted)"));
        assertTrue(out.contains("Why: Assignment 1 is due first."));
        assertTrue(out.contains("1. Assignment 1  2030-05-14 09:00 to 10:00  [scheduled]"));
        assertTrue(out.contains("2. Assignment 1  2030-05-16 09:00 to 10:00  [scheduled]"));
        assertTrue(out.contains("Unscheduled: Assignment 1 - 25 min"));
        assertFalse(out.contains("NEEDS REVISION"));
    }

    @Test
    void recordingProgressUpdatesTheSessionAndFlagsThePlan() throws IOException {
        AcademicTask task = seedTask("Assignment 1", 60);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(session(task, 14, 9));
        plan.accept();
        repository.savePlan(plan);

        String out = run("record 2030-05-15 1 partial 30 45", "plan");

        assertTrue(out.contains("Progress recorded."));
        assertTrue(out.contains("[partial, 30 min]"));
        assertTrue(out.contains("NEEDS REVISION"));
        assertEquals(45, repository.getTask(task.getId()).getRemainingMinutes());
    }

    @Test
    void finishedKeywordCompletesTheTask() throws IOException {
        AcademicTask task = seedTask("Assignment 1", 60);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(session(task, 14, 9));
        repository.savePlan(plan);

        run("record 2030-05-13 1 completed 50 0 finished");

        assertTrue(repository.getTask(task.getId()).isCompleted());
    }

    @Test
    void recordingErrorsAreReportedAndChangeNothing() throws IOException {
        AcademicTask task = seedTask("Assignment 1", 60);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(session(task, 16, 9));
        repository.savePlan(plan);

        String out = run("record 2030-05-13 1 completed 30 20",
                "record 2030-05-13 4 completed 30 20",
                "record 2030-05-13 1 completed lots 20",
                "record 2030-05-13 1");

        assertTrue(out.contains("Error:"));
        assertTrue(out.contains("There is no session number 4"));
        assertTrue(out.contains("'lots' is not a valid minutes"));
        assertTrue(out.contains("Usage: record"));
        assertEquals("scheduled",
                repository.getSessionsForTask(task.getId()).get(0).getStatus());
    }

    // ===== F07 =====

    @Test
    void durationShowsTheOriginalEstimateWithoutHistoryThenAcceptsAnotherValue()
            throws IOException {
        String out = run("course-add Algorithms",
                "task-add 1 Essay Writing 2030-05-30 23:59 2 90",
                "duration 1 1", "duration-accept 1 1 60", "tasks 1");

        assertTrue(out.contains("Suggested: 90 min (your original estimate; only 0 finished "
                + "'Writing' tasks so far"));
        assertTrue(out.contains("Duration accepted."));
        assertTrue(out.contains("estimate 60 min, remaining 60 min"));
    }

    @Test
    void durationUsesHistoryWhenThreeFinishedTasksExist() throws IOException {
        Course course = new Course("Algorithms");
        application.saveCourse(course);
        int[] actuals = {120, 60, 90};
        for (int i = 0; i < actuals.length; i++) {
            AcademicTask done = new AcademicTask(course, "Done " + i, "Writing",
                    LocalDateTime.of(2030, 5, 30, 23, 59), 2, 60);
            application.saveTask(course.getId(), done);
            StudySession session = session(done, 14, 9 + i);
            StudyPlan plan = repository.getPlans(WEEK).isEmpty()
                    ? new StudyPlan(WEEK) : repository.getPlans(WEEK).get(0);
            plan.addSession(session);
            repository.savePlan(plan);
            application.recordProgress(session, "completed", actuals[i], true, 0);
        }
        application.saveTask(course.getId(), new AcademicTask(course, "New essay", "Writing",
                LocalDateTime.of(2030, 5, 30, 23, 59), 2, 60));

        String out = run("duration 1 4");

        assertTrue(out.contains("Suggested: 90 min, based on 3 finished tasks of type 'Writing'."));
    }

    @Test
    void invalidDurationInputIsRejected() throws IOException {
        String out = run("course-add Algorithms",
                "task-add 1 Essay Writing 2030-05-30 23:59 2 90",
                "duration-accept 1 1 0", "duration-accept 1 1 lots", "duration 1 3",
                "duration 1");

        assertTrue(out.contains("Error:"));
        assertTrue(out.contains("'lots' is not a valid minutes"));
        assertTrue(out.contains("There is no task number 3"));
        assertTrue(out.contains("Usage: duration N T"));
    }

    // ===== Helpers =====

    private String run(String... lines) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(
                (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        new StudyCLI(application, in, out, CLOCK).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private AcademicTask seedTask(String title, int minutes) throws IOException {
        Course course = new Course("Algorithms");
        application.saveCourse(course);
        AcademicTask task = new AcademicTask(course, title, "Coursework",
                LocalDateTime.of(2030, 5, 30, 23, 59), 2, minutes);
        application.saveTask(course.getId(), task);
        return task;
    }

    private static StudySession session(AcademicTask task, int dayOfMay, int hour) {
        LocalDateTime start = LocalDateTime.of(2030, 5, dayOfMay, hour, 0);
        return new StudySession(task.getId(), start, start.plusHours(1));
    }
}
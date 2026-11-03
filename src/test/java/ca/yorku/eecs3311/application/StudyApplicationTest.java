package ca.yorku.eecs3311.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyApplicationTest {
    // Wednesday noon. WEEK (Monday May 13) is the current week.
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 15, 12, 0);
    private static final LocalDate WEEK = LocalDate.of(2030, 5, 13);
    private static final LocalDate ENDED_WEEK = LocalDate.of(2030, 4, 29);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2030, 5, 30, 23, 59);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @TempDir
    Path directory;

    private LocalRepository repository;
    private StudyApplication application;
    private Course course;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalRepository(directory.resolve("repository.json"));
        application = new StudyApplication(repository, CLOCK);
        course = new Course("Algorithms");
        application.saveCourse(course);
    }

    // ===== Construction =====

    @Test
    void constructorRejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new StudyApplication(null));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyApplication(repository, null));
    }

    // ===== F01: courses and tasks =====

    @Test
    void savedCoursesAreListedAndRenamesKeepTheId() throws IOException {
        course.rename("Advanced Algorithms");
        application.saveCourse(course);

        assertEquals(1, application.getCourses().size());
        assertEquals("Advanced Algorithms", application.getCourses().get(0).getName());
        assertEquals(course.getId(), application.getCourses().get(0).getId());
        assertThrows(IllegalArgumentException.class, () -> application.saveCourse(null));
    }

    @Test
    void courseWithNoTasksCanBeRemoved() throws IOException {
        Course other = new Course("Databases");
        application.saveCourse(other);

        application.removeCourse(other.getId());

        assertEquals(1, application.getCourses().size());
    }

    @Test
    void savedTaskIsListedForItsCourse() throws IOException {
        AcademicTask task = addTask("Assignment 1", "Coursework", 60);

        List<AcademicTask> tasks = application.getTasks(course.getId());

        assertEquals(1, tasks.size());
        assertEquals(task.getId(), tasks.get(0).getId());
    }

    @Test
    void saveTaskRejectsMismatchedMissingOrUnknownCourse() throws IOException {
        AcademicTask task = new AcademicTask(
                course, "Assignment", "Coursework", DEADLINE, 2, 60);
        Course ghost = new Course("Ghost");

        assertThrows(IllegalArgumentException.class,
                () -> application.saveTask("another-course-id", task));
        assertThrows(IllegalArgumentException.class, () -> application.saveTask(" ", task));
        assertThrows(IllegalArgumentException.class,
                () -> application.saveTask(course.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> application.saveTask(ghost.getId(),
                new AcademicTask(ghost, "Task", "Coursework", DEADLINE, 2, 60)));
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void editingATaskKeepsItsIdentity() throws IOException {
        AcademicTask task = addTask("Assignment 1", "Coursework", 60);

        AcademicTask edited = repository.getTask(task.getId());
        edited.updateDetails("Assignment 1 (revised)", "Coursework", DEADLINE, 3);
        application.saveTask(course.getId(), edited);

        List<AcademicTask> tasks = application.getTasks(course.getId());
        assertEquals(1, tasks.size());
        assertEquals(task.getId(), tasks.get(0).getId());
        assertEquals("Assignment 1 (revised)", tasks.get(0).getTitle());
    }

    @Test
    void taskCannotMoveToAnotherCourse() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        Course other = new Course("Databases");
        application.saveCourse(other);
        AcademicTask moved = new AcademicTask(task.getId(), other, "Assignment",
                "Coursework", DEADLINE, 2, 60, 60, 60, false, false);

        assertThrows(IllegalArgumentException.class,
                () -> application.saveTask(other.getId(), moved));
        assertEquals(course.getId(), repository.getTask(task.getId()).getCourse().getId());
    }

    @Test
    void changingASchedulingDetailFlagsTheAcceptedPlanThatSchedulesTheTask()
            throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        savePlan(true, WEEK, session(task, 16, 9));

        AcademicTask edited = repository.getTask(task.getId());
        edited.updateDetails("Assignment", "Coursework", DEADLINE.plusDays(1), 2);
        application.saveTask(course.getId(), edited);

        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void editingOnlyTheTitleOrAddingATaskDoesNotFlagPlans() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        savePlan(true, WEEK, session(task, 16, 9));

        AcademicTask edited = repository.getTask(task.getId());
        edited.updateDetails("Renamed", "Coursework", DEADLINE, 2);
        application.saveTask(course.getId(), edited);
        addTask("Another task", "Coursework", 30);

        assertFalse(repository.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void plansOfEndedWeeksAndUnacceptedPlansAreNotFlagged() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        savePlan(true, ENDED_WEEK, session(task, 4, 30, 9));
        savePlan(false, WEEK, session(task, 16, 9));

        AcademicTask edited = repository.getTask(task.getId());
        edited.updateDetails("Assignment", "Coursework", DEADLINE.plusDays(1), 2);
        application.saveTask(course.getId(), edited);

        assertFalse(repository.getPlans(ENDED_WEEK).get(0).needsRevision());
        assertFalse(repository.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void removingATaskArchivesItDropsFutureSessionsAndKeepsThePast() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        AcademicTask other = addTask("Other", "Coursework", 60);
        StudySession past = session(task, 14, 9);
        StudySession future = session(task, 16, 9);
        StudySession othersFuture = session(other, 17, 9);
        savePlan(true, WEEK, past, future, othersFuture);

        application.removeTask(task.getId());

        assertTrue(repository.getTask(task.getId()).isArchived());
        StudyPlan plan = repository.getPlans(WEEK).get(0);
        assertEquals(2, plan.getSessions().size());
        assertTrue(plan.getSessions().stream().anyMatch(s -> s.getId().equals(past.getId())));
        assertTrue(plan.getSessions().stream()
                .anyMatch(s -> s.getId().equals(othersFuture.getId())));
        assertTrue(plan.needsRevision());
        assertEquals(1, repository.getSessionsForTask(task.getId()).size());
    }

    @Test
    void removedTasksAreHiddenCannotBeEditedAndBlockCourseDeletion() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);

        application.removeTask(task.getId());

        assertTrue(application.getTasks(course.getId()).isEmpty());
        AcademicTask archived = repository.getTask(task.getId());
        assertThrows(IllegalStateException.class,
                () -> application.saveTask(course.getId(), archived));
        assertThrows(IllegalArgumentException.class,
                () -> application.removeCourse(course.getId()));
    }

    @Test
    void removingAnUnknownTaskIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> application.removeTask("missing"));
        assertThrows(IllegalArgumentException.class, () -> application.removeTask(null));
    }

    @Test
    void removingATaskAlsoClearsItsUnscheduledMinutes() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.setUnscheduledMinutes(java.util.Map.of(task.getId(), 30));
        plan.accept();
        repository.savePlan(plan);

        application.removeTask(task.getId());

        assertTrue(repository.getPlans(WEEK).get(0).getUnscheduledMinutes().isEmpty());
    }

    // ===== F02: weekly availability and commitments =====

    @Test
    void overlappingAvailabilityIsRejectedButCommitmentsMayOverlap() throws IOException {
        application.savePeriod(period(DayOfWeek.MONDAY, 9, 11, "availability"));

        assertThrows(IllegalArgumentException.class, () -> application.savePeriod(
                period(DayOfWeek.MONDAY, 10, 12, "availability")));
        application.savePeriod(period(DayOfWeek.MONDAY, 10, 12, "commitment"));
        application.savePeriod(period(DayOfWeek.TUESDAY, 10, 12, "availability"));

        assertEquals(3, application.getPeriods().size());
    }

    @Test
    void editingAPeriodDoesNotCountAsOverlappingItself() throws IOException {
        WeeklyPeriod original = period(DayOfWeek.MONDAY, 9, 11, "availability");
        application.savePeriod(original);

        application.savePeriod(new WeeklyPeriod(original.getId(), DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0), "availability"));

        assertEquals(1, application.getPeriods().size());
        assertEquals(LocalTime.of(12, 0), application.getPeriods().get(0).getEndTime());
    }

    @Test
    void savingAPeriodFlagsAcceptedPlansThatCanStillBeRevised() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        savePlan(true, WEEK, session(task, 16, 9));
        savePlan(true, ENDED_WEEK, session(task, 4, 30, 9));

        application.savePeriod(period(DayOfWeek.MONDAY, 9, 11, "availability"));

        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());
        assertFalse(repository.getPlans(ENDED_WEEK).get(0).needsRevision());
    }

    @Test
    void removingAPeriodFlagsAcceptedPlans() throws IOException {
        WeeklyPeriod period = period(DayOfWeek.MONDAY, 9, 11, "availability");
        application.savePeriod(period);
        savePlan(true, WEEK);

        application.removePeriod(period.getId());

        assertTrue(application.getPeriods().isEmpty());
        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void failedPeriodRemovalFlagsNothing() throws IOException {
        savePlan(true, WEEK);

        assertThrows(IllegalArgumentException.class,
                () -> application.removePeriod("missing"));

        assertFalse(repository.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void periodsSurviveReloadingTheRepository() throws IOException {
        application.savePeriod(period(DayOfWeek.FRIDAY, 13, 15, "commitment"));

        LocalRepository reloaded = new LocalRepository(directory.resolve("repository.json"));

        assertEquals(1, reloaded.getPeriods().size());
        assertThrows(IllegalArgumentException.class, () -> application.savePeriod(null));
    }

    // ===== F06: study progress =====

    @Test
    void recordingProgressUpdatesSessionTaskAndFlagsThePlan() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession session = session(task, 14, 9);
        savePlan(true, WEEK, session);

        application.recordProgress(session, "partial", 30, false, 45);

        LocalRepository reloaded = new LocalRepository(directory.resolve("repository.json"));
        StudySession saved = reloaded.getSessionsForTask(task.getId()).get(0);
        assertEquals("partial", saved.getStatus());
        assertEquals(30, saved.getActualMinutes());
        assertEquals(45, reloaded.getTask(task.getId()).getRemainingMinutes());
        assertFalse(reloaded.getTask(task.getId()).isCompleted());
        assertTrue(reloaded.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void finishingATaskSetsItsRemainingEffortToZero() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession session = session(task, 14, 9);
        savePlan(true, WEEK, session);

        application.recordProgress(session, "completed", 55, true, 0);

        assertTrue(repository.getTask(task.getId()).isCompleted());
        assertEquals(0, repository.getTask(task.getId()).getRemainingMinutes());
    }

    @Test
    void missedSessionRecordsZeroMinutes() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession session = session(task, 14, 9);
        savePlan(true, WEEK, session);

        application.recordProgress(session, "missed", 40, false, 60);

        StudySession saved = repository.getSessionsForTask(task.getId()).get(0);
        assertEquals("missed", saved.getStatus());
        assertEquals(0, saved.getActualMinutes());
    }

    @Test
    void editingARecordReplacesThePreviousOutcomeWithoutDoubleCounting()
            throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession session = session(task, 14, 9);
        savePlan(true, WEEK, session);

        application.recordProgress(session, "completed", 50, false, 20);
        application.recordProgress(session, "partial", 20, false, 40);

        List<StudySession> sessions = repository.getSessionsForTask(task.getId());
        assertEquals(1, sessions.size());
        assertEquals("partial", sessions.get(0).getStatus());
        assertEquals(20, sessions.get(0).getActualMinutes());
        assertEquals(40, repository.getTask(task.getId()).getRemainingMinutes());
    }

    @Test
    void sessionThatHasNotStartedCannotBeRecorded() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession future = session(task, 16, 9);
        savePlan(true, WEEK, future);

        assertThrows(IllegalStateException.class,
                () -> application.recordProgress(future, "completed", 30, false, 20));

        assertEquals("scheduled",
                repository.getSessionsForTask(task.getId()).get(0).getStatus());
        assertEquals(60, repository.getTask(task.getId()).getRemainingMinutes());
    }

    @Test
    void invalidProgressChangesNothing() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession session = session(task, 14, 9);
        savePlan(true, WEEK, session);

        assertThrows(IllegalArgumentException.class,
                () -> application.recordProgress(session, "partial", -5, false, 30));
        assertThrows(IllegalArgumentException.class,
                () -> application.recordProgress(session, "partial", 20, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> application.recordProgress(session, "bogus", 20, false, 30));

        assertEquals("scheduled",
                repository.getSessionsForTask(task.getId()).get(0).getStatus());
        assertEquals(60, repository.getTask(task.getId()).getRemainingMinutes());
        assertFalse(repository.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void completedTaskCannotBeReopenedByEditingARecord() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession session = session(task, 14, 9);
        savePlan(true, WEEK, session);
        application.recordProgress(session, "completed", 55, true, 0);

        assertThrows(IllegalStateException.class,
                () -> application.recordProgress(session, "partial", 20, false, 30));

        assertTrue(repository.getTask(task.getId()).isCompleted());
        assertEquals(55, repository.getSessionsForTask(task.getId()).get(0).getActualMinutes());
    }

    @Test
    void unknownOrMissingSessionsAreRejected() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession unsaved = session(task, 14, 9);

        assertThrows(IllegalArgumentException.class,
                () -> application.recordProgress(unsaved, "completed", 30, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> application.recordProgress(null, "completed", 30, true, 0));
    }

    @Test
    void onlyAcceptedRevisablePlansThatScheduleTheTaskAreFlagged() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        AcademicTask other = addTask("Other", "Coursework", 60);
        StudySession session = session(task, 14, 9);
        savePlan(true, WEEK, session);
        savePlan(true, WEEK.plusDays(7), session(task, 21, 9));
        savePlan(true, WEEK.plusDays(14), session(other, 28, 9));

        application.recordProgress(session, "partial", 20, false, 40);

        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());
        assertTrue(repository.getPlans(WEEK.plusDays(7)).get(0).needsRevision());
        assertFalse(repository.getPlans(WEEK.plusDays(14)).get(0).needsRevision());
    }

    // ===== F07: adaptive duration estimates =====

    @Test
    void withoutHistoryTheSuggestionIsTheOriginalEstimate() throws IOException {
        AcademicTask task = addTask("New task", "Coursework", 60);

        DurationSuggestion suggestion = application.getDurationSuggestion(task);

        assertEquals(60, suggestion.minutes());
        assertEquals(0, suggestion.supportingTasks());
        assertFalse(suggestion.usedHistory());
    }

    @Test
    void threeFinishedTasksOfTheSameTypeProduceAHistoricalSuggestion() throws IOException {
        finishTask("Coursework", 60, 120, 9);
        finishTask("Coursework", 60, 60, 10);
        finishTask("Coursework", 60, 90, 11);
        AcademicTask task = addTask("New task", "Coursework", 60);

        DurationSuggestion suggestion = application.getDurationSuggestion(task);

        assertEquals(90, suggestion.minutes());
        assertEquals(3, suggestion.supportingTasks());
        assertTrue(suggestion.usedHistory());
        assertEquals(90, application.suggestDuration(task));
    }

    @Test
    void otherTypesAndUnusableRecordsDoNotCountAsHistory() throws IOException {
        finishTask("Exam", 60, 120, 9);
        finishTask("Exam", 60, 120, 10);
        finishTask("Exam", 60, 120, 11);
        finishTask("Coursework", 60, 120, 12);
        finishTask("Coursework", 60, 120, 13);
        finishTask("Coursework", 60, 0, 14);
        AcademicTask task = addTask("New task", "Coursework", 60);

        DurationSuggestion suggestion = application.getDurationSuggestion(task);

        assertEquals(60, suggestion.minutes());
        assertEquals(2, suggestion.supportingTasks());
        assertFalse(suggestion.usedHistory());
    }

    @Test
    void aTaskThatHasAlreadyStartedCannotBeEstimated() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 60);
        StudySession session = session(task, 14, 9);
        savePlan(false, WEEK, session);
        application.recordProgress(session, "partial", 30, false, 40);
        AcademicTask started = repository.getTask(task.getId());

        assertThrows(IllegalStateException.class,
                () -> application.getDurationSuggestion(started));
        assertThrows(IllegalStateException.class,
                () -> application.acceptDuration(started, 30));
        assertThrows(IllegalArgumentException.class,
                () -> application.getDurationSuggestion(null));
    }

    @Test
    void acceptingADurationKeepsTheOriginalEstimateAndFlagsPlans() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 90);
        savePlan(true, WEEK, session(task, 16, 9));

        application.acceptDuration(repository.getTask(task.getId()), 45);

        AcademicTask saved = repository.getTask(task.getId());
        assertEquals(90, saved.getOriginalEstimatedMinutes());
        assertEquals(45, saved.getAcceptedEstimatedMinutes());
        assertEquals(45, saved.getRemainingMinutes());
        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void acceptingADurationOnAnUnsavedTaskOnlyChangesTheObject() throws IOException {
        AcademicTask unsaved = new AcademicTask(
                course, "Draft", "Coursework", DEADLINE, 2, 60);

        application.acceptDuration(unsaved, 30);

        assertEquals(30, unsaved.getAcceptedEstimatedMinutes());
        assertEquals(60, unsaved.getOriginalEstimatedMinutes());
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void acceptingAnInvalidDurationIsRejectedAndChangesNothing() throws IOException {
        AcademicTask task = addTask("Assignment", "Coursework", 90);
        AcademicTask stored = repository.getTask(task.getId());

        assertThrows(IllegalArgumentException.class, () -> application.acceptDuration(stored, 0));
        assertThrows(IllegalArgumentException.class, () -> application.acceptDuration(null, 30));

        assertEquals(90, repository.getTask(task.getId()).getAcceptedEstimatedMinutes());
    }

    @Test
    void completedTasksCannotReceiveANewEstimate() throws IOException {
        AcademicTask task = finishTask("Coursework", 60, 60, 9);
        AcademicTask completed = repository.getTask(task.getId());

        assertThrows(IllegalStateException.class,
                () -> application.acceptDuration(completed, 30));
    }

    // ===== Helpers =====

    private AcademicTask addTask(String title, String type, int minutes) throws IOException {
        AcademicTask task = new AcademicTask(course, title, type, DEADLINE, 2, minutes);
        application.saveTask(course.getId(), task);
        return task;
    }

    private static StudySession session(AcademicTask task, int dayOfMay, int hour) {
        return session(task, 5, dayOfMay, hour);
    }

    private static StudySession session(AcademicTask task, int month, int day, int hour) {
        LocalDateTime start = LocalDateTime.of(2030, month, day, hour, 0);
        return new StudySession(task.getId(), start, start.plusHours(1));
    }

    private void savePlan(boolean accepted, LocalDate week, StudySession... sessions)
            throws IOException {
        StudyPlan plan = new StudyPlan(week);
        for (StudySession session : sessions) {
            plan.addSession(session);
        }
        if (accepted) {
            plan.accept();
        }
        repository.savePlan(plan);
    }

    // Creates a task, schedules one past session in the current week's plan, and records
    // it as finishing the task with the given actual minutes.
    private AcademicTask finishTask(String type, int original, int actual, int hour)
            throws IOException {
        AcademicTask task = addTask("Done at " + hour + " " + type, type, original);
        StudySession session = session(task, 14, hour);
        List<StudyPlan> existing = repository.getPlans(WEEK);
        StudyPlan plan = existing.isEmpty() ? new StudyPlan(WEEK) : existing.get(0);
        plan.addSession(session);
        repository.savePlan(plan);

        application.recordProgress(session, "completed", actual, true, 0);
        return task;
    }

    private static WeeklyPeriod period(DayOfWeek day, int startHour, int endHour, String type) {
        return new WeeklyPeriod(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), type);
    }
}
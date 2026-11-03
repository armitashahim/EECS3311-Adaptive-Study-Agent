package ca.yorku.eecs3311.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalRepositoryPlanningTest {
    private static final LocalDate WEEK = LocalDate.of(2030, 5, 13);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2030, 5, 30, 23, 59);

    // Valid start of a repository file with no courses or tasks.
    private static final String EMPTY = "{\"courses\":[],\"tasks\":[],";
    private static final String PLAN_START =
            "\"plans\":[{\"id\":\"pl1\",\"weekStartDate\":\"2030-05-13\",";
    private static final String PLAN_BODY =
            "\"priorityExplanation\":\"\",\"unscheduledMinutes\":{},\"sessions\":[]}]}";

    @TempDir
    Path directory;

    private Path file() {
        return directory.resolve("repository.json");
    }

    // ----- Weekly periods -----

    @Test
    void periodsPersistInOrderAndSavingAgainReplacesTheSamePeriod() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        WeeklyPeriod monday = new WeeklyPeriod(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0), "availability");
        WeeklyPeriod tuesday = new WeeklyPeriod(DayOfWeek.TUESDAY,
                LocalTime.of(13, 0), LocalTime.of(15, 0), "commitment");
        repository.savePeriod(monday);
        repository.savePeriod(tuesday);

        repository.savePeriod(new WeeklyPeriod(monday.getId(), DayOfWeek.MONDAY,
                LocalTime.of(10, 0), LocalTime.of(12, 0), "availability"));

        List<WeeklyPeriod> reloaded = new LocalRepository(file()).getPeriods();
        assertEquals(2, reloaded.size());
        assertEquals(monday.getId(), reloaded.get(0).getId());
        assertEquals(LocalTime.of(10, 0), reloaded.get(0).getStartTime());
        assertEquals(LocalTime.of(12, 0), reloaded.get(0).getEndTime());
        assertEquals(tuesday.getId(), reloaded.get(1).getId());
        assertEquals("commitment", reloaded.get(1).getType());
        assertEquals(DayOfWeek.TUESDAY, reloaded.get(1).getDayOfWeek());
    }

    @Test
    void deletingAPeriodPersistsAndUnknownOrBlankIdsAreRejected() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        WeeklyPeriod period = new WeeklyPeriod(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0), "availability");
        repository.savePeriod(period);

        assertThrows(IllegalArgumentException.class,
                () -> repository.deletePeriod("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.deletePeriod(" "));
        assertThrows(IllegalArgumentException.class,
                () -> repository.deletePeriod(null));
        assertEquals(1, repository.getPeriods().size());

        repository.deletePeriod(period.getId());

        assertTrue(new LocalRepository(file()).getPeriods().isEmpty());
    }

    @Test
    void savedAndReturnedPeriodsAreCopies() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        WeeklyPeriod period = new WeeklyPeriod(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0), "availability");
        repository.savePeriod(period);

        period.updateTimes(LocalTime.of(14, 0), LocalTime.of(16, 0));
        repository.getPeriods().get(0).updateTimes(LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertEquals(LocalTime.of(9, 0), repository.getPeriods().get(0).getStartTime());
        assertEquals(LocalTime.of(11, 0), repository.getPeriods().get(0).getEndTime());
    }

    // ----- Plans and sessions -----

    @Test
    void planWithSessionsPersistsAfterReload() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        AcademicTask task = saveCourseAndTask(repository);
        StudyPlan plan = new StudyPlan(WEEK);
        StudySession session = session(task, 14, 9);
        plan.addSession(session);
        plan.setPriorityExplanation("Exam first.");
        plan.setUnscheduledMinutes(Map.of(task.getId(), 30));
        plan.accept();
        plan.markNeedsRevision();

        repository.savePlan(plan);

        List<StudyPlan> reloaded = new LocalRepository(file()).getPlans(WEEK);
        assertEquals(1, reloaded.size());
        StudyPlan loaded = reloaded.get(0);
        assertEquals(plan.getId(), loaded.getId());
        assertTrue(loaded.isAccepted());
        assertTrue(loaded.needsRevision());
        assertEquals("Exam first.", loaded.getPriorityExplanation());
        assertEquals(Map.of(task.getId(), 30), loaded.getUnscheduledMinutes());
        assertEquals(1, loaded.getSessions().size());
        StudySession loadedSession = loaded.getSessions().get(0);
        assertEquals(session.getId(), loadedSession.getId());
        assertEquals(task.getId(), loadedSession.getTaskId());
        assertEquals(session.getStartDateTime(), loadedSession.getStartDateTime());
        assertEquals("scheduled", loadedSession.getStatus());
    }

    @Test
    void savingAPlanAgainReplacesItsSessions() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        AcademicTask task = saveCourseAndTask(repository);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(session(task, 14, 9));
        repository.savePlan(plan);

        StudyPlan revised = new StudyPlan(plan.getId(), WEEK, false, false, "",
                Map.of(), List.of(session(task, 15, 10)));
        repository.savePlan(revised);

        List<StudyPlan> plans = new LocalRepository(file()).getPlans(WEEK);
        assertEquals(1, plans.size());
        assertEquals(1, plans.get(0).getSessions().size());
        assertEquals(revised.getSessions().get(0).getId(),
                plans.get(0).getSessions().get(0).getId());
    }

    @Test
    void getPlansReturnsOnlyPlansForTheRequestedWeek() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        repository.savePlan(new StudyPlan(WEEK));
        repository.savePlan(new StudyPlan(WEEK.plusDays(7)));

        assertEquals(1, repository.getPlans(WEEK).size());
        assertEquals(WEEK.plusDays(7),
                repository.getPlans(WEEK.plusDays(7)).get(0).getWeekStartDate());
        assertTrue(repository.getPlans(WEEK.plusDays(14)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repository.getPlans(null));
    }

    @Test
    void savedAndReturnedPlansAreCopies() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        AcademicTask task = saveCourseAndTask(repository);
        StudyPlan plan = new StudyPlan(WEEK);
        StudySession session = session(task, 14, 9);
        plan.addSession(session);
        repository.savePlan(plan);

        session.recordOutcome("completed", 50);
        repository.getPlans(WEEK).get(0).getSessions().get(0).recordOutcome("missed", 0);

        assertEquals("scheduled", repository.getPlans(WEEK).get(0)
                .getSessions().get(0).getStatus());
    }

    @Test
    void planWithSessionForUnknownTaskIsRejectedAndNothingIsSaved() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        StudyPlan plan = new StudyPlan(WEEK);
        LocalDateTime start = LocalDateTime.of(2030, 5, 14, 9, 0);
        plan.addSession(new StudySession("missing-task", start, start.plusHours(1)));

        assertThrows(IllegalArgumentException.class, () -> repository.savePlan(plan));

        assertTrue(repository.getPlans(WEEK).isEmpty());
        assertTrue(new LocalRepository(file()).getPlans(WEEK).isEmpty());
    }

    @Test
    void sessionAlreadyInAnotherPlanIsRejected() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        AcademicTask task = saveCourseAndTask(repository);
        StudySession session = session(task, 14, 9);
        StudyPlan first = new StudyPlan(WEEK);
        first.addSession(session);
        repository.savePlan(first);

        StudyPlan second = new StudyPlan(WEEK);
        second.addSession(session);

        assertThrows(IllegalArgumentException.class, () -> repository.savePlan(second));
        assertEquals(1, repository.getPlans(WEEK).size());
    }

    @Test
    void savingASessionUpdatesItInsideItsPlanAndKeepsThePlanFlags() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        AcademicTask task = saveCourseAndTask(repository);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(session(task, 14, 9));
        plan.setPriorityExplanation("Exam first.");
        plan.accept();
        repository.savePlan(plan);

        StudySession stored = repository.getSessionsForTask(task.getId()).get(0);
        stored.recordOutcome("completed", 50);
        repository.saveSession(stored);

        LocalRepository reloaded = new LocalRepository(file());
        StudyPlan loadedPlan = reloaded.getPlans(WEEK).get(0);
        assertEquals(1, loadedPlan.getSessions().size());
        assertEquals("completed", loadedPlan.getSessions().get(0).getStatus());
        assertEquals(50, loadedPlan.getSessions().get(0).getActualMinutes());
        assertTrue(loadedPlan.isAccepted());
        assertEquals("Exam first.", loadedPlan.getPriorityExplanation());
    }

    @Test
    void savingASessionThatBelongsToNoPlanIsRejected() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        AcademicTask task = saveCourseAndTask(repository);

        assertThrows(IllegalArgumentException.class,
                () -> repository.saveSession(session(task, 14, 9)));
    }

    @Test
    void sessionsForATaskIncludeEveryPlanAndOnlyThatTask() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        AcademicTask task = saveCourseAndTask(repository);
        AcademicTask other = new AcademicTask(
                repository.getCourses().get(0), "Other", "Coursework", DEADLINE, 2, 60);
        repository.saveTask(other);

        StudyPlan thisWeek = new StudyPlan(WEEK);
        thisWeek.addSession(session(task, 14, 9));
        thisWeek.addSession(session(other, 15, 9));
        StudyPlan nextWeek = new StudyPlan(WEEK.plusDays(7));
        nextWeek.addSession(session(task, 21, 9));
        repository.savePlan(thisWeek);
        repository.savePlan(nextWeek);

        List<StudySession> sessions = repository.getSessionsForTask(task.getId());

        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().allMatch(s -> s.getTaskId().equals(task.getId())));
        assertThrows(IllegalArgumentException.class,
                () -> repository.getSessionsForTask(" "));
        assertTrue(repository.getSessionsForTask("unknown").isEmpty());
    }

    // ----- Atomic commit -----

    @Test
    void successfulCommitAppliesAllChangesTogether() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        AcademicTask task = saveCourseAndTask(repository);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(session(task, 14, 9));
        plan.accept();
        repository.savePlan(plan);

        AcademicTask storedTask = repository.getTask(task.getId());
        storedTask.updateRemainingEffort(20);
        StudyPlan storedPlan = repository.getPlans(WEEK).get(0);
        storedPlan.getSessions().get(0).recordOutcome("completed", 45);
        storedPlan.markNeedsRevision();

        repository.commit(draft -> {
            draft.saveTask(storedTask);
            draft.savePlan(storedPlan);
        });

        LocalRepository reloaded = new LocalRepository(file());
        assertEquals(20, reloaded.getTask(task.getId()).getRemainingMinutes());
        StudyPlan reloadedPlan = reloaded.getPlans(WEEK).get(0);
        assertTrue(reloadedPlan.needsRevision());
        assertEquals("completed", reloadedPlan.getSessions().get(0).getStatus());
        assertEquals(45, reloadedPlan.getSessions().get(0).getActualMinutes());
    }

    @Test
    void failedCommitChangesNothingInMemoryOrOnDisk() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        repository.saveCourse(new Course("Algorithms"));

        assertThrows(IllegalStateException.class, () -> repository.commit(draft -> {
            draft.saveCourse(new Course("Databases"));
            throw new IllegalStateException("boom");
        }));

        assertEquals(1, repository.getCourses().size());
        assertEquals(1, new LocalRepository(file()).getCourses().size());
    }

    @Test
    void invalidChangeInTheMiddleOfACommitDiscardsTheEarlierOnes() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        Course course = new Course("Algorithms");

        assertThrows(IllegalArgumentException.class, () -> repository.commit(draft -> {
            draft.saveCourse(course);
            draft.deletePeriod("missing");
        }));

        assertTrue(repository.getCourses().isEmpty());
        assertTrue(new LocalRepository(file()).getCourses().isEmpty());
    }

    @Test
    void draftCannotBeUsedAfterItsCommitFinishes() throws IOException {
        LocalRepository repository = new LocalRepository(file());
        LocalRepository.Draft[] kept = new LocalRepository.Draft[1];
        repository.commit(draft -> kept[0] = draft);

        assertThrows(IllegalStateException.class,
                () -> kept[0].saveCourse(new Course("Algorithms")));
    }

    @Test
    void failedWriteLeavesTheRepositoryUnchanged() throws IOException {
        Path blocker = directory.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        LocalRepository repository = new LocalRepository(blocker.resolve("repository.json"));

        assertThrows(IOException.class,
                () -> repository.saveCourse(new Course("Algorithms")));

        assertTrue(repository.getCourses().isEmpty());
    }

    // ----- File format -----

    @Test
    void fileWrittenBeforePlanningDataExistedStillLoads() throws IOException {
        Files.writeString(file(),
                "{\"courses\":[{\"id\":\"c1\",\"name\":\"Algorithms\"}],\"tasks\":[]}");

        LocalRepository repository = new LocalRepository(file());

        assertEquals(1, repository.getCourses().size());
        assertTrue(repository.getPeriods().isEmpty());
        assertTrue(repository.getPlans(WEEK).isEmpty());

        repository.savePeriod(new WeeklyPeriod(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(11, 0), "availability"));
        assertEquals(1, new LocalRepository(file()).getPeriods().size());
        assertEquals(1, new LocalRepository(file()).getCourses().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // end time before start time
            EMPTY + "\"periods\":[{\"id\":\"p1\",\"dayOfWeek\":\"MONDAY\","
                    + "\"startTime\":\"11:00\",\"endTime\":\"09:00\","
                    + "\"type\":\"availability\"}],\"plans\":[]}",
            // unknown day of week
            EMPTY + "\"periods\":[{\"id\":\"p1\",\"dayOfWeek\":\"FUNDAY\","
                    + "\"startTime\":\"09:00\",\"endTime\":\"11:00\","
                    + "\"type\":\"availability\"}],\"plans\":[]}",
            // periods is not an array
            EMPTY + "\"periods\":\"oops\",\"plans\":[]}",
            // duplicate period ids
            EMPTY + "\"periods\":[{\"id\":\"p1\",\"dayOfWeek\":\"MONDAY\","
                    + "\"startTime\":\"09:00\",\"endTime\":\"11:00\","
                    + "\"type\":\"availability\"},{\"id\":\"p1\",\"dayOfWeek\":\"TUESDAY\","
                    + "\"startTime\":\"09:00\",\"endTime\":\"11:00\","
                    + "\"type\":\"availability\"}],\"plans\":[]}",
            // session references a task that does not exist
            EMPTY + "\"periods\":[]," + PLAN_START
                    + "\"accepted\":false,\"needsRevision\":false,"
                    + "\"priorityExplanation\":\"\",\"unscheduledMinutes\":{},"
                    + "\"sessions\":[{\"id\":\"s1\",\"taskId\":\"missing\","
                    + "\"startDateTime\":\"2030-05-14T09:00\","
                    + "\"endDateTime\":\"2030-05-14T10:00\","
                    + "\"status\":\"scheduled\",\"actualMinutes\":0}]}]}",
            // plan needs revision but was never accepted
            EMPTY + "\"periods\":[]," + PLAN_START
                    + "\"accepted\":false,\"needsRevision\":true," + PLAN_BODY,
            // plan date is not a date
            EMPTY + "\"periods\":[],\"plans\":[{\"id\":\"pl1\",\"weekStartDate\":\"soon\","
                    + "\"accepted\":false,\"needsRevision\":false," + PLAN_BODY
    })
    void malformedPlanningRecordsAreRejectedOnLoad(String json) throws IOException {
        Files.writeString(file(), json);

        assertThrows(IllegalArgumentException.class, () -> new LocalRepository(file()));
    }

    @Test
    void validPlanningRecordsLoad() throws IOException {
        Files.writeString(file(), EMPTY + "\"periods\":[]," + PLAN_START
                + "\"accepted\":true,\"needsRevision\":true," + PLAN_BODY);

        LocalRepository repository = new LocalRepository(file());

        assertEquals(1, repository.getPlans(WEEK).size());
        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());
        assertTrue(repository.getPlans(WEEK).get(0).getSessions().isEmpty());
    }

    // ----- Helpers -----

    private static AcademicTask saveCourseAndTask(LocalRepository repository)
            throws IOException {
        Course course = new Course("Algorithms");
        repository.saveCourse(course);
        AcademicTask task = new AcademicTask(
                course, "Assignment", "Coursework", DEADLINE, 2, 60);
        repository.saveTask(task);
        return task;
    }

    private static StudySession session(AcademicTask task, int dayOfMay, int hour) {
        LocalDateTime start = LocalDateTime.of(2030, 5, dayOfMay, hour, 0);
        return new StudySession(task.getId(), start, start.plusHours(1));
    }
}
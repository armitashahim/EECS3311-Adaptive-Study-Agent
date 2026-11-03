package ca.yorku.eecs3311.application;

import static ca.yorku.eecs3311.application.ScriptedLlm.done;
import static ca.yorku.eecs3311.application.ScriptedLlm.schedule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.agent.AgentException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyApplicationPlanningTest {
    private static final LocalDate WEEK = LocalDate.of(2030, 5, 13);
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 15, 12, 0);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2030, 5, 30, 23, 59);

    /** A clock the test can move forward. */
    private static class MovableClock extends Clock {
        private LocalDateTime time;

        MovableClock(LocalDateTime time) {
            this.time = time;
        }

        void set(LocalDateTime time) {
            this.time = time;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return time.toInstant(ZoneOffset.UTC);
        }
    }

    @TempDir
    Path directory;

    private LocalRepository repository;
    private MovableClock clock;
    private Course course;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalRepository(directory.resolve("repository.json"));
        clock = new MovableClock(NOW);
        course = new Course("Algorithms");
        repository.saveCourse(course);
    }

    private StudyApplication app(ScriptedLlm llm) {
        return new StudyApplication(repository, clock, llm);
    }

    private AcademicTask addTask(String title, int minutes) throws IOException {
        AcademicTask task = new AcademicTask(course, title, "Coursework", DEADLINE, 2, minutes);
        repository.saveTask(task);
        return task;
    }

    private void addAvailability(DayOfWeek day, int startHour, int endHour) throws IOException {
        repository.savePeriod(new WeeklyPeriod(day, LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0), "availability"));
    }

    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2030, 5, day, hour, minute);
    }

    // ===== Generating =====

    @Test
    void generatingRequiresAConfiguredAiAssistant() throws IOException {
        addTask("Essay", 60);
        addAvailability(DayOfWeek.THURSDAY, 9, 12);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> app(null).generatePlan(WEEK));

        assertTrue(error.getMessage().contains("not configured"));
    }

    @Test
    void generatingRejectsBadWeeksAndMissingInputs() throws IOException {
        StudyApplication app = app(new ScriptedLlm());

        assertThrows(IllegalArgumentException.class, () -> app.generatePlan(null));
        assertThrows(IllegalArgumentException.class,
                () -> app.generatePlan(WEEK.plusDays(1)));
        assertThrows(IllegalStateException.class, () -> app.generatePlan(WEEK.minusDays(7)));

        IllegalStateException noAvailability = assertThrows(IllegalStateException.class,
                () -> app.generatePlan(WEEK));
        assertTrue(noAvailability.getMessage().contains("availability"));

        addAvailability(DayOfWeek.THURSDAY, 9, 12);
        IllegalStateException noTasks = assertThrows(IllegalStateException.class,
                () -> app.generatePlan(WEEK));
        assertTrue(noTasks.getMessage().contains("no tasks"));
    }

    @Test
    void generatingWithNoUsableTimeLeftIsRejected() throws IOException {
        addTask("Essay", 60);
        addAvailability(DayOfWeek.MONDAY, 9, 12);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> app(new ScriptedLlm()).generatePlan(WEEK));

        assertTrue(error.getMessage().contains("No usable study time"));
    }

    @Test
    void aGeneratedProposalIsNotSavedUntilItIsAccepted() throws Exception {
        AcademicTask essay = addTask("Essay", 120);
        addAvailability(DayOfWeek.THURSDAY, 9, 12);
        ScriptedLlm llm = new ScriptedLlm(schedule(essay.getId()), done("The essay is first."));
        StudyApplication app = app(llm);

        PlanProposal proposal = app.generatePlan(WEEK);

        assertFalse(proposal.revision());
        assertEquals("The essay is first.", proposal.plan().getPriorityExplanation());
        assertEquals(2, proposal.plan().getSessions().size());
        assertTrue(proposal.plan().getUnscheduledMinutes().isEmpty());
        assertTrue(repository.getPlans(WEEK).isEmpty());

        app.acceptPlan(proposal);

        StudyPlan saved = repository.getPlans(WEEK).get(0);
        assertTrue(saved.isAccepted());
        assertFalse(saved.needsRevision());
        assertEquals(2, saved.getSessions().size());
        assertEquals(at(16, 9, 0), saved.getSessions().get(0).getStartDateTime());
    }

    @Test
    void sessionsOnlyUseFutureAvailableTimeBeforeTheDeadline() throws Exception {
        AcademicTask soon = new AcademicTask(course, "Soon", "Coursework",
                at(16, 10, 0), 3, 500);
        repository.saveTask(soon);
        addAvailability(DayOfWeek.WEDNESDAY, 9, 17);
        addAvailability(DayOfWeek.THURSDAY, 9, 17);
        ScriptedLlm llm = new ScriptedLlm(schedule(soon.getId()), done("Deadline soon."));

        PlanProposal proposal = app(llm).generatePlan(WEEK);

        List<StudySession> sessions = proposal.plan().getSessions();
        assertEquals(at(15, 12, 0), sessions.get(0).getStartDateTime());
        assertTrue(sessions.stream().allMatch(s -> !s.getEndDateTime().isAfter(at(16, 10, 0))));
        int scheduled = sessions.stream().mapToInt(StudySession::getPlannedMinutes).sum();
        assertEquals(360, scheduled); // Wednesday 12:00-17:00 plus Thursday 09:00-10:00
        assertEquals(140, (int) proposal.plan().getUnscheduledMinutes().get(soon.getId()));
    }

    @Test
    void workThatCannotFitIsReportedAsUnscheduled() throws Exception {
        AcademicTask big = addTask("Thesis", 600);
        addAvailability(DayOfWeek.THURSDAY, 9, 11);
        ScriptedLlm llm = new ScriptedLlm(schedule(big.getId()), done("Only part fits."));

        PlanProposal proposal = app(llm).generatePlan(WEEK);

        assertEquals(480, (int) proposal.plan().getUnscheduledMinutes().get(big.getId()));
    }

    @Test
    void aFailingAiServiceSavesNothing() throws IOException {
        addTask("Essay", 60);
        addAvailability(DayOfWeek.THURSDAY, 9, 12);

        assertThrows(AgentException.class, () -> app(new ScriptedLlm()).generatePlan(WEEK));

        assertTrue(repository.getPlans(WEEK).isEmpty());
    }

    @Test
    void anExistingPlanMustBeRevisedNotRegenerated() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        addAvailability(DayOfWeek.THURSDAY, 9, 12);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(new StudySession(essay.getId(), at(16, 9, 0), at(16, 10, 0)));
        repository.savePlan(plan);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> app(new ScriptedLlm()).generatePlan(WEEK));

        assertTrue(error.getMessage().contains("revise"));
    }

    // ===== Accepting =====

    @Test
    void acceptingFailsWhenDataChangedSinceTheProposal() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        addAvailability(DayOfWeek.THURSDAY, 9, 12);
        StudyApplication app = app(new ScriptedLlm(schedule(essay.getId()), done("ok")));
        PlanProposal proposal = app.generatePlan(WEEK);

        addTask("A new task", 30);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> app.acceptPlan(proposal));
        assertTrue(error.getMessage().contains("changed"));
        assertTrue(repository.getPlans(WEEK).isEmpty());
    }

    @Test
    void aProposalCannotBeAcceptedTwice() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        addAvailability(DayOfWeek.THURSDAY, 9, 12);
        StudyApplication app = app(new ScriptedLlm(schedule(essay.getId()), done("ok")));
        PlanProposal proposal = app.generatePlan(WEEK);
        app.acceptPlan(proposal);

        assertThrows(IllegalStateException.class, () -> app.acceptPlan(proposal));
        assertEquals(1, repository.getPlans(WEEK).size());
    }

    @Test
    void acceptingFailsOnceTheProposedTimeHasPassed() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        addAvailability(DayOfWeek.WEDNESDAY, 12, 17);
        StudyApplication app = app(new ScriptedLlm(schedule(essay.getId()), done("ok")));
        PlanProposal proposal = app.generatePlan(WEEK);

        clock.set(at(15, 12, 30));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> app.acceptPlan(proposal));
        assertTrue(error.getMessage().contains("no longer valid"));
        assertTrue(repository.getPlans(WEEK).isEmpty());
    }

    @Test
    void acceptingRequiresAProposal() {
        assertThrows(IllegalArgumentException.class, () -> app(null).acceptPlan(null));
    }

    // ===== Revising =====

    private StudyPlan acceptedPlanWithPastAndFutureSessions(AcademicTask essay) throws IOException {
        addAvailability(DayOfWeek.MONDAY, 9, 12);
        addAvailability(DayOfWeek.THURSDAY, 9, 12);
        addAvailability(DayOfWeek.FRIDAY, 9, 12);
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(new StudySession(essay.getId(), at(13, 9, 0), at(13, 10, 0)));
        plan.addSession(new StudySession(essay.getId(), at(16, 9, 0), at(16, 10, 0)));
        plan.accept();
        repository.savePlan(plan);
        return plan;
    }

    @Test
    void revisingNeedsAnExistingPlan() throws IOException {
        assertThrows(IllegalStateException.class,
                () -> app(new ScriptedLlm()).revisePlan(WEEK));
    }

    @Test
    void revisionKeepsRecordedSessionsAndReplacesFutureOnes() throws Exception {
        AcademicTask essay = addTask("Essay", 180);
        StudyPlan plan = acceptedPlanWithPastAndFutureSessions(essay);
        StudyApplication app = app(new ScriptedLlm(schedule(essay.getId()),
                done("Moved the remaining work to Friday.")));
        StudySession past = repository.getPlans(WEEK).get(0).getSessions().stream()
                .filter(s -> s.getStartDateTime().equals(at(13, 9, 0))).findFirst().orElseThrow();
        app.recordProgress(past, "partial", 30, false, 150);
        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());

        PlanProposal proposal = app.revisePlan(WEEK);

        assertTrue(proposal.revision());
        assertEquals(plan.getId(), proposal.plan().getId());
        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());

        app.acceptPlan(proposal);

        StudyPlan saved = repository.getPlans(WEEK).get(0);
        assertEquals(plan.getId(), saved.getId());
        assertTrue(saved.isAccepted());
        assertFalse(saved.needsRevision());
        StudySession keptPast = saved.getSessions().stream()
                .filter(s -> s.getId().equals(past.getId())).findFirst().orElseThrow();
        assertEquals("partial", keptPast.getStatus());
        assertEquals(30, keptPast.getActualMinutes());
        int newMinutes = saved.getSessions().stream()
                .filter(s -> !s.getId().equals(past.getId()))
                .mapToInt(StudySession::getPlannedMinutes).sum();
        assertEquals(150, newMinutes);
        assertEquals("Moved the remaining work to Friday.", saved.getPriorityExplanation());
        assertEquals(1, repository.getPlans(WEEK).size());
    }

    @Test
    void revisionNeverSchedulesOverKeptSessions() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        acceptedPlanWithPastAndFutureSessions(essay);
        clock.set(at(16, 9, 30)); // the Thursday 09:00 session is now in progress
        StudyApplication app = app(new ScriptedLlm(schedule(essay.getId()), done("ok")));

        PlanProposal proposal = app.revisePlan(WEEK);

        for (StudySession session : proposal.plan().getSessions()) {
            if (proposal.newSessionIds().contains(session.getId())) {
                assertFalse(session.getStartDateTime().isBefore(at(16, 10, 0)));
            }
        }
        assertEquals(3, proposal.plan().getSessions().size());
    }

    @Test
    void revisionWithNoUnfinishedTasksNeedsNoAiAndClearsFutureSessions() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        acceptedPlanWithPastAndFutureSessions(essay);
        StudySession past = repository.getPlans(WEEK).get(0).getSessions().stream()
                .filter(s -> s.getStartDateTime().equals(at(13, 9, 0))).findFirst().orElseThrow();
        ScriptedLlm llm = new ScriptedLlm();
        StudyApplication app = app(llm);
        app.recordProgress(past, "completed", 60, true, 0);

        PlanProposal proposal = app.revisePlan(WEEK);
        app.acceptPlan(proposal);

        assertTrue(llm.prompts.isEmpty());
        StudyPlan saved = repository.getPlans(WEEK).get(0);
        assertEquals(1, saved.getSessions().size());
        assertEquals("completed", saved.getSessions().get(0).getStatus());
        assertFalse(saved.needsRevision());
    }

    @Test
    void aFailedRevisionLeavesThePlanAndItsWarningUntouched() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        acceptedPlanWithPastAndFutureSessions(essay);
        StudyApplication flagger = app(null);
        flagger.savePeriod(new WeeklyPeriod(DayOfWeek.TUESDAY, LocalTime.of(9, 0),
                LocalTime.of(10, 0), "availability"));
        assertTrue(repository.getPlans(WEEK).get(0).needsRevision());

        assertThrows(AgentException.class, () -> app(new ScriptedLlm()).revisePlan(WEEK));

        StudyPlan stored = repository.getPlans(WEEK).get(0);
        assertTrue(stored.needsRevision());
        assertEquals(2, stored.getSessions().size());
    }

    @Test
    void revisionCannotBeAcceptedAfterTheDataChanged() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        acceptedPlanWithPastAndFutureSessions(essay);
        StudyApplication app = app(new ScriptedLlm(schedule(essay.getId()), done("ok")));
        PlanProposal proposal = app.revisePlan(WEEK);

        app.removePeriod(repository.getPeriods().get(0).getId());

        assertThrows(IllegalStateException.class, () -> app.acceptPlan(proposal));
        assertEquals(2, repository.getPlans(WEEK).get(0).getSessions().size());
    }

    @Test
    void revisionWithoutAvailabilityReportsEverythingAsUnscheduled() throws Exception {
        AcademicTask essay = addTask("Essay", 60);
        acceptedPlanWithPastAndFutureSessions(essay);
        StudyApplication app = app(new ScriptedLlm(schedule(essay.getId()), done("No time.")));
        for (WeeklyPeriod period : repository.getPeriods()) {
            app.removePeriod(period.getId());
        }

        PlanProposal proposal = app.revisePlan(WEEK);

        assertEquals(60, (int) proposal.plan().getUnscheduledMinutes().get(essay.getId()));
        assertEquals(1, proposal.plan().getSessions().size());
    }
}
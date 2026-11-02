package ca.yorku.eecs3311.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyPlanTest {
    private static final LocalDate WEEK = LocalDate.of(2030, 5, 13);

    @Test
    void newPlanIsAnEmptyUnacceptedProposal() {
        StudyPlan plan = new StudyPlan(WEEK);

        assertNotNull(plan.getId());
        assertFalse(plan.getId().isBlank());
        assertEquals(WEEK, plan.getWeekStartDate());
        assertFalse(plan.isAccepted());
        assertFalse(plan.needsRevision());
        assertEquals("", plan.getPriorityExplanation());
        assertTrue(plan.getSessions().isEmpty());
        assertTrue(plan.getUnscheduledMinutes().isEmpty());
    }

    @Test
    void rejectsMissingWeekStartDate() {
        assertThrows(IllegalArgumentException.class, () -> new StudyPlan(null));
    }

    @Test
    void addsSessionsWithinTheWeek() {
        StudyPlan plan = new StudyPlan(WEEK);
        StudySession first = session("task-1", 14, 9);
        StudySession second = session("task-2", 19, 20);

        plan.addSession(first);
        plan.addSession(second);

        assertEquals(List.of(first, second), plan.getSessions());
    }

    @Test
    void rejectsNullDuplicateAndOutOfWeekSessions() {
        StudyPlan plan = new StudyPlan(WEEK);
        StudySession session = session("task-1", 14, 9);
        plan.addSession(session);

        assertThrows(IllegalArgumentException.class, () -> plan.addSession(null));
        assertThrows(IllegalArgumentException.class, () -> plan.addSession(session));
        assertThrows(IllegalArgumentException.class,
                () -> plan.addSession(session("task-1", 12, 9)));
        assertThrows(IllegalArgumentException.class,
                () -> plan.addSession(session("task-1", 20, 9)));

        assertEquals(1, plan.getSessions().size());
    }

    @Test
    void sessionsListCannotBeModifiedFromOutside() {
        StudyPlan plan = new StudyPlan(WEEK);

        assertThrows(UnsupportedOperationException.class,
                () -> plan.getSessions().add(session("task-1", 14, 9)));
    }

    @Test
    void acceptingMarksPlanAcceptedAndClearsRevisionFlag() {
        StudyPlan plan = new StudyPlan(WEEK);
        plan.accept();
        plan.markNeedsRevision();
        assertTrue(plan.needsRevision());

        plan.accept();

        assertTrue(plan.isAccepted());
        assertFalse(plan.needsRevision());
    }

    @Test
    void onlyAcceptedPlansCanBeMarkedAsNeedingRevision() {
        StudyPlan proposal = new StudyPlan(WEEK);

        proposal.markNeedsRevision();

        assertFalse(proposal.needsRevision());
    }

    @Test
    void priorityExplanationIsTrimmedAndNullBecomesEmpty() {
        StudyPlan plan = new StudyPlan(WEEK);

        plan.setPriorityExplanation("  Exam first.  ");
        assertEquals("Exam first.", plan.getPriorityExplanation());

        plan.setPriorityExplanation(null);
        assertEquals("", plan.getPriorityExplanation());
    }

    @Test
    void unscheduledMinutesAreCopiedOnTheWayInAndOut() {
        StudyPlan plan = new StudyPlan(WEEK);
        Map<String, Integer> input = new HashMap<>();
        input.put("task-1", 45);

        plan.setUnscheduledMinutes(input);
        input.put("task-2", 10);

        assertEquals(Map.of("task-1", 45), plan.getUnscheduledMinutes());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.getUnscheduledMinutes().put("task-3", 5));
    }

    @Test
    void invalidUnscheduledMinutesLeavePreviousValueUnchanged() {
        StudyPlan plan = new StudyPlan(WEEK);
        plan.setUnscheduledMinutes(Map.of("task-1", 45));

        assertThrows(IllegalArgumentException.class,
                () -> plan.setUnscheduledMinutes(null));
        assertThrows(IllegalArgumentException.class,
                () -> plan.setUnscheduledMinutes(Map.of("task-2", 0)));
        assertThrows(IllegalArgumentException.class,
                () -> plan.setUnscheduledMinutes(Map.of(" ", 10)));

        assertEquals(Map.of("task-1", 45), plan.getUnscheduledMinutes());
    }

    @Test
    void restorationConstructorPreservesSuppliedState() {
        StudySession session = session("task-1", 14, 9);

        StudyPlan plan = new StudyPlan("plan-1", WEEK, true, true,
                "Exam first.", Map.of("task-2", 30), List.of(session));

        assertEquals("plan-1", plan.getId());
        assertEquals(WEEK, plan.getWeekStartDate());
        assertTrue(plan.isAccepted());
        assertTrue(plan.needsRevision());
        assertEquals("Exam first.", plan.getPriorityExplanation());
        assertEquals(Map.of("task-2", 30), plan.getUnscheduledMinutes());
        assertEquals(List.of(session), plan.getSessions());
    }

    @Test
    void restorationRejectsInvalidState() {
        assertThrows(IllegalArgumentException.class,
                () -> new StudyPlan(" ", WEEK, false, false, "", Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyPlan("plan-1", WEEK, false, true, "", Map.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyPlan("plan-1", WEEK, false, false, "", Map.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyPlan("plan-1", WEEK, false, false, "", Map.of(),
                        List.of(session("task-1", 25, 9))));
    }

    @Test
    void weekWindowIncludesItsFirstMomentAndEndsAtMidnightOfTheNextWeek() {
        StudyPlan plan = new StudyPlan(WEEK);
        LocalDateTime weekStart = WEEK.atStartOfDay();
        LocalDateTime weekEnd = WEEK.plusDays(7).atStartOfDay();

        plan.addSession(new StudySession("task-1", weekStart, weekStart.plusHours(1)));
        plan.addSession(new StudySession("task-1", weekEnd.minusHours(1), weekEnd));

        assertEquals(2, plan.getSessions().size());
        assertThrows(IllegalArgumentException.class, () -> plan.addSession(
                new StudySession("task-1", weekEnd, weekEnd.plusHours(1))));
        assertThrows(IllegalArgumentException.class, () -> plan.addSession(
                new StudySession("task-1", weekStart.minusMinutes(1),
                        weekStart.plusHours(1))));
        assertEquals(2, plan.getSessions().size());
    }

    private static StudySession session(String taskId, int dayOfMay, int hour) {
        LocalDateTime start = LocalDateTime.of(2030, 5, dayOfMay, hour, 0);
        return new StudySession(taskId, start, start.plusHours(1));
    }
}
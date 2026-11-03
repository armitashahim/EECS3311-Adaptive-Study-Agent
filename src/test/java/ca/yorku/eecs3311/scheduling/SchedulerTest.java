package ca.yorku.eecs3311.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.TimeSlot;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchedulerTest {
    // Monday May 13, 2030.
    private static final LocalDate WEEK = LocalDate.of(2030, 5, 13);
    private static final LocalDateTime WEEK_START = WEEK.atStartOfDay();
    private static final LocalDateTime FAR_DEADLINE = LocalDateTime.of(2030, 5, 30, 23, 59);

    private final Scheduler scheduler = new Scheduler();
    private final Course course = new Course("Algorithms");

    // ===== Construction =====

    @Test
    void sessionLengthsMustBeSensible() {
        assertThrows(IllegalArgumentException.class, () -> new Scheduler(60, 0));
        assertThrows(IllegalArgumentException.class, () -> new Scheduler(10, 20));
        new Scheduler(30, 30);
    }

    // ===== getAvailableSlots =====

    @Test
    void availabilityBecomesSlotsOnTheMatchingDaysOfTheWeek() {
        List<TimeSlot> slots = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.WEDNESDAY, 14, 16, "availability"),
                        period(DayOfWeek.MONDAY, 9, 11, "availability")),
                WEEK, WEEK_START, List.of());

        assertEquals(2, slots.size());
        assertEquals(at(13, 9, 0), slots.get(0).getStart());
        assertEquals(at(13, 11, 0), slots.get(0).getEnd());
        assertEquals(at(15, 14, 0), slots.get(1).getStart());
    }

    @Test
    void commitmentsAreSubtractedFromAvailability() {
        List<TimeSlot> slots = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.MONDAY, 9, 17, "availability"),
                        period(DayOfWeek.MONDAY, 12, 13, "commitment")),
                WEEK, WEEK_START, List.of());

        assertEquals(2, slots.size());
        assertEquals(at(13, 12, 0), slots.get(0).getEnd());
        assertEquals(at(13, 13, 0), slots.get(1).getStart());
    }

    @Test
    void commitmentThatCoversAllAvailabilityLeavesNothing() {
        List<TimeSlot> slots = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.MONDAY, 9, 11, "availability"),
                        period(DayOfWeek.MONDAY, 8, 12, "commitment")),
                WEEK, WEEK_START, List.of());

        assertTrue(slots.isEmpty());
    }

    @Test
    void commitmentsOnOtherDaysDoNotAffectAvailability() {
        List<TimeSlot> slots = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.MONDAY, 9, 11, "availability"),
                        period(DayOfWeek.TUESDAY, 9, 11, "commitment")),
                WEEK, WEEK_START, List.of());

        assertEquals(1, slots.size());
    }

    @Test
    void nothingEarlierThanNotBeforeIsOfferedAndItRoundsUpToAQuarterHour() {
        List<TimeSlot> periods = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.MONDAY, 9, 17, "availability"),
                        period(DayOfWeek.TUESDAY, 9, 11, "availability")),
                WEEK, at(13, 12, 7), List.of());

        assertEquals(2, periods.size());
        assertEquals(at(13, 12, 15), periods.get(0).getStart());
        assertEquals(at(13, 17, 0), periods.get(0).getEnd());
        assertEquals(at(14, 9, 0), periods.get(1).getStart());
    }

    @Test
    void anExactQuarterHourIsNotRounded() {
        List<TimeSlot> slots = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.MONDAY, 9, 17, "availability")),
                WEEK, at(13, 12, 30), List.of());

        assertEquals(at(13, 12, 30), slots.get(0).getStart());
    }

    @Test
    void periodsThatEndedBeforeNotBeforeAreDropped() {
        List<TimeSlot> slots = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.MONDAY, 9, 11, "availability")),
                WEEK, at(13, 15, 0), List.of());

        assertTrue(slots.isEmpty());
    }

    @Test
    void busyTimeIsSubtracted() {
        List<TimeSlot> slots = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.MONDAY, 9, 12, "availability")),
                WEEK, WEEK_START, List.of(new TimeSlot(at(13, 10, 0), at(13, 11, 0))));

        assertEquals(2, slots.size());
        assertEquals(at(13, 10, 0), slots.get(0).getEnd());
        assertEquals(at(13, 11, 0), slots.get(1).getStart());
    }

    @Test
    void touchingAvailabilityIsJoinedIntoOneSlot() {
        List<TimeSlot> slots = scheduler.getAvailableSlots(
                List.of(period(DayOfWeek.MONDAY, 9, 10, "availability"),
                        period(DayOfWeek.MONDAY, 10, 11, "availability")),
                WEEK, WEEK_START, List.of());

        assertEquals(1, slots.size());
        assertEquals(120, slots.get(0).getDurationMinutes());
    }

    @Test
    void noPeriodsMeansNoSlots() {
        assertTrue(scheduler.getAvailableSlots(List.of(), WEEK, WEEK_START, List.of()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.getAvailableSlots(null, WEEK, WEEK_START, List.of()));
    }

    // ===== createSchedule =====

    @Test
    void longTasksAreSplitIntoSessionsOfAtMostTheMaximum() {
        AcademicTask task = task("Project", 200, FAR_DEADLINE, 2);

        ScheduleResult result = scheduler.createSchedule(List.of(task),
                List.of(slot(13, 9, 13)));

        assertEquals(3, result.sessions().size());
        assertEquals(90, result.sessions().get(0).getPlannedMinutes());
        assertEquals(90, result.sessions().get(1).getPlannedMinutes());
        assertEquals(20, result.sessions().get(2).getPlannedMinutes());
        assertTrue(result.unscheduledMinutes().isEmpty());
        assertEquals(at(13, 9, 0), result.sessions().get(0).getStartDateTime());
        assertEquals(at(13, 9, 90 + 90), result.sessions().get(2).getStartDateTime());
    }

    @Test
    void tasksAreScheduledInTheOrderGiven() {
        AcademicTask first = task("First", 60, FAR_DEADLINE, 1);
        AcademicTask second = task("Second", 60, FAR_DEADLINE, 3);

        ScheduleResult result = scheduler.createSchedule(List.of(first, second),
                List.of(slot(13, 9, 11)));

        assertEquals(first.getId(), result.sessions().get(0).getTaskId());
        assertEquals(at(13, 9, 0), result.sessions().get(0).getStartDateTime());
        assertEquals(second.getId(), result.sessions().get(1).getTaskId());
        assertEquals(at(13, 10, 0), result.sessions().get(1).getStartDateTime());
    }

    @Test
    void sessionsNeverEndAfterTheDeadline() {
        AcademicTask task = task("Essay", 300, at(13, 11, 0), 2);

        ScheduleResult result = scheduler.createSchedule(List.of(task),
                List.of(slot(13, 9, 17)));

        int scheduled = result.sessions().stream().mapToInt(StudySession::getPlannedMinutes).sum();
        assertEquals(120, scheduled);
        assertEquals(Map.of(task.getId(), 180), result.unscheduledMinutes());
        assertTrue(result.sessions().stream()
                .allMatch(s -> !s.getEndDateTime().isAfter(at(13, 11, 0))));
    }

    @Test
    void workThatDoesNotFitIsReportedAsUnscheduled() {
        AcademicTask task = task("Essay", 120, FAR_DEADLINE, 2);

        ScheduleResult empty = scheduler.createSchedule(List.of(task), List.of());
        ScheduleResult partial = scheduler.createSchedule(List.of(task), List.of(slot(13, 9, 10)));

        assertTrue(empty.sessions().isEmpty());
        assertEquals(Map.of(task.getId(), 120), empty.unscheduledMinutes());
        assertEquals(1, partial.sessions().size());
        assertEquals(Map.of(task.getId(), 60), partial.unscheduledMinutes());
    }

    @Test
    void windowsTooSmallForAMinimumSessionAreSkipped() {
        AcademicTask task = task("Reading", 60, FAR_DEADLINE, 2);
        TimeSlot tiny = new TimeSlot(at(13, 9, 0), at(13, 9, 10));
        TimeSlot big = slot(14, 9, 11);

        ScheduleResult result = scheduler.createSchedule(List.of(task), List.of(tiny, big));

        assertEquals(1, result.sessions().size());
        assertEquals(at(14, 9, 0), result.sessions().get(0).getStartDateTime());
    }

    @Test
    void aTaskWhoseWholeRemainderIsShortMayUseASmallWindow() {
        AcademicTask task = task("Quick review", 10, FAR_DEADLINE, 2);
        TimeSlot tiny = new TimeSlot(at(13, 9, 0), at(13, 9, 10));

        ScheduleResult result = scheduler.createSchedule(List.of(task), List.of(tiny));

        assertEquals(1, result.sessions().size());
        assertEquals(10, result.sessions().get(0).getPlannedMinutes());
    }

    @Test
    void finishedRemovedRepeatedAndMissingTasksAreIgnored() {
        AcademicTask done = task("Done", 60, FAR_DEADLINE, 2);
        done.markCompleted();
        AcademicTask removed = task("Removed", 60, FAR_DEADLINE, 2);
        removed.archive();
        AcademicTask active = task("Active", 60, FAR_DEADLINE, 2);
        List<AcademicTask> tasks = new ArrayList<>(List.of(done, removed, active, active));
        tasks.add(null);

        ScheduleResult result = scheduler.createSchedule(tasks, List.of(slot(13, 9, 17)));

        assertEquals(1, result.sessions().size());
        assertEquals(active.getId(), result.sessions().get(0).getTaskId());
    }

    @Test
    void scheduledSessionsNeverOverlapEachOther() {
        List<AcademicTask> tasks = List.of(task("A", 100, FAR_DEADLINE, 2),
                task("B", 100, FAR_DEADLINE, 2), task("C", 100, FAR_DEADLINE, 2));

        ScheduleResult result = scheduler.createSchedule(tasks,
                List.of(slot(13, 9, 12), slot(14, 9, 12)));

        List<StudySession> sessions = result.sessions();
        for (int i = 1; i < sessions.size(); i++) {
            assertFalse(sessions.get(i).getStartDateTime()
                    .isBefore(sessions.get(i - 1).getEndDateTime()));
        }
    }

    @Test
    void customSessionLengthsAreUsed() {
        AcademicTask task = task("Reading", 100, FAR_DEADLINE, 2);

        ScheduleResult result = new Scheduler(30, 10).createSchedule(List.of(task),
                List.of(slot(13, 9, 17)));

        assertEquals(4, result.sessions().size());
        assertEquals(10, result.sessions().get(3).getPlannedMinutes());
    }

    // ===== validatePlan =====

    @Test
    void aScheduleBuiltByTheSchedulerIsValid() {
        List<AcademicTask> tasks = List.of(task("A", 200, FAR_DEADLINE, 2),
                task("B", 50, FAR_DEADLINE, 3));
        List<TimeSlot> slots = List.of(slot(13, 9, 17));

        ScheduleResult result = scheduler.createSchedule(tasks, slots);

        assertTrue(scheduler.validatePlan(result.sessions(), tasks, slots).isEmpty());
    }

    @Test
    void sessionOutsideAvailableTimeIsReported() {
        AcademicTask task = task("A", 60, FAR_DEADLINE, 2);
        StudySession session = session(task, 13, 8, 9);

        List<String> problems = scheduler.validatePlan(List.of(session), List.of(task),
                List.of(slot(13, 9, 17)));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("outside available study time"));
    }

    @Test
    void sessionEndingAfterTheDeadlineIsReported() {
        AcademicTask task = task("A", 60, at(13, 10, 0), 2);
        StudySession session = session(task, 13, 9, 11);

        List<String> problems = scheduler.validatePlan(List.of(session), List.of(task),
                List.of(slot(13, 9, 17)));

        assertTrue(problems.stream().anyMatch(p -> p.contains("after the task's deadline")));
    }

    @Test
    void overlappingSessionsAreReported() {
        AcademicTask a = task("A", 60, FAR_DEADLINE, 2);
        AcademicTask b = task("B", 60, FAR_DEADLINE, 2);

        List<String> problems = scheduler.validatePlan(
                List.of(session(a, 13, 9, 10), new StudySession(b.getId(), at(13, 9, 30),
                        at(13, 10, 30))),
                List.of(a, b), List.of(slot(13, 9, 17)));

        assertTrue(problems.stream().anyMatch(p -> p.contains("overlap")));
    }

    @Test
    void unknownFinishedAndOverScheduledTasksAreReported() {
        AcademicTask known = task("Known", 60, FAR_DEADLINE, 2);
        AcademicTask done = task("Done", 60, FAR_DEADLINE, 2);
        done.markCompleted();
        AcademicTask stranger = task("Stranger", 60, FAR_DEADLINE, 2);
        List<TimeSlot> slots = List.of(slot(13, 9, 17));

        List<String> unknown = scheduler.validatePlan(List.of(session(stranger, 13, 9, 10)),
                List.of(known), slots);
        List<String> finished = scheduler.validatePlan(List.of(session(done, 13, 9, 10)),
                List.of(done), slots);
        List<String> over = scheduler.validatePlan(
                List.of(session(known, 13, 9, 10), session(known, 13, 11, 12),
                        session(known, 13, 13, 14)), List.of(known), slots);

        assertTrue(unknown.get(0).contains("does not exist"));
        assertTrue(finished.get(0).contains("finished or removed"));
        assertTrue(over.stream().anyMatch(p -> p.contains("scheduled for 180 minutes but only 60 remain")));
    }

    @Test
    void validationRequiresItsInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.validatePlan(null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.createSchedule(null, List.of()));
    }

    // ===== defaultOrder =====

    @Test
    void defaultOrderIsEarliestDeadlineThenHigherPriority() {
        AcademicTask late = task("Late", 60, at(20, 12, 0), 3);
        AcademicTask lowSoon = task("LowSoon", 60, at(15, 12, 0), 1);
        AcademicTask highSoon = task("HighSoon", 60, at(15, 12, 0), 3);

        List<AcademicTask> ordered = Scheduler.defaultOrder(List.of(late, lowSoon, highSoon));

        assertEquals(List.of(highSoon, lowSoon, late), ordered);
    }

    // ===== Helpers =====

    private AcademicTask task(String title, int minutes, LocalDateTime deadline, int priority) {
        return new AcademicTask(course, title, "Coursework", deadline, priority, minutes);
    }

    private static WeeklyPeriod period(DayOfWeek day, int startHour, int endHour, String type) {
        return new WeeklyPeriod(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), type);
    }

    private static LocalDateTime at(int dayOfMay, int hour, int minute) {
        return LocalDateTime.of(2030, 5, dayOfMay, 0, 0).plusHours(hour).plusMinutes(minute);
    }

    private static TimeSlot slot(int dayOfMay, int startHour, int endHour) {
        return new TimeSlot(at(dayOfMay, startHour, 0), at(dayOfMay, endHour, 0));
    }

    private static StudySession session(AcademicTask task, int dayOfMay, int startHour,
                                        int endHour) {
        return new StudySession(task.getId(), at(dayOfMay, startHour, 0),
                at(dayOfMay, endHour, 0));
    }
}
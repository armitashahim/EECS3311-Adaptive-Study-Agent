package ca.yorku.eecs3311.scheduling;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.TimeSlot;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic scheduling rules. The AI only suggests the order of tasks; this class
 * decides where sessions go, so a plan never depends on the model getting arithmetic
 * or calendar rules right.
 *
 * <ul>
 *   <li>Study time is availability minus commitments (commitments win where they overlap).
 *   <li>Tasks are placed in the given order, each into the earliest usable time that ends
 *       before its deadline.
 *   <li>Sessions are at most {@code maxSessionMinutes} long and at least
 *       {@code minSessionMinutes}, except for a task's final remainder.
 *   <li>Work that does not fit is reported as unscheduled minutes.
 * </ul>
 */
public class Scheduler {
    public static final int DEFAULT_MAX_SESSION_MINUTES = 90;
    public static final int DEFAULT_MIN_SESSION_MINUTES = 15;
    private static final int ROUNDING_MINUTES = 15;
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(1900, 1, 1, 0, 0);

    private final int maxSessionMinutes;
    private final int minSessionMinutes;

    public Scheduler() {
        this(DEFAULT_MAX_SESSION_MINUTES, DEFAULT_MIN_SESSION_MINUTES);
    }

    public Scheduler(int maxSessionMinutes, int minSessionMinutes) {
        if (minSessionMinutes < 1 || maxSessionMinutes < minSessionMinutes) {
            throw new IllegalArgumentException(
                    "Session lengths need 1 <= minimum <= maximum.");
        }
        this.maxSessionMinutes = maxSessionMinutes;
        this.minSessionMinutes = minSessionMinutes;
    }

    /**
     * Usable study windows for one week, in time order.
     *
     * @param periods   weekly availability and commitments
     * @param weekStart the Monday that starts the week
     * @param notBefore nothing earlier than this is offered (rounded up to a quarter hour);
     *                  pass the start of the week to plan the whole week
     * @param busy      time already taken, such as sessions that are being kept
     */
    public List<TimeSlot> getAvailableSlots(List<WeeklyPeriod> periods, LocalDate weekStart,
                                            LocalDateTime notBefore, List<TimeSlot> busy) {
        if (periods == null || weekStart == null || notBefore == null || busy == null) {
            throw new IllegalArgumentException("Periods, week, start time and busy time are required.");
        }
        LocalDateTime earliest = roundUp(notBefore);
        List<TimeSlot> slots = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = weekStart.plusDays(offset);
            List<TimeSlot> day = new ArrayList<>();
            for (WeeklyPeriod period : periods) {
                if (period.isAvailability() && period.getDayOfWeek() == date.getDayOfWeek()) {
                    day.add(slot(date, period.getStartTime(), period.getEndTime()));
                }
            }
            for (WeeklyPeriod period : periods) {
                if (!period.isAvailability() && period.getDayOfWeek() == date.getDayOfWeek()) {
                    day = subtract(day, slot(date, period.getStartTime(), period.getEndTime()));
                }
            }
            for (TimeSlot taken : busy) {
                day = subtract(day, taken);
            }
            day = subtract(day, new TimeSlot(LONG_AGO, earliest));
            slots.addAll(day);
        }
        slots.sort(Comparator.comparing(TimeSlot::getStart));
        return merge(slots);
    }

    /**
     * Places the tasks, in the order given, into the slots.
     * Completed or archived tasks and repeated tasks are ignored.
     */
    public ScheduleResult createSchedule(List<AcademicTask> orderedTasks, List<TimeSlot> slots) {
        if (orderedTasks == null || slots == null) {
            throw new IllegalArgumentException("Tasks and slots are required.");
        }
        // Each remaining window as {start, end}; start moves forward as time is used.
        List<LocalDateTime[]> windows = new ArrayList<>();
        for (TimeSlot slot : slots) {
            windows.add(new LocalDateTime[] {slot.getStart(), slot.getEnd()});
        }
        windows.sort(Comparator.comparing(w -> w[0]));

        List<StudySession> sessions = new ArrayList<>();
        Map<String, Integer> unscheduled = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (AcademicTask task : orderedTasks) {
            if (task == null || task.isCompleted() || task.isArchived()
                    || !seen.add(task.getId())) {
                continue;
            }
            int needed = task.getRemainingMinutes();
            for (LocalDateTime[] window : windows) {
                if (needed <= 0) {
                    break;
                }
                LocalDateTime end = window[1].isAfter(task.getDeadline())
                        ? task.getDeadline() : window[1];
                while (needed > 0) {
                    int available = (int) java.time.Duration.between(window[0], end).toMinutes();
                    int chunk = Math.min(Math.min(maxSessionMinutes, available), needed);
                    if (chunk <= 0 || (chunk < minSessionMinutes && chunk != needed)) {
                        break;
                    }
                    sessions.add(new StudySession(task.getId(), window[0],
                            window[0].plusMinutes(chunk)));
                    window[0] = window[0].plusMinutes(chunk);
                    needed -= chunk;
                }
            }
            if (needed > 0) {
                unscheduled.put(task.getId(), needed);
            }
        }
        sessions.sort(Comparator.comparing(StudySession::getStartDateTime));
        return new ScheduleResult(sessions, unscheduled);
    }

    /**
     * Checks a proposed set of sessions. Returns a description of every problem found;
     * an empty list means the proposal is valid.
     */
    public List<String> validatePlan(List<StudySession> sessions, List<AcademicTask> tasks,
                                     List<TimeSlot> availableSlots) {
        if (sessions == null || tasks == null || availableSlots == null) {
            throw new IllegalArgumentException("Sessions, tasks and slots are required.");
        }
        Map<String, AcademicTask> tasksById = new HashMap<>();
        for (AcademicTask task : tasks) {
            tasksById.put(task.getId(), task);
        }

        List<String> problems = new ArrayList<>();
        Map<String, Integer> planned = new HashMap<>();
        for (StudySession session : sessions) {
            AcademicTask task = tasksById.get(session.getTaskId());
            if (task == null) {
                problems.add("A session refers to a task that does not exist.");
                continue;
            }
            String label = "'" + task.getTitle() + "' at " + session.getStartDateTime();
            if (task.isCompleted() || task.isArchived()) {
                problems.add("Session for " + label + " belongs to a task that is finished or removed.");
            }
            if (session.getEndDateTime().isAfter(task.getDeadline())) {
                problems.add("Session for " + label + " ends after the task's deadline.");
            }
            if (!fitsInASlot(session, availableSlots)) {
                problems.add("Session for " + label + " is outside available study time.");
            }
            planned.merge(task.getId(), session.getPlannedMinutes(), Integer::sum);
        }

        List<StudySession> byStart = new ArrayList<>(sessions);
        byStart.sort(Comparator.comparing(StudySession::getStartDateTime));
        for (int i = 1; i < byStart.size(); i++) {
            if (byStart.get(i).getStartDateTime().isBefore(byStart.get(i - 1).getEndDateTime())) {
                problems.add("Two sessions overlap at " + byStart.get(i).getStartDateTime() + ".");
            }
        }

        for (Map.Entry<String, Integer> entry : planned.entrySet()) {
            AcademicTask task = tasksById.get(entry.getKey());
            if (entry.getValue() > task.getRemainingMinutes()) {
                problems.add("'" + task.getTitle() + "' is scheduled for " + entry.getValue()
                        + " minutes but only " + task.getRemainingMinutes() + " remain.");
            }
        }
        return problems;
    }

    /** Earliest deadline first; for equal deadlines, higher priority (3 is highest) first. */
    public static List<AcademicTask> defaultOrder(List<AcademicTask> tasks) {
        List<AcademicTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator.comparing(AcademicTask::getDeadline)
                .thenComparing(Comparator.comparingInt(AcademicTask::getPriority).reversed()));
        return ordered;
    }

    // ===== Time arithmetic =====

    private static boolean fitsInASlot(StudySession session, List<TimeSlot> slots) {
        for (TimeSlot slot : slots) {
            if (!session.getStartDateTime().isBefore(slot.getStart())
                    && !session.getEndDateTime().isAfter(slot.getEnd())) {
                return true;
            }
        }
        return false;
    }

    private static TimeSlot slot(LocalDate date, LocalTime start, LocalTime end) {
        return new TimeSlot(date.atTime(start), date.atTime(end));
    }

    private static LocalDateTime roundUp(LocalDateTime time) {
        LocalDateTime trimmed = time.withSecond(0).withNano(0);
        int extra = trimmed.getMinute() % ROUNDING_MINUTES;
        boolean exact = extra == 0 && trimmed.equals(time);
        return exact ? time : trimmed.plusMinutes(ROUNDING_MINUTES - extra);
    }

    /** Removes the cut from every slot, splitting slots where needed. */
    private static List<TimeSlot> subtract(List<TimeSlot> slots, TimeSlot cut) {
        List<TimeSlot> result = new ArrayList<>();
        for (TimeSlot slot : slots) {
            if (!cut.getStart().isBefore(slot.getEnd()) || !cut.getEnd().isAfter(slot.getStart())) {
                result.add(slot);
                continue;
            }
            if (slot.getStart().isBefore(cut.getStart())) {
                result.add(new TimeSlot(slot.getStart(), cut.getStart()));
            }
            if (cut.getEnd().isBefore(slot.getEnd())) {
                result.add(new TimeSlot(cut.getEnd(), slot.getEnd()));
            }
        }
        return result;
    }

    /** Joins slots that touch, so a session can run across them. Input must be sorted. */
    private static List<TimeSlot> merge(List<TimeSlot> sorted) {
        List<TimeSlot> merged = new ArrayList<>();
        for (TimeSlot slot : sorted) {
            if (!merged.isEmpty()
                    && !slot.getStart().isAfter(merged.get(merged.size() - 1).getEnd())) {
                TimeSlot last = merged.remove(merged.size() - 1);
                LocalDateTime end = slot.getEnd().isAfter(last.getEnd()) ? slot.getEnd() : last.getEnd();
                merged.add(new TimeSlot(last.getStart(), end));
            } else {
                merged.add(slot);
            }
        }
        return merged;
    }
}
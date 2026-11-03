package ca.yorku.eecs3311.agent;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.TimeSlot;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import ca.yorku.eecs3311.scheduling.ScheduleResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Everything the agent's tools need for one planning run, plus the proposal the
 * scheduling tool produces. It is built by the application from saved data.
 */
public class PlanningContext {
    private final LocalDate weekStart;
    private final LocalDateTime now;
    private final boolean revision;
    private final List<AcademicTask> tasks;
    private final List<WeeklyPeriod> periods;
    private final List<TimeSlot> slots;
    private final Map<String, Integer> studiedMinutes;
    private ScheduleResult proposal;

    public PlanningContext(LocalDate weekStart, LocalDateTime now, boolean revision,
                           List<AcademicTask> tasks, List<WeeklyPeriod> periods,
                           List<TimeSlot> slots, Map<String, Integer> studiedMinutes) {
        if (weekStart == null || now == null || tasks == null || periods == null
                || slots == null || studiedMinutes == null) {
            throw new IllegalArgumentException("Planning context values are required.");
        }
        this.weekStart = weekStart;
        this.now = now;
        this.revision = revision;
        this.tasks = List.copyOf(tasks);
        this.periods = List.copyOf(periods);
        this.slots = List.copyOf(slots);
        this.studiedMinutes = Map.copyOf(studiedMinutes);
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public LocalDateTime getNow() {
        return now;
    }

    public boolean isRevision() {
        return revision;
    }

    /** Active tasks that still have work remaining. */
    public List<AcademicTask> getTasks() {
        return tasks;
    }

    public List<WeeklyPeriod> getPeriods() {
        return periods;
    }

    /** Usable study windows for this run. */
    public List<TimeSlot> getSlots() {
        return slots;
    }

    public int getStudiedMinutes(String taskId) {
        return studiedMinutes.getOrDefault(taskId, 0);
    }

    /** The schedule built by the scheduling tool, or null if none has been built yet. */
    public ScheduleResult getProposal() {
        return proposal;
    }

    public void setProposal(ScheduleResult proposal) {
        this.proposal = proposal;
    }
}
package ca.yorku.eecs3311.scheduling;

import ca.yorku.eecs3311.model.StudySession;
import java.util.List;
import java.util.Map;

/**
 * The outcome of building a schedule: the sessions that fit, and the minutes of each
 * task (by task id) that could not be placed.
 */
public record ScheduleResult(List<StudySession> sessions, Map<String, Integer> unscheduledMinutes) {
    public ScheduleResult {
        sessions = List.copyOf(sessions);
        unscheduledMinutes = Map.copyOf(unscheduledMinutes);
    }
}
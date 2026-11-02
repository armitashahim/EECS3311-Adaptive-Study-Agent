package ca.yorku.eecs3311.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A study plan for one week: its sessions, explanation and unscheduled work. */
public class StudyPlan {
    private final String id;
    private final LocalDate weekStartDate;
    private final List<StudySession> sessions = new ArrayList<>();
    private final Map<String, Integer> unscheduledMinutes = new LinkedHashMap<>();

    private boolean accepted;
    private boolean needsRevision;
    private String priorityExplanation;

    // Create a new, empty proposal for a week.
    public StudyPlan(LocalDate weekStartDate) {
        this(UUID.randomUUID().toString(), weekStartDate, false, false,
                "", Map.of(), List.of());
    }

    // Restore a plan from saved data.
    public StudyPlan(String id, LocalDate weekStartDate, boolean accepted,
                     boolean needsRevision, String priorityExplanation,
                     Map<String, Integer> unscheduledMinutes,
                     List<StudySession> sessions) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Plan ID cannot be empty.");
        }

        if (weekStartDate == null) {
            throw new IllegalArgumentException(
                    "Plan week start date is required.");
        }

        if (needsRevision && !accepted) {
            throw new IllegalArgumentException(
                    "Only an accepted plan can need revision.");
        }

        this.id = id;
        this.weekStartDate = weekStartDate;
        this.accepted = accepted;
        this.needsRevision = needsRevision;
        setPriorityExplanation(priorityExplanation);
        setUnscheduledMinutes(unscheduledMinutes);

        if (sessions == null) {
            throw new IllegalArgumentException("Sessions list is required.");
        }
        for (StudySession session : sessions) {
            addSession(session);
        }
    }

    public void addSession(StudySession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session is required.");
        }

        for (StudySession existing : sessions) {
            if (existing.getId().equals(session.getId())) {
                throw new IllegalArgumentException(
                        "Plan already contains session: " + session.getId());
            }
        }

        LocalDateTime weekStart = weekStartDate.atStartOfDay();
        LocalDateTime weekEnd = weekStartDate.plusDays(7).atStartOfDay();
        if (session.getStartDateTime().isBefore(weekStart)
                || session.getEndDateTime().isAfter(weekEnd)) {
            throw new IllegalArgumentException(
                    "Session must fall within the plan's week.");
        }

        sessions.add(session);
    }

    // Accepting a plan (or a revision of it) clears any revision warning.
    public void accept() {
        this.accepted = true;
        this.needsRevision = false;
    }

    // Only an accepted plan can need revision; an unaccepted proposal is not flagged.
    public void markNeedsRevision() {
        if (accepted) {
            this.needsRevision = true;
        }
    }

    public void setPriorityExplanation(String explanation) {
        this.priorityExplanation = explanation == null ? "" : explanation.strip();
    }

    public void setUnscheduledMinutes(Map<String, Integer> minutesByTaskId) {
        if (minutesByTaskId == null) {
            throw new IllegalArgumentException(
                    "Unscheduled minutes map is required.");
        }

        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : minutesByTaskId.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException(
                        "Unscheduled minutes need a task ID and a positive amount.");
            }
            copy.put(entry.getKey(), entry.getValue());
        }

        // Replace only after every entry has passed validation.
        unscheduledMinutes.clear();
        unscheduledMinutes.putAll(copy);
    }

    public String getId() {
        return id;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean needsRevision() {
        return needsRevision;
    }

    public String getPriorityExplanation() {
        return priorityExplanation;
    }

    public Map<String, Integer> getUnscheduledMinutes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(unscheduledMinutes));
    }

    public List<StudySession> getSessions() {
        return List.copyOf(sessions);
    }
}
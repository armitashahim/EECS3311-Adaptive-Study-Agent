package ca.yorku.eecs3311.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/** One planned block of study time for a task, and its recorded outcome. */
public class StudySession {
    public static final String SCHEDULED = "scheduled";
    public static final String COMPLETED = "completed";
    public static final String PARTIAL = "partial";
    public static final String MISSED = "missed";

    private final String id;
    private final String taskId;
    private final LocalDateTime startDateTime;
    private final LocalDateTime endDateTime;

    private String status;
    private int actualMinutes;

    // Create a new, not yet recorded session.
    public StudySession(String taskId, LocalDateTime startDateTime,
                        LocalDateTime endDateTime) {
        this(UUID.randomUUID().toString(), taskId, startDateTime,
                endDateTime, SCHEDULED, 0);
    }

    // Restore a session from saved data.
    public StudySession(String id, String taskId, LocalDateTime startDateTime,
                        LocalDateTime endDateTime, String status,
                        int actualMinutes) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Session ID cannot be empty.");
        }

        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException(
                    "A session must belong to a task.");
        }

        if (startDateTime == null || endDateTime == null) {
            throw new IllegalArgumentException(
                    "Session start and end times are required.");
        }

        if (!endDateTime.isAfter(startDateTime)) {
            throw new IllegalArgumentException(
                    "Session end time must be after its start time.");
        }

        String normalizedStatus = normalizeStatus(status);
        if (actualMinutes < 0) {
            throw new IllegalArgumentException(
                    "Actual minutes cannot be negative.");
        }

        if ((normalizedStatus.equals(SCHEDULED) || normalizedStatus.equals(MISSED))
                && actualMinutes != 0) {
            throw new IllegalArgumentException(
                    "A scheduled or missed session must have zero actual minutes.");
        }

        this.id = id;
        this.taskId = taskId;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.status = normalizedStatus;
        this.actualMinutes = actualMinutes;
    }

    // Record (or re-record) what happened. A missed session records zero minutes.
    public void recordOutcome(String status, int actualMinutes) {
        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus.equals(SCHEDULED)) {
            throw new IllegalArgumentException(
                    "An outcome must be completed, partial, or missed.");
        }

        if (actualMinutes < 0) {
            throw new IllegalArgumentException(
                    "Actual minutes cannot be negative.");
        }

        // Assign only after every value has passed validation.
        this.status = normalizedStatus;
        this.actualMinutes = normalizedStatus.equals(MISSED) ? 0 : actualMinutes;
    }

    public int getPlannedMinutes() {
        return (int) Duration.between(startDateTime, endDateTime).toMinutes();
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Session status cannot be empty.");
        }

        String normalized = status.strip().toLowerCase(Locale.ROOT);
        if (!normalized.equals(SCHEDULED) && !normalized.equals(COMPLETED)
                && !normalized.equals(PARTIAL) && !normalized.equals(MISSED)) {
            throw new IllegalArgumentException(
                    "Unknown session status: " + status.strip());
        }
        return normalized;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public String getStatus() {
        return status;
    }

    public int getActualMinutes() {
        return actualMinutes;
    }
}
package ca.yorku.eecs3311.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class AcademicTask {
    private final String id;
    private final Course course;

    private String title;
    private String type;
    private LocalDateTime deadline;
    private int priority;

    private final int originalEstimatedMinutes;
    private int acceptedEstimatedMinutes;
    private int remainingMinutes;

    private boolean completed;
    private boolean archived;

    // Create a new task.
    public AcademicTask(Course course, String title, String type,
                        LocalDateTime deadline, int priority,
                        int estimatedMinutes) {

        this(UUID.randomUUID().toString(), course, title, type,
                deadline, priority, estimatedMinutes,
                estimatedMinutes, estimatedMinutes, false, false);
    }

    // Restore a task from saved data.
    public AcademicTask(String id, Course course, String title,
                        String type, LocalDateTime deadline,
                        int priority, int originalEstimatedMinutes,
                        int acceptedEstimatedMinutes, int remainingMinutes,
                        boolean completed, boolean archived) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Task ID cannot be empty.");
        }

        if (course == null) {
            throw new IllegalArgumentException(
                    "A task must belong to a course.");
        }

        requirePositiveMinutes(originalEstimatedMinutes);
        requirePositiveMinutes(acceptedEstimatedMinutes);

        if (completed && remainingMinutes != 0) {
            throw new IllegalArgumentException(
                    "A completed task must have zero remaining minutes.");
        }

        if (!completed && remainingMinutes <= 0) {
            throw new IllegalArgumentException(
                    "An unfinished task must have positive remaining minutes.");
        }

        this.id = id;
        this.course = course;

        updateDetails(title, type, deadline, priority);

        this.originalEstimatedMinutes = originalEstimatedMinutes;
        this.acceptedEstimatedMinutes = acceptedEstimatedMinutes;
        this.remainingMinutes = remainingMinutes;
        this.completed = completed;
        this.archived = archived;
    }
    
    // Edit task details without changing its identity or estimates.
    public void updateDetails(String title, String type,
                              LocalDateTime deadline, int priority) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Task title cannot be empty.");
        }

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Task type cannot be empty.");
        }

        if (deadline == null) {
            throw new IllegalArgumentException(
                    "Task deadline is required.");
        }

        if (priority < 1 || priority > 3) {
            throw new IllegalArgumentException(
                    "Priority must be 1, 2, or 3.");
        }

        // Assign only after every value has passed validation.
        this.title = title.strip();
        this.type = type.strip();
        this.deadline = deadline;
        this.priority = priority;
    }

    public void markCompleted() {
        this.completed = true;
        this.remainingMinutes = 0;
    }

    public void updateRemainingEffort(int minutes) {
        if (completed) {
            throw new IllegalStateException(
                    "Cannot update remaining effort for a completed task.");
        }

        requirePositiveMinutes(minutes);
        this.remainingMinutes = minutes;
    }

    /*
     * StudyApplication must check session history before calling this:
     * only an unstarted task can accept a new duration estimate.
     */
    public void acceptEstimate(int minutes) {
        if (completed || archived) {
            throw new IllegalStateException(
                    "Cannot adjust a completed or archived task.");
        }

        requirePositiveMinutes(minutes);

        this.acceptedEstimatedMinutes = minutes;
        this.remainingMinutes = minutes;
    }

    public void archive() {
        this.archived = true;
    }

    private static void requirePositiveMinutes(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be greater than zero.");
        }
    }

    public String getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public int getPriority() {
        return priority;
    }

    public int getOriginalEstimatedMinutes() {
        return originalEstimatedMinutes;
    }

    public int getAcceptedEstimatedMinutes() {
        return acceptedEstimatedMinutes;
    }

    public int getRemainingMinutes() {
        return remainingMinutes;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isArchived() {
        return archived;
    }
}

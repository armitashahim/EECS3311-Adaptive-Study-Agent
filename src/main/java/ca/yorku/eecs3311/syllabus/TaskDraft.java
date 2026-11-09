package ca.yorku.eecs3311.syllabus;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A task proposed from a syllabus that the student can still correct. Its id becomes the
 * saved task's id, so confirming the same draft twice never creates a duplicate task.
 * The student must supply anything missing (type, deadline, priority, duration).
 */
public class TaskDraft {
    private final String id;
    private final String note;
    private String title;
    private String type;
    private LocalDateTime deadline;
    private Integer priority;
    private Integer estimatedMinutes;

    /** A draft with a title and whatever the model could determine; others may be null. */
    public TaskDraft(String title, String type, LocalDateTime deadline, String note) {
        this(UUID.randomUUID().toString(), title, type, deadline, note);
    }

    public TaskDraft(String id, String title, String type, LocalDateTime deadline, String note) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Draft ID cannot be empty.");
        }
        this.id = id;
        this.note = note == null ? "" : note.strip();
        setTitle(title);
        this.type = type == null || type.isBlank() ? null : type.strip();
        this.deadline = deadline;
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title cannot be empty.");
        }
        this.title = title.strip();
    }

    public void setType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Task type cannot be empty.");
        }
        this.type = type.strip();
    }

    public void setDeadline(LocalDateTime deadline) {
        if (deadline == null) {
            throw new IllegalArgumentException("Task deadline is required.");
        }
        this.deadline = deadline;
    }

    public void setPriority(int priority) {
        if (priority < 1 || priority > 3) {
            throw new IllegalArgumentException("Priority must be 1, 2, or 3.");
        }
        this.priority = priority;
    }

    public void setEstimatedMinutes(int minutes) {
        if (minutes <= 0) {
            throw new IllegalArgumentException("Duration must be greater than zero.");
        }
        this.estimatedMinutes = minutes;
    }

    /** Names of the details the student still has to provide. */
    public List<String> getMissingFields() {
        List<String> missing = new ArrayList<>();
        if (type == null) {
            missing.add("type");
        }
        if (deadline == null) {
            missing.add("deadline");
        }
        if (priority == null) {
            missing.add("priority");
        }
        if (estimatedMinutes == null) {
            missing.add("duration");
        }
        return missing;
    }

    public boolean isComplete() {
        return getMissingFields().isEmpty();
    }

    /** Builds the task this draft describes; every detail must have been provided. */
    public AcademicTask toTask(Course course) {
        if (!isComplete()) {
            throw new IllegalStateException(
                    "The draft '" + title + "' still needs: " + String.join(", ", getMissingFields()));
        }
        return new AcademicTask(id, course, title, type, deadline, priority,
                estimatedMinutes, estimatedMinutes, estimatedMinutes, false, false);
    }

    public String getId() {
        return id;
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

    public Integer getPriority() {
        return priority;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    /** The syllabus wording the proposal came from, if the model quoted it. */
    public String getNote() {
        return note;
    }
}
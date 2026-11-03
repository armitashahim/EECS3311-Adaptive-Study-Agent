package ca.yorku.eecs3311.agent;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.TimeSlot;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.format.DateTimeFormatter;

/** Read-only tool that gives the model the student's tasks, history and weekly time. */
public class StudyDataTool implements AgentTool {
    public static final String NAME = "get_study_data";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final PlanningContext context;

    public StudyDataTool(PlanningContext context) {
        if (context == null) {
            throw new IllegalArgumentException("A planning context is required.");
        }
        this.context = context;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Returns the tasks that still need study time (id, course, title, type, deadline, "
                + "priority where 3 is highest, remainingMinutes, studiedMinutes so far), the weekly "
                + "availability and commitments, and the minutes of study time open this week. "
                + "Takes no arguments.";
    }

    @Override
    public String execute(JsonObject arguments) {
        JsonObject result = new JsonObject();
        result.addProperty("mode", context.isRevision() ? "revision" : "new plan");
        result.addProperty("weekStart", context.getWeekStart().toString());
        result.addProperty("now", context.getNow().toString());

        JsonArray tasks = new JsonArray();
        for (AcademicTask task : context.getTasks()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", task.getId());
            entry.addProperty("course", task.getCourse().getName());
            entry.addProperty("title", task.getTitle());
            entry.addProperty("type", task.getType());
            entry.addProperty("deadline", task.getDeadline().toString());
            entry.addProperty("priority", task.getPriority());
            entry.addProperty("remainingMinutes", task.getRemainingMinutes());
            entry.addProperty("studiedMinutes", context.getStudiedMinutes(task.getId()));
            tasks.add(entry);
        }
        result.add("tasks", tasks);

        JsonArray availability = new JsonArray();
        JsonArray commitments = new JsonArray();
        for (WeeklyPeriod period : context.getPeriods()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("day", period.getDayOfWeek().toString());
            entry.addProperty("start", period.getStartTime().format(TIME));
            entry.addProperty("end", period.getEndTime().format(TIME));
            (period.isAvailability() ? availability : commitments).add(entry);
        }
        result.add("availability", availability);
        result.add("commitments", commitments);

        int open = context.getSlots().stream().mapToInt(TimeSlot::getDurationMinutes).sum();
        result.addProperty("openStudyMinutesThisWeek", open);
        return result.toString();
    }
}
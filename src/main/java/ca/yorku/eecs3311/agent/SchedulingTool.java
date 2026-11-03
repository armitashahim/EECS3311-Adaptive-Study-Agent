package ca.yorku.eecs3311.agent;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.scheduling.ScheduleResult;
import ca.yorku.eecs3311.scheduling.Scheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool that turns the model's task ordering into sessions. The model only chooses the
 * order; the {@link Scheduler} places the sessions and checks every rule.
 */
public class SchedulingTool implements AgentTool {
    public static final String NAME = "create_schedule";

    private final PlanningContext context;
    private final Scheduler scheduler;

    public SchedulingTool(PlanningContext context, Scheduler scheduler) {
        if (context == null || scheduler == null) {
            throw new IllegalArgumentException("A planning context and scheduler are required.");
        }
        this.context = context;
        this.scheduler = scheduler;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Builds the weekly schedule. Arguments: {\"order\": [task ids, most important "
                + "first]}. Any task you leave out is added at the end by deadline. Returns how "
                + "many sessions were created and which work could not fit.";
    }

    @Override
    public String execute(JsonObject arguments) {
        JsonElement order = arguments.get("order");
        if (order == null || !order.isJsonArray()) {
            throw new IllegalArgumentException(
                    "Argument 'order' must be a list of task ids.");
        }

        Map<String, AcademicTask> byId = new LinkedHashMap<>();
        context.getTasks().forEach(task -> byId.put(task.getId(), task));

        Set<String> ids = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (JsonElement element : order.getAsJsonArray()) {
            String id = element.isJsonPrimitive() ? element.getAsString() : "";
            if (byId.containsKey(id)) {
                ids.add(id);
            } else {
                unknown.add(id);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown task ids: " + unknown
                    + ". Use the ids from get_study_data.");
        }

        List<AcademicTask> ordered = new ArrayList<>();
        ids.forEach(id -> ordered.add(byId.get(id)));
        Scheduler.defaultOrder(context.getTasks()).stream()
                .filter(task -> !ids.contains(task.getId()))
                .forEach(ordered::add);

        ScheduleResult result = scheduler.createSchedule(ordered, context.getSlots());
        List<String> problems = scheduler.validatePlan(
                result.sessions(), context.getTasks(), context.getSlots());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The schedule failed validation: " + problems);
        }
        context.setProposal(result);

        JsonObject summary = new JsonObject();
        summary.addProperty("sessionsCreated", result.sessions().size());
        summary.addProperty("scheduledMinutes", result.sessions().stream()
                .mapToInt(session -> session.getPlannedMinutes()).sum());
        JsonArray unscheduled = new JsonArray();
        result.unscheduledMinutes().forEach((taskId, minutes) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("taskId", taskId);
            entry.addProperty("title", byId.get(taskId).getTitle());
            entry.addProperty("minutes", minutes);
            unscheduled.add(entry);
        });
        summary.add("unscheduled", unscheduled);
        JsonArray orderUsed = new JsonArray();
        ordered.forEach(task -> orderUsed.add(task.getId()));
        summary.add("orderUsed", orderUsed);
        return summary.toString();
    }
}
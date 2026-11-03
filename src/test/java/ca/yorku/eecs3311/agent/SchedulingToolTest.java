package ca.yorku.eecs3311.agent;

import static ca.yorku.eecs3311.agent.AgentTestSupport.FAR;
import static ca.yorku.eecs3311.agent.AgentTestSupport.context;
import static ca.yorku.eecs3311.agent.AgentTestSupport.slot;
import static ca.yorku.eecs3311.agent.AgentTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.scheduling.Scheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulingToolTest {
    private static JsonObject order(String... ids) {
        JsonArray array = new JsonArray();
        for (String id : ids) {
            array.add(id);
        }
        JsonObject args = new JsonObject();
        args.add("order", array);
        return args;
    }

    @Test
    void buildsAScheduleInTheRequestedOrderAndStoresTheProposal() {
        AcademicTask first = task("First", 60, FAR, 1);
        AcademicTask second = task("Second", 60, FAR, 3);
        PlanningContext context = context(List.of(first, second), List.of(slot(13, 9, 12)));
        SchedulingTool tool = new SchedulingTool(context, new Scheduler());

        JsonObject summary = JsonParser.parseString(
                tool.execute(order(second.getId(), first.getId()))).getAsJsonObject();

        assertEquals(2, summary.get("sessionsCreated").getAsInt());
        assertEquals(120, summary.get("scheduledMinutes").getAsInt());
        assertEquals(0, summary.getAsJsonArray("unscheduled").size());
        assertEquals(second.getId(), context.getProposal().sessions().get(0).getTaskId());
    }

    @Test
    void tasksLeftOutAreAppendedByDeadline() {
        AcademicTask soon = task("Soon", 30, LocalDateTime.of(2030, 5, 15, 12, 0), 1);
        AcademicTask later = task("Later", 30, FAR, 1);
        AcademicTask named = task("Named", 30, FAR, 1);
        PlanningContext context = context(List.of(later, named, soon), List.of(slot(13, 9, 12)));

        JsonObject summary = JsonParser.parseString(new SchedulingTool(context, new Scheduler())
                .execute(order(named.getId()))).getAsJsonObject();

        JsonArray used = summary.getAsJsonArray("orderUsed");
        assertEquals(named.getId(), used.get(0).getAsString());
        assertEquals(soon.getId(), used.get(1).getAsString());
        assertEquals(later.getId(), used.get(2).getAsString());
    }

    @Test
    void workThatDoesNotFitIsReportedWithTheTaskTitle() {
        AcademicTask big = task("Big project", 300, FAR, 2);
        PlanningContext context = context(List.of(big), List.of(slot(13, 9, 11)));

        JsonObject summary = JsonParser.parseString(new SchedulingTool(context, new Scheduler())
                .execute(order(big.getId()))).getAsJsonObject();

        JsonObject unscheduled = summary.getAsJsonArray("unscheduled").get(0).getAsJsonObject();
        assertEquals("Big project", unscheduled.get("title").getAsString());
        assertEquals(180, unscheduled.get("minutes").getAsInt());
        assertEquals(120, summary.get("scheduledMinutes").getAsInt());
    }

    @Test
    void unknownIdsAndMissingOrderAreRejectedAndNoProposalIsStored() {
        AcademicTask real = task("Real", 60, FAR, 2);
        PlanningContext context = context(List.of(real), List.of(slot(13, 9, 12)));
        SchedulingTool tool = new SchedulingTool(context, new Scheduler());

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(order("made-up-id")));
        assertTrue(unknown.getMessage().contains("made-up-id"));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(new JsonObject()));
        JsonObject notAList = new JsonObject();
        notAList.addProperty("order", "abc");
        assertThrows(IllegalArgumentException.class, () -> tool.execute(notAList));
        assertNull(context.getProposal());
    }

    @Test
    void anEmptyOrderStillSchedulesEveryTask() {
        AcademicTask only = task("Only", 60, FAR, 2);
        PlanningContext context = context(List.of(only), List.of(slot(13, 9, 12)));

        new SchedulingTool(context, new Scheduler()).execute(order());

        assertEquals(1, context.getProposal().sessions().size());
    }

    @Test
    void constructorRequiresItsInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new SchedulingTool(null, new Scheduler()));
        assertThrows(IllegalArgumentException.class,
                () -> new SchedulingTool(context(List.of(), List.of()), null));
    }
}

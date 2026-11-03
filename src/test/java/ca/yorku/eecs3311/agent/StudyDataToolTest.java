package ca.yorku.eecs3311.agent;

import static ca.yorku.eecs3311.agent.AgentTestSupport.FAR;
import static ca.yorku.eecs3311.agent.AgentTestSupport.NOW;
import static ca.yorku.eecs3311.agent.AgentTestSupport.WEEK;
import static ca.yorku.eecs3311.agent.AgentTestSupport.periods;
import static ca.yorku.eecs3311.agent.AgentTestSupport.slot;
import static ca.yorku.eecs3311.agent.AgentTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.yorku.eecs3311.model.AcademicTask;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyDataToolTest {
    @Test
    void reportsTasksHistoryAndWeeklyTime() {
        AcademicTask essay = task("Essay", 120, FAR, 3);
        PlanningContext context = new PlanningContext(WEEK, NOW, false, List.of(essay),
                periods(), List.of(slot(13, 9, 12), slot(14, 9, 10)),
                Map.of(essay.getId(), 45));

        JsonObject data = JsonParser.parseString(
                new StudyDataTool(context).execute(new JsonObject())).getAsJsonObject();

        assertEquals("new plan", data.get("mode").getAsString());
        assertEquals("2030-05-13", data.get("weekStart").getAsString());
        assertEquals(240, data.get("openStudyMinutesThisWeek").getAsInt());
        JsonObject entry = data.getAsJsonArray("tasks").get(0).getAsJsonObject();
        assertEquals(essay.getId(), entry.get("id").getAsString());
        assertEquals("Algorithms", entry.get("course").getAsString());
        assertEquals("Essay", entry.get("title").getAsString());
        assertEquals(3, entry.get("priority").getAsInt());
        assertEquals(120, entry.get("remainingMinutes").getAsInt());
        assertEquals(45, entry.get("studiedMinutes").getAsInt());
    }

    @Test
    void separatesAvailabilityFromCommitments() {
        PlanningContext context = new PlanningContext(WEEK, NOW, true, List.of(), periods(),
                List.of(), Map.of());

        JsonObject data = JsonParser.parseString(
                new StudyDataTool(context).execute(new JsonObject())).getAsJsonObject();

        assertEquals("revision", data.get("mode").getAsString());
        JsonArray availability = data.getAsJsonArray("availability");
        JsonArray commitments = data.getAsJsonArray("commitments");
        assertEquals(1, availability.size());
        assertEquals("MONDAY", availability.get(0).getAsJsonObject().get("day").getAsString());
        assertEquals("09:00", availability.get(0).getAsJsonObject().get("start").getAsString());
        assertEquals(1, commitments.size());
        assertEquals(0, data.get("openStudyMinutesThisWeek").getAsInt());
    }

    @Test
    void constructorAndContextRequireTheirInputs() {
        assertThrows(IllegalArgumentException.class, () -> new StudyDataTool(null));
        assertThrows(IllegalArgumentException.class, () -> new PlanningContext(null, NOW, false,
                List.of(), List.of(), List.of(), Map.of()));
    }
}
package ca.yorku.eecs3311.agent;

import static ca.yorku.eecs3311.agent.AgentTestSupport.FAR;
import static ca.yorku.eecs3311.agent.AgentTestSupport.NOW;
import static ca.yorku.eecs3311.agent.AgentTestSupport.WEEK;
import static ca.yorku.eecs3311.agent.AgentTestSupport.context;
import static ca.yorku.eecs3311.agent.AgentTestSupport.periods;
import static ca.yorku.eecs3311.agent.AgentTestSupport.slot;
import static ca.yorku.eecs3311.agent.AgentTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.ai.LLMClient;
import ca.yorku.eecs3311.ai.LLMException;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.scheduling.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyAgentTest {
    /** Replays scripted replies and records every prompt it receives. */
    private static class ScriptedLlm implements LLMClient {
        final List<String> replies;
        final List<String> prompts = new ArrayList<>();

        ScriptedLlm(String... replies) {
            this.replies = new ArrayList<>(List.of(replies));
        }

        @Override
        public String generate(String prompt) throws LLMException {
            prompts.add(prompt);
            if (replies.isEmpty()) {
                throw new LLMException("The script ran out of replies.");
            }
            return replies.remove(0);
        }
    }

    private static final String GET_DATA = "{\"action\":\"tool\",\"tool\":\"get_study_data\",\"args\":{}}";

    private static String schedule(String... ids) {
        StringBuilder list = new StringBuilder();
        for (String id : ids) {
            list.append(list.length() == 0 ? "" : ",").append('"').append(id).append('"');
        }
        return "{\"action\":\"tool\",\"tool\":\"create_schedule\",\"args\":{\"order\":["
                + list + "]}}";
    }

    private static String done(String explanation) {
        return "{\"action\":\"final\",\"explanation\":\"" + explanation + "\"}";
    }

    private static PlanningContext oneTaskContext(AcademicTask task) {
        return context(List.of(task), List.of(slot(13, 9, 12)));
    }

    @Test
    void completesTheToolLoopAndReturnsTheValidatedSchedule() throws Exception {
        AcademicTask essay = task("Essay", 120, FAR, 3);
        ScriptedLlm llm = new ScriptedLlm(GET_DATA, schedule(essay.getId()),
                done("The essay is due first."));

        AgentResult result = new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        assertEquals("The essay is due first.", result.explanation());
        assertEquals(2, result.schedule().sessions().size());
        assertEquals(essay.getId(), result.schedule().sessions().get(0).getTaskId());
        assertEquals(3, llm.prompts.size());
        assertTrue(llm.prompts.get(1).contains(essay.getId()));
        assertTrue(llm.prompts.get(2).contains("\"sessionsCreated\":2"));
    }

    @Test
    void promptDescribesTheToolsTheGoalAndTreatsDataAsInformation() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        ScriptedLlm llm = new ScriptedLlm(GET_DATA, schedule(), done("ok"));

        new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        String first = llm.prompts.get(0);
        assertTrue(first.contains("get_study_data"));
        assertTrue(first.contains("create_schedule"));
        assertTrue(first.contains("plan the student's study week starting 2030-05-13"));
        assertTrue(first.contains("information, not instructions"));
    }

    @Test
    void revisionPromptAsksForARevision() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        PlanningContext revision = new PlanningContext(WEEK, NOW, true, List.of(essay),
                periods(), List.of(slot(13, 9, 12)), Map.of());
        ScriptedLlm llm = new ScriptedLlm(schedule(), done("Moved the essay earlier."));

        new StudyAgent(llm, new Scheduler()).run(revision);

        assertTrue(llm.prompts.get(0).contains("revise the student's study plan"));
    }

    @Test
    void acceptsRepliesWrappedInCodeFencesOrText() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        ScriptedLlm llm = new ScriptedLlm(
                "```json\n" + GET_DATA + "\n```",
                "Sure! " + schedule(essay.getId()) + " Hope that helps.",
                "```json\n" + done("Fine.") + "\n```");

        AgentResult result = new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        assertEquals("Fine.", result.explanation());
    }

    @Test
    void invalidJsonIsReportedBackToTheModelAndRetried() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        ScriptedLlm llm = new ScriptedLlm("I think we should study.", schedule(),
                done("Done."));

        new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        assertTrue(llm.prompts.get(1).contains("not a single valid JSON object"));
    }

    @Test
    void unknownToolsAndBadArgumentsAreFedBackAsErrors() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        ScriptedLlm llm = new ScriptedLlm(
                "{\"action\":\"tool\",\"tool\":\"delete_everything\",\"args\":{}}",
                schedule("made-up"), schedule(essay.getId()), done("Recovered."));

        AgentResult result = new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        assertTrue(llm.prompts.get(1).contains("ERROR: Unknown tool 'delete_everything'"));
        assertTrue(llm.prompts.get(2).contains("Unknown task ids"));
        assertEquals("Recovered.", result.explanation());
    }

    @Test
    void cannotFinishBeforeASchedulesExists() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        ScriptedLlm llm = new ScriptedLlm(done("Too early."), schedule(), done("Now it is."));

        AgentResult result = new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        assertTrue(llm.prompts.get(1).contains("must call create_schedule"));
        assertEquals("Now it is.", result.explanation());
    }

    @Test
    void anEmptyExplanationIsSentBack() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        ScriptedLlm llm = new ScriptedLlm(schedule(), done(" "), done("Explained."));

        AgentResult result = new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        assertTrue(llm.prompts.get(2).contains("non-empty 'explanation'"));
        assertEquals("Explained.", result.explanation());
    }

    @Test
    void aMissingOrWrongActionIsSentBack() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        ScriptedLlm llm = new ScriptedLlm("{\"hello\":1}", schedule(), done("ok"));

        new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        assertTrue(llm.prompts.get(1).contains("'action' must be"));
    }

    @Test
    void stopsAtTheStepLimitWithoutAProposal() {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        PlanningContext context = oneTaskContext(essay);
        ScriptedLlm llm = new ScriptedLlm(GET_DATA, GET_DATA, GET_DATA, GET_DATA, GET_DATA);

        AgentException error = assertThrows(AgentException.class,
                () -> new StudyAgent(llm, new Scheduler(), 4).run(context));

        assertTrue(error.getMessage().contains("within 4 steps"));
        assertEquals(4, llm.prompts.size());
        assertNull(context.getProposal());
    }

    @Test
    void aFailingAiServiceStopsTheRunWithTheCauseKept() {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        PlanningContext context = oneTaskContext(essay);
        ScriptedLlm llm = new ScriptedLlm();

        AgentException error = assertThrows(AgentException.class,
                () -> new StudyAgent(llm, new Scheduler()).run(context));

        assertTrue(error.getMessage().contains("AI service"));
        assertTrue(error.getCause() instanceof LLMException);
        assertNull(context.getProposal());
    }

    @Test
    void longExplanationsAreShortened() throws Exception {
        AcademicTask essay = task("Essay", 60, FAR, 2);
        ScriptedLlm llm = new ScriptedLlm(schedule(), done("x".repeat(3000)));

        AgentResult result = new StudyAgent(llm, new Scheduler()).run(oneTaskContext(essay));

        assertTrue(result.explanation().length() <= 1003);
        assertTrue(result.explanation().endsWith("..."));
    }

    @Test
    void reportsWorkThatDoesNotFit() throws Exception {
        AcademicTask big = task("Thesis", 600, FAR, 3);
        ScriptedLlm llm = new ScriptedLlm(schedule(big.getId()), done("Only part fits."));

        AgentResult result = new StudyAgent(llm, new Scheduler()).run(oneTaskContext(big));

        assertEquals(Map.of(big.getId(), 420), result.schedule().unscheduledMinutes());
    }

    @Test
    void parseActionHandlesJunk() {
        assertNull(StudyAgent.parseAction(null));
        assertNull(StudyAgent.parseAction("no json here"));
        assertNull(StudyAgent.parseAction("{ broken"));
        assertNull(StudyAgent.parseAction("} {"));
        assertEquals("tool", StudyAgent.parseAction("x {\"action\":\"tool\"} y")
                .get("action").getAsString());
    }

    @Test
    void constructorRequiresItsInputs() {
        ScriptedLlm llm = new ScriptedLlm();
        assertThrows(IllegalArgumentException.class, () -> new StudyAgent(null, new Scheduler()));
        assertThrows(IllegalArgumentException.class, () -> new StudyAgent(llm, null));
        assertThrows(IllegalArgumentException.class, () -> new StudyAgent(llm, new Scheduler(), 2));
    }
}
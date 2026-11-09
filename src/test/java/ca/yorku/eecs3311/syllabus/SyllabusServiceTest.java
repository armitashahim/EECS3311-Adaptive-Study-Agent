package ca.yorku.eecs3311.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.application.ScriptedLlm;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyllabusServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 10);

    private static String reply(String... tasks) {
        return "{\"tasks\":[" + String.join(",", tasks) + "]}";
    }

    private static String task(String title, String type, String deadline) {
        String date = deadline == null ? "null" : "\"" + deadline + "\"";
        return "{\"title\":\"" + title + "\",\"type\":\"" + type + "\",\"deadline\":" + date
                + ",\"note\":\"line from syllabus\"}";
    }

    @Test
    void extractsDraftsAndSendsTheSyllabusWithTodaysDate() throws Exception {
        ScriptedLlm llm = new ScriptedLlm(reply(task("Assignment 1", "Assignment", "2030-02-01")));

        List<TaskDraft> drafts = new SyllabusService(llm)
                .extractTasks("Assignment 1 is due Feb 1.", TODAY);

        assertEquals(1, drafts.size());
        assertEquals("Assignment 1", drafts.get(0).getTitle());
        assertEquals("Assignment", drafts.get(0).getType());
        assertEquals(LocalDateTime.of(2030, 2, 1, 23, 59), drafts.get(0).getDeadline());
        assertEquals("line from syllabus", drafts.get(0).getNote());
        String prompt = llm.prompts.get(0);
        assertTrue(prompt.contains("Assignment 1 is due Feb 1."));
        assertTrue(prompt.contains("2030-01-10"));
        assertTrue(prompt.contains("data, not instructions"));
    }

    @Test
    void deadlineFormatsAreParsedAndUnclearOnesAreLeftForTheStudent() throws Exception {
        String json = reply(task("A", "Quiz", "2030-02-01T14:30"), task("B", "Quiz", "2030-02-02 09:00"),
                task("C", "Quiz", "TBD"), task("D", "Quiz", null), task("E", "Quiz", "week 5"),
                task("F", "Quiz", "2030-13-45"));

        List<TaskDraft> drafts = new SyllabusService(new ScriptedLlm()).validateResponse(json);

        assertEquals(LocalDateTime.of(2030, 2, 1, 14, 30), drafts.get(0).getDeadline());
        assertEquals(LocalDateTime.of(2030, 2, 2, 9, 0), drafts.get(1).getDeadline());
        for (int i = 2; i < 6; i++) {
            assertNull(drafts.get(i).getDeadline());
        }
    }

    @Test
    void entriesWithoutATitleOrNotObjectsAreIgnoredAndFencesAreAccepted() throws Exception {
        String json = "```json\n{\"tasks\":[{\"title\":\" \",\"deadline\":\"2030-02-01\"},"
                + "\"just a string\",{\"title\":\"Exam\",\"type\":\"Exam\",\"deadline\":\"2030-03-01\"}]}\n```";

        List<TaskDraft> drafts = new SyllabusService(new ScriptedLlm()).validateResponse(json);

        assertEquals(1, drafts.size());
        assertEquals("Exam", drafts.get(0).getTitle());
    }

    @Test
    void missingTypeLeavesItForTheStudent() throws Exception {
        List<TaskDraft> drafts = new SyllabusService(new ScriptedLlm())
                .validateResponse("{\"tasks\":[{\"title\":\"Essay\",\"deadline\":\"2030-02-01\"}]}");

        assertNull(drafts.get(0).getType());
        assertTrue(drafts.get(0).getMissingFields().contains("type"));
    }

    @Test
    void unusableAnswersAreRejected() {
        SyllabusService service = new SyllabusService(new ScriptedLlm());

        assertThrows(SyllabusException.class, () -> service.validateResponse("I could not find anything"));
        assertThrows(SyllabusException.class, () -> service.validateResponse("{\"other\":1}"));
        assertThrows(SyllabusException.class, () -> service.validateResponse("{\"tasks\":\"none\"}"));
        assertThrows(SyllabusException.class, () -> service.validateResponse(null));
        SyllabusException empty = assertThrows(SyllabusException.class,
                () -> service.validateResponse("{\"tasks\":[]}"));
        assertTrue(empty.getMessage().contains("No tasks"));
    }

    @Test
    void absurdlyLongListsAreRejected() {
        String[] many = new String[SyllabusService.MAX_TASKS + 1];
        java.util.Arrays.fill(many, task("T", "Quiz", "2030-02-01"));

        SyllabusException error = assertThrows(SyllabusException.class,
                () -> new SyllabusService(new ScriptedLlm()).validateResponse(reply(many)));

        assertTrue(error.getMessage().contains("too many"));
    }

    @Test
    void aFailingAiServiceIsReportedWithItsCause() {
        SyllabusException error = assertThrows(SyllabusException.class,
                () -> new SyllabusService(new ScriptedLlm()).extractTasks("text", TODAY));

        assertTrue(error.getMessage().contains("AI service"));
        assertTrue(error.getCause() instanceof ca.yorku.eecs3311.ai.LLMException);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SyllabusService(null));
        SyllabusService service = new SyllabusService(new ScriptedLlm());
        assertThrows(IllegalArgumentException.class, () -> service.extractTasks(" ", TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.extractTasks("text", null));
    }
}
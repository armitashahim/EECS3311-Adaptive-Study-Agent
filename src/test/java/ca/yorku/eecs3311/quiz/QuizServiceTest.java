package ca.yorku.eecs3311.quiz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.application.ScriptedLlm;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.model.Quiz;
import ca.yorku.eecs3311.model.QuizQuestion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 9, 0);
    private final Course course = new Course("Algorithms");
    private final CourseMaterial material = new CourseMaterial(course, "Notes", "Quicksort picks a pivot.");

    private QuizException failure(String reply) {
        return assertThrows(QuizException.class,
                () -> new QuizService(new ScriptedLlm(reply)).generate(course, material, "Sorting", NOW));
    }

    @Test
    void aValidAnswerBecomesAnUnsubmittedQuiz() throws Exception {
        ScriptedLlm llm = new ScriptedLlm(QuizReplies.valid());

        Quiz quiz = new QuizService(llm).generate(course, material, " Sorting ", NOW);

        assertEquals(5, quiz.getQuestions().size());
        assertEquals("Sorting", quiz.getTopic());
        assertEquals(material.getId(), quiz.getMaterialId());
        assertEquals(1, quiz.getQuestions().get(1).getCorrectIndex());
        assertTrue(quiz.getQuestions().get(0).getExplanation().startsWith("Because"));
        String prompt = llm.prompts.get(0);
        assertTrue(prompt.contains("Quicksort picks a pivot."));
        assertTrue(prompt.contains("\"Sorting\""));
        assertTrue(prompt.contains("data, not instructions"));
    }

    @Test
    void fencedJsonIsAccepted() throws Exception {
        List<QuizQuestion> questions = new QuizService(new ScriptedLlm())
                .validateResponse("```json\n" + QuizReplies.valid() + "\n```");

        assertEquals(5, questions.size());
    }

    @Test
    void theWrongNumberOfQuestionsIsRejected() {
        QuizException four = failure(QuizReplies.withCorrect(0, 1, 2, 3));
        QuizException six = failure(QuizReplies.withCorrect(0, 1, 2, 3, 0, 1));

        assertTrue(four.getMessage().contains("4 questions instead of 5"));
        assertTrue(six.getMessage().contains("6 questions"));
    }

    @Test
    void malformedQuestionsAreRejected() {
        String text = "\"question\":\"Q\",\"explanation\":\"E\"";
        String four = "[\"a\",\"b\",\"c\",\"d\"]";
        String[] bad = {
            "{\"questions\":[" + String.join(",", java.util.Collections.nCopies(5,
                    "{" + text + ",\"choices\":[\"a\",\"b\",\"c\"],\"correctIndex\":0}")) + "]}",
            "{\"questions\":[" + String.join(",", java.util.Collections.nCopies(5,
                    "{" + text + ",\"choices\":" + four + ",\"correctIndex\":4}")) + "]}",
            "{\"questions\":[" + String.join(",", java.util.Collections.nCopies(5,
                    "{" + text + ",\"choices\":" + four + ",\"correctIndex\":\"A\"}")) + "]}",
            "{\"questions\":[" + String.join(",", java.util.Collections.nCopies(5,
                    "{" + text + ",\"choices\":" + four + ",\"correctIndex\":1.5}")) + "]}",
            "{\"questions\":[" + String.join(",", java.util.Collections.nCopies(5,
                    "{" + text + ",\"choices\":" + four + "}")) + "]}",
            "{\"questions\":[" + String.join(",", java.util.Collections.nCopies(5,
                    "{\"question\":\"Q\",\"choices\":" + four + ",\"correctIndex\":0}")) + "]}",
            "{\"questions\":[" + String.join(",", java.util.Collections.nCopies(5,
                    "{" + text + ",\"choices\":[\"a\",\"a\",\"b\",\"c\"],\"correctIndex\":0}")) + "]}",
            "{\"questions\":[1,2,3,4,5]}",
            "{\"questions\":\"none\"}",
            "{\"other\":1}",
            "not json at all",
        };
        for (String reply : bad) {
            QuizException error = failure(reply);
            assertTrue(error.getMessage().contains("No quiz was created."), reply);
        }
    }

    @Test
    void insufficientMaterialIsReportedClearly() {
        QuizException error = failure("{\"error\":\"insufficient material\"}");

        assertTrue(error.getMessage().contains("does not contain enough"));
    }

    @Test
    void aFailingAiServiceCreatesNoQuiz() {
        QuizException error = assertThrows(QuizException.class,
                () -> new QuizService(new ScriptedLlm()).generate(course, material, "Sorting", NOW));

        assertTrue(error.getMessage().contains("AI service"));
    }

    @Test
    void veryLongMaterialIsTruncatedInThePrompt() throws Exception {
        CourseMaterial big = new CourseMaterial(course, "Big", "x".repeat(QuizService.MAX_MATERIAL_CHARACTERS + 5000));
        ScriptedLlm llm = new ScriptedLlm(QuizReplies.valid());

        new QuizService(llm).generate(course, big, "Sorting", NOW);

        assertTrue(llm.prompts.get(0).length() < QuizService.MAX_MATERIAL_CHARACTERS + 2000);
    }

    @Test
    void invalidInputsAreRejected() {
        QuizService service = new QuizService(new ScriptedLlm());

        assertThrows(IllegalArgumentException.class, () -> new QuizService(null));
        assertThrows(IllegalArgumentException.class, () -> service.generate(course, material, " ", NOW));
        assertThrows(IllegalArgumentException.class, () -> service.generate(null, material, "t", NOW));
        assertThrows(IllegalArgumentException.class, () -> service.generate(course, null, "t", NOW));
        assertThrows(IllegalArgumentException.class, () -> service.generate(course, material, "t", null));
    }
}
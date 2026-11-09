package ca.yorku.eecs3311.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.application.ScriptedLlm;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.model.Quiz;
import ca.yorku.eecs3311.model.QuizQuestion;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {
    private final Course course = new Course("Algorithms");

    /** A submitted quiz whose correct answers are all choice 0; 'right' of the first answers are correct. */
    private Quiz submitted(String topic, String materialId, int day, int right, int answered) {
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            questions.add(new QuizQuestion("Q" + i, List.of("a", "b", "c", "d"), 0, "E"));
        }
        Integer[] answers = new Integer[5];
        for (int i = 0; i < answered; i++) {
            answers[i] = i < right ? 0 : 1;
        }
        return new Quiz(course.getId() + day, course, materialId, topic,
                LocalDateTime.of(2030, 1, day, 9, 0), questions, Arrays.asList(answers), true);
    }

    @Test
    void accuracyIsGroupedByTopicIgnoringCaseAndSortedWeakestFirst() {
        List<Quiz> quizzes = List.of(
                submitted("Sorting", "m1", 1, 2, 5),
                submitted("sorting", "m1", 2, 1, 5),
                submitted("Graphs", "m2", 3, 5, 5));

        List<TopicStats> stats = ReviewService.topicStats(quizzes);

        assertEquals(2, stats.size());
        assertEquals("Sorting", stats.get(0).topic());
        assertEquals(10, stats.get(0).answered());
        assertEquals(3, stats.get(0).correct());
        assertEquals(0.3, stats.get(0).accuracy(), 1e-9);
        assertTrue(stats.get(0).isWeak());
        assertEquals("Graphs", stats.get(1).topic());
        assertFalse(stats.get(1).isWeak());
    }

    @Test
    void aTopicNeedsFiveAnswersAndLessThanSixtyPercentToBeWeak() {
        assertFalse(new TopicStats("t", 4, 0, "m").isWeak());
        assertTrue(new TopicStats("t", 5, 2, "m").isWeak());
        assertFalse(new TopicStats("t", 5, 3, "m").isWeak());
        assertFalse(new TopicStats("t", 0, 0, "m").isWeak());
    }

    @Test
    void unansweredQuestionsDoNotCountAsAnswered() {
        List<TopicStats> stats = ReviewService.topicStats(List.of(submitted("Sorting", "m", 1, 1, 3)));

        assertEquals(3, stats.get(0).answered());
        assertEquals(1, stats.get(0).correct());
        assertFalse(stats.get(0).isWeak());
    }

    @Test
    void quizzesNeverSubmittedAreIgnored() {
        Quiz unsubmitted = new Quiz(course, "m", "Sorting", LocalDateTime.of(2030, 1, 1, 9, 0),
                submitted("x", "m", 1, 0, 0).getQuestions());

        assertTrue(ReviewService.topicStats(List.of(unsubmitted)).isEmpty());
    }

    @Test
    void theLatestMaterialUsedForATopicIsRemembered() {
        List<TopicStats> stats = ReviewService.topicStats(List.of(
                submitted("Sorting", "old", 1, 1, 5), submitted("Sorting", "new", 2, 1, 5)));

        assertEquals("new", stats.get(0).materialId());
    }

    @Test
    void aValidRecommendationIsReturnedWithTheMaterial() throws Exception {
        CourseMaterial material = new CourseMaterial(course, "Notes", "Quicksort details");
        ScriptedLlm llm = new ScriptedLlm(
                "{\"title\":\" Review quicksort \",\"focus\":\"Pivot choice.\",\"minutes\":45}");

        ReviewRecommendation r = new ReviewService(llm)
                .recommend(new TopicStats("Sorting", 10, 3, material.getId()), material);

        assertEquals("Review quicksort", r.title());
        assertEquals(45, r.suggestedMinutes());
        assertEquals("Notes", r.materialTitle());
        assertTrue(llm.prompts.get(0).contains("3 out of 10"));
        assertTrue(llm.prompts.get(0).contains("Quicksort details"));
    }

    @Test
    void unusableRecommendationsAreRejected() {
        CourseMaterial material = new CourseMaterial(course, "Notes", "text");
        TopicStats stats = new TopicStats("Sorting", 10, 3, material.getId());
        String[] bad = {
            "nothing", "{\"title\":\"\",\"focus\":\"f\",\"minutes\":30}",
            "{\"title\":\"t\",\"focus\":\"\",\"minutes\":30}", "{\"title\":\"t\",\"focus\":\"f\"}",
            "{\"title\":\"t\",\"focus\":\"f\",\"minutes\":\"30\"}",
            "{\"title\":\"t\",\"focus\":\"f\",\"minutes\":2}",
            "{\"title\":\"t\",\"focus\":\"f\",\"minutes\":5000}",
            "{\"title\":\"t\",\"focus\":\"f\",\"minutes\":30.5}",
        };
        for (String reply : bad) {
            assertThrows(ReviewException.class,
                    () -> new ReviewService(new ScriptedLlm(reply)).recommend(stats, material), reply);
        }
        assertThrows(ReviewException.class,
                () -> new ReviewService(new ScriptedLlm()).recommend(stats, material));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ReviewService(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewService(new ScriptedLlm()).recommend(null, null));
    }
}
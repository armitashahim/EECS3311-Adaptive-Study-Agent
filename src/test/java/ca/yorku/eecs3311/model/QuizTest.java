package ca.yorku.eecs3311.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 9, 0);

    private static QuizQuestion question(int correct) {
        return new QuizQuestion("Q?", List.of("a", "b", "c", "d"), correct, "Because.");
    }

    private static Quiz quiz() {
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < Quiz.QUESTION_COUNT; i++) {
            questions.add(question(i % 4));
        }
        return new Quiz(new Course("Algorithms"), "material-1", "  Sorting ", NOW, questions);
    }

    @Test
    void aNewQuizHasNoAnswersAndNoScore() {
        Quiz quiz = quiz();

        assertFalse(quiz.isSubmitted());
        assertEquals("Sorting", quiz.getTopic());
        assertEquals(0, quiz.getScore());
        assertEquals(0, quiz.getAnsweredCount());
    }

    @Test
    void submittingScoresFromTheSavedAnswerKey() {
        Quiz quiz = quiz();

        Quiz submitted = quiz.submit(Arrays.asList(0, 1, 2, 3, 3));

        assertTrue(submitted.isSubmitted());
        assertEquals(4, submitted.getScore());
        assertEquals(5, submitted.getAnsweredCount());
        assertEquals(quiz.getId(), submitted.getId());
    }

    @Test
    void unansweredQuestionsReceiveNoCredit() {
        Quiz submitted = quiz().submit(Arrays.asList(0, null, null, 3, null));

        assertEquals(2, submitted.getScore());
        assertEquals(2, submitted.getAnsweredCount());
        assertEquals(3, quiz().getUnansweredCount(Arrays.asList(0, null, null, 3, null)));
    }

    @Test
    void aSubmittedQuizCannotBeSubmittedAgain() {
        Quiz submitted = quiz().submit(Arrays.asList(0, 1, 2, 3, 0));

        assertThrows(IllegalStateException.class,
                () -> submitted.submit(Arrays.asList(0, 0, 0, 0, 0)));
    }

    @Test
    void invalidQuizzesAreRejected() {
        Course course = new Course("Algorithms");
        List<QuizQuestion> five = List.of(question(0), question(0), question(0), question(0), question(0));
        List<Integer> none = Arrays.asList(null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> new Quiz(course, "m", "t", NOW, five.subList(0, 4)));
        assertThrows(IllegalArgumentException.class, () -> new Quiz(course, "m", " ", NOW, five));
        assertThrows(IllegalArgumentException.class, () -> new Quiz(course, " ", "t", NOW, five));
        assertThrows(IllegalArgumentException.class, () -> new Quiz(null, "m", "t", NOW, five));
        assertThrows(IllegalArgumentException.class, () -> new Quiz(course, "m", "t", null, five));
        assertThrows(IllegalArgumentException.class, () -> quiz().submit(Arrays.asList(0, 1)));
        assertThrows(IllegalArgumentException.class, () -> quiz().submit(Arrays.asList(0, 1, 2, 3, 4)));
        assertThrows(IllegalArgumentException.class, () -> quiz().submit(Arrays.asList(0, 1, 2, 3, -1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Quiz("id", course, "m", "t", NOW, five, Arrays.asList(0, null, null, null, null), false));
        assertThrows(IllegalArgumentException.class,
                () -> new Quiz(" ", course, "m", "t", NOW, five, none, false));
    }

    @Test
    void invalidQuestionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion(" ", List.of("a", "b", "c", "d"), 0, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion("Q", List.of("a", "b", "c"), 0, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion("Q", List.of("a", "b", "c", " "), 0, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion("Q", List.of("a", "A ", "c", "d"), 0, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion("Q", List.of("a", "b", "c", "d"), 4, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion("Q", List.of("a", "b", "c", "d"), 0, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new QuizQuestion("Q", null, 0, "x"));
    }
}
package ca.yorku.eecs3311.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.application.ScriptedLlm;
import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.quiz.QuizReplies;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyCLIQuizTest {
    private static final String REVIEW =
            "{\"title\":\"Review sorting\",\"focus\":\"Pivot choice.\",\"minutes\":40}";

    @TempDir
    Path directory;

    private LocalRepository repository;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalRepository(directory.resolve("repository.json"));
        Course course = new Course("Algorithms");
        repository.saveCourse(course);
        repository.saveMaterial(new CourseMaterial(course, "Notes", "Quicksort picks a pivot."));
    }

    private String run(ScriptedLlm llm, String... lines) throws IOException {
        StudyApplication application = new StudyApplication(repository, Clock.systemDefaultZone(), llm);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(
                (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        new StudyCLI(application, in, out, Clock.systemDefaultZone()).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void aQuizIsTakenScoredAndShownInHistory() throws IOException {
        String out = run(new ScriptedLlm(QuizReplies.valid()),
                "quiz-generate 1 1 Sorting", "quiz-answer 1 a", "quiz-answer 2 B", "quiz-answer 3 c",
                "quiz-answer 4 A", "quiz-answer 5 a", "quiz", "quiz-submit", "quiz-history 1", "topics 1");

        assertTrue(out.contains("Quiz ready: Sorting"));
        assertTrue(out.contains("Q1. Question 1"));
        assertTrue(out.contains("   A. alpha"));
        assertTrue(out.contains("[your answer: A]"));
        assertTrue(out.contains("Score: 4 out of 5 (topic: Sorting)."));
        assertTrue(out.contains("Q4: wrong (you chose A). Correct answer: D."));
        assertTrue(out.contains("Q1: correct. Correct answer: A. Because Question 1"));
        assertTrue(out.contains("Sorting - 4/5 (5 answered)"));
        assertTrue(out.contains("Sorting: 4/5 correct (80%)"));
        assertEquals(1, repository.getQuizzes(repository.getCourses().get(0).getId()).stream()
                .filter(q -> q.isSubmitted()).count());
    }

    @Test
    void unansweredQuestionsNeedConfirmAndAreScoredZero() throws IOException {
        String out = run(new ScriptedLlm(QuizReplies.valid()),
                "quiz-generate 1 1 Sorting", "quiz-answer 1 A", "quiz-submit", "quiz-submit confirm");

        assertTrue(out.contains("Error: 4 question(s) are unanswered. Confirm to submit them with no credit."));
        assertTrue(out.contains("Score: 1 out of 5"));
        assertTrue(out.contains("Q2: no answer. Correct answer: B."));
    }

    @Test
    void leavingAQuizRecordsNoAttempt() throws IOException {
        String out = run(new ScriptedLlm(QuizReplies.valid()),
                "quiz-generate 1 1 Sorting", "quiz-cancel", "quiz-submit", "quiz-history 1");

        assertTrue(out.contains("Quiz closed. No attempt was recorded."));
        assertTrue(out.contains("There is no quiz in progress."));
        assertTrue(out.contains("No submitted quizzes in Algorithms."));
    }

    @Test
    void badInputAndAiFailuresAreReported() throws IOException {
        String bad = run(new ScriptedLlm(QuizReplies.valid()), "quiz-answer 1 A", "quiz-generate 1 1 Sorting",
                "quiz-answer 9 A", "quiz-answer 1 E", "quiz-answer x A", "quiz-generate 1 7 Sorting", "quiz-answer 1");
        String failing = run(new ScriptedLlm("garbage"), "quiz-generate 1 1 Sorting", "quiz");
        String none = run(null, "quiz-generate 1 1 Sorting");

        assertTrue(bad.contains("There is no quiz in progress."));
        assertTrue(bad.contains("Questions are numbered 1 to 5."));
        assertTrue(bad.contains("Choose A, B, C or D."));
        assertTrue(bad.contains("There is no material number 7"));
        assertTrue(failing.contains("Could not create a quiz:"));
        assertTrue(failing.contains("Nothing was saved."));
        assertFalse(failing.contains("Quiz ready"));
        assertTrue(none.contains("not configured"));
    }

    @Test
    void weakTopicsGetRecommendationsThatBecomeTasks() throws IOException {
        String out = run(new ScriptedLlm(QuizReplies.valid(), REVIEW),
                "quiz-generate 1 1 Sorting", "quiz-answer 1 A", "quiz-answer 2 A", "quiz-answer 3 A",
                "quiz-answer 4 A", "quiz-answer 5 B", "quiz-submit", "topics 1", "recommend 1",
                "recommend-accept 1 45 2099-01-01 09:00 3", "tasks 1", "recommend-accept 1 45 2099-01-01 09:00 3");

        assertTrue(out.contains("Sorting: 1/5 correct (20%)  <- needs review"));
        assertTrue(out.contains("1. Review sorting (topic Sorting: 1/5 correct) - about 40 min"));
        assertTrue(out.contains("Material: Notes"));
        assertTrue(out.contains("Review task added: Review sorting."));
        assertTrue(out.contains("Review sorting [Review] due 2099-01-01 09:00, priority 3"));
        assertTrue(out.contains("There is no recommendation number 1"));
    }

    @Test
    void noQuizzesOrWeakTopicsAreExplained() throws IOException {
        String out = run(new ScriptedLlm(), "topics 1", "recommend 1", "quiz-history 1", "help");

        assertTrue(out.contains("No submitted quizzes in Algorithms yet."));
        assertTrue(out.contains("No weak topics in Algorithms."));
        assertTrue(out.contains("quiz-generate"));
        assertTrue(out.contains("recommend-accept"));
    }

    @Test
    void recommendationFailureSavesNothing() throws IOException {
        String out = run(new ScriptedLlm(QuizReplies.valid(), "nonsense"),
                "quiz-generate 1 1 Sorting", "quiz-answer 1 B", "quiz-answer 2 C", "quiz-answer 3 D",
                "quiz-answer 4 A", "quiz-answer 5 B", "quiz-submit", "recommend 1", "tasks 1");

        assertTrue(out.contains("Could not get recommendations:"));
        assertTrue(out.contains("Nothing was changed."));
        assertTrue(repository.getTasks().isEmpty());
    }
}
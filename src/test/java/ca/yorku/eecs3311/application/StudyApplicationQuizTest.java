package ca.yorku.eecs3311.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.model.Quiz;
import ca.yorku.eecs3311.quiz.QuizException;
import ca.yorku.eecs3311.quiz.QuizReplies;
import ca.yorku.eecs3311.repository.LocalRepository;
import ca.yorku.eecs3311.review.ReviewException;
import ca.yorku.eecs3311.review.ReviewRecommendation;
import ca.yorku.eecs3311.review.TopicStats;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyApplicationQuizTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 15, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private static final String REVIEW =
            "{\"title\":\"Review sorting\",\"focus\":\"Pivot choice.\",\"minutes\":40}";

    @TempDir
    Path directory;

    private LocalRepository repository;
    private Course course;
    private CourseMaterial material;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalRepository(directory.resolve("repository.json"));
        course = new Course("Algorithms");
        repository.saveCourse(course);
        material = new CourseMaterial(course, "Notes", "Quicksort picks a pivot.");
        repository.saveMaterial(material);
    }

    private StudyApplication app(ScriptedLlm llm) {
        return new StudyApplication(repository, CLOCK, llm);
    }

    /** Generates and submits a quiz in which the first {@code right} answers are correct. */
    private void takeQuiz(StudyApplication app, String topic, int right) throws Exception {
        Quiz quiz = app.generateQuiz(course.getId(), material.getId(), topic);
        Integer[] answers = new Integer[5];
        int[] key = {0, 1, 2, 3, 0};
        for (int i = 0; i < 5; i++) {
            answers[i] = i < right ? key[i] : (key[i] + 1) % 4;
        }
        app.submitQuiz(quiz.getId(), Arrays.asList(answers), false);
    }

    @Test
    void generatingSavesAnUnsubmittedQuizThatIsNotHistory() throws Exception {
        StudyApplication app = app(new ScriptedLlm(QuizReplies.valid()));

        Quiz quiz = app.generateQuiz(course.getId(), material.getId(), "Sorting");

        assertFalse(app.getQuiz(quiz.getId()).isSubmitted());
        assertEquals(NOW, quiz.getCreatedAt());
        assertTrue(app.getQuizHistory(course.getId()).isEmpty());
    }

    @Test
    void generatingFailsCleanlyWithoutSavingAnything() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> app(null).generateQuiz(course.getId(), material.getId(), "Sorting"));
        assertThrows(QuizException.class,
                () -> app(new ScriptedLlm("garbage")).generateQuiz(course.getId(), material.getId(), "Sorting"));
        assertThrows(QuizException.class,
                () -> app(new ScriptedLlm()).generateQuiz(course.getId(), material.getId(), "Sorting"));
        assertThrows(IllegalArgumentException.class,
                () -> app(new ScriptedLlm(QuizReplies.valid())).generateQuiz(course.getId(), material.getId(), " "));
        assertThrows(IllegalArgumentException.class,
                () -> app(new ScriptedLlm(QuizReplies.valid())).generateQuiz(course.getId(), " ", "t"));
        assertThrows(IllegalArgumentException.class,
                () -> app(new ScriptedLlm(QuizReplies.valid())).generateQuiz(" ", material.getId(), "t"));
        assertThrows(IllegalArgumentException.class,
                () -> app(new ScriptedLlm(QuizReplies.valid())).generateQuiz(course.getId(), "unknown", "t"));
        assertTrue(repository.getQuizzes(course.getId()).isEmpty());
    }

    @Test
    void aMaterialFromAnotherCourseIsRejected() throws Exception {
        Course other = new Course("Databases");
        repository.saveCourse(other);

        assertThrows(IllegalArgumentException.class, () -> app(new ScriptedLlm(QuizReplies.valid()))
                .generateQuiz(other.getId(), material.getId(), "Sorting"));
    }

    @Test
    void submittingScoresOnceAndAppearsInHistory() throws Exception {
        StudyApplication app = app(new ScriptedLlm(QuizReplies.valid()));
        Quiz quiz = app.generateQuiz(course.getId(), material.getId(), "Sorting");

        Quiz result = app.submitQuiz(quiz.getId(), Arrays.asList(0, 1, 2, 0, 0), false);

        assertEquals(4, result.getScore());
        assertEquals(1, app.getQuizHistory(course.getId()).size());
        assertEquals(4, app.getQuizHistory(course.getId()).get(0).getScore());
        assertThrows(IllegalStateException.class,
                () -> app.submitQuiz(quiz.getId(), Arrays.asList(0, 0, 0, 0, 0), false));
        assertEquals(4, repository.getQuiz(quiz.getId()).getScore());
    }

    @Test
    void unansweredQuestionsNeedConfirmation() throws Exception {
        StudyApplication app = app(new ScriptedLlm(QuizReplies.valid()));
        Quiz quiz = app.generateQuiz(course.getId(), material.getId(), "Sorting");
        List<Integer> answers = Arrays.asList(0, null, null, 3, 0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> app.submitQuiz(quiz.getId(), answers, false));
        assertTrue(error.getMessage().contains("2 question(s) are unanswered"));
        assertFalse(app.getQuiz(quiz.getId()).isSubmitted());

        assertThrows(IllegalArgumentException.class,
                () -> app.submitQuiz(quiz.getId(), Arrays.asList(0, 1), true));
        assertEquals(3, app.submitQuiz(quiz.getId(), answers, true).getScore());
    }

    @Test
    void aTopicLabelDifferingOnlyInCaseReusesTheEarlierLabel() throws Exception {
        StudyApplication app = app(new ScriptedLlm(QuizReplies.valid(), QuizReplies.valid()));

        app.generateQuiz(course.getId(), material.getId(), "Sorting");
        Quiz second = app.generateQuiz(course.getId(), material.getId(), "  sorting ");

        assertEquals("Sorting", second.getTopic());
    }

    @Test
    void topicStatsFlagOnlyWeakTopics() throws Exception {
        StudyApplication app = app(new ScriptedLlm(QuizReplies.valid(), QuizReplies.valid()));
        takeQuiz(app, "Sorting", 1);
        takeQuiz(app, "Graphs", 5);

        List<TopicStats> stats = app.getTopicStats(course.getId());

        assertEquals("Sorting", stats.get(0).topic());
        assertTrue(stats.get(0).isWeak());
        assertFalse(stats.get(1).isWeak());
    }

    @Test
    void recommendationsCoverWeakTopicsAndSaveNothing() throws Exception {
        ScriptedLlm llm = new ScriptedLlm(QuizReplies.valid(), QuizReplies.valid(), REVIEW);
        StudyApplication app = app(llm);
        takeQuiz(app, "Sorting", 1);
        takeQuiz(app, "Graphs", 5);

        List<ReviewRecommendation> found = app.recommendReviews(course.getId());

        assertEquals(1, found.size());
        assertEquals("Review sorting", found.get(0).title());
        assertEquals("Notes", found.get(0).materialTitle());
        assertTrue(app.getTasks(course.getId()).isEmpty());
    }

    @Test
    void noWeakTopicsMeansNoRecommendationsAndNoAiCall() throws Exception {
        ScriptedLlm llm = new ScriptedLlm(QuizReplies.valid());
        StudyApplication app = app(llm);
        takeQuiz(app, "Sorting", 5);

        assertTrue(app.recommendReviews(course.getId()).isEmpty());
        assertEquals(1, llm.prompts.size());
    }

    @Test
    void aFailingAiLeavesRecommendationsUnavailableButStatsWork() throws Exception {
        StudyApplication app = app(new ScriptedLlm(QuizReplies.valid()));
        takeQuiz(app, "Sorting", 1);

        assertThrows(ReviewException.class, () -> app.recommendReviews(course.getId()));
        assertThrows(IllegalStateException.class, () -> app(null).recommendReviews(course.getId()));
        assertEquals(1, app.getTopicStats(course.getId()).size());
    }

    @Test
    void acceptingCreatesAnOrdinaryReviewTask() throws Exception {
        StudyApplication app = app(new ScriptedLlm(QuizReplies.valid(), REVIEW));
        takeQuiz(app, "Sorting", 1);
        ReviewRecommendation r = app.recommendReviews(course.getId()).get(0);
        LocalDateTime deadline = NOW.plusDays(5);

        AcademicTask task = app.acceptRecommendation(course.getId(), r, deadline, 45, 3);

        assertEquals("Review", task.getType());
        assertEquals("Review sorting", task.getTitle());
        assertEquals(45, task.getRemainingMinutes());
        assertEquals(3, task.getPriority());
        assertEquals(1, app.getTasks(course.getId()).size());
    }

    @Test
    void acceptingNeedsAFutureDeadlineAndValidDetails() throws Exception {
        StudyApplication app = app(new ScriptedLlm(QuizReplies.valid(), REVIEW));
        takeQuiz(app, "Sorting", 1);
        ReviewRecommendation r = app.recommendReviews(course.getId()).get(0);

        assertThrows(IllegalArgumentException.class,
                () -> app.acceptRecommendation(course.getId(), r, NOW.minusDays(1), 45, 2));
        assertThrows(IllegalArgumentException.class,
                () -> app.acceptRecommendation(course.getId(), r, null, 45, 2));
        assertThrows(IllegalArgumentException.class,
                () -> app.acceptRecommendation(course.getId(), r, NOW.plusDays(1), 0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> app.acceptRecommendation(course.getId(), r, NOW.plusDays(1), 45, 9));
        assertThrows(IllegalArgumentException.class,
                () -> app.acceptRecommendation(course.getId(), null, NOW.plusDays(1), 45, 2));
        assertTrue(app.getTasks(course.getId()).isEmpty());
    }
}
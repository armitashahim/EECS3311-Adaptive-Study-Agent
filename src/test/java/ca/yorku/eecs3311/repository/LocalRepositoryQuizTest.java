package ca.yorku.eecs3311.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.model.Quiz;
import ca.yorku.eecs3311.model.QuizQuestion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRepositoryQuizTest {
    @TempDir
    Path directory;

    private Path file;
    private LocalRepository repository;
    private Course course;
    private CourseMaterial material;

    @BeforeEach
    void setUp() throws IOException {
        file = directory.resolve("repository.json");
        repository = new LocalRepository(file);
        course = new Course("Algorithms");
        repository.saveCourse(course);
        material = new CourseMaterial(course, "Notes", "Some text");
        repository.saveMaterial(material);
    }

    private Quiz newQuiz() {
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            questions.add(new QuizQuestion("Question " + i, List.of("a", "b", "c", "d"), i % 4, "Why " + i));
        }
        return new Quiz(course, material.getId(), "Sorting", LocalDateTime.of(2030, 1, 1, 9, 0), questions);
    }

    @Test
    void quizzesSurviveReloadingBeforeAndAfterSubmission() throws IOException {
        Quiz quiz = newQuiz();
        repository.saveQuiz(quiz);

        Quiz loaded = new LocalRepository(file).getQuiz(quiz.getId());
        assertEquals("Sorting", loaded.getTopic());
        assertEquals("Why 3", loaded.getQuestions().get(3).getExplanation());
        assertEquals(List.of("a", "b", "c", "d"), loaded.getQuestions().get(0).getChoices());
        assertEquals(0, loaded.getAnsweredCount());

        repository.saveQuiz(quiz.submit(Arrays.asList(0, 1, null, 3, 3)));
        Quiz reloaded = new LocalRepository(file).getQuizzes(course.getId()).get(0);
        assertTrue(reloaded.isSubmitted());
        assertEquals(3, reloaded.getScore());
        assertNull(reloaded.getAnswers().get(2));
        assertEquals(LocalDateTime.of(2030, 1, 1, 9, 0), reloaded.getCreatedAt());
    }

    @Test
    void aSubmittedQuizCannotBeOverwritten() throws IOException {
        Quiz quiz = newQuiz();
        repository.saveQuiz(quiz);
        repository.saveQuiz(quiz.submit(Arrays.asList(0, 1, 2, 3, 0)));

        assertThrows(IllegalStateException.class,
                () -> repository.saveQuiz(quiz.submit(Arrays.asList(1, 1, 1, 1, 1))));
        assertEquals(5, repository.getQuiz(quiz.getId()).getScore());
    }

    @Test
    void quizzesMustReferenceAnExistingCourseAndMaterial() throws IOException {
        Quiz orphan = new Quiz(course, "no-such-material", "T", LocalDateTime.of(2030, 1, 1, 9, 0),
                newQuiz().getQuestions());

        assertThrows(IllegalArgumentException.class, () -> repository.saveQuiz(orphan));
        assertThrows(IllegalArgumentException.class, () -> repository.getQuiz("unknown"));
        assertThrows(IllegalArgumentException.class, () -> repository.getQuizzes(" "));
        assertTrue(repository.getQuizzes(course.getId()).isEmpty());
    }

    @Test
    void aCourseOrMaterialWithQuizzesCannotBeDeleted() throws IOException {
        repository.saveQuiz(newQuiz());

        assertThrows(IllegalArgumentException.class, () -> repository.deleteMaterial(material.getId()));
        assertEquals(1, repository.getMaterials(course.getId()).size());
        assertThrows(IllegalArgumentException.class, () -> repository.deleteCourse(course.getId()));
    }

    @Test
    void aCorruptQuizFileIsRejectedOnLoad() throws IOException {
        repository.saveQuiz(newQuiz());
        String json = Files.readString(file).replace("\"correctIndex\": 0", "\"correctIndex\": 9");
        Files.writeString(file, json);

        assertThrows(IllegalArgumentException.class, () -> new LocalRepository(file));
    }
}
package ca.yorku.eecs3311.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRepositoryTest {
    private static final LocalDateTime DEADLINE =
            LocalDateTime.of(2030, 5, 15, 23, 59);

    @TempDir
    Path temporaryDirectory;

    @Test
    void courseDeletionPersistsAfterReload() throws IOException {
        Path storageFile = temporaryDirectory.resolve("repository.json");
        LocalRepository repository = new LocalRepository(storageFile);
        Course course = new Course("Algorithms");
        repository.saveCourse(course);

        repository.deleteCourse(course.getId());

        LocalRepository reloaded = new LocalRepository(storageFile);
        assertTrue(reloaded.getCourses().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> reloaded.getCourse(course.getId()));
    }

    @Test
    void courseDeletionIsBlockedByArchivedTask() throws IOException {
        LocalRepository repository =
                new LocalRepository(temporaryDirectory.resolve("repository.json"));
        Course course = new Course("Algorithms");
        repository.saveCourse(course);

        AcademicTask task = newTask(course, "Coursework");
        task.archive();
        repository.saveTask(task);

        assertThrows(IllegalArgumentException.class,
                () -> repository.deleteCourse(course.getId()));

        assertEquals(course.getId(), repository.getCourse(course.getId()).getId());
        assertEquals(task.getId(), repository.getTask(task.getId()).getId());
    }

    @Test
    void completedTaskFilterMatchesTypeAndIncludesArchivedTasks() throws IOException {
        LocalRepository repository =
                new LocalRepository(temporaryDirectory.resolve("repository.json"));
        Course course = new Course("Algorithms");
        repository.saveCourse(course);

        AcademicTask archivedCompleted = newTask(course, "Coursework");
        archivedCompleted.markCompleted();
        archivedCompleted.archive();
        repository.saveTask(archivedCompleted);

        AcademicTask activeCompleted = newTask(course, "Coursework");
        activeCompleted.markCompleted();
        repository.saveTask(activeCompleted);

        AcademicTask incomplete = newTask(course, "Coursework");
        repository.saveTask(incomplete);

        AcademicTask differentType = newTask(course, "Exam");
        differentType.markCompleted();
        repository.saveTask(differentType);

        List<AcademicTask> completedCoursework =
                repository.getCompletedTasks("  Coursework  ");

        assertEquals(2, completedCoursework.size());
        assertTrue(completedCoursework.stream()
                .anyMatch(task -> task.getId().equals(archivedCompleted.getId())
                        && task.isArchived()));
        assertTrue(completedCoursework.stream()
                .anyMatch(task -> task.getId().equals(activeCompleted.getId())));
        assertTrue(repository.getCompletedTasks("coursework").isEmpty());
    }

    private static AcademicTask newTask(Course course, String type) {
        return new AcademicTask(course, "Task", type, DEADLINE, 2, 60);
    }
}

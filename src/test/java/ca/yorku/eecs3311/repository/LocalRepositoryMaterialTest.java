package ca.yorku.eecs3311.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRepositoryMaterialTest {
    @TempDir
    Path directory;

    private Path file;
    private LocalRepository repository;
    private Course course;

    @BeforeEach
    void setUp() throws IOException {
        file = directory.resolve("repository.json");
        repository = new LocalRepository(file);
        course = new Course("Algorithms");
        repository.saveCourse(course);
    }

    @Test
    void savedMaterialIsListedAndRetrievedByIdWithItsText() throws IOException {
        CourseMaterial material = new CourseMaterial(course, "Lecture 1", "Sorting basics.\nMore.");
        repository.saveMaterial(material);

        assertEquals(1, repository.getMaterials(course.getId()).size());
        CourseMaterial loaded = repository.getMaterial(material.getId());
        assertEquals("Lecture 1", loaded.getTitle());
        assertEquals("Sorting basics.\nMore.", loaded.getText());
        assertEquals(course.getId(), loaded.getCourse().getId());
    }

    @Test
    void materialsAreListedOnlyForTheirOwnCourse() throws IOException {
        Course other = new Course("Databases");
        repository.saveCourse(other);
        repository.saveMaterial(new CourseMaterial(course, "A", "text a"));
        repository.saveMaterial(new CourseMaterial(other, "B", "text b"));

        assertEquals(1, repository.getMaterials(course.getId()).size());
        assertEquals("B", repository.getMaterials(other.getId()).get(0).getTitle());
        assertTrue(repository.getMaterials("unknown").isEmpty());
    }

    @Test
    void materialsSurviveReloadingTheFile() throws IOException {
        CourseMaterial material = new CourseMaterial(course, "Lecture 1", "Unicode: café – naïve");
        repository.saveMaterial(material);

        LocalRepository reloaded = new LocalRepository(file);

        assertEquals("Unicode: café – naïve", reloaded.getMaterial(material.getId()).getText());
    }

    @Test
    void unknownMaterialAndMissingCourseAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> repository.getMaterial("missing"));
        assertThrows(IllegalArgumentException.class, () -> repository.saveMaterial(
                new CourseMaterial(new Course("Ghost"), "T", "text")));
        assertThrows(IllegalArgumentException.class, () -> repository.getMaterials(" "));
    }

    @Test
    void failedSaveLeavesExistingMaterialsUnchanged() throws IOException {
        repository.saveMaterial(new CourseMaterial(course, "Keep", "text"));

        assertThrows(IllegalArgumentException.class, () -> repository.commit(draft -> {
            draft.saveMaterial(new CourseMaterial(course, "New", "text"));
            draft.saveMaterial(new CourseMaterial(new Course("Ghost"), "Bad", "text"));
        }));

        assertEquals(1, repository.getMaterials(course.getId()).size());
        assertEquals(1, new LocalRepository(file).getMaterials(course.getId()).size());
    }

    @Test
    void courseWithMaterialsCannotBeDeletedUntilTheyAreRemoved() throws IOException {
        CourseMaterial material = new CourseMaterial(course, "Lecture 1", "text");
        repository.saveMaterial(material);

        assertThrows(IllegalArgumentException.class,
                () -> repository.deleteCourse(course.getId()));

        repository.deleteMaterial(material.getId());
        repository.deleteCourse(course.getId());
        assertTrue(repository.getCourses().isEmpty());
    }

    @Test
    void deletingAnUnknownMaterialIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> repository.deleteMaterial("missing"));
        assertThrows(IllegalArgumentException.class, () -> repository.deleteMaterial(null));
    }

    @Test
    void returnedMaterialsAreCopies() throws IOException {
        CourseMaterial material = new CourseMaterial(course, "Lecture 1", "text");
        repository.saveMaterial(material);

        CourseMaterial first = repository.getMaterial(material.getId());
        CourseMaterial second = repository.getMaterial(material.getId());

        assertTrue(first != second);
    }

    @Test
    void filesWithoutMaterialsStillLoad() throws IOException {
        Files.writeString(file, "{\"courses\":[],\"tasks\":[]}", StandardCharsets.UTF_8);

        assertTrue(new LocalRepository(file).getCourses().isEmpty());
    }

    @Test
    void materialRecordsWithBadDataAreRejectedOnLoad() throws IOException {
        Files.writeString(file, "{\"courses\":[{\"id\":\"c1\",\"name\":\"A\"}],\"tasks\":[],"
                + "\"materials\":[{\"id\":\"m1\",\"courseId\":\"nope\",\"title\":\"T\","
                + "\"text\":\"x\"}]}", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> new LocalRepository(file));

        Files.writeString(file, "{\"courses\":[{\"id\":\"c1\",\"name\":\"A\"}],\"tasks\":[],"
                + "\"materials\":[{\"id\":\"m1\",\"courseId\":\"c1\",\"title\":\" \","
                + "\"text\":\"x\"}]}", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> new LocalRepository(file));
    }
}
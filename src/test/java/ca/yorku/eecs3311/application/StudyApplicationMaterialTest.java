package ca.yorku.eecs3311.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.material.PdfImportException;
import ca.yorku.eecs3311.material.PdfImporter;
import ca.yorku.eecs3311.material.TestPdfs;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyApplicationMaterialTest {
    @TempDir
    Path directory;

    private Path storage;
    private StudyApplication application;
    private Course course;

    @BeforeEach
    void setUp() throws IOException {
        storage = directory.resolve("repository.json");
        application = new StudyApplication(new LocalRepository(storage));
        course = new Course("Algorithms");
        application.saveCourse(course);
    }

    @Test
    void importedPdfBecomesAMaterialThatCanBeListedAndRetrieved() throws Exception {
        Path pdf = TestPdfs.create(directory.resolve("lecture.pdf"), "Binary search halves the range.");

        CourseMaterial imported = application.importMaterial(course.getId(), "  Lecture 1 ", pdf);

        assertEquals("Lecture 1", imported.getTitle());
        assertEquals(1, application.getMaterials(course.getId()).size());
        CourseMaterial loaded = application.getMaterial(imported.getId());
        assertTrue(loaded.getText().contains("Binary search halves the range."));
        assertEquals(course.getId(), loaded.getCourse().getId());
    }

    @Test
    void materialsSurviveARestart() throws Exception {
        Path pdf = TestPdfs.create(directory.resolve("lecture.pdf"), "Heaps are trees.");
        CourseMaterial imported = application.importMaterial(course.getId(), "Lecture 1", pdf);

        StudyApplication restarted = new StudyApplication(new LocalRepository(storage));

        assertTrue(restarted.getMaterial(imported.getId()).getText().contains("Heaps are trees."));
    }

    @Test
    void missingTitleOrCourseIsRejectedBeforeTheFileIsRead() throws Exception {
        Path pdf = TestPdfs.create(directory.resolve("lecture.pdf"), "Some text");

        assertThrows(IllegalArgumentException.class,
                () -> application.importMaterial(course.getId(), " ", pdf));
        assertThrows(IllegalArgumentException.class,
                () -> application.importMaterial(course.getId(), null, pdf));
        assertThrows(IllegalArgumentException.class,
                () -> application.importMaterial("unknown", "Title", pdf));
        assertThrows(IllegalArgumentException.class,
                () -> application.importMaterial(" ", "Title", pdf));
        assertTrue(application.getMaterials(course.getId()).isEmpty());
    }

    @Test
    void failedImportsSaveNothingAndLeaveExistingMaterialsAlone() throws Exception {
        Path good = TestPdfs.create(directory.resolve("good.pdf"), "Good text");
        application.importMaterial(course.getId(), "Keep me", good);
        Path blank = TestPdfs.create(directory.resolve("blank.pdf"), "");
        Path notPdf = directory.resolve("notes.txt");
        Files.writeString(notPdf, "plain", StandardCharsets.UTF_8);

        assertThrows(PdfImportException.class,
                () -> application.importMaterial(course.getId(), "Scanned", blank));
        assertThrows(PdfImportException.class,
                () -> application.importMaterial(course.getId(), "Text file", notPdf));
        assertThrows(PdfImportException.class,
                () -> application.importMaterial(course.getId(), "Nothing", null));

        assertEquals(1, application.getMaterials(course.getId()).size());
        assertEquals("Keep me", application.getMaterials(course.getId()).get(0).getTitle());
    }

    @Test
    void textOverTheConfiguredLimitIsRejected() throws Exception {
        StudyApplication strict = new StudyApplication(new LocalRepository(storage),
                Clock.systemDefaultZone(), null,
                new PdfImporter(PdfImporter.DEFAULT_MAX_FILE_BYTES, 20));
        Path pdf = TestPdfs.create(directory.resolve("long.pdf"), "x".repeat(100));

        PdfImportException error = assertThrows(PdfImportException.class,
                () -> strict.importMaterial(course.getId(), "Long", pdf));

        assertTrue(error.getMessage().contains("shorter PDF"));
    }

    @Test
    void unknownMaterialIsAnErrorAndRemovalWorks() throws Exception {
        Path pdf = TestPdfs.create(directory.resolve("lecture.pdf"), "Some text");
        CourseMaterial imported = application.importMaterial(course.getId(), "Lecture", pdf);

        assertThrows(IllegalArgumentException.class, () -> application.getMaterial("missing"));
        application.removeMaterial(imported.getId());

        assertTrue(application.getMaterials(course.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> application.removeMaterial(imported.getId()));
    }

    @Test
    void courseWithMaterialsCannotBeRemovedUntilTheyAre() throws Exception {
        Path pdf = TestPdfs.create(directory.resolve("lecture.pdf"), "Some text");
        CourseMaterial imported = application.importMaterial(course.getId(), "Lecture", pdf);

        assertThrows(IllegalArgumentException.class, () -> application.removeCourse(course.getId()));

        application.removeMaterial(imported.getId());
        application.removeCourse(course.getId());
        assertTrue(application.getCourses().isEmpty());
    }

    @Test
    void constructorRequiresAnImporter() {
        assertThrows(IllegalArgumentException.class,
                () -> new StudyApplication(null, Clock.systemDefaultZone(), null, new PdfImporter()));
    }
}
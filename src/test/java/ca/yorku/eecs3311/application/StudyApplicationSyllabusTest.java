package ca.yorku.eecs3311.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.agent.AgentException;
import ca.yorku.eecs3311.material.PdfImportException;
import ca.yorku.eecs3311.material.TestPdfs;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.repository.LocalRepository;
import ca.yorku.eecs3311.syllabus.SyllabusException;
import ca.yorku.eecs3311.syllabus.TaskDraft;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyApplicationSyllabusTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 15, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private static final String REPLY = "{\"tasks\":[{\"title\":\"Assignment 1\",\"type\":\"Assignment\","
            + "\"deadline\":\"2030-06-01\",\"note\":\"A1\"},{\"title\":\"Midterm\",\"type\":\"Exam\","
            + "\"deadline\":null,\"note\":\"date TBD\"}]}";

    @TempDir
    Path directory;

    private LocalRepository repository;
    private Course course;
    private Path pdf;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalRepository(directory.resolve("repository.json"));
        course = new Course("Algorithms");
        repository.saveCourse(course);
        pdf = TestPdfs.create(directory.resolve("syllabus.pdf"), "Assignment 1 due June 1");
    }

    private StudyApplication app(ScriptedLlm llm) {
        return new StudyApplication(repository, CLOCK, llm);
    }

    private static void complete(TaskDraft draft) {
        if (draft.getDeadline() == null) {
            draft.setDeadline(LocalDateTime.of(2030, 6, 10, 9, 0));
        }
        draft.setPriority(2);
        draft.setEstimatedMinutes(60);
    }

    @Test
    void importingProposesDraftsWithoutSavingAnything() throws Exception {
        List<TaskDraft> drafts = app(new ScriptedLlm(REPLY)).importSyllabus(course.getId(), pdf);

        assertEquals(2, drafts.size());
        assertEquals(List.of("priority", "duration"), drafts.get(0).getMissingFields());
        assertTrue(drafts.get(1).getMissingFields().contains("deadline"));
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void importingNeedsTheAssistantACourseAndAReadablePdf() throws Exception {
        assertThrows(IllegalStateException.class, () -> app(null).importSyllabus(course.getId(), pdf));
        assertThrows(IllegalArgumentException.class,
                () -> app(new ScriptedLlm(REPLY)).importSyllabus("unknown", pdf));
        assertThrows(IllegalArgumentException.class,
                () -> app(new ScriptedLlm(REPLY)).importSyllabus(" ", pdf));
        Path blank = TestPdfs.create(directory.resolve("blank.pdf"), "");
        assertThrows(PdfImportException.class,
                () -> app(new ScriptedLlm(REPLY)).importSyllabus(course.getId(), blank));
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void aFailingOrUselessAssistantSavesNothing() throws Exception {
        assertThrows(SyllabusException.class,
                () -> app(new ScriptedLlm()).importSyllabus(course.getId(), pdf));
        assertThrows(SyllabusException.class,
                () -> app(new ScriptedLlm("nothing useful")).importSyllabus(course.getId(), pdf));
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void confirmingSavesEveryCompletedDraftTogether() throws Exception {
        StudyApplication app = app(new ScriptedLlm(REPLY));
        List<TaskDraft> drafts = app.importSyllabus(course.getId(), pdf);
        drafts.forEach(StudyApplicationSyllabusTest::complete);

        List<AcademicTask> saved = app.confirmTasks(course.getId(), drafts);

        assertEquals(2, saved.size());
        assertEquals(2, app.getTasks(course.getId()).size());
        AcademicTask first = repository.getTask(drafts.get(0).getId());
        assertEquals("Assignment 1", first.getTitle());
        assertEquals(LocalDateTime.of(2030, 6, 1, 23, 59), first.getDeadline());
        assertEquals(60, first.getRemainingMinutes());
    }

    @Test
    void incompleteDraftsAreRejectedAndNothingIsSaved() throws Exception {
        StudyApplication app = app(new ScriptedLlm(REPLY));
        List<TaskDraft> drafts = app.importSyllabus(course.getId(), pdf);
        complete(drafts.get(0)); // the Midterm still has no deadline, priority or duration

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> app.confirmTasks(course.getId(), drafts));

        assertTrue(error.getMessage().contains("Task 2 ('Midterm') still needs: deadline, priority, duration."));
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void confirmingTheSameDraftsAgainDoesNotDuplicateTasks() throws Exception {
        StudyApplication app = app(new ScriptedLlm(REPLY));
        List<TaskDraft> drafts = app.importSyllabus(course.getId(), pdf);
        drafts.forEach(StudyApplicationSyllabusTest::complete);

        app.confirmTasks(course.getId(), drafts);
        List<AcademicTask> again = app.confirmTasks(course.getId(), drafts);

        assertTrue(again.isEmpty());
        assertEquals(2, repository.getTasks().size());
    }

    @Test
    void invalidConfirmationRequestsAreRejected() throws Exception {
        StudyApplication app = app(new ScriptedLlm(REPLY));
        List<TaskDraft> drafts = app.importSyllabus(course.getId(), pdf);
        drafts.forEach(StudyApplicationSyllabusTest::complete);
        TaskDraft duplicate = drafts.get(0);

        assertThrows(IllegalArgumentException.class, () -> app.confirmTasks(course.getId(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> app.confirmTasks(course.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> app.confirmTasks(" ", drafts));
        assertThrows(IllegalArgumentException.class, () -> app.confirmTasks("unknown", drafts));
        assertThrows(IllegalArgumentException.class,
                () -> app.confirmTasks(course.getId(), List.of(duplicate, duplicate)));
        assertTrue(repository.getTasks().isEmpty());
    }

    @Test
    void aDraftIdAlreadyUsedInAnotherCourseIsRejected() throws Exception {
        StudyApplication app = app(new ScriptedLlm(REPLY));
        List<TaskDraft> drafts = app.importSyllabus(course.getId(), pdf);
        drafts.forEach(StudyApplicationSyllabusTest::complete);
        app.confirmTasks(course.getId(), drafts);
        Course other = new Course("Databases");
        repository.saveCourse(other);

        assertThrows(IllegalArgumentException.class, () -> app.confirmTasks(other.getId(), drafts));
    }

    @Test
    void confirmingFlagsAcceptedPlansThatCouldGainWork() throws Exception {
        AcademicTask existing = new AcademicTask(course, "Existing", "Quiz",
                LocalDateTime.of(2030, 5, 30, 23, 59), 2, 60);
        repository.saveTask(existing);
        StudyPlan current = new StudyPlan(LocalDate.of(2030, 5, 13));
        current.addSession(new StudySession(existing.getId(),
                LocalDateTime.of(2030, 5, 16, 9, 0), LocalDateTime.of(2030, 5, 16, 10, 0)));
        current.accept();
        repository.savePlan(current);
        StudyPlan ended = new StudyPlan(LocalDate.of(2030, 4, 29));
        ended.accept();
        repository.savePlan(ended);
        StudyApplication app = app(new ScriptedLlm(REPLY));
        List<TaskDraft> drafts = app.importSyllabus(course.getId(), pdf);
        drafts.forEach(StudyApplicationSyllabusTest::complete);

        app.confirmTasks(course.getId(), drafts);

        assertTrue(repository.getPlans(LocalDate.of(2030, 5, 13)).get(0).needsRevision());
        assertFalse(repository.getPlans(LocalDate.of(2030, 4, 29)).get(0).needsRevision());
    }

    @Test
    void anUnusedAgentExceptionTypeStillCompilesWithTheFacade() {
        // keeps the facade's planning and syllabus exceptions distinct types
        assertFalse(AgentException.class.isAssignableFrom(SyllabusException.class));
    }
}
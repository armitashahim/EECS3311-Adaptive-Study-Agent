package ca.yorku.eecs3311.syllabus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskDraftTest {
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2030, 5, 30, 23, 59);

    @Test
    void aDraftReportsWhatTheStudentStillHasToProvide() {
        TaskDraft draft = new TaskDraft("Essay", null, null, "due soon");

        assertEquals(List.of("type", "deadline", "priority", "duration"), draft.getMissingFields());
        assertFalse(draft.isComplete());

        draft.setType("Assignment");
        draft.setDeadline(DEADLINE);
        draft.setPriority(2);
        assertEquals(List.of("duration"), draft.getMissingFields());

        draft.setEstimatedMinutes(90);
        assertTrue(draft.isComplete());
        assertEquals("due soon", draft.getNote());
    }

    @Test
    void aCompleteDraftBecomesATaskWithTheSameId() {
        TaskDraft draft = new TaskDraft("  Essay ", "Assignment", DEADLINE, null);
        draft.setPriority(3);
        draft.setEstimatedMinutes(120);
        Course course = new Course("Algorithms");

        AcademicTask task = draft.toTask(course);

        assertEquals(draft.getId(), task.getId());
        assertEquals("Essay", task.getTitle());
        assertEquals(120, task.getOriginalEstimatedMinutes());
        assertEquals(120, task.getAcceptedEstimatedMinutes());
        assertEquals(120, task.getRemainingMinutes());
        assertEquals(3, task.getPriority());
        assertEquals("", draft.getNote());
    }

    @Test
    void anIncompleteDraftCannotBecomeATask() {
        TaskDraft draft = new TaskDraft("Essay", "Assignment", DEADLINE, null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> draft.toTask(new Course("Algorithms")));

        assertTrue(error.getMessage().contains("priority, duration"));
    }

    @Test
    void invalidValuesAreRejected() {
        TaskDraft draft = new TaskDraft("Essay", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> new TaskDraft(" ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new TaskDraft(" ", "T", DEADLINE, null));
        assertThrows(IllegalArgumentException.class, () -> draft.setTitle(" "));
        assertThrows(IllegalArgumentException.class, () -> draft.setType(null));
        assertThrows(IllegalArgumentException.class, () -> draft.setDeadline(null));
        assertThrows(IllegalArgumentException.class, () -> draft.setPriority(0));
        assertThrows(IllegalArgumentException.class, () -> draft.setPriority(4));
        assertThrows(IllegalArgumentException.class, () -> draft.setEstimatedMinutes(0));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskDraft(" ", "Essay", null, DEADLINE, null));
    }
}
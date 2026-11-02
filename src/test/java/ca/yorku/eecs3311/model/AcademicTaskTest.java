package ca.yorku.eecs3311.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AcademicTaskTest {
    private static final LocalDateTime DEADLINE =
            LocalDateTime.of(2030, 5, 15, 23, 59);

    @Test
    void newTaskHasIdCourseAndInitializedEstimates() {
        Course course = new Course("Algorithms");

        AcademicTask task = new AcademicTask(
                course, "Assignment", "Coursework", DEADLINE, 2, 90);

        assertNotNull(task.getId());
        assertFalse(task.getId().isBlank());
        assertSame(course, task.getCourse());
        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(90, task.getAcceptedEstimatedMinutes());
        assertEquals(90, task.getRemainingMinutes());
    }

    @Test
    void newTaskIsNeitherCompletedNorArchived() {
        AcademicTask task = newTask();

        assertFalse(task.isCompleted());
        assertFalse(task.isArchived());
    }

    @Test
    void restorationConstructorPreservesSuppliedTaskState() {
        Course course = new Course("Algorithms");
        LocalDateTime deadline = DEADLINE.plusDays(4);

        AcademicTask task = new AcademicTask(
                "task-123", course, "Restored task", "Project", deadline,
                3, 120, 80, 25, false, true);

        assertEquals("task-123", task.getId());
        assertSame(course, task.getCourse());
        assertEquals("Restored task", task.getTitle());
        assertEquals("Project", task.getType());
        assertEquals(deadline, task.getDeadline());
        assertEquals(3, task.getPriority());
        assertEquals(120, task.getOriginalEstimatedMinutes());
        assertEquals(80, task.getAcceptedEstimatedMinutes());
        assertEquals(25, task.getRemainingMinutes());
        assertFalse(task.isCompleted());
        assertTrue(task.isArchived());
    }

    @Test
    void restorationConstructorAllowsCompletedTaskWithZeroRemainingMinutes() {
        AcademicTask task = restoreTask(
                "completed-task", new Course("Algorithms"),
                120, 80, 0, true, true);

        assertTrue(task.isCompleted());
        assertTrue(task.isArchived());
        assertEquals(0, task.getRemainingMinutes());
    }

    @Test
    void restorationConstructorRejectsCompletedTaskWithNonzeroRemainingMinutes() {
        assertThrows(IllegalArgumentException.class,
                () -> restoreTask("completed-task", new Course("Algorithms"),
                        120, 80, 1, true, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void restorationConstructorRejectsUnfinishedTaskWithNonpositiveRemainingMinutes(
            int remainingMinutes) {
        assertThrows(IllegalArgumentException.class,
                () -> restoreTask("unfinished-task", new Course("Algorithms"),
                        120, 80, remainingMinutes, false, false));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void restorationConstructorRejectsNullOrBlankIds(String id) {
        assertThrows(IllegalArgumentException.class,
                () -> restoreTask(id, new Course("Algorithms"),
                        120, 80, 25, false, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void restorationConstructorRejectsNonpositiveOriginalEstimates(int minutes) {
        assertThrows(IllegalArgumentException.class,
                () -> restoreTask("task-123", new Course("Algorithms"),
                        minutes, 80, 25, false, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void restorationConstructorRejectsNonpositiveAcceptedEstimates(int minutes) {
        assertThrows(IllegalArgumentException.class,
                () -> restoreTask("task-123", new Course("Algorithms"),
                        120, minutes, 25, false, false));
    }

    @Test
    void editingDetailsPreservesIdAndDurationFields() {
        AcademicTask task = newTask();
        task.updateRemainingEffort(60);
        String id = task.getId();

        task.updateDetails("  Final project  ", "  Assessment  ",
                DEADLINE.plusDays(2), 3);

        assertEquals(id, task.getId());
        assertEquals("Final project", task.getTitle());
        assertEquals("Assessment", task.getType());
        assertEquals(DEADLINE.plusDays(2), task.getDeadline());
        assertEquals(3, task.getPriority());
        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(90, task.getAcceptedEstimatedMinutes());
        assertEquals(60, task.getRemainingMinutes());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void invalidTitlesDoNotPartiallyChangeTask(String title) {
        AcademicTask task = newTask();

        assertThrows(IllegalArgumentException.class,
                () -> task.updateDetails(title, "Review", DEADLINE, 2));

        assertOriginalDetails(task);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void invalidTypesDoNotPartiallyChangeTask(String type) {
        AcademicTask task = newTask();

        assertThrows(IllegalArgumentException.class,
                () -> task.updateDetails("Changed title", type, DEADLINE, 2));

        assertOriginalDetails(task);
    }

    @Test
    void missingDeadlineDoesNotPartiallyChangeTask() {
        AcademicTask task = newTask();

        assertThrows(IllegalArgumentException.class,
                () -> task.updateDetails("Changed title", "Review", null, 2));

        assertOriginalDetails(task);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, -1})
    void invalidPrioritiesDoNotPartiallyChangeTask(int priority) {
        AcademicTask task = newTask();

        assertThrows(IllegalArgumentException.class,
                () -> task.updateDetails("Changed title", "Review", DEADLINE, priority));

        assertOriginalDetails(task);
    }

    @Test
    void acceptingEstimateChangesAcceptedAndRemainingButNotOriginalEstimate() {
        AcademicTask task = newTask();

        task.acceptEstimate(45);

        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(45, task.getAcceptedEstimatedMinutes());
        assertEquals(45, task.getRemainingMinutes());
    }

    @Test
    void updatingRemainingEffortPreservesBothEstimateFields() {
        AcademicTask task = newTask();
        task.acceptEstimate(75);

        task.updateRemainingEffort(30);

        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(75, task.getAcceptedEstimatedMinutes());
        assertEquals(30, task.getRemainingMinutes());
    }

    @Test
    void completingTaskSetsRemainingMinutesToZero() {
        AcademicTask task = newTask();
        task.updateRemainingEffort(30);

        task.markCompleted();

        assertTrue(task.isCompleted());
        assertEquals(0, task.getRemainingMinutes());
    }

    @Test
    void completedTaskRejectsRemainingEffortUpdatesAndNewEstimates() {
        AcademicTask task = newTask();
        task.markCompleted();

        assertThrows(IllegalStateException.class,
                () -> task.updateRemainingEffort(30));
        assertThrows(IllegalStateException.class,
                () -> task.acceptEstimate(45));

        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(90, task.getAcceptedEstimatedMinutes());
        assertEquals(0, task.getRemainingMinutes());
    }

    @Test
    void archivingPreservesTaskDataAndPreventsNewEstimates() {
        AcademicTask task = newTask();
        task.updateDetails("Project", "Assessment", DEADLINE.plusDays(1), 3);
        task.acceptEstimate(75);
        task.updateRemainingEffort(30);
        String id = task.getId();

        task.archive();

        assertTrue(task.isArchived());
        assertFalse(task.isCompleted());
        assertEquals(id, task.getId());
        assertEquals("Project", task.getTitle());
        assertEquals("Assessment", task.getType());
        assertEquals(DEADLINE.plusDays(1), task.getDeadline());
        assertEquals(3, task.getPriority());
        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(75, task.getAcceptedEstimatedMinutes());
        assertEquals(30, task.getRemainingMinutes());
        assertThrows(IllegalStateException.class, () -> task.acceptEstimate(45));
        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(75, task.getAcceptedEstimatedMinutes());
        assertEquals(30, task.getRemainingMinutes());
    }

    @Test
    void constructorRejectsMissingCourse() {
        assertThrows(IllegalArgumentException.class,
                () -> new AcademicTask(null, "Title", "Type", DEADLINE, 2, 60));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void constructorRejectsMissingTitle(String title) {
        assertThrows(IllegalArgumentException.class,
                () -> new AcademicTask(new Course("Algorithms"), title,
                        "Type", DEADLINE, 2, 60));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void constructorRejectsMissingType(String type) {
        assertThrows(IllegalArgumentException.class,
                () -> new AcademicTask(new Course("Algorithms"), "Title",
                        type, DEADLINE, 2, 60));
    }

    @Test
    void constructorRejectsMissingDeadline() {
        assertThrows(IllegalArgumentException.class,
                () -> new AcademicTask(new Course("Algorithms"), "Title",
                        "Type", null, 2, 60));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, -1})
    void constructorRejectsInvalidPriorities(int priority) {
        assertThrows(IllegalArgumentException.class,
                () -> new AcademicTask(new Course("Algorithms"), "Title",
                        "Type", DEADLINE, priority, 60));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructorRejectsNonpositiveDuration(int minutes) {
        assertThrows(IllegalArgumentException.class,
                () -> new AcademicTask(new Course("Algorithms"), "Title",
                        "Type", DEADLINE, 2, minutes));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void remainingEffortRejectsNonpositiveDurationWithoutChangingEstimates(int minutes) {
        AcademicTask task = newTask();

        assertThrows(IllegalArgumentException.class,
                () -> task.updateRemainingEffort(minutes));

        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(90, task.getAcceptedEstimatedMinutes());
        assertEquals(90, task.getRemainingMinutes());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void acceptEstimateRejectsNonpositiveDurationWithoutChangingEstimates(int minutes) {
        AcademicTask task = newTask();

        assertThrows(IllegalArgumentException.class, () -> task.acceptEstimate(minutes));

        assertEquals(90, task.getOriginalEstimatedMinutes());
        assertEquals(90, task.getAcceptedEstimatedMinutes());
        assertEquals(90, task.getRemainingMinutes());
    }

    private static AcademicTask newTask() {
        return new AcademicTask(
                new Course("Algorithms"), "task title", "task type", DEADLINE, 2, 90);
    }

    private static AcademicTask restoreTask(String id, Course course,
                                            int originalEstimatedMinutes,
                                            int acceptedEstimatedMinutes,
                                            int remainingMinutes,
                                            boolean completed,
                                            boolean archived) {
        return new AcademicTask(id, course, "Restored task", "Project",
                DEADLINE, 3, originalEstimatedMinutes,
                acceptedEstimatedMinutes, remainingMinutes, completed, archived);
    }

    private static void assertOriginalDetails(AcademicTask task) {
        assertEquals("task title", task.getTitle());
        assertEquals("task type", task.getType());
        assertEquals(DEADLINE, task.getDeadline());
        assertEquals(2, task.getPriority());
    }
}

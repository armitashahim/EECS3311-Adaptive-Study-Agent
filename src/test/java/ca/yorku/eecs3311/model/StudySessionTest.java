package ca.yorku.eecs3311.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StudySessionTest {
    private static final LocalDateTime START = LocalDateTime.of(2030, 5, 14, 9, 0);
    private static final LocalDateTime END = START.plusMinutes(90);

    @Test
    void newSessionIsScheduledWithNoRecordedTime() {
        StudySession session = newSession();

        assertNotNull(session.getId());
        assertFalse(session.getId().isBlank());
        assertEquals("task-1", session.getTaskId());
        assertEquals(START, session.getStartDateTime());
        assertEquals(END, session.getEndDateTime());
        assertEquals("scheduled", session.getStatus());
        assertEquals(0, session.getActualMinutes());
        assertEquals(90, session.getPlannedMinutes());
    }

    @Test
    void recordingCompletedOutcomeStoresStatusAndMinutes() {
        StudySession session = newSession();

        session.recordOutcome("completed", 80);

        assertEquals("completed", session.getStatus());
        assertEquals(80, session.getActualMinutes());
        assertEquals(90, session.getPlannedMinutes());
    }

    @Test
    void recordingPartialOutcomeStoresStatusAndMinutes() {
        StudySession session = newSession();

        session.recordOutcome("  Partial ", 30);

        assertEquals("partial", session.getStatus());
        assertEquals(30, session.getActualMinutes());
    }

    @Test
    void missedSessionRecordsZeroMinutes() {
        StudySession session = newSession();

        session.recordOutcome("missed", 45);

        assertEquals("missed", session.getStatus());
        assertEquals(0, session.getActualMinutes());
    }

    @Test
    void editingAnOutcomeReplacesThePreviousOne() {
        StudySession session = newSession();
        session.recordOutcome("completed", 80);

        session.recordOutcome("partial", 20);

        assertEquals("partial", session.getStatus());
        assertEquals(20, session.getActualMinutes());
    }

    @Test
    void negativeMinutesAreRejectedWithoutChangingTheSession() {
        StudySession session = newSession();
        session.recordOutcome("completed", 80);

        assertThrows(IllegalArgumentException.class,
                () -> session.recordOutcome("partial", -1));

        assertEquals("completed", session.getStatus());
        assertEquals(80, session.getActualMinutes());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "scheduled", "finished"})
    void invalidOutcomeStatusesAreRejectedWithoutChangingTheSession(String status) {
        StudySession session = newSession();

        assertThrows(IllegalArgumentException.class,
                () -> session.recordOutcome(status, 30));

        assertEquals("scheduled", session.getStatus());
        assertEquals(0, session.getActualMinutes());
    }

    @Test
    void restorationConstructorPreservesSuppliedState() {
        StudySession session = new StudySession(
                "session-1", "task-1", START, END, "completed", 75);

        assertEquals("session-1", session.getId());
        assertEquals("task-1", session.getTaskId());
        assertEquals("completed", session.getStatus());
        assertEquals(75, session.getActualMinutes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"scheduled", "missed"})
    void restorationRejectsMinutesOnScheduledOrMissedSessions(String status) {
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession("session-1", "task-1", START, END, status, 10));
    }

    @Test
    void restorationRejectsNegativeMinutesAndUnknownStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession("session-1", "task-1", START, END, "completed", -5));
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession("session-1", "task-1", START, END, "bogus", 0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void restorationRejectsNullOrBlankIds(String id) {
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession(id, "task-1", START, END, "scheduled", 0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void constructorRejectsNullOrBlankTaskIds(String taskId) {
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession(taskId, START, END));
    }

    @Test
    void constructorRejectsMissingTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession("task-1", null, END));
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession("task-1", START, null));
    }

    @Test
    void constructorRejectsEndThatIsNotAfterStart() {
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession("task-1", START, START));
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession("task-1", END, START));
    }

    private static StudySession newSession() {
        return new StudySession("task-1", START, END);
    }
}
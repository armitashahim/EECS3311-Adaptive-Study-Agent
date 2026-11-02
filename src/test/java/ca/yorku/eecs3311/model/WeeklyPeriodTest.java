package ca.yorku.eecs3311.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WeeklyPeriodTest {
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime ELEVEN = LocalTime.of(11, 0);

    @Test
    void newPeriodHasGeneratedIdAndSuppliedFields() {
        WeeklyPeriod period = availability(DayOfWeek.MONDAY, NINE, ELEVEN);

        assertNotNull(period.getId());
        assertFalse(period.getId().isBlank());
        assertEquals(DayOfWeek.MONDAY, period.getDayOfWeek());
        assertEquals(NINE, period.getStartTime());
        assertEquals(ELEVEN, period.getEndTime());
        assertEquals("availability", period.getType());
        assertTrue(period.isAvailability());
    }

    @Test
    void commitmentIsNotAvailability() {
        WeeklyPeriod period = new WeeklyPeriod(
                DayOfWeek.TUESDAY, NINE, ELEVEN, WeeklyPeriod.COMMITMENT);

        assertEquals("commitment", period.getType());
        assertFalse(period.isAvailability());
    }

    @Test
    void typeIsTrimmedAndCaseInsensitive() {
        WeeklyPeriod period = new WeeklyPeriod(
                DayOfWeek.MONDAY, NINE, ELEVEN, "  Commitment ");

        assertEquals("commitment", period.getType());
    }

    @Test
    void restorationConstructorPreservesId() {
        WeeklyPeriod period = new WeeklyPeriod(
                "period-123", DayOfWeek.FRIDAY, NINE, ELEVEN, "availability");

        assertEquals("period-123", period.getId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsNullOrBlankIds(String id) {
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyPeriod(id, DayOfWeek.MONDAY, NINE, ELEVEN, "availability"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "study", "work"})
    void rejectsMissingOrUnknownTypes(String type) {
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyPeriod(DayOfWeek.MONDAY, NINE, ELEVEN, type));
    }

    @Test
    void rejectsMissingDay() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyPeriod(null, NINE, ELEVEN, "availability"));
    }

    @Test
    void rejectsMissingTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyPeriod(DayOfWeek.MONDAY, null, ELEVEN, "availability"));
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyPeriod(DayOfWeek.MONDAY, NINE, null, "availability"));
    }

    @Test
    void rejectsEndTimeThatIsNotAfterStartTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyPeriod(DayOfWeek.MONDAY, ELEVEN, NINE, "availability"));
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyPeriod(DayOfWeek.MONDAY, NINE, NINE, "availability"));
    }

    @Test
    void updateTimesChangesTimesAndKeepsIdDayAndType() {
        WeeklyPeriod period = availability(DayOfWeek.MONDAY, NINE, ELEVEN);
        String id = period.getId();

        period.updateTimes(LocalTime.of(13, 0), LocalTime.of(15, 30));

        assertEquals(LocalTime.of(13, 0), period.getStartTime());
        assertEquals(LocalTime.of(15, 30), period.getEndTime());
        assertEquals(id, period.getId());
        assertEquals(DayOfWeek.MONDAY, period.getDayOfWeek());
        assertEquals("availability", period.getType());
    }

    @Test
    void invalidTimeUpdatesLeaveTimesUnchanged() {
        WeeklyPeriod period = availability(DayOfWeek.MONDAY, NINE, ELEVEN);

        assertThrows(IllegalArgumentException.class,
                () -> period.updateTimes(ELEVEN, NINE));
        assertThrows(IllegalArgumentException.class,
                () -> period.updateTimes(null, ELEVEN));
        assertThrows(IllegalArgumentException.class,
                () -> period.updateTimes(NINE, null));

        assertEquals(NINE, period.getStartTime());
        assertEquals(ELEVEN, period.getEndTime());
    }

    @Test
    void periodsOverlapWhenTheyShareTimeOnTheSameDay() {
        WeeklyPeriod first = availability(DayOfWeek.MONDAY, NINE, ELEVEN);
        WeeklyPeriod second = availability(
                DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0));

        assertTrue(first.overlaps(second));
        assertTrue(second.overlaps(first));
    }

    @Test
    void periodsThatOnlyTouchDoNotOverlap() {
        WeeklyPeriod first = availability(DayOfWeek.MONDAY, NINE, ELEVEN);
        WeeklyPeriod second = availability(
                DayOfWeek.MONDAY, ELEVEN, LocalTime.of(12, 0));

        assertFalse(first.overlaps(second));
        assertFalse(second.overlaps(first));
    }

    @Test
    void periodsOnDifferentDaysDoNotOverlap() {
        WeeklyPeriod monday = availability(DayOfWeek.MONDAY, NINE, ELEVEN);
        WeeklyPeriod tuesday = availability(DayOfWeek.TUESDAY, NINE, ELEVEN);

        assertFalse(monday.overlaps(tuesday));
        assertFalse(monday.overlaps(null));
    }

    private static WeeklyPeriod availability(DayOfWeek day, LocalTime start, LocalTime end) {
        return new WeeklyPeriod(day, start, end, WeeklyPeriod.AVAILABILITY);
    }
}
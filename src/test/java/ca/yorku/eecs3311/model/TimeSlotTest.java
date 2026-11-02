package ca.yorku.eecs3311.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TimeSlotTest {
    private static final LocalDateTime START = LocalDateTime.of(2030, 5, 14, 9, 0);

    @Test
    void calculatesDurationInMinutes() {
        TimeSlot slot = new TimeSlot(START, START.plusMinutes(90));

        assertEquals(90, slot.getDurationMinutes());
        assertEquals(START, slot.getStart());
        assertEquals(START.plusMinutes(90), slot.getEnd());
    }

    @Test
    void durationCanSpanMidnight() {
        TimeSlot slot = new TimeSlot(START.withHour(23), START.plusDays(1).withHour(1));

        assertEquals(120, slot.getDurationMinutes());
    }

    @Test
    void rejectsMissingStartOrEnd() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSlot(null, START));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSlot(START, null));
    }

    @Test
    void rejectsEndThatIsNotAfterStart() {
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSlot(START, START));
        assertThrows(IllegalArgumentException.class,
                () -> new TimeSlot(START, START.minusMinutes(1)));
    }
}
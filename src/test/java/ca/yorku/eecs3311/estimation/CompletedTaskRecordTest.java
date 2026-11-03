package ca.yorku.eecs3311.estimation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompletedTaskRecordTest {

    @Test
    void keepsSuppliedDurations() {
        CompletedTaskRecord record = new CompletedTaskRecord(60, 90);

        assertEquals(60, record.originalEstimatedMinutes());
        assertEquals(90, record.actualMinutes());
    }

    @Test
    void recordWithPositiveDurationsIsUsable() {
        assertTrue(new CompletedTaskRecord(60, 90).isUsable());
    }

    @Test
    void recordWithAZeroDurationIsNotUsable() {
        assertFalse(new CompletedTaskRecord(0, 90).isUsable());
        assertFalse(new CompletedTaskRecord(60, 0).isUsable());
        assertFalse(new CompletedTaskRecord(0, 0).isUsable());
    }

    @Test
    void rejectsNegativeDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompletedTaskRecord(-1, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new CompletedTaskRecord(60, -1));
    }
}
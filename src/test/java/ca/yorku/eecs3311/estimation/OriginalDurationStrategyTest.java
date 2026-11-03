package ca.yorku.eecs3311.estimation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OriginalDurationStrategyTest {

    @Test
    void returnsTheOriginalEstimate() {
        OriginalDurationStrategy strategy = new OriginalDurationStrategy();

        assertEquals(60, strategy.estimateMinutes(newTask(60), List.of()));
    }

    @Test
    void ignoresHistory() {
        OriginalDurationStrategy strategy = new OriginalDurationStrategy();
        List<CompletedTaskRecord> history = List.of(
                new CompletedTaskRecord(60, 120),
                new CompletedTaskRecord(60, 120),
                new CompletedTaskRecord(60, 120));

        assertEquals(60, strategy.estimateMinutes(newTask(60), history));
    }

    @Test
    void rejectsMissingTask() {
        assertThrows(IllegalArgumentException.class,
                () -> new OriginalDurationStrategy().estimateMinutes(null, List.of()));
    }

    static AcademicTask newTask(int originalMinutes) {
        return new AcademicTask(new Course("Algorithms"), "Task", "Coursework",
                LocalDateTime.of(2030, 5, 15, 23, 59), 2, originalMinutes);
    }
}
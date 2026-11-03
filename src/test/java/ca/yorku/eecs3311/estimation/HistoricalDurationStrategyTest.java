package ca.yorku.eecs3311.estimation;

import static ca.yorku.eecs3311.estimation.OriginalDurationStrategyTest.newTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalDurationStrategyTest {
    private final HistoricalDurationStrategy strategy = new HistoricalDurationStrategy();

    // Ratios 2.0, 1.0 and 1.5 average to exactly 1.5.
    private static List<CompletedTaskRecord> threeUsableRecords() {
        return List.of(
                new CompletedTaskRecord(40, 80),
                new CompletedTaskRecord(60, 60),
                new CompletedTaskRecord(30, 45));
    }

    @Test
    void scalesTheOriginalEstimateByTheAverageRatio() {
        assertEquals(90, strategy.estimateMinutes(newTask(60), threeUsableRecords()));
    }

    @Test
    void tasksThatTookLessTimeShrinkTheEstimate() {
        List<CompletedTaskRecord> history = List.of(
                new CompletedTaskRecord(100, 50),
                new CompletedTaskRecord(60, 30),
                new CompletedTaskRecord(40, 20));

        assertEquals(30, strategy.estimateMinutes(newTask(60), history));
    }

    @Test
    void recordsWithZeroDurationsAreExcluded() {
        List<CompletedTaskRecord> history = Arrays.asList(
                new CompletedTaskRecord(40, 80),
                new CompletedTaskRecord(0, 50),
                new CompletedTaskRecord(60, 60),
                new CompletedTaskRecord(50, 0),
                new CompletedTaskRecord(30, 45),
                new CompletedTaskRecord(0, 0));

        assertEquals(90, strategy.estimateMinutes(newTask(60), history));
    }

    @Test
    void missingRecordsInTheListAreSkipped() {
        List<CompletedTaskRecord> history = Arrays.asList(
                new CompletedTaskRecord(40, 80),
                null,
                new CompletedTaskRecord(60, 60),
                new CompletedTaskRecord(30, 45));

        assertEquals(90, strategy.estimateMinutes(newTask(60), history));
    }

    @Test
    void fewerThanThreeUsableRecordsFallBackToTheOriginalEstimate() {
        List<CompletedTaskRecord> history = List.of(
                new CompletedTaskRecord(40, 80),
                new CompletedTaskRecord(60, 60),
                new CompletedTaskRecord(0, 45),
                new CompletedTaskRecord(30, 0));

        assertEquals(60, strategy.estimateMinutes(newTask(60), history));
        assertEquals(60, strategy.estimateMinutes(newTask(60), List.of()));
    }

    @Test
    void resultIsRoundedToTheNearestWholeMinute() {
        // Ratios 1, 1 and 2 average to 4/3.
        List<CompletedTaskRecord> history = List.of(
                new CompletedTaskRecord(10, 10),
                new CompletedTaskRecord(10, 10),
                new CompletedTaskRecord(10, 20));

        assertEquals(13, strategy.estimateMinutes(newTask(10), history));
        assertEquals(15, strategy.estimateMinutes(newTask(11), history));
    }

    @Test
    void resultIsNeverLessThanOneMinute() {
        List<CompletedTaskRecord> history = List.of(
                new CompletedTaskRecord(1000, 1),
                new CompletedTaskRecord(1000, 1),
                new CompletedTaskRecord(1000, 1));

        assertEquals(1, strategy.estimateMinutes(newTask(10), history));
    }

    @Test
    void countsOnlyUsableRecords() {
        List<CompletedTaskRecord> history = Arrays.asList(
                new CompletedTaskRecord(40, 80),
                new CompletedTaskRecord(0, 50),
                null,
                new CompletedTaskRecord(30, 45));

        assertEquals(2, HistoricalDurationStrategy.countUsable(history));
        assertEquals(0, HistoricalDurationStrategy.countUsable(List.of()));
    }

    @Test
    void rejectsMissingTaskOrHistory() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.estimateMinutes(null, threeUsableRecords()));
        assertThrows(IllegalArgumentException.class,
                () -> strategy.estimateMinutes(newTask(60), null));
        assertThrows(IllegalArgumentException.class,
                () -> HistoricalDurationStrategy.countUsable(null));
    }
}
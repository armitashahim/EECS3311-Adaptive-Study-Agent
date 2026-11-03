package ca.yorku.eecs3311.estimation;

import static ca.yorku.eecs3311.estimation.OriginalDurationStrategyTest.newTask;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DurationEstimatorTest {
    private static final List<CompletedTaskRecord> HISTORY = List.of(
            new CompletedTaskRecord(40, 80),
            new CompletedTaskRecord(60, 60),
            new CompletedTaskRecord(30, 45));

    @Test
    void usesTheOriginalStrategyByDefault() {
        DurationEstimator estimator = new DurationEstimator();

        assertEquals(60, estimator.suggestMinutes(newTask(60), HISTORY));
    }

    @Test
    void switchingStrategyChangesTheSuggestion() {
        DurationEstimator estimator = new DurationEstimator();

        estimator.setStrategy(new HistoricalDurationStrategy());
        assertEquals(90, estimator.suggestMinutes(newTask(60), HISTORY));

        estimator.setStrategy(new OriginalDurationStrategy());
        assertEquals(60, estimator.suggestMinutes(newTask(60), HISTORY));
    }

    @Test
    void delegatesToTheSelectedStrategy() {
        DurationEstimator estimator = new DurationEstimator((task, history) -> 42);

        assertEquals(42, estimator.suggestMinutes(newTask(60), HISTORY));
    }

    @Test
    void rejectsMissingStrategyAndKeepsThePreviousOne() {
        DurationEstimator estimator = new DurationEstimator(new HistoricalDurationStrategy());

        assertThrows(IllegalArgumentException.class, () -> estimator.setStrategy(null));
        assertThrows(IllegalArgumentException.class, () -> new DurationEstimator(null));

        assertEquals(90, estimator.suggestMinutes(newTask(60), HISTORY));
    }
}
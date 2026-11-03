package ca.yorku.eecs3311.estimation;

import ca.yorku.eecs3311.model.AcademicTask;
import java.util.List;

/** Strategy context: asks whichever strategy is currently selected for an estimate. */
public class DurationEstimator {
    private DurationEstimationStrategy strategy;

    public DurationEstimator() {
        this(new OriginalDurationStrategy());
    }

    public DurationEstimator(DurationEstimationStrategy strategy) {
        setStrategy(strategy);
    }

    public void setStrategy(DurationEstimationStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Estimation strategy is required.");
        }

        this.strategy = strategy;
    }

    public int suggestMinutes(AcademicTask task, List<CompletedTaskRecord> history) {
        return strategy.estimateMinutes(task, history);
    }
}
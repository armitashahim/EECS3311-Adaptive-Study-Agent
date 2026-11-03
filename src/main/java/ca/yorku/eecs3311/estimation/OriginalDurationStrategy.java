package ca.yorku.eecs3311.estimation;

import ca.yorku.eecs3311.model.AcademicTask;
import java.util.List;

/** Concrete strategy: trust the student's own estimate and ignore history. */
public class OriginalDurationStrategy implements DurationEstimationStrategy {

    @Override
    public int estimateMinutes(AcademicTask task, List<CompletedTaskRecord> history) {
        if (task == null) {
            throw new IllegalArgumentException("Task is required.");
        }

        return task.getOriginalEstimatedMinutes();
    }
}
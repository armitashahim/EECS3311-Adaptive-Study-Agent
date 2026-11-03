package ca.yorku.eecs3311.estimation;

import ca.yorku.eecs3311.model.AcademicTask;
import java.util.List;

/**
 * Concrete strategy: scale the original estimate by the average
 * actual-to-estimated ratio of comparable finished tasks. With too little
 * usable history it falls back to the original estimate.
 */
public class HistoricalDurationStrategy implements DurationEstimationStrategy {
    public static final int MINIMUM_HISTORY = 3;

    @Override
    public int estimateMinutes(AcademicTask task, List<CompletedTaskRecord> history) {
        if (task == null) {
            throw new IllegalArgumentException("Task is required.");
        }

        if (history == null) {
            throw new IllegalArgumentException("History is required.");
        }

        double ratioSum = 0;
        int usable = 0;
        for (CompletedTaskRecord record : history) {
            if (record != null && record.isUsable()) {
                ratioSum += (double) record.actualMinutes() / record.originalEstimatedMinutes();
                usable++;
            }
        }

        if (usable < MINIMUM_HISTORY) {
            return task.getOriginalEstimatedMinutes();
        }

        double suggested = task.getOriginalEstimatedMinutes() * (ratioSum / usable);
        long rounded = Math.round(suggested);
        // Always a positive whole number of minutes.
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, rounded));
    }

    /** How many records can actually support a historical suggestion. */
    public static int countUsable(List<CompletedTaskRecord> history) {
        if (history == null) {
            throw new IllegalArgumentException("History is required.");
        }

        int usable = 0;
        for (CompletedTaskRecord record : history) {
            if (record != null && record.isUsable()) {
                usable++;
            }
        }
        return usable;
    }
}
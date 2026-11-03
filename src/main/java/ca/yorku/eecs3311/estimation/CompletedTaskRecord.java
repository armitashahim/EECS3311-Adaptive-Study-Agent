package ca.yorku.eecs3311.estimation;

/**
 * What a finished task of the same type taught us: the student's original
 * estimate and the study minutes actually recorded for it.
 */
public record CompletedTaskRecord(int originalEstimatedMinutes, int actualMinutes) {

    public CompletedTaskRecord {
        if (originalEstimatedMinutes < 0 || actualMinutes < 0) {
            throw new IllegalArgumentException(
                    "Recorded durations cannot be negative.");
        }
    }

    // Records with a zero (or missing) duration cannot give a usable ratio.
    public boolean isUsable() {
        return originalEstimatedMinutes > 0 && actualMinutes > 0;
    }
}
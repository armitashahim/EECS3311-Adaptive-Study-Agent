package ca.yorku.eecs3311.estimation;

import ca.yorku.eecs3311.model.AcademicTask;
import java.util.List;

/** Strategy interface: one way of suggesting how long a task will take. */
public interface DurationEstimationStrategy {
    /**
     * @param task    the task being estimated; its original estimate is the starting point
     * @param history finished tasks of the same type, with their recorded actual minutes
     * @return a positive suggested number of minutes
     */
    int estimateMinutes(AcademicTask task, List<CompletedTaskRecord> history);
}

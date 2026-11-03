package ca.yorku.eecs3311.application;

import ca.yorku.eecs3311.estimation.CompletedTaskRecord;
import ca.yorku.eecs3311.estimation.DurationEstimator;
import ca.yorku.eecs3311.estimation.HistoricalDurationStrategy;
import ca.yorku.eecs3311.estimation.OriginalDurationStrategy;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade: the single entry point the GUI and CLI use. It validates requests,
 * coordinates the domain classes, the duration estimator and the repository,
 * and keeps related updates together in one atomic repository commit.
 *
 * This version covers courses and tasks (F01), weekly availability (F02),
 * study progress (F06) and duration estimates (F07).
 */
public class StudyApplication {
    private final LocalRepository repository;
    private final Clock clock;
    private final DurationEstimator durationEstimator = new DurationEstimator();

    public StudyApplication(LocalRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public StudyApplication(LocalRepository repository, Clock clock) {
        if (repository == null) {
            throw new IllegalArgumentException("Repository is required.");
        }

        if (clock == null) {
            throw new IllegalArgumentException("Clock is required.");
        }

        this.repository = repository;
        this.clock = clock;
    }

    // ----- F01: courses and tasks -----

    public void saveCourse(Course course) throws IOException {
        if (course == null) {
            throw new IllegalArgumentException("Course is required.");
        }

        repository.saveCourse(course);
    }

    // Rejected while any task (archived ones included) still belongs to the course.
    public void removeCourse(String courseId) throws IOException {
        repository.deleteCourse(courseId);
    }

    public List<Course> getCourses() {
        return repository.getCourses();
    }

    public void saveTask(String courseId, AcademicTask task) throws IOException {
        if (courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("Course ID cannot be empty.");
        }

        if (task == null) {
            throw new IllegalArgumentException("Task is required.");
        }

        if (!courseId.equals(task.getCourse().getId())) {
            throw new IllegalArgumentException(
                    "Task does not belong to course: " + courseId);
        }

        repository.getCourse(courseId);

        List<StudyPlan> flaggedPlans = List.of();
        AcademicTask previous = findTask(task.getId());
        if (previous != null) {
            if (previous.isArchived()) {
                throw new IllegalStateException("An archived task cannot be edited.");
            }

            if (!previous.getCourse().getId().equals(courseId)) {
                throw new IllegalArgumentException(
                        "A task cannot be moved to another course.");
            }

            if (schedulingChanged(previous, task)) {
                flaggedPlans = flagPlansScheduling(task.getId());
            }
        }

        List<StudyPlan> plansToSave = flaggedPlans;
        repository.commit(draft -> {
            draft.saveTask(task);
            plansToSave.forEach(draft::savePlan);
        });
    }

    /**
     * Remove a task: it is archived, its future unrecorded sessions are removed
     * from every plan, and past sessions and recorded progress are kept.
     */
    public void removeTask(String taskId) throws IOException {
        AcademicTask task = repository.getTask(taskId);
        LocalDateTime now = now();
        task.archive();

        List<StudyPlan> changedPlans = new ArrayList<>();
        for (StudyPlan plan : repository.getPlans()) {
            List<StudySession> kept = new ArrayList<>();
            boolean removedSession = false;
            for (StudySession session : plan.getSessions()) {
                boolean futureSession = session.getTaskId().equals(taskId)
                        && StudySession.SCHEDULED.equals(session.getStatus())
                        && session.getStartDateTime().isAfter(now);
                if (futureSession) {
                    removedSession = true;
                } else {
                    kept.add(session);
                }
            }

            Map<String, Integer> unscheduled = new LinkedHashMap<>(plan.getUnscheduledMinutes());
            boolean removedUnscheduled = unscheduled.remove(taskId) != null;

            if (removedSession || removedUnscheduled) {
                StudyPlan rebuilt = new StudyPlan(plan.getId(), plan.getWeekStartDate(),
                        plan.isAccepted(), plan.needsRevision(),
                        plan.getPriorityExplanation(), unscheduled, kept);
                if (removedSession && isLive(rebuilt)) {
                    rebuilt.markNeedsRevision();
                }
                changedPlans.add(rebuilt);
            }
        }

        repository.commit(draft -> {
            draft.saveTask(task);
            changedPlans.forEach(draft::savePlan);
        });
    }

    // Archived (removed) tasks are not listed.
    public List<AcademicTask> getTasks(String courseId) {
        List<AcademicTask> result = new ArrayList<>();
        for (AcademicTask task : repository.getTasks(courseId)) {
            if (!task.isArchived()) {
                result.add(task);
            }
        }
        return List.copyOf(result);
    }

    // ----- F02: weekly availability and commitments -----

    public void savePeriod(WeeklyPeriod period) throws IOException {
        if (period == null) {
            throw new IllegalArgumentException("Period is required.");
        }

        if (period.isAvailability()) {
            for (WeeklyPeriod existing : repository.getPeriods()) {
                if (existing.isAvailability()
                        && !existing.getId().equals(period.getId())
                        && existing.overlaps(period)) {
                    throw new IllegalArgumentException(
                            "Availability periods cannot overlap; adjust or merge them.");
                }
            }
        }

        List<StudyPlan> flaggedPlans = flagAllLivePlans();
        repository.commit(draft -> {
            draft.savePeriod(period);
            flaggedPlans.forEach(draft::savePlan);
        });
    }

    public void removePeriod(String periodId) throws IOException {
        List<StudyPlan> flaggedPlans = flagAllLivePlans();
        repository.commit(draft -> {
            draft.deletePeriod(periodId);
            flaggedPlans.forEach(draft::savePlan);
        });
    }

    public List<WeeklyPeriod> getPeriods() {
        return repository.getPeriods();
    }

    public List<StudyPlan> getPlans(LocalDate weekStart) {
        return repository.getPlans(weekStart);
    }

    // ----- F06: study progress -----

    /**
     * Record what happened in a session and update its task. The session
     * outcome, task progress and plan revision flags are saved together.
     */
    public void recordProgress(StudySession session, String status, int actualMinutes,
                               boolean taskCompleted, int remainingMinutes)
            throws IOException {
        if (session == null) {
            throw new IllegalArgumentException("Session is required.");
        }

        List<StudyPlan> plans = repository.getPlans();
        StudyPlan ownerPlan = null;
        StudySession stored = null;
        for (StudyPlan plan : plans) {
            for (StudySession candidate : plan.getSessions()) {
                if (candidate.getId().equals(session.getId())) {
                    ownerPlan = plan;
                    stored = candidate;
                }
            }
        }

        if (stored == null) {
            throw new IllegalArgumentException("Session not found: " + session.getId());
        }

        if (now().isBefore(stored.getStartDateTime())) {
            throw new IllegalStateException(
                    "A session can only be recorded after it has started.");
        }

        AcademicTask task = repository.getTask(stored.getTaskId());
        if (taskCompleted) {
            task.markCompleted();
        } else {
            if (task.isCompleted()) {
                throw new IllegalStateException(
                        "A completed task cannot be reopened.");
            }
            task.updateRemainingEffort(remainingMinutes);
        }

        // Replaces any earlier outcome, so editing a record never double counts.
        stored.recordOutcome(status, actualMinutes);

        List<StudyPlan> plansToSave = new ArrayList<>();
        for (StudyPlan plan : plans) {
            boolean affected = isLive(plan) && plan.isAccepted()
                    && hasSessionFor(plan, task.getId());
            if (affected) {
                plan.markNeedsRevision();
            }
            if (affected || plan == ownerPlan) {
                plansToSave.add(plan);
            }
        }

        repository.commit(draft -> {
            draft.saveTask(task);
            plansToSave.forEach(draft::savePlan);
        });
    }

    // ----- F07: adaptive duration estimates -----

    public int suggestDuration(AcademicTask task) {
        return getDurationSuggestion(task).minutes();
    }

    public DurationSuggestion getDurationSuggestion(AcademicTask task) {
        if (task == null) {
            throw new IllegalArgumentException("Task is required.");
        }

        requireEstimable(task);

        List<CompletedTaskRecord> history = historyFor(task.getType());
        int supporting = HistoricalDurationStrategy.countUsable(history);
        boolean useHistory = supporting >= HistoricalDurationStrategy.MINIMUM_HISTORY;

        durationEstimator.setStrategy(useHistory
                ? new HistoricalDurationStrategy()
                : new OriginalDurationStrategy());
        int minutes = durationEstimator.suggestMinutes(task, history);
        return new DurationSuggestion(minutes, supporting, useHistory);
    }

    /**
     * Accept a duration for an unstarted task. The original estimate is kept.
     * A task that has not been saved yet is only updated in memory; saving it
     * with saveTask() stores the accepted estimate.
     */
    public void acceptDuration(AcademicTask task, int minutes) throws IOException {
        if (task == null) {
            throw new IllegalArgumentException("Task is required.");
        }

        requireEstimable(task);
        task.acceptEstimate(minutes);

        if (findTask(task.getId()) == null) {
            return;
        }

        List<StudyPlan> flaggedPlans = flagPlansScheduling(task.getId());
        repository.commit(draft -> {
            draft.saveTask(task);
            flaggedPlans.forEach(draft::savePlan);
        });
    }

    // ----- Helpers -----

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // A plan can still be revised until its week has ended.
    private boolean isLive(StudyPlan plan) {
        return plan.getWeekStartDate().plusDays(7).atStartOfDay().isAfter(now());
    }

    private static boolean hasSessionFor(StudyPlan plan, String taskId) {
        for (StudySession session : plan.getSessions()) {
            if (session.getTaskId().equals(taskId)) {
                return true;
            }
        }
        return false;
    }

    // Mark accepted, still-revisable plans that schedule this task; returns them to be saved.
    private List<StudyPlan> flagPlansScheduling(String taskId) {
        List<StudyPlan> flagged = new ArrayList<>();
        for (StudyPlan plan : repository.getPlans()) {
            if (isLive(plan) && plan.isAccepted() && hasSessionFor(plan, taskId)) {
                plan.markNeedsRevision();
                flagged.add(plan);
            }
        }
        return flagged;
    }

    // Mark every accepted, still-revisable plan; returns them to be saved.
    private List<StudyPlan> flagAllLivePlans() {
        List<StudyPlan> flagged = new ArrayList<>();
        for (StudyPlan plan : repository.getPlans()) {
            if (isLive(plan) && plan.isAccepted()) {
                plan.markNeedsRevision();
                flagged.add(plan);
            }
        }
        return flagged;
    }

    private AcademicTask findTask(String taskId) {
        for (AcademicTask task : repository.getTasks()) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    private static boolean schedulingChanged(AcademicTask before, AcademicTask after) {
        return !before.getDeadline().equals(after.getDeadline())
                || before.getPriority() != after.getPriority()
                || before.getAcceptedEstimatedMinutes() != after.getAcceptedEstimatedMinutes()
                || before.getRemainingMinutes() != after.getRemainingMinutes()
                || before.isCompleted() != after.isCompleted()
                || before.isArchived() != after.isArchived();
    }

    private void requireEstimable(AcademicTask task) {
        if (task.isCompleted() || task.isArchived()) {
            throw new IllegalStateException(
                    "Only an active task can receive a duration estimate.");
        }

        if (findTask(task.getId()) != null && hasRecordedWork(task.getId())) {
            throw new IllegalStateException(
                    "The task has already started; update its remaining effort instead.");
        }
    }

    private boolean hasRecordedWork(String taskId) {
        for (StudySession session : repository.getSessionsForTask(taskId)) {
            if (!StudySession.SCHEDULED.equals(session.getStatus())) {
                return true;
            }
        }
        return false;
    }

    // Finished tasks of the same type, each with its total recorded study minutes.
    private List<CompletedTaskRecord> historyFor(String taskType) {
        List<CompletedTaskRecord> history = new ArrayList<>();
        for (AcademicTask completed : repository.getCompletedTasks(taskType)) {
            int actualMinutes = 0;
            for (StudySession session : repository.getSessionsForTask(completed.getId())) {
                actualMinutes += session.getActualMinutes();
            }
            history.add(new CompletedTaskRecord(
                    completed.getOriginalEstimatedMinutes(), actualMinutes));
        }
        return history;
    }
}
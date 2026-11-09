package ca.yorku.eecs3311.application;

import ca.yorku.eecs3311.agent.AgentException;
import ca.yorku.eecs3311.agent.AgentResult;
import ca.yorku.eecs3311.agent.PlanningContext;
import ca.yorku.eecs3311.agent.StudyAgent;
import ca.yorku.eecs3311.ai.LLMClient;
import ca.yorku.eecs3311.estimation.CompletedTaskRecord;
import ca.yorku.eecs3311.estimation.DurationEstimator;
import ca.yorku.eecs3311.estimation.HistoricalDurationStrategy;
import ca.yorku.eecs3311.estimation.OriginalDurationStrategy;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.material.PdfImportException;
import ca.yorku.eecs3311.material.PdfImporter;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.TimeSlot;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import ca.yorku.eecs3311.repository.LocalRepository;
import ca.yorku.eecs3311.syllabus.SyllabusException;
import ca.yorku.eecs3311.syllabus.SyllabusService;
import ca.yorku.eecs3311.syllabus.TaskDraft;
import ca.yorku.eecs3311.scheduling.ScheduleResult;
import ca.yorku.eecs3311.scheduling.Scheduler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Facade: the single entry point the GUI and CLI use. It validates requests,
 * coordinates the domain classes, the duration estimator and the repository,
 * and keeps related updates together in one atomic repository commit.
 *
 * This version covers courses and tasks (F01), weekly availability (F02),
 * study progress (F06), duration estimates (F07), AI-assisted weekly
 * planning and revision (F04, F05), course materials (F08) and syllabus import (F03).
 */
public class StudyApplication {
    private final LocalRepository repository;
    private final Clock clock;
    private final LLMClient llm;
    private final PdfImporter pdfImporter;
    private final DurationEstimator durationEstimator = new DurationEstimator();
    private final Scheduler scheduler = new Scheduler();

    public StudyApplication(LocalRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public StudyApplication(LocalRepository repository, Clock clock) {
        this(repository, clock, null);
    }

    // The LLM client may be null; planning then reports that AI is not configured.
    public StudyApplication(LocalRepository repository, Clock clock, LLMClient llm) {
        this(repository, clock, llm, new PdfImporter());
    }

    public StudyApplication(LocalRepository repository, Clock clock, LLMClient llm,
                            PdfImporter pdfImporter) {
        if (pdfImporter == null) {
            throw new IllegalArgumentException("PDF importer is required.");
        }

        if (repository == null) {
            throw new IllegalArgumentException("Repository is required.");
        }

        if (clock == null) {
            throw new IllegalArgumentException("Clock is required.");
        }

        this.repository = repository;
        this.clock = clock;
        this.llm = llm;
        this.pdfImporter = pdfImporter;
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

    // ----- F04 / F05: weekly planning and revision -----

    /**
     * Ask the planning agent for a new plan for a week. Nothing is saved until the
     * proposal is accepted with {@link #acceptPlan(PlanProposal)}.
     */
    public PlanProposal generatePlan(LocalDate weekStart) throws AgentException {
        requirePlannableWeek(weekStart);
        if (!repository.getPlans(weekStart).isEmpty()) {
            throw new IllegalStateException(
                    "This week already has a plan; revise it instead.");
        }
        requireLlm();

        boolean hasAvailability = repository.getPeriods().stream()
                .anyMatch(WeeklyPeriod::isAvailability);
        if (!hasAvailability) {
            throw new IllegalStateException(
                    "Add weekly availability before generating a plan.");
        }
        PlanningContext context = buildContext(weekStart, false, List.of());
        if (context.getTasks().isEmpty()) {
            throw new IllegalStateException("There are no tasks that need study time.");
        }
        if (context.getSlots().isEmpty()) {
            throw new IllegalStateException(
                    "No usable study time remains in this week.");
        }

        AgentResult result = new StudyAgent(llm, scheduler).run(context);
        StudyPlan plan = new StudyPlan(weekStart);
        return toProposal(plan, false, List.of(), result, weekStart);
    }

    /**
     * Ask the planning agent to rebuild the future sessions of the week's plan.
     * Past and recorded sessions are kept. Nothing is saved until the proposal is accepted.
     */
    public PlanProposal revisePlan(LocalDate weekStart) throws AgentException {
        requirePlannableWeek(weekStart);
        List<StudyPlan> existing = repository.getPlans(weekStart);
        if (existing.isEmpty()) {
            throw new IllegalStateException("This week has no plan to revise.");
        }
        StudyPlan current = existing.get(0);
        List<StudySession> kept = keptSessions(current);

        PlanningContext context = buildContext(weekStart, true, kept);
        StudyPlan base = new StudyPlan(current.getId(), weekStart, current.isAccepted(), false,
                current.getPriorityExplanation(), Map.of(), List.of());
        if (context.getTasks().isEmpty()) {
            AgentResult nothingLeft = new AgentResult(
                    new ScheduleResult(List.of(), Map.of()),
                    "No unfinished tasks remain, so the future sessions were removed.");
            return toProposal(base, true, kept, nothingLeft, weekStart);
        }

        requireLlm();
        AgentResult result = new StudyAgent(llm, scheduler).run(context);
        return toProposal(base, true, kept, result, weekStart);
    }

    /**
     * Save a proposal. The stored data must be unchanged since the proposal was made and
     * every proposed session must still be valid; otherwise nothing is saved.
     */
    public void acceptPlan(PlanProposal proposal) throws IOException {
        if (proposal == null) {
            throw new IllegalArgumentException("Proposal is required.");
        }
        LocalDate week = proposal.plan().getWeekStartDate();
        if (!proposal.fingerprint().equals(fingerprint(week))) {
            throw new IllegalStateException("Your tasks, availability or plan changed since "
                    + "this proposal was made. Generate it again.");
        }

        List<StudyPlan> existing = repository.getPlans(week);
        List<StudySession> kept = proposal.revision() ? keptSessions(existing.get(0)) : List.of();
        PlanningContext fresh = buildContext(week, proposal.revision(), kept);
        List<StudySession> proposed = new ArrayList<>();
        for (StudySession session : proposal.plan().getSessions()) {
            if (proposal.newSessionIds().contains(session.getId())) {
                proposed.add(session);
            }
        }
        List<String> problems = scheduler.validatePlan(proposed, fresh.getTasks(), fresh.getSlots());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The proposal is no longer valid ("
                    + problems.get(0) + "). Generate it again.");
        }

        StudyPlan source = proposal.plan();
        StudyPlan accepted = new StudyPlan(source.getId(), week, true, false,
                source.getPriorityExplanation(), source.getUnscheduledMinutes(),
                source.getSessions());
        repository.commit(draft -> draft.savePlan(accepted));
    }

    private void requireLlm() {
        if (llm == null) {
            throw new IllegalStateException(
                    "The AI assistant is not configured. Add LLM_API_KEY to your .env file.");
        }
    }

    private void requirePlannableWeek(LocalDate weekStart) {
        if (weekStart == null) {
            throw new IllegalArgumentException("Week start date is required.");
        }
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("A week must start on a Monday.");
        }
        if (!weekStart.plusDays(7).atStartOfDay().isAfter(now())) {
            throw new IllegalStateException("That week has already ended.");
        }
    }

    // Past, started or recorded sessions stay; only future unrecorded ones are rebuilt.
    private List<StudySession> keptSessions(StudyPlan plan) {
        LocalDateTime now = now();
        List<StudySession> kept = new ArrayList<>();
        for (StudySession session : plan.getSessions()) {
            boolean replaceable = StudySession.SCHEDULED.equals(session.getStatus())
                    && session.getStartDateTime().isAfter(now);
            if (!replaceable) {
                kept.add(session);
            }
        }
        return kept;
    }

    private PlanningContext buildContext(LocalDate weekStart, boolean revision,
                                         List<StudySession> kept) {
        LocalDateTime now = now();
        LocalDateTime notBefore = now.isAfter(weekStart.atStartOfDay())
                ? now : weekStart.atStartOfDay();
        List<TimeSlot> busy = new ArrayList<>();
        kept.forEach(session ->
                busy.add(new TimeSlot(session.getStartDateTime(), session.getEndDateTime())));
        List<WeeklyPeriod> periods = repository.getPeriods();
        List<TimeSlot> slots = scheduler.getAvailableSlots(periods, weekStart, notBefore, busy);

        List<AcademicTask> tasks = new ArrayList<>();
        Map<String, Integer> studied = new HashMap<>();
        for (AcademicTask task : repository.getTasks()) {
            if (!task.isArchived() && !task.isCompleted() && task.getRemainingMinutes() > 0) {
                tasks.add(task);
            }
            int minutes = 0;
            for (StudySession session : repository.getSessionsForTask(task.getId())) {
                minutes += session.getActualMinutes();
            }
            studied.put(task.getId(), minutes);
        }
        return new PlanningContext(weekStart, now, revision, tasks, periods, slots, studied);
    }

    private PlanProposal toProposal(StudyPlan base, boolean revision, List<StudySession> kept,
                                    AgentResult result, LocalDate weekStart) {
        StudyPlan plan = new StudyPlan(base.getId(), weekStart, base.isAccepted(), false,
                result.explanation(), result.schedule().unscheduledMinutes(), kept);
        Set<String> newIds = new LinkedHashSet<>();
        for (StudySession session : result.schedule().sessions()) {
            plan.addSession(session);
            newIds.add(session.getId());
        }
        return new PlanProposal(plan, revision, fingerprint(weekStart), newIds);
    }

    // A summary of every stored value planning depends on, to detect changes before accepting.
    private String fingerprint(LocalDate weekStart) {
        StringBuilder state = new StringBuilder();
        repository.getTasks().stream().sorted(Comparator.comparing(AcademicTask::getId))
                .forEach(task -> state.append("T|").append(task.getId()).append('|')
                        .append(task.getDeadline()).append('|').append(task.getPriority()).append('|')
                        .append(task.getRemainingMinutes()).append('|').append(task.isCompleted())
                        .append('|').append(task.isArchived()).append('\n'));
        repository.getPeriods().stream().sorted(Comparator.comparing(WeeklyPeriod::getId))
                .forEach(period -> state.append("P|").append(period.getId()).append('|')
                        .append(period.getDayOfWeek()).append('|').append(period.getStartTime())
                        .append('|').append(period.getEndTime()).append('|')
                        .append(period.getType()).append('\n'));
        for (StudyPlan plan : repository.getPlans(weekStart)) {
            state.append("L|").append(plan.getId()).append('|').append(plan.isAccepted())
                    .append('|').append(plan.needsRevision()).append('\n');
            plan.getSessions().stream().sorted(Comparator.comparing(StudySession::getId))
                    .forEach(session -> state.append("S|").append(session.getId()).append('|')
                            .append(session.getStartDateTime()).append('|')
                            .append(session.getEndDateTime()).append('|')
                            .append(session.getStatus()).append('|')
                            .append(session.getActualMinutes()).append('\n'));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(state.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    // ----- F08: course materials -----

    /**
     * Import a text-based PDF as a course material. The course and title are checked
     * first, then the PDF is validated and read; a failed import saves nothing.
     */
    public CourseMaterial importMaterial(String courseId, String title, Path pdf)
            throws PdfImportException, IOException {
        if (courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("Course ID cannot be empty.");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Material title cannot be empty.");
        }

        Course course = repository.getCourse(courseId);
        String text = pdfImporter.extractText(pdf);
        CourseMaterial material = new CourseMaterial(course, title, text);
        repository.saveMaterial(material);
        return material;
    }

    public List<CourseMaterial> getMaterials(String courseId) {
        return repository.getMaterials(courseId);
    }

    public CourseMaterial getMaterial(String materialId) {
        return repository.getMaterial(materialId);
    }

    public void removeMaterial(String materialId) throws IOException {
        repository.deleteMaterial(materialId);
    }

    // ----- F03: syllabus import -----

    /**
     * Read a syllabus PDF and ask the AI for proposed tasks. Nothing is saved; the student
     * reviews and completes the drafts, then calls {@link #confirmTasks}.
     */
    public List<TaskDraft> importSyllabus(String courseId, Path pdf)
            throws PdfImportException, SyllabusException {
        if (courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("Course ID cannot be empty.");
        }
        requireLlm();
        repository.getCourse(courseId);

        String text = pdfImporter.extractText(pdf);
        return new SyllabusService(llm).extractTasks(text, now().toLocalDate());
    }

    /**
     * Save the reviewed drafts as tasks of the course, all together. Every draft must be
     * complete. A draft that was already saved (same id) is skipped, so a retry never
     * creates duplicates. Accepted plans that could now gain work are flagged.
     *
     * @return the tasks that were newly saved
     */
    public List<AcademicTask> confirmTasks(String courseId, List<TaskDraft> drafts)
            throws IOException {
        if (courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("Course ID cannot be empty.");
        }

        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("Select at least one task to save.");
        }

        Course course = repository.getCourse(courseId);
        List<String> problems = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < drafts.size(); i++) {
            TaskDraft draft = drafts.get(i);
            if (!ids.add(draft.getId())) {
                problems.add("Task " + (i + 1) + " ('" + draft.getTitle() + "') is listed twice.");
            } else if (!draft.isComplete()) {
                problems.add("Task " + (i + 1) + " ('" + draft.getTitle() + "') still needs: "
                        + String.join(", ", draft.getMissingFields()) + ".");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }

        List<AcademicTask> created = new ArrayList<>();
        for (TaskDraft draft : drafts) {
            AcademicTask existing = findTask(draft.getId());
            if (existing == null) {
                created.add(draft.toTask(course));
            } else if (!existing.getCourse().getId().equals(courseId)) {
                throw new IllegalArgumentException(
                        "Task '" + draft.getTitle() + "' already exists in another course.");
            }
        }

        List<StudyPlan> flagged = new ArrayList<>();
        LocalDateTime latestDeadline = created.stream().map(AcademicTask::getDeadline)
                .max(Comparator.naturalOrder()).orElse(null);
        if (latestDeadline != null) {
            for (StudyPlan plan : repository.getPlans()) {
                if (isLive(plan) && plan.isAccepted()
                        && plan.getWeekStartDate().atStartOfDay().isBefore(latestDeadline)) {
                    plan.markNeedsRevision();
                    flagged.add(plan);
                }
            }
        }

        repository.commit(draft -> {
            created.forEach(draft::saveTask);
            flagged.forEach(draft::savePlan);
        });
        return List.copyOf(created);
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
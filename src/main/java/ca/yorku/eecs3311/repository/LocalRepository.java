package ca.yorku.eecs3311.repository;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * JSON-file persistence. Every change is applied to a copy of the whole
 * repository state, written atomically, and only then adopted in memory, so
 * related updates either all succeed or all leave the previous state intact.
 * Callers needing several updates at once use {@link #commit(Consumer)}.
 */
public class LocalRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path storageFile;
    private State state = new State();

    public LocalRepository(String storageFile) throws IOException {
        this(Path.of(Objects.requireNonNull(storageFile, "Storage file path is required.")));
    }

    public LocalRepository(Path storageFile) throws IOException {
        if (storageFile == null) {
            throw new IllegalArgumentException("Storage file path is required.");
        }

        this.storageFile = storageFile.toAbsolutePath().normalize();
        load();
    }

    /**
     * Apply several changes as one unit. If the changes throw, or the file
     * cannot be written, nothing is changed in memory or on disk. The Draft
     * must not be used after this method returns.
     */
    public synchronized void commit(Consumer<Draft> changes) throws IOException {
        Objects.requireNonNull(changes, "Changes are required.");

        State working = state.copy();
        Draft draft = new Draft(working);
        try {
            changes.accept(draft);
            write(working);
        } finally {
            draft.close();
        }
        state = working;
    }

    // ----- Courses -----

    public synchronized void saveCourse(Course course) throws IOException {
        commit(draft -> draft.saveCourse(course));
    }

    public synchronized List<Course> getCourses() {
        List<Course> result = new ArrayList<>(state.courses.size());
        for (Course course : state.courses.values()) {
            result.add(copyCourse(course));
        }
        return List.copyOf(result);
    }

    public synchronized Course getCourse(String id) {
        Course course = state.courses.get(id);
        if (course == null) {
            throw new IllegalArgumentException("Course not found: " + id);
        }
        return copyCourse(course);
    }

    public synchronized void deleteCourse(String courseId) throws IOException {
        commit(draft -> draft.deleteCourse(courseId));
    }

    // ----- Tasks -----

    public synchronized void saveTask(AcademicTask task) throws IOException {
        commit(draft -> draft.saveTask(task));
    }

    public synchronized List<AcademicTask> getTasks() {
        List<AcademicTask> result = new ArrayList<>();
        for (AcademicTask task : state.tasks.values()) {
            result.add(copyTask(task, state.courses.get(task.getCourse().getId())));
        }
        return List.copyOf(result);
    }

    public synchronized List<AcademicTask> getTasks(String courseId) {
        List<AcademicTask> result = new ArrayList<>();
        for (AcademicTask task : state.tasks.values()) {
            if (task.getCourse().getId().equals(courseId)) {
                result.add(copyTask(task, state.courses.get(courseId)));
            }
        }
        return List.copyOf(result);
    }

    public synchronized AcademicTask getTask(String id) {
        AcademicTask task = state.tasks.get(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + id);
        }
        return copyTask(task, state.courses.get(task.getCourse().getId()));
    }

    public synchronized List<AcademicTask> getCompletedTasks(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("Task type cannot be empty.");
        }

        String normalizedType = taskType.strip();
        List<AcademicTask> result = new ArrayList<>();
        for (AcademicTask task : state.tasks.values()) {
            if (task.isCompleted() && task.getType().equals(normalizedType)) {
                result.add(copyTask(task, state.courses.get(task.getCourse().getId())));
            }
        }
        return List.copyOf(result);
    }

    // ----- Weekly periods -----

    public synchronized void savePeriod(WeeklyPeriod period) throws IOException {
        commit(draft -> draft.savePeriod(period));
    }

    public synchronized List<WeeklyPeriod> getPeriods() {
        List<WeeklyPeriod> result = new ArrayList<>();
        for (WeeklyPeriod period : state.periods.values()) {
            result.add(copyPeriod(period));
        }
        return List.copyOf(result);
    }

    public synchronized void deletePeriod(String periodId) throws IOException {
        commit(draft -> draft.deletePeriod(periodId));
    }

    // ----- Study plans and sessions -----

    public synchronized void savePlan(StudyPlan plan) throws IOException {
        commit(draft -> draft.savePlan(plan));
    }

    public synchronized List<StudyPlan> getPlans() {
        List<StudyPlan> result = new ArrayList<>();
        for (StudyPlan plan : state.plans.values()) {
            result.add(copyPlan(plan));
        }
        return List.copyOf(result);
    }

    public synchronized List<StudyPlan> getPlans(LocalDate weekStart) {
        if (weekStart == null) {
            throw new IllegalArgumentException("Week start date is required.");
        }

        List<StudyPlan> result = new ArrayList<>();
        for (StudyPlan plan : state.plans.values()) {
            if (plan.getWeekStartDate().equals(weekStart)) {
                result.add(copyPlan(plan));
            }
        }
        return List.copyOf(result);
    }

    public synchronized void saveSession(StudySession session) throws IOException {
        commit(draft -> draft.saveSession(session));
    }

    public synchronized List<StudySession> getSessionsForTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be empty.");
        }

        List<StudySession> result = new ArrayList<>();
        for (StudyPlan plan : state.plans.values()) {
            for (StudySession session : plan.getSessions()) {
                if (session.getTaskId().equals(taskId)) {
                    result.add(copySession(session));
                }
            }
        }
        return List.copyOf(result);
    }

    // ----- Transaction -----

    /** The changes of one commit, applied to a working copy of the repository. */
    public static final class Draft {
        private final State working;
        private boolean open = true;

        private Draft(State working) {
            this.working = working;
        }

        private void close() {
            open = false;
        }

        private void requireOpen() {
            if (!open) {
                throw new IllegalStateException("This transaction is already finished.");
            }
        }

        public void saveCourse(Course course) {
            requireOpen();
            Objects.requireNonNull(course, "Course is required.");

            Course copy = copyCourse(course);
            working.courses.put(copy.getId(), copy);
        }

        public void deleteCourse(String courseId) {
            requireOpen();
            if (courseId == null || courseId.isBlank()) {
                throw new IllegalArgumentException("Course ID cannot be empty.");
            }

            if (!working.courses.containsKey(courseId)) {
                throw new IllegalArgumentException("Course not found: " + courseId);
            }

            for (AcademicTask task : working.tasks.values()) {
                if (task.getCourse().getId().equals(courseId)) {
                    throw new IllegalArgumentException(
                            "Cannot delete course while tasks belong to it: " + courseId);
                }
            }

            // Also reject deletion when course-material storage is added and materials exist.
            working.courses.remove(courseId);
        }

        public void saveTask(AcademicTask task) {
            requireOpen();
            Objects.requireNonNull(task, "Task is required.");

            String courseId = task.getCourse().getId();
            Course repositoryCourse = working.courses.get(courseId);
            if (repositoryCourse == null) {
                throw new IllegalArgumentException(
                        "Cannot save task: course not found in repository: " + courseId);
            }

            AcademicTask copy = copyTask(task, repositoryCourse);
            working.tasks.put(copy.getId(), copy);
        }

        public void savePeriod(WeeklyPeriod period) {
            requireOpen();
            Objects.requireNonNull(period, "Period is required.");

            WeeklyPeriod copy = copyPeriod(period);
            working.periods.put(copy.getId(), copy);
        }

        public void deletePeriod(String periodId) {
            requireOpen();
            if (periodId == null || periodId.isBlank()) {
                throw new IllegalArgumentException("Period ID cannot be empty.");
            }

            if (working.periods.remove(periodId) == null) {
                throw new IllegalArgumentException("Period not found: " + periodId);
            }
        }

        /** Save a plan together with its sessions, replacing any plan with the same id. */
        public void savePlan(StudyPlan plan) {
            requireOpen();
            Objects.requireNonNull(plan, "Plan is required.");

            for (StudySession session : plan.getSessions()) {
                requireTaskExists(session);

                StudyPlan owner = working.planContainingSession(session.getId());
                if (owner != null && !owner.getId().equals(plan.getId())) {
                    throw new IllegalArgumentException(
                            "Session already belongs to another plan: " + session.getId());
                }
            }

            StudyPlan copy = copyPlan(plan);
            working.plans.put(copy.getId(), copy);
        }

        /** Update a session that already belongs to a saved plan. */
        public void saveSession(StudySession session) {
            requireOpen();
            Objects.requireNonNull(session, "Session is required.");

            StudyPlan owner = working.planContainingSession(session.getId());
            if (owner == null) {
                throw new IllegalArgumentException(
                        "Session not found in any saved plan: " + session.getId());
            }
            requireTaskExists(session);

            StudySession copy = copySession(session);
            List<StudySession> updated = new ArrayList<>();
            for (StudySession existing : owner.getSessions()) {
                updated.add(existing.getId().equals(copy.getId()) ? copy : existing);
            }

            StudyPlan rebuilt = new StudyPlan(owner.getId(), owner.getWeekStartDate(),
                    owner.isAccepted(), owner.needsRevision(),
                    owner.getPriorityExplanation(), owner.getUnscheduledMinutes(), updated);
            working.plans.put(rebuilt.getId(), rebuilt);
        }

        private void requireTaskExists(StudySession session) {
            if (!working.tasks.containsKey(session.getTaskId())) {
                throw new IllegalArgumentException(
                        "Cannot save session: task not found in repository: "
                                + session.getTaskId());
            }
        }
    }

    // ----- Internal state -----

    private static final class State {
        final Map<String, Course> courses = new LinkedHashMap<>();
        final Map<String, AcademicTask> tasks = new LinkedHashMap<>();
        final Map<String, WeeklyPeriod> periods = new LinkedHashMap<>();
        final Map<String, StudyPlan> plans = new LinkedHashMap<>();

        State copy() {
            State result = new State();
            for (Course course : courses.values()) {
                Course copy = copyCourse(course);
                result.courses.put(copy.getId(), copy);
            }
            for (AcademicTask task : tasks.values()) {
                AcademicTask copy = copyTask(task, result.courses.get(task.getCourse().getId()));
                result.tasks.put(copy.getId(), copy);
            }
            for (WeeklyPeriod period : periods.values()) {
                WeeklyPeriod copy = copyPeriod(period);
                result.periods.put(copy.getId(), copy);
            }
            for (StudyPlan plan : plans.values()) {
                StudyPlan copy = copyPlan(plan);
                result.plans.put(copy.getId(), copy);
            }
            return result;
        }

        StudyPlan planContainingSession(String sessionId) {
            for (StudyPlan plan : plans.values()) {
                for (StudySession session : plan.getSessions()) {
                    if (session.getId().equals(sessionId)) {
                        return plan;
                    }
                }
            }
            return null;
        }
    }

    // ----- Loading -----

    private void load() throws IOException {
        if (!Files.exists(storageFile)) {
            return;
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(Files.readString(storageFile, StandardCharsets.UTF_8));
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException(
                    "Repository file contains malformed JSON: " + storageFile, exception);
        }

        if (!parsed.isJsonObject()) {
            throw malformed("Expected a JSON object at the document root.");
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonArray courseRecords = requireArray(root, "courses");
        JsonArray taskRecords = requireArray(root, "tasks");
        // Files written before planning data existed have no periods or plans.
        JsonArray periodRecords = optionalArray(root, "periods");
        JsonArray planRecords = optionalArray(root, "plans");

        State loaded = new State();

        for (int i = 0; i < courseRecords.size(); i++) {
            JsonObject record = requireObject(courseRecords.get(i), "courses[" + i + "]");
            String id = requireString(record, "id", "courses[" + i + "]");
            String name = requireString(record, "name", "courses[" + i + "]");
            Course course = new Course(id, name);
            if (loaded.courses.putIfAbsent(course.getId(), course) != null) {
                throw malformed("Duplicate course ID: " + course.getId());
            }
        }

        for (int i = 0; i < taskRecords.size(); i++) {
            String location = "tasks[" + i + "]";
            JsonObject record = requireObject(taskRecords.get(i), location);
            String id = requireString(record, "id", location);
            String courseId = requireString(record, "courseId", location);
            Course course = loaded.courses.get(courseId);
            if (course == null) {
                throw malformed("Task " + id + " references missing course: " + courseId);
            }

            String title = requireString(record, "title", location);
            String type = requireString(record, "type", location);
            String deadlineText = requireString(record, "deadline", location);
            LocalDateTime deadline;
            try {
                deadline = LocalDateTime.parse(deadlineText);
            } catch (DateTimeParseException exception) {
                throw malformed("Invalid task deadline in " + location + ": " + deadlineText,
                        exception);
            }

            AcademicTask task = new AcademicTask(
                    id,
                    course,
                    title,
                    type,
                    deadline,
                    requireInt(record, "priority", location),
                    requireInt(record, "originalEstimatedMinutes", location),
                    requireInt(record, "acceptedEstimatedMinutes", location),
                    requireInt(record, "remainingMinutes", location),
                    requireBoolean(record, "completed", location),
                    requireBoolean(record, "archived", location));

            if (loaded.tasks.putIfAbsent(task.getId(), task) != null) {
                throw malformed("Duplicate task ID: " + task.getId());
            }
        }

        for (int i = 0; i < periodRecords.size(); i++) {
            WeeklyPeriod period = parsePeriod(
                    requireObject(periodRecords.get(i), "periods[" + i + "]"),
                    "periods[" + i + "]");
            if (loaded.periods.putIfAbsent(period.getId(), period) != null) {
                throw malformed("Duplicate period ID: " + period.getId());
            }
        }

        Set<String> sessionIds = new HashSet<>();
        for (int i = 0; i < planRecords.size(); i++) {
            StudyPlan plan = parsePlan(
                    requireObject(planRecords.get(i), "plans[" + i + "]"),
                    "plans[" + i + "]", loaded.tasks, sessionIds);
            if (loaded.plans.putIfAbsent(plan.getId(), plan) != null) {
                throw malformed("Duplicate plan ID: " + plan.getId());
            }
        }

        state = loaded;
    }

    private static WeeklyPeriod parsePeriod(JsonObject record, String location) {
        String id = requireString(record, "id", location);
        DayOfWeek day = parseValue(requireString(record, "dayOfWeek", location),
                DayOfWeek::valueOf, "dayOfWeek", location);
        LocalTime start = parseValue(requireString(record, "startTime", location),
                LocalTime::parse, "startTime", location);
        LocalTime end = parseValue(requireString(record, "endTime", location),
                LocalTime::parse, "endTime", location);
        String type = requireString(record, "type", location);

        try {
            return new WeeklyPeriod(id, day, start, end, type);
        } catch (IllegalArgumentException exception) {
            throw malformed("Invalid period at " + location + ": " + exception.getMessage(),
                    exception);
        }
    }

    private static StudyPlan parsePlan(JsonObject record, String location,
                                       Map<String, AcademicTask> tasks,
                                       Set<String> sessionIds) {
        String id = requireString(record, "id", location);
        LocalDate weekStart = parseValue(requireString(record, "weekStartDate", location),
                LocalDate::parse, "weekStartDate", location);
        boolean accepted = requireBoolean(record, "accepted", location);
        boolean needsRevision = requireBoolean(record, "needsRevision", location);
        String explanation = requireString(record, "priorityExplanation", location);

        JsonObject unscheduledRecord = requireObjectProperty(
                record, "unscheduledMinutes", location);
        Map<String, Integer> unscheduled = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : unscheduledRecord.entrySet()) {
            unscheduled.put(entry.getKey(), intValue(entry.getValue(),
                    "unscheduledMinutes." + entry.getKey(), location));
        }

        JsonArray sessionRecords = requireArray(record, "sessions");
        List<StudySession> sessions = new ArrayList<>();
        for (int i = 0; i < sessionRecords.size(); i++) {
            String sessionLocation = location + ".sessions[" + i + "]";
            JsonObject sessionRecord = requireObject(sessionRecords.get(i), sessionLocation);
            StudySession session = parseSession(sessionRecord, sessionLocation);
            if (!tasks.containsKey(session.getTaskId())) {
                throw malformed("Session " + session.getId()
                        + " references missing task: " + session.getTaskId());
            }
            if (!sessionIds.add(session.getId())) {
                throw malformed("Duplicate session ID: " + session.getId());
            }
            sessions.add(session);
        }

        try {
            return new StudyPlan(id, weekStart, accepted, needsRevision,
                    explanation, unscheduled, sessions);
        } catch (IllegalArgumentException exception) {
            throw malformed("Invalid plan at " + location + ": " + exception.getMessage(),
                    exception);
        }
    }

    private static StudySession parseSession(JsonObject record, String location) {
        String id = requireString(record, "id", location);
        String taskId = requireString(record, "taskId", location);
        LocalDateTime start = parseValue(requireString(record, "startDateTime", location),
                LocalDateTime::parse, "startDateTime", location);
        LocalDateTime end = parseValue(requireString(record, "endDateTime", location),
                LocalDateTime::parse, "endDateTime", location);
        String status = requireString(record, "status", location);
        int actualMinutes = requireInt(record, "actualMinutes", location);

        try {
            return new StudySession(id, taskId, start, end, status, actualMinutes);
        } catch (IllegalArgumentException exception) {
            throw malformed("Invalid session at " + location + ": " + exception.getMessage(),
                    exception);
        }
    }

    // ----- Writing -----

    private void write(State toWrite) throws IOException {
        Path parent = storageFile.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(
                parent, storageFile.getFileName().toString() + ".", ".tmp");

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporaryFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                GSON.toJson(toJson(toWrite), writer);
            }

            Files.move(temporaryFile, storageFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            if (exception instanceof AtomicMoveNotSupportedException) {
                throw new IOException(
                        "Atomic replacement is not supported for repository file: "
                                + storageFile,
                        exception);
            }
            throw exception;
        }
    }

    private static JsonObject toJson(State toWrite) {
        JsonObject root = new JsonObject();
        JsonArray courseRecords = new JsonArray();
        for (Course course : toWrite.courses.values()) {
            JsonObject record = new JsonObject();
            record.addProperty("id", course.getId());
            record.addProperty("name", course.getName());
            courseRecords.add(record);
        }

        JsonArray taskRecords = new JsonArray();
        for (AcademicTask task : toWrite.tasks.values()) {
            JsonObject record = new JsonObject();
            record.addProperty("id", task.getId());
            record.addProperty("courseId", task.getCourse().getId());
            record.addProperty("title", task.getTitle());
            record.addProperty("type", task.getType());
            record.addProperty("deadline", task.getDeadline().toString());
            record.addProperty("priority", task.getPriority());
            record.addProperty("originalEstimatedMinutes",
                    task.getOriginalEstimatedMinutes());
            record.addProperty("acceptedEstimatedMinutes",
                    task.getAcceptedEstimatedMinutes());
            record.addProperty("remainingMinutes", task.getRemainingMinutes());
            record.addProperty("completed", task.isCompleted());
            record.addProperty("archived", task.isArchived());
            taskRecords.add(record);
        }

        JsonArray periodRecords = new JsonArray();
        for (WeeklyPeriod period : toWrite.periods.values()) {
            JsonObject record = new JsonObject();
            record.addProperty("id", period.getId());
            record.addProperty("dayOfWeek", period.getDayOfWeek().name());
            record.addProperty("startTime", period.getStartTime().toString());
            record.addProperty("endTime", period.getEndTime().toString());
            record.addProperty("type", period.getType());
            periodRecords.add(record);
        }

        JsonArray planRecords = new JsonArray();
        for (StudyPlan plan : toWrite.plans.values()) {
            JsonObject record = new JsonObject();
            record.addProperty("id", plan.getId());
            record.addProperty("weekStartDate", plan.getWeekStartDate().toString());
            record.addProperty("accepted", plan.isAccepted());
            record.addProperty("needsRevision", plan.needsRevision());
            record.addProperty("priorityExplanation", plan.getPriorityExplanation());

            JsonObject unscheduledRecord = new JsonObject();
            for (Map.Entry<String, Integer> entry : plan.getUnscheduledMinutes().entrySet()) {
                unscheduledRecord.addProperty(entry.getKey(), entry.getValue());
            }
            record.add("unscheduledMinutes", unscheduledRecord);

            JsonArray sessionRecords = new JsonArray();
            for (StudySession session : plan.getSessions()) {
                JsonObject sessionRecord = new JsonObject();
                sessionRecord.addProperty("id", session.getId());
                sessionRecord.addProperty("taskId", session.getTaskId());
                sessionRecord.addProperty("startDateTime", session.getStartDateTime().toString());
                sessionRecord.addProperty("endDateTime", session.getEndDateTime().toString());
                sessionRecord.addProperty("status", session.getStatus());
                sessionRecord.addProperty("actualMinutes", session.getActualMinutes());
                sessionRecords.add(sessionRecord);
            }
            record.add("sessions", sessionRecords);
            planRecords.add(record);
        }

        root.add("courses", courseRecords);
        root.add("tasks", taskRecords);
        root.add("periods", periodRecords);
        root.add("plans", planRecords);
        return root;
    }

    // ----- JSON helpers -----

    private static JsonArray requireArray(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonArray()) {
            throw malformed("Expected array property: " + property);
        }
        return value.getAsJsonArray();
    }

    private static JsonArray optionalArray(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull()) {
            return new JsonArray();
        }
        if (!value.isJsonArray()) {
            throw malformed("Expected array property: " + property);
        }
        return value.getAsJsonArray();
    }

    private static JsonObject requireObject(JsonElement element, String location) {
        if (element == null || !element.isJsonObject()) {
            throw malformed("Expected object at " + location);
        }
        return element.getAsJsonObject();
    }

    private static JsonObject requireObjectProperty(JsonObject object, String property,
                                                    String location) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonObject()) {
            throw malformed("Expected object property " + property + " at " + location);
        }
        return value.getAsJsonObject();
    }

    private static String requireString(JsonObject object, String property,
                                        String location) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw malformed("Expected string property " + property + " at " + location);
        }
        return value.getAsString();
    }

    private static int requireInt(JsonObject object, String property,
                                  String location) {
        return intValue(object.get(property), property, location);
    }

    private static int intValue(JsonElement value, String property, String location) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw malformed("Expected integer property " + property + " at " + location);
        }

        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw malformed("Invalid integer property " + property + " at " + location,
                    exception);
        }
    }

    private static boolean requireBoolean(JsonObject object, String property,
                                          String location) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw malformed("Expected boolean property " + property + " at " + location);
        }
        return value.getAsBoolean();
    }

    private static <T> T parseValue(String text, Function<String, T> parser,
                                    String property, String location) {
        try {
            return parser.apply(text);
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw malformed("Invalid " + property + " at " + location + ": " + text,
                    exception);
        }
    }

    private static IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException(
                "Invalid repository data: " + message);
    }

    private static IllegalArgumentException malformed(String message, Throwable cause) {
        return new IllegalArgumentException(
                "Invalid repository data: " + message, cause);
    }

    // ----- Copies (stored objects never leave the repository) -----

    private static Course copyCourse(Course course) {
        return new Course(course.getId(), course.getName());
    }

    private static AcademicTask copyTask(AcademicTask task, Course course) {
        return new AcademicTask(
                task.getId(),
                copyCourse(course),
                task.getTitle(),
                task.getType(),
                task.getDeadline(),
                task.getPriority(),
                task.getOriginalEstimatedMinutes(),
                task.getAcceptedEstimatedMinutes(),
                task.getRemainingMinutes(),
                task.isCompleted(),
                task.isArchived());
    }

    private static WeeklyPeriod copyPeriod(WeeklyPeriod period) {
        return new WeeklyPeriod(period.getId(), period.getDayOfWeek(),
                period.getStartTime(), period.getEndTime(), period.getType());
    }

    private static StudySession copySession(StudySession session) {
        return new StudySession(session.getId(), session.getTaskId(),
                session.getStartDateTime(), session.getEndDateTime(),
                session.getStatus(), session.getActualMinutes());
    }

    private static StudyPlan copyPlan(StudyPlan plan) {
        List<StudySession> sessions = new ArrayList<>();
        for (StudySession session : plan.getSessions()) {
            sessions.add(copySession(session));
        }

        return new StudyPlan(plan.getId(), plan.getWeekStartDate(), plan.isAccepted(),
                plan.needsRevision(), plan.getPriorityExplanation(),
                plan.getUnscheduledMinutes(), sessions);
    }
}
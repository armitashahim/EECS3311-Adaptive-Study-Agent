package ca.yorku.eecs3311.repository;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LocalRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path storageFile;
    private final Map<String, Course> courses = new LinkedHashMap<>();
    private final Map<String, AcademicTask> tasks = new LinkedHashMap<>();

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

    public synchronized void saveCourse(Course course) throws IOException {
        Objects.requireNonNull(course, "Course is required.");

        Map<String, Course> updatedCourses = copyCourses(courses);
        Course copy = copyCourse(course);
        updatedCourses.put(copy.getId(), copy);

        write(updatedCourses, tasks);
        courses.clear();
        courses.putAll(updatedCourses);
    }

    public synchronized List<Course> getCourses() {
        List<Course> result = new ArrayList<>(courses.size());
        for (Course course : courses.values()) {
            result.add(copyCourse(course));
        }
        return List.copyOf(result);
    }

    public synchronized Course getCourse(String id) {
        Course course = courses.get(id);
        if (course == null) {
            throw new IllegalArgumentException("Course not found: " + id);
        }
        return copyCourse(course);
    }

    public synchronized void deleteCourse(String courseId) throws IOException {
        if (courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("Course ID cannot be empty.");
        }

        if (!courses.containsKey(courseId)) {
            throw new IllegalArgumentException("Course not found: " + courseId);
        }

        for (AcademicTask task : tasks.values()) {
            if (task.getCourse().getId().equals(courseId)) {
                throw new IllegalArgumentException(
                        "Cannot delete course while tasks belong to it: " + courseId);
            }
        }

        // Also reject deletion when course-material storage is added and materials exist.
        Map<String, Course> updatedCourses = copyCourses(courses);
        updatedCourses.remove(courseId);

        write(updatedCourses, tasks);
        courses.clear();
        courses.putAll(updatedCourses);
    }

    public synchronized void saveTask(AcademicTask task) throws IOException {
        Objects.requireNonNull(task, "Task is required.");

        String courseId = task.getCourse().getId();
        Course repositoryCourse = courses.get(courseId);
        if (repositoryCourse == null) {
            throw new IllegalArgumentException(
                    "Cannot save task: course not found in repository: " + courseId);
        }

        Map<String, AcademicTask> updatedTasks = copyTasks(tasks, courses);
        AcademicTask copy = copyTask(task, repositoryCourse);
        updatedTasks.put(copy.getId(), copy);

        write(courses, updatedTasks);
        tasks.clear();
        tasks.putAll(updatedTasks);
    }

    public synchronized List<AcademicTask> getTasks() {
        return copyTaskList(tasks.values(), courses);
    }

    public synchronized List<AcademicTask> getTasks(String courseId) {
        List<AcademicTask> result = new ArrayList<>();
        for (AcademicTask task : tasks.values()) {
            if (task.getCourse().getId().equals(courseId)) {
                result.add(copyTask(task, courses.get(courseId)));
            }
        }
        return List.copyOf(result);
    }

    public synchronized AcademicTask getTask(String id) {
        AcademicTask task = tasks.get(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + id);
        }
        return copyTask(task, courses.get(task.getCourse().getId()));
    }

    public synchronized List<AcademicTask> getCompletedTasks(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("Task type cannot be empty.");
        }

        String normalizedType = taskType.strip();
        List<AcademicTask> result = new ArrayList<>();
        for (AcademicTask task : tasks.values()) {
            if (task.isCompleted() && task.getType().equals(normalizedType)) {
                result.add(copyTask(task, courses.get(task.getCourse().getId())));
            }
        }
        return List.copyOf(result);
    }

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

        Map<String, Course> loadedCourses = new LinkedHashMap<>();
        for (int i = 0; i < courseRecords.size(); i++) {
            JsonObject record = requireObject(courseRecords.get(i), "courses[" + i + "]");
            String id = requireString(record, "id", "courses[" + i + "]");
            String name = requireString(record, "name", "courses[" + i + "]");
            Course course = new Course(id, name);
            if (loadedCourses.putIfAbsent(course.getId(), course) != null) {
                throw malformed("Duplicate course ID: " + course.getId());
            }
        }

        Map<String, AcademicTask> loadedTasks = new LinkedHashMap<>();
        for (int i = 0; i < taskRecords.size(); i++) {
            String location = "tasks[" + i + "]";
            JsonObject record = requireObject(taskRecords.get(i), location);
            String id = requireString(record, "id", location);
            String courseId = requireString(record, "courseId", location);
            Course course = loadedCourses.get(courseId);
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

            if (loadedTasks.putIfAbsent(task.getId(), task) != null) {
                throw malformed("Duplicate task ID: " + task.getId());
            }
        }

        courses.putAll(loadedCourses);
        tasks.putAll(loadedTasks);
    }

    private void write(Map<String, Course> coursesToWrite,
                       Map<String, AcademicTask> tasksToWrite) throws IOException {
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
                GSON.toJson(toJson(coursesToWrite, tasksToWrite), writer);
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

    private static JsonObject toJson(Map<String, Course> coursesToWrite,
                                     Map<String, AcademicTask> tasksToWrite) {
        JsonObject root = new JsonObject();
        JsonArray courseRecords = new JsonArray();
        for (Course course : coursesToWrite.values()) {
            JsonObject record = new JsonObject();
            record.addProperty("id", course.getId());
            record.addProperty("name", course.getName());
            courseRecords.add(record);
        }

        JsonArray taskRecords = new JsonArray();
        for (AcademicTask task : tasksToWrite.values()) {
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

        root.add("courses", courseRecords);
        root.add("tasks", taskRecords);
        return root;
    }

    private static JsonArray requireArray(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || !value.isJsonArray()) {
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
        JsonElement value = object.get(property);
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

    private static IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException(
                "Invalid repository data: " + message);
    }

    private static IllegalArgumentException malformed(String message, Throwable cause) {
        return new IllegalArgumentException(
                "Invalid repository data: " + message, cause);
    }

    private static Course copyCourse(Course course) {
        return new Course(course.getId(), course.getName());
    }

    private static Map<String, Course> copyCourses(Map<String, Course> source) {
        Map<String, Course> result = new LinkedHashMap<>();
        for (Course course : source.values()) {
            Course copy = copyCourse(course);
            result.put(copy.getId(), copy);
        }
        return result;
    }

    private static Map<String, AcademicTask> copyTasks(
            Map<String, AcademicTask> source, Map<String, Course> currentCourses) {
        Map<String, AcademicTask> result = new LinkedHashMap<>();
        for (AcademicTask task : source.values()) {
            Course course = currentCourses.get(task.getCourse().getId());
            AcademicTask copy = copyTask(task, course);
            result.put(copy.getId(), copy);
        }
        return result;
    }

    private static List<AcademicTask> copyTaskList(
            Iterable<AcademicTask> source, Map<String, Course> currentCourses) {
        List<AcademicTask> result = new ArrayList<>();
        for (AcademicTask task : source) {
            result.add(copyTask(task, currentCourses.get(task.getCourse().getId())));
        }
        return List.copyOf(result);
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
}

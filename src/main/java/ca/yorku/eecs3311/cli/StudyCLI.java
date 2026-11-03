package ca.yorku.eecs3311.cli;

import ca.yorku.eecs3311.agent.AgentException;
import ca.yorku.eecs3311.ai.DeepSeekClient;
import ca.yorku.eecs3311.application.DurationSuggestion;
import ca.yorku.eecs3311.application.PlanProposal;
import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.config.AppConfig;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command-line view over {@link StudyApplication}. It only parses input and prints
 * results; every rule lives in the facade, so the GUI and CLI behave the same way.
 * Items are referred to by the numbers shown in the most recent listing order.
 */
public class StudyCLI {
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final StudyApplication application;
    private final BufferedReader input;
    private final PrintStream output;
    private final Clock clock;
    private PlanProposal pendingProposal;

    public StudyCLI(StudyApplication application, InputStream input, PrintStream output,
                    Clock clock) {
        if (application == null || input == null || output == null || clock == null) {
            throw new IllegalArgumentException("CLI dependencies cannot be null.");
        }
        this.application = application;
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
        this.clock = clock;
    }

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : "data/repository.json");
        LocalRepository repository = new LocalRepository(file);
        AppConfig config = AppConfig.load();
        StudyApplication application = new StudyApplication(repository, Clock.systemDefaultZone(),
                config.hasApiKey() ? DeepSeekClient.fromConfig(config) : null);
        new StudyCLI(application, System.in, System.out, Clock.systemDefaultZone()).run();
    }

    /** Reads commands until "exit" or the end of the input. */
    public void run() throws IOException {
        output.println("Adaptive Study Agent (type 'help' for commands, 'exit' to quit)");
        while (true) {
            output.print("> ");
            output.flush();
            String line = input.readLine();
            if (line == null) {
                output.println();
                return;
            }
            if (line.isBlank()) {
                continue;
            }
            if (!execute(line)) {
                return;
            }
        }
    }

    /** Runs one command line. Returns false when the user asked to exit. */
    boolean execute(String line) {
        List<String> words;
        try {
            words = tokenize(line);
        } catch (IllegalArgumentException e) {
            output.println("Error: " + e.getMessage());
            return true;
        }
        if (words.isEmpty()) {
            return true;
        }
        String command = words.get(0).toLowerCase(Locale.ROOT);
        List<String> args = words.subList(1, words.size());
        try {
            switch (command) {
                case "exit", "quit" -> {
                    output.println("Goodbye.");
                    return false;
                }
                case "help" -> printHelp();
                case "courses" -> listCourses();
                case "course-add" -> addCourse(args);
                case "course-rename" -> renameCourse(args);
                case "course-remove" -> removeCourse(args);
                case "tasks" -> listTasks(args);
                case "task-add" -> addTask(args);
                case "task-edit" -> editTask(args);
                case "task-remove" -> removeTask(args);
                case "periods" -> listPeriods();
                case "period-add" -> addPeriod(args);
                case "period-remove" -> removePeriod(args);
                case "plan" -> showPlan(args);
                case "plan-generate" -> generatePlan(args);
                case "plan-revise" -> revisePlan(args);
                case "plan-accept" -> acceptPlan();
                case "plan-reject" -> rejectPlan();
                case "record" -> recordProgress(args);
                case "duration" -> showDuration(args);
                case "duration-accept" -> acceptDuration(args);
                default -> output.println("Unknown command '" + command
                        + "'. Type 'help' to see the commands.");
            }
        } catch (IllegalArgumentException | IllegalStateException | DateTimeParseException e) {
            output.println("Error: " + e.getMessage());
        } catch (AgentException e) {
            output.println("The AI assistant could not produce a plan: " + e.getMessage());
            output.println("Nothing was changed.");
        } catch (IOException e) {
            output.println("Error: could not save your data (" + e.getMessage() + ")");
        }
        return true;
    }

    // ===== Commands =====

    private void printHelp() {
        output.println("""
                Courses (F01)
                  courses                              list courses
                  course-add NAME                      add a course
                  course-rename N NAME                 rename course N
                  course-remove N                      delete course N (only if it never had tasks)
                Tasks (F01)
                  tasks N                              list tasks of course N
                  task-add N TITLE TYPE YYYY-MM-DD HH:mm PRIORITY MINUTES
                  task-edit N T TITLE TYPE YYYY-MM-DD HH:mm PRIORITY
                  task-remove N T                      archive task T of course N
                Availability and commitments (F02)
                  periods                              list weekly periods
                  period-add availability|commitment DAY HH:mm HH:mm
                  period-remove N
                Study plan (F04, F05) - uses the AI assistant
                  plan [YYYY-MM-DD]                    show the saved plan for that week (default: this week)
                  plan-generate [YYYY-MM-DD]           propose a new plan for the week
                  plan-revise [YYYY-MM-DD]             propose a revision of the week's plan
                  plan-accept                          save the proposal shown
                  plan-reject                          discard the proposal shown
                Progress (F06)
                  record DATE S STATUS MINUTES REMAINING [finished]
                                                       STATUS: completed | partial | missed
                Duration estimates (F07)
                  duration N T                         show the suggested duration for task T
                  duration-accept N T MINUTES          accept a duration for task T
                Use "double quotes" around values that contain spaces.
                  help, exit""");
    }

    private void listCourses() {
        List<Course> courses = application.getCourses();
        if (courses.isEmpty()) {
            output.println("No courses yet. Add one with: course-add NAME");
            return;
        }
        for (int i = 0; i < courses.size(); i++) {
            output.printf("%d. %s%n", i + 1, courses.get(i).getName());
        }
    }

    private void addCourse(List<String> args) throws IOException {
        requireArgs(args, 1, "course-add NAME");
        application.saveCourse(new Course(String.join(" ", args)));
        output.println("Course added.");
    }

    private void renameCourse(List<String> args) throws IOException {
        requireArgs(args, 2, "course-rename N NAME");
        Course course = courseAt(args.get(0));
        course.rename(String.join(" ", args.subList(1, args.size())));
        application.saveCourse(course);
        output.println("Course renamed.");
    }

    private void removeCourse(List<String> args) throws IOException {
        requireArgs(args, 1, "course-remove N");
        Course course = courseAt(args.get(0));
        application.removeCourse(course.getId());
        output.println("Course deleted.");
    }

    private void listTasks(List<String> args) {
        requireArgs(args, 1, "tasks N");
        Course course = courseAt(args.get(0));
        List<AcademicTask> tasks = application.getTasks(course.getId());
        if (tasks.isEmpty()) {
            output.println("No tasks in " + course.getName() + ".");
            return;
        }
        for (int i = 0; i < tasks.size(); i++) {
            AcademicTask task = tasks.get(i);
            output.printf("%d. %s [%s] due %s, priority %d, estimate %d min, remaining %d min%s%n",
                    i + 1, task.getTitle(), task.getType(),
                    task.getDeadline().format(DATE_TIME), task.getPriority(),
                    task.getAcceptedEstimatedMinutes(), task.getRemainingMinutes(),
                    task.isCompleted() ? " (completed)" : "");
        }
    }

    private void addTask(List<String> args) throws IOException {
        requireArgs(args, 7, "task-add N TITLE TYPE YYYY-MM-DD HH:mm PRIORITY MINUTES");
        Course course = courseAt(args.get(0));
        AcademicTask task = new AcademicTask(course, args.get(1), args.get(2),
                parseDateTime(args.get(3), args.get(4)),
                parseNumber(args.get(5), "priority"), parseNumber(args.get(6), "minutes"));
        application.saveTask(course.getId(), task);
        output.println("Task added.");
    }

    private void editTask(List<String> args) throws IOException {
        requireArgs(args, 7, "task-edit N T TITLE TYPE YYYY-MM-DD HH:mm PRIORITY");
        Course course = courseAt(args.get(0));
        AcademicTask task = taskAt(course, args.get(1));
        task.updateDetails(args.get(2), args.get(3), parseDateTime(args.get(4), args.get(5)),
                parseNumber(args.get(6), "priority"));
        application.saveTask(course.getId(), task);
        output.println("Task updated.");
    }

    private void removeTask(List<String> args) throws IOException {
        requireArgs(args, 2, "task-remove N T");
        AcademicTask task = taskAt(courseAt(args.get(0)), args.get(1));
        application.removeTask(task.getId());
        output.println("Task removed. Its future sessions were dropped and past records kept.");
    }

    private void listPeriods() {
        List<WeeklyPeriod> periods = sortedPeriods();
        if (periods.isEmpty()) {
            output.println("No weekly periods yet. Add one with: period-add");
            return;
        }
        for (int i = 0; i < periods.size(); i++) {
            WeeklyPeriod period = periods.get(i);
            output.printf("%d. %s %s %s-%s%n", i + 1, period.getType(), period.getDayOfWeek(),
                    period.getStartTime().format(TIME), period.getEndTime().format(TIME));
        }
    }

    private void addPeriod(List<String> args) throws IOException {
        requireArgs(args, 4, "period-add availability|commitment DAY HH:mm HH:mm");
        String type = args.get(0).toLowerCase(Locale.ROOT);
        application.savePeriod(new WeeklyPeriod(parseDay(args.get(1)),
                parseTime(args.get(2)), parseTime(args.get(3)), type));
        output.println("Period added.");
    }

    private void removePeriod(List<String> args) throws IOException {
        requireArgs(args, 1, "period-remove N");
        List<WeeklyPeriod> periods = sortedPeriods();
        WeeklyPeriod period = pick(periods, args.get(0), "period");
        application.removePeriod(period.getId());
        output.println("Period removed.");
    }

    private void showPlan(List<String> args) {
        LocalDate week = weekStart(args.isEmpty() ? null : args.get(0));
        List<StudyPlan> plans = application.getPlans(week);
        if (plans.isEmpty()) {
            output.println("No plan for the week of " + week + ".");
            return;
        }
        printPlans(plans);
    }

    private void generatePlan(List<String> args) throws AgentException {
        LocalDate week = weekStart(args.isEmpty() ? null : args.get(0));
        output.println("Asking the AI assistant to plan the week of " + week + "...");
        showProposal(application.generatePlan(week));
    }

    private void revisePlan(List<String> args) throws AgentException {
        LocalDate week = weekStart(args.isEmpty() ? null : args.get(0));
        output.println("Asking the AI assistant to revise the week of " + week + "...");
        showProposal(application.revisePlan(week));
    }

    private void showProposal(PlanProposal proposal) {
        pendingProposal = proposal;
        output.println(proposal.revision() ? "Proposed revision (not saved yet):"
                : "Proposed plan (not saved yet):");
        printPlans(List.of(proposal.plan()));
        output.println("Type plan-accept to save it or plan-reject to discard it.");
    }

    private void acceptPlan() throws IOException {
        if (pendingProposal == null) {
            throw new IllegalStateException(
                    "There is no proposal to accept. Use plan-generate or plan-revise first.");
        }
        PlanProposal proposal = pendingProposal;
        pendingProposal = null;
        try {
            application.acceptPlan(proposal);
        } catch (IOException e) {
            pendingProposal = proposal;
            throw e;
        }
        output.println("Plan saved.");
    }

    private void rejectPlan() {
        if (pendingProposal == null) {
            throw new IllegalStateException("There is no proposal to discard.");
        }
        pendingProposal = null;
        output.println("Proposal discarded. Your saved plan was not changed.");
    }

    private void printPlans(List<StudyPlan> plans) {
        Map<String, String> titles = taskTitles();
        for (StudyPlan plan : plans) {
            output.println("Week of " + plan.getWeekStartDate()
                    + (plan.isAccepted() ? " (accepted)" : " (proposed)")
                    + (plan.needsRevision() ? " - NEEDS REVISION" : ""));
            if (plan.getPriorityExplanation() != null
                    && !plan.getPriorityExplanation().isBlank()) {
                output.println("Why: " + plan.getPriorityExplanation());
            }
        }
        List<StudySession> sessions = sessionsOf(plans);
        for (int i = 0; i < sessions.size(); i++) {
            StudySession session = sessions.get(i);
            output.printf("%d. %s  %s to %s  [%s%s]%n", i + 1,
                    titles.getOrDefault(session.getTaskId(), "(removed task)"),
                    session.getStartDateTime().format(DATE_TIME),
                    session.getEndDateTime().format(TIME), session.getStatus(),
                    StudySession.SCHEDULED.equals(session.getStatus())
                            ? "" : ", " + session.getActualMinutes() + " min");
        }
        for (StudyPlan plan : plans) {
            plan.getUnscheduledMinutes().forEach((taskId, minutes) -> output.printf(
                    "Unscheduled: %s - %d min%n",
                    titles.getOrDefault(taskId, "(removed task)"), minutes));
        }
    }

    private void recordProgress(List<String> args) throws IOException {
        requireArgs(args, 5, "record DATE S STATUS MINUTES REMAINING [finished]");
        LocalDate week = weekStart(args.get(0));
        List<StudySession> sessions = sessionsOf(application.getPlans(week));
        StudySession session = pick(sessions, args.get(1), "session");
        boolean finished = args.size() > 5 && args.get(5).equalsIgnoreCase("finished");
        int remaining = finished ? 0 : parseNumber(args.get(4), "remaining minutes");
        application.recordProgress(session, args.get(2).toLowerCase(Locale.ROOT),
                parseNumber(args.get(3), "minutes"), finished, remaining);
        output.println("Progress recorded.");
    }

    private void showDuration(List<String> args) {
        requireArgs(args, 2, "duration N T");
        AcademicTask task = taskAt(courseAt(args.get(0)), args.get(1));
        DurationSuggestion suggestion = application.getDurationSuggestion(task);
        if (suggestion.usedHistory()) {
            output.printf("Suggested: %d min, based on %d finished tasks of type '%s'.%n",
                    suggestion.minutes(), suggestion.supportingTasks(), task.getType());
        } else {
            output.printf("Suggested: %d min (your original estimate; only %d finished "
                    + "'%s' tasks so far, 3 are needed to learn from history).%n",
                    suggestion.minutes(), suggestion.supportingTasks(), task.getType());
        }
    }

    private void acceptDuration(List<String> args) throws IOException {
        requireArgs(args, 3, "duration-accept N T MINUTES");
        AcademicTask task = taskAt(courseAt(args.get(0)), args.get(1));
        application.acceptDuration(task, parseNumber(args.get(2), "minutes"));
        output.println("Duration accepted.");
    }

    // ===== Lookups and parsing =====

    private Course courseAt(String number) {
        return pick(application.getCourses(), number, "course");
    }

    private AcademicTask taskAt(Course course, String number) {
        return pick(application.getTasks(course.getId()), number, "task");
    }

    private static <T> T pick(List<T> items, String number, String what) {
        int index = parseNumber(number, what + " number");
        if (index < 1 || index > items.size()) {
            throw new IllegalArgumentException("There is no " + what + " number " + index
                    + ". Use the numbers from the list.");
        }
        return items.get(index - 1);
    }

    private List<WeeklyPeriod> sortedPeriods() {
        List<WeeklyPeriod> periods = new ArrayList<>(application.getPeriods());
        periods.sort(Comparator.comparing(WeeklyPeriod::getDayOfWeek)
                .thenComparing(WeeklyPeriod::getStartTime));
        return periods;
    }

    private List<StudySession> sessionsOf(List<StudyPlan> plans) {
        List<StudySession> sessions = new ArrayList<>();
        plans.forEach(plan -> sessions.addAll(plan.getSessions()));
        sessions.sort(Comparator.comparing(StudySession::getStartDateTime));
        return sessions;
    }

    private Map<String, String> taskTitles() {
        Map<String, String> titles = new HashMap<>();
        for (Course course : application.getCourses()) {
            for (AcademicTask task : application.getTasks(course.getId())) {
                titles.put(task.getId(), task.getTitle());
            }
        }
        return titles;
    }

    private LocalDate weekStart(String text) {
        LocalDate date = text == null ? LocalDate.now(clock) : LocalDate.parse(text);
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static void requireArgs(List<String> args, int minimum, String usage) {
        if (args.size() < minimum) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static int parseNumber(String text, String what) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid " + what + ".");
        }
    }

    private static LocalDateTime parseDateTime(String date, String time) {
        try {
            return LocalDateTime.parse(date + " " + time, DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Use the date and time format YYYY-MM-DD HH:mm (for example 2030-05-30 23:59).");
        }
    }

    private static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text, TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Use the time format HH:mm (for example 09:30).");
        }
    }

    private static DayOfWeek parseDay(String text) {
        String prefix = text.toLowerCase(Locale.ROOT);
        if (prefix.length() >= 3) {
            for (DayOfWeek day : DayOfWeek.values()) {
                if (day.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    return day;
                }
            }
        }
        throw new IllegalArgumentException("'" + text + "' is not a day of the week.");
    }

    /** Splits a line into words; text inside double quotes stays together. */
    static List<String> tokenize(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasWord = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                hasWord = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (hasWord) {
                    words.add(current.toString());
                    current.setLength(0);
                    hasWord = false;
                }
            } else {
                current.append(c);
                hasWord = true;
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("A quotation mark was opened but never closed.");
        }
        if (hasWord) {
            words.add(current.toString());
        }
        return words;
    }
}
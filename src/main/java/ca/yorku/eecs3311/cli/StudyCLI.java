package ca.yorku.eecs3311.cli;

import ca.yorku.eecs3311.agent.AgentException;
import ca.yorku.eecs3311.ai.DeepSeekClient;
import ca.yorku.eecs3311.application.DurationSuggestion;
import ca.yorku.eecs3311.application.PlanProposal;
import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.config.AppConfig;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.material.PdfImportException;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.model.Quiz;
import ca.yorku.eecs3311.model.QuizQuestion;
import ca.yorku.eecs3311.quiz.QuizException;
import ca.yorku.eecs3311.review.ReviewException;
import ca.yorku.eecs3311.review.ReviewRecommendation;
import ca.yorku.eecs3311.review.TopicStats;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import ca.yorku.eecs3311.repository.LocalRepository;
import ca.yorku.eecs3311.syllabus.SyllabusException;
import ca.yorku.eecs3311.syllabus.TaskDraft;
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
    private static final int MATERIAL_PREVIEW_CHARACTERS = 800;

    private final StudyApplication application;
    private final BufferedReader input;
    private final PrintStream output;
    private final Clock clock;
    private PlanProposal pendingProposal;
    private List<TaskDraft> pendingDrafts = new ArrayList<>();
    private Quiz currentQuiz;
    private final Integer[] currentAnswers = new Integer[Quiz.QUESTION_COUNT];
    private List<ReviewRecommendation> pendingRecommendations = new ArrayList<>();
    private String pendingRecommendationCourseId;
    private String pendingDraftCourseId;

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
                case "syllabus-import" -> importSyllabus(args);
                case "drafts" -> showDrafts();
                case "draft-set" -> setDraft(args);
                case "draft-remove" -> removeDraft(args);
                case "syllabus-confirm" -> confirmDrafts();
                case "syllabus-cancel" -> cancelDrafts();
                case "materials" -> listMaterials(args);
                case "material-import" -> importMaterial(args);
                case "material-show" -> showMaterial(args);
                case "material-remove" -> removeMaterial(args);
                case "quiz-generate" -> generateQuiz(args);
                case "quiz" -> showQuiz();
                case "quiz-answer" -> answerQuiz(args);
                case "quiz-submit" -> submitQuiz(args);
                case "quiz-cancel" -> cancelQuiz();
                case "quiz-history" -> showQuizHistory(args);
                case "topics" -> showTopics(args);
                case "recommend" -> recommend(args);
                case "recommend-accept" -> acceptRecommendation(args);
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
        } catch (SyllabusException e) {
            output.println("Could not read tasks from the syllabus: " + e.getMessage());
            output.println("No tasks were saved. You can add tasks yourself with task-add.");
        } catch (QuizException e) {
            output.println("Could not create a quiz: " + e.getMessage());
            output.println("Nothing was saved.");
        } catch (ReviewException e) {
            output.println("Could not get recommendations: " + e.getMessage());
            output.println("Nothing was changed.");
        } catch (PdfImportException e) {
            output.println("Import failed: " + e.getMessage());
            output.println("No material was saved.");
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
                Syllabus import (F03) - uses the AI assistant
                  syllabus-import N PDF_PATH           propose tasks from a syllabus PDF
                  drafts                               show the proposed tasks
                  draft-set D FIELD VALUE              D is a number or 'all'; FIELD is title, type,
                                                       deadline (YYYY-MM-DD [HH:mm]), priority or minutes
                  draft-remove D                       drop a proposed task
                  syllabus-confirm                     save the tasks once every detail is filled in
                  syllabus-cancel                      discard the proposals
                Course materials (F08)
                  materials N                          list materials of course N
                  material-import N TITLE PDF_PATH     import a text-based PDF
                  material-show N M                    show the start of material M's text
                  material-remove N M                  delete material M
                Practice quizzes (F09) - uses the AI assistant
                  quiz-generate N M TOPIC              make a 5-question quiz on TOPIC from material M
                  quiz                                 show the current quiz
                  quiz-answer Q CHOICE                 answer question Q with A, B, C or D
                  quiz-submit [confirm]                score it ('confirm' to submit unanswered questions)
                  quiz-cancel                          leave the quiz without submitting
                  quiz-history N                       past results of course N
                Review recommendations (F10) - uses the AI assistant
                  topics N                             accuracy per topic (weak: under 60% over 5+ answers)
                  recommend N                          propose review tasks for weak topics
                  recommend-accept R MINUTES YYYY-MM-DD HH:mm PRIORITY
                                                       create the review task R as a normal task
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

    private void importSyllabus(List<String> args) throws PdfImportException, SyllabusException {
        requireArgs(args, 2, "syllabus-import N PDF_PATH");
        Course course = courseAt(args.get(0));
        output.println("Reading the syllabus and asking the AI assistant...");
        pendingDrafts = new ArrayList<>(
                application.importSyllabus(course.getId(), Path.of(args.get(1))));
        pendingDraftCourseId = course.getId();
        output.println("Found " + pendingDrafts.size() + " possible tasks for " + course.getName()
                + " (not saved yet).");
        showDrafts();
    }

    private void showDrafts() {
        if (pendingDrafts.isEmpty()) {
            output.println("There are no proposed tasks. Use syllabus-import first.");
            return;
        }
        for (int i = 0; i < pendingDrafts.size(); i++) {
            TaskDraft draft = pendingDrafts.get(i);
            output.printf("%d. %s [%s] due %s, priority %s, duration %s%n", i + 1,
                    draft.getTitle(), draft.getType() == null ? "?" : draft.getType(),
                    draft.getDeadline() == null ? "?" : draft.getDeadline().format(DATE_TIME),
                    draft.getPriority() == null ? "?" : draft.getPriority(),
                    draft.getEstimatedMinutes() == null ? "?" : draft.getEstimatedMinutes() + " min");
        }
        long incomplete = pendingDrafts.stream().filter(d -> !d.isComplete()).count();
        output.println(incomplete == 0
                ? "Every task is complete. Type syllabus-confirm to save them."
                : incomplete + " task(s) still have a '?'. Fill them in with draft-set.");
    }

    private void setDraft(List<String> args) {
        requireArgs(args, 3, "draft-set D FIELD VALUE");
        List<TaskDraft> targets = args.get(0).equalsIgnoreCase("all")
                ? pendingDrafts : List.of(pick(pendingDrafts, args.get(0), "draft"));
        String field = args.get(1).toLowerCase(Locale.ROOT);
        String value = args.get(2);
        switch (field) {
            case "title" -> targets.forEach(draft -> draft.setTitle(value));
            case "type" -> targets.forEach(draft -> draft.setType(value));
            case "priority" -> {
                int priority = parseNumber(value, "priority");
                targets.forEach(draft -> draft.setPriority(priority));
            }
            case "minutes" -> {
                int minutes = parseNumber(value, "minutes");
                targets.forEach(draft -> draft.setEstimatedMinutes(minutes));
            }
            case "deadline" -> {
                java.time.LocalDateTime deadline = args.size() > 3
                        ? parseDateTime(value, args.get(3))
                        : parseDateTime(value, "23:59");
                targets.forEach(draft -> draft.setDeadline(deadline));
            }
            default -> throw new IllegalArgumentException(
                    "Unknown field '" + field + "'. Use title, type, deadline, priority or minutes.");
        }
        output.println("Updated.");
    }

    private void removeDraft(List<String> args) {
        requireArgs(args, 1, "draft-remove D");
        pendingDrafts.remove(pick(pendingDrafts, args.get(0), "draft"));
        output.println("Draft removed.");
    }

    private void confirmDrafts() throws IOException {
        if (pendingDrafts.isEmpty()) {
            throw new IllegalStateException(
                    "There are no proposed tasks to save. Use syllabus-import first.");
        }
        List<AcademicTask> saved = application.confirmTasks(pendingDraftCourseId, pendingDrafts);
        output.println("Saved " + saved.size() + " task(s).");
        pendingDrafts = new ArrayList<>();
        pendingDraftCourseId = null;
    }

    private void cancelDrafts() {
        pendingDrafts = new ArrayList<>();
        pendingDraftCourseId = null;
        output.println("Proposed tasks discarded. Nothing was saved.");
    }

    private void listMaterials(List<String> args) {
        requireArgs(args, 1, "materials N");
        Course course = courseAt(args.get(0));
        List<CourseMaterial> materials = application.getMaterials(course.getId());
        if (materials.isEmpty()) {
            output.println("No materials in " + course.getName() + ".");
            return;
        }
        for (int i = 0; i < materials.size(); i++) {
            output.printf("%d. %s (%d characters)%n", i + 1, materials.get(i).getTitle(),
                    materials.get(i).getText().length());
        }
    }

    private void importMaterial(List<String> args) throws IOException, PdfImportException {
        requireArgs(args, 3, "material-import N TITLE PDF_PATH");
        Course course = courseAt(args.get(0));
        CourseMaterial material = application.importMaterial(
                course.getId(), args.get(1), Path.of(args.get(2)));
        output.printf("Imported '%s' (%d characters).%n", material.getTitle(),
                material.getText().length());
    }

    private void showMaterial(List<String> args) {
        requireArgs(args, 2, "material-show N M");
        CourseMaterial material = materialAt(courseAt(args.get(0)), args.get(1));
        String text = application.getMaterial(material.getId()).getText();
        output.println(material.getTitle() + ":");
        if (text.length() > MATERIAL_PREVIEW_CHARACTERS) {
            output.println(text.substring(0, MATERIAL_PREVIEW_CHARACTERS) + "...");
            output.printf("(%d more characters)%n", text.length() - MATERIAL_PREVIEW_CHARACTERS);
        } else {
            output.println(text);
        }
    }

    private void removeMaterial(List<String> args) throws IOException {
        requireArgs(args, 2, "material-remove N M");
        CourseMaterial material = materialAt(courseAt(args.get(0)), args.get(1));
        application.removeMaterial(material.getId());
        output.println("Material removed.");
    }

    private void generateQuiz(List<String> args) throws QuizException, IOException {
        requireArgs(args, 3, "quiz-generate N M TOPIC");
        Course course = courseAt(args.get(0));
        CourseMaterial material = materialAt(course, args.get(1));
        output.println("Asking the AI assistant for a quiz...");
        currentQuiz = application.generateQuiz(course.getId(), material.getId(), args.get(2));
        java.util.Arrays.fill(currentAnswers, null);
        output.println("Quiz ready: " + currentQuiz.getTopic() + " (answers are hidden until you submit).");
        printQuestions();
    }

    private void showQuiz() {
        requireQuiz();
        printQuestions();
    }

    private void printQuestions() {
        List<QuizQuestion> questions = currentQuiz.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            output.printf("Q%d. %s%s%n", i + 1, questions.get(i).getText(),
                    currentAnswers[i] == null ? "" : "  [your answer: "
                            + (char) ('A' + currentAnswers[i]) + "]");
            for (int c = 0; c < questions.get(i).getChoices().size(); c++) {
                output.printf("   %c. %s%n", (char) ('A' + c), questions.get(i).getChoices().get(c));
            }
        }
    }

    private void answerQuiz(List<String> args) {
        requireQuiz();
        requireArgs(args, 2, "quiz-answer Q CHOICE");
        int question = parseNumber(args.get(0), "question number");
        if (question < 1 || question > Quiz.QUESTION_COUNT) {
            throw new IllegalArgumentException("Questions are numbered 1 to " + Quiz.QUESTION_COUNT + ".");
        }
        String choice = args.get(1).trim().toUpperCase(Locale.ROOT);
        if (choice.length() != 1 || choice.charAt(0) < 'A' || choice.charAt(0) > 'D') {
            throw new IllegalArgumentException("Choose A, B, C or D.");
        }
        currentAnswers[question - 1] = choice.charAt(0) - 'A';
        output.println("Answer " + choice + " recorded for question " + question + ".");
    }

    private void submitQuiz(List<String> args) throws IOException {
        requireQuiz();
        boolean confirm = !args.isEmpty() && args.get(0).equalsIgnoreCase("confirm");
        Quiz result = application.submitQuiz(currentQuiz.getId(),
                java.util.Arrays.asList(currentAnswers), confirm);
        output.printf("Score: %d out of %d (topic: %s).%n", result.getScore(),
                Quiz.QUESTION_COUNT, result.getTopic());
        printResults(result);
        currentQuiz = null;
        java.util.Arrays.fill(currentAnswers, null);
    }

    private void printResults(Quiz quiz) {
        for (int i = 0; i < quiz.getQuestions().size(); i++) {
            QuizQuestion question = quiz.getQuestions().get(i);
            Integer answer = quiz.getAnswers().get(i);
            String mark = answer == null ? "no answer" : answer == question.getCorrectIndex()
                    ? "correct" : "wrong (you chose " + (char) ('A' + answer) + ")";
            output.printf("Q%d: %s. Correct answer: %c. %s%n", i + 1, mark,
                    (char) ('A' + question.getCorrectIndex()), question.getExplanation());
        }
    }

    private void cancelQuiz() {
        currentQuiz = null;
        java.util.Arrays.fill(currentAnswers, null);
        output.println("Quiz closed. No attempt was recorded.");
    }

    private void showQuizHistory(List<String> args) {
        requireArgs(args, 1, "quiz-history N");
        Course course = courseAt(args.get(0));
        List<Quiz> history = application.getQuizHistory(course.getId());
        if (history.isEmpty()) {
            output.println("No submitted quizzes in " + course.getName() + ".");
            return;
        }
        for (int i = 0; i < history.size(); i++) {
            Quiz quiz = history.get(i);
            output.printf("%d. %s - %s - %d/%d (%d answered)%n", i + 1,
                    quiz.getCreatedAt().format(DATE_TIME), quiz.getTopic(), quiz.getScore(),
                    Quiz.QUESTION_COUNT, quiz.getAnsweredCount());
        }
    }

    private void showTopics(List<String> args) {
        requireArgs(args, 1, "topics N");
        Course course = courseAt(args.get(0));
        List<TopicStats> stats = application.getTopicStats(course.getId());
        if (stats.isEmpty()) {
            output.println("No submitted quizzes in " + course.getName() + " yet.");
            return;
        }
        for (TopicStats topic : stats) {
            output.printf("%s: %d/%d correct (%.0f%%)%s%n", topic.topic(), topic.correct(),
                    topic.answered(), topic.accuracy() * 100,
                    topic.isWeak() ? "  <- needs review"
                            : topic.answered() < TopicStats.MIN_ANSWERED
                            ? "  (needs " + TopicStats.MIN_ANSWERED + "+ answers to judge)" : "");
        }
    }

    private void recommend(List<String> args) throws ReviewException {
        requireArgs(args, 1, "recommend N");
        Course course = courseAt(args.get(0));
        List<ReviewRecommendation> found = application.getTopicStats(course.getId()).stream()
                .anyMatch(TopicStats::isWeak) ? application.recommendReviews(course.getId()) : List.of();
        pendingRecommendations = new ArrayList<>(found);
        pendingRecommendationCourseId = course.getId();
        if (found.isEmpty()) {
            output.println("No weak topics in " + course.getName()
                    + ". A topic is weak below 60% accuracy over at least 5 answered questions.");
            return;
        }
        for (int i = 0; i < found.size(); i++) {
            ReviewRecommendation r = found.get(i);
            output.printf("%d. %s (topic %s: %d/%d correct) - about %d min%n   %s%n   Material: %s%n",
                    i + 1, r.title(), r.stats().topic(), r.stats().correct(), r.stats().answered(),
                    r.suggestedMinutes(), r.focus(), r.materialTitle());
        }
        output.println("Nothing is saved. Use recommend-accept R MINUTES YYYY-MM-DD HH:mm PRIORITY.");
    }

    private void acceptRecommendation(List<String> args) throws IOException {
        requireArgs(args, 5, "recommend-accept R MINUTES YYYY-MM-DD HH:mm PRIORITY");
        ReviewRecommendation r = pick(pendingRecommendations, args.get(0), "recommendation");
        AcademicTask task = application.acceptRecommendation(pendingRecommendationCourseId, r,
                parseDateTime(args.get(2), args.get(3)), parseNumber(args.get(1), "minutes"),
                parseNumber(args.get(4), "priority"));
        pendingRecommendations.remove(r);
        output.println("Review task added: " + task.getTitle() + ". Schedule it with plan-generate.");
    }

    private void requireQuiz() {
        if (currentQuiz == null) {
            throw new IllegalStateException("There is no quiz in progress. Start one with quiz-generate.");
        }
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

    private CourseMaterial materialAt(Course course, String number) {
        return pick(application.getMaterials(course.getId()), number, "material");
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
package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.application.DurationSuggestion;
import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import java.awt.BorderLayout;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** F01 (courses and tasks) and F07 (duration suggestions). */
final class TasksSection extends Section {
    private static final String[] TASK_FIELDS = {"Title", "Type (e.g. Assignment, Quiz, Exam)",
        "Deadline (yyyy-MM-dd HH:mm)", "Priority (1-3, 3 = highest)", "Estimated minutes"};

    private final DefaultTableModel model = GuiSupport.readOnlyModel(
            "Title", "Type", "Deadline", "Priority", "Estimate (min)", "Remaining (min)", "Status");
    private final JTable table = table(model);
    private List<AcademicTask> shown = new ArrayList<>();

    TasksSection(StudyApplication app, Clock clock, Supplier<Course> course, Runnable refreshAll) {
        super(app, clock, course, refreshAll);
        JButton add = new JButton("Add task...");
        JButton edit = new JButton("Edit...");
        JButton remove = new JButton("Remove");
        JButton suggest = new JButton("Suggest duration...");
        add.addActionListener(e -> GuiSupport.guard(this, this::addTask));
        edit.addActionListener(e -> GuiSupport.guard(this, this::editTask));
        remove.addActionListener(e -> GuiSupport.guard(this, this::removeTask));
        suggest.addActionListener(e -> GuiSupport.guard(this, this::suggestDuration));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons(add, edit, remove, suggest), BorderLayout.SOUTH);
        add.setName("task-add");
        edit.setName("task-edit");
        remove.setName("task-remove");
        suggest.setName("task-suggest");
    }

    @Override
    void refresh() {
        model.setRowCount(0);
        Course course = course();
        shown = course == null ? new ArrayList<>() : new ArrayList<>(app.getTasks(course.getId()));
        for (AcademicTask task : shown) {
            String status = task.isArchived() ? "removed" : task.isCompleted() ? "done" : "open";
            model.addRow(new Object[] {task.getTitle(), task.getType(),
                task.getDeadline().format(GuiSupport.DATE_TIME), task.getPriority(),
                task.getOriginalEstimatedMinutes(), task.getRemainingMinutes(), status});
        }
    }

    private AcademicTask selectedTask() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            throw new IllegalStateException("Select a task in the table first.");
        }
        return shown.get(row);
    }

    private void addTask() throws Exception {
        Course course = requireCourse();
        String[] v = GuiSupport.dialogs.form(this, "Add task to " + course.getName(), TASK_FIELDS, null);
        if (v == null) {
            return;
        }
        AcademicTask task = new AcademicTask(course, v[0], v[1], GuiSupport.parseDateTime(v[2]),
                GuiSupport.parseNumber(v[3], "priority"), GuiSupport.parseNumber(v[4], "number of minutes"));
        app.saveTask(course.getId(), task);
        changed();
    }

    private void editTask() throws Exception {
        Course course = requireCourse();
        AcademicTask task = selectedTask();
        String[] labels = {TASK_FIELDS[0], TASK_FIELDS[1], TASK_FIELDS[2], TASK_FIELDS[3]};
        String[] initial = {task.getTitle(), task.getType(),
            task.getDeadline().format(GuiSupport.DATE_TIME), String.valueOf(task.getPriority())};
        String[] v = GuiSupport.dialogs.form(this, "Edit task", labels, initial);
        if (v == null) {
            return;
        }
        task.updateDetails(v[0], v[1], GuiSupport.parseDateTime(v[2]),
                GuiSupport.parseNumber(v[3], "priority"));
        app.saveTask(course.getId(), task);
        changed();
    }

    private void removeTask() throws Exception {
        AcademicTask task = selectedTask();
        if (GuiSupport.dialogs.confirm(this, "Remove '" + task.getTitle()
                + "'? It is archived and its future study sessions are dropped.")) {
            app.removeTask(task.getId());
            changed();
        }
    }

    private void suggestDuration() throws Exception {
        AcademicTask task = selectedTask();
        DurationSuggestion suggestion = app.getDurationSuggestion(task);
        String basis = suggestion.usedHistory()
                ? "based on " + suggestion.supportingTasks() + " finished '" + task.getType() + "' tasks."
                : "your original estimate (only " + suggestion.supportingTasks() + " finished '"
                        + task.getType() + "' tasks so far; 3 are needed to learn from history).";
        if (GuiSupport.dialogs.confirm(this, "Suggested duration: " + suggestion.minutes()
                + " minutes, " + basis + "\nAccept it for this task?")) {
            app.acceptDuration(task, suggestion.minutes());
            changed();
        }
    }
}
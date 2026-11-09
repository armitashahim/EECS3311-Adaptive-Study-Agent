package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.syllabus.TaskDraft;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** F03: the AI proposes tasks from a syllabus PDF; nothing is saved until the student confirms. */
final class SyllabusSection extends Section {
    private final DefaultTableModel model = GuiSupport.readOnlyModel(
            "Title", "Type", "Deadline", "Priority", "Minutes", "Still needed");
    private final JTable table = table(model);
    private final JLabel hint = new JLabel("Import a syllabus PDF to get proposed tasks. Nothing is saved until you confirm.");
    private final JButton importButton = new JButton("Import syllabus PDF...");
    private List<TaskDraft> drafts = new ArrayList<>();
    private String draftCourseId;

    SyllabusSection(StudyApplication app, Clock clock, Supplier<Course> course, Runnable refreshAll) {
        super(app, clock, course, refreshAll);
        JButton edit = new JButton("Edit selected...");
        JButton all = new JButton("Set priority/minutes for all...");
        JButton remove = new JButton("Remove selected");
        JButton confirm = new JButton("Save all as tasks");
        JButton cancel = new JButton("Discard");
        importButton.setName("syllabus-import");
        confirm.setName("syllabus-confirm");
        importButton.addActionListener(e -> GuiSupport.guard(this, this::importSyllabus));
        edit.addActionListener(e -> GuiSupport.guard(this, this::editDraft));
        all.addActionListener(e -> GuiSupport.guard(this, this::setForAll));
        remove.addActionListener(e -> GuiSupport.guard(this, this::removeDraft));
        confirm.addActionListener(e -> GuiSupport.guard(this, this::confirmDrafts));
        cancel.addActionListener(e -> {
            drafts = new ArrayList<>();
            reload();
            hint.setText("Proposed tasks discarded. Nothing was saved.");
        });
        add(hint, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons(importButton, edit, all, remove, confirm, cancel), BorderLayout.SOUTH);
    }

    @Override
    void refresh() {
        Course course = course();
        if (course == null || !course.getId().equals(draftCourseId)) {
            drafts = new ArrayList<>();
        }
        reload();
    }

    private void reload() {
        model.setRowCount(0);
        for (TaskDraft draft : drafts) {
            List<String> missing = draft.getMissingFields();
            model.addRow(new Object[] {draft.getTitle(), draft.getType() == null ? "?" : draft.getType(),
                draft.getDeadline() == null ? "?" : draft.getDeadline().format(GuiSupport.DATE_TIME),
                draft.getPriority() == null ? "?" : draft.getPriority(),
                draft.getEstimatedMinutes() == null ? "?" : draft.getEstimatedMinutes(),
                missing.isEmpty() ? "complete" : String.join(", ", missing)});
        }
    }

    private void importSyllabus() {
        Course course = requireCourse();
        Path pdf = GuiSupport.dialogs.choosePdf(this);
        if (pdf == null) {
            return;
        }
        GuiSupport.runAsync(this, "Reading the syllabus and asking the AI assistant...",
                () -> app.importSyllabus(course.getId(), pdf), result -> {
                    drafts = new ArrayList<>(result);
                    draftCourseId = course.getId();
                    reload();
                    hint.setText("Found " + drafts.size() + " possible tasks for " + course.getName()
                            + " (not saved yet). Fill in every '?' and then save.");
                }, importButton);
    }

    private TaskDraft selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= drafts.size()) {
            throw new IllegalStateException("Select a proposed task in the table first.");
        }
        return drafts.get(row);
    }

    private void editDraft() {
        TaskDraft draft = selected();
        String[] labels = {"Title", "Type", "Deadline (yyyy-MM-dd HH:mm)", "Priority (1-3, 3 = highest)",
            "Estimated minutes"};
        String[] initial = {draft.getTitle(), draft.getType() == null ? "" : draft.getType(),
            draft.getDeadline() == null ? "" : draft.getDeadline().format(GuiSupport.DATE_TIME),
            draft.getPriority() == null ? "" : String.valueOf(draft.getPriority()),
            draft.getEstimatedMinutes() == null ? "" : String.valueOf(draft.getEstimatedMinutes())};
        String[] v = GuiSupport.dialogs.form(this, "Edit proposed task", labels, initial);
        if (v == null) {
            return;
        }
        if (!v[0].isBlank()) {
            draft.setTitle(v[0]);
        }
        if (!v[1].isBlank()) {
            draft.setType(v[1]);
        }
        if (!v[2].isBlank()) {
            draft.setDeadline(GuiSupport.parseDateTime(v[2]));
        }
        if (!v[3].isBlank()) {
            draft.setPriority(GuiSupport.parseNumber(v[3], "priority"));
        }
        if (!v[4].isBlank()) {
            draft.setEstimatedMinutes(GuiSupport.parseNumber(v[4], "number of minutes"));
        }
        reload();
    }

    private void setForAll() {
        if (drafts.isEmpty()) {
            throw new IllegalStateException("There are no proposed tasks.");
        }
        String[] v = GuiSupport.dialogs.form(this, "Set for all proposed tasks",
                new String[] {"Priority (1-3, 3 = highest)", "Estimated minutes"}, null);
        if (v == null) {
            return;
        }
        Integer priority = v[0].isBlank() ? null : GuiSupport.parseNumber(v[0], "priority");
        Integer minutes = v[1].isBlank() ? null : GuiSupport.parseNumber(v[1], "number of minutes");
        for (TaskDraft draft : drafts) {
            if (priority != null) {
                draft.setPriority(priority);
            }
            if (minutes != null) {
                draft.setEstimatedMinutes(minutes);
            }
        }
        reload();
    }

    private void removeDraft() {
        drafts.remove(selected());
        reload();
    }

    private void confirmDrafts() throws Exception {
        if (drafts.isEmpty()) {
            throw new IllegalStateException("There are no proposed tasks to save.");
        }
        int saved = app.confirmTasks(draftCourseId, drafts).size();
        drafts = new ArrayList<>();
        reload();
        hint.setText("Saved " + saved + " task(s).");
        changed();
    }
}
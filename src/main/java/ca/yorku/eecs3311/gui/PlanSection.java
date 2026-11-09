package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.application.PlanProposal;
import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** F04 (generate a weekly plan), F05 (revise it) and F06 (record progress). */
final class PlanSection extends Section {
    private final JTextField week = new JTextField(10);
    private final JLabel heading = new JLabel(" ");
    private final DefaultTableModel model = GuiSupport.readOnlyModel(
            "Date", "Time", "Task", "Planned (min)", "Status", "Actual (min)");
    private final JTable table = table(model);
    private final JTextArea explanation = new JTextArea(4, 40);
    private final JButton generate = new JButton("Generate plan (AI)");
    private final JButton revise = new JButton("Revise plan (AI)");
    private final JButton accept = new JButton("Accept proposal");
    private final JButton discard = new JButton("Discard proposal");
    private final JButton record = new JButton("Record progress...");
    private PlanProposal pending;
    private List<StudySession> rows = new ArrayList<>();

    PlanSection(StudyApplication app, Clock clock, Supplier<Course> course, Runnable refreshAll) {
        super(app, clock, course, refreshAll);
        week.setText(mondayOf(LocalDate.now(clock)).toString());
        generate.setName("plan-generate");
        accept.setName("plan-accept");
        JButton show = new JButton("Show saved plan");
        show.addActionListener(e -> GuiSupport.guard(this, this::showSaved));
        generate.addActionListener(e -> GuiSupport.guard(this, () -> propose(false)));
        revise.addActionListener(e -> GuiSupport.guard(this, () -> propose(true)));
        accept.addActionListener(e -> GuiSupport.guard(this, this::acceptProposal));
        discard.addActionListener(e -> {
            pending = null;
            heading.setText("Proposal discarded. Nothing was changed.");
            GuiSupport.guard(this, this::showSaved);
        });
        record.addActionListener(e -> GuiSupport.guard(this, this::recordProgress));
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(new JLabel("Week starting (Monday):"));
        top.add(week);
        top.add(show);
        top.add(generate);
        top.add(revise);
        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(heading, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(new JScrollPane(explanation), BorderLayout.CENTER);
        south.add(buttons(accept, discard, record), BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        updateButtons();
    }

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate selectedWeek() {
        return mondayOf(GuiSupport.parseDate(week.getText()));
    }

    @Override
    void refresh() {
        if (pending == null) {
            try {
                showSaved();
            } catch (RuntimeException e) {
                heading.setText(GuiSupport.describe(e));
            }
        }
    }

    private void updateButtons() {
        accept.setEnabled(pending != null);
        discard.setEnabled(pending != null);
        record.setEnabled(pending == null);
    }

    private Map<String, String> taskTitles() {
        Map<String, String> titles = new HashMap<>();
        for (Course c : app.getCourses()) {
            for (AcademicTask task : app.getTasks(c.getId())) {
                titles.put(task.getId(), task.getTitle());
            }
        }
        return titles;
    }

    private void showSaved() {
        LocalDate start = selectedWeek();
        List<StudyPlan> plans = app.getPlans(start);
        pending = null;
        updateButtons();
        if (plans.isEmpty()) {
            heading.setText("No saved plan for the week of " + start + ".");
            display(List.of());
            return;
        }
        boolean needsRevision = plans.stream().anyMatch(StudyPlan::needsRevision);
        heading.setText("Saved plan for the week of " + start
                + (needsRevision ? " - needs revision (your tasks or time changed)" : ""));
        display(plans);
    }

    private void display(List<StudyPlan> plans) {
        model.setRowCount(0);
        rows = new ArrayList<>();
        plans.forEach(plan -> rows.addAll(plan.getSessions()));
        rows.sort(Comparator.comparing(StudySession::getStartDateTime));
        Map<String, String> titles = taskTitles();
        for (StudySession session : rows) {
            model.addRow(new Object[] {session.getStartDateTime().toLocalDate(),
                session.getStartDateTime().format(GuiSupport.TIME) + "-"
                        + session.getEndDateTime().format(GuiSupport.TIME),
                titles.getOrDefault(session.getTaskId(), "(removed task)"),
                session.getPlannedMinutes(), session.getStatus(),
                StudySession.SCHEDULED.equals(session.getStatus()) ? "" : session.getActualMinutes()});
        }
        StringBuilder text = new StringBuilder();
        for (StudyPlan plan : plans) {
            if (plan.getPriorityExplanation() != null && !plan.getPriorityExplanation().isBlank()) {
                text.append(plan.getPriorityExplanation()).append('\n');
            }
            plan.getUnscheduledMinutes().forEach((taskId, minutes) -> text.append("Could not fit: ")
                    .append(titles.getOrDefault(taskId, "(removed task)")).append(" - ")
                    .append(minutes).append(" min\n"));
        }
        explanation.setText(text.toString());
        explanation.setCaretPosition(0);
    }

    private void propose(boolean revision) {
        LocalDate start = selectedWeek();
        GuiSupport.runAsync(this, "Asking the AI assistant to " + (revision ? "revise" : "plan")
                + " the week of " + start + "...",
                () -> revision ? app.revisePlan(start) : app.generatePlan(start), proposal -> {
                    pending = proposal;
                    heading.setText((proposal.revision() ? "Proposed revision" : "Proposed plan")
                            + " for the week of " + start + " (not saved yet) - accept or discard it.");
                    display(List.of(proposal.plan()));
                    updateButtons();
                }, generate, revise);
    }

    private void acceptProposal() throws Exception {
        if (pending == null) {
            throw new IllegalStateException("There is no proposal to accept.");
        }
        PlanProposal proposal = pending;
        app.acceptPlan(proposal);
        pending = null;
        heading.setText("Plan saved.");
        updateButtons();
        changed();
    }

    private void recordProgress() throws Exception {
        int row = table.getSelectedRow();
        if (row < 0 || row >= rows.size()) {
            throw new IllegalStateException("Select a study session in the table first.");
        }
        StudySession session = rows.get(row);
        String[] v = GuiSupport.dialogs.form(this, "Record progress",
                new String[] {"Outcome (completed, partial or missed)", "Minutes actually studied",
                    "Task finished? (yes/no)", "Minutes still needed (if not finished)"},
                new String[] {"completed", String.valueOf(session.getPlannedMinutes()), "no", "0"});
        if (v == null) {
            return;
        }
        boolean finished = v[2].equalsIgnoreCase("yes");
        app.recordProgress(session, v[0].toLowerCase(java.util.Locale.ROOT),
                GuiSupport.parseNumber(v[1], "number of minutes"), finished,
                finished ? 0 : GuiSupport.parseNumber(v[3], "number of minutes"));
        changed();
    }
}
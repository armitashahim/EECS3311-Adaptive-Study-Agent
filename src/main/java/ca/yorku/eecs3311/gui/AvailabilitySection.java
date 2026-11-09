package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** F02: weekly availability and commitments. Study time is availability minus commitments. */
final class AvailabilitySection extends Section {
    private final DefaultTableModel model = GuiSupport.readOnlyModel("Kind", "Day", "From", "To");
    private final JTable table = table(model);
    private final JComboBox<String> kind = new JComboBox<>(
            new String[] {WeeklyPeriod.AVAILABILITY, WeeklyPeriod.COMMITMENT});
    private final JComboBox<DayOfWeek> day = new JComboBox<>(DayOfWeek.values());
    private final JTextField from = new JTextField("09:00", 6);
    private final JTextField to = new JTextField("11:00", 6);
    private List<WeeklyPeriod> shown = new ArrayList<>();

    AvailabilitySection(StudyApplication app, Clock clock, Supplier<Course> course, Runnable refreshAll) {
        super(app, clock, course, refreshAll);
        day.setRenderer((list, value, index, selected, focus) ->
                new JLabel(value.getDisplayName(TextStyle.FULL, Locale.ENGLISH)));
        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove selected");
        add.setName("period-add");
        add.addActionListener(e -> GuiSupport.guard(this, this::addPeriod));
        remove.addActionListener(e -> GuiSupport.guard(this, this::removePeriod));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        form.add(new JLabel("Add:"));
        form.add(kind);
        form.add(day);
        form.add(new JLabel("from"));
        form.add(from);
        form.add(new JLabel("to"));
        form.add(to);
        form.add(add);
        form.add(remove);
        add(new JLabel("Availability is when you can study; commitments (classes, work) are removed from it."),
                BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(form, BorderLayout.SOUTH);
    }

    @Override
    void refresh() {
        model.setRowCount(0);
        shown = new ArrayList<>(app.getPeriods());
        shown.sort(Comparator.comparing(WeeklyPeriod::getDayOfWeek)
                .thenComparing(WeeklyPeriod::getStartTime));
        for (WeeklyPeriod period : shown) {
            model.addRow(new Object[] {period.getType(),
                period.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                period.getStartTime().format(GuiSupport.TIME), period.getEndTime().format(GuiSupport.TIME)});
        }
    }

    private void addPeriod() throws Exception {
        app.savePeriod(new WeeklyPeriod((DayOfWeek) day.getSelectedItem(),
                GuiSupport.parseTime(from.getText()), GuiSupport.parseTime(to.getText()),
                (String) kind.getSelectedItem()));
        changed();
    }

    private void removePeriod() throws Exception {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            throw new IllegalStateException("Select a period in the table first.");
        }
        app.removePeriod(shown.get(row).getId());
        changed();
    }
}
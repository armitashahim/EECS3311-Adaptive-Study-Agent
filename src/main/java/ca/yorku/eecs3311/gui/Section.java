package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.Course;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.Clock;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

/** One tab of the window. It shows facade data and calls the facade; it holds no rules. */
abstract class Section extends JPanel {
    protected final StudyApplication app;
    protected final Clock clock;
    private final Supplier<Course> selectedCourse;
    private final Runnable refreshAll;

    Section(StudyApplication app, Clock clock, Supplier<Course> selectedCourse, Runnable refreshAll) {
        super(new BorderLayout(8, 8));
        this.app = app;
        this.clock = clock;
        this.selectedCourse = selectedCourse;
        this.refreshAll = refreshAll;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    /** Reloads what this tab shows from the application. */
    abstract void refresh();

    protected Course course() {
        return selectedCourse.get();
    }

    protected Course requireCourse() {
        Course course = selectedCourse.get();
        if (course == null) {
            throw new IllegalStateException("Add or select a course first.");
        }
        return course;
    }

    protected void changed() {
        refreshAll.run();
    }

    protected Component self() {
        return this;
    }

    protected static JTable table(javax.swing.table.DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(30);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.getTableHeader().setPreferredSize(new java.awt.Dimension(100, 32));
        table.setFillsViewportHeight(true);
        return table;
    }

    protected static JPanel buttons(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }
}
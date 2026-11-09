package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.ai.DeepSeekClient;
import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.config.AppConfig;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Graphical view over {@link StudyApplication}, the same facade the CLI uses. Each tab is a
 * {@link Section}; slow AI calls run in the background so the window stays responsive.
 */
public class StudyGUI extends JFrame {
    private final StudyApplication app;
    private final JComboBox<Course> courses = new JComboBox<>();
    private final List<Section> sections = new ArrayList<>();
    private boolean refreshing;

    public StudyGUI(StudyApplication app, Clock clock, boolean aiConfigured) {
        super("Adaptive Study & Learning Agent");
        this.app = app;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        courses.setRenderer((list, value, index, selected, focus) ->
                new JLabel(value == null ? "(no courses yet)" : value.getName()));
        courses.setName("course-combo");
        courses.addActionListener(e -> {
            if (!refreshing) {
                refreshSections();
            }
        });
        JButton addCourse = new JButton("Add course...");
        JButton renameCourse = new JButton("Rename...");
        JButton removeCourse = new JButton("Delete");
        addCourse.setName("course-add");
        addCourse.addActionListener(e -> GuiSupport.guard(this, this::addCourse));
        renameCourse.addActionListener(e -> GuiSupport.guard(this, this::renameCourse));
        removeCourse.addActionListener(e -> GuiSupport.guard(this, this::removeCourse));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        top.add(new JLabel("Course:"));
        top.add(courses);
        top.add(addCourse);
        top.add(renameCourse);
        top.add(removeCourse);

        JTabbedPane tabs = new JTabbedPane();
        add(tabs, "Tasks (F01, F07)", new TasksSection(app, clock, this::selectedCourse, this::refreshAllSections));
        add(tabs, "Syllabus import (F03)", new SyllabusSection(app, clock, this::selectedCourse, this::refreshAllSections));
        add(tabs, "Availability (F02)", new AvailabilitySection(app, clock, this::selectedCourse, this::refreshAllSections));
        add(tabs, "Weekly plan (F04-F06)", new PlanSection(app, clock, this::selectedCourse, this::refreshAllSections));
        add(tabs, "Materials (F08)", new MaterialsSection(app, clock, this::selectedCourse, this::refreshAllSections));
        add(tabs, "Practice quiz (F09)", new QuizSection(app, clock, this::selectedCourse, this::refreshAllSections));
        add(tabs, "Review (F10)", new ReviewSection(app, clock, this::selectedCourse, this::refreshAllSections));

        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        status.add(GuiSupport.statusLabel, BorderLayout.WEST);
        status.add(new JLabel(aiConfigured ? "AI assistant: connected"
                : "AI assistant: not configured (add LLM_API_KEY to .env)"), BorderLayout.EAST);

        setLayout(new BorderLayout());
        super.add(top, BorderLayout.NORTH);
        super.add(tabs, BorderLayout.CENTER);
        super.add(status, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(1040, 680));
        pack();
        setLocationRelativeTo(null);
        reloadCourses(null);
    }

    private void add(JTabbedPane tabs, String title, Section section) {
        sections.add(section);
        tabs.addTab(title, section);
    }

    private Course selectedCourse() {
        return (Course) courses.getSelectedItem();
    }

    private void refreshAllSections() {
        reloadCourses(selectedCourse() == null ? null : selectedCourse().getId());
    }

    private void refreshSections() {
        sections.forEach(Section::refresh);
    }

    private void reloadCourses(String keepId) {
        refreshing = true;
        try {
            courses.removeAllItems();
            Course keep = null;
            for (Course course : app.getCourses()) {
                courses.addItem(course);
                if (course.getId().equals(keepId)) {
                    keep = course;
                }
            }
            if (keep != null) {
                courses.setSelectedItem(keep);
            }
        } finally {
            refreshing = false;
        }
        refreshSections();
    }

    private void addCourse() throws IOException {
        String[] v = GuiSupport.dialogs.form(this, "Add course", new String[] {"Course name"}, null);
        if (v == null) {
            return;
        }
        Course course = new Course(v[0]);
        app.saveCourse(course);
        reloadCourses(course.getId());
    }

    private void renameCourse() throws IOException {
        Course course = selectedCourse();
        if (course == null) {
            throw new IllegalStateException("There is no course to rename.");
        }
        String[] v = GuiSupport.dialogs.form(this, "Rename course", new String[] {"Course name"},
                new String[] {course.getName()});
        if (v == null) {
            return;
        }
        course.rename(v[0]);
        app.saveCourse(course);
        reloadCourses(course.getId());
    }

    private void removeCourse() throws IOException {
        Course course = selectedCourse();
        if (course == null) {
            throw new IllegalStateException("There is no course to delete.");
        }
        if (GuiSupport.dialogs.confirm(this, "Delete the course '" + course.getName()
                + "'? This only works if it never had tasks, materials or quizzes.")) {
            app.removeCourse(course.getId());
            reloadCourses(null);
        }
    }

    /** Starts the window. The optional argument is the data file (default data/repository.json). */
    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : "data/repository.json");
        LocalRepository repository = new LocalRepository(file);
        AppConfig config = AppConfig.load();
        Clock clock = Clock.systemDefaultZone();
        StudyApplication application = new StudyApplication(repository, clock,
                config.hasApiKey() ? DeepSeekClient.fromConfig(config) : null);
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception ignored) {
                // The default look and feel is fine.
            }
            new StudyGUI(application, clock, config.hasApiKey()).setVisible(true);
        });
    }
}
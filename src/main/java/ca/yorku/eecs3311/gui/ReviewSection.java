package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.review.ReviewRecommendation;
import ca.yorku.eecs3311.review.TopicStats;
import java.awt.BorderLayout;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** F10: topic accuracy from saved quizzes (code) and AI-proposed review tasks for weak topics. */
final class ReviewSection extends Section {
    private final DefaultTableModel topicModel = GuiSupport.readOnlyModel(
            "Topic", "Correct", "Answered", "Accuracy", "Status");
    private final DefaultTableModel recommendationModel = GuiSupport.readOnlyModel(
            "Proposed review task", "Topic", "Suggested minutes", "Why", "Material");
    private final JTable recommendationTable = table(recommendationModel);
    private final JButton recommend = new JButton("Get recommendations (AI)");
    private final JLabel hint = new JLabel(" ");
    private List<ReviewRecommendation> recommendations = new ArrayList<>();
    private String recommendationCourseId;

    ReviewSection(StudyApplication app, Clock clock, Supplier<Course> course, Runnable refreshAll) {
        super(app, clock, course, refreshAll);
        recommend.setName("review-recommend");
        JButton accept = new JButton("Accept selected...");
        accept.setName("review-accept");
        recommend.addActionListener(e -> GuiSupport.guard(this, this::recommend));
        accept.addActionListener(e -> GuiSupport.guard(this, this::acceptSelected));
        JPanel topics = new JPanel(new BorderLayout());
        topics.add(new JLabel("Accuracy by topic (a topic is weak below 60% over at least 5 answered questions)"),
                BorderLayout.NORTH);
        topics.add(new JScrollPane(table(topicModel)), BorderLayout.CENTER);
        JPanel recs = new JPanel(new BorderLayout());
        recs.add(hint, BorderLayout.NORTH);
        recs.add(new JScrollPane(recommendationTable), BorderLayout.CENTER);
        recs.add(buttons(recommend, accept), BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topics, recs);
        split.setResizeWeight(0.4);
        add(split, BorderLayout.CENTER);
    }

    @Override
    void refresh() {
        topicModel.setRowCount(0);
        Course course = course();
        if (course == null || !course.getId().equals(recommendationCourseId)) {
            recommendations = new ArrayList<>();
            recommendationModel.setRowCount(0);
        }
        if (course == null) {
            return;
        }
        for (TopicStats stats : app.getTopicStats(course.getId())) {
            topicModel.addRow(new Object[] {stats.topic(), stats.correct(), stats.answered(),
                Math.round(stats.accuracy() * 100) + "%",
                stats.isWeak() ? "needs review" : stats.answered() < TopicStats.MIN_ANSWERED
                        ? "not enough answers yet" : "ok"});
        }
    }

    private void recommend() {
        Course course = requireCourse();
        GuiSupport.runAsync(this, "Asking the AI assistant for review recommendations...",
                () -> app.recommendReviews(course.getId()), found -> {
                    recommendations = new ArrayList<>(found);
                    recommendationCourseId = course.getId();
                    recommendationModel.setRowCount(0);
                    for (ReviewRecommendation r : found) {
                        recommendationModel.addRow(new Object[] {r.title(), r.stats().topic(),
                            r.suggestedMinutes(), r.focus(), r.materialTitle()});
                    }
                    hint.setText(found.isEmpty() ? "No weak topics yet. Take more quizzes first."
                            : "Nothing is saved until you accept a recommendation.");
                }, recommend);
    }

    private void acceptSelected() throws Exception {
        Course course = requireCourse();
        int row = recommendationTable.getSelectedRow();
        if (row < 0 || row >= recommendations.size()) {
            throw new IllegalStateException("Select a recommendation first.");
        }
        ReviewRecommendation r = recommendations.get(row);
        String[] v = GuiSupport.dialogs.form(this, "Accept review task: " + r.title(),
                new String[] {"Minutes", "Deadline (yyyy-MM-dd HH:mm)", "Priority (1-3, 3 = highest)"},
                new String[] {String.valueOf(r.suggestedMinutes()),
                    LocalDateTime.now(clock).plusDays(3).withHour(18).withMinute(0).format(GuiSupport.DATE_TIME),
                    "2"});
        if (v == null) {
            return;
        }
        app.acceptRecommendation(course.getId(), r, GuiSupport.parseDateTime(v[1]),
                GuiSupport.parseNumber(v[0], "number of minutes"), GuiSupport.parseNumber(v[2], "priority"));
        recommendations.remove(row);
        recommendationModel.removeRow(row);
        GuiSupport.dialogs.info(this, "Review task added. Schedule it from the Weekly plan tab.");
        changed();
    }
}
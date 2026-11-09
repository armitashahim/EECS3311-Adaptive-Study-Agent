package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import ca.yorku.eecs3311.model.Quiz;
import ca.yorku.eecs3311.model.QuizQuestion;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** F09: AI-written practice quizzes, scored by code, with a history of submitted attempts. */
final class QuizSection extends Section {
    private final JComboBox<CourseMaterial> materials = new JComboBox<>();
    private final JTextField topic = new JTextField(16);
    private final JButton generate = new JButton("Generate quiz (AI)");
    private final JButton submit = new JButton("Submit answers");
    private final JButton close = new JButton("Close quiz");
    private final JPanel questions = new JPanel();
    private final JLabel result = new JLabel(" ");
    private final DefaultTableModel historyModel = GuiSupport.readOnlyModel(
            "Date", "Topic", "Score", "Answered");
    private final JTable historyTable = table(historyModel);
    private Quiz quiz;
    private String quizCourseId;
    private final List<List<JRadioButton>> choices = new ArrayList<>();
    private List<Quiz> history = new ArrayList<>();

    QuizSection(StudyApplication app, Clock clock, Supplier<Course> course, Runnable refreshAll) {
        super(app, clock, course, refreshAll);
        materials.setRenderer((list, value, index, selected, focus) ->
                new JLabel(value == null ? "(no materials)" : value.getTitle()));
        generate.setName("quiz-generate");
        submit.setName("quiz-submit");
        generate.addActionListener(e -> GuiSupport.guard(this, this::generateQuiz));
        submit.addActionListener(e -> GuiSupport.guard(this, this::submitQuiz));
        close.addActionListener(e -> {
            clearQuiz();
            result.setText("Quiz closed. No attempt was recorded.");
        });
        JButton view = new JButton("View selected results...");
        view.addActionListener(e -> GuiSupport.guard(this, this::viewResults));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(new JLabel("Material:"));
        top.add(materials);
        top.add(new JLabel("Topic:"));
        top.add(topic);
        top.add(generate);
        questions.setLayout(new BoxLayout(questions, BoxLayout.Y_AXIS));
        JPanel quizPanel = new JPanel(new BorderLayout());
        quizPanel.add(top, BorderLayout.NORTH);
        quizPanel.add(new JScrollPane(questions), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.add(submit);
        actions.add(close);
        actions.add(result);
        quizPanel.add(actions, BorderLayout.SOUTH);

        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.add(new JLabel("Quiz history (submitted attempts)"), BorderLayout.NORTH);
        historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        historyPanel.add(buttons(view), BorderLayout.SOUTH);
        historyPanel.setPreferredSize(new java.awt.Dimension(100, 190));
        add(quizPanel, BorderLayout.CENTER);
        add(historyPanel, BorderLayout.SOUTH);
        setQuizActive(false);
    }

    @Override
    void refresh() {
        Course course = course();
        if (quiz != null && (course == null || !course.getId().equals(quizCourseId))) {
            clearQuiz();
        }
        materials.removeAllItems();
        historyModel.setRowCount(0);
        history = new ArrayList<>();
        if (course == null) {
            return;
        }
        app.getMaterials(course.getId()).forEach(materials::addItem);
        history = app.getQuizHistory(course.getId());
        for (Quiz past : history) {
            historyModel.addRow(new Object[] {past.getCreatedAt().format(GuiSupport.DATE_TIME),
                past.getTopic(), past.getScore() + " / " + Quiz.QUESTION_COUNT, past.getAnsweredCount()});
        }
    }

    private void setQuizActive(boolean active) {
        submit.setEnabled(active);
        close.setEnabled(active);
    }

    private void clearQuiz() {
        quiz = null;
        choices.clear();
        questions.removeAll();
        questions.revalidate();
        questions.repaint();
        setQuizActive(false);
    }

    private void generateQuiz() {
        Course course = requireCourse();
        CourseMaterial material = (CourseMaterial) materials.getSelectedItem();
        if (material == null) {
            throw new IllegalStateException("Import a material for this course first (Materials tab).");
        }
        String label = topic.getText();
        GuiSupport.runAsync(this, "Asking the AI assistant to write a quiz...",
                () -> app.generateQuiz(course.getId(), material.getId(), label), created -> {
                    quiz = created;
                    quizCourseId = course.getId();
                    result.setText("Answers stay hidden until you submit.");
                    buildQuestions();
                    setQuizActive(true);
                }, generate);
    }

    private void buildQuestions() {
        questions.removeAll();
        choices.clear();
        int number = 1;
        for (QuizQuestion question : quiz.getQuestions()) {
            JPanel block = new JPanel();
            block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
            block.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
            JLabel text = new JLabel("<html><b>Q" + number + ".</b> "
                    + GuiSupport.html(question.getText()) + "</html>");
            block.add(text);
            ButtonGroup group = new ButtonGroup();
            List<JRadioButton> row = new ArrayList<>();
            for (int c = 0; c < question.getChoices().size(); c++) {
                JRadioButton radio = new JRadioButton((char) ('A' + c) + ". " + question.getChoices().get(c));
                group.add(radio);
                block.add(radio);
                row.add(radio);
            }
            choices.add(row);
            questions.add(block);
            number++;
        }
        questions.revalidate();
        questions.repaint();
    }

    private List<Integer> chosenAnswers() {
        List<Integer> answers = new ArrayList<>();
        for (List<JRadioButton> row : choices) {
            Integer chosen = null;
            for (int c = 0; c < row.size(); c++) {
                if (row.get(c).isSelected()) {
                    chosen = c;
                }
            }
            answers.add(chosen);
        }
        return answers;
    }

    private void submitQuiz() throws Exception {
        if (quiz == null) {
            throw new IllegalStateException("There is no quiz in progress.");
        }
        List<Integer> answers = chosenAnswers();
        int unanswered = quiz.getUnansweredCount(answers);
        boolean confirmed = false;
        if (unanswered > 0) {
            if (!GuiSupport.dialogs.confirm(this, unanswered + " question(s) are unanswered and will "
                    + "receive no credit. Submit anyway?")) {
                return;
            }
            confirmed = true;
        }
        Quiz submitted = app.submitQuiz(quiz.getId(), answers, confirmed);
        showResults(submitted);
        quiz = null;
        setQuizActive(false);
        changed();
    }

    private void showResults(Quiz submitted) {
        result.setText("Score: " + submitted.getScore() + " out of " + Quiz.QUESTION_COUNT);
        for (int i = 0; i < choices.size() && i < questions.getComponentCount(); i++) {
            choices.get(i).forEach(radio -> radio.setEnabled(false));
            QuizQuestion question = submitted.getQuestions().get(i);
            Integer answer = submitted.getAnswers().get(i);
            String verdict = answer == null ? "No answer." : answer == question.getCorrectIndex()
                    ? "Correct." : "Not correct (you chose " + (char) ('A' + answer) + ").";
            ((JPanel) questions.getComponent(i)).add(new JLabel("<html><i>" + verdict
                    + " Correct answer: " + (char) ('A' + question.getCorrectIndex()) + ". "
                    + GuiSupport.html(question.getExplanation()) + "</i></html>"));
        }
        questions.revalidate();
        questions.repaint();
    }

    private void viewResults() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= history.size()) {
            throw new IllegalStateException("Select a quiz in the history first.");
        }
        Quiz past = history.get(row);
        StringBuilder text = new StringBuilder("Topic: " + past.getTopic() + "\nScore: "
                + past.getScore() + " out of " + Quiz.QUESTION_COUNT + "\n\n");
        for (int i = 0; i < past.getQuestions().size(); i++) {
            QuizQuestion question = past.getQuestions().get(i);
            Integer answer = past.getAnswers().get(i);
            text.append("Q").append(i + 1).append(". ").append(question.getText()).append('\n')
                    .append("   Your answer: ").append(answer == null ? "none" : String.valueOf((char) ('A' + answer)))
                    .append("   Correct: ").append((char) ('A' + question.getCorrectIndex())).append('\n')
                    .append("   ").append(question.getExplanation()).append("\n\n");
        }
        GuiSupport.dialogs.showText(this, "Quiz results", text.toString());
    }
}
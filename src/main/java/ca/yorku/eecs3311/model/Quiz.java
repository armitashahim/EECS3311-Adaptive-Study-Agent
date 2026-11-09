package ca.yorku.eecs3311.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A saved five-question quiz and its single attempt. Answers are stored as choice
 * indexes (null = unanswered). Once submitted, the score is fixed and nothing can change.
 */
public class Quiz {
    public static final int QUESTION_COUNT = 5;

    private final String id;
    private final Course course;
    private final String materialId;
    private final String topic;
    private final LocalDateTime createdAt;
    private final List<QuizQuestion> questions;
    private final List<Integer> answers;
    private final boolean submitted;
    private final int score;

    /** A new quiz with no answers yet. */
    public Quiz(Course course, String materialId, String topic, LocalDateTime createdAt,
                List<QuizQuestion> questions) {
        this(UUID.randomUUID().toString(), course, materialId, topic, createdAt, questions,
                Collections.nCopies(QUESTION_COUNT, (Integer) null), false);
    }

    /** Restores a saved quiz; the score is recalculated from the answers. */
    public Quiz(String id, Course course, String materialId, String topic, LocalDateTime createdAt,
                List<QuizQuestion> questions, List<Integer> answers, boolean submitted) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Quiz ID cannot be empty.");
        }
        if (course == null) {
            throw new IllegalArgumentException("A quiz must belong to a course.");
        }
        if (materialId == null || materialId.isBlank()) {
            throw new IllegalArgumentException("A quiz must be based on a material.");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic cannot be empty.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation time is required.");
        }
        if (questions == null || questions.size() != QUESTION_COUNT) {
            throw new IllegalArgumentException("A quiz needs exactly " + QUESTION_COUNT + " questions.");
        }
        if (answers == null || answers.size() != QUESTION_COUNT) {
            throw new IllegalArgumentException("A quiz needs one answer slot per question.");
        }
        for (Integer answer : answers) {
            if (answer != null && (answer < 0 || answer >= QuizQuestion.CHOICE_COUNT)) {
                throw new IllegalArgumentException("An answer must be one of the four choices.");
            }
        }
        if (!submitted && answers.stream().anyMatch(a -> a != null)) {
            throw new IllegalArgumentException("Answers are only kept once a quiz is submitted.");
        }
        this.id = id;
        this.course = course;
        this.materialId = materialId;
        this.topic = topic.strip();
        this.createdAt = createdAt;
        this.questions = List.copyOf(questions);
        this.answers = Collections.unmodifiableList(new ArrayList<>(answers));
        this.submitted = submitted;
        int correct = 0;
        for (int i = 0; i < QUESTION_COUNT; i++) {
            if (answers.get(i) != null && answers.get(i) == questions.get(i).getCorrectIndex()) {
                correct++;
            }
        }
        this.score = correct;
    }

    /** Returns a submitted copy. Unanswered questions (null) receive no credit. */
    public Quiz submit(List<Integer> chosen) {
        if (submitted) {
            throw new IllegalStateException("This quiz was already submitted.");
        }
        return new Quiz(id, course, materialId, topic, createdAt, questions, chosen, true);
    }

    public int getUnansweredCount(List<Integer> chosen) {
        int count = 0;
        for (Integer answer : chosen) {
            if (answer == null) {
                count++;
            }
        }
        return count;
    }

    public String getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getMaterialId() {
        return materialId;
    }

    public String getTopic() {
        return topic;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<QuizQuestion> getQuestions() {
        return questions;
    }

    public List<Integer> getAnswers() {
        return answers;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    /** Number of correct answers (0 until submitted). */
    public int getScore() {
        return submitted ? score : 0;
    }

    /** Number of questions that received an answer. */
    public int getAnsweredCount() {
        int count = 0;
        for (Integer answer : answers) {
            if (answer != null) {
                count++;
            }
        }
        return count;
    }
}
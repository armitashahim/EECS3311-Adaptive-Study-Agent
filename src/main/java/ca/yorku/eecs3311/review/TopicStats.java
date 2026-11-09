package ca.yorku.eecs3311.review;

/** Accuracy for one topic across a course's submitted quizzes. */
public record TopicStats(String topic, int answered, int correct, String materialId) {
    public static final int MIN_ANSWERED = 5;
    public static final double WEAK_BELOW = 0.60;

    public double accuracy() {
        return answered == 0 ? 0.0 : (double) correct / answered;
    }

    /** Weak = at least five answered questions and accuracy below 60%. */
    public boolean isWeak() {
        return answered >= MIN_ANSWERED && accuracy() < WEAK_BELOW;
    }
}
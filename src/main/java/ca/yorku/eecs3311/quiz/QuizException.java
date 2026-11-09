package ca.yorku.eecs3311.quiz;

/** Thrown when a quiz cannot be created from the AI service's answer. */
public class QuizException extends Exception {
    public QuizException(String message) {
        super(message);
    }

    public QuizException(String message, Throwable cause) {
        super(message, cause);
    }
}
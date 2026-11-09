package ca.yorku.eecs3311.review;

public class ReviewException extends Exception {
    public ReviewException(String message) {
        super(message);
    }

    public ReviewException(String message, Throwable cause) {
        super(message, cause);
    }
}
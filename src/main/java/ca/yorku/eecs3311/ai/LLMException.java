package ca.yorku.eecs3311.ai;

/** Raised when a model request fails or returns an unusable response. */
public class LLMException extends Exception {
    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
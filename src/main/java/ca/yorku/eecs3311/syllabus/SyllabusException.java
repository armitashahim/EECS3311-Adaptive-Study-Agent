package ca.yorku.eecs3311.syllabus;

/** Tasks could not be extracted from a syllabus; nothing is saved. */
public class SyllabusException extends Exception {
    public SyllabusException(String message) {
        super(message);
    }

    public SyllabusException(String message, Throwable cause) {
        super(message, cause);
    }
}
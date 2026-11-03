package ca.yorku.eecs3311.material;

/** A PDF could not be imported; the message explains why, for display to the student. */
public class PdfImportException extends Exception {
    public PdfImportException(String message) {
        super(message);
    }

    public PdfImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
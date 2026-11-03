package ca.yorku.eecs3311.material;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Checks and reads text-based PDF files. Every rejection carries a plain explanation:
 * the file type and size are checked first, then the text is extracted and checked
 * for being present and within the length limit.
 */
public class PdfImporter {
    public static final long DEFAULT_MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final int DEFAULT_MAX_TEXT_CHARACTERS = 60_000;
    private static final int HEADER_SEARCH_BYTES = 1024;

    private final long maxFileBytes;
    private final int maxTextCharacters;

    public PdfImporter() {
        this(DEFAULT_MAX_FILE_BYTES, DEFAULT_MAX_TEXT_CHARACTERS);
    }

    public PdfImporter(long maxFileBytes, int maxTextCharacters) {
        if (maxFileBytes <= 0 || maxTextCharacters <= 0) {
            throw new IllegalArgumentException("Import limits must be positive.");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxTextCharacters = maxTextCharacters;
    }

    public int getMaxTextCharacters() {
        return maxTextCharacters;
    }

    /** Checks that the file exists, is a PDF, and is within the size limit. */
    public void validateFile(Path file) throws PdfImportException {
        if (file == null) {
            throw new PdfImportException("No file was selected.");
        }
        if (!Files.isRegularFile(file)) {
            throw new PdfImportException("The file could not be found: " + file.getFileName());
        }
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new PdfImportException("Only PDF files are supported.");
        }

        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new PdfImportException("The file could not be read: " + e.getMessage(), e);
        }
        if (size == 0) {
            throw new PdfImportException("The file is empty.");
        }
        if (size > maxFileBytes) {
            throw new PdfImportException(String.format(Locale.ROOT,
                    "The file is too large (%.1f MB). The limit is %.1f MB.",
                    size / 1048576.0, maxFileBytes / 1048576.0));
        }
        if (!hasPdfHeader(file)) {
            throw new PdfImportException("The file is not a valid PDF.");
        }
    }

    /**
     * Validates the file and returns its text. Rejects password-protected, corrupted,
     * scanned (no text) and over-long documents.
     */
    public String extractText(Path file) throws PdfImportException {
        validateFile(file);

        String raw;
        try (PDDocument document = PDDocument.load(file.toFile())) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw new PdfImportException("The PDF does not allow its text to be copied.");
            }
            raw = new PDFTextStripper().getText(document);
        } catch (InvalidPasswordException e) {
            throw new PdfImportException("The PDF is password-protected.", e);
        } catch (IOException | RuntimeException e) {
            throw new PdfImportException("The PDF could not be read; it may be corrupted.", e);
        }

        String text = normalize(raw);
        if (text.isEmpty()) {
            throw new PdfImportException(
                    "The PDF contains no text. Scanned or image-only PDFs are not supported.");
        }
        if (text.length() > maxTextCharacters) {
            throw new PdfImportException("The PDF's text is too long (" + text.length()
                    + " characters; the limit is " + maxTextCharacters
                    + "). Use a shorter PDF.");
        }
        return text;
    }

    private static boolean hasPdfHeader(Path file) throws PdfImportException {
        try (InputStream in = Files.newInputStream(file)) {
            String start = new String(in.readNBytes(HEADER_SEARCH_BYTES), StandardCharsets.ISO_8859_1);
            return start.contains("%PDF-");
        } catch (IOException e) {
            throw new PdfImportException("The file could not be read: " + e.getMessage(), e);
        }
    }

    // Unix line endings, no trailing spaces, at most one blank line in a row.
    private static String normalize(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replaceAll("[ \\t]+\\n", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }
}   
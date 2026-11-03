package ca.yorku.eecs3311.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfImporterTest {
    @TempDir
    Path directory;

    private final PdfImporter importer = new PdfImporter();

    // Writes a PDF with one page per list entry; an empty string makes a blank page.
    private Path pdf(String name, String... pages) throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!text.isEmpty()) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(PDType1Font.HELVETICA, 12);
                        content.newLineAtOffset(50, 700);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    private Path protectedPdf(String name, boolean requirePassword, boolean allowCopy)
            throws IOException {
        Path file = directory.resolve(name);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(50, 700);
                content.showText("Secret text");
                content.endText();
            }
            AccessPermission permission = new AccessPermission();
            permission.setCanExtractContent(allowCopy);
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password", requirePassword ? "user-password" : "", permission);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(file.toFile());
        }
        return file;
    }

    private String messageOf(Path file) {
        return assertThrows(PdfImportException.class, () -> importer.extractText(file))
                .getMessage();
    }

    @Test
    void extractsTextFromEveryPage() throws Exception {
        Path file = pdf("notes.pdf", "Binary search halves the range.", "Heaps are trees.");

        String text = importer.extractText(file);

        assertTrue(text.contains("Binary search halves the range."));
        assertTrue(text.contains("Heaps are trees."));
        assertFalse(text.endsWith("\n"));
        assertFalse(text.contains("\r"));
    }

    @Test
    void uppercaseExtensionIsAccepted() throws Exception {
        Path file = pdf("NOTES.PDF", "Some text");

        assertTrue(importer.extractText(file).contains("Some text"));
    }

    @Test
    void missingNullAndNonPdfFilesAreRejectedWithExplanations() throws IOException {
        Path text = directory.resolve("notes.txt");
        Files.writeString(text, "plain text", StandardCharsets.UTF_8);

        assertTrue(messageOf(null).contains("No file was selected"));
        assertTrue(messageOf(directory.resolve("missing.pdf")).contains("could not be found"));
        assertTrue(messageOf(text).contains("Only PDF files"));
        assertTrue(messageOf(directory).contains("could not be found"));
    }

    @Test
    void emptyFilesAndFilesWithoutAPdfHeaderAreRejected() throws IOException {
        Path empty = directory.resolve("empty.pdf");
        Files.write(empty, new byte[0]);
        Path fake = directory.resolve("fake.pdf");
        Files.writeString(fake, "This is not a PDF at all.", StandardCharsets.UTF_8);

        assertTrue(messageOf(empty).contains("empty"));
        assertTrue(messageOf(fake).contains("not a valid PDF"));
    }

    @Test
    void oversizedFilesAreRejectedBeforeReading() throws Exception {
        Path file = pdf("big.pdf", "Some text");
        PdfImporter tiny = new PdfImporter(100, 1000);

        PdfImportException error = assertThrows(PdfImportException.class,
                () -> tiny.validateFile(file));

        assertTrue(error.getMessage().contains("too large"));
    }

    @Test
    void corruptedPdfsAreRejected() throws IOException {
        Path file = directory.resolve("broken.pdf");
        Files.writeString(file, "%PDF-1.4\nthis is not a real document body\n",
                StandardCharsets.UTF_8);

        assertTrue(messageOf(file).contains("could not be read"));
    }

    @Test
    void passwordProtectedPdfsAreRejected() throws IOException {
        assertTrue(messageOf(protectedPdf("locked.pdf", true, true))
                .contains("password-protected"));
    }

    @Test
    void pdfsThatForbidCopyingTextAreRejected() throws IOException {
        assertTrue(messageOf(protectedPdf("nocopy.pdf", false, false))
                .contains("does not allow its text to be copied"));
    }

    @Test
    void scannedOrBlankPdfsAreRejected() throws IOException {
        assertTrue(messageOf(pdf("blank.pdf", "")).contains("contains no text"));
    }

    @Test
    void textOverTheLimitAsksForAShorterPdf() throws Exception {
        Path file = pdf("long.pdf", "x".repeat(200));
        PdfImporter small = new PdfImporter(PdfImporter.DEFAULT_MAX_FILE_BYTES, 100);

        PdfImportException error = assertThrows(PdfImportException.class,
                () -> small.extractText(file));

        assertTrue(error.getMessage().contains("too long"));
        assertTrue(error.getMessage().contains("shorter PDF"));
    }

    @Test
    void textExactlyAtTheLimitIsAccepted() throws Exception {
        Path file = pdf("exact.pdf", "x".repeat(100));
        PdfImporter exact = new PdfImporter(PdfImporter.DEFAULT_MAX_FILE_BYTES, 100);

        assertEquals(100, exact.extractText(file).length());
    }

    @Test
    void limitsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new PdfImporter(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new PdfImporter(10, 0));
        assertEquals(PdfImporter.DEFAULT_MAX_TEXT_CHARACTERS, importer.getMaxTextCharacters());
    }
}
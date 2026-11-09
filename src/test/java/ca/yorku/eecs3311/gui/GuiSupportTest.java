package ca.yorku.eecs3311.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.agent.AgentException;
import ca.yorku.eecs3311.material.PdfImportException;
import ca.yorku.eecs3311.quiz.QuizException;
import ca.yorku.eecs3311.review.ReviewException;
import ca.yorku.eecs3311.syllabus.SyllabusException;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Tests the GUI's input parsing and error wording; these need no display. */
class GuiSupportTest {
    @Test
    void dateTimesAcceptDateAndTimeOrJustADate() {
        assertEquals(LocalDateTime.of(2030, 5, 1, 17, 30), GuiSupport.parseDateTime("2030-05-01 17:30"));
        assertEquals(LocalDateTime.of(2030, 5, 1, 23, 59), GuiSupport.parseDateTime(" 2030-05-01 "));
    }

    @Test
    void badDateTimesGiveAFriendlyMessage() {
        for (String text : new String[] {"", "soon", "2030-13-01 10:00", "01/05/2030 10:00", null}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> GuiSupport.parseDateTime(text));
            assertTrue(error.getMessage().contains("yyyy-MM-dd HH:mm"));
        }
    }

    @Test
    void timesDatesNumbersAndDaysAreParsed() {
        assertEquals(LocalTime.of(9, 5), GuiSupport.parseTime("09:05"));
        assertEquals(LocalDate.of(2030, 5, 1), GuiSupport.parseDate("2030-05-01"));
        assertEquals(42, GuiSupport.parseNumber(" 42 ", "minutes"));
        assertEquals(DayOfWeek.WEDNESDAY, GuiSupport.parseDay("wed"));
        assertEquals(DayOfWeek.MONDAY, GuiSupport.parseDay("Monday"));
    }

    @Test
    void badTimesDatesNumbersAndDaysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> GuiSupport.parseTime("9am"));
        assertThrows(IllegalArgumentException.class, () -> GuiSupport.parseTime(null));
        assertThrows(IllegalArgumentException.class, () -> GuiSupport.parseDate("tomorrow"));
        assertThrows(IllegalArgumentException.class, () -> GuiSupport.parseNumber("lots", "minutes"));
        assertThrows(IllegalArgumentException.class, () -> GuiSupport.parseNumber(null, "minutes"));
        assertThrows(IllegalArgumentException.class, () -> GuiSupport.parseDay("x"));
        assertThrows(IllegalArgumentException.class, () -> GuiSupport.parseDay("Sa"));
    }

    @Test
    void errorsAreWordedForTheStudent() {
        assertEquals("Fix this.", GuiSupport.describe(new IllegalArgumentException("Fix this.")));
        assertEquals("Not now.", GuiSupport.describe(new IllegalStateException("Not now.")));
        assertTrue(GuiSupport.describe(new SyllabusException("no AI")).contains("No tasks were saved"));
        assertTrue(GuiSupport.describe(new PdfImportException("scanned")).contains("No material was saved"));
        assertTrue(GuiSupport.describe(new QuizException("bad")).contains("Nothing was saved"));
        assertTrue(GuiSupport.describe(new ReviewException("bad")).contains("Nothing was changed"));
        assertTrue(GuiSupport.describe(new AgentException("bad")).contains("Nothing was changed"));
        assertTrue(GuiSupport.describe(new IOException("disk full")).contains("disk full"));
        assertTrue(GuiSupport.describe(new NullPointerException()).startsWith("Unexpected problem"));
    }

    @Test
    void htmlTextIsEscaped() {
        assertEquals("a &lt;b&gt; &amp; c", GuiSupport.html("a <b> & c"));
    }

    @Test
    void guardReportsFailuresThroughTheDialogs() {
        java.util.List<String> errors = new java.util.ArrayList<>();
        Dialogs original = GuiSupport.dialogs;
        GuiSupport.dialogs = new Dialogs() {
            public void info(java.awt.Component o, String m) { }
            public void error(java.awt.Component o, String m) { errors.add(m); }
            public boolean confirm(java.awt.Component o, String m) { return true; }
            public String[] form(java.awt.Component o, String t, String[] l, String[] i) { return null; }
            public java.nio.file.Path choosePdf(java.awt.Component o) { return null; }
            public void showText(java.awt.Component o, String t, String x) { }
        };
        try {
            GuiSupport.guard(null, () -> {
                throw new IllegalStateException("Select a task first.");
            });
            GuiSupport.guard(null, () -> { });
        } finally {
            GuiSupport.dialogs = original;
        }

        assertEquals(java.util.List.of("Select a task first."), errors);
    }
}
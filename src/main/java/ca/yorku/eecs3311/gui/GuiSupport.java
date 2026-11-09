package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.agent.AgentException;
import ca.yorku.eecs3311.material.PdfImportException;
import ca.yorku.eecs3311.quiz.QuizException;
import ca.yorku.eecs3311.review.ReviewException;
import ca.yorku.eecs3311.syllabus.SyllabusException;
import java.awt.Component;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** Shared helpers for the GUI: input parsing, error wording and background work. */
final class GuiSupport {
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    static Dialogs dialogs = new SwingDialogs();
    static JLabel statusLabel = new JLabel(" ");

    /** An action that may fail with any of the application's exceptions. */
    interface Action {
        void run() throws Exception;
    }

    private GuiSupport() {
    }

    // ===== Parsing =====

    /** "yyyy-MM-dd HH:mm", or just a date (meaning the end of that day, 23:59). */
    static LocalDateTime parseDateTime(String text) {
        String value = text == null ? "" : text.trim();
        try {
            if (value.length() <= 10) {
                return LocalDate.parse(value).atTime(23, 59);
            }
            return LocalDateTime.parse(value, DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Enter the date and time as yyyy-MM-dd HH:mm, for example 2026-11-20 17:00.");
        }
    }

    static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Enter the time as HH:mm, for example 09:30.");
        }
    }

    static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Enter the date as yyyy-MM-dd, for example 2026-11-16.");
        }
    }

    static int parseNumber(String text, String what) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid " + what + ".");
        }
    }

    static DayOfWeek parseDay(String text) {
        String value = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (value.length() >= 3 && day.name().startsWith(value)) {
                return day;
            }
        }
        throw new IllegalArgumentException("'" + text + "' is not a day of the week.");
    }

    // ===== Wording =====

    /** Turns any exception from the application into text a student can act on. */
    static String describe(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName()
                : error.getMessage();
        if (error instanceof SyllabusException) {
            return "Could not read tasks from the syllabus: " + message
                    + "\nNo tasks were saved. You can add tasks yourself on the Tasks tab.";
        }
        if (error instanceof PdfImportException) {
            return "Import failed: " + message + "\nNo material was saved.";
        }
        if (error instanceof AgentException) {
            return "The AI assistant could not produce a plan: " + message
                    + "\nNothing was changed.";
        }
        if (error instanceof QuizException) {
            return "Could not create a quiz: " + message + "\nNothing was saved.";
        }
        if (error instanceof ReviewException) {
            return "Could not get recommendations: " + message + "\nNothing was changed.";
        }
        if (error instanceof IOException) {
            return "Could not save your data (" + message + ").";
        }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            return message;
        }
        return "Unexpected problem: " + message;
    }

    /** Escapes text for use inside the small HTML labels the GUI shows. */
    static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ===== Running things =====

    static void setStatus(String text) {
        statusLabel.setText(text == null || text.isBlank() ? " " : text);
    }

    /** Runs a quick action on the calling thread and reports any failure in a dialog. */
    static void guard(Component owner, Action action) {
        try {
            action.run();
        } catch (Exception e) {
            dialogs.error(owner, describe(e));
        }
    }

    /**
     * Runs slow work (the AI calls) off the screen thread so the window stays responsive,
     * then hands the result to {@code onDone} on the screen thread.
     */
    static <T> void runAsync(Component owner, String busyText, Callable<T> work,
                             Consumer<T> onDone, AbstractButton... disabledWhileBusy) {
        for (AbstractButton button : disabledWhileBusy) {
            button.setEnabled(false);
        }
        setStatus(busyText);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    setStatus(" ");
                    onDone.accept(result);
                } catch (ExecutionException e) {
                    setStatus("The last request failed.");
                    dialogs.error(owner, describe(e.getCause()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    dialogs.error(owner, describe(e));
                } finally {
                    for (AbstractButton button : disabledWhileBusy) {
                        button.setEnabled(true);
                    }
                }
            }
        }.execute();
    }

    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
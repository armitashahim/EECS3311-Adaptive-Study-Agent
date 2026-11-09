package ca.yorku.eecs3311.gui;

import java.awt.Component;
import java.nio.file.Path;

/** Everything the GUI asks of the user, behind one interface so it can be replaced in tests. */
interface Dialogs {
    void info(Component owner, String message);

    void error(Component owner, String message);

    boolean confirm(Component owner, String message);

    /** Shows labelled text fields; returns the entered values, or null if cancelled. */
    String[] form(Component owner, String title, String[] labels, String[] initial);

    /** Lets the user pick a PDF file; returns null if cancelled. */
    Path choosePdf(Component owner);

    void showText(Component owner, String title, String text);
}
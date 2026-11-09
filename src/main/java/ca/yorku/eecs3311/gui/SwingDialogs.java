package ca.yorku.eecs3311.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

/** The real dialogs, built from standard Swing option panes. */
final class SwingDialogs implements Dialogs {
    @Override
    public void info(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Adaptive Study Agent",
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void error(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Something went wrong",
                JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public boolean confirm(Component owner, String message) {
        return JOptionPane.showConfirmDialog(owner, message, "Please confirm",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    @Override
    public String[] form(Component owner, String title, String[] labels, String[] initial) {
        JPanel panel = new JPanel(new GridBagLayout());
        JTextField[] fields = new JTextField[labels.length];
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < labels.length; i++) {
            c.gridx = 0;
            c.gridy = i;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            panel.add(new JLabel(labels[i]), c);
            fields[i] = new JTextField(initial == null ? "" : initial[i], 24);
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            panel.add(fields[i], c);
        }
        int answer = JOptionPane.showConfirmDialog(owner, panel, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        String[] values = new String[labels.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = fields[i].getText().trim();
        }
        return values;
    }

    @Override
    public Path choosePdf(Component owner) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF files", "pdf"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }

    @Override
    public void showText(Component owner, String title, String text) {
        JTextArea area = new JTextArea(text, 18, 60);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JPanel panel = new JPanel(new BorderLayout());
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(620, 360));
        panel.add(scroll, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(owner, panel, title, JOptionPane.PLAIN_MESSAGE);
    }
}
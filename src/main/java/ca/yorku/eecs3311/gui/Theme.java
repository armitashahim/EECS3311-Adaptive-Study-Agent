package ca.yorku.eecs3311.gui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/** Colours and fonts for the window. Call {@link #apply()} before creating any component. */
final class Theme {
    static final Color ACCENT = new Color(0x3F4FA8);
    static final Color ACCENT_DARK = new Color(0x2E3A82);
    static final Color BACKGROUND = new Color(0xF4F6FB);
    static final Color STRIPE = new Color(0xEEF1FA);
    static final Color BORDER = new Color(0xD5DAEA);
    static final Font BASE = new Font("Segoe UI", Font.PLAIN, 14);

    private Theme() {
    }

    static void apply() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
            // The default look and feel is fine.
        }
        UIManager.put("defaultFont", new FontUIResource(BASE));
        UIManager.put("nimbusBase", new ColorUIResource(ACCENT));
        UIManager.put("nimbusFocus", new ColorUIResource(new Color(0x7C8AE0)));
        UIManager.put("nimbusSelectionBackground", new ColorUIResource(new Color(0x5C6BC0)));
        UIManager.put("nimbusSelectedText", new ColorUIResource(Color.WHITE));
        UIManager.put("control", new ColorUIResource(BACKGROUND));
        UIManager.put("Table.alternateRowColor", new ColorUIResource(STRIPE));
        UIManager.put("Table.background", new ColorUIResource(Color.WHITE));
        UIManager.put("Table.showGrid", Boolean.FALSE);
        UIManager.put("TabbedPane.contentMargins", new java.awt.Insets(6, 6, 6, 6));
        UIManager.put("TabbedPane.tabAreaInsets", new java.awt.Insets(4, 8, 0, 8));
    }
}
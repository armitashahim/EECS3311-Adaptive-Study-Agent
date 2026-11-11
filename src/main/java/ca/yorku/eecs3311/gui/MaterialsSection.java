package ca.yorku.eecs3311.gui;

import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.CourseMaterial;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** F08: import text-based PDFs as course materials and view or remove them. */
final class MaterialsSection extends Section {
    private final DefaultTableModel model = GuiSupport.readOnlyModel("Title", "Characters of text");
    private final JTable table = table(model);
    private List<CourseMaterial> shown = new ArrayList<>();

    MaterialsSection(StudyApplication app, Clock clock, Supplier<Course> course, Runnable refreshAll) {
        super(app, clock, course, refreshAll);
        JButton importButton = new JButton("Import PDF...");
        JButton view = new JButton("View text...");
        JButton remove = new JButton("Remove");
        importButton.setName("material-import");
        importButton.addActionListener(e -> GuiSupport.guard(this, this::importMaterial));
        view.addActionListener(e -> GuiSupport.guard(this, this::viewMaterial));
        remove.addActionListener(e -> GuiSupport.guard(this, this::removeMaterial));
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons(importButton, view, remove), BorderLayout.SOUTH);
    }

    @Override
    void refresh() {
        model.setRowCount(0);
        Course course = course();
        shown = course == null ? new ArrayList<>() : new ArrayList<>(app.getMaterials(course.getId()));
        for (CourseMaterial material : shown) {
            model.addRow(new Object[] {material.getTitle(), material.getText().length()});
        }
    }

    private CourseMaterial selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            throw new IllegalStateException("Select a material in the table first.");
        }
        return shown.get(row);
    }

    private void importMaterial() throws Exception {
        Course course = requireCourse();
        Path pdf = GuiSupport.dialogs.choosePdf(this);
        if (pdf == null) {
            return;
        }
        String suggested = pdf.getFileName().toString().replaceFirst("(?i)\\.pdf$", "");
        String[] v = GuiSupport.dialogs.form(this, "Material title", new String[] {"Title"},
                new String[] {suggested});
        if (v == null) {
            return;
        }
        CourseMaterial material = app.importMaterial(course.getId(), v[0], pdf);
        GuiSupport.dialogs.info(this, "Imported '" + material.getTitle() + "' ("
                + material.getText().length() + " characters).");
        changed();
    }

    private void viewMaterial() {
        CourseMaterial material = selected();
        GuiSupport.dialogs.showText(this, material.getTitle(),
                app.getMaterial(material.getId()).getText());
    }

    private void removeMaterial() throws Exception {
        CourseMaterial material = selected();
        if (GuiSupport.dialogs.confirm(this, "Delete the material '" + material.getTitle() + "'?")) {
            app.removeMaterial(material.getId());
            changed();
        }
    }
}
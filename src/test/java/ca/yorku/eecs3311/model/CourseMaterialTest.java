package ca.yorku.eecs3311.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CourseMaterialTest {
    private final Course course = new Course("Algorithms");

    @Test
    void newMaterialGetsAnIdAndTrimsItsTitle() {
        CourseMaterial material = new CourseMaterial(course, "  Lecture 1  ", "Sorting basics");

        assertEquals("Lecture 1", material.getTitle());
        assertEquals("Sorting basics", material.getText());
        assertEquals(course.getId(), material.getCourse().getId());
        assertNotEquals(material.getId(),
                new CourseMaterial(course, "Lecture 1", "Sorting basics").getId());
    }

    @Test
    void restoredMaterialKeepsItsId() {
        CourseMaterial material = new CourseMaterial("m1", course, "Notes", "Text");

        assertEquals("m1", material.getId());
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CourseMaterial(null, "T", "x"));
        assertThrows(IllegalArgumentException.class, () -> new CourseMaterial(course, null, "x"));
        assertThrows(IllegalArgumentException.class, () -> new CourseMaterial(course, " ", "x"));
        assertThrows(IllegalArgumentException.class, () -> new CourseMaterial(course, "T", null));
        assertThrows(IllegalArgumentException.class, () -> new CourseMaterial(course, "T", "  "));
        assertThrows(IllegalArgumentException.class,
                () -> new CourseMaterial(" ", course, "T", "x"));
    }
}
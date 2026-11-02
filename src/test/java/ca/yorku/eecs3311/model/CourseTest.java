package ca.yorku.eecs3311.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CourseTest {

    @Test
    void createsCourseWithNonblankGeneratedId() {
        Course course = new Course("Algorithms");

        assertNotNull(course.getId());
        assertFalse(course.getId().isBlank());
    }

    @Test
    void generatesDifferentIdsForNewCourses() {
        Course first = new Course("Algorithms");
        Course second = new Course("Databases");

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void restoresCourseWithSuppliedId() {
        Course course = new Course("course-123", "Algorithms");

        assertEquals("course-123", course.getId());
    }

    @Test
    void trimsLeadingAndTrailingSpacesFromName() {
        Course course = new Course("  Algorithms  ");

        assertEquals("Algorithms", course.getName());
    }

    @Test
    void renameChangesNameAndPreservesId() {
        Course course = new Course("course-123", "Algorithms");

        course.rename("  Data Structures  ");

        assertEquals("Data Structures", course.getName());
        assertEquals("course-123", course.getId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", " \t "})
    void rejectsNullOrBlankNamesOnCreation(String name) {
        assertThrows(IllegalArgumentException.class, () -> new Course(name));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", " \t "})
    void rejectsNullOrBlankNamesWhenRenaming(String name) {
        Course course = new Course("Algorithms");

        assertThrows(IllegalArgumentException.class, () -> course.rename(name));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", " \t "})
    void rejectsNullOrBlankSuppliedIds(String id) {
        assertThrows(IllegalArgumentException.class, () -> new Course(id, "Algorithms"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", " \t "})
    void failedRenameLeavesPreviousNameUnchanged(String name) {
        Course course = new Course("Algorithms");

        assertThrows(IllegalArgumentException.class, () -> course.rename(name));

        assertEquals("Algorithms", course.getName());
    }
}

package ca.yorku.eecs3311.model;

import java.util.UUID;

/** Study material for a course: a title and the text extracted from its PDF. */
public class CourseMaterial {
    private final String id;
    private final Course course;
    private final String title;
    private final String text;

    // Create a new material.
    public CourseMaterial(Course course, String title, String text) {
        this(UUID.randomUUID().toString(), course, title, text);
    }

    // Restore a material from saved data.
    public CourseMaterial(String id, Course course, String title, String text) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Material ID cannot be empty.");
        }

        if (course == null) {
            throw new IllegalArgumentException("A material must belong to a course.");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Material title cannot be empty.");
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Material text cannot be empty.");
        }

        this.id = id;
        this.course = course;
        this.title = title.strip();
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }
}
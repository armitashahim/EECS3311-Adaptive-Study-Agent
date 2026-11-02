package ca.yorku.eecs3311.model;

import java.util.UUID;

public class Course {
    private final String id;
    private String name;

    public Course(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public Course(String id, String name) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Course ID cannot be empty.");
        }

        this.id = id;
        rename(name);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Course name cannot be empty.");
        }

        this.name = name.strip();
    }
}
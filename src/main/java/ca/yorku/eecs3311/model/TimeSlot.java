package ca.yorku.eecs3311.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** A concrete block of time, used by the Scheduler for available study windows. */
public class TimeSlot {
    private final LocalDateTime start;
    private final LocalDateTime end;

    public TimeSlot(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException(
                    "A time slot needs a start and an end.");
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "A time slot must end after it starts.");
        }

        this.start = start;
        this.end = end;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public int getDurationMinutes() {
        return (int) Duration.between(start, end).toMinutes();
    }
}
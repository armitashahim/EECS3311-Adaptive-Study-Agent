package ca.yorku.eecs3311.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Locale;
import java.util.UUID;

/** A recurring weekly period: either study availability or a fixed commitment. */
public class WeeklyPeriod {
    public static final String AVAILABILITY = "availability";
    public static final String COMMITMENT = "commitment";

    private final String id;
    private final DayOfWeek dayOfWeek;
    private final String type;

    private LocalTime startTime;
    private LocalTime endTime;

    // Create a new period.
    public WeeklyPeriod(DayOfWeek dayOfWeek, LocalTime startTime,
                        LocalTime endTime, String type) {
        this(UUID.randomUUID().toString(), dayOfWeek, startTime, endTime, type);
    }

    // Restore a period from saved data (also used to edit a period and keep its id).
    public WeeklyPeriod(String id, DayOfWeek dayOfWeek, LocalTime startTime,
                        LocalTime endTime, String type) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Period ID cannot be empty.");
        }

        if (dayOfWeek == null) {
            throw new IllegalArgumentException(
                    "Period day of week is required.");
        }

        String normalizedType = normalizeType(type);
        requireValidTimes(startTime, endTime);

        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.type = normalizedType;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void updateTimes(LocalTime start, LocalTime end) {
        requireValidTimes(start, end);

        this.startTime = start;
        this.endTime = end;
    }

    public boolean isAvailability() {
        return AVAILABILITY.equals(type);
    }

    // Two periods overlap when they share part of the same day.
    public boolean overlaps(WeeklyPeriod other) {
        if (other == null || dayOfWeek != other.dayOfWeek) {
            return false;
        }

        return startTime.isBefore(other.endTime)
                && other.startTime.isBefore(endTime);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Period type cannot be empty.");
        }

        String normalized = type.strip().toLowerCase(Locale.ROOT);
        if (!normalized.equals(AVAILABILITY) && !normalized.equals(COMMITMENT)) {
            throw new IllegalArgumentException(
                    "Period type must be availability or commitment.");
        }
        return normalized;
    }

    private static void requireValidTimes(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException(
                    "Period start and end times are required.");
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "Period end time must be after its start time.");
        }
    }

    public String getId() {
        return id;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getType() {
        return type;
    }
}

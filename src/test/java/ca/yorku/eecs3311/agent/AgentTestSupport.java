package ca.yorku.eecs3311.agent;

import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.TimeSlot;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Shared builders for the agent tests. */
final class AgentTestSupport {
    static final LocalDate WEEK = LocalDate.of(2030, 5, 13);
    static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 13, 8, 0);
    static final LocalDateTime FAR = LocalDateTime.of(2030, 5, 30, 23, 59);
    static final Course COURSE = new Course("Algorithms");

    private AgentTestSupport() {
    }

    static AcademicTask task(String title, int minutes, LocalDateTime deadline, int priority) {
        return new AcademicTask(COURSE, title, "Coursework", deadline, priority, minutes);
    }

    static TimeSlot slot(int dayOfMay, int startHour, int endHour) {
        return new TimeSlot(LocalDateTime.of(2030, 5, dayOfMay, startHour, 0),
                LocalDateTime.of(2030, 5, dayOfMay, endHour, 0));
    }

    static List<WeeklyPeriod> periods() {
        return List.of(
                new WeeklyPeriod(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0),
                        "availability"),
                new WeeklyPeriod(DayOfWeek.TUESDAY, LocalTime.of(13, 0), LocalTime.of(14, 0),
                        "commitment"));
    }

    static PlanningContext context(List<AcademicTask> tasks, List<TimeSlot> slots) {
        return new PlanningContext(WEEK, NOW, false, tasks, periods(), slots, Map.of());
    }
}
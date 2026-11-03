package ca.yorku.eecs3311.cli;

import static ca.yorku.eecs3311.application.ScriptedLlm.done;
import static ca.yorku.eecs3311.application.ScriptedLlm.schedule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.eecs3311.application.ScriptedLlm;
import ca.yorku.eecs3311.application.StudyApplication;
import ca.yorku.eecs3311.model.AcademicTask;
import ca.yorku.eecs3311.model.Course;
import ca.yorku.eecs3311.model.StudyPlan;
import ca.yorku.eecs3311.model.StudySession;
import ca.yorku.eecs3311.model.WeeklyPeriod;
import ca.yorku.eecs3311.repository.LocalRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyCLIPlanningTest {
    private static final LocalDate WEEK = LocalDate.of(2030, 5, 13);
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 15, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @TempDir
    Path directory;

    private LocalRepository repository;
    private AcademicTask essay;

    @BeforeEach
    void setUp() throws IOException {
        repository = new LocalRepository(directory.resolve("repository.json"));
        Course course = new Course("Algorithms");
        repository.saveCourse(course);
        essay = new AcademicTask(course, "Essay", "Coursework",
                LocalDateTime.of(2030, 5, 30, 23, 59), 2, 120);
        repository.saveTask(essay);
        repository.savePeriod(new WeeklyPeriod(DayOfWeek.THURSDAY, LocalTime.of(9, 0),
                LocalTime.of(12, 0), "availability"));
    }

    private String run(ScriptedLlm llm, String... lines) throws IOException {
        StudyApplication application = new StudyApplication(repository, CLOCK, llm);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(
                (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        new StudyCLI(application, in, out, CLOCK).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void generatedPlanIsShownButOnlySavedWhenAccepted() throws IOException {
        ScriptedLlm llm = new ScriptedLlm(schedule(essay.getId()), done("The essay is first."));

        String out = run(llm, "plan-generate", "plan", "plan-accept", "plan");

        assertTrue(out.contains("Proposed plan (not saved yet):"));
        assertTrue(out.contains("Why: The essay is first."));
        assertTrue(out.contains("1. Essay  2030-05-16 09:00 to 10:30  [scheduled]"));
        assertTrue(out.contains("Type plan-accept to save it or plan-reject to discard it."));
        assertTrue(out.contains("No plan for the week of 2030-05-13."));
        assertTrue(out.contains("Plan saved."));
        assertTrue(out.contains("Week of 2030-05-13 (accepted)"));
        assertEquals(1, repository.getPlans(WEEK).size());
    }

    @Test
    void rejectingDiscardsTheProposal() throws IOException {
        ScriptedLlm llm = new ScriptedLlm(schedule(essay.getId()), done("ok"));

        String out = run(llm, "plan-generate", "plan-reject", "plan-accept");

        assertTrue(out.contains("Proposal discarded. Your saved plan was not changed."));
        assertTrue(out.contains("There is no proposal to accept."));
        assertTrue(repository.getPlans(WEEK).isEmpty());
    }

    @Test
    void acceptingOrRejectingWithoutAProposalIsAnError() throws IOException {
        String out = run(null, "plan-accept", "plan-reject");

        assertTrue(out.contains("There is no proposal to accept."));
        assertTrue(out.contains("There is no proposal to discard."));
    }

    @Test
    void missingAiConfigurationIsExplained() throws IOException {
        String out = run(null, "plan-generate");

        assertTrue(out.contains("not configured"));
        assertTrue(repository.getPlans(WEEK).isEmpty());
    }

    @Test
    void aFailingAssistantChangesNothing() throws IOException {
        String out = run(new ScriptedLlm(), "plan-generate", "plan-accept");

        assertTrue(out.contains("The AI assistant could not produce a plan"));
        assertTrue(out.contains("Nothing was changed."));
        assertTrue(out.contains("There is no proposal to accept."));
        assertTrue(repository.getPlans(WEEK).isEmpty());
    }

    @Test
    void aProposalMadeStaleByNewDataIsRefused() throws IOException {
        ScriptedLlm llm = new ScriptedLlm(schedule(essay.getId()), done("ok"));

        String out = run(llm, "plan-generate", "task-add 1 Quiz Quiz 2030-05-30 23:59 2 30",
                "plan-accept");

        assertTrue(out.contains("Error: Your tasks, availability or plan changed"));
        assertFalse(out.contains("Plan saved."));
        assertTrue(repository.getPlans(WEEK).isEmpty());
    }

    @Test
    void unscheduledWorkIsListed() throws IOException {
        AcademicTask thesis = new AcademicTask(essay.getCourse(), "Thesis", "Coursework",
                LocalDateTime.of(2030, 5, 30, 23, 59), 3, 600);
        repository.saveTask(thesis);
        ScriptedLlm llm = new ScriptedLlm(schedule(thesis.getId(), essay.getId()),
                done("Thesis first; not everything fits."));

        String out = run(llm, "plan-generate");

        assertTrue(out.contains("Unscheduled: "));
        assertTrue(out.contains(" min"));
    }

    @Test
    void revisionKeepsPastSessionsAndIsAcceptedUnderTheSamePlan() throws IOException {
        repository.savePeriod(new WeeklyPeriod(DayOfWeek.FRIDAY, LocalTime.of(9, 0),
                LocalTime.of(12, 0), "availability"));
        StudyPlan plan = new StudyPlan(WEEK);
        plan.addSession(new StudySession(essay.getId(), LocalDateTime.of(2030, 5, 13, 9, 0),
                LocalDateTime.of(2030, 5, 13, 10, 0)));
        plan.addSession(new StudySession(essay.getId(), LocalDateTime.of(2030, 5, 16, 9, 0),
                LocalDateTime.of(2030, 5, 16, 10, 0)));
        plan.accept();
        repository.savePlan(plan);
        ScriptedLlm llm = new ScriptedLlm(schedule(essay.getId()), done("Rebuilt the week."));

        String out = run(llm, "plan-revise", "plan-accept", "plan");

        assertTrue(out.contains("Proposed revision (not saved yet):"));
        assertTrue(out.contains("Plan saved."));
        assertEquals(1, repository.getPlans(WEEK).size());
        assertEquals(plan.getId(), repository.getPlans(WEEK).get(0).getId());
        assertTrue(repository.getPlans(WEEK).get(0).getSessions().stream()
                .anyMatch(s -> s.getStartDateTime().equals(LocalDateTime.of(2030, 5, 13, 9, 0))));
        assertFalse(repository.getPlans(WEEK).get(0).needsRevision());
    }

    @Test
    void helpMentionsThePlanCommands() throws IOException {
        String out = run(null, "help");

        assertTrue(out.contains("plan-generate"));
        assertTrue(out.contains("plan-revise"));
        assertTrue(out.contains("plan-accept"));
        assertTrue(out.contains("plan-reject"));
    }
}
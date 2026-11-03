package ca.yorku.eecs3311.application;

import ca.yorku.eecs3311.model.StudyPlan;
import java.util.Set;

/**
 * A generated or revised plan that has not been saved yet. It is saved only by
 * {@link StudyApplication#acceptPlan(PlanProposal)}; discarding it changes nothing.
 *
 * @param plan           the proposed plan (for a revision: kept sessions plus new ones)
 * @param revision       true when it replaces future sessions of an existing plan
 * @param fingerprint    summary of the stored data it was based on, used to detect changes
 * @param newSessionIds  ids of the sessions the agent proposed (these are re-validated)
 */
public record PlanProposal(StudyPlan plan, boolean revision, String fingerprint,
                           Set<String> newSessionIds) {
    public PlanProposal {
        newSessionIds = Set.copyOf(newSessionIds);
    }
}
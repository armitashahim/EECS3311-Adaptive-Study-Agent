package ca.yorku.eecs3311.agent;

import ca.yorku.eecs3311.scheduling.ScheduleResult;

/** A validated proposal from the agent: the schedule and the model's explanation. */
public record AgentResult(ScheduleResult schedule, String explanation) {
}
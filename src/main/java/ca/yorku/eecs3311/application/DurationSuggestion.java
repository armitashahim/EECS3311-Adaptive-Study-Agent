package ca.yorku.eecs3311.application;

/**
 * A suggested task duration, with how many finished tasks could support it
 * and whether that history was actually used.
 */
public record DurationSuggestion(int minutes, int supportingTasks, boolean usedHistory) {
}
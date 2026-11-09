package ca.yorku.eecs3311.review;

/** A proposed review task for a weak topic. Nothing is saved until the student accepts it. */
public record ReviewRecommendation(TopicStats stats, String materialTitle, String title,
                                   String focus, int suggestedMinutes) {
}
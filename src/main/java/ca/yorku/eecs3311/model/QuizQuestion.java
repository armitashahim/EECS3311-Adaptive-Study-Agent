package ca.yorku.eecs3311.model;

import java.util.List;

/** One multiple-choice question: four choices, one correct, and an explanation. */
public class QuizQuestion {
    public static final int CHOICE_COUNT = 4;

    private final String text;
    private final List<String> choices;
    private final int correctIndex;
    private final String explanation;

    public QuizQuestion(String text, List<String> choices, int correctIndex, String explanation) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Question text cannot be empty.");
        }
        if (choices == null || choices.size() != CHOICE_COUNT) {
            throw new IllegalArgumentException("A question needs exactly " + CHOICE_COUNT + " choices.");
        }
        for (String choice : choices) {
            if (choice == null || choice.isBlank()) {
                throw new IllegalArgumentException("Choices cannot be empty.");
            }
        }
        if (choices.stream().map(c -> c.strip().toLowerCase()).distinct().count() != CHOICE_COUNT) {
            throw new IllegalArgumentException("The four choices must be different.");
        }
        if (correctIndex < 0 || correctIndex >= CHOICE_COUNT) {
            throw new IllegalArgumentException("The correct answer must be one of the four choices.");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("A question needs an explanation.");
        }
        this.text = text.strip();
        this.choices = choices.stream().map(String::strip).toList();
        this.correctIndex = correctIndex;
        this.explanation = explanation.strip();
    }

    public String getText() {
        return text;
    }

    public List<String> getChoices() {
        return choices;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public String getExplanation() {
        return explanation;
    }
}
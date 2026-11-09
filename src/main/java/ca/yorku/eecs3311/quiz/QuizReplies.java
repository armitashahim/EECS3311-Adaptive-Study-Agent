package ca.yorku.eecs3311.quiz;

import java.util.ArrayList;
import java.util.List;

/** Builds scripted AI replies for quiz tests. */
public final class QuizReplies {
    private QuizReplies() {
    }

    public static String question(String text, int correctIndex) {
        return "{\"question\":\"" + text + "\",\"choices\":[\"alpha\",\"beta\",\"gamma\",\"delta\"],"
                + "\"correctIndex\":" + correctIndex + ",\"explanation\":\"Because " + text + "\"}";
    }

    /** Five valid questions whose correct choices are 0, 1, 2, 3, 0 (A, B, C, D, A). */
    public static String valid() {
        return withCorrect(0, 1, 2, 3, 0);
    }

    public static String withCorrect(int... correct) {
        List<String> questions = new ArrayList<>();
        for (int i = 0; i < correct.length; i++) {
            questions.add(question("Question " + (i + 1), correct[i]));
        }
        return "{\"questions\":[" + String.join(",", questions) + "]}";
    }
}
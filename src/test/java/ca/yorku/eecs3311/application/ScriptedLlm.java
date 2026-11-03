package ca.yorku.eecs3311.application;

import ca.yorku.eecs3311.ai.LLMClient;
import ca.yorku.eecs3311.ai.LLMException;
import java.util.ArrayList;
import java.util.List;

/** Test double that replays scripted replies and records every prompt it receives. */
public class ScriptedLlm implements LLMClient {
    public final List<String> replies;
    public final List<String> prompts = new ArrayList<>();

    public ScriptedLlm(String... replies) {
        this.replies = new ArrayList<>(List.of(replies));
    }

    public static String schedule(String... taskIds) {
        StringBuilder list = new StringBuilder();
        for (String id : taskIds) {
            list.append(list.length() == 0 ? "" : ",").append('"').append(id).append('"');
        }
        return "{\"action\":\"tool\",\"tool\":\"create_schedule\",\"args\":{\"order\":["
                + list + "]}}";
    }

    public static String done(String explanation) {
        return "{\"action\":\"final\",\"explanation\":\"" + explanation + "\"}";
    }

    @Override
    public String generate(String prompt) throws LLMException {
        prompts.add(prompt);
        if (replies.isEmpty()) {
            throw new LLMException("No more scripted replies.");
        }
        return replies.remove(0);
    }
}
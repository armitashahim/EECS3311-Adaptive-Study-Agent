package ca.yorku.eecs3311.agent;

import com.google.gson.JsonObject;

/**
 * Command interface for an action the planning agent may request. Each tool wraps one
 * operation (reading study data, building a schedule) behind the same call, so the agent
 * loop can run any of them by name without knowing what they do.
 */
public interface AgentTool {
    /** The name the model uses to request this tool. */
    String getName();

    /** What the tool does and which arguments it takes, written for the model to read. */
    String getDescription();

    /**
     * Runs the tool.
     *
     * @return the result as JSON text, which is shown back to the model
     * @throws IllegalArgumentException if the arguments are missing or invalid
     */
    String execute(JsonObject arguments);
}
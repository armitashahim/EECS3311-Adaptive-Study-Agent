package ca.yorku.eecs3311.agent;

/** The planning agent could not produce a valid proposal; nothing should be saved. */
public class AgentException extends Exception {
    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
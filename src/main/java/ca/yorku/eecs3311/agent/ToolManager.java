package ca.yorku.eecs3311.agent;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Invoker for {@link AgentTool} commands: registers tools and runs them by name. */
public class ToolManager {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public void register(AgentTool tool) {
        if (tool == null || tool.getName() == null || tool.getName().isBlank()) {
            throw new IllegalArgumentException("A tool needs a name.");
        }
        if (tools.containsKey(tool.getName())) {
            throw new IllegalArgumentException("A tool named '" + tool.getName()
                    + "' is already registered.");
        }
        tools.put(tool.getName(), tool);
    }

    /** One line per tool, for inclusion in the prompt. */
    public String getToolDescriptions() {
        StringBuilder text = new StringBuilder();
        tools.values().forEach(tool -> text.append("- ").append(tool.getName()).append(": ")
                .append(tool.getDescription()).append('\n'));
        return text.toString();
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Runs the named tool.
     *
     * @throws IllegalArgumentException for an unknown tool or invalid arguments
     */
    public String executeTool(String name, JsonObject arguments) {
        AgentTool tool = name == null ? null : tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool '" + name
                    + "'. Available tools: " + String.join(", ", tools.keySet()) + ".");
        }
        return tool.execute(arguments == null ? new JsonObject() : arguments);
    }
}
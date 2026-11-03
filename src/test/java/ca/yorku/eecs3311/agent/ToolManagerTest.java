package ca.yorku.eecs3311.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ToolManagerTest {
    private static AgentTool echo(String name) {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "echoes " + name;
            }

            @Override
            public String execute(JsonObject arguments) {
                return name + ":" + arguments;
            }
        };
    }

    @Test
    void registeredToolsRunByName() {
        ToolManager manager = new ToolManager();
        manager.register(echo("alpha"));
        manager.register(echo("beta"));
        JsonObject args = new JsonObject();
        args.addProperty("x", 1);

        assertEquals("beta:{\"x\":1}", manager.executeTool("beta", args));
        assertTrue(manager.hasTool("alpha"));
        assertFalse(manager.hasTool("gamma"));
    }

    @Test
    void missingArgumentsBecomeAnEmptyObject() {
        ToolManager manager = new ToolManager();
        manager.register(echo("alpha"));

        assertEquals("alpha:{}", manager.executeTool("alpha", null));
    }

    @Test
    void unknownToolNamesAreRejectedWithTheAvailableTools() {
        ToolManager manager = new ToolManager();
        manager.register(echo("alpha"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> manager.executeTool("nope", new JsonObject()));

        assertTrue(error.getMessage().contains("Unknown tool 'nope'"));
        assertTrue(error.getMessage().contains("alpha"));
        assertThrows(IllegalArgumentException.class,
                () -> manager.executeTool(null, new JsonObject()));
    }

    @Test
    void duplicateAndInvalidToolsCannotBeRegistered() {
        ToolManager manager = new ToolManager();
        manager.register(echo("alpha"));

        assertThrows(IllegalArgumentException.class, () -> manager.register(echo("alpha")));
        assertThrows(IllegalArgumentException.class, () -> manager.register(null));
        assertThrows(IllegalArgumentException.class, () -> manager.register(echo(" ")));
    }

    @Test
    void descriptionsListEveryToolInRegistrationOrder() {
        ToolManager manager = new ToolManager();
        manager.register(echo("alpha"));
        manager.register(echo("beta"));

        assertEquals("- alpha: echoes alpha\n- beta: echoes beta\n", manager.getToolDescriptions());
    }
}
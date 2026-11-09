package ca.yorku.eecs3311.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class JsonRepliesTest {
    @Test
    void findsAnObjectInsideFencesAndText() {
        assertEquals("v", JsonReplies.extractObject("{\"k\":\"v\"}").get("k").getAsString());
        assertEquals("v", JsonReplies.extractObject("```json\n{\"k\":\"v\"}\n```").get("k").getAsString());
        assertEquals("v", JsonReplies.extractObject("Sure: {\"k\":\"v\"} done").get("k").getAsString());
    }

    @Test
    void returnsNullWhenThereIsNoValidObject() {
        assertNull(JsonReplies.extractObject(null));
        assertNull(JsonReplies.extractObject("no json"));
        assertNull(JsonReplies.extractObject("{ broken"));
        assertNull(JsonReplies.extractObject("} {"));
        assertNull(JsonReplies.extractObject(""));
    }

    @Test
    void textReturnsEmptyForMissingOrStructuredValues() {
        JsonObject object = JsonReplies.extractObject("{\"a\":\"x\",\"n\":5,\"o\":{},\"z\":null}");

        assertEquals("x", JsonReplies.text(object, "a"));
        assertEquals("5", JsonReplies.text(object, "n"));
        assertEquals("", JsonReplies.text(object, "o"));
        assertEquals("", JsonReplies.text(object, "z"));
        assertEquals("", JsonReplies.text(object, "missing"));
    }
}
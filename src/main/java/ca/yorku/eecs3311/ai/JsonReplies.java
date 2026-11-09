package ca.yorku.eecs3311.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/** Helpers for reading structured replies from a language model. */
public final class JsonReplies {
    private JsonReplies() {
    }

    /**
     * Finds the JSON object in a model reply, tolerating code fences and text around it.
     *
     * @return the object, or null if the reply holds no valid JSON object
     */
    public static JsonObject extractObject(String reply) {
        if (reply == null) {
            return null;
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(reply.substring(start, end + 1));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    /** The string value of a property, or "" if it is missing or not a string/number. */
    public static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }
}
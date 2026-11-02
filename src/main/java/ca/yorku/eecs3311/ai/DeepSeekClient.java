package ca.yorku.eecs3311.ai;

import ca.yorku.eecs3311.config.AppConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Adapter: converts LLMClient.generate(prompt) into a DeepSeek chat-completions
 * HTTP request and extracts the generated text from the response.
 */
public class DeepSeekClient implements LLMClient {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String modelName;
    private final Duration timeout;

    public static DeepSeekClient fromConfig(AppConfig config) {
        return new DeepSeekClient(config.getApiKey(), config.getBaseUrl(),
                config.getModel(), DEFAULT_TIMEOUT);
    }

    public DeepSeekClient(String apiKey, String baseUrl, String model, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key cannot be empty.");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Base URL cannot be empty.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name cannot be empty.");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive.");
        }

        String base = baseUrl.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(base + "/chat/completions");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Base URL is not a valid URL.", exception);
        }
        requireSecureTransport(uri);
        this.endpoint = uri;
        this.apiKey = apiKey;
        this.modelName = model.strip();
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    // The API key is sent in a header, so never allow it over plain HTTP,
    // except to a local machine (used by tests and local proxies).
    private static void requireSecureTransport(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || scheme == null) {
            throw new IllegalArgumentException("Base URL is not a valid URL.");
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return;
        }
        boolean loopback = "http".equalsIgnoreCase(scheme)
                && (host.equals("localhost") || host.equals("127.0.0.1")
                || host.equals("[::1]"));
        if (!loopback) {
            throw new IllegalArgumentException(
                    "Base URL must use https (plain http is allowed only for localhost).");
        }
    }

    @Override
    public String generate(String prompt) throws LLMException {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be empty.");
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildBody(prompt), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new LLMException("Could not reach the AI service.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LLMException("The AI request was interrupted.", exception);
        }

        if (response.statusCode() != 200) {
            // The body is not included: it could echo request details.
            throw new LLMException(
                    "AI service returned HTTP " + response.statusCode() + ".");
        }
        return extractText(response.body());
    }

    private String buildBody(String prompt) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject body = new JsonObject();
        body.addProperty("model", modelName);
        body.add("messages", messages);
        body.addProperty("stream", false);
        return body.toString();
    }

    static String extractText(String responseBody) throws LLMException {
        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            JsonArray choices = parsed.getAsJsonObject().getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new LLMException("AI response contained no choices.");
            }
            JsonElement content = choices.get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content");
            if (content == null || !content.isJsonPrimitive()
                    || content.getAsString().isBlank()) {
                throw new LLMException("AI response contained no text.");
            }
            return content.getAsString();
        } catch (JsonParseException | IllegalStateException | ClassCastException
                 | NullPointerException exception) {
            throw new LLMException("AI response was not in the expected format.", exception);
        }
    }
}
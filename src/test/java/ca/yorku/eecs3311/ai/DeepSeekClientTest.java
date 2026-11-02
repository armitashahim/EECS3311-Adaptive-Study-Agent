package ca.yorku.eecs3311.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DeepSeekClientTest {
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private DeepSeekClient client() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        return new DeepSeekClient("test-key", baseUrl, "deepseek-flash",
                Duration.ofSeconds(5));
    }

    @Test
    void sendsPromptAndReturnsGeneratedText() throws LLMException {
        responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Hello student\"}}]}";

        String result = client().generate("Say hello");

        assertEquals("Hello student", result);
        assertEquals("Bearer test-key", authHeader.get());
        JsonObject sent = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        assertEquals("deepseek-flash", sent.get("model").getAsString());
        assertEquals("Say hello", sent.getAsJsonArray("messages").get(0)
                .getAsJsonObject().get("content").getAsString());
    }

    @Test
    void nonSuccessStatusBecomesLlmException() {
        status = 401;
        responseBody = "{\"error\":\"bad key\"}";

        LLMException exception =
                assertThrows(LLMException.class, () -> client().generate("Hi"));

        assertTrue(exception.getMessage().contains("401"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "{\"choices\":[]}",
            "{\"choices\":[{\"message\":{}}]}",
            "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"})
    void malformedResponsesBecomeLlmException(String body) {
        responseBody = body;

        assertThrows(LLMException.class, () -> client().generate("Hi"));
    }

    @Test
    void unreachableServerBecomesLlmException() {
        DeepSeekClient unreachable = new DeepSeekClient("k", "http://127.0.0.1:1",
                "m", Duration.ofSeconds(2));

        assertThrows(LLMException.class, () -> unreachable.generate("Hi"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankPrompts(String prompt) {
        assertThrows(IllegalArgumentException.class, () -> client().generate(prompt));
    }

    @Test
    void constructorRejectsBlankSettings() {
        String url = "https://api.example.com";
        assertThrows(IllegalArgumentException.class,
                () -> new DeepSeekClient(" ", url, "m", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new DeepSeekClient("k", " ", "m", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new DeepSeekClient("k", url, " ", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new DeepSeekClient("k", url, "m", Duration.ZERO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://api.example.com", "ftp://api.example.com",
            "api.example.com", "not a url"})
    void rejectsBaseUrlsThatWouldNotProtectTheKey(String baseUrl) {
        assertThrows(IllegalArgumentException.class,
                () -> new DeepSeekClient("k", baseUrl, "m", Duration.ofSeconds(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://api.deepseek.com", "https://api.deepseek.com/",
            "http://localhost:8080", "http://127.0.0.1:8080", "http://[::1]:8080"})
    void acceptsHttpsAndLocalhostBaseUrls(String baseUrl) {
        new DeepSeekClient("k", baseUrl, "m", Duration.ofSeconds(1));
    }

    @Test
    void errorMessagesNeverContainTheApiKey() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";

        LLMException exception =
                assertThrows(LLMException.class, () -> client().generate("Hi"));

        assertTrue(!exception.getMessage().contains("test-key"));
    }
}
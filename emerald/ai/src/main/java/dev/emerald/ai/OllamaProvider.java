package dev.emerald.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local Ollama via {@code POST /api/chat} with {@code stream:false} and {@code format} set to the JSON
 * schema (structured outputs). Runs on its own daemon thread; never call {@code join()} from the server tick.
 */
public final class OllamaProvider implements AiProvider {
    private final AiConfig config;
    private final ExecutorService executor;
    private final HttpClient http;

    public OllamaProvider(AiConfig config) {
        this.config = config;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "emerald-ollama");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(executor)
                .build();
    }

    @Override
    public String name() {
        return "ollama:" + config.model();
    }

    @Override
    public CompletableFuture<String> completeJson(String jsonSchema, String systemPrompt, String userPrompt) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(stripSlash(config.baseUrl()) + "/api/chat"))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(config.model(), jsonSchema, systemPrompt, userPrompt)))
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(OllamaProvider::extractContent);
    }

    static String requestBody(String model, String jsonSchema, String systemPrompt, String userPrompt) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        body.add("format", JsonParser.parseString(jsonSchema));
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.2);
        body.add("options", options);
        return body.toString();
    }

    static String extractContent(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            throw new ProposalRejected("ollama HTTP " + response.statusCode());
        }
        JsonElement root = JsonParser.parseString(response.body());
        if (!root.isJsonObject() || !root.getAsJsonObject().has("message")) {
            throw new ProposalRejected("ollama response has no message");
        }
        JsonElement content = root.getAsJsonObject().getAsJsonObject("message").get("content");
        if (content == null || !content.isJsonPrimitive()) {
            throw new ProposalRejected("ollama message has no content");
        }
        return content.getAsString();
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}

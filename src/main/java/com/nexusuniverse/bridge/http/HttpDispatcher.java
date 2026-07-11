package com.nexusuniverse.bridge.http;

import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.json.Json;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class HttpDispatcher implements AutoCloseable {
    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final ThreadPoolExecutor executor;
    private final HttpClient client;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public HttpDispatcher(JavaPlugin plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.executor = new ThreadPoolExecutor(
            1,
            4,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(config.queueCapacity()),
            r -> {
                Thread t = new Thread(r, "NexusBridge-HTTP");
                t.setDaemon(true);
                return t;
            },
            (r, e) -> plugin.getLogger().warning("HTTP queue full; event dropped")
        );
        this.client = HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout())
            .executor(executor)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public void post(String endpoint, Map<String, Object> payload) {
        post(endpoint, payload, null);
    }

    public void post(String endpoint, Map<String, Object> payload, Consumer<Response> callback) {
        if (!config.enabled() || config.baseUrl().isBlank()) return;
        String body = Json.stringify(payload);
        if (config.logPayloads()) plugin.getLogger().info("POST " + endpoint + " " + body);
        sendAttempt(endpoint, body, 0, callback);
    }

    private void sendAttempt(String endpoint, String body, int attempt, Consumer<Response> callback) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(config.baseUrl() + endpoint))
            .timeout(config.requestTimeout())
            .header("Content-Type", "application/json")
            .header("User-Agent", "NexusBridge/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(body));

        String token = config.token();
        if (token != null && !token.isBlank() && !"CHANGE_ME".equals(token)) {
            builder.header("Authorization", "Bearer " + token);
            builder.header("X-Nexus-Token", token);
        }

        client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, error) -> {
                if (error != null || response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (attempt < config.maxRetries()) {
                        long delay = config.retryBaseDelayMs() * (1L << attempt);
                        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, executor)
                            .execute(() -> sendAttempt(endpoint, body, attempt + 1, callback));
                        return;
                    }
                    failed.incrementAndGet();
                    String message = error != null ? error.getMessage() : "HTTP " + response.statusCode();
                    plugin.getLogger().warning("POST failed endpoint=" + endpoint + " error=" + message);
                    invokeCallback(callback, new Response(false, response == null ? 0 : response.statusCode(), response == null ? "" : response.body(), message));
                    return;
                }

                sent.incrementAndGet();
                invokeCallback(callback, new Response(true, response.statusCode(), response.body(), ""));
            });
    }

    private void invokeCallback(Consumer<Response> callback, Response response) {
        if (callback == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(response));
    }

    public long sentCount() { return sent.get(); }
    public long failedCount() { return failed.get(); }
    public int queuedCount() { return executor.getQueue().size(); }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public record Response(boolean ok, int status, String body, String error) {
        public boolean bodySaysAccepted() {
            if (!ok || body == null) return false;
            String compact = body.replace(" ", "").replace("\n", "").toLowerCase();
            return compact.contains("\"accepted\":true") || compact.contains("\"ok\":true") && compact.contains("artifact_accepted");
        }
    }
}

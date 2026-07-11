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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class HttpDispatcher implements AutoCloseable {
    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final ThreadPoolExecutor executor;
    private final HttpClient client;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long circuitOpenUntil = 0L;
    private volatile long lastWarningAt = 0L;

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
            (r, e) -> {
                dropped.incrementAndGet();
                warnRateLimited("HTTP queue full; event dropped");
            }
        );
        this.client = HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout())
            .executor(executor)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public void get(String endpoint, Consumer<Response> callback) {
        if (!ready(callback)) return;
        HttpRequest.Builder builder = baseRequest(endpoint).GET();
        client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, error) -> complete(endpoint, response, error, callback));
    }

    public void post(String endpoint, Map<String, Object> payload) {
        post(endpoint, payload, null);
    }

    public void post(String endpoint, Map<String, Object> payload, Consumer<Response> callback) {
        if (!ready(callback)) return;
        String body = Json.stringify(payload);
        if (config.logPayloads()) plugin.getLogger().info("POST " + endpoint + " " + body);
        sendAttempt(endpoint, body, 0, callback);
    }

    private boolean ready(Consumer<Response> callback) {
        if (!config.enabled() || config.baseUrl().isBlank()) {
            invokeCallback(callback, new Response(false, 0, "", "bridge_disabled"));
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < circuitOpenUntil) {
            dropped.incrementAndGet();
            invokeCallback(callback, new Response(false, 0, "", "circuit_open"));
            return false;
        }
        return true;
    }

    private HttpRequest.Builder baseRequest(String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(config.baseUrl() + endpoint))
            .timeout(config.requestTimeout())
            .header("Content-Type", "application/json")
            .header("User-Agent", "NexusBridge/2.0");

        String token = config.token();
        if (token != null && !token.isBlank() && !"CHANGE_ME".equals(token)) {
            builder.header("Authorization", "Bearer " + token);
            builder.header("X-Nexus-Token", token);
        }
        return builder;
    }

    private void sendAttempt(String endpoint, String body, int attempt, Consumer<Response> callback) {
        HttpRequest request = baseRequest(endpoint)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete((response, error) -> {
                boolean bad = error != null || response == null || response.statusCode() < 200 || response.statusCode() >= 300;
                if (bad && attempt < config.maxRetries()) {
                    long delay = config.retryBaseDelayMs() * (1L << attempt);
                    CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, executor)
                        .execute(() -> sendAttempt(endpoint, body, attempt + 1, callback));
                    return;
                }
                complete(endpoint, response, error, callback);
            });
    }

    private void complete(String endpoint, HttpResponse<String> response, Throwable error, Consumer<Response> callback) {
        if (error != null || response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            failed.incrementAndGet();
            int failures = consecutiveFailures.incrementAndGet();
            String message = error != null ? error.getClass().getSimpleName() + ": " + error.getMessage()
                : "HTTP " + (response == null ? 0 : response.statusCode());
            if (failures >= config.failureThreshold()) {
                circuitOpenUntil = System.currentTimeMillis() + config.circuitOpenMs();
                warnRateLimited("Kairos unavailable; circuit paused for " + config.circuitOpenMs() + "ms. Last error=" + message);
            } else {
                warnRateLimited("Request failed endpoint=" + endpoint + " error=" + message);
            }
            invokeCallback(callback, new Response(false, response == null ? 0 : response.statusCode(), response == null ? "" : response.body(), message));
            return;
        }

        consecutiveFailures.set(0);
        circuitOpenUntil = 0L;
        sent.incrementAndGet();
        invokeCallback(callback, new Response(true, response.statusCode(), response.body(), ""));
    }

    private void warnRateLimited(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarningAt < config.warningCooldownMs()) return;
        lastWarningAt = now;
        plugin.getLogger().warning(message);
    }

    private void invokeCallback(Consumer<Response> callback, Response response) {
        if (callback == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(response));
    }

    public long sentCount() { return sent.get(); }
    public long failedCount() { return failed.get(); }
    public long droppedCount() { return dropped.get(); }
    public int queuedCount() { return executor.getQueue().size(); }
    public boolean circuitOpen() { return System.currentTimeMillis() < circuitOpenUntil; }
    public long circuitRemainingMs() { return Math.max(0L, circuitOpenUntil - System.currentTimeMillis()); }

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
            return compact.contains("\"accepted\":true")
                || (compact.contains("\"ok\":true") && compact.contains("artifact_accepted"));
        }
    }
}

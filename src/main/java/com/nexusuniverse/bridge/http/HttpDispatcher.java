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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class HttpDispatcher implements AutoCloseable {
    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final ExecutorService executor;
    private final HttpClient client;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long circuitOpenUntil;
    private volatile long lastWarningAt;

    public HttpDispatcher(JavaPlugin plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.executor = new ThreadPoolExecutor(
            2, 4, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(config.queueCapacity()),
            runnable -> {
                Thread thread = new Thread(runnable, "NexusBridge-HTTP");
                thread.setDaemon(true);
                return thread;
            },
            (runnable, pool) -> {
                dropped.incrementAndGet();
                warnRateLimited("HTTP queue full; request dropped.");
            }
        );
        this.client = HttpClient.newBuilder()
            .connectTimeout(config.connectTimeout())
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    public void get(String endpoint, Consumer<Response> callback) {
        if (!ready(callback)) return;
        HttpRequest request = requestBuilder(endpoint).GET().build();
        executor.execute(() -> send(request, endpoint, callback));
    }

    public void post(String endpoint, Map<String, Object> payload, Consumer<Response> callback) {
        if (!ready(callback)) return;
        String body = Json.stringify(payload);
        if (config.logPayloads()) plugin.getLogger().info("POST " + endpoint + " " + body);
        HttpRequest request = requestBuilder(endpoint)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        executor.execute(() -> send(request, endpoint, callback));
    }

    public void post(String endpoint, Map<String, Object> payload) {
        post(endpoint, payload, null);
    }

    private HttpRequest.Builder requestBuilder(String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(config.baseUrl() + endpoint))
            .timeout(config.requestTimeout())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "NexusBridge/3.0.0");
        String token = config.token();
        if (token != null && !token.isBlank() && !"CHANGE_ME".equals(token)) {
            builder.header("Authorization", "Bearer " + token);
            builder.header("X-Nexus-Token", token);
        }
        return builder;
    }

    private void send(HttpRequest request, String endpoint, Consumer<Response> callback) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                sent.incrementAndGet();
                consecutiveFailures.set(0);
                circuitOpenUntil = 0L;
                invoke(callback, new Response(true, response.statusCode(), response.body(), ""));
                return;
            }
            fail(endpoint, "HTTP " + response.statusCode(), response.statusCode(), response.body(), callback);
        } catch (Exception exception) {
            fail(endpoint, exception.getClass().getSimpleName() + ": " + exception.getMessage(), 0, "", callback);
        }
    }

    private void fail(String endpoint, String message, int status, String body, Consumer<Response> callback) {
        failed.incrementAndGet();
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= config.failureThreshold()) {
            circuitOpenUntil = System.currentTimeMillis() + config.circuitOpenMs();
            warnRateLimited("Kairos is unavailable; outbound requests paused for " + config.circuitOpenMs() + "ms.");
        } else {
            warnRateLimited("Request failed endpoint=" + endpoint + " error=" + message);
        }
        invoke(callback, new Response(false, status, body, message));
    }

    private boolean ready(Consumer<Response> callback) {
        if (!config.enabled() || config.baseUrl().isBlank()) {
            invoke(callback, new Response(false, 0, "", "bridge_disabled"));
            return false;
        }
        if (System.currentTimeMillis() < circuitOpenUntil) {
            dropped.incrementAndGet();
            invoke(callback, new Response(false, 0, "", "circuit_open"));
            return false;
        }
        return true;
    }

    private void warnRateLimited(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarningAt < config.warningCooldownMs()) return;
        lastWarningAt = now;
        plugin.getLogger().warning(message);
    }

    private void invoke(Consumer<Response> callback, Response response) {
        if (callback == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(response));
    }

    public long sentCount() { return sent.get(); }
    public long failedCount() { return failed.get(); }
    public long droppedCount() { return dropped.get(); }
    public int queuedCount() {
        return executor instanceof ThreadPoolExecutor pool ? pool.getQueue().size() : 0;
    }
    public boolean circuitOpen() { return System.currentTimeMillis() < circuitOpenUntil; }
    public long circuitRemainingMs() { return Math.max(0L, circuitOpenUntil - System.currentTimeMillis()); }

    @Override
    public void close() {
        executor.shutdownNow();
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

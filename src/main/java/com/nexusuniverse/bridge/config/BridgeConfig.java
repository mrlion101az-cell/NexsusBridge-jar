package com.nexusuniverse.bridge.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BridgeConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public BridgeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public boolean enabled() { return config.getBoolean("bridge.enabled", true); }
    public String baseUrl() {
        String value = config.getString("bridge.base-url", "").trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
    public String token() { return config.getString("bridge.auth-token", ""); }
    public Duration connectTimeout() { return Duration.ofMillis(Math.max(1000L, config.getLong("bridge.connect-timeout-ms", 10000L))); }
    public Duration requestTimeout() { return Duration.ofMillis(Math.max(5000L, config.getLong("bridge.request-timeout-ms", 45000L))); }
    public int queueCapacity() { return Math.max(10, config.getInt("bridge.queue-capacity", 100)); }
    public int failureThreshold() { return Math.max(1, config.getInt("bridge.failure-threshold", 2)); }
    public long circuitOpenMs() { return Math.max(10000L, config.getLong("bridge.circuit-open-ms", 60000L)); }
    public long warningCooldownMs() { return Math.max(10000L, config.getLong("bridge.warning-cooldown-ms", 60000L)); }
    public boolean logPayloads() { return config.getBoolean("bridge.log-payloads", false); }
    public String endpoint(String name) {
        String endpoint = config.getString("endpoints." + name, "");
        if (endpoint == null || endpoint.isBlank()) return "/";
        return endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    }

    public boolean repositoryEnabled() { return config.getBoolean("repository.enabled", true); }
    public boolean removeAcceptedRepositoryItems() { return config.getBoolean("repository.remove-accepted-items", true); }
    public boolean requireArtifactTag() { return config.getBoolean("repository.require-artifact-tag", false); }
    public String artifactKey() { return config.getString("repository.artifact-key", "nexus_artifact"); }
    public List<String> acceptedNameFragments() {
        return Collections.unmodifiableList(new ArrayList<>(config.getStringList("repository.accepted-name-fragments")));
    }

    public Map<String, RepositoryLocation> repositories() {
        ConfigurationSection section = config.getConfigurationSection("repository.locations");
        Map<String, RepositoryLocation> out = new LinkedHashMap<>();
        if (section == null) return out;
        for (String id : section.getKeys(false)) {
            String base = "repository.locations." + id + ".";
            out.put(id, new RepositoryLocation(
                id,
                config.getString(base + "world", "nexsus"),
                config.getInt(base + "x"),
                config.getInt(base + "y"),
                config.getInt(base + "z")
            ));
        }
        return out;
    }

    public record RepositoryLocation(String id, String world, int x, int y, int z) {}
}

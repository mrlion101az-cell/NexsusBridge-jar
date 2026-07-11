package com.nexusuniverse.bridge.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;

public final class BridgeConfig {
    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public BridgeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    public boolean enabled() { return cfg.getBoolean("bridge.enabled", true); }
    public String baseUrl() { return trimSlash(cfg.getString("bridge.base-url", "")); }
    public String token() { return cfg.getString("bridge.auth-token", ""); }
    public Duration connectTimeout() { return Duration.ofMillis(cfg.getLong("bridge.connect-timeout-ms", 5000)); }
    public Duration requestTimeout() { return Duration.ofMillis(cfg.getLong("bridge.request-timeout-ms", 12000)); }
    public int queueCapacity() { return Math.max(100, cfg.getInt("bridge.queue-capacity", 2000)); }
    public int maxRetries() { return Math.max(0, cfg.getInt("bridge.max-retries", 3)); }
    public long retryBaseDelayMs() { return Math.max(100, cfg.getLong("bridge.retry-base-delay-ms", 750)); }
    public boolean logPayloads() { return cfg.getBoolean("bridge.log-payloads", false); }
    public boolean feature(String name) { return cfg.getBoolean("features." + name, true); }
    public String endpoint(String name) { return cfg.getString("endpoints." + name, "/world_event"); }
    public boolean removeAcceptedRepositoryItems() { return cfg.getBoolean("repository.remove-accepted-items", true); }
    public boolean requireArtifactTag() { return cfg.getBoolean("repository.require-artifact-tag", false); }
    public String artifactKey() { return cfg.getString("repository.artifact-key", "nexus_artifact"); }
    public List<String> acceptedNameFragments() { return cfg.getStringList("repository.accepted-name-fragments"); }
    public int snapshotDelayTicks() { return Math.max(1, cfg.getInt("inventory.snapshot-delay-ticks", 2)); }
    public boolean includeArmor() { return cfg.getBoolean("inventory.include-armor", true); }
    public boolean includeOffhand() { return cfg.getBoolean("inventory.include-offhand", true); }
    public boolean includeEnderChest() { return cfg.getBoolean("inventory.include-ender-chest", false); }
    public int regionCheckEveryTicks() { return Math.max(1, cfg.getInt("regions.check-every-ticks", 5)); }
    public String npcMetadataKey() { return cfg.getString("npc.citizens-metadata-key", "NPC"); }
    public boolean includeTaggedNpcs() { return cfg.getBoolean("npc.include-scoreboard-tagged", true); }
    public String npcScoreboardTag() { return cfg.getString("npc.scoreboard-tag", "nexus_npc"); }

    public Map<String, RepositoryLocation> repositories() {
        Map<String, RepositoryLocation> out = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("repository.locations");
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            String path = "repository.locations." + key;
            out.put(key, new RepositoryLocation(key, cfg.getString(path + ".world", "world"), cfg.getInt(path + ".x"), cfg.getInt(path + ".y"), cfg.getInt(path + ".z")));
        }
        return out;
    }

    public Map<String, RegionDefinition> regions() {
        Map<String, RegionDefinition> out = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("regions.definitions");
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            String path = "regions.definitions." + key;
            List<Integer> min = cfg.getIntegerList(path + ".min");
            List<Integer> max = cfg.getIntegerList(path + ".max");
            if (min.size() == 3 && max.size() == 3) {
                out.put(key, new RegionDefinition(key, cfg.getString(path + ".world", "world"), min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2)));
            }
        }
        return out;
    }

    private static String trimSlash(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    public record RepositoryLocation(String id, String world, int x, int y, int z) {}
    public record RegionDefinition(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(String w, int x, int y, int z) {
            return world.equals(w) && x >= Math.min(minX,maxX) && x <= Math.max(minX,maxX)
                && y >= Math.min(minY,maxY) && y <= Math.max(minY,maxY)
                && z >= Math.min(minZ,maxZ) && z <= Math.max(minZ,maxZ);
        }
    }
}

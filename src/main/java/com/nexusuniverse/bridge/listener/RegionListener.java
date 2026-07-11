package com.nexusuniverse.bridge.listener;

import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.http.HttpDispatcher;
import com.nexusuniverse.bridge.model.BridgeEvent;
import com.nexusuniverse.bridge.serialize.PlayerSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionListener {
    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final HttpDispatcher http;
    private final Map<UUID, Set<String>> current = new ConcurrentHashMap<>();
    private int taskId = -1;

    public RegionListener(JavaPlugin plugin, BridgeConfig config, HttpDispatcher http) {
        this.plugin = plugin;
        this.config = config;
        this.http = http;
    }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, config.regionCheckEveryTicks(), config.regionCheckEveryTicks());
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        current.clear();
    }

    private void tick() {
        if (!config.feature("regions")) return;
        Map<String, BridgeConfig.RegionDefinition> regions = config.regions();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<String> now = new HashSet<>();
            String worldName = p.getWorld().getName();
            String worldKey = p.getWorld().getKey().toString();
            for (var entry : regions.entrySet()) {
                var r = entry.getValue();
                if (r.contains(worldName, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ()) || r.contains(worldKey, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ())) now.add(entry.getKey());
            }
            Set<String> before = current.getOrDefault(p.getUniqueId(), Set.of());
            for (String entered : difference(now, before)) send(p, "region_enter", entered);
            for (String exited : difference(before, now)) send(p, "region_exit", exited);
            current.put(p.getUniqueId(), now);
        }
    }

    private Set<String> difference(Set<String> a, Set<String> b) { Set<String> x = new HashSet<>(a); x.removeAll(b); return x; }
    private void send(Player p, String type, String region) {
        Map<String,Object> d = new LinkedHashMap<>(PlayerSerializer.base(p));
        d.put("region", region);
        d.put("description", p.getName() + " " + type + " " + region);
        http.post(config.endpoint("world-event"), new BridgeEvent(type, p.getName(), d).toMap());
    }
}

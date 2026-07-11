package com.nexusuniverse.bridge.listener;

import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.http.HttpDispatcher;
import com.nexusuniverse.bridge.model.BridgeEvent;
import com.nexusuniverse.bridge.serialize.PlayerSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryListener implements Listener {
    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final HttpDispatcher http;
    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    public InventoryListener(JavaPlugin plugin, BridgeConfig config, HttpDispatcher http) {
        this.plugin = plugin;
        this.config = config;
        this.http = http;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { schedule(event.getPlayer(), "join"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player p) schedule(p, "inventory_click"); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) { if (event.getPlayer() instanceof Player p) schedule(p, "inventory_close"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player p) schedule(p, "item_pickup"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { schedule(event.getPlayer(), "item_drop"); }

    private void schedule(Player player, String reason) {
        if (!config.feature("inventory-snapshots")) return;
        long token = System.nanoTime();
        pending.put(player.getUniqueId().toString(), token);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!Long.valueOf(token).equals(pending.get(player.getUniqueId().toString()))) return;
            Map<String, Object> data = PlayerSerializer.inventory(player, config);
            data.put("reason", reason);
            http.post(config.endpoint("inventory"), new BridgeEvent("inventory_snapshot", player.getName(), data).toMap());
        }, config.snapshotDelayTicks());
    }
}

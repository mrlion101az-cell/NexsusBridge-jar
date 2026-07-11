package com.nexusuniverse.bridge.listener;

import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.http.HttpDispatcher;
import com.nexusuniverse.bridge.model.BridgeEvent;
import com.nexusuniverse.bridge.serialize.ItemSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RepositoryListener implements Listener {
    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final HttpDispatcher http;

    public RepositoryListener(JavaPlugin plugin, BridgeConfig config, HttpDispatcher http) {
        this.plugin = plugin;
        this.config = config;
        this.http = http;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!config.repositoryEnabled() || !(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof Container container)) return;
        BridgeConfig.RepositoryLocation repository = findRepository(container.getLocation());
        if (repository == null) return;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            String artifactId = detectArtifactId(item);
            if (artifactId == null && config.requireArtifactTag()) continue;
            if (artifactId == null && !matchesAcceptedName(item)) continue;
            submit(player, repository, inventory, slot, item.clone(), artifactId);
        }
    }

    private void submit(Player player, BridgeConfig.RepositoryLocation repository, Inventory inventory,
                        int slot, ItemStack submitted, String artifactId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("repository_id", repository.id());
        data.put("world", repository.world());
        data.put("x", repository.x());
        data.put("y", repository.y());
        data.put("z", repository.z());
        data.put("artifact_id", artifactId == null ? "" : artifactId);
        data.put("slot", slot);
        data.put("item", ItemSerializer.serialize(submitted, slot));

        http.post(config.endpoint("repository"),
            new BridgeEvent("repository_submit", player.getName(), data).toMap(),
            response -> {
                if (!response.bodySaysAccepted()) {
                    player.sendMessage("§6[F.R.A.C.T.U.R.E.] §cSubmission rejected or unavailable.");
                    return;
                }
                if (config.removeAcceptedRepositoryItems()) removeMatchingItem(inventory, slot, submitted);
                player.sendMessage("§6[F.R.A.C.T.U.R.E.] §eArtifact accepted. Archive processing initiated.");
            }
        );
    }

    private void removeMatchingItem(Inventory inventory, int slot, ItemStack submitted) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || !current.isSimilar(submitted)) return;
        int amount = Math.min(submitted.getAmount(), current.getAmount());
        if (current.getAmount() <= amount) inventory.setItem(slot, null);
        else current.setAmount(current.getAmount() - amount);
        if (inventory.getHolder() instanceof BlockState state) state.update(true, false);
    }

    private BridgeConfig.RepositoryLocation findRepository(Location location) {
        String worldName = location.getWorld() == null ? "" : location.getWorld().getName();
        String worldKey = location.getWorld() == null ? "" : location.getWorld().getKey().toString();
        for (BridgeConfig.RepositoryLocation repository : config.repositories().values()) {
            boolean sameWorld = repository.world().equals(worldName) || repository.world().equals(worldKey);
            if (sameWorld && repository.x() == location.getBlockX()
                && repository.y() == location.getBlockY() && repository.z() == location.getBlockZ()) return repository;
        }
        return null;
    }

    private String detectArtifactId(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        NamespacedKey configured = NamespacedKey.fromString(config.artifactKey());
        if (configured != null) {
            String value = item.getItemMeta().getPersistentDataContainer().get(configured, PersistentDataType.STRING);
            if (value != null && !value.isBlank()) return value.trim().toLowerCase(Locale.ROOT);
        }
        for (NamespacedKey existing : item.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (existing.getKey().equalsIgnoreCase(config.artifactKey())
                || existing.toString().endsWith(":" + config.artifactKey())) {
                String value = item.getItemMeta().getPersistentDataContainer().get(existing, PersistentDataType.STRING);
                if (value != null && !value.isBlank()) return value.trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private boolean matchesAcceptedName(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String text = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return config.acceptedNameFragments().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(lower::contains);
    }
}

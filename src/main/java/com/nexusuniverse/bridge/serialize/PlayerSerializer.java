package com.nexusuniverse.bridge.serialize;

import com.nexusuniverse.bridge.config.BridgeConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class PlayerSerializer {
    private PlayerSerializer() {}

    public static Map<String, Object> base(Player player) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("player", player.getName());
        out.put("uuid", player.getUniqueId().toString());
        out.put("world", player.getWorld().getKey().toString());
        out.put("location", location(player.getLocation()));
        out.put("game_mode", player.getGameMode().name().toLowerCase());
        return out;
    }

    public static Map<String, Object> inventory(Player player, BridgeConfig config) {
        Map<String, Object> out = base(player);
        List<Map<String, Object>> items = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) items.add(ItemSerializer.serialize(item, i));
        }
        if (config.includeArmor()) {
            ItemStack[] armor = player.getInventory().getArmorContents();
            for (int i = 0; i < armor.length; i++) {
                ItemStack item = armor[i];
                if (item != null && !item.getType().isAir()) items.add(ItemSerializer.serialize(item, 100 + i));
            }
        }
        if (config.includeOffhand()) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (!offhand.getType().isAir()) items.add(ItemSerializer.serialize(offhand, 150));
        }
        if (config.includeEnderChest()) {
            ItemStack[] ender = player.getEnderChest().getContents();
            for (int i = 0; i < ender.length; i++) {
                ItemStack item = ender[i];
                if (item != null && !item.getType().isAir()) items.add(ItemSerializer.serialize(item, 200 + i));
            }
        }
        out.put("items", items);
        return out;
    }

    public static Map<String, Object> location(Location loc) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", loc.getWorld() == null ? "" : loc.getWorld().getKey().toString());
        out.put("x", loc.getX());
        out.put("y", loc.getY());
        out.put("z", loc.getZ());
        out.put("block_x", loc.getBlockX());
        out.put("block_y", loc.getBlockY());
        out.put("block_z", loc.getBlockZ());
        out.put("yaw", loc.getYaw());
        out.put("pitch", loc.getPitch());
        return out;
    }
}

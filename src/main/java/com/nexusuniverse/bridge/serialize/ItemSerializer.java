package com.nexusuniverse.bridge.serialize;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class ItemSerializer {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ItemSerializer() {}

    public static Map<String, Object> serialize(ItemStack item, int slot) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("slot", slot);
        if (item == null || item.getType().isAir()) {
            out.put("empty", true);
            return out;
        }

        out.put("material", item.getType().getKey().toString());
        out.put("amount", item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasCustomName() && meta.customName() != null) out.put("custom_name", PLAIN.serialize(meta.customName()));
            else if (meta.hasDisplayName() && meta.displayName() != null) out.put("display_name", PLAIN.serialize(meta.displayName()));

            if (meta.hasLore() && meta.lore() != null) {
                List<String> lore = new ArrayList<>();
                meta.lore().forEach(line -> lore.add(PLAIN.serialize(line)));
                out.put("lore", lore);
            }

            Map<String, Object> pdc = serializePdc(meta.getPersistentDataContainer());
            if (!pdc.isEmpty()) out.put("custom_data", pdc);
            if (meta.hasCustomModelData()) out.put("custom_model_data", meta.getCustomModelData());
        }

        return out;
    }

    public static Map<String, Object> serializePdc(PersistentDataContainer pdc) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (NamespacedKey key : pdc.getKeys()) {
            Object value = readKnownType(pdc, key);
            if (value != null) out.put(key.toString(), value);
        }
        return out;
    }

    private static Object readKnownType(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.STRING)) return pdc.get(key, PersistentDataType.STRING);
        if (pdc.has(key, PersistentDataType.INTEGER)) return pdc.get(key, PersistentDataType.INTEGER);
        if (pdc.has(key, PersistentDataType.LONG)) return pdc.get(key, PersistentDataType.LONG);
        if (pdc.has(key, PersistentDataType.DOUBLE)) return pdc.get(key, PersistentDataType.DOUBLE);
        if (pdc.has(key, PersistentDataType.FLOAT)) return pdc.get(key, PersistentDataType.FLOAT);
        if (pdc.has(key, PersistentDataType.BYTE)) return pdc.get(key, PersistentDataType.BYTE);
        if (pdc.has(key, PersistentDataType.BYTE_ARRAY)) return Base64.getEncoder().encodeToString(pdc.get(key, PersistentDataType.BYTE_ARRAY));
        if (pdc.has(key, PersistentDataType.INTEGER_ARRAY)) return Arrays.stream(Objects.requireNonNull(pdc.get(key, PersistentDataType.INTEGER_ARRAY))).boxed().toList();
        if (pdc.has(key, PersistentDataType.LONG_ARRAY)) return Arrays.stream(Objects.requireNonNull(pdc.get(key, PersistentDataType.LONG_ARRAY))).boxed().toList();
        return "<unsupported>";
    }
}

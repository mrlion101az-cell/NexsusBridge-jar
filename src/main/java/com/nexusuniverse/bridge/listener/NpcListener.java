package com.nexusuniverse.bridge.listener;

import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.http.HttpDispatcher;
import com.nexusuniverse.bridge.model.BridgeEvent;
import com.nexusuniverse.bridge.serialize.PlayerSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NpcListener implements Listener {
    private final BridgeConfig config;
    private final HttpDispatcher http;

    public NpcListener(BridgeConfig config, HttpDispatcher http) {
        this.config = config;
        this.http = http;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!config.feature("npc-interactions")) return;
        Entity entity = event.getRightClicked();
        boolean citizens = entity.hasMetadata(config.npcMetadataKey());
        boolean tagged = config.includeTaggedNpcs() && entity.getScoreboardTags().contains(config.npcScoreboardTag());
        if (!citizens && !tagged) return;

        Map<String, Object> data = new LinkedHashMap<>(PlayerSerializer.base(event.getPlayer()));
        data.put("npc_name", entity.getName());
        data.put("npc_uuid", entity.getUniqueId().toString());
        data.put("entity_type", entity.getType().getKey().toString());
        data.put("message", "[NPC_TRIGGER] " + entity.getName() + " " + event.getPlayer().getName());
        http.post(config.endpoint("npc"), new BridgeEvent("npc_interaction", event.getPlayer().getName(), data).toMap());
    }
}

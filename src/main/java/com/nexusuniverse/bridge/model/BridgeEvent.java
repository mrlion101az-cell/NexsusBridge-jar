package com.nexusuniverse.bridge.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class BridgeEvent {
    private final String id = UUID.randomUUID().toString();
    private final String type;
    private final String player;
    private final Map<String, Object> data;

    public BridgeEvent(String type, String player, Map<String, Object> data) {
        this.type = type;
        this.player = player;
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("event_id", id);
        out.put("event_type", type);
        out.put("player", player);
        out.put("timestamp", Instant.now().toString());
        out.putAll(data);
        return out;
    }
}

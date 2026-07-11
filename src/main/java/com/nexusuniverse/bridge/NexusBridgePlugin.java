package com.nexusuniverse.bridge;

import com.nexusuniverse.bridge.command.NexusBridgeCommand;
import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.http.HttpDispatcher;
import com.nexusuniverse.bridge.listener.*;
import com.nexusuniverse.bridge.model.BridgeEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NexusBridgePlugin extends JavaPlugin {
    private BridgeConfig bridgeConfig;
    private HttpDispatcher http;
    private RegionListener regionListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        startBridge();
        NexusBridgeCommand command = new NexusBridgeCommand(this);
        if (getCommand("nexusbridge") != null) getCommand("nexusbridge").setExecutor(command);
        getLogger().info("NexusBridge enabled. Kairos endpoint=" + bridgeConfig.baseUrl());
    }

    @Override
    public void onDisable() {
        if (regionListener != null) regionListener.stop();
        if (http != null) http.close();
    }

    private void startBridge() {
        this.bridgeConfig = new BridgeConfig(this);
        this.http = new HttpDispatcher(this, bridgeConfig);
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new RepositoryListener(this, bridgeConfig, http), this);
        pm.registerEvents(new InventoryListener(this, bridgeConfig, http), this);
        pm.registerEvents(new NpcListener(bridgeConfig, http), this);
        pm.registerEvents(new WorldEventListener(bridgeConfig, http), this);
        this.regionListener = new RegionListener(this, bridgeConfig, http);
        regionListener.start();
    }

    public void reloadBridge() {
        if (regionListener != null) regionListener.stop();
        if (http != null) http.close();
        bridgeConfig.reload();
        this.http = new HttpDispatcher(this, bridgeConfig);
        this.regionListener = new RegionListener(this, bridgeConfig, http);
        regionListener.start();
    }

    public void sendTestEvent(String sender) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("event_type", "nexusbridge_test");
        data.put("description", "NexusBridge test requested by " + sender);
        http.post(bridgeConfig.endpoint("world-event"), new BridgeEvent("nexusbridge_test", sender, data).toMap());
    }

    public BridgeConfig bridgeConfig() { return bridgeConfig; }
    public HttpDispatcher http() { return http; }
}

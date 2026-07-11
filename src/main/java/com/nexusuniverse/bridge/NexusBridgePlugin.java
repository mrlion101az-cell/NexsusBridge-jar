package com.nexusuniverse.bridge;

import com.nexusuniverse.bridge.command.NexusBridgeCommand;
import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.http.HttpDispatcher;
import com.nexusuniverse.bridge.listener.InventoryListener;
import com.nexusuniverse.bridge.listener.NpcListener;
import com.nexusuniverse.bridge.listener.RegionListener;
import com.nexusuniverse.bridge.listener.RepositoryListener;
import com.nexusuniverse.bridge.listener.WorldEventListener;
import com.nexusuniverse.bridge.model.BridgeEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
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
        this.bridgeConfig = new BridgeConfig(this);
        startRuntime();

        NexusBridgeCommand command = new NexusBridgeCommand(this);
        if (getCommand("nexusbridge") != null) {
            getCommand("nexusbridge").setExecutor(command);
            getCommand("nexusbridge").setTabCompleter(command);
        } else {
            getLogger().severe("Command 'nexusbridge' is missing from plugin.yml");
        }

        getLogger().info("NexusBridge enabled. Kairos endpoint=" + bridgeConfig.baseUrl());
    }

    @Override
    public void onDisable() {
        stopRuntime();
    }

    private void startRuntime() {
        this.http = new HttpDispatcher(this, bridgeConfig);

        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new RepositoryListener(this, bridgeConfig, http), this);
        pluginManager.registerEvents(new InventoryListener(this, bridgeConfig, http), this);
        pluginManager.registerEvents(new NpcListener(bridgeConfig, http), this);
        pluginManager.registerEvents(new WorldEventListener(bridgeConfig, http), this);

        this.regionListener = new RegionListener(this, bridgeConfig, http);
        regionListener.start();
    }

    private void stopRuntime() {
        if (regionListener != null) {
            regionListener.stop();
            regionListener = null;
        }

        HandlerList.unregisterAll(this);

        if (http != null) {
            http.close();
            http = null;
        }
    }

    public synchronized void reloadBridge() {
        stopRuntime();
        bridgeConfig.reload();
        startRuntime();
        getLogger().info("NexusBridge runtime reloaded successfully.");
    }

    public void sendTestEvent(String sender) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event_type", "nexusbridge_test");
        data.put("description", "NexusBridge test requested by " + sender);
        data.put("source", "minecraft");
        http.post(
            bridgeConfig.endpoint("world-event"),
            new BridgeEvent("nexusbridge_test", sender, data).toMap()
        );
    }

    public BridgeConfig bridgeConfig() {
        return bridgeConfig;
    }

    public HttpDispatcher http() {
        return http;
    }
}

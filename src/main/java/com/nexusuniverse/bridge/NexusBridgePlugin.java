package com.nexusuniverse.bridge;

import com.nexusuniverse.bridge.command.NexusBridgeCommand;
import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.http.HttpDispatcher;
import com.nexusuniverse.bridge.listener.RepositoryListener;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexusBridgePlugin extends JavaPlugin {
    private BridgeConfig bridgeConfig;
    private HttpDispatcher http;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bridgeConfig = new BridgeConfig(this);
        startRuntime();

        NexusBridgeCommand command = new NexusBridgeCommand(this);
        if (getCommand("nexusbridge") == null) {
            getLogger().severe("Command 'nexusbridge' is missing from plugin.yml; disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("nexusbridge").setExecutor(command);
        getCommand("nexusbridge").setTabCompleter(command);
        getLogger().info("NexusBridge v" + getPluginMeta().getVersion() + " enabled. General world events are disabled.");
    }

    @Override
    public void onDisable() {
        stopRuntime();
    }

    private void startRuntime() {
        http = new HttpDispatcher(this, bridgeConfig);
        Bukkit.getPluginManager().registerEvents(new RepositoryListener(this, bridgeConfig, http), this);
    }

    private void stopRuntime() {
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
        getLogger().info("NexusBridge configuration reloaded.");
    }

    public BridgeConfig bridgeConfig() { return bridgeConfig; }
    public HttpDispatcher http() { return http; }
}

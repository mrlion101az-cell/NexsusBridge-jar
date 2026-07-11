package com.nexusuniverse.bridge.command;

import com.nexusuniverse.bridge.NexusBridgePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class NexusBridgeCommand implements CommandExecutor {
    private final NexusBridgePlugin plugin;

    public NexusBridgeCommand(NexusBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                plugin.reloadBridge();
                sender.sendMessage("§aNexusBridge reloaded.");
            }
            case "test" -> {
                plugin.sendTestEvent(sender.getName());
                sender.sendMessage("§eNexusBridge test event queued.");
            }
            case "flush" -> sender.sendMessage("§eQueued: " + plugin.http().queuedCount());
            default -> {
                sender.sendMessage("§6NexusBridge §fv" + plugin.getPluginMeta().getVersion());
                sender.sendMessage("§7Enabled: §f" + plugin.bridgeConfig().enabled());
                sender.sendMessage("§7Base URL: §f" + plugin.bridgeConfig().baseUrl());
                sender.sendMessage("§7Repositories: §f" + plugin.bridgeConfig().repositories().size());
                sender.sendMessage("§7Regions: §f" + plugin.bridgeConfig().regions().size());
                sender.sendMessage("§7HTTP sent/failed/queued: §f" + plugin.http().sentCount() + "/" + plugin.http().failedCount() + "/" + plugin.http().queuedCount());
            }
        }
        return true;
    }
}

package com.nexusuniverse.bridge.command;

import com.nexusuniverse.bridge.NexusBridgePlugin;
import com.nexusuniverse.bridge.config.BridgeConfig;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NexusBridgeCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = "§6[NexusBridge] §r";
    private final NexusBridgePlugin plugin;

    public NexusBridgeCommand(NexusBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("nexusbridge.admin")) {
            sender.sendMessage(PREFIX + "§cYou do not have permission.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "ping" -> ping(sender);
            case "chat" -> chat(sender, args);
            case "repository", "repo" -> repository(sender, args);
            case "reload" -> {
                plugin.reloadBridge();
                sender.sendMessage(PREFIX + "§aReloaded.");
            }
            case "flush", "queue" -> queue(sender);
            case "version" -> sender.sendMessage(PREFIX + "§fVersion §63.0.0");
            case "help", "?" -> help(sender, label);
            default -> help(sender, label);
        }
        return true;
    }

    private void status(CommandSender sender) {
        BridgeConfig config = plugin.bridgeConfig();
        sender.sendMessage("§6§m----------------------------------------");
        sender.sendMessage("§6NexusBridge §fv" + plugin.getPluginMeta().getVersion());
        sender.sendMessage("§7Kairos URL: §f" + config.baseUrl());
        sender.sendMessage("§7Repository listener: " + (config.repositoryEnabled() ? "§aONLINE" : "§cOFFLINE"));
        sender.sendMessage("§7General world events: §cDISABLED");
        sender.sendMessage("§7Sent / failed / dropped / queued: §f" + plugin.http().sentCount() + " / "
            + plugin.http().failedCount() + " / " + plugin.http().droppedCount() + " / " + plugin.http().queuedCount());
        sender.sendMessage("§7Circuit: " + (plugin.http().circuitOpen() ? "§cOPEN" : "§aCLOSED"));
        sender.sendMessage("§6§m----------------------------------------");
    }

    private void ping(CommandSender sender) {
        long started = System.nanoTime();
        sender.sendMessage(PREFIX + "§ePinging Kairos...");
        plugin.http().get("/", response -> {
            long elapsed = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            if (response.ok()) sender.sendMessage(PREFIX + "§aKairos responded in §f" + elapsed + " ms§a.");
            else sender.sendMessage(PREFIX + "§cPing failed: §7" + response.error());
        });
    }

    private void chat(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "§eUsage: /kairos chat <message>");
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("player", sender.getName());
        payload.put("message", message);
        payload.put("source", "minecraft");
        payload.put("platform", "minecraft");
        sender.sendMessage(PREFIX + "§7Sending message to Kairos...");
        plugin.http().post(plugin.bridgeConfig().endpoint("chat"), payload, response -> {
            if (response.ok()) sender.sendMessage(PREFIX + "§aKairos accepted the message.");
            else sender.sendMessage(PREFIX + "§cChat failed: §7" + response.error());
        });
    }

    private void repository(CommandSender sender, String[] args) {
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if ("nearest".equals(mode) && sender instanceof Player player) {
            nearest(player);
            return;
        }
        sender.sendMessage(PREFIX + "§eRepositories: §f" + plugin.bridgeConfig().repositories().size());
        plugin.bridgeConfig().repositories().values().forEach(repository -> sender.sendMessage(
            "§8- §6" + repository.id() + " §7" + repository.world() + " @ §f"
                + repository.x() + ", " + repository.y() + ", " + repository.z()));
    }

    private void nearest(Player player) {
        BridgeConfig.RepositoryLocation nearest = null;
        double best = Double.MAX_VALUE;
        Location location = player.getLocation();
        String worldName = location.getWorld() == null ? "" : location.getWorld().getName();
        String worldKey = location.getWorld() == null ? "" : location.getWorld().getKey().toString();
        for (BridgeConfig.RepositoryLocation repository : plugin.bridgeConfig().repositories().values()) {
            if (!repository.world().equals(worldName) && !repository.world().equals(worldKey)) continue;
            double dx = location.getX() - repository.x();
            double dy = location.getY() - repository.y();
            double dz = location.getZ() - repository.z();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) { best = distance; nearest = repository; }
        }
        if (nearest == null) {
            player.sendMessage(PREFIX + "§cNo repository is configured in this world.");
            return;
        }
        player.sendMessage(PREFIX + "§eNearest: §6" + nearest.id() + " §7at §f"
            + nearest.x() + ", " + nearest.y() + ", " + nearest.z());
    }

    private void queue(CommandSender sender) {
        sender.sendMessage(PREFIX + "§7Sent/failed/dropped/queued: §f" + plugin.http().sentCount() + "/"
            + plugin.http().failedCount() + "/" + plugin.http().droppedCount() + "/" + plugin.http().queuedCount());
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6/" + label + " status");
        sender.sendMessage("§6/" + label + " ping");
        sender.sendMessage("§6/" + label + " chat <message>");
        sender.sendMessage("§6/" + label + " repository [list|nearest]");
        sender.sendMessage("§6/" + label + " reload");
        sender.sendMessage("§6/" + label + " flush");
        sender.sendMessage("§6/" + label + " version");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("nexusbridge.admin")) return Collections.emptyList();
        if (args.length == 1) return filter(List.of("status", "ping", "chat", "repository", "reload", "flush", "version", "help"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("repository") || args[0].equalsIgnoreCase("repo"))) {
            return filter(List.of("list", "nearest"), args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) if (value.startsWith(lower)) out.add(value);
        return out;
    }
}

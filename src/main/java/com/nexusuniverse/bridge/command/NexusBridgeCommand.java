package com.nexusuniverse.bridge.command;

import com.nexusuniverse.bridge.NexusBridgePlugin;
import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.model.BridgeEvent;
import org.bukkit.ChatColor;
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
import java.util.concurrent.ConcurrentHashMap;

public final class NexusBridgeCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = "§6[NexusBridge] §r";
    private static final long COMMAND_COOLDOWN_MS = 1_000L;

    private final NexusBridgePlugin plugin;
    private final Map<String, Long> commandCooldowns = new ConcurrentHashMap<>();

    public NexusBridgeCommand(NexusBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!sender.hasPermission("nexusbridge.admin")) {
            sender.sendMessage(PREFIX + "§cYou do not have permission to use this command.");
            return true;
        }

        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);

        try {
            switch (sub) {
                case "status" -> showStatus(sender);
                case "ping" -> ping(sender);
                case "chat" -> chat(sender, args);
                case "repository", "repo" -> repository(sender, args);
                case "reload" -> reload(sender);
                case "test" -> test(sender);
                case "flush", "queue" -> showQueue(sender);
                case "version" -> showVersion(sender);
                case "help", "?" -> showHelp(sender, label);
                default -> {
                    sender.sendMessage(PREFIX + "§cUnknown subcommand: §f" + sub);
                    showHelp(sender, label);
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().severe("Command failure for /" + label + " " + String.join(" ", args) + ": " + exception.getMessage());
            exception.printStackTrace();
            sender.sendMessage(PREFIX + "§cCommand failed. Check the server console for details.");
        }

        return true;
    }

    private void showStatus(CommandSender sender) {
        BridgeConfig config = plugin.bridgeConfig();
        sender.sendMessage("§6§m--------------------------------------------------");
        sender.sendMessage("§6NexusBridge §fv" + plugin.getPluginMeta().getVersion());
        sender.sendMessage("§7Enabled: " + yesNo(config.enabled()));
        sender.sendMessage("§7Kairos URL: §f" + config.baseUrl());
        sender.sendMessage("§7HTTP sent / failed / queued: §f"
            + plugin.http().sentCount() + " / "
            + plugin.http().failedCount() + " / "
            + plugin.http().queuedCount());
        sender.sendMessage("§7Repositories: §f" + config.repositories().size());
        sender.sendMessage("§7Regions: §f" + config.regions().size());
        sender.sendMessage("§7Repository listener: " + yesNo(config.feature("repository")));
        sender.sendMessage("§7Inventory snapshots: " + yesNo(config.feature("inventory-snapshots")));
        sender.sendMessage("§7NPC interactions: " + yesNo(config.feature("npc-interactions")));
        sender.sendMessage("§7World events: " + yesNo(config.feature("combat") || config.feature("block-interactions")));
        sender.sendMessage("§6§m--------------------------------------------------");
    }

    private void ping(CommandSender sender) {
        if (!checkCooldown(sender, "ping")) return;

        long started = System.nanoTime();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("description", "NexusBridge ping requested by " + sender.getName());
        data.put("source", "minecraft");
        data.put("command", "ping");

        sender.sendMessage(PREFIX + "§ePinging Kairos...");

        plugin.http().post(
            plugin.bridgeConfig().endpoint("world-event"),
            new BridgeEvent("nexusbridge_ping", sender.getName(), data).toMap(),
            response -> {
                long elapsedMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
                if (response.ok()) {
                    sender.sendMessage(PREFIX + "§aKairos responded in §f" + elapsedMs + " ms §7(HTTP " + response.status() + ").");
                    String reply = extractReply(response.body());
                    if (!reply.isBlank()) sender.sendMessage("§5Kairos: §f" + reply);
                } else {
                    sender.sendMessage(PREFIX + "§cKairos did not respond successfully. §7"
                        + failureText(response));
                }
            }
        );
    }

    private void chat(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "§eUsage: §f/kairos chat <message>");
            return;
        }
        if (!checkCooldown(sender, "chat")) return;

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (message.length() > 500) {
            sender.sendMessage(PREFIX + "§cMessage is too long. Maximum: 500 characters.");
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("player", sender.getName());
        payload.put("message", message);
        payload.put("source", "minecraft");
        payload.put("platform", "minecraft");

        sender.sendMessage(PREFIX + "§7Sending message to Kairos...");

        plugin.http().post(
            plugin.bridgeConfig().endpoint("chat"),
            payload,
            response -> {
                if (!response.ok()) {
                    sender.sendMessage(PREFIX + "§cChat request failed. §7" + failureText(response));
                    return;
                }

                String reply = extractReply(response.body());
                if (reply.isBlank()) {
                    sender.sendMessage(PREFIX + "§7Kairos accepted the message but returned no dialogue.");
                } else {
                    sender.sendMessage("§5Kairos: §f" + reply);
                }
            }
        );
    }

    private void repository(CommandSender sender, String[] args) {
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";

        if ("nearest".equals(mode)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX + "§cOnly a player can use repository nearest.");
                return;
            }
            showNearestRepository(player);
            return;
        }

        if (!"list".equals(mode)) {
            sender.sendMessage(PREFIX + "§eUsage: §f/kairos repository [list|nearest]");
            return;
        }

        Map<String, BridgeConfig.RepositoryLocation> repositories = plugin.bridgeConfig().repositories();
        sender.sendMessage(PREFIX + "§eConfigured repositories: §f" + repositories.size());
        if (repositories.isEmpty()) {
            sender.sendMessage("§7No repository locations are configured.");
            return;
        }

        repositories.values().forEach(repository -> sender.sendMessage(
            "§8- §6" + repository.id()
                + " §7world=§f" + repository.world()
                + " §7xyz=§f" + repository.x() + ", " + repository.y() + ", " + repository.z()
        ));
    }

    private void showNearestRepository(Player player) {
        BridgeConfig.RepositoryLocation nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        Location playerLocation = player.getLocation();

        for (BridgeConfig.RepositoryLocation repository : plugin.bridgeConfig().repositories().values()) {
            String worldName = playerLocation.getWorld() == null ? "" : playerLocation.getWorld().getName();
            String worldKey = playerLocation.getWorld() == null ? "" : playerLocation.getWorld().getKey().toString();
            if (!repository.world().equals(worldName) && !repository.world().equals(worldKey)) continue;

            double dx = playerLocation.getX() - repository.x();
            double dy = playerLocation.getY() - repository.y();
            double dz = playerLocation.getZ() - repository.z();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = repository;
            }
        }

        if (nearest == null) {
            player.sendMessage(PREFIX + "§cNo repository is configured in your current world.");
            return;
        }

        player.sendMessage(PREFIX + "§eNearest repository: §6" + nearest.id());
        player.sendMessage("§7Location: §f" + nearest.world() + " @ "
            + nearest.x() + ", " + nearest.y() + ", " + nearest.z());
        player.sendMessage("§7Distance: §f" + String.format(Locale.US, "%.1f", Math.sqrt(nearestDistanceSquared)) + " blocks");
    }

    private void reload(CommandSender sender) {
        plugin.reloadBridge();
        sender.sendMessage(PREFIX + "§aConfiguration and bridge services reloaded.");
    }

    private void test(CommandSender sender) {
        if (!checkCooldown(sender, "test")) return;
        plugin.sendTestEvent(sender.getName());
        sender.sendMessage(PREFIX + "§eTest event queued for Kairos.");
    }

    private void showQueue(CommandSender sender) {
        sender.sendMessage(PREFIX + "§7HTTP queue: §f" + plugin.http().queuedCount()
            + " §7| sent: §f" + plugin.http().sentCount()
            + " §7| failed: §f" + plugin.http().failedCount());
    }

    private void showVersion(CommandSender sender) {
        sender.sendMessage(PREFIX + "§fVersion §6" + plugin.getPluginMeta().getVersion()
            + " §7| Paper API §f1.21 §7| Java §f" + System.getProperty("java.version"));
    }

    private void showHelp(CommandSender sender, String label) {
        sender.sendMessage("§6§m--------------------------------------------------");
        sender.sendMessage("§6NexusBridge command center");
        sender.sendMessage("§e/" + label + " status §7- Show bridge and subsystem status");
        sender.sendMessage("§e/" + label + " ping §7- Test live communication with Kairos");
        sender.sendMessage("§e/" + label + " chat <message> §7- Send a message to Kairos");
        sender.sendMessage("§e/" + label + " repository [list|nearest] §7- Inspect repository locations");
        sender.sendMessage("§e/" + label + " reload §7- Reload config and bridge services");
        sender.sendMessage("§e/" + label + " test §7- Queue a test world event");
        sender.sendMessage("§e/" + label + " flush §7- Show HTTP queue statistics");
        sender.sendMessage("§e/" + label + " version §7- Show plugin/runtime version");
        sender.sendMessage("§6§m--------------------------------------------------");
    }

    private boolean checkCooldown(CommandSender sender, String action) {
        String key = sender.getName().toLowerCase(Locale.ROOT) + ":" + action;
        long now = System.currentTimeMillis();
        long allowedAt = commandCooldowns.getOrDefault(key, 0L);
        if (now < allowedAt) {
            sender.sendMessage(PREFIX + "§cPlease wait a moment before using that again.");
            return false;
        }
        commandCooldowns.put(key, now + COMMAND_COOLDOWN_MS);
        return true;
    }

    private static String yesNo(boolean value) {
        return value ? "§aONLINE" : "§cOFFLINE";
    }

    private static String failureText(com.nexusuniverse.bridge.http.HttpDispatcher.Response response) {
        if (response.error() != null && !response.error().isBlank()) return response.error();
        return "HTTP " + response.status();
    }

    private static String extractReply(String body) {
        if (body == null || body.isBlank()) return "";
        for (String key : List.of("reply", "message", "text", "response")) {
            String value = extractJsonString(body, key);
            if (!value.isBlank()) return ChatColor.stripColor(value);
        }
        return "";
    }

    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        if (keyIndex < 0) return "";
        int colon = json.indexOf(':', keyIndex + needle.length());
        if (colon < 0) return "";
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return "";

        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return out.toString().trim();
            } else {
                out.append(c);
            }
        }
        return "";
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!sender.hasPermission("nexusbridge.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return filterPrefix(
                List.of("status", "ping", "chat", "repository", "reload", "test", "flush", "version", "help"),
                args[0]
            );
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("repository") || args[0].equalsIgnoreCase("repo"))) {
            return filterPrefix(List.of("list", "nearest"), args[1]);
        }

        return Collections.emptyList();
    }

    private static List<String> filterPrefix(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(lower)) matches.add(value);
        }
        return matches;
    }
}

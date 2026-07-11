package com.nexusuniverse.bridge.listener;

import com.nexusuniverse.bridge.config.BridgeConfig;
import com.nexusuniverse.bridge.http.HttpDispatcher;
import com.nexusuniverse.bridge.model.BridgeEvent;
import com.nexusuniverse.bridge.serialize.ItemSerializer;
import com.nexusuniverse.bridge.serialize.PlayerSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class WorldEventListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final BridgeConfig config;
    private final HttpDispatcher http;

    public WorldEventListener(BridgeConfig config, HttpDispatcher http) {
        this.config = config;
        this.http = http;
    }

    private void post(String type, Player player, Map<String,Object> data) {
        Map<String,Object> merged = new LinkedHashMap<>(PlayerSerializer.base(player));
        merged.putAll(data);
        merged.put("description", type + " by " + player.getName());
        http.post(config.endpoint("world-event"), new BridgeEvent(type, player.getName(), merged).toMap());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onInteract(PlayerInteractEvent e) {
        if (!config.feature("block-interactions") || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Map<String,Object> d = new LinkedHashMap<>();
        d.put("action", e.getAction().name().toLowerCase());
        d.put("block", e.getClickedBlock().getType().getKey().toString());
        d.put("block_location", PlayerSerializer.location(e.getClickedBlock().getLocation()));
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getState() instanceof Sign sign && config.feature("signs")) {
            List<String> lines = new ArrayList<>();
            sign.getSide(org.bukkit.block.sign.Side.FRONT).lines().forEach(c -> lines.add(PLAIN.serialize(c)));
            d.put("sign_lines", lines);
            post("sign_read", e.getPlayer(), d);
        } else {
            post("block_interaction", e.getPlayer(), d);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onSign(SignChangeEvent e) {
        if (!config.feature("signs")) return;
        List<String> lines = new ArrayList<>();
        e.lines().forEach(c -> lines.add(PLAIN.serialize(c)));
        post("sign_change", e.getPlayer(), Map.of("lines", lines, "block_location", PlayerSerializer.location(e.getBlock().getLocation())));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBook(PlayerEditBookEvent e) {
        if (!config.feature("books")) return;
        List<String> pages = e.getNewBookMeta().pages().stream().map(PLAIN::serialize).toList();
        post("book_edit", e.getPlayer(), Map.of("title", Objects.toString(e.getNewBookMeta().getTitle(), ""), "pages", pages, "signed", e.isSigning()));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onCraft(CraftItemEvent e) {
        if (!config.feature("crafting") || !(e.getWhoClicked() instanceof Player p)) return;
        post("item_crafted", p, Map.of("item", ItemSerializer.serialize(e.getRecipe().getResult(), -1)));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onFish(PlayerFishEvent e) {
        if (!config.feature("fishing")) return;
        post("fishing", e.getPlayer(), Map.of("state", e.getState().name().toLowerCase(), "caught", e.getCaught() == null ? "" : e.getCaught().getType().getKey().toString()));
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (!config.feature("advancements")) return;
        post("advancement", e.getPlayer(), Map.of("advancement", e.getAdvancement().getKey().toString()));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPortal(PlayerPortalEvent e) {
        if (!config.feature("portals")) return;
        post("portal", e.getPlayer(), Map.of("cause", e.getCause().name().toLowerCase(), "to", e.getTo() == null ? Map.of() : PlayerSerializer.location(e.getTo())));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBed(PlayerBedEnterEvent e) {
        if (!config.feature("sleeping")) return;
        post("bed_enter", e.getPlayer(), Map.of("bed", PlayerSerializer.location(e.getBed().getLocation())));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!config.feature("combat") || !(e.getDamager() instanceof Player p)) return;
        Entity target = e.getEntity();
        post("combat_hit", p, Map.of("target", target.getName(), "target_type", target.getType().getKey().toString(), "damage", e.getFinalDamage(), "cause", e.getCause().name().toLowerCase()));
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!config.feature("combat")) return;
        Player p = e.getEntity();
        Map<String,Object> d = new LinkedHashMap<>();
        d.put("victim", p.getName());
        d.put("killer", p.getKiller() == null ? "" : p.getKiller().getName());
        d.put("death_message", e.deathMessage() == null ? "" : PLAIN.serialize(e.deathMessage()));
        http.post(config.endpoint("player-kill"), new BridgeEvent("player_death", p.getName(), d).toMap());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent e) {
        if (!config.feature("combat") || e.getEntity() instanceof Player || e.getEntity().getKiller() == null) return;
        Player p = e.getEntity().getKiller();
        post("entity_kill", p, Map.of("entity", e.getEntity().getName(), "entity_type", e.getEntityType().getKey().toString(), "experience", e.getDroppedExp()));
    }
}

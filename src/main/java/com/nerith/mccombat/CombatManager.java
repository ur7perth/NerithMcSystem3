package com.nerith.mccombat;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class CombatManager implements Listener {

    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final NerithMcCombat plugin;
    private final Map<UUID, Long> ends = new HashMap<>();
    private final Map<UUID, String> opponents = new HashMap<>();
    private final Set<UUID> loggedOut = new HashSet<>();
    private int seconds = 10;

    public CombatManager(NerithMcCombat plugin) {
        this.plugin = plugin;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public void load() {
        seconds = plugin.getConfig().getInt("combat-time", 10);
        loggedOut.clear();
        for (String s : plugin.getData().getStringList("combat-logged")) {
            try {
                loggedOut.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveLogged() {
        List<String> list = new ArrayList<>();
        for (UUID id : loggedOut) list.add(id.toString());
        plugin.getData().set("combat-logged", list);
        plugin.saveData();
    }

    public boolean isInCombat(Player p) {
        Long end = ends.get(p.getUniqueId());
        return end != null && end > System.currentTimeMillis();
    }

    private void tag(Player p, Player other) {
        UUID id = p.getUniqueId();
        boolean was = isInCombat(p);
        ends.put(id, System.currentTimeMillis() + seconds * 1000L);
        opponents.put(id, other.getName());
        if (!was) {
            p.sendMessage(LEGACY.deserialize("&cYou are in combat don't log out"));
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = ends.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            UUID id = e.getKey();
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                it.remove();
                opponents.remove(id);
                continue;
            }
            String opp = opponents.getOrDefault(id, "?");
            long left = e.getValue() - now;
            if (left <= 0) {
                it.remove();
                opponents.remove(id);
                p.sendActionBar(LEGACY.deserialize("&cCombat :&f0 &7(" + opp + ")"));
                p.sendMessage(LEGACY.deserialize("&aYou are no longer in combat"));
                continue;
            }
            int sec = (int) Math.ceil(left / 1000.0);
            p.sendActionBar(LEGACY.deserialize("&cCombat :&f" + sec + " &7(" + opp + ")"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player damager = null;
        if (e.getDamager() instanceof Player p1) {
            damager = p1;
        } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2) {
            damager = p2;
        }
        if (damager == null || damager.equals(victim)) return;
        tag(damager, victim);
        tag(victim, damager);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Long end = ends.remove(id);
        opponents.remove(id);
        if (end != null && end > System.currentTimeMillis()) {
            loggedOut.add(id);
            saveLogged();
            p.setHealth(0);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (loggedOut.remove(p.getUniqueId())) {
            saveLogged();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    p.sendMessage(LEGACY.deserialize("&cYou logged out in combat and die")), 5L);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        ends.remove(id);
        opponents.remove(id);
    }
}

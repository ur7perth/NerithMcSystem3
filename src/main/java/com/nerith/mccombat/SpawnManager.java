package com.nerith.mccombat;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnManager implements CommandExecutor, Listener {

    private final NerithMcCombat plugin;
    private final CombatManager combat;
    private Location spawn;
    private final Map<UUID, BukkitTask> teleporting = new HashMap<>();

    public SpawnManager(NerithMcCombat plugin, CombatManager combat) {
        this.plugin = plugin;
        this.combat = combat;
        load();
    }

    public void load() {
        spawn = null;
        ConfigurationSection s = plugin.getData().getConfigurationSection("spawn");
        if (s == null) return;
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) return;
        spawn = new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("setspawn")) {
            Location l = p.getLocation();
            plugin.getData().set("spawn.world", l.getWorld().getName());
            plugin.getData().set("spawn.x", l.getX());
            plugin.getData().set("spawn.y", l.getY());
            plugin.getData().set("spawn.z", l.getZ());
            plugin.getData().set("spawn.yaw", l.getYaw());
            plugin.getData().set("spawn.pitch", l.getPitch());
            plugin.saveData();
            spawn = l.clone();
            p.sendMessage("§aSpawn has been set.");
            return true;
        }

        // /spawn
        if (spawn == null) {
            p.sendMessage("§cSpawn is not set.");
            return true;
        }
        if (combat.isInCombat(p)) {
            p.sendMessage("§cYou can't use /spawn while in combat.");
            return true;
        }
        if (teleporting.containsKey(p.getUniqueId())) return true;

        BukkitTask task = new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (!p.isOnline() || combat.isInCombat(p)) {
                    stop(p.getUniqueId());
                    return;
                }
                if (count < 0) {
                    stop(p.getUniqueId());
                    p.teleport(spawn);
                    p.sendActionBar(Component.empty());
                    return;
                }
                p.sendActionBar(CombatManager.LEGACY.deserialize(
                        "&aTeleport in &f" + count + " &cdon't move"));
                count--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        teleporting.put(p.getUniqueId(), task);
        return true;
    }

    private void stop(UUID id) {
        BukkitTask t = teleporting.remove(id);
        if (t != null) t.cancel();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        Player p = e.getPlayer();
        if (teleporting.containsKey(p.getUniqueId())) {
            stop(p.getUniqueId());
            p.sendActionBar(Component.empty());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stop(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (spawn != null) e.setRespawnLocation(spawn);
    }
}

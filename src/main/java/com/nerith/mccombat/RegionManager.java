package com.nerith.mccombat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegionManager implements CommandExecutor, Listener {

    private record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(Location l) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private final NerithMcCombat plugin;
    private final NamespacedKey wandKey;
    private final Map<String, Region> regions = new HashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public RegionManager(NerithMcCombat plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "deathwand");
        load();
    }

    public void load() {
        regions.clear();
        ConfigurationSection sec = plugin.getData().getConfigurationSection("regions");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(name);
            if (r == null) continue;
            regions.put(name.toLowerCase(), new Region(r.getString("world", ""),
                    r.getInt("minX"), r.getInt("minY"), r.getInt("minZ"),
                    r.getInt("maxX"), r.getInt("maxY"), r.getInt("maxZ")));
        }
    }

    private void save() {
        plugin.getData().set("regions", null);
        for (Map.Entry<String, Region> e : regions.entrySet()) {
            String path = "regions." + e.getKey() + ".";
            Region r = e.getValue();
            plugin.getData().set(path + "world", r.world());
            plugin.getData().set(path + "minX", r.minX());
            plugin.getData().set(path + "minY", r.minY());
            plugin.getData().set(path + "minZ", r.minZ());
            plugin.getData().set(path + "maxX", r.maxX());
            plugin.getData().set(path + "maxY", r.maxY());
            plugin.getData().set(path + "maxZ", r.maxZ());
        }
        plugin.saveData();
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("deathwand")) {
            ItemStack wand = new ItemStack(Material.BLAZE_ROD);
            ItemMeta meta = wand.getItemMeta();
            meta.displayName(Component.text("Death Wand", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
            p.getInventory().addItem(wand);
            p.sendMessage("§7Left click = Pos1 | Right click = Pos2");
            return true;
        }

        // /deathregion
        if (args.length == 0) {
            p.sendMessage("§cUsage: /deathregion <name> | delete <name> | list");
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            p.sendMessage("§7Regions: §f" + String.join(", ", regions.keySet()));
            return true;
        }

        if (args[0].equalsIgnoreCase("delete")) {
            if (args.length < 2 || regions.remove(args[1].toLowerCase()) == null) {
                p.sendMessage("§cRegion not found.");
                return true;
            }
            save();
            p.sendMessage("§aRegion deleted.");
            return true;
        }

        Location a = pos1.get(p.getUniqueId());
        Location b = pos2.get(p.getUniqueId());
        if (a == null || b == null) {
            p.sendMessage("§cSelect Pos1 and Pos2 with /deathwand first.");
            return true;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            p.sendMessage("§cPositions must be in the same world.");
            return true;
        }
        Region r = new Region(a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()), Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
        regions.put(args[0].toLowerCase(), r);
        save();
        p.sendMessage("§aDeath region '" + args[0] + "' created.");
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null) return;
        if (!isWand(e.getItem())) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("nerithmccombat.admin")) return;

        Location l = e.getClickedBlock().getLocation();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            pos1.put(p.getUniqueId(), l);
            p.sendMessage("§aPos1 set: §f" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            pos2.put(p.getUniqueId(), l);
            p.sendMessage("§aPos2 set: §f" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
        }
    }

    private void check(Player p, Location l) {
        if (regions.isEmpty() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.hasPermission("nerithmccombat.bypass")) return;
        for (Region r : regions.values()) {
            if (r.contains(l)) {
                p.setHealth(0);
                return;
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        check(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) check(p, p.getLocation());
        }, 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) check(p, p.getLocation());
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pos1.remove(e.getPlayer().getUniqueId());
        pos2.remove(e.getPlayer().getUniqueId());
    }
}

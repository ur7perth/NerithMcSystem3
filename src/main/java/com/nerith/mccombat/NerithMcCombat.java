package com.nerith.mccombat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class NerithMcCombat extends JavaPlugin implements Listener, CommandExecutor {

    private File dataFile;
    private YamlConfiguration data;
    private CombatManager combat;
    private SpawnManager spawn;
    private RegionManager regions;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        combat = new CombatManager(this);
        spawn = new SpawnManager(this, combat);
        regions = new RegionManager(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(combat, this);
        Bukkit.getPluginManager().registerEvents(spawn, this);
        Bukkit.getPluginManager().registerEvents(regions, this);

        getCommand("setspawn").setExecutor(spawn);
        getCommand("spawn").setExecutor(spawn);
        getCommand("deathwand").setExecutor(regions);
        getCommand("deathregion").setExecutor(regions);
        getCommand("nmcreload").setExecutor(this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    public void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public YamlConfiguration getData() {
        return data;
    }

    public void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void doReload() {
        reloadConfig();
        loadData();
        combat.load();
        spawn.load();
        regions.load();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        doReload();
        sender.sendMessage("§aNerithMcCombat reloaded.");
        return true;
    }

    // Make /reload reload this plugin instead of the whole server
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerReload(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().trim().toLowerCase();
        if (m.equals("/reload") || m.startsWith("/reload ")) {
            Player p = e.getPlayer();
            if (!p.hasPermission("nerithmccombat.admin")) return;
            e.setCancelled(true);
            doReload();
            p.sendMessage("§aNerithMcCombat reloaded.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsoleReload(ServerCommandEvent e) {
        String m = e.getCommand().trim().toLowerCase();
        if (m.equals("reload") || m.startsWith("reload ")) {
            e.setCancelled(true);
            doReload();
            e.getSender().sendMessage("§aNerithMcCombat reloaded.");
        }
    }
}

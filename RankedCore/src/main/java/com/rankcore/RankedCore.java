package com.rankcore;

import com.rankcore.booster.BoosterManager;
import com.rankcore.command.EloCommand;
import com.rankcore.database.Database;
import com.rankcore.database.MySQLDatabase;
import com.rankcore.database.SQLiteDatabase;
import com.rankcore.gui.GuiManager;
import com.rankcore.integration.VaultHook;
import com.rankcore.listener.CombatListener;
import com.rankcore.listener.ConnectionListener;
import com.rankcore.placeholder.RankedExpansion;
import com.rankcore.player.PlayerManager;
import com.rankcore.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class RankedCore extends JavaPlugin {
    private static RankedCore instance;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private Database database;
    private RankManager rankManager;
    private PlayerManager playerManager;
    private BoosterManager boosterManager;
    private GuiManager guiManager;
    private VaultHook vaultHook;

    public static RankedCore get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        for (String name : new String[]{"config.yml", "ranks.yml", "boosters.yml", "messages.yml", "gui.yml", "database.yml"})
            saveResource(name, false);
        reloadAllConfigs();
        try {
            database = "MYSQL".equalsIgnoreCase(fileCfg("database.yml").getString("type"))
                    ? new MySQLDatabase(this) : new SQLiteDatabase(this);
            database.init();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Database init failed; using volatile storage", e);
        }
        vaultHook = new VaultHook(this);
        rankManager = new RankManager(this);
        boosterManager = new BoosterManager(this);
        playerManager = new PlayerManager(this);
        guiManager = new GuiManager(this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ConnectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);
        EloCommand cmd = new EloCommand(this);
        var map = getCommand("elo");
        map.setExecutor(cmd);
        map.setTabCompleter(cmd);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
            new RankedExpansion(this).register();
        boosterManager.startTicker();
        long autosave = fileCfg("config.yml").getLong("performance.autosave-minutes", 5) * 1200L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> playerManager.saveAll(false), autosave, autosave);
        getLogger().info("RankedCore enabled. Elo ladder: LT5 -> HT1. LuckPerms untouched.");
    }

    @Override
    public void onDisable() {
        if (playerManager != null) playerManager.saveAll(true);
        if (boosterManager != null) boosterManager.save();
        if (database != null) database.close();
    }

    public void reloadAllConfigs() {
        configs.clear();
        reloadConfig();
        configs.put("config.yml", getConfig());
        for (String name : new String[]{"ranks.yml", "boosters.yml", "messages.yml", "gui.yml", "database.yml"}) {
            File f = new File(getDataFolder(), name);
            configs.put(name, YamlConfiguration.loadConfiguration(f));
        }
    }

    public FileConfiguration fileCfg(String name) { return configs.computeIfAbsent(name, n -> getConfig()); }

    public Database database() { return database; }
    public RankManager ranks() { return rankManager; }
    public PlayerManager players() { return playerManager; }
    public BoosterManager boosters() { return boosterManager; }
    public GuiManager guis() { return guiManager; }
    public VaultHook vault() { return vaultHook; }
}

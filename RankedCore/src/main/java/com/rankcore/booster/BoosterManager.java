package com.rankcore.booster;

import com.rankcore.RankedCore;
import com.rankcore.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class BoosterManager {
    private final RankedCore plugin;
    private final List<Booster> active = new CopyOnWriteArrayList<>();
    private final List<Booster> scheduled = new ArrayList<>();
    private final BossBar bar;
    private File dataFile;

    public BoosterManager(RankedCore plugin) {
        this.plugin = plugin;
        this.bar = BossBar.bossBar(Msg.parse("<gold>Elo Booster</gold>"), 0f,
                BossBar.Color.valueOf(plugin.fileCfg("boosters.yml").getString("bossbar.color", "PURPLE")),
                BossBar.Overlay.PROGRESS);
        load();
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "boosters-data.yml");
        if (!dataFile.exists()) plugin.saveResource("boosters-data.yml", false);
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        for (Map<?, ?> m : data.getMapList("active")) {
            long end = (long) m.get("end");
            if (end > System.currentTimeMillis())
                active.add(new Booster((String) m.get("id"), (String) m.get("name"),
                        ((Number) m.get("multiplier")).doubleValue(), (long) m.get("start"), end));
        }
        scheduled.clear();
        ConfigurationSection ev = plugin.fileCfg("boosters.yml").getConfigurationSection("events");
        if (ev != null) for (String id : ev.getKeys(false)) {
            ConfigurationSection e = ev.getConfigurationSection(id);
            try {
                LocalDateTime start = LocalDateTime.parse(Objects.requireNonNull(e.getString("start")));
                long startMs = start.atZone(ZoneId.systemDefault()).toInstant().toMillis();
                long endMs = startMs + e.getLong("duration-minutes") * 60000L;
                scheduled.add(new Booster(id, e.getString("name", id), e.getDouble("multiplier"), startMs, endMs));
            } catch (Exception ex) { plugin.getLogger().warning("Bad scheduled booster " + id + ": " + ex.getMessage()); }
        }
    }

    public void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Booster b : active) out.add(Map.of(
                "id", b.id, "name", b.name, "multiplier", b.multiplier, "start", b.start, "end", b.end));
        FileConfiguration data = new YamlConfiguration();
        data.set("active", out);
        try { data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Could not save boosters: " + e.getMessage()); }
    }

    public Booster start(String name, double multiplier, long durationMs) {
        Booster b = new Booster(UUID.randomUUID().toString().substring(0, 8), name, multiplier,
                System.currentTimeMillis(), System.currentTimeMillis() + durationMs);
        active.add(b);
        save();
        if (plugin.fileCfg("boosters.yml").getBoolean("announce-start", true))
            Bukkit.broadcast(Msg.parse(Msg.prefix(plugin) + Msg.color(plugin, "booster-start")
                    .replace("%name%", name).replace("%multiplier%", String.valueOf(multiplier))
                    .replace("%duration%", fmt(durationMs))));
        return b;
    }

    public boolean stop() {
        if (active.isEmpty()) return false;
        active.clear();
        save();
        if (plugin.fileCfg("boosters.yml").getBoolean("announce-end", true))
            Bukkit.broadcast(Msg.parse(Msg.prefix(plugin) + Msg.color(plugin, "booster-end").replace("%name%", "manual")));
        return true;
    }

    public boolean cancel(String id) {
        boolean r = active.removeIf(b -> b.id.equalsIgnoreCase(id)) | scheduled.removeIf(b -> b.id.equalsIgnoreCase(id));
        if (r) save();
        return r;
    }

    public List<Booster> active() { return Collections.unmodifiableList(active); }
    public List<Booster> scheduled() { return Collections.unmodifiableList(scheduled); }

    public double gainMultiplier() {
        String mode = plugin.fileCfg("boosters.yml").getString("stacking", "MULTIPLICATIVE");
        double base = 1.0;
        if (mode.equalsIgnoreCase("HIGHEST"))
            return active.stream().mapToDouble(b -> b.multiplier).max().orElse(1.0);
        for (Booster b : active) {
            if (mode.equalsIgnoreCase("ADDITIVE")) base += (b.multiplier - 1.0);
            else base *= b.multiplier;
        }
        return base;
    }

    public boolean appliesToPenalties() { return plugin.fileCfg("boosters.yml").getBoolean("apply-to-death-penalties", false); }

    public long timeLeftMs() { return active.stream().mapToLong(Booster::remaining).max().orElse(0); }

    public static String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%dh %dm", s / 3600, (s % 3600) / 60);
    }

    public void startTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Booster b : active) {
                if (now >= b.end) {
                    active.remove(b);
                    save();
                    Bukkit.broadcast(Msg.parse(Msg.prefix(plugin) + Msg.color(plugin, "booster-end").replace("%name%", b.name)));
                }
            }
            for (Booster b : new ArrayList<>(scheduled)) {
                if (now >= b.start && now < b.end) { active.add(b); scheduled.remove(b); save(); }
            }
            boolean bossbar = plugin.fileCfg("boosters.yml").getBoolean("bossbar.enabled", true);
            long maxEnd = active.stream().mapToLong(b -> b.end).max().orElse(0);
            long minStart = active.stream().mapToLong(b -> b.start).min().orElse(now);
            if (!active.isEmpty()) {
                float progress = Math.max(0f, Math.min(1f, (float) (maxEnd - now) / Math.max(1, maxEnd - minStart)));
                bar.progress(progress);
                String names = String.join(" + ", active.stream().map(b -> b.name + " " + b.multiplier + "x").toList());
                bar.name(Msg.parse("<gold>Elo Booster:</gold> <white>" + names + "</white> <gray>・</gray> <yellow>" + fmt(maxEnd - now) + "</yellow>"));
                if (bossbar) for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
                long every = plugin.fileCfg("boosters.yml").getLong("actionbar-seconds", 60) * 20L;
                if (Bukkit.getCurrentTick() % every == 0)
                    for (Player p : Bukkit.getOnlinePlayers())
                        p.sendActionBar(Msg.parse("<light_purple>⚡ " + names + " ・ " + fmt(maxEnd - now) + " remaining</light_purple>"));
            } else if (bossbar) {
                for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
            }
        }, 20L, 20L);
    }
}
(Create src/main/resources/boosters-data.yml with content active: [].)

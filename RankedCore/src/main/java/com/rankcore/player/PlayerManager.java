package com.rankcore.player;

import com.rankcore.RankedCore;
import com.rankcore.elo.EloCalculator;
import com.rankcore.rank.Rank;
import com.rankcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private final RankedCore plugin;
    private final Map<UUID, RankedPlayer> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    private final Map<String, Long> killCooldowns = new ConcurrentHashMap<>();
    private final Map<String, List<String[]>> historyCache = new ConcurrentHashMap<>();
    private List<String[]> leaderboardCache = new ArrayList<>();
    private Map<UUID, Integer> positionCache = new HashMap<>();
    private long leaderboardStamp;

    public PlayerManager(RankedCore plugin) {
        this.plugin = plugin;
    }

    public RankedPlayer get(UUID uuid) { return cache.get(uuid); }

    public CompletableFuture<RankedPlayer> load(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (locks.computeIfAbsent(uuid, u -> new Object())) {
                RankedPlayer rp = cache.computeIfAbsent(uuid, u -> {
                    RankedPlayer fresh = new RankedPlayer(uuid, name);
                    fresh.addElo(plugin.fileCfg("config.yml").getInt("elo.starting-elo"));
                    fresh.highestRankId(plugin.ranks().rankFor(fresh.elo()).id());
                    return fresh;
                });
                rp.name(name);
                plugin.database().loadInto(rp);
                rp.touch();
                return rp;
            }
        });
    }

    public void unload(UUID uuid, boolean remove) {
        RankedPlayer rp = cache.get(uuid);
        if (rp == null) return;
        synchronized (locks.computeIfAbsent(uuid, u -> new Object())) {
            CompletableFuture.runAsync(() -> plugin.database().save(rp))
                    .thenRun(rp::clearDirty);
            if (remove) cache.remove(uuid);
        }
    }

    public void saveAll(boolean syncWait) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (RankedPlayer rp : cache.values())
            futures.add(CompletableFuture.runAsync(() -> plugin.database().save(rp)));
        if (syncWait) CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public boolean onCooldown(Player killer, Player victim) {
        if (killer.hasPermission("ranked.bypass.cooldown")) return false;
        long cd = plugin.fileCfg("config.yml").getLong("combat.kill-cooldown-seconds", 300) * 1000L;
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        Long last = killCooldowns.get(key);
        return last != null && System.currentTimeMillis() - last < cd;
    }

    /** Single synchronized entry point for a legitimate PvP kill. Never double-processes: cooldown map acts as dedupe. */
    public void handleKill(Player killer, Player victim) {
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        killCooldowns.put(key, System.currentTimeMillis());
        RankedPlayer k = cache.get(killer.getUniqueId()), v = cache.get(victim.getUniqueId());
        if (k == null || v == null) return; // data not loaded yet; ignore (restart-safe)
        synchronized (locks.computeIfAbsent(killer.getUniqueId(), u -> new Object())) {
            synchronized (locks.computeIfAbsent(victim.getUniqueId(), u -> new Object())) {
                FileConfiguration cfg = plugin.fileCfg("config.yml");
                EloCalculator.Result r = EloCalculator.compute(k.elo(), v.elo(), k.kills() + k.deaths(), cfg);
                double mult = cfg.getDouble("elo.multiplier", 1.0) * plugin.boosters().gainMultiplier();
                int gain = (int) Math.round(r.gain() * mult);
                int loss = plugin.boosters().appliesToPenalties() ? (int) Math.round(r.loss() * mult) : r.loss();
                k.addElo(gain); v.addElo(-loss);
                k.onKill(); v.onDeath();
                plugin.ranks().applyRankChange(k);
                plugin.ranks().applyRankChange(v);
                UUID ku = k.uuid(), vu = v.uuid();
                String vn = v.name(), kn = k.name();
                int g = gain, l = loss;
                CompletableFuture.runAsync(() -> {
                    plugin.database().insertHistory(ku, +g, "KILL", vn);
                    plugin.database().insertHistory(vu, -l, "DEATH", kn);
                    plugin.database().save(cache.get(ku));
                    plugin.database().save(cache.get(vu));
                });
                audit(kn + " killed " + vn + "  +" + g + " / -" + l + "  (Elo " + k.elo() + " vs " + v.elo() + ")");
                killer.sendMessage(Msg.parse(Msg.prefix(plugin) + Msg.color(plugin, "kill")
                        .replace("%amount%", String.valueOf(g)).replace("%victim%", vn)
                        .replace("%victim_elo%", String.valueOf(v.elo())).replace("%streak%", String.valueOf(k.streak()))));
                victim.sendMessage(Msg.parse(Msg.prefix(plugin) + Msg.color(plugin, "death")
                        .replace("%amount%", String.valueOf(l)).replace("%killer%", kn)));
                if (cfg.getBoolean("economy.enabled")) {
                    plugin.vault().deposit(killer, cfg.getDouble("economy.kill-reward"));
                    plugin.vault().withdraw(victim, cfg.getDouble("economy.death-loss"));
                }
                announceStreak(killer, k.streak());
                milestoneCheck(killer, k);
            }
        }
    }

    private void announceStreak(Player p, int streak) {
        String msg = plugin.fileCfg("config.yml").getString("streak-announce." + streak);
        if (msg != null) Bukkit.broadcast(Msg.parse(msg.replace("%player%", p.getName())));
    }

    private void milestoneCheck(Player p, RankedPlayer rp) {
        if (!plugin.fileCfg("config.yml").getBoolean("economy.milestone-rewards")) return;
        for (String m : plugin.fileCfg("config.yml").getConfigurationSection("milestones").getKeys(false)) {
            if (rp.elo() == Integer.parseInt(m)) {
                plugin.vault().deposit(p, Double.parseDouble(plugin.fileCfg("config.yml").getString("milestones." + m).split(":")[1]));
                return;
            }
        }
    }

    public CompletableFuture<List<String[]>> history(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String[]> h = plugin.database().history(uuid, plugin.fileCfg("config.yml").getInt("performance.history-limit", 8));
            historyCache.put(uuid.toString(), new ArrayList<>(h));
            return h;
        });
    }

    public void refreshLeaderboard() {
        if (System.currentTimeMillis() - leaderboardStamp < plugin.fileCfg("config.yml").getLong("leaderboard.cache-seconds", 120) * 1000L
                && !leaderboardCache.isEmpty()) return;
        leaderboardStamp = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            leaderboardCache = plugin.database().topElo(100);
            positionCache = plugin.database().positions(leaderboardCache);
        });
    }

    public List<String[]> leaderboard() { refreshLeaderboard(); return leaderboardCache; }
    public int position(UUID uuid) { refreshLeaderboard(); return positionCache.getOrDefault(uuid, -1); }

    private void audit(String line) {
        CompletableFuture.runAsync(() -> plugin.database().audit(line));
    }
}

package com.rankcore.rank;

import com.rankcore.RankedCore;
import com.rankcore.player.RankedPlayer;
import com.rankcore.util.Effects;
import com.rankcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public class RankManager {
    private final RankedCore plugin;
    private final List<Rank> ladder = new ArrayList<>();
    private final Map<UUID, String> currentRankCache = new HashMap<>();

    public RankManager(RankedCore plugin) { this.plugin = plugin; load(); }

    public void load() {
        ladder.clear();
        ConfigurationSection sec = plugin.fileCfg("ranks.yml").getConfigurationSection("ranks");
        if (sec == null) { plugin.getLogger().severe("ranks.yml has no 'ranks' section!"); return; }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(id);
            ladder.add(new Rank(
                    id, r.getInt("min-elo"), r.getString("display", id), r.getString("suffix", id),
                    r.getString("rank-up-message", ""), r.getString("rank-down-message", ""),
                    r.getString("rank-up-sound", ""), r.getString("rank-down-sound", ""),
                    r.getBoolean("broadcast", false), r.getString("particles-up", ""),
                    r.getStringList("rewards")));
        }
        ladder.sort(Comparator.comparingInt(Rank::minElo));
        if (ladder.size() != 10) plugin.getLogger().warning("Ladder has " + ladder.size() + " ranks (expected 10).");
    }

    public List<Rank> ladder() { return Collections.unmodifiableList(ladder); }

    public Rank rankFor(int elo) {
        Rank best = ladder.isEmpty() ? null : ladder.get(0);
        for (Rank r : ladder) if (elo >= r.minElo()) best = r;
        return best;
    }

    public Rank next(Rank r) {
        int i = ladder.indexOf(r);
        return (i >= 0 && i + 1 < ladder.size()) ? ladder.get(i + 1) : null;
    }

    public Rank byId(String id) { return ladder.stream().filter(r -> r.id().equalsIgnoreCase(id)).findFirst().orElse(null); }

    public int index(String id) {
        for (int i = 0; i < ladder.size(); i++) if (ladder.get(i).id().equalsIgnoreCase(id)) return i;
        return -1;
    }

    public String currentRankId(UUID uuid, int elo) { return currentRankCache.computeIfAbsent(uuid, u -> rankFor(elo).id()); }

    /** Recalculates rank after an Elo change; handles messages, sounds, particles, broadcast, rewards, highest-rank tracking. */
    public void applyRankChange(RankedPlayer rp) {
        Rank now = rankFor(rp.elo());
        String oldId = currentRankCache.put(rp.uuid(), now.id());
        if (oldId == null || oldId.equalsIgnoreCase(now.id())) { trackHighest(rp, now); return; }
        Rank old = byId(oldId);
        int cmp = Integer.compare(index(now.id()), index(oldId));
        Player p = Bukkit.getPlayer(rp.uuid());
        trackHighest(rp, now);
        if (p == null) return;
        if (cmp > 0) {
            Msg.sendRaw(p, plugin.fileCfg("ranks.yml").getString("plugin.prefix", "") + now.upMsg());
            Effects.playSound(p, now.upSound());
            Effects.particles(p, now.particles());
            if (now.broadcast())
                Bukkit.broadcast(Msg.parse(plugin.fileCfg("messages.yml").getString("rank-broadcast", "")
                        .replace("%player%", p.getName()).replace("%ranked_rank_suffix%", now.suffix())));
            for (String reward : now.rewards()) grant(p, reward);
        } else {
            Msg.sendRaw(p, plugin.fileCfg("messages.yml").getString("plugin.prefix", "") + now.downMsg());
            Effects.playSound(p, now.downSound());
        }
    }

    private void trackHighest(RankedPlayer rp, Rank now) {
        Rank highest = rp.highestRankId() == null ? null : byId(rp.highestRankId());
        if (highest == null || index(now.id()) > index(highest.id())) rp.highestRankId(now.id());
    }

    private void grant(Player p, String reward) {
        if (reward.startsWith("money:")) {
            plugin.vault().deposit(p, Double.parseDouble(reward.substring(6)));
        } else if (reward.startsWith("console:")) {
            String cmd = reward.substring(8).replace("%player%", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }
}

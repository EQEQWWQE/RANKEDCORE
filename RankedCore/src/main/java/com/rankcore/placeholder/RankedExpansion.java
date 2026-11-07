package com.rankcore.placeholder;

import com.rankcore.RankedCore;
import com.rankcore.player.RankedPlayer;
import com.rankcore.rank.Rank;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

public class RankedExpansion extends PlaceholderExpansion {
    private final RankedCore plugin;

    public RankedExpansion(RankedCore plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "ranked"; }
    @Override public String getAuthor() { return "RankedCore"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer op, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        Player online = op != null ? op.getPlayer() : null;
        RankedPlayer rp = op != null ? plugin.players().get(op.getUniqueId()) : null;

        // offline-player suffixed variants: %ranked_elo_<player>%
        for (String suffix : new String[]{"_elo", "_rank", "_rank_suffix", "_kills", "_deaths"}) {
            if (key.endsWith(suffix) && key.length() > suffix.length()) {
                String name = key.substring(0, key.length() - suffix.length());
                Player t = Bukkit.getPlayerExact(name);
                if (t != null) { rp = plugin.players().get(t.getUniqueId()); key = suffix.substring(1); }
                else return "-";
                break;
            }
        }

        if (rp == null) return "-";
        Rank rank = plugin.ranks().rankFor(rp.elo());
        Rank next = plugin.ranks().next(rank);
        return switch (key) {
            case "elo" -> String.valueOf(rp.elo());
            case "rank" -> rank != null ? rank.id() : "-";
            case "rank_display" -> rank != null ? rank.display() : "-";
            case "rank_suffix" -> rank != null ? rank.suffix() : "";
            case "prefix" -> rank != null ? rank.suffix() : ""; // alias; LP prefix stays untouched
            case "kills" -> String.valueOf(rp.kills());
            case "deaths" -> String.valueOf(rp.deaths());
            case "kdr" -> String.valueOf(rp.kdr());
            case "wins" -> String.valueOf(rp.wins());
            case "losses" -> String.valueOf(rp.losses());
            case "peak_elo" -> String.valueOf(rp.peakElo());
            case "highest_rank" -> rp.highestRankId() != null ? rp.highestRankId() : "-";
            case "position" -> { int p = plugin.players().position(rp.uuid()); yield p > 0 ? "#" + p : "-"; }
            case "next_rank" -> next != null ? next.display() : "MAX";
            case "elo_to_next_rank" -> next != null ? String.valueOf(Math.max(0, next.minElo() - rp.elo())) : "0";
            case "elo_gained" -> String.valueOf(rp.gained());
            case "elo_lost" -> String.valueOf(rp.lost());
            case "killstreak" -> String.valueOf(rp.streak());
            case "winstreak" -> String.valueOf(rp.winStreak());
            case "booster_multiplier" -> String.format("%.2f", plugin.boosters().gainMultiplier());
            case "booster_time_left" -> com.rankcore.booster.BoosterManager.fmt(plugin.boosters().timeLeftMs());
            case "elo_change" -> (rp.lastChange() >= 0 ? "+" : "") + rp.lastChange();
            default -> null;
        };
    }
}

package com.rankcore.command;

import com.rankcore.RankedCore;
import com.rankcore.player.RankedPlayer;
import com.rankcore.rank.Rank;
import com.rankcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class EloCommand implements CommandExecutor, TabCompleter {
    private final RankedCore plugin;

    public EloCommand(RankedCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { return stats(sender, sender instanceof Player p ? p.getName() : null); }
        switch (args[0].toLowerCase()) {
            case "stats", "profile" -> stats(sender, args.length > 1 ? args[1] : (sender instanceof Player p ? p.getName() : null));
            case "rank" -> { openRanks(sender); return true; }
            case "top" -> { openTop(sender, args.length > 1 ? parseInt(args[1], 1) : 1); return true; }
            case "history" -> history(sender, sender instanceof Player p ? p.getUniqueId() : null);
            case "boosters" -> { openBoosters(sender); return true; }
            case "help" -> help(sender);
            case "admin" -> admin(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> help(sender);
        }
        return true;
    }

    private boolean stats(CommandSender sender, String name) {
        if (!perm(sender, "ranked.stats")) return true;
        if (name == null) { Msg.send(sender, plugin, "player-only"); return true; }
        Player target = Bukkit.getPlayerExact(name);
        RankedPlayer rp = target != null ? plugin.players().get(target.getUniqueId()) : null;
        if (rp == null) { Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "unknown-player").replace("%player%", name));; return true; }
        Rank rank = plugin.ranks().rankFor(rp.elo());
        Rank next = plugin.ranks().next(rank);
        String prefix = Msg.prefix(plugin);
        for (String line : plugin.fileCfg("messages.yml").getStringList("elo-summary")) {
            line = line.replace("%ranked_rank_suffix%", rank.suffix()).replace("%ranked_elo%", String.valueOf(rp.elo()))
                    .replace("%ranked_peak_elo%", String.valueOf(rp.peakElo()))
                    .replace("%ranked_kills%", String.valueOf(rp.kills())).replace("%ranked_deaths%", String.valueOf(rp.deaths()))
                    .replace("%ranked_kdr%", String.valueOf(rp.kdr()))
                    .replace("%ranked_next_rank%", next != null ? next.display() : "MAX")
                    .replace("%ranked_elo_to_next_rank%", next != null ? String.valueOf(Math.max(0, next.minElo() - rp.elo())) : "0");
            sender.sendMessage(Msg.parse(prefix + line));
        }
        return true;
    }

    private void history(CommandSender sender, UUID uuid) {
        if (!perm(sender, "ranked.history") || uuid == null) return;
        plugin.players().history(uuid).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (list.isEmpty()) { Msg.send(sender, plugin, "history-empty"); return; }
            Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "history-header").replace("%player%", sender.getName()));
            for (String[] h : list)
                Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "history-entry")
                        .replace("%time%", Msg.fmtTime(Long.parseLong(h[0]))).replace("%signed%", h[1])
                        .replace("%reason%", h[2]).replace("%other%", h[3] != null ? h[3] : "-"));
        }));
    }

    private void openRanks(CommandSender sender) {
        if (sender instanceof Player p && perm(sender, "ranked.use")) plugin.guis().openRanks(p);
    }

    private void openTop(CommandSender sender, int page) {
        if (sender instanceof Player p && perm(sender, "ranked.top")) plugin.guis().openLeaderboard(p, page - 1);
        else if (!(sender instanceof Player)) {
            List<String[]> top = plugin.players().leaderboard();
            for (int i = 0; i < Math.min(10, top.size()); i++)
                sender.sendMessage("#" + (i + 1) + " " + top.get(i)[0] + " - " + top.get(i)[1]);
        }
    }

    private void openBoosters(CommandSender sender) {
        if (sender instanceof Player p && perm(sender, "ranked.boosters")) plugin.guis().openBoosters(p);
    }

    private void help(CommandSender sender) {
        Msg.sendRaw(sender, "<gold>/elo</gold> <gray>- your competitive summary</gray>");
        Msg.sendRaw(sender, "<gold>/elo stats [player]</gold> <gray>- statistics</gray>");
        Msg.sendRaw(sender, "<gold>/elo top [page]</gold> <gray>- leaderboard GUI</gray>");
        Msg.sendRaw(sender, "<gold>/elo rank</gold> <gray>- rank ladder GUI</gray>");
        Msg.sendRaw(sender, "<gold>/elo history</gold> <gray>- recent Elo changes</gray>");
        Msg.sendRaw(sender, "<gold>/elo boosters</gold> <gray>- booster GUI</gray>");
        if (sender.hasPermission("ranked.admin"))
            Msg.sendRaw(sender, "<gold>/elo admin <set|add|remove|reset|resetall|rank|booster|reload|debug></gold>");
    }

    private boolean admin(CommandSender sender, String[] a) {
        if (!perm(sender, "ranked.admin")) return true;
        if (a.length == 0) { help(sender); return true; }
        switch (a[0].toLowerCase()) {
            case "set" -> modify(sender, a, "set");
            case "add" -> modify(sender, a, "add");
            case "remove" -> modify(sender, a, "remove");
            case "reset" -> reset(sender, a.length > 1 ? a[1] : null);
            case "resetall" -> Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                long n = plugin.database().count();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    RankedPlayer rp = plugin.players().get(p.getUniqueId());
                    if (rp != null) { rp.addElo(-rp.elo()); rp.onDeath(); rp.onKill(); }
                }
                Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "elo-resetall").replace("%count%", String.valueOf(n)));
            });
            case "rank" -> setRank(sender, a);
            case "reload" -> { plugin.reloadAllConfigs(); plugin.ranks().load(); Msg.send(sender, plugin, "reload"); }
            case "stats" -> stats(sender, a.length > 1 ? a[1] : null);
            case "history" -> {
                Player t = Bukkit.getPlayerExact(a.length > 1 ? a[1] : "");
                if (t != null) history(sender, t.getUniqueId());
            }
            case "debug" -> {
                Player t = Bukkit.getPlayerExact(a.length > 1 ? a[1] : (sender instanceof Player p ? p.getName() : ""));
                if (t == null) { Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "unknown-player").replace("%player%", ""));; break; }
                RankedPlayer rp = plugin.players().get(t.getUniqueId());
                Msg.sendRaw(sender, Msg.color(plugin, "debug")
                        .replace("%player%", t.getName()).replace("%elo%", rp != null ? String.valueOf(rp.elo()) : "?")
                        .replace("%rank%", plugin.ranks().rankFor(rp != null ? rp.elo() : 0).id())
                        .replace("%cached%", String.valueOf(rp != null)).replace("%kills%", rp != null ? String.valueOf(rp.kills()) : "?")
                        .replace("%deaths%", rp != null ? String.valueOf(rp.deaths()) : "?"));
            }
            case "booster" -> booster(sender, Arrays.copyOfRange(a, 1, a.length));
            default -> help(sender);
        }
        return true;
    }

    private void modify(CommandSender sender, String[] a, String mode) {
        if (a.length < 3) { Msg.sendRaw(sender, "Usage: /elo admin " + mode + " <player> <amount>"); return; }
        String perm = "ranked.admin." + mode;
        if (!perm(sender, perm)) return;
        int amount = parseInt(a[2], -1);
        if (amount < 0) { Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "invalid-number").replace("%input%", a[2]));; return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        plugin.players().load(target.getUniqueId(), a[1]).thenAccept(rp -> Bukkit.getScheduler().runTask(plugin, () -> {
            switch (mode) {
                case "set" -> { rp.addElo(amount - rp.elo()); Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "elo-set").replace("%player%", a[1]).replace("%amount%", a[2]));; }
                case "add" -> { rp.addElo(amount); Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "elo-added").replace("%player%", a[1]).replace("%amount%", a[2]).replace("%new%", String.valueOf(rp.elo()))); }
                case "remove" -> { rp.addElo(-amount); Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "elo-removed").replace("%player%", a[1]).replace("%amount%", a[2]).replace("%new%", String.valueOf(rp.elo());)); }
            }
            plugin.ranks().applyRankChange(rp);
            rp.markDirty();
        }));
    }

    private void reset(CommandSender sender, String name) {
        if (!perm(sender, "ranked.admin.reset") || name == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        plugin.players().load(target.getUniqueId(), name).thenAccept(rp -> Bukkit.getScheduler().runTask(plugin, () -> {
            rp.addElo(-rp.elo()); rp.onDeath(); rp.onKill();
            plugin.ranks().applyRankChange(rp);
            Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "elo-reset").replace("%player%", name));;
        }));
    }

    private void setRank(CommandSender sender, String[] a) {
        if (!perm(sender, "ranked.admin.rank")) return;
        if (a.length < 3) { Msg.sendRaw(sender, "Usage: /elo admin rank <player> <rank>"); return; }
        Rank rank = plugin.ranks().byId(a[2]);
        if (rank == null) { Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "invalid-rank").replace("%input%", a[2]));; return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(a[1]);
        plugin.players().load(target.getUniqueId(), a[1]).thenAccept(rp -> Bukkit.getScheduler().runTask(plugin, () -> {
            rp.addElo(rank.minElo() - rp.elo());
            plugin.ranks().applyRankChange(rp);
            Msg.sendRaw(sender, "<green>Set " + a[1] + " to rank " + rank.id() + ".</green>");
        }));
    }

    private void booster(CommandSender sender, String[] a) {
        if (!perm(sender, "ranked.admin.booster")) return;
        if (a.length == 0) { Msg.sendRaw(sender, "Usage: /elo admin booster <start|stop|cancel>"); return; }
        switch (a[0].toLowerCase()) {
            case "start" -> {
                if (a.length < 3) { Msg.sendRaw(sender, "Usage: /elo admin booster start <multiplier> <duration like 60m/2h>"); return; }
                double mult;
                try { mult = Double.parseDouble(a[1]); } catch (NumberFormatException ex) {
                    Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "invalid-number").replace("%input%", a[1]));; return;
                }
                long ms = parseDuration(a[2]);
                if (ms <= 0) { Msg.sendRaw(sender, Msg.prefix(plugin) + Msg.color(plugin, "invalid-number").replace("%input%", a[2]));; return; }
                String name = a.length > 3 ? a[3] : mult + "x Booster";
                plugin.boosters().start(name, mult, ms);
                Msg.sendRaw(sender, "<green>Booster started: " + name + " for " + com.rankcore.booster.BoosterManager.fmt(ms) + "</green>");
            }
            case "stop" -> Msg.sendRaw(sender, plugin.boosters().stop() ? "<green>All boosters stopped.</green>" : "<gray>No active boosters.</gray>");
            case "cancel" -> {
                if (a.length < 2) { Msg.sendRaw(sender, "Usage: /elo admin booster cancel <id>"); return; }
                Msg.sendRaw(sender, plugin.boosters().cancel(a[1]) ? "<green>Cancelled " + a[1] + ".</green>" : "<red>No booster with id " + a[1] + ".</red>");
            }
            default -> Msg.sendRaw(sender, "Usage: /elo admin booster <start|stop|cancel>");
        }
    }

    private boolean perm(CommandSender s, String p) {
        if (s.hasPermission(p)) return true;
        Msg.send(s, plugin, "no-permission");
        return false;
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }

    private long parseDuration(String s) {
        try {
            char u = Character.toLowerCase(s.charAt(s.length() - 1));
            long n = Long.parseLong(s.substring(0, s.length() - (Character.isLetter(u) ? 1 : 0)));
            return switch (u) { case 's' -> n * 1000; case 'm' -> n * 60000; case 'h' -> n * 3600000; case 'd' -> n * 86400000; default -> n * 1000; };
        } catch (Exception e) { return -1; }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1)
            return filter(Arrays.asList("stats", "profile", "rank", "top", "history", "boosters", "help", "admin"), args[0]);
        if (args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2)
                return filter(Arrays.asList("set", "add", "remove", "reset", "resetall", "rank", "reload", "stats", "history", "debug", "booster"), args[1]);
            if (args.length == 3 && List.of("set", "add", "remove", "reset", "rank", "stats", "history", "debug").contains(args[1].toLowerCase()))
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            if (args.length == 4 && args[1].equalsIgnoreCase("rank"))
                return filter(plugin.ranks().ladder().stream().map(Rank::id).collect(Collectors.toList()), args[3]);
            if (args.length == 3 && args[1].equalsIgnoreCase("booster"))
                return filter(Arrays.asList("start", "stop", "cancel"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> opts, String pre) {
        return opts.stream().filter(o -> o.toLowerCase().startsWith(pre.toLowerCase())).collect(Collectors.toList());
    }
}

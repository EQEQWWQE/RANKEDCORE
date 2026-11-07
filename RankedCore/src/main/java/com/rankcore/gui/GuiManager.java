package com.rankcore.gui;

import com.rankcore.RankedCore;
import com.rankcore.player.RankedPlayer;
import com.rankcore.rank.Rank;
import com.rankcore.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class GuiManager implements Listener {
    private final RankedCore plugin;

    record Holder(Type type, int page, String extra) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    enum Type { PROFILE, RANKS, LEADERBOARD, BOOSTERS }

    public GuiManager(RankedCore plugin) { this.plugin = plugin; }

    private ItemStack item(String path, Material fallback) {
        ConfigurationSection s = plugin.fileCfg("gui.yml").getConfigurationSection("items." + path);
        Material m = Material.matchMaterial(s != null && s.getString("material") != null ? s.getString("material") : fallback.name());
        if (m == null) m = fallback;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (s != null && s.getString("name") != null) meta.displayName(Msg.parse(s.getString("name")));
        if (s != null && !s.getStringList("lore").isEmpty())
            meta.lore(s.getStringList("lore").stream().map(Msg::parse).toList());
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.parse(name));
        meta.lore(lore.stream().map(Msg::parse).toList());
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack filler = item("filler", Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
    }

    public void openProfile(Player viewer, Player target) {
        RankedPlayer rp = plugin.players().get(target.getUniqueId());
        Rank rank = plugin.ranks().rankFor(rp != null ? rp.elo() : 0);
        Rank next = plugin.ranks().next(rank);
        String title = plugin.fileCfg("gui.yml").getString("profile.title", "Profile").replace("%player%", target.getName());
        int size = plugin.fileCfg("gui.yml").getInt("profile.size", 54);
        Inventory inv = Bukkit.createInventory(new Holder(Type.PROFILE, 0, target.getName()), size, Msg.parse(title));
        ItemStack head = item("profile-head", Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(target);
        sm.displayName(Msg.parse("<white>" + target.getName() + "</white>"));
        sm.setLore(List.of(rank.suffix() + " ・ " + rp.elo() + " Elo",
                "K/D: " + rp.kills() + "/" + rp.deaths() + "  KDR: " + rp.kdr(),
                "Peak: " + rp.peakElo() + "  Best streak: " + rp.bestStreak(),
                "Progress: " + (next != null ? (next.minElo() - rp.elo()) + " Elo to " + next.display() : "Top rank reached")));
        head.setItemMeta(sm);
        inv.setItem(13, head);
        fill(inv);
        viewer.openInventory(inv);
    }

    public void openRanks(Player p) {
        RankedPlayer rp = plugin.players().get(p.getUniqueId());
        int elo = rp != null ? rp.elo() : 0;
        Rank current = plugin.ranks().rankFor(elo);
        Inventory inv = Bukkit.createInventory(new Holder(Type.RANKS, 0, ""),
                plugin.fileCfg("gui.yml").getInt("rank-progression.size", 54),
                Msg.parse(plugin.fileCfg("gui.yml").getString("rank-progression.title", "Ranks")));
        List<Rank> ladder = plugin.ranks().ladder();
        int slot = 10;
        for (Rank r : ladder) {
            boolean unlocked = elo >= r.minElo();
            boolean isCurrent = r.id().equals(current.id());
            String status = isCurrent ? "<green><bold>CURRENT RANK</bold></green>"
                    : unlocked ? "<green>✔ Unlocked</green>" : "<red>🔒 Locked</red>";
            if (isCurrent && plugin.ranks().next(r) != null) {
                int pct = Math.min(100, (int) ((elo - r.minElo()) * 100.0 / Math.max(1, plugin.ranks().next(r).minElo() - r.minElo())));
                status += " <gray>・</gray><yellow> " + pct + "% to next</yellow>";
            }
            List<String> lore = new ArrayList<>();
            for (String l : plugin.fileCfg("gui.yml").getStringList("items.rank-item.lore"))
                lore.add(l.replace("%min_elo%", String.valueOf(r.minElo())).replace("%status%", status));
            Material m = isCurrent ? Material.NETHER_STAR : unlocked ? Material.LIME_DYE : Material.GRAY_DYE;
            inv.setItem(slot, named(m, r.suffix(), lore));
            slot += (slot % 9 == 7) ? 3 : 1;
            if (slot >= inv.getSize() - 9) break;
        }
        fill(inv);
        p.openInventory(inv);
    }

    public void openLeaderboard(Player p, int page) {
        List<String[]> top = plugin.players().leaderboard();
        int perPage = 45;
        int maxPage = Math.max(1, (int) Math.ceil(top.size() / (double) perPage));
        page = Math.max(0, Math.min(page, maxPage - 1));
        Inventory inv = Bukkit.createInventory(new Holder(Type.LEADERBOARD, page, ""),
                plugin.fileCfg("gui.yml").getInt("leaderboard.size", 54),
                Msg.parse(plugin.fileCfg("gui.yml").getString("leaderboard.title", "Leaderboard") + " <gray>#Elo</gray>"));
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < top.size(); i++) {
            String[] row = top.get(start + i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(row[0]));
            int pos = start + i + 1;
            sm.displayName(Msg.parse("<gold>#" + pos + "</gold> <white>" + row[0] + "</white>"));
            sm.setLore(List.of(row[1] + " Elo"));
            head.setItemMeta(sm);
            inv.setItem(i, head);
        }
        if (page > 0) inv.setItem(45, item("prev-page", Material.ARROW));
        if (page < maxPage - 1) inv.setItem(53, item("next-page", Material.ARROW));
        fill(inv);
        p.openInventory(inv);
    }

    public void openBoosters(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(Type.BOOSTERS, 0, ""),
                plugin.fileCfg("gui.yml").getInt("boosters.size", 27),
                Msg.parse(plugin.fileCfg("gui.yml").getString("boosters.title", "Boosters")));
        int slot = 10;
        if (plugin.boosters().active().isEmpty() && plugin.boosters().scheduled().isEmpty())
            inv.setItem(13, named(Material.BARRIER, "<gray>No boosters</gray>", List.of("<gray>Check back later!</gray>")));
        for (var b : plugin.boosters().active()) {
            inv.setItem(slot++, named(Material.EXPERIENCE_BOTTLE, "<light_purple>" + b.name + "</light_purple>",
                    List.of("<white>" + b.multiplier + "x Elo</white>",
                            "<gray>" + com.rankcore.booster.BoosterManager.fmt(b.remaining()) + " remaining</gray>")));
        }
        for (var b : plugin.boosters().scheduled()) {
            inv.setItem(slot++, named(Material.CLOCK, "<yellow>Upcoming: " + b.name + "</yellow>",
                    List.of("<white>" + b.multiplier + "x Elo</white>",
                            "<gray>Starts at " + Msg.fmtTime(b.start) + "</gray>")));
        }
        fill(inv);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (h.type() == Type.LEADERBOARD) {
            if (e.getSlot() == 45) openLeaderboard(p, h.page() - 1);
            else if (e.getSlot() == 53) openLeaderboard(p, h.page() + 1);
        }
    }
}

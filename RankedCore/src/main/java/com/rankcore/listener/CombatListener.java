package com.rankcore.listener;

import com.rankcore.RankedCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatListener implements Listener {
    private final RankedCore plugin;
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();

    public CombatListener(RankedCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player victim && e.getDamager() instanceof Player attacker) {
            lastCombat.put(victim.getUniqueId(), System.currentTimeMillis());
            lastCombat.put(attacker.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        FileConfiguration cfg = plugin.fileCfg("config.yml");
        List<String> blocked = cfg.getStringList("combat.blocked-worlds");
        if (blocked.contains(victim.getWorld().getName())) return;
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;           // environmental/void/suicide: no Elo
        if (killer.hasMetadata("NPC") || victim.hasMetadata("NPC")) return; // Citizens etc.
        if (!(killer instanceof Player)) return;                        // non-player entity kill
        if (plugin.players().onCooldown(killer, victim)) {
            killer.sendMessage(com.rankcore.util.Msg.parse(com.rankcore.util.Msg.prefix(plugin)
                    + com.rankcore.util.Msg.color(plugin, "cooldown-blocked").replace("%victim%", victim.getName())));
            return;
        }
        plugin.players().handleKill(killer, victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        FileConfiguration cfg = plugin.fileCfg("config.yml");
        if (!cfg.getBoolean("combat.combat-log.enabled")) return;
        long last = lastCombat.getOrDefault(e.getPlayer().getUniqueId(), 0L);
        if (System.currentTimeMillis() - last > cfg.getLong("combat.combat-log.window-seconds") * 1000L) return;
        int penalty = cfg.getInt("combat.combat-log.penalty");
        var rp = plugin.players().get(e.getPlayer().getUniqueId());
        if (rp == null) return;
        rp.onCombatLog(penalty);
        plugin.ranks().applyRankChange(rp);
        if (cfg.getBoolean("combat.combat-log.broadcast"))
            org.bukkit.Bukkit.broadcast(com.rankcore.util.Msg.parse(com.rankcore.util.Msg.prefix(plugin)
                    + com.rankcore.util.Msg.color(plugin, "combat-log-broadcast")
                    .replace("%player%", e.getPlayer().getName()).replace("%amount%", String.valueOf(penalty))));
        plugin.players().unload(e.getPlayer().getUniqueId(), true);
    }
}

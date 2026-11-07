package com.rankcore.listener;

import com.rankcore.RankedCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionListener implements Listener {
    private final RankedCore plugin;
    private final ConcurrentHashMap<UUID, String> pending = new ConcurrentHashMap<>();

    public ConnectionListener(RankedCore plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        pending.put(e.getUniqueId(), e.getName());
        plugin.players().load(e.getUniqueId(), e.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        // rank placeholder resolves lazily from cache; nothing else required
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!com.rankcore.rank.RankManager.class.getName().isEmpty()) { // noop keep
        }
        plugin.players().unload(e.getPlayer().getUniqueId(), true);
    }
}

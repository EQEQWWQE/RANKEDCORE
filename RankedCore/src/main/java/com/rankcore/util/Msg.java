package com.rankcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private Msg() {}

    public static Component parse(String miniMessage) { return MM.deserialize(miniMessage == null ? "" : miniMessage); }

    public static String prefix(com.rankcore.RankedCore plugin) {
        return plugin.fileCfg("messages.yml").getString("plugin.prefix", "");
    }

    public static void send(CommandSender sender, com.rankcore.RankedCore plugin, String key) {
        String raw = plugin.fileCfg("messages.yml").getString(key, key);
        sender.sendMessage(parse(prefix(plugin) + raw));
    }

    public static void sendRaw(CommandSender sender, String miniMessage) { sender.sendMessage(parse(miniMessage)); }

    public static String color(com.rankcore.RankedCore plugin, String key) {
        return plugin.fileCfg("messages.yml").getString(key, "");
    }

    public static String fmtTime(long millis) { return TIME.format(Instant.ofEpochMilli(millis)); }
}

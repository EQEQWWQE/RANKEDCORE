package com.rankcore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class Effects {
    private Effects() {}

    public static void playSound(Player p, String key) {
        if (key == null || key.isEmpty()) return;
        Sound s = Registry.SOUNDS.get(NamespacedKey.fromString(key.toLowerCase()));
        if (s != null) p.playSound(p.getLocation(), s, 1f, 1f);
    }

    public static void particles(Player p, String key) {
        if (key == null || key.isEmpty()) return;
        Particle pt = Registry.PARTICLE_TYPE.get(NamespacedKey.fromString(key.toLowerCase()));
        if (pt != null) p.getWorld().spawnParticle(pt, p.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.05);
    }
}

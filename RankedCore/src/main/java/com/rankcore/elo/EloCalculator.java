package com.rankcore.elo;

import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EloCalculator {
    public record Result(int gain, int loss) {}

    private EloCalculator() {}

    public static Result compute(int winnerElo, int loserElo, int winnerGamesPlayed, FileConfiguration cfg) {
        double k = cfg.getDouble("elo.k-factor", 32);
        if (cfg.getBoolean("elo.placement.enabled") && winnerGamesPlayed < cfg.getInt("elo.placement.matches"))
            k = cfg.getDouble("elo.placement.k-factor", k);
        double expected = 1.0 / (1.0 + Math.pow(10.0, (loserElo - winnerElo) / 400.0));
        RoundingMode mode = "UP".equalsIgnoreCase(cfg.getString("elo.rounding", "HALF_UP")) ? RoundingMode.UP : RoundingMode.HALF_UP;
        int gain = BigDecimal.valueOf(k * (1.0 - expected)).setScale(0, mode).intValue();
        int loss = BigDecimal.valueOf(k * expected).setScale(0, mode).intValue();
        gain = clamp(gain, cfg.getInt("elo.min-kill-reward"), cfg.getInt("elo.max-kill-reward"));
        loss = clamp(loss, cfg.getInt("elo.min-death-penalty"), cfg.getInt("elo.max-death-penalty"));
        return new Result(gain, loss);
    }

    public static int clampElo(int elo, FileConfiguration cfg) {
        return clamp(elo, cfg.getInt("elo.min-elo"), cfg.getInt("elo.max-elo"));
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}

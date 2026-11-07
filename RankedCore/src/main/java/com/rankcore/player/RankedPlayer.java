package com.rankcore.player;

import java.util.UUID;

public class RankedPlayer {
    private final UUID uuid;
    private String name;
    private int elo, peakElo, kills, deaths, gained, lost, streak, bestStreak,
            wins, losses, winStreak, bestWinStreak;
    private String highestRankId;
    private long lastSeen;
    private volatile int lastChange;
    private volatile boolean dirty;

    public RankedPlayer(UUID uuid, String name) { this.uuid = uuid; this.name = name; }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void name(String n) { this.name = n; }
    public int elo() { return elo; }
    public int peakElo() { return peakElo; }
    public int kills() { return kills; }
    public int deaths() { return deaths; }
    public int gained() { return gained; }
    public int lost() { return lost; }
    public int streak() { return streak; }
    public int bestStreak() { return bestStreak; }
    public int wins() { return wins; }
    public int losses() { return losses; }
    public int winStreak() { return winStreak; }
    public int bestWinStreak() { return bestWinStreak; }
    public String highestRankId() { return highestRankId; }
    public void highestRankId(String id) { this.highestRankId = id; markDirty(); }
    public long lastSeen() { return lastSeen; }
    public int lastChange() { return lastChange; }
    public double kdr() { return deaths == 0 ? kills : Math.round((double) kills / deaths * 100.0) / 100.0; }
    public boolean dirty() { return dirty; }

    public void markDirty() { dirty = true; }
    public void clearDirty() { dirty = false; }
    public void touch() { lastSeen = System.currentTimeMillis(); markDirty(); }

    public void addElo(int delta) {
        elo += delta; lastChange = delta;
        if (delta >= 0) gained += delta; else lost += -delta;
        if (elo > peakElo) peakElo = elo;
        markDirty();
    }

    public void onKill() { kills++; streak++; bestStreak = Math.max(bestStreak, streak); wins++; winStreak++; bestWinStreak = Math.max(bestWinStreak, winStreak); touch(); }
    public void onDeath() { deaths++; streak = 0; losses++; winStreak = 0; touch(); }
    public void onCombatLog(int penalty) { addElo(-penalty); deaths++; streak = 0; winStreak = 0; touch(); }
}

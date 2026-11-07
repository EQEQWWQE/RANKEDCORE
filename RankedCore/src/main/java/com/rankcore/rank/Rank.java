package com.rankcore.rank;

import java.util.List;

public class Rank {
    private final String id, display, suffix, upMsg, downMsg, upSound, downSound, particles;
    private final int minElo;
    private final boolean broadcast;
    private final List<String> rewards;

    public Rank(String id, int minElo, String display, String suffix, String upMsg, String downMsg,
                String upSound, String downSound, boolean broadcast, String particles, List<String> rewards) {
        this.id = id; this.minElo = minElo; this.display = display; this.suffix = suffix;
        this.upMsg = upMsg; this.downMsg = downMsg; this.upSound = upSound; this.downSound = downSound;
        this.broadcast = broadcast; this.particles = particles; this.rewards = rewards;
    }

    public String id() { return id; }
    public int minElo() { return minElo; }
    public String display() { return display; }
    public String suffix() { return suffix; }
    public String upMsg() { return upMsg; }
    public String downMsg() { return downMsg; }
    public String upSound() { return upSound; }
    public String downSound() { return downSound; }
    public boolean broadcast() { return broadcast; }
    public String particles() { return particles; }
    public List<String> rewards() { return rewards; }
}

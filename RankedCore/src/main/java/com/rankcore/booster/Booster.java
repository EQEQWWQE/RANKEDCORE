package com.rankcore.booster;

public class Booster {
    public final String id, name;
    public final double multiplier;
    public final long start, end;

    public Booster(String id, String name, double multiplier, long start, long end) {
        this.id = id; this.name = name; this.multiplier = multiplier; this.start = start; this.end = end;
    }

    public long remaining() { return Math.max(0, end - System.currentTimeMillis()); }
}

package com.rankcore.database;

import com.rankcore.player.RankedPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Database {
    void init() throws Exception;
    void loadInto(RankedPlayer rp);          // blocking; called async only
    void save(RankedPlayer rp);              // blocking; called async only
    void insertHistory(UUID uuid, int delta, String reason, String other); // async only
    List<String[]> history(UUID uuid, int limit);   // [time, signed, reason, other]
    List<String[]> topElo(int limit);               // [name, elo]
    Map<UUID, Integer> positions(List<String[]> top);
    long count();
    void audit(String line);
    void close();
}

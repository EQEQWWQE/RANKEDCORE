package com.rankcore.database;

import com.rankcore.RankedCore;
import com.rankcore.player.RankedPlayer;

import java.io.File;
import java.sql.*;
import java.util.*;

public class SQLiteDatabase implements Database {
    private final RankedCore plugin;
    private String url;

    public SQLiteDatabase(RankedCore plugin) { this.plugin = plugin; }

    @Override
    public void init() throws Exception {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();
        String file = plugin.fileCfg("database.yml").getString("sqlite.file", "data.db");
        url = "jdbc:sqlite:" + new File(dir, file).getAbsolutePath();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ranked_players (" +
                    "uuid TEXT PRIMARY KEY, name TEXT, elo INT DEFAULT 0, peak INT DEFAULT 0," +
                    "kills INT DEFAULT 0, deaths INT DEFAULT 0, gained INT DEFAULT 0, lost INT DEFAULT 0," +
                    "streak INT DEFAULT 0, best_streak INT DEFAULT 0, wins INT DEFAULT 0, losses INT DEFAULT 0," +
                    "win_streak INT DEFAULT 0, best_win_streak INT DEFAULT 0, highest_rank TEXT, last_seen BIGINT DEFAULT 0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS elo_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT, delta INT, reason TEXT, other TEXT, ts BIGINT)");
        }
    }

    protected Connection connect() throws SQLException { return DriverManager.getConnection(url); }

    @Override
    public void loadInto(RankedPlayer rp) {
        String sql = "SELECT * FROM ranked_players WHERE uuid=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, rp.uuid().toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                rp.name(rs.getString("name"));
                rp.addElo(rs.getInt("elo") - rp.elo());
                rp.highestRankId(rs.getString("highest_rank"));
                setStats(rp, rs);
                rp.clearDirty();
            }
        } catch (SQLException e) { plugin.getLogger().warning("load failed: " + e.getMessage()); }
    }

    static void setStats(RankedPlayer rp, ResultSet rs) throws SQLException {
        rp.getClass(); // no-op guard
    }

    @Override
    public void save(RankedPlayer rp) {
        String sql = "INSERT INTO ranked_players (uuid,name,elo,peak,kills,deaths,gained,lost,streak,best_streak," +
                "wins,losses,win_streak,best_win_streak,highest_rank,last_seen) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, elo=excluded.elo, peak=excluded.peak, kills=excluded.kills," +
                "deaths=excluded.deaths, gained=excluded.gained, lost=excluded.lost, streak=excluded.streak," +
                "best_streak=excluded.best_streak, wins=excluded.wins, losses=excluded.losses, win_streak=excluded.win_streak," +
                "best_win_streak=excluded.best_win_streak, highest_rank=excluded.highest_rank, last_seen=excluded.last_seen";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            fill(ps, rp);
            ps.executeUpdate();
            rp.clearDirty();
        } catch (SQLException e) { plugin.getLogger().warning("save failed: " + e.getMessage()); }
    }

    static void fill(PreparedStatement ps, RankedPlayer rp) throws SQLException {
        // written reflectively-free, explicit order
        ps.setString(1, rp.uuid().toString());
        ps.setString(2, rp.name());
        ps.setInt(3, rp.elo());
        ps.setInt(4, rp.peakElo());
        ps.setInt(5, rp.kills());
        ps.setInt(6, rp.deaths());
        ps.setInt(7, rp.gained());
        ps.setInt(8, rp.lost());
        ps.setInt(9, rp.streak());
        ps.setInt(10, rp.bestStreak());
        ps.setInt(11, rp.wins());
        ps.setInt(12, rp.losses());
        ps.setInt(13, rp.winStreak());
        ps.setInt(14, rp.bestWinStreak());
        ps.setString(15, rp.highestRankId());
        ps.setLong(16, System.currentTimeMillis());
    }

    @Override
    public void insertHistory(UUID uuid, int delta, String reason, String other) {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO elo_history (uuid,delta,reason,other,ts) VALUES (?,?,?,?,?)")) {
            ps.setString(1, uuid.toString()); ps.setInt(2, delta);
            ps.setString(3, reason); ps.setString(4, other); ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("history failed: " + e.getMessage()); }
    }

    @Override
    public List<String[]> history(UUID uuid, int limit) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT ts,delta,reason,other FROM elo_history WHERE uuid=? ORDER BY ts DESC LIMIT ?")) {
            ps.setString(1, uuid.toString()); ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(new String[]{String.valueOf(rs.getLong(1)),
                    (rs.getInt(2) >= 0 ? "+" : "") + rs.getInt(2), rs.getString(3), rs.getString(4)});
        } catch (SQLException e) { plugin.getLogger().warning("history read failed: " + e.getMessage()); }
        return out;
    }

    @Override
    public List<String[]> topElo(int limit) {
        List<String[]> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT name,elo FROM ranked_players ORDER BY elo DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(new String[]{rs.getString(1), String.valueOf(rs.getInt(2))});
        } catch (SQLException e) { plugin.getLogger().warning("topElo failed: " + e.getMessage()); }
        return out;
    }

    @Override
    public Map<UUID, Integer> positions(List<String[]> top) {
        Map<UUID, Integer> map = new HashMap<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT uuid, (SELECT COUNT(*)+1 FROM ranked_players p2 WHERE p2.elo > p1.elo) pos FROM ranked_players p1")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(UUID.fromString(rs.getString(1)), rs.getInt(2));
        } catch (SQLException e) { plugin.getLogger().warning("positions failed: " + e.getMessage()); }
        return map;
    }

    @Override
    public long count() {
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM ranked_players")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    @Override
    public void audit(String line) {
        plugin.getLogger().info("[AUDIT] " + line);
    }

    @Override
    public void close() { /* SQLite connections are per-call; nothing pooled */ }
}

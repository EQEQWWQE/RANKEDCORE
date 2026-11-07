package com.rankcore.database;

import com.rankcore.RankedCore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQLDatabase extends SQLiteDatabase {
    private final RankedCore plugin;

    public MySQLDatabase(RankedCore plugin) { super(plugin); this.plugin = plugin; }

    @Override
    public void init() throws Exception {
        String h = cfg("host"), db = cfg("database");
        int port = plugin.fileCfg("database.yml").getInt("mysql.port", 3306);
        String user = cfg("username"), pass = cfg("password");
        boolean ssl = plugin.fileCfg("database.yml").getBoolean("mysql.use-ssl", false);
        setUrl("jdbc:mysql://" + h + ":" + port + "/" + db + "?useSSL=" + ssl + "&autoReconnect=true");
        try (Connection c = connect()) {
            c.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS ranked_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(32), elo INT DEFAULT 0, peak INT DEFAULT 0," +
                    "kills INT DEFAULT 0, deaths INT DEFAULT 0, gained INT DEFAULT 0, lost INT DEFAULT 0," +
                    "streak INT DEFAULT 0, best_streak INT DEFAULT 0, wins INT DEFAULT 0, losses INT DEFAULT 0," +
                    "win_streak INT DEFAULT 0, best_win_streak INT DEFAULT 0, highest_rank VARCHAR(8), last_seen BIGINT DEFAULT 0)");
            c.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS elo_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36), delta INT, reason VARCHAR(16), other VARCHAR(32), ts BIGINT)");
        }
    }

    private String cfg(String k) { return plugin.fileCfg("database.yml").getString("mysql." + k); }

    // SQLite INSERT ... ON CONFLICT is also valid MySQL 8.0.19+ syntax; keep JDBC simple:
    @Override
    protected Connection connect() throws SQLException { return DriverManager.getConnection(url(), "user", "pass"); }

    private String urlValue;
    private void setUrl(String u) { urlValue = u; }
    private String url() { return urlValue; }
}

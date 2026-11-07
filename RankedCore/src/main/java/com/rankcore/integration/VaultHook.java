package com.rankcore.integration;

import com.rankcore.RankedCore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {
    private final RankedCore plugin;
    private Economy economy;

    public VaultHook(RankedCore plugin) {
        this.plugin = plugin;
        if (plugin.fileCfg("config.yml").getBoolean("economy.enabled") && Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
    }

    public boolean active() { return economy != null; }

    public void deposit(Player p, double amount) {
        if (economy != null && amount > 0) economy.depositPlayer(p, amount);
    }

    public void withdraw(Player p, double amount) {
        if (economy != null && amount > 0) economy.withdrawPlayer(p, amount);
    }

    public void deposit(OfflinePlayer p, double amount) {
        if (economy != null && amount > 0) economy.depositPlayer(p, amount);
    }
}

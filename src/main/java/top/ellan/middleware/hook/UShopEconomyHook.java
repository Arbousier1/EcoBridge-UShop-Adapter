package top.ellan.middleware.hook;

import cn.superiormc.ultimateshop.hooks.economy.AbstractEconomyHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import top.ellan.middleware.listener.ShopInterceptor;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UShopEconomyHook extends AbstractEconomyHook {

    private static Economy econ = null;
    private static final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    public UShopEconomyHook() {
        super("EcoBridge-Mid");
        setupEconomy();
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
    }

    private Object getLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, k -> new Object());
    }

    @Override
    public double getEconomy(Player player, String currencyID) {
        return (econ != null) ? econ.getBalance(player) : 0;
    }

    @Override
    public void takeEconomy(Player player, double value, String currencyID) {
        if (econ == null) return;
        synchronized (getLock(player.getUniqueId())) {
            // 获取锁定后的购买价格（已含溢价）
            Double dynamicPrice = ShopInterceptor.getAndRemoveCache(player.getUniqueId(), "buy");
            double finalPrice = (dynamicPrice != null && dynamicPrice > 0) ? dynamicPrice : value;

            if (econ.getBalance(player) >= finalPrice) {
                econ.withdrawPlayer(player, finalPrice);
            }
            playerLocks.remove(player.getUniqueId());
        }
    }

    @Override
    public void giveEconomy(Player player, double value, String currencyID) {
        if (econ == null) return;
        synchronized (getLock(player.getUniqueId())) {
            // 获取锁定后的出售价格（基准价）
            Double dynamicPrice = ShopInterceptor.getAndRemoveCache(player.getUniqueId(), "sell");
            double finalReward = (dynamicPrice != null && dynamicPrice > 0) ? dynamicPrice : value;
            
            econ.depositPlayer(player, finalReward);
            playerLocks.remove(player.getUniqueId());
        }
    }
}
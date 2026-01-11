package top.ellan.middleware.hook;

import cn.superiormc.ultimateshop.hooks.economy.AbstractEconomyHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import top.ellan.middleware.listener.ShopInterceptor;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;

public class UShopEconomyHook extends AbstractEconomyHook {

    private static Economy econ = null;
    // 采用对象锁池，避免 intern() 导致的元空间泄露
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
            Double dynamicPrice = ShopInterceptor.getAndRemoveCache(player.getUniqueId());
            // 如果缓存失效或演算错误，回退至 UltimateShop 的基准价格
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
            Double dynamicPrice = ShopInterceptor.getAndRemoveCache(player.getUniqueId());
            double finalReward = (dynamicPrice != null && dynamicPrice > 0) ? dynamicPrice : value;
            econ.depositPlayer(player, finalReward);
            playerLocks.remove(player.getUniqueId());
        }
    }
}
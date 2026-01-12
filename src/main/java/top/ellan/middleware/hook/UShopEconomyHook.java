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
    // 使用 ConcurrentHashMap 存储锁对象，且不在 synchronized 块内移除，确保线程安全
    private static final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    public UShopEconomyHook() {
        super("EcoBridge-Mid");
        setupEconomy();
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
    }

    // 暴露 Economy 对象供 Interceptor 做余额预检查
    public static Economy getEconomy() {
        return econ;
    }

    public static void removePlayerLock(UUID uuid) {
        playerLocks.remove(uuid);
    }

    public static void clearAllLocks() {
        playerLocks.clear();
    }

    private Object getLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, k -> new Object());
    }

    @Override
    public double getEconomy(Player player, String currencyID) {
        return (econ != null) ? econ.getBalance(player) : 0;
    }

    /**
     * 购买扣款逻辑 (Buy Flow)
     * UltimateShop 在通过所有验证(包括余额充足)后调用此方法
     */
    @Override
    public void takeEconomy(Player player, double value, String currencyID) {
        if (econ == null) return;

        synchronized (getLock(player.getUniqueId())) {
            // 尝试获取拦截器计算并缓存的动态价格
            Double dynamicPrice = ShopInterceptor.getAndRemoveCache(player.getUniqueId(), "buy");
            
            // 如果没有缓存(极罕见，除非拦截器失效)，回退到 UShop 传递的原始配置价格
            double finalPrice = (dynamicPrice != null && dynamicPrice > 0) ? dynamicPrice : value;

            // 执行原子扣款
            // 注意：虽然 ShopInterceptor 已经做过余额检查，但为了防止并发操作，这里必须再次检查
            if (econ.getBalance(player) >= finalPrice) {
                econ.withdrawPlayer(player, finalPrice);
            } else {
                // 这是一个兜底日志。如果出现这条日志，说明玩家在点击检查后、扣款前的毫秒级时间内把钱转走了
                Bukkit.getLogger().warning("[EcoBridge] 交易异常拦截：玩家 " + player.getName() + " 试图购买但余额不足 (动态价格: " + finalPrice + ")");
            }
        }
    }

    /**
     * 出售给钱逻辑 (Sell Flow)
     */
    @Override
    public void giveEconomy(Player player, double value, String currencyID) {
        if (econ == null) return;
        synchronized (getLock(player.getUniqueId())) {
            Double dynamicPrice = ShopInterceptor.getAndRemoveCache(player.getUniqueId(), "sell");
            double finalReward = (dynamicPrice != null && dynamicPrice > 0) ? dynamicPrice : value;
            
            econ.depositPlayer(player, finalReward);
        }
    }
}
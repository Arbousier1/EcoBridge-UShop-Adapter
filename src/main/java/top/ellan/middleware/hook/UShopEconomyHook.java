package top.ellan.middleware.hook;

// 修正：根据源码确定的正确路径
import cn.superiormc.ultimateshop.hooks.economy.AbstractEconomyHook;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * 经济桥接器
 * 职责：连接 UltimateShop 与 Vault 系统。
 * 当 EcoBridge 计算出“岚金”动态价格后，通过此类执行真实的扣款与发放。
 */
public class UShopEconomyHook extends AbstractEconomyHook {

    private static Economy econ = null;

    public UShopEconomyHook() {
        if (!setupEconomy()) {
            Bukkit.getLogger().severe("[EcoBridge-Middleware] 无法找到 Vault 依赖！经济功能将不可用。");
        }
    }

    /**
     * 初始化 Vault 经济系统
     */
    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public String getName() {
        return "EcoBridge-Mid"; // 对应 config.yml 中的 economy-plugin 名称
    }

    /**
     * 获取玩家余额 (岚金)
     */
    @Override
    public double getEconomy(Player player, String currencyID) {
        if (econ == null) return 0;
        return econ.getBalance(player);
    }

    /**
     * 扣除玩家经济 (购买商品)
     * @param value 这里的 value 是由 EcoBridge 演算出的动态价格
     */
    @Override
    public boolean takeEconomy(Player player, double value, String currencyName) {
        if (econ == null) return false;
        
        // 检查余额是否足够
        if (econ.getBalance(player) < value) {
            return false;
        }

        EconomyResponse r = econ.withdrawPlayer(player, value);
        return r.transactionSuccess();
    }

    /**
     * 给予玩家经济 (出售商品)
     * @param value 这里的 value 是由 EcoBridge 演算出的动态收入
     */
    @Override
    public void giveEconomy(Player player, double value, String currencyName) {
        if (econ != null) {
            econ.depositPlayer(player, value);
        }
    }

    // --- 以下为兼容性重写方法 ---

    public double getBalance(Player player) {
        return getEconomy(player, null);
    }

    public boolean withdraw(Player player, double amount) {
        return takeEconomy(player, amount, null);
    }

    public boolean give(Player player, double amount) {
        giveEconomy(player, amount, null);
        return true;
    }
}
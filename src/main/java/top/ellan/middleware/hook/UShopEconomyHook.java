package top.ellan.middleware.hook;

import cn.superiormc.ultimateshop.api.hook.economy.AbstractEconomyHook;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * 经济桥接器
 * 职责：当 UltimateShop 确定了由 EcoBridge 计算出的价格后，调用此类进行真实的 Vault 扣款
 */
public class UShopEconomyHook extends AbstractEconomyHook {

    private static Economy econ = null;

    public UShopEconomyHook() {
        if (!setupEconomy()) {
            Bukkit.getLogger().severe(String.format("[%s] - 无法找到 Vault 依赖！经济功能将不可用。", "EcoBridge-Middleware"));
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
        return "EcoBridge-Mid"; // 对应 config 中的 economy-plugin
    }

    @Override
    public double getBalance(Player player) {
        if (econ == null) return 0;
        return econ.getBalance(player);
    }

    /**
     * 执行扣款逻辑 (购买商品)
     * @param amount 这里的 amount 已经是经过 EcoBridge 计算后的动态价格
     */
    @Override
    public boolean withdraw(Player player, double amount) {
        if (econ == null) return false;
        
        // 检查余额是否足够
        if (econ.getBalance(player) < amount) {
            return false;
        }

        EconomyResponse r = econ.withdrawPlayer(player, amount);
        return r.transactionSuccess();
    }

    /**
     * 执行给予逻辑 (出售商品)
     * @param amount 这里的 amount 已经是经过 EcoBridge 计算后的动态收入
     */
    @Override
    public boolean give(Player player, double amount) {
        if (econ == null) return false;
        
        EconomyResponse r = econ.depositPlayer(player, amount);
        return r.transactionSuccess();
    }

    // 注意：根据 UltimateShop 的 API 版本，可能需要额外重写以下方法来处理货币名称
    @Override
    public double getEconomy(Player player, String currencyID) {
        return getBalance(player);
    }

    @Override
    public void giveEconomy(Player player, double value, String currencyName) {
        give(player, value);
    }

    @Override
    public boolean takeEconomy(Player player, double value, String currencyName) {
        return withdraw(player, value);
    }
}
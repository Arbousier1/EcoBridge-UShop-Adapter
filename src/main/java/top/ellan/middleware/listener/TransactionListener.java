package top.ellan.middleware.listener;

import cn.superiormc.ultimateshop.api.events.ItemPreTransactionEvent;
import cn.superiormc.ultimateshop.api.shop.ObjectItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import top.ellan.ecobridge.bridge.DataPacket;
import top.ellan.ecobridge.bridge.RustCore;
import top.ellan.ecobridge.provider.UShopProvider;

/**
 * 交易拦截监听器
 * 职责：在交易动作执行前，拦截并注入由 EcoBridge 计算出的动态价格
 */
public class TransactionListener implements Listener {

    /**
     * 使用 LOWEST 优先级以确保本插件首先修改价格
     * ignoreCancelled = true 确保在交易已被其他逻辑取消时不执行计算
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreTrade(ItemPreTransactionEvent event) {
        final Player player = event.getPlayer();
        final ObjectItem item = event.getItem();
        final int amount = event.getAmount();

        // 使用 Java 21 的结构化错误处理，确保 Rust 核心异常时不中断商店基本功能
        try {
            // 1. 构建交易数据包：整合玩家数据、物品详情及历史交易频率
            DataPacket packet = UShopProvider.getFullDataPacket(player, item, amount);

            // 2. 调用 Rust 核心：执行经济学模型演算 (p(n) = ε * p0 * e^(-λn))
            String jsonData = packet.toJson();
            double dynamicPrice = RustCore.calculatePrice(jsonData);

            // 3. 价格注入：强制覆盖 UltimateShop 的原始价格数值
            if (dynamicPrice >= 0) {
                // 此时修改 Price，后续 UltimateShop 调用 EconomyHook 时将使用此新值
                event.setPrice(dynamicPrice);
                
                // 生产环境下建议仅开启 Debug 日志
                // Bukkit.getLogger().info("[EcoBridge-Mid] 商品 " + item.getID() + " 价格已动态调整为: " + dynamicPrice);
            }

        } catch (Exception e) {
            // 容错处理：如果 Rust 计算失败或 JSON 解析异常，UltimateShop 将回退至原价交易
            Bukkit.getLogger().warning("[EcoBridge-Mid] 无法通过 Rust 核心计算价格，将使用商店默认价格。错误原因: " + e.getMessage());
        }
    }
}
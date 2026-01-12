package top.ellan.ecobridge.adapter.ushop.listener;

import cn.superiormc.ultimateshop.UltimateShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import top.ellan.ecobridge.adapter.ushop.EcoBridgeUShopAdapter;
import top.ellan.ecobridge.api.event.PriceCalculatedEvent;

import java.lang.reflect.Method;

/**
 * 价格演算结果监听器 (同步优化版)
 */
public class PriceCalculatedListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPriceCalculated(PriceCalculatedEvent event) {
        String shopId = event.getShopId();
        String productId = event.getProductId();
        double basePrice = event.getNewPrice(); 

        // 1. 异步阶段：计算价格（该操作不涉及物品对象，安全）
        double multiplier = EcoBridgeUShopAdapter.getInstance()
                .getConfig().getDouble("settings.buy-multiplier", 1.25);
        
        double sellPrice = Math.round(basePrice * 100.0) / 100.0;
        double buyPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;

        // 2. 同步阶段：回归主线程进行数据注入
        // UltimateShop 的 ObjectItem 及其内部配置对象在主线程操作最为安全
        Bukkit.getScheduler().runTask(EcoBridgeUShopAdapter.getInstance(), () -> {
            ObjectItem item = UltimateShop.getInstance().getShopManager()
                    .getShop(shopId)
                    .getItem(productId);

            if (item == null) return;

            try {
                // 更新内存中的配置节点
                updateConfigNode(item, "buy-prices", buyPrice);
                updateConfigNode(item, "sell-prices", sellPrice);
                updateConfigNode(item, "prices", buyPrice);

                // 反射强制重载价格对象
                refreshMemory(item);

                // 自检逻辑
                if (item.getBuyPrice().empty || item.getSellPrice().empty) {
                    throw new IllegalStateException("价格注入成功但 ObjectPrices 状态为空");
                }

                // 调试日志
                if (EcoBridgeUShopAdapter.getInstance().getConfig().getBoolean("settings.debug-log", true)) {
                    EcoBridgeUShopAdapter.getInstance().getLogger().info(String.format(
                        "§b[行情波动] §f%s §8| §a出售: %.2f §8| §c买入: %.2f (%.2fx)",
                        productId, sellPrice, buyPrice, multiplier
                    ));
                }
            } catch (Exception e) {
                EcoBridgeUShopAdapter.getInstance().getLogger().severe(
                    "§c[内核警报] 无法接管物品 " + productId + " : " + e.getMessage()
                );
            }
        });
    }

    private void updateConfigNode(ObjectItem item, String node, double value) {
        ConfigurationSection config = item.getItemConfig().getConfigurationSection(node);
        if (config != null) {
            for (String key : config.getKeys(false)) {
                config.set(key + ".amount", value);
            }
        }
    }

    private void refreshMemory(ObjectItem item) throws Exception {
        try {
            Method initBuy = ObjectItem.class.getDeclaredMethod("initBuyPrice");
            Method initSell = ObjectItem.class.getDeclaredMethod("initSellPrice");
            initBuy.setAccessible(true);
            initSell.setAccessible(true);
            initBuy.invoke(item);
            initSell.invoke(item);
        } catch (NoSuchMethodException e) {
            throw new Exception("UltimateShop 版本不兼容：缺失初始化方法。");
        }
    }
}
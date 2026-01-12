package top.ellan.ecobridge.adapter.ushop.listener;

import cn.superiormc.ultimateshop.UltimateShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import top.ellan.ecobridge.adapter.ushop.EcoBridgeUShopAdapter;
import top.ellan.ecobridge.api.event.PriceCalculatedEvent;

import java.lang.reflect.Method;

/**
 * 价格演算结果监听器 (MiniMessage 优化版)
 */
public class PriceCalculatedListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPriceCalculated(PriceCalculatedEvent event) {
        String shopId = event.getShopId();
        String productId = event.getProductId();
        double basePrice = event.getNewPrice(); 

        // 1. 异步阶段：演算逻辑
        double multiplier = EcoBridgeUShopAdapter.getInstance()
                .getConfig().getDouble("settings.buy-multiplier", 1.25);
        
        double sellPrice = Math.round(basePrice * 100.0) / 100.0;
        double buyPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;

        // 2. 同步阶段：注入数据
        Bukkit.getScheduler().runTask(EcoBridgeUShopAdapter.getInstance(), () -> {
            try {
                ObjectItem item = UltimateShop.getPlugin(UltimateShop.class).getShopManager()
                        .getShop(shopId)
                        .getItem(productId);

                if (item == null) return;

                updateConfigNode(item, "buy-prices", buyPrice);
                updateConfigNode(item, "sell-prices", sellPrice);
                updateConfigNode(item, "prices", buyPrice);

                refreshMemory(item);

                if (item.getBuyPrice().empty || item.getSellPrice().empty) {
                    throw new IllegalStateException("价格注入成功但 ObjectPrices 状态为空 (.empty)");
                }

                // 3. 调试日志 (使用 MiniMessage)
                if (EcoBridgeUShopAdapter.getInstance().getConfig().getBoolean("settings.debug-log", true)) {
                    // 使用占位符系统防止 String.format 的繁琐
                    String logMsg = "<aqua>[行情波动] <white><id> <dark_gray>| <green>出售: <sell> <dark_gray>| <red>买入: <buy> <dark_gray>(<mul>x)";
                    
                    Bukkit.getConsoleSender().sendMessage(MM.deserialize(logMsg,
                            Placeholder.unparsed("id", productId),
                            Placeholder.unparsed("sell", String.format("%.2f", sellPrice)),
                            Placeholder.unparsed("buy", String.format("%.2f", buyPrice)),
                            Placeholder.unparsed("mul", String.format("%.2f", multiplier))
                    ));
                }
            } catch (Exception e) {
                // 错误日志也转为 MiniMessage
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                        "<red>[内核警报] 无法接管物品 <id> : <error>",
                        Placeholder.unparsed("id", productId),
                        Placeholder.unparsed("error", e.getMessage())
                ));
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
            throw new Exception("UltimateShop 版本不兼容：未找到所需的私有初始化方法。");
        }
    }
}
package top.ellan.ecobridge.adapter.ushop.listener;

import cn.superiormc.ultimateshop.managers.ConfigManager;
import cn.superiormc.ultimateshop.objects.ObjectItem;
import cn.superiormc.ultimateshop.objects.ObjectShop;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import top.ellan.ecobridge.adapter.ushop.EcoBridgeUShopAdapter;
import top.ellan.ecobridge.adapter.ushop.util.ShopReflector;
import top.ellan.ecobridge.api.event.PriceCalculatedEvent;
import top.ellan.ecobridge.bridge.RustCore;

/**
 * 价格演算结果监听器 (修正 API 版)
 */
public class PriceCalculatedListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPriceCalculated(PriceCalculatedEvent event) {
        if (!RustCore.isLoaded()) return;

        String shopId = event.getShopId();
        String productId = event.getProductId();
        double basePrice = event.getNewPrice();

        // 1. 异步计算倍率价格
        double multiplier = EcoBridgeUShopAdapter.getInstance().getConfig().getDouble("settings.buy-multiplier", 1.25);
        double sellPrice = Math.round(basePrice * 100.0) / 100.0;
        double buyPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;

        // 2. 回到主线程进行安全注入
        Bukkit.getScheduler().runTask(EcoBridgeUShopAdapter.getInstance(), () -> {
            try {
                // 【核心修正】通过 ConfigManager 获取商店和商品
                ObjectShop shop = ConfigManager.configManager.getShop(shopId);
                if (shop == null) return;

                ObjectItem item = shop.getProduct(productId);
                if (item == null) return;

                // 使用工具类执行注入
                ShopReflector.injectPriceAndFixState(item, buyPrice, sellPrice);

                // 3. 调试日志
                if (EcoBridgeUShopAdapter.getInstance().getConfig().getBoolean("settings.debug-log", true)) {
                    Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                        "<aqua>[行情波动] <white><id> <dark_gray>| <green>出售: <sell> <dark_gray>| <red>买入: <buy> <gray>(<mul>x)",
                        Placeholder.unparsed("id", productId),
                        Placeholder.unparsed("sell", String.format("%.2f", sellPrice)),
                        Placeholder.unparsed("buy", String.format("%.2f", buyPrice)),
                        Placeholder.unparsed("mul", String.format("%.2f", multiplier))
                    ));
                }
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                    "<red>[内核警报] 无法接管物品 <id> : <error>",
                    Placeholder.unparsed("id", productId),
                    Placeholder.unparsed("error", e.getMessage())
                ));
            }
        });
    }
}
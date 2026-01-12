package top.ellan.ecobridge.adapter.ushop.listener;

import cn.superiormc.ultimateshop.UltimateShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import cn.superiormc.ultimateshop.objects.shop.ObjectShop;
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
 * 价格演算结果监听器 (安全注入版)
 * 职责：响应内核行情变动，同步更新 UltimateShop 内存数据
 */
public class PriceCalculatedListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPriceCalculated(PriceCalculatedEvent event) {
        // 防御性检查：如果 Rust 核心未加载，跳过处理
        if (!RustCore.isLoaded()) return;

        String shopId = event.getShopId();
        String productId = event.getProductId();
        double basePrice = event.getNewPrice();

        // 1. 异步计算倍率价格 (保持在事件线程)
        double multiplier = EcoBridgeUShopAdapter.getInstance().getConfig().getDouble("settings.buy-multiplier", 1.25);
        double sellPrice = Math.round(basePrice * 100.0) / 100.0;
        double buyPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;

        // 2. 回到主线程进行安全注入
        Bukkit.getScheduler().runTask(EcoBridgeUShopAdapter.getInstance(), () -> {
            try {
                // 安全获取 UltimateShop 实例及其 API
                UltimateShop plugin = UltimateShop.getPlugin(UltimateShop.class);
                if (plugin.getAPI() == null) return;

                ObjectShop shop = plugin.getAPI().getShopManager().getShop(shopId);
                if (shop == null) return;

                ObjectItem item = shop.getItem(productId);
                if (item == null) return;

                // 【核心改进】调用统一的工具类方法进行注入
                ShopReflector.injectPriceAndFixState(item, buyPrice, sellPrice);

                // 3. 调试日志输出
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
                // 统一错误捕获，使用 MiniMessage 格式化输出
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                    "<red>[内核警报] 无法接管物品 <id> 的价格系统: <error>",
                    Placeholder.unparsed("id", productId),
                    Placeholder.unparsed("error", e.getMessage())
                ));
            }
        });
    }
}
package top.ellan.ecobridge.adapter.ushop.util;

import cn.superiormc.ultimateshop.managers.ConfigManager;
import cn.superiormc.ultimateshop.objects.ObjectItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import top.ellan.ecobridge.adapter.ushop.EcoBridgeUShopAdapter;
import top.ellan.ecobridge.api.UShopProvider;
import top.ellan.ecobridge.bridge.DataPacket;
import top.ellan.ecobridge.bridge.RustCore;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 价格预热管理器 (修正 API 版)
 */
public class WarmupManager {
    
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BATCH_SIZE = 50;
    private static final long BATCH_DELAY_TICKS = 1L;

    private static final AtomicInteger totalSuccess = new AtomicInteger(0);
    private static final AtomicInteger totalFail = new AtomicInteger(0);

    public static void startAsyncWarmup() {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        
        if (!RustCore.isLoaded()) {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize("<red>[行情预热] Rust 核心未就绪，取消任务。"));
            return;
        }

        long delaySeconds = adapter.getConfig().getLong("warmup.delay-seconds", 30L);
        totalSuccess.set(0);
        totalFail.set(0);

        Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, () -> {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize("<aqua>[行情预热] 任务启动：恢复全球市场基准行情..."));

            try {
                // 【核心修正】通过 ConfigManager 扁平化获取所有商品
                List<ObjectItem> allItems = ConfigManager.configManager.getShops().stream()
                        .flatMap(shop -> shop.getProductList().stream())
                        .collect(Collectors.toList());

                if (allItems.isEmpty()) {
                    Bukkit.getConsoleSender().sendMessage(MM.deserialize("<gray>[行情预热] 未找到物品，跳过。"));
                    return;
                }

                processBatch(allItems, 0);
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                        "<red>[行情预热] 获取商店列表失败: <error>",
                        Placeholder.unparsed("error", e.getMessage())
                ));
            }
        }, delaySeconds * 20L);
    }

    private static void processBatch(List<ObjectItem> items, int index) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        int end = Math.min(index + BATCH_SIZE, items.size());

        for (int i = index; i < end; i++) {
            ObjectItem item = items.get(i);
            if (item != null) processItemWithRetry(item, 0);
        }

        if (end < items.size()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, 
                () -> processBatch(items, end), BATCH_DELAY_TICKS);
        } else {
            Bukkit.getScheduler().runTaskLater(adapter, () -> {
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                        "<green>[行情预热] 同步完成。成功: <success>, 失败: <fail>",
                        Placeholder.unparsed("success", String.valueOf(totalSuccess.get())),
                        Placeholder.unparsed("fail", String.valueOf(totalFail.get()))
                ));
            }, 100L);
        }
    }

    private static void processItemWithRetry(ObjectItem item, int retryCount) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();

        try {
            // 已确保 UShopProvider 对 null player 有防御逻辑
            DataPacket packet = UShopProvider.getFullDataPacket(null, item, 0);
            double basePrice = RustCore.calculatePrice(packet.toJson());
            
            if (basePrice <= 0) throw new IllegalArgumentException("内核返回无效价格: " + basePrice);

            double multiplier = adapter.getConfig().getDouble("settings.buy-multiplier", 1.25);
            double buyPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;
            double sellPrice = Math.round(basePrice * 100.0) / 100.0;

            Bukkit.getScheduler().runTask(adapter, () -> {
                try {
                    ShopReflector.injectPriceAndFixState(item, buyPrice, sellPrice);
                    totalSuccess.incrementAndGet();
                } catch (Exception e) {
                    handleError(item, e, retryCount, "同步注入", false);
                }
            });
        } catch (Exception e) {
            handleError(item, e, retryCount, "行情演算", true);
        }
    }

    private static void handleError(ObjectItem item, Exception e, int retry, String phase, boolean allowRetry) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        if (allowRetry && retry < 3) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, 
                    () -> processItemWithRetry(item, retry + 1), 60L);
        } else {
            totalFail.incrementAndGet();
            if (adapter.getConfig().getBoolean("settings.debug-log", true)) {
                Bukkit.getConsoleSender().sendMessage(MM.deserialize("<red>[预热失败] <id> (<phase>): <error>",
                        Placeholder.unparsed("id", item.getProduct()),
                        Placeholder.unparsed("phase", phase),
                        Placeholder.unparsed("error", e.getMessage())));
            }
        }
    }
}
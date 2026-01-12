package top.ellan.ecobridge.adapter.ushop.util;

import cn.superiormc.ultimateshop.UltimateShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import top.ellan.ecobridge.adapter.ushop.EcoBridgeUShopAdapter;
import top.ellan.ecobridge.api.UShopProvider;
import top.ellan.ecobridge.bridge.DataPacket;
import top.ellan.ecobridge.bridge.RustCore;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 价格预热管理器 (MiniMessage 优化版)
 */
public class WarmupManager {
    
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int BATCH_SIZE = 5;
    private static final long BATCH_DELAY_TICKS = 2L;

    // 全局统计计数器
    private static final AtomicInteger totalSuccess = new AtomicInteger(0);
    private static final AtomicInteger totalFail = new AtomicInteger(0);

    /**
     * 启动异步预热流程
     */
    public static void startAsyncWarmup() {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        long delaySeconds = adapter.getConfig().getLong("warmup.delay-seconds", 30L);

        totalSuccess.set(0);
        totalFail.set(0);

        Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, () -> {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize("<aqua>[行情预热] 任务启动：正在从数据库恢复全球市场行情..."));

            try {
                // 【修复】使用 getPlugin 替换 getInstance
                List<ObjectItem> allItems = UltimateShop.getPlugin(UltimateShop.class).getShopManager()
                        .getShops().values().stream()
                        .flatMap(shop -> shop.getItems().values().stream())
                        .collect(Collectors.toList());

                if (allItems.isEmpty()) {
                    Bukkit.getConsoleSender().sendMessage(MM.deserialize("<gray>[行情预热] 未找到任何物品，跳过。"));
                    return;
                }

                processBatch(allItems, 0);
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                        "<red>[行情预热] 获取商店物品列表失败: <error>",
                        Placeholder.unparsed("error", e.getMessage())
                ));
            }
        }, delaySeconds * 20L);
    }

    /**
     * 递归分批处理物品 (异步调度)
     */
    private static void processBatch(List<ObjectItem> items, int index) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        int end = Math.min(index + BATCH_SIZE, items.size());

        for (int i = index; i < end; i++) {
            ObjectItem item = items.get(i);
            if (item == null) continue;
            processItemWithRetry(item, 0);
        }

        if (end < items.size()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, 
                () -> processBatch(items, end), BATCH_DELAY_TICKS);
        } else {
            // 延迟打印总结，等待最后的注入任务完成
            Bukkit.getScheduler().runTaskLater(adapter, () -> {
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                        "<green>[行情预热] 任务已排队完成。目前成功: <success>, 失败: <fail>",
                        Placeholder.unparsed("success", String.valueOf(totalSuccess.get())),
                        Placeholder.unparsed("fail", String.valueOf(totalFail.get()))
                ));
            }, 100L);
        }
    }

    /**
     * 处理单个物品，集成异步计算与同步注入
     */
    private static void processItemWithRetry(ObjectItem item, int retryCount) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();

        // 步骤 1: 异步演算 (在当前异步线程执行)
        try {
            DataPacket packet = UShopProvider.getFullDataPacket(null, item, 0);
            double basePrice = RustCore.calculatePrice(packet.toJson());
            
            if (basePrice <= 0) {
                throw new IllegalArgumentException("Rust 核心返回了无效基础价格: " + basePrice);
            }

            double multiplier = adapter.getConfig().getDouble("settings.buy-multiplier", 1.25);
            double buyPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;
            double sellPrice = Math.round(basePrice * 100.0) / 100.0;

            // 步骤 2: 同步注入 (切换回 Bukkit 主线程)
            Bukkit.getScheduler().runTask(adapter, () -> {
                try {
                    updateConfigNode(item, "buy-prices", buyPrice);
                    updateConfigNode(item, "sell-prices", sellPrice);
                    updateConfigNode(item, "prices", buyPrice);
                    
                    refreshMemory(item);

                    if (item.getBuyPrice().empty || item.getSellPrice().empty) {
                        throw new IllegalStateException("ObjectPrices 重载后仍标记为空");
                    }
                    
                    totalSuccess.incrementAndGet();
                } catch (Exception e) {
                    handleProcessError(item, e, retryCount, "同步注入");
                }
            });

        } catch (Exception e) {
            handleProcessError(item, e, retryCount, "价格演算");
        }
    }

    /**
     * 统一异常处理与异步重试调度 (MiniMessage)
     */
    private static void handleProcessError(ObjectItem item, Exception e, int retryCount, String phase) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        int maxRetries = adapter.getConfig().getInt("warmup.max-retries", 3);
        boolean shouldRetry = adapter.getConfig().getBoolean("warmup.retry-failed-items", true);

        if (shouldRetry && retryCount < maxRetries) {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                    "<yellow>[预热重试] <id> 在 [<phase>] 失败 (<retry>/<max>): <error>",
                    Placeholder.unparsed("id", item.getProduct()),
                    Placeholder.unparsed("phase", phase),
                    Placeholder.unparsed("retry", String.valueOf(retryCount + 1)),
                    Placeholder.unparsed("max", String.valueOf(maxRetries)),
                    Placeholder.unparsed("error", e.getMessage())
            ));
            
            Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, 
                    () -> processItemWithRetry(item, retryCount + 1), 60L);
        } else {
            totalFail.incrementAndGet();
            Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                    "<red>[预热失败] <id> 最终失败: <error>",
                    Placeholder.unparsed("id", item.getProduct()),
                    Placeholder.unparsed("error", e.getMessage())
            ));
            
            if (adapter.getConfig().getBoolean("settings.debug-log", true)) {
                e.printStackTrace();
            }
        }
    }

    private static void updateConfigNode(ObjectItem item, String node, double val) {
        ConfigurationSection config = item.getItemConfig().getConfigurationSection(node);
        if (config != null) {
            for (String key : config.getKeys(false)) {
                config.set(key + ".amount", val);
            }
        }
    }

    private static void refreshMemory(ObjectItem item) throws Exception {
        Method initBuy = ObjectItem.class.getDeclaredMethod("initBuyPrice");
        Method initSell = ObjectItem.class.getDeclaredMethod("initSellPrice");
        initBuy.setAccessible(true);
        initSell.setAccessible(true);
        initBuy.invoke(item);
        initSell.invoke(item);
    }
}
package top.ellan.ecobridge.adapter.ushop.util;

import cn.superiormc.ultimateshop.UltimateShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
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
 * 价格预热管理器 (安全注入与批量优化版)
 * 职责：
 * 1. 异步并行演算行情。
 * 2. 批量同步注入内存，修正 ObjectItem 内部状态。
 * 3. 智能区分错误类型，修复预热时的 NPE 风险。
 */
public class WarmupManager {
    
    private static final MiniMessage MM = MiniMessage.miniMessage();
    
    // 性能参数：每批次处理 50 个物品，每 Ticks 调度一次，平衡性能与主线程压力
    private static final int BATCH_SIZE = 50;
    private static final long BATCH_DELAY_TICKS = 1L;

    private static final AtomicInteger totalSuccess = new AtomicInteger(0);
    private static final AtomicInteger totalFail = new AtomicInteger(0);

    /**
     * 启动异步预热流程
     */
    public static void startAsyncWarmup() {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        
        // 1. 检查 Rust 核心状态
        if (!RustCore.isLoaded()) {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize("<red>[行情预热] Rust 核心未就绪，取消预热任务。"));
            return;
        }

        long delaySeconds = adapter.getConfig().getLong("warmup.delay-seconds", 30L);
        totalSuccess.set(0);
        totalFail.set(0);

        // 延迟执行预热，确保 UltimateShop 商店已完全加载
        Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, () -> {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize("<aqua>[行情预热] 任务启动：正在从数据库恢复全球市场基准行情..."));

            try {
                // 安全获取 UltimateShop 所有已加载的物品
                List<ObjectItem> allItems = UltimateShop.getPlugin(UltimateShop.class).getAPI().getShopManager()
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
                        "<red>[行情预热] 获取商店数据失败: <error>",
                        Placeholder.unparsed("error", e.getMessage())
                ));
            }
        }, delaySeconds * 20L);
    }

    /**
     * 递归分批处理物品
     */
    private static void processBatch(List<ObjectItem> items, int index) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        int end = Math.min(index + BATCH_SIZE, items.size());

        for (int i = index; i < end; i++) {
            ObjectItem item = items.get(i);
            if (item != null) processItemWithRetry(item, 0);
        }

        // 调度下一批次
        if (end < items.size()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, 
                () -> processBatch(items, end), BATCH_DELAY_TICKS);
        } else {
            // 预留足够时间等待最后一批注入任务完成，然后打印总结
            Bukkit.getScheduler().runTaskLater(adapter, () -> {
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                        "<green>[行情预热] 同步任务完成。成功: <success>, 失败: <fail>",
                        Placeholder.unparsed("success", String.valueOf(totalSuccess.get())),
                        Placeholder.unparsed("fail", String.valueOf(totalFail.get()))
                ));
            }, 100L);
        }
    }

    /**
     * 处理单个物品：异步演算 -> 同步注入
     */
    private static void processItemWithRetry(ObjectItem item, int retryCount) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();

        // 1. 异步阶段：演算行情
        try {
            // 【关键修复】确保传入 null 玩家时不会崩溃。
            // 请确保 UShopProvider.getFullDataPacket 已对 Player 参数进行了 null 检查
            DataPacket packet = UShopProvider.getFullDataPacket(null, item, 0);
            
            double basePrice = RustCore.calculatePrice(packet.toJson());
            
            if (basePrice <= 0) throw new IllegalArgumentException("内核返回无效基准价格: " + basePrice);

            double multiplier = adapter.getConfig().getDouble("settings.buy-multiplier", 1.25);
            double buyPrice = Math.round(basePrice * multiplier * 100.0) / 100.0;
            double sellPrice = Math.round(basePrice * 100.0) / 100.0;

            // 2. 同步阶段：安全注入内存
            Bukkit.getScheduler().runTask(adapter, () -> {
                try {
                    // 调用统一的 ShopReflector 工具类，修正 empty 状态字段
                    ShopReflector.injectPriceAndFixState(item, buyPrice, sellPrice);
                    totalSuccess.incrementAndGet();
                } catch (Exception e) {
                    // 反射或代码结构错误不建议频繁重试
                    handleProcessError(item, e, retryCount, "同步注入", false);
                }
            });

        } catch (Exception e) {
            // 演算阶段失败（通常是 Rust 通讯或计算异常）允许重试
            handleProcessError(item, e, retryCount, "行情演算", true);
        }
    }

    /**
     * 错误处理与重试调度
     */
    private static void handleProcessError(ObjectItem item, Exception e, int retryCount, String phase, boolean allowRetry) {
        EcoBridgeUShopAdapter adapter = EcoBridgeUShopAdapter.getInstance();
        int maxRetries = adapter.getConfig().getInt("warmup.max-retries", 3);

        if (allowRetry && retryCount < maxRetries) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(adapter, 
                    () -> processItemWithRetry(item, retryCount + 1), 60L);
        } else {
            totalFail.incrementAndGet();
            if (adapter.getConfig().getBoolean("settings.debug-log", true)) {
                Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                        "<red>[预热失败] 商品 <id> (<phase>): <error>",
                        Placeholder.unparsed("id", item.getProduct()),
                        Placeholder.unparsed("phase", phase),
                        Placeholder.unparsed("error", e.getMessage())
                ));
            }
        }
    }
}
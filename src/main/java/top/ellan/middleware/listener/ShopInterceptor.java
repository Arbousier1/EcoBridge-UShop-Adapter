package top.ellan.middleware.listener;

import cn.superiormc.ultimateshop.managers.ConfigManager;
import cn.superiormc.ultimateshop.objects.ObjectShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import top.ellan.ecobridge.bridge.DataPacket;
import top.ellan.ecobridge.bridge.RustCore;
import top.ellan.ecobridge.provider.UShopProvider;
import top.ellan.middleware.EcoBridgeMiddleware;
import top.ellan.middleware.hook.UShopEconomyHook;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopInterceptor implements Listener {

    private static EcoBridgeMiddleware plugin;
    public static void init(EcoBridgeMiddleware instance) { plugin = instance; }

    // 使用 ConcurrentHashMap 保证线程安全
    private static final ConcurrentHashMap<UUID, PriceCacheEntry> priceCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ObjectItem> fastLookupMap = new ConcurrentHashMap<>();
    
    private static final long CACHE_EXPIRY_MS = 60000; 

    public static void buildIndex() {
        fastLookupMap.clear();
        for (ObjectShop shop : ConfigManager.configManager.getShops()) {
            for (ObjectItem item : shop.getProductList()) {
                ItemStack stack = item.getProduct().getItemStack(null, 1);
                if (stack != null) {
                    fastLookupMap.put(getItemSignature(stack), item);
                }
            }
        }
        plugin.getLogger().info("已重建商品索引，缓存商品数: " + fastLookupMap.size());
    }

    // 封装 Rust 计算，防止崩溃影响主线程
    public static double safeCalculatePrice(String json) {
        try {
            double price = RustCore.calculatePrice(json);
            return (price <= -1.0) ? -1.0 : price;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[EcoBridge-Mid] Rust 演算核心异常: " + e.getMessage());
            return -1.0;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // 1. 快速查找是否为商店物品
        ObjectItem shopItem = getShopItemByStack(clicked);
        if (shopItem != null) {
            // 获取点击动作 (buy/sell/sell-all 等)
            String clickAction = ConfigManager.configManager.getClickAction(event.getClick(), shopItem);
            
            // 2. 调用 EcoBridge API 获取数据包 (默认数量1，因为 Rust 计算的是单价)
            // 注意：即便是购买，我们暂时也使用 getFullDataPacket，假设 Rust 核心能处理
            DataPacket packet = UShopProvider.getFullDataPacket(player, shopItem, 1);
            double basePrice = safeCalculatePrice(packet.toJson());
            
            if (basePrice > 0) {
                // 3. 应用买卖倍率
                double multiplier = 1.0;
                boolean isBuy = false;

                if (clickAction.contains("buy")) {
                    multiplier = plugin.getConfig().getDouble("economy.buy-multiplier", 1.25);
                    isBuy = true;
                } else if (clickAction.contains("sell")) {
                    multiplier = plugin.getConfig().getDouble("economy.sell-multiplier", 1.0);
                }

                double finalPrice = basePrice * multiplier;

                // 4. [核心防御] 余额预检查
                // UltimateShop 只会检查 config 中的静态价格。
                // 如果 静态价格 < 余额 < 动态价格，UltimateShop 会放行，导致 Hook 扣款失败或负数。
                // 因此必须在这里拦截。
                if (isBuy) {
                    Economy econ = UShopEconomyHook.getEconomy();
                    if (econ != null && !econ.has(player, finalPrice)) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<red>交易被拦截：你的余额不足以支付动态市场价格 (" + String.format("%.2f", finalPrice) + ")"
                        ));
                        // 移除脏缓存
                        priceCache.remove(player.getUniqueId());
                        // 取消事件，彻底阻止 UltimateShop 流程
                        event.setCancelled(true);
                        return;
                    }
                }

                // 5. 存入缓存，供 UShopEconomyHook 在随后的 take/give 中使用
                // 缓存 Key 为玩家 UUID，Value 包含价格和操作类型验证
                priceCache.put(player.getUniqueId(), new PriceCacheEntry(finalPrice, clickAction));
            }
        }
    }

    public static Double getAndRemoveCache(UUID uuid, String requiredAction) {
        PriceCacheEntry entry = priceCache.remove(uuid); // 读取即焚，保证一次性
        if (entry != null && !entry.isExpired()) {
            // 简单的模糊匹配，防止买操作用了卖的价格
            if (entry.tradeType.contains(requiredAction) || requiredAction.contains(entry.tradeType)) {
                return entry.price;
            }
        }
        return null;
    }

    public static ObjectItem getShopItemByStack(ItemStack stack) {
        return stack == null ? null : fastLookupMap.get(getItemSignature(stack));
    }

    // [修复] 使用 serialize().hashCode() 解决附魔/改名物品哈希冲突问题
    public static int getItemSignature(ItemStack item) {
        if (item == null) return 0;
        return item.serialize().toString().hashCode();
    }

    public static void clearAllCaches() {
        priceCache.clear();
        fastLookupMap.clear();
    }
    
    public static void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        priceCache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > CACHE_EXPIRY_MS);
    }

    private record PriceCacheEntry(double price, String tradeType, long timestamp) {
        PriceCacheEntry(double price, String tradeType) { 
            this(price, tradeType, System.currentTimeMillis()); 
        }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS; }
    }
}
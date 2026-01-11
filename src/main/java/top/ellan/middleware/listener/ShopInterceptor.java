package top.ellan.middleware.listener;

import cn.superiormc.ultimateshop.objects.ObjectShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import cn.superiormc.ultimateshop.managers.ConfigManager;
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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;

public class ShopInterceptor implements Listener {

    // 交易级缓存：存活 60 秒，确保点击到扣款之间的时间差内价格有效
    private static final ConcurrentHashMap<UUID, PriceCacheEntry> priceCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 60000; 
    
    // 快速索引：特征码 -> 物品对象
    private static final Map<Integer, ObjectItem> fastLookupMap = new HashMap<>();

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
    }

    /**
     * 安全的 JNI 调用包装
     */
    public static double safeCalculatePrice(String json) {
        try {
            double price = RustCore.calculatePrice(json);
            return (price <= -1.0) ? -1.0 : price;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[EcoBridge-Mid] Rust 核心演算崩溃: " + e.getMessage());
            return -1.0;
        }
    }

    public static ObjectItem getShopItemByStack(ItemStack stack) {
        return stack == null ? null : fastLookupMap.get(getItemSignature(stack));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ObjectItem shopItem = getShopItemByStack(clicked);
        if (shopItem != null) {
            DataPacket packet = UShopProvider.getFullDataPacket(player, shopItem, 1);
            double dynamicPrice = safeCalculatePrice(packet.toJson());
            
            if (dynamicPrice > 0) {
                priceCache.put(player.getUniqueId(), new PriceCacheEntry(dynamicPrice));
            }
        }
    }

    public static Double getAndRemoveCache(UUID uuid) {
        PriceCacheEntry entry = priceCache.remove(uuid);
        return (entry != null && !entry.isExpired()) ? entry.price : null;
    }

    /**
     * 生成物品指纹：修正为 public 供 Display 类调用
     */
    public static int getItemSignature(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return (item == null) ? 0 : item.getType().hashCode();
        int result = item.getType().hashCode();
        var meta = item.getItemMeta();
        if (meta.hasDisplayName()) result = 31 * result + meta.getDisplayName().hashCode();
        if (meta.hasCustomModelData()) result = 31 * result + meta.getCustomModelData();
        return result;
    }

    public static void clearAllCaches() {
        priceCache.clear();
        fastLookupMap.clear();
    }

    private record PriceCacheEntry(double price, long timestamp) {
        PriceCacheEntry(double price) { this(price, System.currentTimeMillis()); }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS; }
    }
}
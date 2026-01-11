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
import top.ellan.middleware.EcoBridgeMiddleware;
import top.ellan.ecobridge.bridge.DataPacket;
import top.ellan.ecobridge.bridge.RustCore;
import top.ellan.ecobridge.provider.UShopProvider;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;

public class ShopInterceptor implements Listener {

    private static EcoBridgeMiddleware plugin;
    public static void init(EcoBridgeMiddleware instance) { plugin = instance; }

    private static final ConcurrentHashMap<UUID, PriceCacheEntry> priceCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 60000; 
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

        ObjectItem shopItem = getShopItemByStack(clicked);
        if (shopItem != null) {
            String clickAction = ConfigManager.configManager.getClickAction(event.getClick(), shopItem);
            
            DataPacket packet = UShopProvider.getFullDataPacket(player, shopItem, 1);
            double basePrice = safeCalculatePrice(packet.toJson());
            
            if (basePrice > 0) {
                // 读取倍率：购买 1.25x / 出售 1.0x
                double multiplier = 1.0;
                if (clickAction.contains("buy")) {
                    multiplier = plugin.getConfig().getDouble("economy.buy-multiplier", 1.25);
                } else if (clickAction.contains("sell")) {
                    multiplier = plugin.getConfig().getDouble("economy.sell-multiplier", 1.0);
                }

                double finalPrice = basePrice * multiplier;
                priceCache.put(player.getUniqueId(), new PriceCacheEntry(finalPrice, clickAction));
            }
        }
    }

    public static Double getAndRemoveCache(UUID uuid, String requiredAction) {
        PriceCacheEntry entry = priceCache.remove(uuid);
        if (entry != null && !entry.isExpired()) {
            // 校验操作类型
            if (entry.tradeType.contains(requiredAction) || requiredAction.contains(entry.tradeType)) {
                return entry.price;
            }
        }
        return null;
    }

    public static ObjectItem getShopItemByStack(ItemStack stack) {
        return stack == null ? null : fastLookupMap.get(getItemSignature(stack));
    }

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

    private record PriceCacheEntry(double price, String tradeType, long timestamp) {
        PriceCacheEntry(double price, String tradeType) { 
            this(price, tradeType, System.currentTimeMillis()); 
        }
        boolean isExpired() { return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS; }
    }
}
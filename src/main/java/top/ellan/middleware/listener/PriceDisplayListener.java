package top.ellan.middleware.listener;

import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import top.ellan.middleware.EcoBridgeMiddleware;
import top.ellan.ecobridge.bridge.DataPacket;
import top.ellan.ecobridge.provider.UShopProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PriceDisplayListener implements Listener {
    
    private final EcoBridgeMiddleware plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private static final Map<Integer, PriceEntry> displayPriceCache = new ConcurrentHashMap<>();

    public PriceDisplayListener(EcoBridgeMiddleware plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopOpen(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        if (!(event.getPlayer() instanceof Player player)) return;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;

            ObjectItem shopItem = ShopInterceptor.getShopItemByStack(stack);
            if (shopItem != null) {
                double price = getOrCalculatePrice(player, shopItem, stack);
                if (price >= 0) {
                    injectDynamicLore(stack, price);
                }
            }
        }
    }

    private double getOrCalculatePrice(Player player, ObjectItem item, ItemStack stack) {
        int sig = ShopInterceptor.getItemSignature(stack);
        PriceEntry entry = displayPriceCache.get(sig);
        
        long configTTL = plugin.getConfig().getLong("settings.cache-ttl", 30) * 1000L;
        
        if (entry != null && (System.currentTimeMillis() - entry.timestamp < configTTL)) {
            return entry.price;
        }

        try {
            DataPacket packet = UShopProvider.getFullDataPacket(player, item, 1);
            double basePrice = ShopInterceptor.safeCalculatePrice(packet.toJson());
            
            if (basePrice < 0) return -1.0;

            // 显示时根据买卖属性应用倍率，确保 UI 显示与结算一致
            double multiplier = item.isBuy() ? 
                plugin.getConfig().getDouble("economy.buy-multiplier", 1.25) : 
                plugin.getConfig().getDouble("economy.sell-multiplier", 1.0);
            
            double finalPrice = basePrice * multiplier;

            displayPriceCache.put(sig, new PriceEntry(finalPrice, System.currentTimeMillis()));
            return finalPrice;
        } catch (Exception e) {
            return -1.0;
        }
    }

    public static void clearTemplateCache() {
        displayPriceCache.clear();
    }

    private void injectDynamicLore(ItemStack item, double price) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> loreTemplate = plugin.getConfig().getStringList("display.price-lore");
        String currencyName = plugin.getConfig().getString("settings.currency-name", "岚金");

        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        for (String line : loreTemplate) {
            if (line.isEmpty()) {
                lore.add(Component.empty());
                continue;
            }
            lore.add(mm.deserialize(line, 
                Placeholder.component("price", Component.text(String.format("%.2f", price))),
                Placeholder.component("currency", mm.deserialize(currencyName))
            ));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private record PriceEntry(double price, long timestamp) {}
}
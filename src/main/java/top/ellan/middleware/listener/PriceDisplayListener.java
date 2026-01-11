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

/**
 * GUI 实时显示监听器 (配置驱动版)
 * 职责：拦截商店打开事件，根据 config.yml 模板注入动态价格 Lore
 */
public class PriceDisplayListener implements Listener {
    
    private final EcoBridgeMiddleware plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    
    // 二级显示缓存：物品特征码 -> {价格, 时间戳}
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

    /**
     * 获取演算价格（支持从配置文件读取 TTL）
     */
    private double getOrCalculatePrice(Player player, ObjectItem item, ItemStack stack) {
        int sig = ShopInterceptor.getItemSignature(stack);
        PriceEntry entry = displayPriceCache.get(sig);
        
        // 从配置读取缓存时长 (秒 -> 毫秒)
        long configTTL = plugin.getConfig().getLong("settings.cache-ttl", 30) * 1000L;
        
        if (entry != null && (System.currentTimeMillis() - entry.timestamp < configTTL)) {
            return entry.price;
        }

        try {
            DataPacket packet = UShopProvider.getFullDataPacket(player, item, 1);
            double price = ShopInterceptor.safeCalculatePrice(packet.toJson());
            displayPriceCache.put(sig, new PriceEntry(price, System.currentTimeMillis()));
            return price;
        } catch (Exception e) {
            return -1.0;
        }
    }

    public static void clearTemplateCache() {
        displayPriceCache.clear();
    }

    /**
     * 按照 config.yml 模板注入动态 Lore
     */
    private void injectDynamicLore(ItemStack item, double price) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        // 1. 获取配置中的 Lore 模板和货币名称
        List<String> loreTemplate = plugin.getConfig().getStringList("display.price-lore");
        String currencyName = plugin.getConfig().getString("settings.currency-name", "岚金");

        // 2. 解析模板并注入 Lore
        for (String line : loreTemplate) {
            // 如果行内容为空，MiniMessage 渲染为空行组件
            if (line.isEmpty()) {
                lore.add(Component.empty());
                continue;
            }

            // 使用 MiniMessage 解析，并绑定价格与货币占位符
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
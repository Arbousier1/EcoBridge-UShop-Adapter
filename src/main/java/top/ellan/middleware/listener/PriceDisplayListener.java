package top.ellan.middleware.listener;

import cn.superiormc.ultimateshop.api.shop.ObjectItem;
import cn.superiormc.ultimateshop.api.shop.ObjectShop;
import cn.superiormc.ultimateshop.managers.ConfigManager;
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
import top.ellan.ecobridge.bridge.DataPacket;
import top.ellan.ecobridge.bridge.RustCore;
import top.ellan.ecobridge.provider.UShopProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI 实时显示监听器
 * 职责：在玩家打开商店界面时，实时计算价格并以 MiniMessage 格式注入动态 Lore
 */
public class PriceDisplayListener implements Listener {

    private final MiniMessage mm = MiniMessage.miniMessage();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopOpen(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        Player player = (Player) event.getPlayer();

        // 识别 UltimateShop 商店界面并匹配物品
        for (ObjectShop shop : ConfigManager.configManager.getShops()) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || !stack.hasItemMeta()) continue;

                ObjectItem shopItem = findObjectItem(stack, shop);
                if (shopItem != null) {
                    // 1. 构建数据包并调用 Rust 核心计算动态价格
                    DataPacket packet = UShopProvider.getFullDataPacket(player, shopItem, 1);
                    double currentPrice = RustCore.calculatePrice(packet.toJson());

                    // 2. 使用 Paper 1.21.1 原生 Component API 进行注入
                    injectDynamicLore(stack, currentPrice);
                }
            }
        }
    }

    /**
     * 注入动态 Lore 信息
     * 货币单位已修改为：岚金
     */
    private void injectDynamicLore(ItemStack item, double price) {
        ItemMeta meta = item.getItemMeta();
        
        // 使用 Paper 提供的原生 Component 列表获取方式
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        // 使用 MiniMessage 和 TagResolver 注入动态数值，单位设置为 岚金
        Component priceLine = mm.deserialize(
            "<gray>实时动态价格: <gold><price> <yellow>岚金",
            Placeholder.component("price", Component.text(String.format("%.2f", price)))
        );

        lore.add(Component.empty());
        lore.add(priceLine);
        lore.add(mm.deserialize("<dark_gray>⚡ 由 EcoBridge 实时推演"));

        // 直接通过 Paper 的接口设置 Component Lore
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * 匹配商店中的商品对象
     */
    private ObjectItem findObjectItem(ItemStack stack, ObjectShop shop) {
        return shop.getProductList().stream()
                .filter(item -> item.getProduct().getItemStack(null, 1).isSimilar(stack))
                .findFirst()
                .orElse(null);
    }
}
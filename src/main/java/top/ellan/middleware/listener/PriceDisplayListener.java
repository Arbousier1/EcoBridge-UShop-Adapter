package top.ellan.middleware.listener;

// 修正：UltimateShop 核心对象的正确路径
import cn.superiormc.ultimateshop.objects.ObjectShop;
import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import cn.superiormc.ultimateshop.managers.ConfigManager;

// Paper & Adventure API
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

// EcoBridge 核心引用 (请确保 pom.xml 已正确安装此依赖)
import top.ellan.ecobridge.bridge.DataPacket;
import top.ellan.ecobridge.bridge.RustCore;
import top.ellan.ecobridge.provider.UShopProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI 实时显示监听器
 * 职责：拦截商店打开事件，利用 EcoBridge 计算“岚金”动态价格并注入 Lore
 */
public class PriceDisplayListener implements Listener {

    private final MiniMessage mm = MiniMessage.miniMessage();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShopOpen(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        Player player = (Player) event.getPlayer();

        // 识别 UltimateShop 商店界面
        for (ObjectShop shop : ConfigManager.configManager.getShops()) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || !stack.hasItemMeta()) continue;

                ObjectItem shopItem = findObjectItem(stack, shop);
                if (shopItem != null) {
                    // 1. 构建交易包并请求 Rust 核心计算价格
                    DataPacket packet = UShopProvider.getFullDataPacket(player, shopItem, 1);
                    double currentPrice = RustCore.calculatePrice(packet.toJson());

                    // 2. 使用现代化 Component API 注入 Lore
                    injectDynamicLore(stack, currentPrice);
                }
            }
        }
    }

    private void injectDynamicLore(ItemStack item, double price) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        // 占位符解析：岚金货币显示
        Component priceLine = mm.deserialize(
            "<gray>实时动态价格: <gold><price> <yellow>岚金",
            Placeholder.component("price", Component.text(String.format("%.2f", price)))
        );

        lore.add(Component.empty());
        lore.add(priceLine);
        lore.add(mm.deserialize("<dark_gray>⚡ 由 EcoBridge 实时推演"));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private ObjectItem findObjectItem(ItemStack stack, ObjectShop shop) {
        // 匹配逻辑：利用 UltimateShop 原生获取 ItemStack 的方法进行比对
        return shop.getProductList().stream()
                .filter(item -> item.getProduct().getItemStack(null, 1).isSimilar(stack))
                .findFirst()
                .orElse(null);
    }
}
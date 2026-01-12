package top.ellan.ecobridge.adapter.ushop.util;

import cn.superiormc.ultimateshop.objects.buttons.ObjectItem;
import org.bukkit.configuration.ConfigurationSection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 商店反射工具类
 * 职责：安全地注入演算价格并修正物品的内部状态标记
 */
public class ShopReflector {

    /**
     * 强行注入价格并刷新 ObjectItem 内部状态
     * @param item UltimateShop 物品实例
     * @param buy 计算后的买入价
     * @param sell 计算后的卖出价
     */
    public static void injectPriceAndFixState(ObjectItem item, double buy, double sell) throws Exception {
        // 1. 更新 ConfigurationSection 内存镜像
        updateConfigNode(item, "buy-prices", buy);
        updateConfigNode(item, "sell-prices", sell);
        updateConfigNode(item, "prices", buy);

        // 2. 通过反射调用私有初始化方法，强制重新实例化内存中的 ObjectPrices 对象
        Method initBuy = ObjectItem.class.getDeclaredMethod("initBuyPrice");
        Method initSell = ObjectItem.class.getDeclaredMethod("initSellPrice");
        initBuy.setAccessible(true);
        initSell.setAccessible(true);
        initBuy.invoke(item);
        initSell.invoke(item);

        // 3. 关键修正：手动更新私有字段 'empty'
        // UltimateShop 源码中，如果 empty 为 true，GUI 将无法显示该物品。
        // 逻辑：只有当奖励、买价、卖价均不为空时，物品才有效。
        Field emptyField = ObjectItem.class.getDeclaredField("empty");
        emptyField.setAccessible(true);
        
        boolean isNowEmpty = item.getReward().empty && 
                            item.getBuyPrice().empty && 
                            item.getSellPrice().empty;
        
        emptyField.set(item, isNowEmpty);
    }

    private static void updateConfigNode(ObjectItem item, String node, double val) {
        ConfigurationSection config = item.getItemConfig().getConfigurationSection(node);
        if (config != null) {
            for (String key : config.getKeys(false)) {
                config.set(key + ".amount", val);
            }
        }
    }
}
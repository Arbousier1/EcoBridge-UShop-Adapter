package top.ellan.ecobridge.adapter.ushop.util;

import cn.superiormc.ultimateshop.objects.ObjectItem;
import org.bukkit.configuration.ConfigurationSection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 商店反射工具类
 * 修正了对 ObjectItem 字段访问的路径
 */
public class ShopReflector {

    public static void injectPriceAndFixState(ObjectItem item, double buy, double sell) throws Exception {
        updateConfigNode(item, "buy-prices", buy);
        updateConfigNode(item, "sell-prices", sell);
        updateConfigNode(item, "prices", buy);

        Method initBuy = ObjectItem.class.getDeclaredMethod("initBuyPrice");
        Method initSell = ObjectItem.class.getDeclaredMethod("initSellPrice");
        initBuy.setAccessible(true);
        initSell.setAccessible(true);
        initBuy.invoke(item);
        initSell.invoke(item);

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
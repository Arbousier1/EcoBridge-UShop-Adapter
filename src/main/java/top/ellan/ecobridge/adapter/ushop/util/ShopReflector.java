package top.ellan.ecobridge.adapter.ushop.util;

import cn.superiormc.ultimateshop.objects.buttons.ObjectItem; // 【核心修正】修复包路径
import org.bukkit.configuration.ConfigurationSection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 商店反射工具类
 * 职责：绕过 UltimateShop 的常规逻辑，直接修改内存中的物品价格并强制刷新其状态。
 */
public class ShopReflector {

    /**
     * 强行注入价格并同步修正物品的内部状态
     * * @param item UltimateShop 的物品对象
     * @param buy  计算出的买入价
     * @param sell 计算出的出售价
     * @throws Exception 反射操作可能抛出的异常
     */
    public static void injectPriceAndFixState(ObjectItem item, double buy, double sell) throws Exception {
        // 1. 同步修改配置镜像 (ConfigSection)
        // 这是为了确保当 UltimateShop 内部逻辑重新读取配置时，拿到的是演算后的新价格
        updateConfigNode(item, "buy-prices", buy);
        updateConfigNode(item, "sell-prices", sell);
        updateConfigNode(item, "prices", buy);

        // 2. 强制触发内部初始化方法
        // ObjectItem 内部使用 ObjectPrices 对象存储价格。
        // 通过反射调用私有的 init 方法，可以强制它重新根据我们刚刚修改的 ConfigSection 生成新的价格对象。
        Method initBuy = ObjectItem.class.getDeclaredMethod("initBuyPrice");
        Method initSell = ObjectItem.class.getDeclaredMethod("initSellPrice");
        initBuy.setAccessible(true);
        initSell.setAccessible(true);
        initBuy.invoke(item);
        initSell.invoke(item);

        // 3. 关键修正：同步刷新私有 'empty' 字段
        // UltimateShop 的 GUI 会检查物品是否为 "empty"（即无价格或无奖励）。
        // 如果我们在注入价格后不刷新这个标记位，物品可能会在商店菜单中“隐身”。
        Field emptyField = ObjectItem.class.getDeclaredField("empty");
        emptyField.setAccessible(true);
        
        // 重新计算 empty 状态：只有当奖励、买价、卖价全部为空时才为 true
        boolean isNowEmpty = item.getReward().empty && 
                            item.getBuyPrice().empty && 
                            item.getSellPrice().empty;
        
        emptyField.set(item, isNowEmpty);
    }

    /**
     * 辅助方法：递归更新配置节点中的数值
     */
    private static void updateConfigNode(ObjectItem item, String node, double val) {
        ConfigurationSection config = item.getItemConfig().getConfigurationSection(node);
        if (config != null) {
            // 遍历节点下的所有经济类型（如 Vault, PlayerPoints）并更新 amount
            for (String key : config.getKeys(false)) {
                config.set(key + ".amount", val);
            }
        }
    }
}
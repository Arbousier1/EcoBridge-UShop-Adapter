package top.ellan.ecobridge.adapter.ushop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import top.ellan.ecobridge.adapter.ushop.listener.PriceCalculatedListener;
import top.ellan.ecobridge.adapter.ushop.util.WarmupManager;

/**
 * EcoBridge-UShop 适配器主类
 * 职责：管理插件生命周期、验证依赖关系、启动异步预热任务。
 */
public class EcoBridgeUShopAdapter extends JavaPlugin {

    private static EcoBridgeUShopAdapter instance;

    @Override
    public void onEnable() {
        instance = this;

        // 1. 初始化配置
        saveDefaultConfig();
        
        // 2. 严格依赖检查
        if (!checkDependencies()) {
            getLogger().severe("§c[Adapter] 未检测到 EcoBridge 或 UltimateShop，适配器无法运行！");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. 注册事件监听器 (内部已优化为同步注入模式)
        getServer().getPluginManager().registerEvents(new PriceCalculatedListener(), this);

        // 4. 执行异步行情预热 (内部已优化为分批同步模式)
        WarmupManager.startAsyncWarmup();

        // 5. 启动日志
        printLogo();
    }

    @Override
    public void onDisable() {
        // 清理静态引用防止内存泄漏
        instance = null;
        getLogger().info("§7[Adapter] 适配器已卸载。");
    }

    /**
     * 检查核心依赖是否存在
     */
    private boolean checkDependencies() {
        return Bukkit.getPluginManager().isPluginEnabled("EcoBridge") && 
               Bukkit.getPluginManager().isPluginEnabled("UltimateShop");
    }

    /**
     * 获取插件单例
     */
    public static EcoBridgeUShopAdapter getInstance() {
        return instance;
    }

    /**
     * 优雅的控制台 Logo
     */
    private void printLogo() {
        double multiplier = getConfig().getDouble("settings.buy-multiplier", 1.25);
        boolean debug = getConfig().getBoolean("settings.debug-log", true);

        getLogger().info("§b┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓");
        getLogger().info("§b┃ §aEcoBridge-UShop Adapter §f启动成功        §b┃");
        getLogger().info("§b┃ §f当前买入倍率: §e" + String.format("%.2f", multiplier) + "x                  §b┃");
        getLogger().info("§b┃ §f调试模式: " + (debug ? "§a开启" : "§7关闭") + "                         §b┃");
        getLogger().info("§b┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛");
    }
    
    /**
     * 提供配置重载支持 (可选)
     */
    public void reloadAdapter() {
        reloadConfig();
        getLogger().info("§a[Adapter] 配置已重新加载，新倍率: " + getConfig().getDouble("settings.buy-multiplier"));
    }
}
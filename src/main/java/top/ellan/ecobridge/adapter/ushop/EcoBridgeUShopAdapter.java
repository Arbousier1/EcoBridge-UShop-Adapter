package top.ellan.ecobridge.adapter.ushop;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import top.ellan.ecobridge.adapter.ushop.listener.PriceCalculatedListener;
import top.ellan.ecobridge.adapter.ushop.util.WarmupManager;

/**
 * EcoBridge-UShop 适配器主类 (MiniMessage 优化版)
 * 职责：管理插件生命周期、验证依赖关系、启动异步预热任务。
 */
public class EcoBridgeUShopAdapter extends JavaPlugin {

    private static EcoBridgeUShopAdapter instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        instance = this;

        // 1. 初始化配置
        saveDefaultConfig();
        
        // 2. 严格依赖检查 (MiniMessage 格式)
        if (!checkDependencies()) {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                "<red>[Adapter] 未检测到 EcoBridge 或 UltimateShop，适配器无法运行！"
            ));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. 注册事件监听器
        getServer().getPluginManager().registerEvents(new PriceCalculatedListener(), this);

        // 4. 执行异步行情预热
        WarmupManager.startAsyncWarmup();

        // 5. 打印启动 Logo
        printLogo();
    }

    @Override
    public void onDisable() {
        instance = null;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<gray>[Adapter] 适配器已卸载。"));
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
     * 优雅的控制台 Logo (使用 MiniMessage 标签)
     */
    private void printLogo() {
        double multiplier = getConfig().getDouble("settings.buy-multiplier", 1.25);
        boolean debug = getConfig().getBoolean("settings.debug-log", true);

        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<blue>┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓"));
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<blue>┃ <green>EcoBridge-UShop Adapter <white>启动成功        <blue>┃"));
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(
            "<blue>┃ <white>当前买入倍率: <yellow><mul>x                  <blue>┃",
            Placeholder.unparsed("mul", String.format("%.2f", multiplier))
        ));
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(
            "<blue>┃ <white>调试模式: <status>                         <blue>┃",
            Placeholder.parsed("status", debug ? "<green>开启" : "<gray>关闭")
        ));
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<blue>┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛"));
    }
    
    /**
     * 配置重载支持
     */
    public void reloadAdapter() {
        reloadConfig();
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(
            "<green>[Adapter] 配置已重新加载，新倍率: <yellow><mul>",
            Placeholder.unparsed("mul", String.valueOf(getConfig().getDouble("settings.buy-multiplier")))
        ));
    }
}
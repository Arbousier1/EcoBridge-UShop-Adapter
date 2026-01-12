package top.ellan.ecobridge.adapter.ushop;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import top.ellan.ecobridge.adapter.ushop.listener.PriceCalculatedListener;
import top.ellan.ecobridge.adapter.ushop.util.WarmupManager;

/**
 * EcoBridge-UShop 适配器主类
 * 职责：管理插件生命周期、验证依赖关系、启动异步预热任务及指令重载。
 */
public class EcoBridgeUShopAdapter extends JavaPlugin implements CommandExecutor {

    private static EcoBridgeUShopAdapter instance;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        instance = this;

        // 1. 初始化配置文件
        saveDefaultConfig();
        
        // 2. 严格依赖检查
        if (!checkDependencies()) {
            Bukkit.getConsoleSender().sendMessage(MM.deserialize(
                "<red>[Adapter] 核心依赖缺失（EcoBridge 或 UltimateShop），适配器已自动禁用！"
            ));
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. 注册事件监听器
        getServer().getPluginManager().registerEvents(new PriceCalculatedListener(), this);

        // 4. 注册重载指令 (默认为 /ebushop reload)
        if (getCommand("ebushop") != null) {
            getCommand("ebushop").setExecutor(this);
        }

        // 5. 启动异步行情预热流程
        WarmupManager.startAsyncWarmup();

        // 6. 打印炫酷的启动 Logo
        printLogo();
    }

    @Override
    public void onDisable() {
        // 防止内存泄漏
        instance = null;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<gray>[Adapter] 适配器已安全卸载。"));
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
     * 优雅的控制台 Logo (MiniMessage 实现)
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
     * 指令执行器：处理重载指令
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ecobridge.admin")) {
                sender.sendMessage(MM.deserialize("<red>你没有权限执行此操作。"));
                return true;
            }
            reloadAdapter();
            sender.sendMessage(MM.deserialize("<green>[Adapter] 配置重载成功！"));
            return true;
        }
        return false;
    }
    
    /**
     * 配置重载逻辑
     */
    public void reloadAdapter() {
        reloadConfig();
        double newMul = getConfig().getDouble("settings.buy-multiplier", 1.25);
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(
            "<green>[Adapter] 配置文件已重载。新倍率: <yellow><mul>x",
            Placeholder.unparsed("mul", String.valueOf(newMul))
        ));
    }
}
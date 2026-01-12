package top.ellan.ecobridge.adapter.ushop;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.ellan.ecobridge.adapter.ushop.listener.PriceCalculatedListener;
import top.ellan.ecobridge.adapter.ushop.util.WarmupManager;

import java.util.Collections;
import java.util.List;

/**
 * EcoBridge-UShop 适配器主类
 * 职责：管理插件生命周期、验证依赖关系、启动异步预热任务及指令处理。
 */
public class EcoBridgeUShopAdapter extends JavaPlugin implements CommandExecutor, TabCompleter {

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

        // 4. 注册指令与补全器
        if (getCommand("ebushop") != null) {
            getCommand("ebushop").setExecutor(this);
            getCommand("ebushop").setTabCompleter(this);
        }

        // 5. 启动异步行情预热流程
        WarmupManager.startAsyncWarmup();

        // 6. 打印 Logo
        printLogo();
    }

    @Override
    public void onDisable() {
        instance = null;
        Bukkit.getConsoleSender().sendMessage(MM.deserialize("<gray>[Adapter] 适配器已安全卸载。"));
    }

    private boolean checkDependencies() {
        return Bukkit.getPluginManager().isPluginEnabled("EcoBridge") && 
               Bukkit.getPluginManager().isPluginEnabled("UltimateShop");
    }

    public static EcoBridgeUShopAdapter getInstance() {
        return instance;
    }

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
     * 指令执行逻辑
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize("<aqua>EcoBridge-UShop Adapter <gray>v" + getDescription().getVersion()));
            sender.sendMessage(MM.deserialize("<gray>使用方式: <white>/ebushop reload"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ecobridge.admin")) {
                sender.sendMessage(MM.deserialize("<red>你没有权限执行此操作。"));
                return true;
            }
            reloadAdapter();
            sender.sendMessage(MM.deserialize("<green>[Adapter] 配置与倍率重载成功！"));
            return true;
        }

        sender.sendMessage(MM.deserialize("<red>未知子指令。请使用: /ebushop reload"));
        return true;
    }

    /**
     * 指令补全逻辑 (新增)
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("ecobridge.admin")) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
    
    public void reloadAdapter() {
        reloadConfig();
        double newMul = getConfig().getDouble("settings.buy-multiplier", 1.25);
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(
            "<green>[Adapter] 配置文件已重载。新倍率: <yellow><mul>x",
            Placeholder.unparsed("mul", String.valueOf(newMul))
        ));
    }
}
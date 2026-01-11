package top.ellan.middleware;

import cn.superiormc.ultimateshop.UltimateShop;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import top.ellan.middleware.hook.UShopEconomyHook;
import top.ellan.middleware.listener.PriceDisplayListener;
import top.ellan.middleware.listener.ShopInterceptor;

public final class EcoBridgeMiddleware extends JavaPlugin {

    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        // 0. 初始化配置文件
        saveDefaultConfig();

        // 1. 验证依赖项
        if (!validateDependencies()) {
            sendRichLog("<red>[!] 核心依赖缺失 (UltimateShop/EcoBridge/Vault)，插件已禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // 2. 初始化模块
            ShopInterceptor.init(this);

            // 延迟构建索引，确保 UltimateShop 配置就绪
            Bukkit.getScheduler().runTaskLater(this, ShopInterceptor::buildIndex, 1L);
            
            // 3. 注册经济钩子
            UltimateShop.getInstance().getHookManager()
                .registerNewEconomyHook("EcoBridge-Mid", new UShopEconomyHook());
            
            // 4. 注册事件
            var pluginManager = getServer().getPluginManager();
            pluginManager.registerEvents(new ShopInterceptor(), this);
            pluginManager.registerEvents(new PriceDisplayListener(this), this);
            
            logStartup();
        } catch (Exception e) {
            sendRichLog("<red>[!] 启动失败: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        ShopInterceptor.clearAllCaches(); 
        PriceDisplayListener.clearTemplateCache();
        sendRichLog("<gold>EcoBridgeMiddleware 已安全关闭，所有缓存已释放。");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ecobridge.admin")) {
                sender.sendMessage(mm.deserialize("<red>你没有权限执行此指令。"));
                return true;
            }
            reloadConfig();
            ShopInterceptor.buildIndex();
            PriceDisplayListener.clearTemplateCache();
            sender.sendMessage(mm.deserialize("<green>[EcoBridge-Mid] 配置与索引已成功重载！"));
            return true;
        }
        return false;
    }

    private boolean validateDependencies() {
        var pm = getServer().getPluginManager();
        return pm.isPluginEnabled("UltimateShop") && 
               pm.isPluginEnabled("EcoBridge") && 
               pm.isPluginEnabled("Vault");
    }

    private void sendRichLog(String message) {
        Bukkit.getConsoleSender().sendMessage(mm.deserialize(message));
    }

    private void logStartup() {
        sendRichLog("<gradient:#00fbff:#0072ff>========================================</gradient>");
        sendRichLog("  <bold><white>EcoBridgeMiddleware</white></bold> <gray>v1.0.0</gray>");
        sendRichLog("  <green>状态:</green> <white>接管成功 [1.25x 买卖差价对齐模式]</white>");
        sendRichLog("<gradient:#00fbff:#0072ff>========================================</gradient>");
    }
}
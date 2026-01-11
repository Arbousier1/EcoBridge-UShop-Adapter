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

/**
 * EcoBridgeMiddleware 主类
 * 职责：连接 UltimateShop 与 EcoBridge 演算核心
 * 采用 Paper 1.21.1 (Java 21) 架构标准
 */
public final class EcoBridgeMiddleware extends JavaPlugin {

    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        // 0. 初始化配置文件
        saveDefaultConfig();

        // 1. 验证必要依赖
        if (!validateDependencies()) {
            sendRichLog("<red>[!] 核心依赖缺失 (UltimateShop/EcoBridge/Vault)，插件已禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // 2. 核心：构建商店物品快速查找索引
            // 放在延迟任务中，确保 UltimateShop 商店配置已完全加载
            Bukkit.getScheduler().runTaskLater(this, ShopInterceptor::buildIndex, 1L);
            
            // 3. 注册经济钩子至 UltimateShop
            UltimateShop.getInstance().getHookManager()
                .registerNewEconomyHook("EcoBridge-Mid", new UShopEconomyHook());
            
            // 4. 注册事件监听器
            var pluginManager = getServer().getPluginManager();
            pluginManager.registerEvents(new ShopInterceptor(), this);
            
            // 注意：此处传入 this，以便监听器读取 config.yml
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
        // 5. 资源清理：防止热重载导致的内存泄露
        ShopInterceptor.clearAllCaches(); 
        PriceDisplayListener.clearTemplateCache();
        sendRichLog("<gold>EcoBridgeMiddleware 已安全关闭，所有缓存已释放。");
    }

    /**
     * 指令处理：支持 /ebm reload
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ecobridge.admin")) {
                sender.sendMessage(mm.deserialize("<red>你没有权限执行此指令。"));
                return true;
            }

            // 执行重载逻辑
            reloadConfig(); // 重新加载 config.yml
            ShopInterceptor.buildIndex(); // 重新构建 $O(1)$ 查找索引
            PriceDisplayListener.clearTemplateCache(); // 清理 GUI 显示缓存
            
            sender.sendMessage(mm.deserialize("<green>[EcoBridge-Mid] 配置文件与商店索引已成功重载！"));
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
        sendRichLog("  <aqua>环境:</aqua> <white>Paper 1.21.1 (Java 21)</white>");
        sendRichLog("  <green>状态:</green> <white>接管成功 [配置驱动 & 索引增强模式]</white>");
        sendRichLog("  <yellow>提示:</yellow> <white>使用 /ebm reload 刷新配置</white>");
        sendRichLog("<gradient:#00fbff:#0072ff>========================================</gradient>");
    }
}
package top.ellan.middleware;

import cn.superiormc.ultimateshop.UltimateShop;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import top.ellan.middleware.hook.UShopEconomyHook;
import top.ellan.middleware.listener.PriceDisplayListener;
import top.ellan.middleware.listener.TransactionListener;

/**
 * EcoBridgeMiddleware 主类
 * 采用 Paper 现代化 API 构建，整合 MiniMessage 渲染与 Java 21 语法
 */
public final class EcoBridgeMiddleware extends JavaPlugin {

    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        // 1. 验证必要依赖是否存在
        if (!validateDependencies()) {
            sendRichLog("<red>[!] 核心依赖缺失 (UltimateShop/EcoBridge/Vault)，插件已禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // 2. 注册经济钩子至 UltimateShop
            UltimateShop.getInstance().getHookManager()
                .registerNewEconomyHook("EcoBridge-Mid", new UShopEconomyHook());
            
            // 3. 注册事件监听器 (包含交易拦截与 GUI 渲染)
            var pluginManager = getServer().getPluginManager();
            pluginManager.registerEvents(new TransactionListener(), this);
            pluginManager.registerEvents(new PriceDisplayListener(), this);
            
            // 4. 打印 MiniMessage 风格的启动日志
            logStartup();

        } catch (Exception e) {
            sendRichLog("<red>[!] 启动过程中发生异常: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        sendRichLog("<gold>EcoBridgeMiddleware 已安全关闭。");
    }

    /**
     * 校验依赖项状态
     */
    private boolean validateDependencies() {
        var pm = getServer().getPluginManager();
        return pm.isPluginEnabled("UltimateShop") && 
               pm.isPluginEnabled("EcoBridge") && 
               pm.isPluginEnabled("Vault");
    }

    /**
     * 使用 MiniMessage 发送格式化控制台日志
     */
    private void sendRichLog(String message) {
        Bukkit.getConsoleSender().sendMessage(mm.deserialize(message));
    }

    private void logStartup() {
        sendRichLog("<gradient:#00fbff:#0072ff>========================================</gradient>");
        sendRichLog("  <bold><white>EcoBridgeMiddleware</white></bold> <gray>v1.0.0</gray>");
        sendRichLog("  <aqua>环境:</aqua> <white>Paper 1.21.11 (Java 21)</white>");
        sendRichLog("  <green>状态:</green> <white>已成功接管 UltimateShop 经济流</white>");
        sendRichLog("<gradient:#00fbff:#0072ff>========================================</gradient>");
    }
}
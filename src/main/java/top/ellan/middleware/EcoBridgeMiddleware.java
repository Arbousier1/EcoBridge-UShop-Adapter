package top.ellan.middleware;

import cn.superiormc.ultimateshop.UltimateShop;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import top.ellan.middleware.hook.UShopEconomyHook;
import top.ellan.middleware.listener.PlayerQuitListener;
import top.ellan.middleware.listener.PriceDisplayListener;
import top.ellan.middleware.listener.ShopInterceptor;

public final class EcoBridgeMiddleware extends JavaPlugin {

    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. 依赖检查
        if (!validateDependencies()) {
            sendRichLog("<red>[!] 核心依赖缺失 (UltimateShop/EcoBridge/Vault)，插件已禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // 2. 初始化拦截器静态引用
            ShopInterceptor.init(this);

            // 3. 同步构建索引 (防止玩家在插件刚启动时打开商店报错)
            ShopInterceptor.buildIndex();
            
            // 4. 注册 UltimateShop 经济钩子
            // 这将接管 UShop 的 checkEconomy 和 takeEconomy/giveEconomy
            UltimateShop.getInstance().getHookManager()
                .registerNewEconomyHook("EcoBridge-Mid", new UShopEconomyHook());
            
            // 5. 注册事件监听器
            var pluginManager = getServer().getPluginManager();
            pluginManager.registerEvents(new ShopInterceptor(), this);
            pluginManager.registerEvents(new PriceDisplayListener(this), this);
            pluginManager.registerEvents(new PlayerQuitListener(), this); // 新增：防止内存泄漏

            // 6. 启动异步清理任务 (每分钟清理一次过期缓存)
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                PriceDisplayListener.cleanExpiredCache();
                ShopInterceptor.cleanExpiredCache();
            }, 1200L, 1200L);
            
            logStartup();
        } catch (Exception e) {
            sendRichLog("<red>[!] 启动失败: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // 清理所有静态缓存和锁
        ShopInterceptor.clearAllCaches(); 
        PriceDisplayListener.clearTemplateCache();
        UShopEconomyHook.clearAllLocks();
        sendRichLog("<gold>EcoBridgeMiddleware 已安全关闭。");
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
        sendRichLog("  <bold><white>EcoBridgeMiddleware</white></bold> <gray>v1.0.2-RELEASE</gray>");
        sendRichLog("  <green>状态:</green> <white>双向交易拦截系统已就绪</white>");
        sendRichLog("<gradient:#00fbff:#0072ff>========================================</gradient>");
    }
}
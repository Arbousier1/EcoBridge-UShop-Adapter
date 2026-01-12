package top.ellan.middleware.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import top.ellan.middleware.hook.UShopEconomyHook;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 当玩家离线时，清理他们在 EconomyHook 中可能残留的锁对象
        UShopEconomyHook.removePlayerLock(event.getPlayer().getUniqueId());
    }
}
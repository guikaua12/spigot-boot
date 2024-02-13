package me.approximations.apxPlugin.testPlugin.listener;

import me.approximations.apxPlugin.utils.ColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        final String msg = ColorUtil.colored(event.getMessage());

        event.setMessage(msg);
    }
}

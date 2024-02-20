package me.approximations.apxPlugin.testPlugin.listener;

import lombok.NoArgsConstructor;
import me.approximations.apxPlugin.di.annotations.Inject;
import me.approximations.apxPlugin.testPlugin.services.PointsService;
import me.approximations.apxPlugin.utils.ColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@NoArgsConstructor(force=true)
public class ChatListener implements Listener {
    @Inject
    private final PointsService pointsService;

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        final String msg = ColorUtil.colored(event.getMessage());

        event.setMessage(msg);
    }
}

package me.approximations.apxPlugin.testPlugin.listener;

import lombok.NoArgsConstructor;
import me.approximations.apxPlugin.di.annotations.Inject;
import me.approximations.apxPlugin.testPlugin.People;
import me.approximations.apxPlugin.testPlugin.services.UserService;
import me.approximations.apxPlugin.utils.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;

@NoArgsConstructor(force=true)
public class JoinListener implements Listener {
    @Inject
    private final UserService userService;

    @EventHandler
    public void onChat(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        userService.getPeople(player.getUniqueId().toString()).thenAccept(peopleOptional -> {
            if (!peopleOptional.isPresent()) {
                player.sendMessage("Not in database, creating...");
                final People people = new People(event.getPlayer().getUniqueId().toString(),
                        player.getName(),
                        player.getName() + "@example.com",
                        Instant.now()
                );

                userService.savePeople(people).join();
                player.sendMessage("Created!");
                return;
            }

            final People people = peopleOptional.get();
            player.sendMessage(ColorUtil.colored("&aWelcome back, &e" + people.getName() + "!"));
        }).exceptionally(throwable -> {
            player.sendMessage(ColorUtil.colored("&cAn error occurred while trying to fetch your data."));
            throwable.printStackTrace();
            return null;
        });
    }
}

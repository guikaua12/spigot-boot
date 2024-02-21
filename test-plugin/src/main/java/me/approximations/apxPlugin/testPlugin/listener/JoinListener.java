package me.approximations.apxPlugin.testPlugin.listener;

import lombok.NoArgsConstructor;
import me.approximations.apxPlugin.di.annotations.Inject;
import me.approximations.apxPlugin.testPlugin.People;
import me.approximations.apxPlugin.testPlugin.placeholders.UserNamePlaceholder;
import me.approximations.apxPlugin.testPlugin.services.UserService;
import me.approximations.apxPlugin.utils.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

@NoArgsConstructor(force=true)
public class JoinListener implements Listener {
    @NotNull @Inject
    private final UserService userService;
    @NotNull @Inject
    private final UserNamePlaceholder userNamePlaceholder;

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

        player.sendMessage(ColorUtil.colored("&aBased on UserNamePlaceholder, your name is: &e" + userNamePlaceholder.getValue(player) + "!"));
    }
}

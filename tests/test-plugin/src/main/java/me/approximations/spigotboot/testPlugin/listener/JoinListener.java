package me.approximations.spigotboot.testPlugin.listener;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.messaging.bungee.BungeeChannel;
import me.approximations.spigotboot.testPlugin.placeholders.Placeholders;
import me.approximations.spigotboot.testPlugin.services.UserService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;

@RequiredArgsConstructor
public class JoinListener implements Listener {
    private final BungeeChannel bungeeChannel;
    private final UserService userService;
    private final Placeholders placeholders;

    @EventHandler
    public void onChat(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

//        userService.getPeople(player.getUniqueId().toString()).thenAccept(peopleOptional -> {
//            if (!peopleOptional.isPresent()) {
//                player.sendMessage("Not in database, creating...");
//                final People people = new People(event.getPlayer().getUniqueId(),
//                        player.getName(),
//                        player.getName() + "@example.com",
//                        Instant.now()
//                );
//
//                userService.savePeople(people).join();
//                player.sendMessage("Created!");
//                return;
//            }
//
//            final People people = peopleOptional.get();
//            player.sendMessage(ColorUtil.colored("&aWelcome back, &e" + people.getName() + "!"));
//        }).exceptionally(throwable -> {
//            player.sendMessage(ColorUtil.colored("&cAn error occurred while trying to fetch your data."));
//            throwable.printStackTrace();
//            return null;
//        });

//        player.sendMessage(ColorUtil.colored("&aBased on UserNamePlaceholder, your name is: &e" + placeholders.userNamePlaceholder(player) + "!"));
    }

    @EventHandler
    public void onBlockBreak(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.DIAMOND_BLOCK) return;

        final Player player = event.getPlayer();
        player.sendMessage("[ApxPlugin] - test message from BlockBreakEvent");

//        try {
//            bungeeChannel.sendMessage(player, new GetPlayerServerAction(player.getName())).thenAccept(serverName -> {
//                player.sendMessage("You are on server: " + serverName);
//            }).exceptionally(throwable -> {
//                player.sendMessage("An error occurred while trying to fetch your server.");
//                throwable.printStackTrace();
//                return null;
//            });
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

//        final Optional<People> people = userService.getPeople(player.getUniqueId().toString());
//
//        if (!people.isPresent()) {
//            player.sendMessage("People not in database!");
//            return;
//        }

//        try {
//            bungeeChannel.sendMessage(player, new ForwardAction<People, String>(ForwardAction.SERVER_ALL, "test", people.get()))
//                    .thenAccept(response -> {
//                        System.out.println("[SendMessage] received response: " + response.getBody());
//                    });
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }
}

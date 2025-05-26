package me.approximations.apxPlugin.testPlugin.placeholders;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.placeholder.annotations.Placeholder;
import me.approximations.apxPlugin.placeholder.annotations.RegisterPlaceholder;
import me.approximations.apxPlugin.testPlugin.People;
import me.approximations.apxPlugin.testPlugin.services.UserService;
import org.bukkit.entity.Player;

import java.util.Optional;

@RegisterPlaceholder
@RequiredArgsConstructor
public class Placeholders {
    private final UserService userService;

    @Placeholder(
            value = "%user_name%",
            description = "Returns the name of the user associated with the player."
    )
    public String userNamePlaceholder(Player player, String params) {
        final Optional<People> peopleOptional = userService.getPeople(player.getUniqueId()).join();
        return peopleOptional.map(People::getName).orElse("People not found!");
    }
}

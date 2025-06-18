package me.approximations.spigotboot.testPlugin.placeholders;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.placeholder.annotations.Param;
import me.approximations.spigotboot.placeholder.annotations.Placeholder;
import me.approximations.spigotboot.placeholder.annotations.RegisterPlaceholder;
import me.approximations.spigotboot.testPlugin.People;
import me.approximations.spigotboot.testPlugin.services.UserService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@RegisterPlaceholder
@RequiredArgsConstructor
public class Placeholders {
    private final UserService userService;

    @Placeholder(
            value = "user_name",
            description = "Returns the nasme of the user associated with the player."
    )
    public String userNamePlaceholder(Player player) {
        final Optional<People> peopleOptional = userService.getPeople(player.getUniqueId()).join();
        return peopleOptional.map(People::getName).orElse("People not found!");
    }

    @Placeholder(
            value = "format_number_<number>",
            description = "Formats a number to two decimal places."
    )
    public String userNamePlaceholder(Player player, @Param("number") @NotNull Double number) {
        return String.format("%.2f", number);
    }
}

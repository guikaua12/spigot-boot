package me.approximations.apxPlugin.testPlugin.placeholders;

import me.approximations.apxPlugin.di.annotations.Inject;
import me.approximations.apxPlugin.placeholder.Placeholder;
import me.approximations.apxPlugin.testPlugin.People;
import me.approximations.apxPlugin.testPlugin.services.UserService;
import org.bukkit.entity.Player;

import java.util.Optional;

public class UserNamePlaceholder extends Placeholder {
    @Inject
    private UserService userService;

    public UserNamePlaceholder() {
        super("user_name", '%');
    }

    @Override
    public String getValue(Player player) {
        final Optional<People> peopleOptional = userService.getPeople(player.getUniqueId().toString()).join();
        return peopleOptional.map(People::getName).orElse("People not found!");
    }

    @Override
    public boolean shouldRegisterPAPI() {
        return true;
    }
}

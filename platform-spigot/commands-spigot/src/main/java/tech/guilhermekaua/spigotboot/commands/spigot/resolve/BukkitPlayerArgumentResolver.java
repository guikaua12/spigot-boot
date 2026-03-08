package tech.guilhermekaua.spigotboot.commands.spigot.resolve;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;
import java.util.List;

public class BukkitPlayerArgumentResolver implements CommandArgumentResolver<Player>, Ordered {
    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return Player.class.equals(parameter.getValueType());
    }

    @Override
    public Player resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        Player player = Bukkit.getPlayerExact(input);
        if (player == null) {
            player = Bukkit.getPlayer(input);
        }
        if (player == null) {
            throw new IllegalArgumentException("Player not found: " + input);
        }
        return player;
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                values.add(player.getName());
            }
            return values;
        };
    }
}

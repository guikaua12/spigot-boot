package tech.guilhermekaua.spigotboot.commands.spigot.resolve;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
            List<Player> matches = Bukkit.matchPlayer(input);
            if (matches.size() == 1) {
                player = matches.get(0);
            } else if (matches.size() > 1) {
                throw CommandMessageException.of(SpigotCommandMessages.PLAYER_AMBIGUOUS)
                        .with("input", input)
                        .with("count", matches.size());
            }
        }
        if (player == null) {
            throw CommandMessageException.of(SpigotCommandMessages.PLAYER_NOT_FOUND).with("input", input);
        }
        return player;
    }

    @Override
    public Collection<CommandMessageKey> messageKeys() {
        return Arrays.asList(SpigotCommandMessages.PLAYER_NOT_FOUND, SpigotCommandMessages.PLAYER_AMBIGUOUS);
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            Player requester = context.getSender().unwrap(Player.class).orElse(null);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (requester == null || requester.canSee(player)) {
                    values.add(player.getName());
                }
            }
            return values;
        };
    }
}

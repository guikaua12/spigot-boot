package tech.guilhermekaua.spigotboot.commands.spigot.resolve;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;
import java.util.List;

public class BukkitOfflinePlayerArgumentResolver implements CommandArgumentResolver<OfflinePlayer>, Ordered {
    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return OfflinePlayer.class.equals(parameter.getValueType()) && !Player.class.equals(parameter.getValueType());
    }

    @Override
    public OfflinePlayer resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }

        OfflinePlayer fallback = Bukkit.getOfflinePlayer(input);
        if (fallback.getName() == null && !fallback.hasPlayedBefore()) {
            throw new IllegalArgumentException("Offline player not found: " + input);
        }
        return fallback;
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null) {
                    values.add(player.getName());
                }
            }
            return values;
        };
    }
}

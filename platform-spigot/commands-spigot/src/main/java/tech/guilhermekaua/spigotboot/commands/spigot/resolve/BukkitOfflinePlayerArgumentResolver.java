package tech.guilhermekaua.spigotboot.commands.spigot.resolve;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class BukkitOfflinePlayerArgumentResolver implements CommandArgumentResolver<OfflinePlayer>, Ordered {
    private volatile List<String> cachedNames = Collections.emptyList();
    private volatile long lastRefreshTime;

    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return OfflinePlayer.class.equals(parameter.getValueType());
    }

    @Override
    public OfflinePlayer resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }

        OfflinePlayer fallback = Bukkit.getOfflinePlayer(input);
        if (fallback.getName() == null && !fallback.hasPlayedBefore()) {
            throw CommandMessageException.of(SpigotCommandMessages.OFFLINE_NOT_FOUND).with("input", input);
        }
        return fallback;
    }

    @Override
    public Collection<CommandMessageKey> messageKeys() {
        return Collections.singletonList(SpigotCommandMessages.OFFLINE_NOT_FOUND);
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> {
            long now = System.currentTimeMillis();
            if (now - lastRefreshTime > 30_000) {
                List<String> names = new ArrayList<>();
                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    if (player.getName() != null) {
                        names.add(player.getName());
                    }
                }
                cachedNames = names;
                lastRefreshTime = now;
            }

            String prefix = input == null ? "" : input.toLowerCase();
            List<String> filtered = new ArrayList<>();
            for (String name : cachedNames) {
                if (name.toLowerCase().startsWith(prefix)) {
                    filtered.add(name);
                }
            }
            return filtered;
        };
    }
}

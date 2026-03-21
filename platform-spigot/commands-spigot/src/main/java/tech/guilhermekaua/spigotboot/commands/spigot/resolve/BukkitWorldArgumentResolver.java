package tech.guilhermekaua.spigotboot.commands.spigot.resolve;

import org.bukkit.Bukkit;
import org.bukkit.World;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.ArrayList;
import java.util.List;

public class BukkitWorldArgumentResolver implements CommandArgumentResolver<World>, Ordered {
    @Override
    public int getOrder() {
        return 1000;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return World.class.equals(parameter.getValueType());
    }

    @Override
    public World resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        World world = Bukkit.getWorld(input);
        if (world == null) {
            throw new IllegalArgumentException("World not found: " + input);
        }
        return world;
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> {
            List<String> values = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                values.add(world.getName());
            }
            return values;
        };
    }
}

package tech.guilhermekaua.spigotboot.commands.spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.*;

public class BukkitCommandRegistrar {
    private final BukkitCommandMapAccessor commandMapAccessor;
    private final CommandDispatcher dispatcher;
    private final CommandPlatformSupport commandPlatformSupport;

    public BukkitCommandRegistrar(BukkitCommandMapAccessor commandMapAccessor,
                                  CommandDispatcher dispatcher,
                                  CommandPlatformSupport commandPlatformSupport) {
        this.commandMapAccessor = commandMapAccessor;
        this.dispatcher = dispatcher;
        this.commandPlatformSupport = commandPlatformSupport;
    }

    public RegisteredCommandSet register(Context context, List<CompiledRootCommand> roots) {
        CommandMap commandMap = commandMapAccessor.getCommandMap();
        Map<String, Command> knownCommands = commandMapAccessor.getKnownCommands(commandMap);

        Set<Command> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        List<SpigotBootCommand> commands = new ArrayList<>();

        for (CompiledRootCommand root : roots) {
            collectCollisions(context, root, knownCommands, toRemove);
            commands.add(new SpigotBootCommand(context, root, dispatcher, commandPlatformSupport));
        }

        for (Command existing : toRemove) {
            existing.unregister(commandMap);
            knownCommands.entrySet().removeIf(entry -> entry.getValue() == existing);
        }

        String prefix = context.getPlugin().getName().toLowerCase(Locale.ROOT);
        for (SpigotBootCommand command : commands) {
            commandMap.register(prefix, command);
        }

        return new RegisteredCommandSet(commands);
    }

    public void unregister(RegisteredCommandSet registeredCommandSet) {
        CommandMap commandMap = commandMapAccessor.getCommandMap();
        Map<String, Command> knownCommands = commandMapAccessor.getKnownCommands(commandMap);
        for (SpigotBootCommand command : registeredCommandSet.getCommands()) {
            command.unregister(commandMap);
            knownCommands.entrySet().removeIf(entry -> entry.getValue() == command);
        }
    }

    private void collectCollisions(Context context,
                                    CompiledRootCommand root,
                                    Map<String, Command> knownCommands,
                                    Set<Command> toRemove) {
        for (String label : root.getAliases().allValues()) {
            Command existing = knownCommands.get(label.toLowerCase(Locale.ROOT));
            if (existing == null) {
                continue;
            }

            if (existing instanceof SpigotBootCommand && ((SpigotBootCommand) existing).isOwnedBy(context.getPlugin())) {
                toRemove.add(existing);
                continue;
            }

            throw new IllegalStateException("Command label collision detected for '" + label + "'.");
        }
    }
}

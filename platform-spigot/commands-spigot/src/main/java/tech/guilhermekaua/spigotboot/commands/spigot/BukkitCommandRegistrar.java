package tech.guilhermekaua.spigotboot.commands.spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.*;

public class BukkitCommandRegistrar {
    private final BukkitCommandMapAccessor commandMapAccessor;
    private final CommandDispatcher dispatcher;

    public BukkitCommandRegistrar(BukkitCommandMapAccessor commandMapAccessor, CommandDispatcher dispatcher) {
        this.commandMapAccessor = commandMapAccessor;
        this.dispatcher = dispatcher;
    }

    public RegisteredCommandSet register(Context context, List<CompiledRootCommand> roots) {
        CommandMap commandMap = commandMapAccessor.getCommandMap();
        Map<String, Command> knownCommands = commandMapAccessor.getKnownCommands(commandMap);
        Set<Command> removedExisting = Collections.newSetFromMap(new IdentityHashMap<>());

        List<SpigotBootCommand> commands = new ArrayList<>();
        for (CompiledRootCommand root : roots) {
            checkAndPrepareCollisions(context, root, commandMap, knownCommands, removedExisting);

            SpigotBootCommand command = new SpigotBootCommand(context, root, dispatcher);
            commandMap.register(context.getPlugin().getName().toLowerCase(Locale.ROOT), command);
            commands.add(command);
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

    private void checkAndPrepareCollisions(Context context,
                                           CompiledRootCommand root,
                                           CommandMap commandMap,
                                           Map<String, Command> knownCommands,
                                           Set<Command> removedExisting) {
        for (String label : root.getAliases().allValues()) {
            Command existing = knownCommands.get(label.toLowerCase(Locale.ROOT));
            if (existing == null) {
                continue;
            }

            if (existing instanceof SpigotBootCommand && ((SpigotBootCommand) existing).isOwnedBy(context.getPlugin())) {
                if (removedExisting.add(existing)) {
                    existing.unregister(commandMap);
                    knownCommands.entrySet().removeIf(entry -> entry.getValue() == existing);
                }
                continue;
            }

            throw new IllegalStateException("Command label collision detected for '" + label + "'.");
        }
    }
}

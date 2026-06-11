package tech.guilhermekaua.spigotboot.commands.spigot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
            removeKnownCommandEntries(knownCommands, existing);
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
            removeKnownCommandEntries(knownCommands, command);
        }
    }

    private void removeKnownCommandEntries(Map<String, Command> knownCommands, Command command) {
        List<String> labelsToRemove = new ArrayList<>();
        for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
            if (entry.getValue() == command) {
                labelsToRemove.add(entry.getKey());
            }
        }

        for (String label : labelsToRemove) {
            knownCommands.remove(label, command);
        }
    }

    private void collectCollisions(Context context,
                                   CompiledRootCommand root,
                                   Map<String, Command> knownCommands,
                                    Set<Command> toRemove) {
        for (String label : root.getAliases().allValues()) {
            Command existing = knownCommands.get(CommandSupport.normalizeLabel(label));
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

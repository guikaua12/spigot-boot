package tech.guilhermekaua.spigotboot.commands.message;

import tech.guilhermekaua.spigotboot.commands.CommandMessages;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CommandMessagesProvider {
    private final DefaultCommandMessages defaultMessages;

    public CommandMessagesProvider(DefaultCommandMessages defaultMessages) {
        this.defaultMessages = defaultMessages;
    }

    public CommandMessages resolve(Context context) {
        Collection<Object> instances = context.getDependencyManager().getBeanInstanceRegistry().asMapView().values();
        List<CommandMessages> candidates = new ArrayList<>();
        for (Object instance : instances) {
            if (instance instanceof CommandMessages && instance != defaultMessages) {
                candidates.add((CommandMessages) instance);
            }
        }

        List<CommandMessages> unique = CommandSupport.deduplicateByIdentity(candidates);
        if (unique.isEmpty()) {
            return defaultMessages;
        }
        if (unique.size() == 1) {
            return unique.get(0);
        }

        CommandMessages primary = null;
        for (Map.Entry<BeanDefinition, Object> entry : context.getDependencyManager().getBeanInstanceRegistry().asMapView().entrySet()) {
            BeanDefinition definition = entry.getKey();
            Object instance = entry.getValue();
            if (!definition.isPrimary() || !(instance instanceof CommandMessages) || instance == defaultMessages) {
                continue;
            }

            if (primary != null && primary != instance) {
                throw new IllegalStateException("Multiple primary CommandMessages beans were found.");
            }
            primary = (CommandMessages) instance;
        }

        if (primary == null) {
            throw new IllegalStateException("Multiple CommandMessages beans were found. Mark exactly one as @Primary.");
        }
        return primary;
    }
}

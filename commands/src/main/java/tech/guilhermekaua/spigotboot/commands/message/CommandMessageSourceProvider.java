package tech.guilhermekaua.spigotboot.commands.message;

import tech.guilhermekaua.spigotboot.commands.CommandMessageSource;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Resolves the active {@link CommandMessageSource} from the dependency context, mirroring
 * {@link CommandMessagesProvider}: no bean yields a no-op source, one bean wins, several
 * require exactly one {@code @Primary}.
 */
public class CommandMessageSourceProvider {
    private static final CommandMessageSource NO_OP = (context, key) -> null;

    /**
     * @param context the command context; must not be {@code null}
     * @return the resolved source, never {@code null} (a no-op source when none is registered)
     */
    public CommandMessageSource resolve(Context context) {
        Collection<Object> instances = context.getDependencyManager().getBeanInstanceRegistry().asMapView().values();
        List<CommandMessageSource> candidates = new ArrayList<>();
        for (Object instance : instances) {
            if (instance instanceof CommandMessageSource) {
                candidates.add((CommandMessageSource) instance);
            }
        }

        List<CommandMessageSource> unique = CommandSupport.deduplicateByIdentity(candidates);
        if (unique.isEmpty()) {
            return NO_OP;
        }
        if (unique.size() == 1) {
            return unique.get(0);
        }

        CommandMessageSource primary = null;
        for (Map.Entry<BeanDefinition, Object> entry : context.getDependencyManager().getBeanInstanceRegistry().asMapView().entrySet()) {
            BeanDefinition definition = entry.getKey();
            Object instance = entry.getValue();
            if (!definition.isPrimary() || !(instance instanceof CommandMessageSource)) {
                continue;
            }

            if (primary != null && primary != instance) {
                throw new IllegalStateException("Multiple primary CommandMessageSource beans were found.");
            }
            primary = (CommandMessageSource) instance;
        }

        if (primary == null) {
            throw new IllegalStateException("Multiple CommandMessageSource beans were found. Mark exactly one as @Primary.");
        }
        return primary;
    }
}

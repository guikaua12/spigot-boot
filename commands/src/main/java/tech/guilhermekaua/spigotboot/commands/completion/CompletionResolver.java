package tech.guilhermekaua.spigotboot.commands.completion;

import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinding;

import java.util.*;
import java.util.logging.Level;

public class CompletionResolver {
    private final CommandCompletionRegistry completionRegistry;
    private final CommandArgumentResolverRegistry argumentResolverRegistry;

    public CompletionResolver(CommandCompletionRegistry completionRegistry,
                              CommandArgumentResolverRegistry argumentResolverRegistry) {
        this.completionRegistry = completionRegistry;
        this.argumentResolverRegistry = argumentResolverRegistry;
    }

    public List<String> resolve(CommandExecutionContext context, CommandParameterBinding binding, String input) {
        String completionId = binding.getCompletionId();
        CommandCompletionProvider provider = (completionId != null && !completionId.isEmpty())
                ? completionRegistry.resolve(completionId).orElse(null)
                : null;

        if (provider == null) {
            provider = argumentResolverRegistry.resolve(binding.getMetadata())
                    .map(CommandArgumentResolver::defaultCompletionProvider)
                    .orElse(null);
        }

        if (provider == null) {
            return Collections.emptyList();
        }

        String prefix = input == null ? "" : input;
        List<String> suggestions;
        try {
            suggestions = provider.complete(context, binding.getMetadata(), prefix);
        } catch (Exception e) {
            context.getPlugin().getLogger().log(
                    Level.WARNING,
                    "Error resolving command completions for parameter "
                            + binding.getMetadata().getParameterName(),
                    e
            );
            return Collections.emptyList();
        }
        if (suggestions == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : suggestions) {
            if (value == null) {
                continue;
            }
            if (prefix.isEmpty() || value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }
}

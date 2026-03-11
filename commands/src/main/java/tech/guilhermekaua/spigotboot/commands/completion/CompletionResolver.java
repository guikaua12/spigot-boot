package tech.guilhermekaua.spigotboot.commands.completion;

import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class CompletionResolver {
    private final CommandCompletionRegistry completionRegistry;
    private final CommandArgumentResolverRegistry argumentResolverRegistry;

    public CompletionResolver(CommandCompletionRegistry completionRegistry,
                              CommandArgumentResolverRegistry argumentResolverRegistry) {
        this.completionRegistry = completionRegistry;
        this.argumentResolverRegistry = argumentResolverRegistry;
    }

    public List<String> resolve(CommandExecutionContext context, CommandParameterBinding binding, String input) {
        CommandCompletionProvider provider = null;
        String completionId = binding.getCompletionId();
        if (completionId != null && !completionId.isEmpty()) {
            provider = completionRegistry.resolve(completionId);
        }

        if (provider == null) {
            CommandArgumentResolver<?> resolver = argumentResolverRegistry.resolve(binding.getMetadata());
            if (resolver != null) {
                provider = resolver.defaultCompletionProvider();
            }
        }

        if (provider == null) {
            return java.util.Collections.emptyList();
        }

        String prefix = input == null ? "" : input;
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : provider.complete(context, binding.getMetadata(), prefix)) {
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

package tech.guilhermekaua.spigotboot.commands.completion;

import tech.guilhermekaua.spigotboot.commands.CommandCompletionProvider;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultCommandCompletionRegistry implements CommandCompletionRegistry {
    private final Map<String, CommandCompletionProvider> providers = new LinkedHashMap<>();

    public DefaultCommandCompletionRegistry(List<CommandCompletionRegistryCustomizer> customizers) {
        registerBuiltIns();
        for (CommandCompletionRegistryCustomizer customizer : CommandSupport.sortBeans(customizers)) {
            customizer.customize(this);
        }
    }

    @Override
    public void register(String id, CommandCompletionProvider provider) {
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("Completion id cannot be blank.");
        }
        providers.put(normalizedId, provider);
    }

    @Override
    public CommandCompletionProvider resolve(String id) {
        if (id == null) {
            return null;
        }
        return providers.get(id.trim());
    }

    private void registerBuiltIns() {
        register("booleans", (context, parameter, input) -> Arrays.asList("true", "false"));
    }
}

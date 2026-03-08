package tech.guilhermekaua.spigotboot.commands;

import java.util.function.Supplier;

public interface CommandReplacementRegistryCustomizer {
    void customize(CommandReplacementRegistry registry);

    static CommandReplacementRegistryCustomizer register(String key, String value) {
        return registry -> registry.register(key, value);
    }

    static CommandReplacementRegistryCustomizer register(String key, Supplier<String> valueSupplier) {
        return registry -> registry.register(key, valueSupplier);
    }
}

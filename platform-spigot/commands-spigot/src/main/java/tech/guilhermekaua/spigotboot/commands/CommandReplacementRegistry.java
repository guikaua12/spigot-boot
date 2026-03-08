package tech.guilhermekaua.spigotboot.commands;

import java.util.function.Supplier;

public interface CommandReplacementRegistry {
    void register(String key, String value);

    default void register(String key, Supplier<String> valueSupplier) {
        register(key, valueSupplier == null ? null : valueSupplier.get());
    }

    String replace(String value);
}

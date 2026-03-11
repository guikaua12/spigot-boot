package tech.guilhermekaua.spigotboot.commands;

import java.util.function.Supplier;

public interface CommandReplacementRegistry {
    void register(String key, String value);

    /**
     * Registers a replacement supplier for the given key.
     *
     * <p>The default implementation eagerly evaluates the supplier once and delegates
     * to {@link #register(String, String)}. Implementations that need lazy evaluation
     * on each {@link #replace(String)} call (e.g. {@code DefaultCommandReplacementRegistry})
     * should override this method.
     */
    default void register(String key, Supplier<String> valueSupplier) {
        register(key, valueSupplier == null ? null : valueSupplier.get());
    }

    String replace(String value);
}

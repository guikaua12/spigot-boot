package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.message.CommandMessageTemplates;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Raised by a {@link CommandArgumentResolver} to signal a specific, customizable failure.
 * The carried {@link CommandMessageKey} and placeholder values are rendered into the final
 * message by the command pipeline, letting a downstream {@link CommandMessageSource}
 * override the text per key.
 */
public class CommandMessageException extends RuntimeException {
    private final CommandMessageKey key;
    private final Map<String, Object> placeholders = new LinkedHashMap<>();

    private CommandMessageException(CommandMessageKey key) {
        this.key = Objects.requireNonNull(key, "key must not be null");
    }

    /**
     * @param key the message key describing this failure; must not be {@code null}
     * @return a new exception carrying {@code key} and no placeholders yet
     */
    public static CommandMessageException of(CommandMessageKey key) {
        return new CommandMessageException(key);
    }

    /**
     * Binds a placeholder value for rendering.
     *
     * @param name  the placeholder name (without braces); must not be {@code null}
     * @param value the value; may be {@code null}
     * @return this exception, for chaining
     */
    public CommandMessageException with(String name, Object value) {
        placeholders.put(Objects.requireNonNull(name, "name must not be null"), value);
        return this;
    }

    /**
     * @return the key describing this failure
     */
    public CommandMessageKey getKey() {
        return key;
    }

    /**
     * @return an unmodifiable view of the bound placeholder values
     */
    public Map<String, Object> getPlaceholders() {
        return Collections.unmodifiableMap(placeholders);
    }

    @Override
    public String getMessage() {
        return CommandMessageTemplates.interpolate(key.defaultTemplate(), placeholders);
    }
}

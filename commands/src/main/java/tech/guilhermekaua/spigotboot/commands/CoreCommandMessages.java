package tech.guilhermekaua.spigotboot.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The complete set of customizable message keys raised by the framework's built-in
 * argument resolvers. Reference these constants from a {@link CommandMessageSource} to
 * override the corresponding messages.
 */
public enum CoreCommandMessages implements CommandMessageKey {
    /** Raised when a boolean argument cannot be parsed. Placeholders: {@code {input}}. */
    BOOLEAN_INVALID("boolean.invalid", "'{input}' is not a valid true/false value.", "input"),
    /** Raised when a numeric argument cannot be parsed. Placeholders: {@code {input}}. */
    NUMBER_INVALID("number.invalid", "'{input}' is not a valid number.", "input"),
    /** Raised when an enum argument does not match any constant. Placeholders: {@code {input}}, {@code {options}}. */
    ENUM_INVALID("enum.invalid", "'{input}' is not a valid option. Valid: {options}.", "input", "options"),
    /** Raised when a UUID argument cannot be parsed. Placeholders: {@code {input}}. */
    UUID_INVALID("uuid.invalid", "'{input}' is not a valid UUID.", "input");

    private final String id;
    private final String defaultTemplate;
    private final List<String> placeholders;

    CoreCommandMessages(String id, String defaultTemplate, String... placeholders) {
        this.id = id;
        this.defaultTemplate = defaultTemplate;
        this.placeholders = Collections.unmodifiableList(Arrays.asList(placeholders));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String defaultTemplate() {
        return defaultTemplate;
    }

    @Override
    public List<String> placeholders() {
        return placeholders;
    }
}

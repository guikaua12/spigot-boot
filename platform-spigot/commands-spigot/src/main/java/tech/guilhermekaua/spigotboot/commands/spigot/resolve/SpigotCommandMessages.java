package tech.guilhermekaua.spigotboot.commands.spigot.resolve;

import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The complete set of customizable message keys raised by the Spigot built-in argument
 * resolvers. Reference these constants from a {@code CommandMessageSource} to override the
 * corresponding messages.
 */
public enum SpigotCommandMessages implements CommandMessageKey {
    /** Raised when no online player matches the input. Placeholders: {@code {input}}. */
    PLAYER_NOT_FOUND("player.not-found", "No player named '{input}' is online.", "input"),
    /** Raised when the input matches several online players. Placeholders: {@code {input}}, {@code {count}}. */
    PLAYER_AMBIGUOUS("player.ambiguous", "'{input}' matches {count} players.", "input", "count"),
    /** Raised when no world matches the input. Placeholders: {@code {input}}. */
    WORLD_NOT_FOUND("world.not-found", "World '{input}' does not exist.", "input"),
    /** Raised when no known offline player matches the input. Placeholders: {@code {input}}. */
    OFFLINE_NOT_FOUND("offline-player.not-found", "Never seen a player named '{input}'.", "input");

    private final String id;
    private final String defaultTemplate;
    private final List<String> placeholders;

    SpigotCommandMessages(String id, String defaultTemplate, String... placeholders) {
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

package tech.guilhermekaua.spigotboot.commands;

import java.util.List;

/**
 * A stable, discoverable identifier for a customizable command message raised by an
 * argument resolver. Implementations are typically enum constants so the full set of keys
 * is discoverable through IDE autocompletion.
 */
public interface CommandMessageKey {
    /**
     * @return the stable identifier of this key, for example {@code "player.not-found"}
     */
    String id();

    /**
     * @return the fallback message template used when no {@link CommandMessageSource}
     *         overrides this key; may contain {@code {placeholder}} tokens
     */
    String defaultTemplate();

    /**
     * @return the documented placeholder names this key's template understands
     */
    List<String> placeholders();
}

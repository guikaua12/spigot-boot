package tech.guilhermekaua.spigotboot.commands;

/**
 * Customization point for command messages raised via {@link CommandMessageException}.
 * Register an implementation as a bean to override the text for one or more
 * {@link CommandMessageKey}s.
 */
public interface CommandMessageSource {
    /**
     * Resolves the template to use for {@code key}.
     *
     * @param context the current execution context (e.g. for per-sender decisions)
     * @param key     the key being rendered
     * @return the template to use, or {@code null} to fall back to {@code key.defaultTemplate()}
     */
    String resolveTemplate(CommandExecutionContext context, CommandMessageKey key);
}

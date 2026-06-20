package tech.guilhermekaua.spigotboot.commands.message;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageSource;

import java.util.Objects;

/**
 * Renders a {@link CommandMessageException} into the final message string: the active
 * {@link CommandMessageSource} template if it supplies one, otherwise the key's default
 * template, then placeholder interpolation.
 */
public class CommandMessageRenderer {
    private final CommandMessageSourceProvider sourceProvider;

    public CommandMessageRenderer(CommandMessageSourceProvider sourceProvider) {
        this.sourceProvider = Objects.requireNonNull(sourceProvider, "sourceProvider must not be null");
    }

    /**
     * @param context   the current execution context; must not be {@code null}
     * @param exception the keyed failure to render; must not be {@code null}
     * @return the message to send to the sender
     */
    public String render(CommandExecutionContext context, CommandMessageException exception) {
        CommandMessageSource source = sourceProvider.resolve(context.getContext());
        String template = source.resolveTemplate(context, exception.getKey());
        if (template == null) {
            template = exception.getKey().defaultTemplate();
        }
        return CommandMessageTemplates.interpolate(template, exception.getPlaceholders());
    }
}

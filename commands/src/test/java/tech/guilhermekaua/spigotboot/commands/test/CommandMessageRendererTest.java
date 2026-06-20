package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageException;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.CommandMessageSource;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageRenderer;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageSourceProvider;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandMessageRendererTest {
    private enum Key implements CommandMessageKey {
        NOT_FOUND;

        @Override public String id() { return "player.not-found"; }
        @Override public String defaultTemplate() { return "No player named '{input}' is online."; }
        @Override public List<String> placeholders() { return Collections.singletonList("input"); }
    }

    private final CommandMessageRenderer renderer = new CommandMessageRenderer(new CommandMessageSourceProvider());

    private CommandExecutionContext executionContextWith(DependencyManager dependencyManager) {
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        CommandExecutionContext executionContext = mock(CommandExecutionContext.class);
        when(executionContext.getContext()).thenReturn(context);
        return executionContext;
    }

    @Test
    void usesDefaultTemplateWhenNoSourceRegistered() {
        CommandExecutionContext context = executionContextWith(new DependencyManager());
        CommandMessageException exception = CommandMessageException.of(Key.NOT_FOUND).with("input", "bob");

        assertEquals("No player named 'bob' is online.", renderer.render(context, exception));
    }

    @Test
    void usesSourceTemplateWhenItReturnsNonNull() {
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessageSource source = (ctx, key) -> "&cNao achei '{input}'.";
        dependencyManager.registerDependency(source, "source", true);
        CommandExecutionContext context = executionContextWith(dependencyManager);
        CommandMessageException exception = CommandMessageException.of(Key.NOT_FOUND).with("input", "bob");

        assertEquals("&cNao achei 'bob'.", renderer.render(context, exception));
    }

    @Test
    void fallsBackToDefaultWhenSourceReturnsNull() {
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessageSource source = (ctx, key) -> null;
        dependencyManager.registerDependency(source, "source", true);
        CommandExecutionContext context = executionContextWith(dependencyManager);
        CommandMessageException exception = CommandMessageException.of(Key.NOT_FOUND).with("input", "bob");

        assertEquals("No player named 'bob' is online.", renderer.render(context, exception));
    }
}

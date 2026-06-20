package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessageKey;
import tech.guilhermekaua.spigotboot.commands.CommandMessageSource;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageSourceProvider;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandMessageSourceProviderTest {
    private final CommandMessageSourceProvider provider = new CommandMessageSourceProvider();

    private Context contextWith(DependencyManager dependencyManager) {
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        return context;
    }

    @Test
    void noSourceReturnsNoOpThatYieldsNull() {
        Context context = contextWith(new DependencyManager());
        CommandMessageSource resolved = provider.resolve(context);
        assertNull(resolved.resolveTemplate(mock(CommandExecutionContext.class), mock(CommandMessageKey.class)));
    }

    @Test
    void singleSourceIsReturned() {
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessageSource source = (ctx, key) -> "x";
        dependencyManager.registerDependency(source, "source", true);

        assertSame(source, provider.resolve(contextWith(dependencyManager)));
    }

    @Test
    void multipleSourcesWithoutPrimaryThrows() {
        DependencyManager dependencyManager = new DependencyManager();
        dependencyManager.registerDependency((CommandMessageSource) (ctx, key) -> "a", "a", false);
        dependencyManager.registerDependency((CommandMessageSource) (ctx, key) -> "b", "b", false);

        assertThrows(IllegalStateException.class, () -> provider.resolve(contextWith(dependencyManager)));
    }
}

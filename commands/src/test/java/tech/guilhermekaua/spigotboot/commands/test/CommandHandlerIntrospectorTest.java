package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.annotations.CatchUnknown;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHandlerIntrospectorTest {
    @Test
    void rejectsNestedCatchUnknownHandlers() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CommandHandlerIntrospector().introspect(new InvalidNestedUnknownCommands(), new DependencyManager())
        );

        assertTrue(exception.getMessage().contains("Nested command groups do not support @CatchUnknown"));
    }

    @CommandHandler
    @RootCommand("admin")
    static class InvalidNestedUnknownCommands {
        @Command("coin")
        public static class CoinCommands {
            @CatchUnknown
            public void unknown() {
            }
        }
    }
}

package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.guilhermekaua.spigotboot.commands.test.CommandTestSupport.platformSupport;

class CommandRouteValidatorTest {
    @Test
    void rejectsAmbiguousLiteralAndArgumentCrossOverloads() {
        CommandRouteFactory factory = newRouteFactory();
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(new AmbiguousCommands());
        CompiledRootCommand root = factory.create(metadata);

        assertThrows(IllegalStateException.class, () -> new CommandRouteValidator().validate(Collections.singletonList(root)));
    }

    private CommandRouteFactory newRouteFactory() {
        CommandReplacementRegistry replacementRegistry = new DefaultCommandReplacementRegistry(Collections.emptyList());
        return new CommandRouteFactory(
                new CommandPatternParser(),
                replacementRegistry,
                new CommandInvocationFactory(new CommandParameterRoleResolver(platformSupport()))
        );
    }

    @CommandHandler
    @RootCommand("admin")
    static class AmbiguousCommands {
        @Command("inspect <name> exact")
        public void first(String name) {
        }

        @Command("inspect exact <name>")
        public void second(String name) {
        }
    }
}

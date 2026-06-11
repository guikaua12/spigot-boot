package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistryCustomizer;
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
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.guilhermekaua.spigotboot.commands.test.CommandTestSupport.platformSupport;

class CommandReplacementRegistryTest {
    @Test
    void registerSupplierResolvesPercentPlaceholders() {
        DefaultCommandReplacementRegistry registry = new DefaultCommandReplacementRegistry(Collections.<CommandReplacementRegistryCustomizer>emptyList());

        registry.register("root", "admin");
        registry.register("command", () -> "%verb% <player>");
        registry.register("verb", () -> "set");

        assertEquals("admin set <player>", registry.replace("%root% %command%"));
    }

    @Test
    void customizerFactorySupportsSupplierRegistration() {
        DefaultCommandReplacementRegistry registry = new DefaultCommandReplacementRegistry(
                Collections.singletonList(CommandReplacementRegistryCustomizer.register("dynamic", () -> "coins"))
        );

        assertEquals("coins", registry.replace("%dynamic%"));
    }

    @Test
    void defaultRegistryEvaluatesSupplierForEachReplacement() {
        DefaultCommandReplacementRegistry registry = new DefaultCommandReplacementRegistry(Collections.<CommandReplacementRegistryCustomizer>emptyList());
        AtomicInteger counter = new AtomicInteger();

        registry.register("dynamic", () -> "value-" + counter.incrementAndGet());

        assertEquals("value-1", registry.replace("%dynamic%"));
        assertEquals("value-2", registry.replace("%dynamic%"));
    }

    @Test
    void routeFactoryAppliesPercentPlaceholdersToRootAndSubcommandPatterns() {
        DefaultCommandReplacementRegistry registry = new DefaultCommandReplacementRegistry(Arrays.asList(
                CommandReplacementRegistryCustomizer.register("root", () -> "admin|adm"),
                CommandReplacementRegistryCustomizer.register("verb", () -> "set|give")
        ));

        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                registry,
                new CommandInvocationFactory(new CommandParameterRoleResolver(platformSupport()))
        );

        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(new PlaceholderCommands());
        CompiledRootCommand root = factory.create(metadata);

        assertEquals("admin", root.getAliases().getPrimary());
        assertEquals(Collections.singletonList("adm"), root.getAliases().getAliases());
        assertEquals(
                Arrays.asList("coin set <player> <amount>", "coin give <player> <amount>"),
                routeSources(root)
        );
    }

    private List<String> routeSources(CompiledRootCommand root) {
        return root.getRoutes().stream()
                .map(route -> route.getPattern().getSource())
                .collect(Collectors.toList());
    }

    @CommandHandler
    @RootCommand("%root%")
    static class PlaceholderCommands {
        @Command("coin %verb% <player> <amount>")
        public void coins(String player, int amount) {
        }
    }
}

package tech.guilhermekaua.spigotboot.commands.test;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.*;
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
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CommandTextResolverChainTest {
    @Test
    void createWithContextProvidesRuntimeContextToResolvers() {
        AtomicReference<Context> seenContext = new AtomicReference<>();
        Context runtimeContext = new NoOpContext();

        CommandTextResolver resolver = (context, value) -> {
            seenContext.set(context.getContext());
            return value.replace("admin", "moderator");
        };

        CommandRouteFactory factory = newFactory(
                Collections.emptyList(),
                Collections.singletonList(resolver)
        );

        CompiledRootCommand root = factory.create(runtimeContext, introspect(new RootOnlyCommands()));

        assertSame(runtimeContext, seenContext.get());
        assertEquals("moderator", root.getAliases().getPrimary());
    }

    @Test
    void resolverChainRunsAfterReplacementAndHonorsOrder() {
        CommandRouteFactory factory = newFactory(
                Collections.singletonList(CommandReplacementRegistryCustomizer.register("verb", "se")),
                Arrays.asList(new ReplaceSetWithGiveResolver(), new CompleteSetResolver())
        );

        CompiledRootCommand root = factory.create(introspect(new ReplacementDrivenCommands()));

        assertEquals("coin give <player>", root.getRoutes().get(0).getPattern().getSource());
    }

    @Test
    void legacyCreateOverloadStillWorksWithoutRuntimeContext() {
        CommandTextResolver resolver = (context, value) -> value.replace("admin", "moderator");

        CommandRouteFactory factory = newFactory(
                Collections.emptyList(),
                Collections.singletonList(resolver)
        );

        CompiledRootCommand root = factory.create(introspect(new RootOnlyCommands()));

        assertEquals("moderator", root.getAliases().getPrimary());
    }

    private CommandRouteFactory newFactory(Iterable<CommandReplacementRegistryCustomizer> customizers,
                                           Iterable<CommandTextResolver> resolvers) {
        List<CommandReplacementRegistryCustomizer> replacementCustomizers = new java.util.ArrayList<>();
        for (CommandReplacementRegistryCustomizer customizer : customizers) {
            replacementCustomizers.add(customizer);
        }

        List<CommandTextResolver> textResolvers = new java.util.ArrayList<>();
        for (CommandTextResolver resolver : resolvers) {
            textResolvers.add(resolver);
        }

        return new CommandRouteFactory(
                new CommandPatternParser(),
                new DefaultCommandReplacementRegistry(replacementCustomizers),
                new CommandTextResolverChain(textResolvers),
                new CommandInvocationFactory(new CommandParameterRoleResolver(new TestPlatformSupport()))
        );
    }

    private RootCommandMetadata introspect(Object handler) {
        return new CommandHandlerIntrospector().introspect(handler);
    }

    private static final class TestPlatformSupport implements CommandPlatformSupport {
        @Override
        public CommandSenderHandle createSender(Object nativeSender) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSenderType(Class<?> type) {
            return false;
        }
    }

    private static final class NoOpContext implements Context {
        @Override
        public void initialize() {
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public void registerBean(@NotNull Object instance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerBean(@NotNull Class<?> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> @NotNull List<T> getBeansByType(@NotNull Class<T> type) {
            return Collections.emptyList();
        }

        @Override
        public @NotNull DependencyManager getDependencyManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroy() {
        }

        @Override
        public @NotNull List<Class<? extends Module>> getModulesToLoad() {
            return Collections.emptyList();
        }

        @Override
        public void setModulesToLoad(@NotNull List<Class<? extends Module>> modulesToLoad) {
        }

        @Override
        public BootPlugin getPlugin() {
            return null;
        }

        @Override
        public void registerShutdownHook(@NotNull Runnable runnable) {
        }

        @Override
        public void unregisterShutdownHook(@NotNull Runnable runnable) {
        }
    }

    private static final class CompleteSetResolver implements CommandTextResolver, Ordered {
        @Override
        public String resolve(CommandTextResolutionContext context, String value) {
            return value.replace(" se", " set");
        }

        @Override
        public int getOrder() {
            return -10;
        }
    }

    private static final class ReplaceSetWithGiveResolver implements CommandTextResolver, Ordered {
        @Override
        public String resolve(CommandTextResolutionContext context, String value) {
            return value.replace("set", "give");
        }

        @Override
        public int getOrder() {
            return 10;
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class RootOnlyCommands {
        @Command("coin")
        public void coin() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class ReplacementDrivenCommands {
        @Command("coin %verb% <player>")
        public void coin(String player) {
        }
    }
}

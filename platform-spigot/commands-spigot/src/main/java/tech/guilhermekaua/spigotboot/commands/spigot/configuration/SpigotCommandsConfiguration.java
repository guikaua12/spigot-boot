package tech.guilhermekaua.spigotboot.commands.spigot.configuration;

import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.cooldown.CommandCooldownInterceptor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageRenderer;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandMapAccessor;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandRegistrar;
import tech.guilhermekaua.spigotboot.commands.spigot.CommandsContextReadyRegistrar;
import tech.guilhermekaua.spigotboot.commands.spigot.completion.BukkitCommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.BukkitOfflinePlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.BukkitPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.BukkitWorldArgumentResolver;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.cooldown.CooldownManager;

@Configuration
public class SpigotCommandsConfiguration {
    @Bean
    public CommandPlatformSupport commandPlatformSupport() {
        return new BukkitCommandPlatformSupport();
    }

    @Bean
    public CommandParameterRoleResolver commandParameterRoleResolver(CommandPlatformSupport commandPlatformSupport) {
        return new CommandParameterRoleResolver(commandPlatformSupport);
    }

    @Bean
    public CommandInvocationFactory commandInvocationFactory(CommandParameterRoleResolver commandParameterRoleResolver) {
        return new CommandInvocationFactory(commandParameterRoleResolver);
    }

    @Bean
    public CommandRouteFactory commandRouteFactory(CommandPatternParser commandPatternParser,
                                                   CommandReplacementRegistry commandReplacementRegistry,
                                                   CommandTextResolverChain commandTextResolverChain,
                                                   CommandInvocationFactory commandInvocationFactory) {
        return new CommandRouteFactory(
                commandPatternParser,
                commandReplacementRegistry,
                commandTextResolverChain,
                commandInvocationFactory
        );
    }

    @Bean
    public CommandRootCompiler commandRootCompiler(CommandHandlerIntrospector commandHandlerIntrospector,
                                                   CommandRouteFactory commandRouteFactory,
                                                   CommandRouteValidator commandRouteValidator,
                                                   CommandInterceptorChain commandInterceptorChain) {
        return new CommandRootCompiler(
                commandHandlerIntrospector,
                commandRouteFactory,
                commandRouteValidator,
                commandInterceptorChain
        );
    }

    @Bean
    public CommandParameterBinder commandParameterBinder(tech.guilhermekaua.spigotboot.commands.CommandArgumentResolverRegistry commandArgumentResolverRegistry) {
        return new CommandParameterBinder(commandArgumentResolverRegistry);
    }

    @Bean
    public CommandDispatcher commandDispatcher(CommandParameterBinder commandParameterBinder,
                                               CommandInvocationExecutor commandInvocationExecutor,
                                               CommandMessagesProvider commandMessagesProvider,
                                               tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver completionResolver,
                                               CommandInterceptorChain commandInterceptorChain,
                                               CommandMessageRenderer commandMessageRenderer) {
        return new CommandDispatcher(
                commandParameterBinder,
                commandInvocationExecutor,
                commandMessagesProvider,
                completionResolver,
                commandInterceptorChain,
                commandMessageRenderer
        );
    }

    @Bean
    public CommandCooldownInterceptor commandCooldownInterceptor(CooldownManager cooldownManager,
                                                                 CommandMessagesProvider commandMessagesProvider) {
        return new CommandCooldownInterceptor(cooldownManager, commandMessagesProvider);
    }

    @Bean
    public BukkitCommandMapAccessor bukkitCommandMapAccessor() {
        return new BukkitCommandMapAccessor();
    }

    @Bean
    public BukkitCommandRegistrar bukkitCommandRegistrar(BukkitCommandMapAccessor bukkitCommandMapAccessor,
                                                         CommandDispatcher commandDispatcher,
                                                         CommandPlatformSupport commandPlatformSupport) {
        return new BukkitCommandRegistrar(bukkitCommandMapAccessor, commandDispatcher, commandPlatformSupport);
    }

    @Bean
    public CommandsContextReadyRegistrar commandsContextReadyRegistrar(CommandRootCompiler commandRootCompiler,
                                                                       BukkitCommandRegistrar bukkitCommandRegistrar,
                                                                       CommandReplacementRegistry commandReplacementRegistry) {
        return new CommandsContextReadyRegistrar(
                commandRootCompiler,
                bukkitCommandRegistrar,
                commandReplacementRegistry
        );
    }

    @Bean
    public CommandCompletionRegistryCustomizer bukkitCommandCompletionRegistryCustomizer() {
        return new BukkitCommandCompletionRegistryCustomizer();
    }

    @Bean
    public CommandArgumentResolver<?> bukkitPlayerArgumentResolver() {
        return new BukkitPlayerArgumentResolver();
    }

    @Bean
    public CommandArgumentResolver<?> bukkitOfflinePlayerArgumentResolver() {
        return new BukkitOfflinePlayerArgumentResolver();
    }

    @Bean
    public CommandArgumentResolver<?> bukkitWorldArgumentResolver() {
        return new BukkitWorldArgumentResolver();
    }
}

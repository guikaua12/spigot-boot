package tech.guilhermekaua.spigotboot.commands.configuration;

import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.commands.completion.DefaultCommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.cooldown.CommandCooldownInterceptor;
import tech.guilhermekaua.spigotboot.commands.cooldown.FixedCommandCooldownPolicy;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.message.DefaultCommandMessages;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandMapAccessor;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandRegistrar;
import tech.guilhermekaua.spigotboot.commands.spigot.CommandsContextReadyRegistrar;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.cooldown.CooldownManager;

import java.util.List;

@Configuration
public class CommandsConfiguration {
    @Bean
    public CommandPatternParser commandPatternParser() {
        return new CommandPatternParser();
    }

    @Bean
    public CommandHandlerIntrospector commandHandlerIntrospector() {
        return new CommandHandlerIntrospector();
    }

    @Bean
    public CommandReplacementRegistry commandReplacementRegistry(List<CommandReplacementRegistryCustomizer> customizers) {
        return new DefaultCommandReplacementRegistry(customizers);
    }

    @Bean
    public CommandArgumentResolverRegistry commandArgumentResolverRegistry(List<CommandArgumentResolver<?>> customResolvers,
                                                                           List<CommandArgumentResolverRegistryCustomizer> customizers) {
        return new DefaultCommandArgumentResolverRegistry(customResolvers, customizers);
    }

    @Bean
    public CommandCompletionRegistry commandCompletionRegistry(List<CommandCompletionRegistryCustomizer> customizers) {
        return new DefaultCommandCompletionRegistry(customizers);
    }

    @Bean
    public DefaultCommandMessages defaultCommandMessages() {
        return new DefaultCommandMessages();
    }

    @Bean
    public CommandMessagesProvider commandMessagesProvider(DefaultCommandMessages defaultCommandMessages) {
        return new CommandMessagesProvider(defaultCommandMessages);
    }

    @Bean
    public FixedCommandCooldownPolicy fixedCommandCooldownPolicy() {
        return new FixedCommandCooldownPolicy();
    }

    @Bean
    public CommandCooldownInterceptor commandCooldownInterceptor(CooldownManager cooldownManager,
                                                                 CommandMessagesProvider commandMessagesProvider) {
        return new CommandCooldownInterceptor(cooldownManager, commandMessagesProvider);
    }

    @Bean
    public CommandParameterRoleResolver commandParameterRoleResolver() {
        return new CommandParameterRoleResolver();
    }

    @Bean
    public CommandInvocationFactory commandInvocationFactory(CommandParameterRoleResolver commandParameterRoleResolver) {
        return new CommandInvocationFactory(commandParameterRoleResolver);
    }

    @Bean
    public CommandRouteFactory commandRouteFactory(CommandPatternParser commandPatternParser,
                                                   CommandReplacementRegistry commandReplacementRegistry,
                                                   CommandInvocationFactory commandInvocationFactory) {
        return new CommandRouteFactory(commandPatternParser, commandReplacementRegistry, commandInvocationFactory);
    }

    @Bean
    public CommandRouteValidator commandRouteValidator() {
        return new CommandRouteValidator();
    }

    @Bean
    public CommandParameterBinder commandParameterBinder(CommandArgumentResolverRegistry commandArgumentResolverRegistry) {
        return new CommandParameterBinder(commandArgumentResolverRegistry);
    }

    @Bean
    public CompletionResolver completionResolver(CommandCompletionRegistry commandCompletionRegistry,
                                                 CommandArgumentResolverRegistry commandArgumentResolverRegistry) {
        return new CompletionResolver(commandCompletionRegistry, commandArgumentResolverRegistry);
    }

    @Bean
    public CommandInterceptorChain commandInterceptorChain() {
        return new CommandInterceptorChain();
    }

    @Bean
    public CommandInvocationExecutor commandInvocationExecutor(CommandInterceptorChain commandInterceptorChain) {
        return new CommandInvocationExecutor(commandInterceptorChain);
    }

    @Bean
    public CommandDispatcher commandDispatcher(CommandParameterBinder commandParameterBinder,
                                               CommandInvocationExecutor commandInvocationExecutor,
                                               CommandMessagesProvider commandMessagesProvider,
                                               CompletionResolver completionResolver,
                                               CommandInterceptorChain commandInterceptorChain) {
        return new CommandDispatcher(
                commandParameterBinder,
                commandInvocationExecutor,
                commandMessagesProvider,
                completionResolver,
                commandInterceptorChain
        );
    }

    @Bean
    public BukkitCommandMapAccessor bukkitCommandMapAccessor() {
        return new BukkitCommandMapAccessor();
    }

    @Bean
    public BukkitCommandRegistrar bukkitCommandRegistrar(BukkitCommandMapAccessor bukkitCommandMapAccessor,
                                                         CommandDispatcher commandDispatcher) {
        return new BukkitCommandRegistrar(bukkitCommandMapAccessor, commandDispatcher);
    }

    @Bean
    public CommandsContextReadyRegistrar commandsContextReadyRegistrar(CommandHandlerIntrospector commandHandlerIntrospector,
                                                                       CommandRouteFactory commandRouteFactory,
                                                                       CommandRouteValidator commandRouteValidator,
                                                                       BukkitCommandRegistrar bukkitCommandRegistrar,
                                                                       CommandReplacementRegistry commandReplacementRegistry,
                                                                       CommandInterceptorChain commandInterceptorChain) {
        return new CommandsContextReadyRegistrar(
                commandHandlerIntrospector,
                commandRouteFactory,
                commandRouteValidator,
                bukkitCommandRegistrar,
                commandReplacementRegistry,
                commandInterceptorChain
        );
    }
}

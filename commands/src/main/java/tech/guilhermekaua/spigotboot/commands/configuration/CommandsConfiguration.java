package tech.guilhermekaua.spigotboot.commands.configuration;

import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.commands.completion.DefaultCommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.cooldown.FixedCommandCooldownPolicy;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageRenderer;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessageSourceProvider;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.message.DefaultCommandMessages;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;

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
    public CommandTextResolverChain commandTextResolverChain(List<CommandTextResolver> resolvers) {
        return new CommandTextResolverChain(resolvers);
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
    public CommandRouteValidator commandRouteValidator() {
        return new CommandRouteValidator();
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
    public CommandMessageSourceProvider commandMessageSourceProvider() {
        return new CommandMessageSourceProvider();
    }

    @Bean
    public CommandMessageRenderer commandMessageRenderer(CommandMessageSourceProvider commandMessageSourceProvider) {
        return new CommandMessageRenderer(commandMessageSourceProvider);
    }
}

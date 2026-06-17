/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.commands.bungee.configuration;

import net.md_5.bungee.api.plugin.Plugin;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandRootCompiler;
import tech.guilhermekaua.spigotboot.commands.CommandTextResolverChain;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandRegistrar;
import tech.guilhermekaua.spigotboot.commands.bungee.BungeeCommandsContextReadyRegistrar;
import tech.guilhermekaua.spigotboot.commands.bungee.completion.BungeeCommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.bungee.resolve.BungeeProxiedPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.bungee.resolve.BungeeServerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.cooldown.CommandCooldownInterceptor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.cooldown.CooldownManager;

/**
 * Wires the BungeeCord command pipeline. The 13 platform-neutral beans come from the generic
 * {@code CommandsConfiguration}; this configuration declares only the beans that depend on the
 * platform {@link CommandPlatformSupport}, plus the Bungee registrar, context-ready registrar,
 * argument resolvers, and completion customizer. Mirrors {@code SpigotCommandsConfiguration} (without
 * the CommandMap accessor, which BungeeCord does not need).
 */
@Configuration
public class BungeeCommandsConfiguration {

    @Bean
    public CommandPlatformSupport commandPlatformSupport() {
        return new BungeeCommandPlatformSupport();
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
    public CommandParameterBinder commandParameterBinder(CommandArgumentResolverRegistry commandArgumentResolverRegistry) {
        return new CommandParameterBinder(commandArgumentResolverRegistry);
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
    public CommandCooldownInterceptor commandCooldownInterceptor(CooldownManager cooldownManager,
                                                                 CommandMessagesProvider commandMessagesProvider) {
        return new CommandCooldownInterceptor(cooldownManager, commandMessagesProvider);
    }

    @Bean
    public BungeeCommandRegistrar bungeeCommandRegistrar(CommandDispatcher commandDispatcher,
                                                         CommandPlatformSupport commandPlatformSupport) {
        return new BungeeCommandRegistrar(commandDispatcher, commandPlatformSupport);
    }

    @Bean
    public BungeeCommandsContextReadyRegistrar bungeeCommandsContextReadyRegistrar(CommandRootCompiler commandRootCompiler,
                                                                                   BungeeCommandRegistrar bungeeCommandRegistrar,
                                                                                   CommandReplacementRegistry commandReplacementRegistry) {
        return new BungeeCommandsContextReadyRegistrar(
                commandRootCompiler,
                bungeeCommandRegistrar,
                commandReplacementRegistry
        );
    }

    @Bean
    public CommandCompletionRegistryCustomizer bungeeCommandCompletionRegistryCustomizer(Plugin plugin) {
        return new BungeeCommandCompletionRegistryCustomizer(plugin);
    }

    @Bean
    public CommandArgumentResolver<?> bungeeProxiedPlayerArgumentResolver(Plugin plugin) {
        return new BungeeProxiedPlayerArgumentResolver(plugin);
    }

    @Bean
    public CommandArgumentResolver<?> bungeeServerArgumentResolver(Plugin plugin) {
        return new BungeeServerArgumentResolver(plugin);
    }
}

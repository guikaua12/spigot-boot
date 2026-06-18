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
package tech.guilhermekaua.spigotboot.commands.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.annotations.CatchUnknown;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.DefaultCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Permission;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.bungee.completion.BungeeCommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.bungee.resolve.BungeeProxiedPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.bungee.resolve.BungeeServerArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.commands.completion.DefaultCommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.message.DefaultCommandMessages;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.bungee.BungeeBootPlugin;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BungeeBootCommandEndToEndTest {
    private final BungeeCommandPlatformSupport platformSupport = new BungeeCommandPlatformSupport();

    private Plugin mockPlugin(ProxyServer proxy) {
        Plugin plugin = mock(Plugin.class);
        PluginDescription description = mock(PluginDescription.class);
        when(description.getName()).thenReturn("TestPlugin");
        when(plugin.getDescription()).thenReturn(description);
        when(plugin.getProxy()).thenReturn(proxy);
        return plugin;
    }

    private Context newContext(Plugin plugin) {
        Context context = mock(Context.class);
        when(context.getPlugin()).thenReturn(new BungeeBootPlugin(plugin));
        when(context.getBean(Plugin.class)).thenReturn(plugin);
        when(context.getDependencyManager()).thenReturn(new DependencyManager());
        return context;
    }

    private CompiledRootCommand compileRoot(Object handler) {
        CommandReplacementRegistry replacementRegistry = new DefaultCommandReplacementRegistry(Collections.emptyList());
        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                replacementRegistry,
                new CommandInvocationFactory(new CommandParameterRoleResolver(platformSupport))
        );
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler);
        CompiledRootCommand root = factory.create(metadata);
        new CommandRouteValidator().validate(Collections.singletonList(root));
        return root;
    }

    private CommandDispatcher newDispatcher(Plugin plugin) {
        DefaultCommandArgumentResolverRegistry resolverRegistry = new DefaultCommandArgumentResolverRegistry(
                Arrays.asList(new BungeeProxiedPlayerArgumentResolver(plugin), new BungeeServerArgumentResolver(plugin)),
                Collections.emptyList()
        );
        DefaultCommandCompletionRegistry completionRegistry = new DefaultCommandCompletionRegistry(
                Collections.singletonList(new BungeeCommandCompletionRegistryCustomizer(plugin))
        );
        return new CommandDispatcher(
                new CommandParameterBinder(resolverRegistry),
                new CommandInvocationExecutor(new CommandInterceptorChain()),
                new CommandMessagesProvider(new DefaultCommandMessages()),
                new CompletionResolver(completionRegistry, resolverRegistry)
        );
    }

    @Test
    void executesWithSenderBindingAndProxiedPlayerAndServerResolution() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        ProxiedPlayer target = mock(ProxiedPlayer.class);
        ServerInfo lobby = mock(ServerInfo.class);
        when(sender.getName()).thenReturn("Sender");
        when(target.getName()).thenReturn("Target");
        when(lobby.getName()).thenReturn("lobby");
        when(proxy.getPlayer("Target")).thenReturn(target);
        when(proxy.getServerInfo("lobby")).thenReturn(lobby);

        ServerCommands handler = new ServerCommands();
        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(handler), newDispatcher(plugin), platformSupport);

        command.execute(sender, new String[]{"send", "Target", "lobby"});

        assertSame(sender, handler.lastSender);
        assertSame(target, handler.lastTarget);
        assertSame(lobby, handler.lastServer);
    }

    @Test
    void tabCompleteSurfacesPlayerNames() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer sender = mock(ProxiedPlayer.class);
        ProxiedPlayer target = mock(ProxiedPlayer.class);
        when(sender.getName()).thenReturn("Sender");
        when(target.getName()).thenReturn("Target");
        when(proxy.getPlayers()).thenReturn(Arrays.asList(sender, target));

        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(new ServerCommands()), newDispatcher(plugin), platformSupport);

        List<String> values = new ArrayList<>();
        command.onTabComplete(sender, new String[]{"send", "T"}).forEach(values::add);

        assertEquals(Collections.singletonList("Target"), values);
    }

    // ---------------------------------------------------------------------------------------------
    // command visibility vs. per-route permission.
    //
    // BungeeCord injects each registered command into the player's brigadier command tree (and thus
    // into client-side tab completion) in DownstreamBridge#handle(Commands) only when
    // command.hasPermission(connection) is true. The base BungeeBootCommand is created with a null
    // permission, so Command#hasPermission always returns true — meaning a player who cannot use any
    // subcommand still receives the command literal and sees it offered in completion.
    //
    // These tests pin the desired behaviour: the command must be visible iff the sender can use at
    // least one route (a subcommand or the default handler).
    // ---------------------------------------------------------------------------------------------

    @Test
    void commandWithOnlyAGatedSubcommandIsHiddenFromPlayersWithoutPermission() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("NoPerms");
        when(player.hasPermission("network.reload")).thenReturn(false);

        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(new GatedNetworkCommand()), newDispatcher(plugin), platformSupport);

        // argument-level completion is already permission-filtered: "/network <TAB>" suggests nothing.
        List<String> argSuggestions = new ArrayList<>();
        command.onTabComplete(player, new String[]{""}).forEach(argSuggestions::add);
        assertTrue(argSuggestions.isEmpty(), "the gated 'reload' subcommand must not be suggested");

        // but command visibility keys off Command#hasPermission, which BungeeCord uses to decide whether
        // to put "/network" in the player's command tree at all. It must be false here.
        assertFalse(command.hasPermission(player),
                "a player who can use no route must not see the command in tab completion");
    }

    @Test
    void commandWithAGatedSubcommandStaysVisibleToPlayersWithPermission() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("Admin");
        when(player.hasPermission("network.reload")).thenReturn(true);

        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(new GatedNetworkCommand()), newDispatcher(plugin), platformSupport);

        assertTrue(command.hasPermission(player),
                "a player who can use a route must still see the command");
    }

    @Test
    void commandWithPermissionlessDefaultStaysVisibleToEveryone() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("AnyOne");

        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(new OpenDefaultCommand()), newDispatcher(plugin), platformSupport);

        assertTrue(command.hasPermission(player),
                "a command whose default handler needs no permission must stay visible to everyone");
    }

    @Test
    void commandWithOnlyACatchUnknownHandlerIsHiddenFromEveryone() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("AnyOne");

        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(new CatchUnknownOnlyCommand()), newDispatcher(plugin), platformSupport);

        // @CatchUnknown only handles unmatched input; it is not a usable route, so the command exposes none.
        assertFalse(command.hasPermission(player),
                "a command whose only handler is @CatchUnknown offers no usable route and must stay hidden");
    }

    @Test
    void commandStaysVisibleWhenOnlyOneOfSeveralRoutesIsUngated() {
        ProxyServer proxy = mock(ProxyServer.class);
        Plugin plugin = mockPlugin(proxy);

        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getName()).thenReturn("NoPerms");
        when(player.hasPermission("monitor.reload")).thenReturn(false);

        BungeeBootCommand command = new BungeeBootCommand(
                newContext(plugin), compileRoot(new MixedAccessCommand()), newDispatcher(plugin), platformSupport);

        // the gated 'reload' route is denied, but the permissionless 'info' route is still usable.
        assertTrue(command.hasPermission(player),
                "a sender who can use at least one route must still see the command");
    }

    @CommandHandler
    @RootCommand("server")
    static class ServerCommands {
        private ProxiedPlayer lastSender;
        private ProxiedPlayer lastTarget;
        private ServerInfo lastServer;

        @Command("send <target> <server>")
        public void send(@Sender ProxiedPlayer sender, ProxiedPlayer target, ServerInfo server) {
            this.lastSender = sender;
            this.lastTarget = target;
            this.lastServer = server;
        }
    }

    @CommandHandler
    @RootCommand("network")
    static class GatedNetworkCommand {
        @Command("reload")
        @Permission("network.reload")
        public void reload(@Sender CommandSender sender) {
        }
    }

    @CommandHandler
    @RootCommand("status")
    static class OpenDefaultCommand {
        @DefaultCommand
        public void status(@Sender CommandSender sender) {
        }
    }

    @CommandHandler
    @RootCommand("broadcast")
    static class CatchUnknownOnlyCommand {
        @CatchUnknown
        public void unknown() {
        }
    }

    @CommandHandler
    @RootCommand("monitor")
    static class MixedAccessCommand {
        @Command("info")
        public void info(@Sender CommandSender sender) {
        }

        @Command("reload")
        @Permission("monitor.reload")
        public void reload(@Sender CommandSender sender) {
        }
    }
}

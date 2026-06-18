package tech.guilhermekaua.spigotboot.commands.test;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.annotations.*;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
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
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.spigot.SpigotBootCommand;
import tech.guilhermekaua.spigotboot.commands.spigot.completion.BukkitCommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.BukkitPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.spigot.SpigotBootPlugin;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpigotBootCommandTest {
    private ServerMock server;
    private final BukkitCommandPlatformSupport platformSupport = new BukkitCommandPlatformSupport();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void executesWithBukkitSenderBindingAndPlayerArgumentResolution() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Player sender = server.addPlayer("Sender");
        Player target = server.addPlayer("Target");

        TeleportCommands handler = new TeleportCommands();
        SpigotBootCommand command = new SpigotBootCommand(
                newContext(plugin),
                compileRoot(handler),
                newDispatcher(),
                platformSupport
        );

        boolean handled = command.execute(sender, "admin", new String[]{"tp", "Target"});

        assertTrue(handled);
        assertSame(sender, handler.lastSender);
        assertSame(target, handler.lastTarget);
    }

    @Test
    void tabCompleteUsesBukkitResolversAndCompletionCustomizer() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Player sender = server.addPlayer("Sender");
        server.addPlayer("Target");

        SpigotBootCommand command = new SpigotBootCommand(
                newContext(plugin),
                compileRoot(new TeleportCommands()),
                newDispatcher(),
                platformSupport
        );

        List<String> playerSuggestions = command.tabComplete(sender, "admin", new String[]{"tp", "T"});
        List<String> worldSuggestions = command.tabComplete(sender, "admin", new String[]{"warp", "w"});

        assertEquals(Collections.singletonList("Target"), playerSuggestions);
        assertTrue(worldSuggestions.contains("world"));

        // permission-gated command: player without permission gets no tab suggestions
        SpigotBootCommand restrictedCommand = new SpigotBootCommand(
                newContext(plugin),
                compileRoot(new AdminOnlyCommands()),
                newDispatcher(),
                platformSupport
        );

        List<String> restrictedSuggestions = restrictedCommand.tabComplete(sender, "restricted", new String[]{"m"});
        assertTrue(restrictedSuggestions.isEmpty());
    }

    // ---------------------------------------------------------------------------------------------
    // command visibility vs. per-route permission.
    //
    // CraftBukkit builds each command's client-side command-tree node with a requires(...) predicate
    // backed by Command#testPermissionSilent, so a command with only a permission-gated subcommand must
    // not be exposed in tab completion to players who cannot use any of its routes. Argument-level
    // completion is already filtered by the dispatcher (covered above); these tests pin the visibility gate.
    // ---------------------------------------------------------------------------------------------

    @Test
    void commandWithOnlyAGatedSubcommandIsHiddenFromPlayersWithoutPermission() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Player player = server.addPlayer("NoPerms");

        SpigotBootCommand command = new SpigotBootCommand(
                newContext(plugin), compileRoot(new GatedNetworkCommand()), newDispatcher(), platformSupport);

        assertFalse(command.testPermissionSilent(player),
                "a player who can use no route must not see the command in tab completion");
    }

    @Test
    void commandWithAGatedSubcommandStaysVisibleToPlayersWithPermission() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Player player = server.addPlayer("Admin");
        player.addAttachment(plugin, "network.reload", true);

        SpigotBootCommand command = new SpigotBootCommand(
                newContext(plugin), compileRoot(new GatedNetworkCommand()), newDispatcher(), platformSupport);

        assertTrue(command.testPermissionSilent(player),
                "a player who can use a route must still see the command");
    }

    @Test
    void commandWithPermissionlessDefaultStaysVisibleToEveryone() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Player player = server.addPlayer("AnyOne");

        SpigotBootCommand command = new SpigotBootCommand(
                newContext(plugin), compileRoot(new OpenDefaultCommand()), newDispatcher(), platformSupport);

        assertTrue(command.testPermissionSilent(player),
                "a command whose default handler needs no permission must stay visible to everyone");
    }

    @Test
    void commandWithOnlyACatchUnknownHandlerIsHiddenFromEveryone() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Player player = server.addPlayer("AnyOne");

        SpigotBootCommand command = new SpigotBootCommand(
                newContext(plugin), compileRoot(new CatchUnknownOnlyCommand()), newDispatcher(), platformSupport);

        // @CatchUnknown only handles unmatched input; it is not a usable route, so the command exposes none.
        assertFalse(command.testPermissionSilent(player),
                "a command whose only handler is @CatchUnknown offers no usable route and must stay hidden");
    }

    @Test
    void commandStaysVisibleWhenOnlyOneOfSeveralRoutesIsUngated() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Player player = server.addPlayer("NoPerms");

        SpigotBootCommand command = new SpigotBootCommand(
                newContext(plugin), compileRoot(new MixedAccessCommand()), newDispatcher(), platformSupport);

        // the gated 'reload' route is denied, but the permissionless 'info' route is still usable.
        assertTrue(command.testPermissionSilent(player),
                "a sender who can use at least one route must still see the command");
    }

    private Context newContext(JavaPlugin plugin) {
        Context context = mock(Context.class);
        when(context.getPlugin()).thenReturn(new SpigotBootPlugin(plugin));
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

    private CommandDispatcher newDispatcher() {
        DefaultCommandArgumentResolverRegistry resolverRegistry = new DefaultCommandArgumentResolverRegistry(
                Collections.singletonList(new BukkitPlayerArgumentResolver()),
                Collections.emptyList()
        );
        DefaultCommandCompletionRegistry completionRegistry = new DefaultCommandCompletionRegistry(
                Collections.singletonList(new BukkitCommandCompletionRegistryCustomizer())
        );
        return new CommandDispatcher(
                new CommandParameterBinder(resolverRegistry),
                new CommandInvocationExecutor(new CommandInterceptorChain()),
                new CommandMessagesProvider(new DefaultCommandMessages()),
                new CompletionResolver(completionRegistry, resolverRegistry)
        );
    }

    @CommandHandler
    @RootCommand("restricted")
    static class AdminOnlyCommands {
        @Permission("admin.manage")
        @Command("manage")
        public void manage(@Sender Player sender) {
        }
    }

    @CommandHandler
    @RootCommand("network")
    static class GatedNetworkCommand {
        @Permission("network.reload")
        @Command("reload")
        public void reload(@Sender Player sender) {
        }
    }

    @CommandHandler
    @RootCommand("status")
    static class OpenDefaultCommand {
        @DefaultCommand
        public void status(@Sender Player sender) {
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
        public void info(@Sender Player sender) {
        }

        @Permission("monitor.reload")
        @Command("reload")
        public void reload(@Sender Player sender) {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class TeleportCommands {
        private Player lastSender;
        private Player lastTarget;

        @Command("tp <target>")
        public void teleport(@Sender Player sender, Player target) {
            this.lastSender = sender;
            this.lastTarget = target;
        }

        @Command("warp <world>")
        public void warp(@Completion("worlds") String world) {
        }
    }
}

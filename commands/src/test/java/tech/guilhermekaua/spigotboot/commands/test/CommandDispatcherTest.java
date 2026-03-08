package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
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
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.guilhermekaua.spigotboot.commands.test.CommandTestSupport.*;

class CommandDispatcherTest {
    @Test
    void dispatchBindsGreedyStringAndSender() {
        GreedyCommands handler = new GreedyCommands();
        CompiledRootCommand root = compileRoot(handler);
        CommandDispatcher dispatcher = newDispatcher();

        DependencyManager dependencyManager = new DependencyManager();
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        when(context.getPlugin()).thenReturn(testPlugin());

        TestSender sender = testSender("Console");

        dispatcher.dispatch(context, root, senderHandleFor(sender), "admin", new String[]{"say", "hello", "there"});

        assertEquals("hello there", handler.lastMessage);
        assertEquals(sender, handler.lastSender);
        assertEquals("admin", handler.lastLabel);
    }

    @Test
    void dispatchesNestedStaticCommandGroupUsingRootAndRouteAliases() {
        NestedAliasCommands.created = null;
        NestedAliasCommands handler = new NestedAliasCommands();
        CompiledRootCommand root = compileRoot(handler);
        CommandDispatcher dispatcher = newDispatcher();

        DependencyManager dependencyManager = new DependencyManager();
        Context context = newContext(dependencyManager);
        TestSender sender = newSender();

        dispatcher.dispatch(context, root, senderHandleFor(sender), "adm", new String[]{"coins", "Alex", "give", "5"});

        assertNotNull(NestedAliasCommands.created);
        assertEquals("Alex", NestedAliasCommands.created.lastPlayer);
        assertEquals(Integer.valueOf(5), NestedAliasCommands.created.lastAmount);
        assertEquals("adm", NestedAliasCommands.created.lastLabel);
    }

    @Test
    void compilesNestedAndLeafAliasesIntoCartesianRoutes() {
        CompiledRootCommand root = compileRoot(new NestedAliasCommands());

        Set<String> patterns = root.getRoutes().stream()
                .map(route -> route.getPattern().getSource())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Arrays.asList("admin", "adm"), root.getAliases().allValues());
        assertEquals(new LinkedHashSet<>(Arrays.asList(
                "coin <player> set <amount>",
                "coin <player> give <amount>",
                "coins <player> set <amount>",
                "coins <player> give <amount>"
        )), patterns);
    }

    @Test
    void dispatchesNestedDefaultCommandAtExactPrefix() {
        NestedDefaultCommands.CoinCommands.created = null;
        CompiledRootCommand root = compileRoot(new NestedDefaultCommands());
        CommandDispatcher dispatcher = newDispatcher();

        DependencyManager dependencyManager = new DependencyManager();
        Context context = newContext(dependencyManager);
        TestSender sender = newSender();

        dispatcher.dispatch(context, root, senderHandleFor(sender), "admin", new String[]{"coin"});

        assertNotNull(NestedDefaultCommands.CoinCommands.created);
        assertEquals(1, NestedDefaultCommands.CoinCommands.created.defaultCalls);
    }

    @Test
    void dispatchesNonStaticInnerCommandGroup() {
        NonStaticNestedCommands handler = new NonStaticNestedCommands("owner");
        CompiledRootCommand root = compileRoot(handler);
        CommandDispatcher dispatcher = newDispatcher();

        DependencyManager dependencyManager = new DependencyManager();
        Context context = newContext(dependencyManager);
        TestSender sender = newSender();

        dispatcher.dispatch(context, root, senderHandleFor(sender), "admin", new String[]{"profile", "Kai"});

        assertEquals("owner:Kai", handler.lastInvocation);
    }

    @Test
    void dispatchesNestedCommandGroupWithConstructorAndFieldInjection() {
        DependencyManager dependencyManager = new DependencyManager();
        PrefixService prefixService = new PrefixService("pre-");
        SuffixService suffixService = new SuffixService("-post");
        Recorder recorder = new Recorder();
        dependencyManager.registerDependency(prefixService, null, true);
        dependencyManager.registerDependency(suffixService, null, true);
        dependencyManager.registerDependency(recorder, null, true);

        CompiledRootCommand root = compileRoot(new DependencyInjectedNestedCommands(), dependencyManager);
        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        TestSender sender = newSender();

        dispatcher.dispatch(context, root, senderHandleFor(sender), "admin", new String[]{"audit", "Sam"});

        assertEquals("pre-Sam-post", recorder.value);
    }

    private CompiledRootCommand compileRoot(Object handler) {
        return compileRoot(handler, new DependencyManager());
    }

    private CompiledRootCommand compileRoot(Object handler, DependencyManager dependencyManager) {
        CommandReplacementRegistry replacementRegistry = new DefaultCommandReplacementRegistry(Collections.emptyList());
        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                replacementRegistry,
                new CommandInvocationFactory(new CommandParameterRoleResolver(platformSupport()))
        );
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler, dependencyManager);
        CompiledRootCommand root = factory.create(metadata);
        new CommandRouteValidator().validate(Collections.singletonList(root));
        return root;
    }

    private CommandDispatcher newDispatcher() {
        DefaultCommandArgumentResolverRegistry resolverRegistry = new DefaultCommandArgumentResolverRegistry(Collections.emptyList(), Collections.emptyList());
        return new CommandDispatcher(
                new CommandParameterBinder(resolverRegistry),
                new CommandInvocationExecutor(new CommandInterceptorChain()),
                new CommandMessagesProvider(new DefaultCommandMessages()),
                new CompletionResolver(new DefaultCommandCompletionRegistry(Collections.emptyList()), resolverRegistry)
        );
    }

    private Context newContext(DependencyManager dependencyManager) {
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        when(context.getPlugin()).thenReturn(testPlugin());
        return context;
    }

    private TestSender newSender() {
        return testSender("Console");
    }

    @CommandHandler
    @RootCommand("admin")
    static class GreedyCommands {
        private String lastMessage;
        private TestSender lastSender;
        private String lastLabel;

        @Command("say <message>")
        public void say(@Sender TestSender sender, String message, CommandExecutionContext context) {
            this.lastSender = sender;
            this.lastMessage = message;
            this.lastLabel = context.getCommandLabel();
        }
    }

    @CommandHandler
    @RootCommand("admin|adm")
    static class NestedAliasCommands {
        private static CoinCommands created;

        @Command("coin|coins")
        public static class CoinCommands {
            private String lastPlayer;
            private Integer lastAmount;
            private String lastLabel;

            public CoinCommands() {
                created = this;
            }

            @Command(value = "<player> set <amount>", aliases = {"<player> give <amount>"})
            public void update(@Sender TestSender sender, String player, Integer amount, CommandExecutionContext context) {
                this.lastPlayer = player;
                this.lastAmount = amount;
                this.lastLabel = context.getCommandLabel();
            }
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class NestedDefaultCommands {
        @Command("coin")
        public static class CoinCommands {
            private static CoinCommands created;
            private int defaultCalls;

            public CoinCommands() {
                created = this;
            }

            @DefaultCommand
            public void root() {
                defaultCalls++;
            }
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class NonStaticNestedCommands {
        private final String owner;
        private String lastInvocation;

        NonStaticNestedCommands(String owner) {
            this.owner = owner;
        }

        @Command("profile")
        public class ProfileCommands {
            @Command("<player>")
            public void inspect(String player) {
                lastInvocation = owner + ":" + player;
            }
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class DependencyInjectedNestedCommands {
        @Command("audit")
        public static class AuditCommands {
            private final PrefixService prefixService;
            private final Recorder recorder;

            @Inject
            private SuffixService suffixService;

            AuditCommands(PrefixService prefixService, Recorder recorder) {
                this.prefixService = prefixService;
                this.recorder = recorder;
            }

            @Command("<player>")
            public void audit(String player) {
                recorder.value = prefixService.value + player + suffixService.value;
            }
        }
    }

    static class PrefixService {
        private final String value;

        PrefixService(String value) {
            this.value = value;
        }
    }

    static class SuffixService {
        private final String value;

        SuffixService(String value) {
            this.value = value;
        }
    }

    static class Recorder {
        private String value;
    }

}

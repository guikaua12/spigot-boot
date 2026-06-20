package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandMessages;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.Cooldown;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.commands.completion.DefaultCommandCompletionRegistry;
import tech.guilhermekaua.spigotboot.commands.cooldown.CommandCooldownInterceptor;
import tech.guilhermekaua.spigotboot.commands.cooldown.CommandCooldownPolicy;
import tech.guilhermekaua.spigotboot.commands.cooldown.FixedCommandCooldownPolicy;
import tech.guilhermekaua.spigotboot.commands.execution.CommandDispatcher;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationExecutor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.message.DefaultCommandMessages;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.cooldown.CooldownManager;
import tech.guilhermekaua.spigotboot.core.cooldown.DefaultCooldownManager;
import tech.guilhermekaua.spigotboot.core.utils.Timestring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.guilhermekaua.spigotboot.commands.test.CommandTestSupport.*;

class CommandCooldownInterceptorTest {
    @Test
    void secondExecutionFromSameSenderIsBlockedWithDefaultRemainingMessage() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);

        StaticCooldownCommands handler = new StaticCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture sender = consoleSender("Console");

        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"run"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"run"});

        assertEquals(1, handler.calls);
        assertEquals(Collections.singletonList("This command is on cooldown. Wait 30s before using it again."), sender.messages);
    }

    @Test
    void differentSendersDoNotShareCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);

        StaticCooldownCommands handler = new StaticCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture senderA = consoleSender("Console-A");
        SenderFixture senderB = consoleSender("Console-B");

        dispatcher.dispatch(context, root, senderA.senderHandle, "admin", new String[]{"run"});
        dispatcher.dispatch(context, root, senderB.senderHandle, "admin", new String[]{"run"});

        assertEquals(2, handler.calls);
        assertTrue(senderA.messages.isEmpty());
        assertTrue(senderB.messages.isEmpty());
    }

    @Test
    void differentMethodsDoNotShareCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);

        MultiMethodCooldownCommands handler = new MultiMethodCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture sender = consoleSender("Console");

        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"alpha"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"beta"});

        assertEquals(1, handler.alphaCalls);
        assertEquals(1, handler.betaCalls);
        assertTrue(sender.messages.isEmpty());
    }

    @Test
    void typeLevelCooldownAppliesToNestedCommandsAndMethodLevelWins() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);

        ScopedCooldownCommands.CoinCommands.statusCalls = 0;
        ScopedCooldownCommands.CoinCommands.setCalls = 0;
        ScopedCooldownCommands handler = new ScopedCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture sender = consoleSender("Console");

        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"ping"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"ping"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"coin", "status"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"coin", "status"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"coin", "set"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"coin", "set"});

        assertEquals(1, handler.pingCalls);
        assertEquals(1, ScopedCooldownCommands.CoinCommands.statusCalls);
        assertEquals(1, ScopedCooldownCommands.CoinCommands.setCalls);
        assertEquals(
                asList(
                        "This command is on cooldown. Wait 30s before using it again.",
                        "This command is on cooldown. Wait 20s before using it again.",
                        "This command is on cooldown. Wait 10s before using it again."
                ),
                sender.messages
        );
    }

    @Test
    void invalidFixedCooldownFailsValidation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);
        Context context = newContext(dependencyManager);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> compileAndValidateRoot(new InvalidCooldownCommands(), dependencyManager, context)
        );

        assertTrue(exception.getMessage().contains("must resolve to a positive duration"));
    }

    @Test
    void missingPolicyBeanFailsValidation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);
        Context context = newContext(dependencyManager);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> compileAndValidateRoot(new MissingPolicyCommands(), dependencyManager, context)
        );

        assertEquals(
                "No bean was found for command cooldown policy type " + MissingPolicy.class.getName() + ".",
                exception.getMessage()
        );
    }

    @Test
    void customPolicyCanInspectSenderAndShortenVipCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);
        dependencyManager.registerDependency(new VipCooldownPolicy(), null, true);

        PolicyCooldownCommands handler = new PolicyCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture vip = playerSender("Vip", UUID.fromString("11111111-1111-1111-1111-111111111111"), "test.vip");

        dispatcher.dispatch(context, root, vip.senderHandle, "admin", new String[]{"run"});
        dispatcher.dispatch(context, root, vip.senderHandle, "admin", new String[]{"run"});
        clock.advance(Duration.ofSeconds(6));
        dispatcher.dispatch(context, root, vip.senderHandle, "admin", new String[]{"run"});

        assertEquals(2, handler.calls);
        assertEquals(Collections.singletonList("This command is on cooldown. Wait 5s before using it again."), vip.messages);
    }

    @Test
    void customPolicyReturningZeroDisablesCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);
        dependencyManager.registerDependency(new DisabledCooldownPolicy(), null, true);

        DisabledPolicyCommands handler = new DisabledPolicyCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture sender = consoleSender("Console");

        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"run"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"run"});

        assertEquals(2, handler.calls);
        assertTrue(sender.messages.isEmpty());
    }

    @Test
    void bindingFailuresDoNotConsumeCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);

        NumericCooldownCommands handler = new NumericCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture sender = consoleSender("Console");

        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"number", "oops"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"number", "1"});

        assertEquals(1, handler.calls);
        assertEquals(Collections.singletonList("'oops' is not a valid number."), sender.messages);
    }

    @Test
    void handlerExceptionsDoNotConsumeCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);

        ThrowingCooldownCommands handler = new ThrowingCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture sender = consoleSender("Console");

        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"boom"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"boom"});

        assertEquals(2, handler.calls);
        assertEquals(
                asList(
                        "An internal error occurred while executing this command.",
                        "An internal error occurred while executing this command."
                ),
                sender.messages
        );
    }

    @Test
    void failingLaterAfterInterceptorDoesNotConsumeCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);
        dependencyManager.registerDependency(new FailingAfterInterceptor(), null, true);

        StaticCooldownCommands handler = new StaticCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture sender = consoleSender("Console");

        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"run"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"run"});

        assertEquals(2, handler.calls);
        assertEquals(
                asList(
                        "An internal error occurred while executing this command.",
                        "An internal error occurred while executing this command."
                ),
                sender.messages
        );
    }

    @Test
    void customCommandMessagesCanOverrideCooldownMessage() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-08T00:00:00Z"));
        DependencyManager dependencyManager = new DependencyManager();
        CommandMessagesProvider provider = new CommandMessagesProvider(new DefaultCommandMessages());
        registerCooldownInfrastructure(dependencyManager, clock, provider);
        dependencyManager.registerDependency(new CustomCooldownMessages(), null, true);

        StaticCooldownCommands handler = new StaticCooldownCommands();
        Context context = newContext(dependencyManager);
        CommandDispatcher dispatcher = newDispatcher(provider);
        CompiledRootCommand root = compileAndValidateRoot(handler, dependencyManager, context);
        SenderFixture sender = consoleSender("Console");

        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"run"});
        dispatcher.dispatch(context, root, sender.senderHandle, "admin", new String[]{"run"});

        assertEquals(Collections.singletonList("[custom] wait 30000ms"), sender.messages);
    }

    private void registerCooldownInfrastructure(DependencyManager dependencyManager,
                                                Clock clock,
                                                CommandMessagesProvider commandMessagesProvider) {
        CooldownManager cooldownManager = new DefaultCooldownManager(clock);
        dependencyManager.registerDependency(cooldownManager, null, true);
        dependencyManager.registerDependency(new FixedCommandCooldownPolicy(), null, true);
        dependencyManager.registerDependency(new CommandCooldownInterceptor(cooldownManager, commandMessagesProvider), null, true);
    }

    private CompiledRootCommand compileAndValidateRoot(Object handler,
                                                       DependencyManager dependencyManager,
                                                       Context context) {
        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                new DefaultCommandReplacementRegistry(Collections.emptyList()),
                new CommandInvocationFactory(new CommandParameterRoleResolver(platformSupport()))
        );
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler, dependencyManager);
        CompiledRootCommand root = factory.create(metadata);
        new CommandRouteValidator().validate(Collections.singletonList(root));
        new CommandInterceptorChain().validate(context, Collections.singletonList(root));
        return root;
    }

    private CommandDispatcher newDispatcher(CommandMessagesProvider commandMessagesProvider) {
        DefaultCommandArgumentResolverRegistry resolverRegistry =
                new DefaultCommandArgumentResolverRegistry(Collections.emptyList(), Collections.emptyList());
        CommandInterceptorChain chain = new CommandInterceptorChain();
        return new CommandDispatcher(
                new CommandParameterBinder(resolverRegistry),
                new CommandInvocationExecutor(chain),
                commandMessagesProvider,
                new CompletionResolver(new DefaultCommandCompletionRegistry(Collections.emptyList()), resolverRegistry),
                chain
        );
    }

    private Context newContext(DependencyManager dependencyManager) {
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        when(context.getPlugin()).thenReturn(testPlugin());
        return context;
    }

    private SenderFixture consoleSender(String name) {
        return new SenderFixture(testSender(name));
    }

    private SenderFixture playerSender(String name, UUID uniqueId, String vipPermission) {
        return new SenderFixture(testPlayer(name, uniqueId, vipPermission));
    }

    private List<String> asList(String... values) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, values);
        return result;
    }

    @CommandHandler
    @RootCommand("admin")
    static class StaticCooldownCommands {
        private int calls;

        @Cooldown(time = "30s")
        @Command("run")
        public void run() {
            calls++;
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class MultiMethodCooldownCommands {
        private int alphaCalls;
        private int betaCalls;

        @Cooldown(time = "30s")
        @Command("alpha")
        public void alpha() {
            alphaCalls++;
        }

        @Cooldown(time = "30s")
        @Command("beta")
        public void beta() {
            betaCalls++;
        }
    }

    @Cooldown(time = "30s")
    @CommandHandler
    @RootCommand("admin")
    static class ScopedCooldownCommands {
        private int pingCalls;

        @Command("ping")
        public void ping() {
            pingCalls++;
        }

        @Cooldown(time = "20s")
        @Command("coin")
        public static class CoinCommands {
            private static int statusCalls;
            private static int setCalls;

            @Command("status")
            public void status() {
                statusCalls++;
            }

            @Cooldown(time = "10s")
            @Command("set")
            public void set() {
                setCalls++;
            }
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class InvalidCooldownCommands {
        @Cooldown(time = "wat")
        @Command("run")
        public void run() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class MissingPolicyCommands {
        @Cooldown(policy = MissingPolicy.class)
        @Command("run")
        public void run() {
        }
    }

    static class MissingPolicy implements CommandCooldownPolicy {
        @Override
        public Duration resolve(Cooldown annotation,
                                CommandExecutionContext context,
                                CommandInvocationPlan invocation) {
            return Duration.ZERO;
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class PolicyCooldownCommands {
        private int calls;

        @Cooldown(time = "30s", policy = VipCooldownPolicy.class)
        @Command("run")
        public void run() {
            calls++;
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class DisabledPolicyCommands {
        private int calls;

        @Cooldown(policy = DisabledCooldownPolicy.class)
        @Command("run")
        public void run() {
            calls++;
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class NumericCooldownCommands {
        private int calls;

        @Cooldown(time = "30s")
        @Command("number <amount>")
        public void number(Integer amount) {
            calls++;
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class ThrowingCooldownCommands {
        private int calls;

        @Cooldown(time = "30s")
        @Command("boom")
        public void boom() {
            calls++;
            throw new IllegalStateException("boom");
        }
    }

    static class VipCooldownPolicy implements CommandCooldownPolicy {
        @Override
        public Duration resolve(Cooldown annotation,
                                CommandExecutionContext context,
                                CommandInvocationPlan invocation) {
            TestPlayer sender = context.getSender().unwrap(TestPlayer.class).orElse(null);
            if (sender != null && sender.hasPermission("test.vip")) {
                return Duration.ofSeconds(5);
            }

            return Duration.ofMillis(Timestring.durationLong(annotation.time(), "ms"));
        }
    }

    static class DisabledCooldownPolicy implements CommandCooldownPolicy {
        @Override
        public Duration resolve(Cooldown annotation,
                                CommandExecutionContext context,
                                CommandInvocationPlan invocation) {
            return Duration.ZERO;
        }
    }

    static class FailingAfterInterceptor implements tech.guilhermekaua.spigotboot.commands.CommandInterceptor {
        @Override
        public int getOrder() {
            return 100;
        }

        @Override
        public void after(CommandExecutionContext context,
                          CommandInvocationPlan invocation,
                          Object result) {
            throw new IllegalStateException("after-fail");
        }
    }

    static class CustomCooldownMessages implements CommandMessages {
        @Override
        public String missingRequiredArgument(CommandExecutionContext context, CommandParameterMetadata parameter) {
            return "missing";
        }

        @Override
        public String invalidArgumentValue(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            return "invalid";
        }

        @Override
        public String noPermission(CommandExecutionContext context, String permission) {
            return "no-permission";
        }

        @Override
        public String senderTypeMismatch(CommandExecutionContext context, Class<?> expectedSenderType) {
            return "sender-type";
        }

        @Override
        public String unknownSubcommand(CommandExecutionContext context) {
            return "unknown";
        }

        @Override
        public String usage(CommandExecutionContext context, String usage) {
            return "usage";
        }

        @Override
        public String executionError(CommandExecutionContext context, Throwable throwable) {
            return "error";
        }

        @Override
        public String onCooldown(CommandExecutionContext context, Duration remaining) {
            return "[custom] wait " + remaining.toMillis() + "ms";
        }
    }

    static class SenderFixture {
        private final TestSender sender;
        private final TestCommandSenderHandle senderHandle;
        private final List<String> messages;

        private SenderFixture(TestSender sender) {
            this.sender = sender;
            this.senderHandle = new TestCommandSenderHandle(sender);
            this.messages = sender.getMessages();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}

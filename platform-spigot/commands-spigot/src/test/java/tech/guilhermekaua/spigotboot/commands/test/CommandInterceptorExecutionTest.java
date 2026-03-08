package tech.guilhermekaua.spigotboot.commands.test;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.*;
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
import tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.strategy.BeanProxyDeciderResolver;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanInstanceRegistry;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CommandInterceptorExecutionTest {
    @Test
    void globalInterceptorCanStopExecutionBeforeBinding() {
        NumericCommands handler = new NumericCommands();
        DependencyManager dependencyManager = new DependencyManager();
        StopAllInterceptor interceptor = new StopAllInterceptor();
        registerBeans(dependencyManager, interceptor);

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(handler, dependencyManager), sender, "admin", new String[]{"number", "oops"});

        assertEquals(1, interceptor.beforeCalls);
        assertEquals(0, handler.calls);
        verify(sender, never()).sendMessage(anyString());
    }

    @Test
    void annotationInterceptorCanStopDirectCommandExecution() {
        BlockedMethodCommands handler = new BlockedMethodCommands();
        DependencyManager dependencyManager = new DependencyManager();
        registerBeans(dependencyManager, new BlockedInterceptor());

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(handler, dependencyManager), sender, "admin", new String[]{"blocked"});

        assertEquals(0, handler.blockedCalls);
        verify(sender, never()).sendMessage(anyString());
    }

    @Test
    void annotationInterceptorCanStopDefaultAndUnknownHandlers() {
        DefaultUnknownBlockedCommands handler = new DefaultUnknownBlockedCommands();
        DependencyManager dependencyManager = new DependencyManager();
        registerBeans(dependencyManager, new BlockedInterceptor());

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();
        CompiledRootCommand root = compileRoot(handler, dependencyManager);

        dispatcher.dispatch(context, root, sender, "admin", new String[0]);
        dispatcher.dispatch(context, root, sender, "admin", new String[]{"missing"});

        assertEquals(0, handler.defaultCalls);
        assertEquals(0, handler.unknownCalls);
    }

    @Test
    void nearestScopeWinsForTypeAndMethodInterceptorAnnotations() {
        DependencyManager dependencyManager = new DependencyManager();
        CooldownInterceptor interceptor = new CooldownInterceptor();
        registerBeans(dependencyManager, interceptor);

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();
        CompiledRootCommand root = compileRoot(new ScopedCooldownCommands(), dependencyManager);

        dispatcher.dispatch(context, root, sender, "admin", new String[]{"ping"});
        dispatcher.dispatch(context, root, sender, "admin", new String[]{"coin", "status"});
        dispatcher.dispatch(context, root, sender, "admin", new String[]{"coin", "set"});

        assertEquals(Arrays.asList("30s", "20s", "10s"), interceptor.times);
    }

    @Test
    void differentAnnotationInterceptorsComposeOnOneCommand() {
        DependencyManager dependencyManager = new DependencyManager();
        List<String> events = new ArrayList<>();
        registerBeans(
                dependencyManager,
                new AuditAnnotationInterceptor(events),
                new TagAnnotationInterceptor(events)
        );

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(new ComposedAnnotationCommands(), dependencyManager), sender, "admin", new String[]{"run"});

        assertEquals(Arrays.asList("audit.before", "tag.before"), events);
    }

    @Test
    void sameAnnotationInterceptorBeanCanRunForDifferentMatchedAnnotations() {
        DependencyManager dependencyManager = new DependencyManager();
        SharedAnnotationInterceptor interceptor = new SharedAnnotationInterceptor();
        registerBeans(dependencyManager, interceptor);

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(new SharedAnnotationCommands(), dependencyManager), sender, "admin", new String[]{"run"});

        assertEquals(Arrays.asList("SharedAlpha", "SharedBeta"), interceptor.annotationTypes);
    }

    @Test
    void beforeRunsForwardAndAfterRunsInReverseOrder() {
        DependencyManager dependencyManager = new DependencyManager();
        List<String> events = new ArrayList<>();
        registerBeans(
                dependencyManager,
                new OrderedGlobalInterceptor("global-a", 10, events),
                new OrderedGlobalInterceptor("global-b", 20, events),
                new OrderedAnnotationLifecycleInterceptor(events)
        );

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(new LifecycleCommands(), dependencyManager), sender, "admin", new String[]{"ok"});

        assertEquals(
                Arrays.asList(
                        "global-a.before",
                        "global-b.before",
                        "annotation.before",
                        "annotation.after",
                        "global-b.after",
                        "global-a.after"
                ),
                events
        );
    }

    @Test
    void errorsRunInReverseOrderAcrossMergedInterceptorChain() {
        DependencyManager dependencyManager = new DependencyManager();
        List<String> events = new ArrayList<>();
        registerBeans(
                dependencyManager,
                new OrderedGlobalInterceptor("global-a", 10, events),
                new OrderedGlobalInterceptor("global-b", 20, events),
                new OrderedAnnotationLifecycleInterceptor(events)
        );

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(new LifecycleCommands(), dependencyManager), sender, "admin", new String[]{"fail"});

        assertEquals(
                Arrays.asList(
                        "global-a.before",
                        "global-b.before",
                        "annotation.before",
                        "annotation.error",
                        "global-b.error",
                        "global-a.error"
                ),
                events
        );
        verify(sender).sendMessage("An internal error occurred while executing this command.");
    }

    @Test
    void bindingFailuresTriggerOnErrorAndKeepDefaultBindingMessage() {
        DependencyManager dependencyManager = new DependencyManager();
        BindingErrorInterceptor interceptor = new BindingErrorInterceptor();
        registerBeans(dependencyManager, interceptor);

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(new NumericCommands(), dependencyManager), sender, "admin", new String[]{"number", "oops"});

        assertEquals(Arrays.asList("before", "CommandBindingException"), interceptor.events);
        verify(sender).sendMessage("Invalid value 'oops' for argument: amount");
    }

    @Test
    void afterFailuresTriggerOnErrorAndPreserveGenericErrorHandling() {
        DependencyManager dependencyManager = new DependencyManager();
        List<String> events = new ArrayList<>();
        registerBeans(
                dependencyManager,
                new ErrorLoggingInterceptor(events),
                new FailingAfterInterceptor()
        );

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);
        CommandSender sender = newSender();

        dispatcher.dispatch(context, compileRoot(new SimpleOkCommands(), dependencyManager), sender, "admin", new String[]{"ok"});

        assertEquals(Arrays.asList("before", "IllegalStateException"), events);
        verify(sender).sendMessage("An internal error occurred while executing this command.");
    }

    @Test
    void validationFailsWhenAnnotationInterceptorBeanIsMissing() {
        DependencyManager dependencyManager = new DependencyManager();
        CommandInterceptorChain chain = new CommandInterceptorChain();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> chain.validate(newContext(dependencyManager), Collections.singletonList(compileRoot(new MissingInterceptorCommands(), dependencyManager)))
        );

        assertEquals(
                "No bean was found for annotation interceptor type " + MissingAnnotationInterceptor.class.getName() + ".",
                exception.getMessage()
        );
    }

    @Test
    void validationFailsWhenResolvedAnnotationBeanHasWrongRuntimeType() {
        BeanDefinitionRegistry definitionRegistry = new BeanDefinitionRegistry();
        BeanInstanceRegistry instanceRegistry = new BeanInstanceRegistry();
        DependencyManager dependencyManager = new DependencyManager(definitionRegistry, instanceRegistry, new BeanProxyDeciderResolver());

        BeanDefinition definition = new BeanDefinition(
                WrongTypedAnnotationInterceptor.class,
                Object.class,
                "wrongTypedAnnotationInterceptor",
                false,
                null,
                null
        );
        definitionRegistry.register(WrongTypedAnnotationInterceptor.class, definition);
        instanceRegistry.put(definition, new Object());

        CommandInterceptorChain chain = new CommandInterceptorChain();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> chain.validate(newContext(dependencyManager), Collections.singletonList(compileRoot(new WrongTypedInterceptorCommands(), dependencyManager)))
        );

        assertEquals(
                "Resolved bean for annotation interceptor type " + WrongTypedAnnotationInterceptor.class.getName() +
                        " does not implement CommandAnnotationInterceptor.",
                exception.getMessage()
        );
    }

    @Test
    void validationFailsWhenBeanImplementsBothInterceptorInterfaces() {
        DependencyManager dependencyManager = new DependencyManager();
        registerBeans(dependencyManager, new DualInterceptor());
        CommandInterceptorChain chain = new CommandInterceptorChain();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> chain.validate(newContext(dependencyManager), Collections.singletonList(compileRoot(new DualInterceptorCommands(), dependencyManager)))
        );

        assertEquals(
                "Global command interceptor " + DualInterceptor.class.getName() +
                        " cannot implement both CommandInterceptor and CommandAnnotationInterceptor.",
                exception.getMessage()
        );
    }

    private CompiledRootCommand compileRoot(Object handler, DependencyManager dependencyManager) {
        CommandReplacementRegistry replacementRegistry = new DefaultCommandReplacementRegistry(Collections.emptyList());
        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                replacementRegistry,
                new CommandInvocationFactory(new CommandParameterRoleResolver())
        );
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler, dependencyManager);
        CompiledRootCommand root = factory.create(metadata);
        new CommandRouteValidator().validate(Collections.singletonList(root));
        return root;
    }

    private CommandDispatcher newDispatcher() {
        DefaultCommandArgumentResolverRegistry resolverRegistry =
                new DefaultCommandArgumentResolverRegistry(Collections.emptyList(), Collections.emptyList());
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
        when(context.getPlugin()).thenReturn(new TestBootPlugin());
        return context;
    }

    private CommandSender newSender() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        return sender;
    }

    private void registerBeans(DependencyManager dependencyManager, Object... beans) {
        for (int index = 0; index < beans.length; index++) {
            Object bean = beans[index];
            dependencyManager.registerDependency(bean, bean.getClass().getName() + "#" + index, true);
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class NumericCommands {
        private int calls;

        @Command("number <amount>")
        public void number(Integer amount) {
            calls++;
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class BlockedMethodCommands {
        private int blockedCalls;

        @Blocked
        @Command("blocked")
        public void blocked() {
            blockedCalls++;
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class DefaultUnknownBlockedCommands {
        private int defaultCalls;
        private int unknownCalls;

        @Blocked
        @DefaultCommand
        public void root() {
            defaultCalls++;
        }

        @Blocked
        @CatchUnknown
        public void unknown() {
            unknownCalls++;
        }
    }

    @Cooldown("30s")
    @CommandHandler
    @RootCommand("admin")
    static class ScopedCooldownCommands {
        @Command("ping")
        public void ping() {
        }

        @Cooldown("20s")
        @Command("coin")
        public static class CoinCommands {
            @Command("status")
            public void status() {
            }

            @Cooldown("10s")
            @Command("set")
            public void set() {
            }
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class ComposedAnnotationCommands {
        @Audit
        @Tag
        @Command("run")
        public void run() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class SharedAnnotationCommands {
        @SharedAlpha
        @SharedBeta
        @Command("run")
        public void run() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class LifecycleCommands {
        @LifecycleBound
        @Command("ok")
        public void ok() {
        }

        @LifecycleBound
        @Command("fail")
        public void fail() {
            throw new IllegalStateException("boom");
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class SimpleOkCommands {
        @Command("ok")
        public void ok() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class MissingInterceptorCommands {
        @MissingBinding
        @Command("run")
        public void run() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class WrongTypedInterceptorCommands {
        @WrongTypedBinding
        @Command("run")
        public void run() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class DualInterceptorCommands {
        @DualBinding
        @Command("run")
        public void run() {
        }
    }

    static class StopAllInterceptor implements CommandInterceptor {
        private int beforeCalls;

        @Override
        public CommandExecutionDecision before(CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            beforeCalls++;
            return CommandExecutionDecision.stopExecution();
        }
    }

    static class BlockedInterceptor implements CommandAnnotationInterceptor<Blocked> {
        @Override
        public CommandExecutionDecision before(Blocked annotation,
                                               CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            return CommandExecutionDecision.stopExecution();
        }
    }

    static class CooldownInterceptor implements CommandAnnotationInterceptor<Cooldown> {
        private final List<String> times = new ArrayList<>();

        @Override
        public CommandExecutionDecision before(Cooldown annotation,
                                               CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            times.add(annotation.value());
            return CommandExecutionDecision.continueExecution();
        }
    }

    static class AuditAnnotationInterceptor implements CommandAnnotationInterceptor<Audit> {
        private final List<String> events;

        AuditAnnotationInterceptor(List<String> events) {
            this.events = events;
        }

        @Override
        public int getOrder() {
            return 10;
        }

        @Override
        public CommandExecutionDecision before(Audit annotation,
                                               CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            events.add("audit.before");
            return CommandExecutionDecision.continueExecution();
        }
    }

    static class TagAnnotationInterceptor implements CommandAnnotationInterceptor<Tag> {
        private final List<String> events;

        TagAnnotationInterceptor(List<String> events) {
            this.events = events;
        }

        @Override
        public int getOrder() {
            return 20;
        }

        @Override
        public CommandExecutionDecision before(Tag annotation,
                                               CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            events.add("tag.before");
            return CommandExecutionDecision.continueExecution();
        }
    }

    static class SharedAnnotationInterceptor implements CommandAnnotationInterceptor<java.lang.annotation.Annotation> {
        private final List<String> annotationTypes = new ArrayList<>();

        @Override
        public CommandExecutionDecision before(java.lang.annotation.Annotation annotation,
                                               CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            annotationTypes.add(annotation.annotationType().getSimpleName());
            return CommandExecutionDecision.continueExecution();
        }
    }

    static class OrderedGlobalInterceptor implements CommandInterceptor {
        private final String name;
        private final int order;
        private final List<String> events;

        OrderedGlobalInterceptor(String name, int order, List<String> events) {
            this.name = name;
            this.order = order;
            this.events = events;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public CommandExecutionDecision before(CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            events.add(name + ".before");
            return CommandExecutionDecision.continueExecution();
        }

        @Override
        public void after(CommandExecutionContext context,
                          tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation,
                          Object result) {
            events.add(name + ".after");
        }

        @Override
        public void onError(CommandExecutionContext context,
                            tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation,
                            Throwable throwable) {
            events.add(name + ".error");
        }
    }

    static class OrderedAnnotationLifecycleInterceptor implements CommandAnnotationInterceptor<LifecycleBound> {
        private final List<String> events;

        OrderedAnnotationLifecycleInterceptor(List<String> events) {
            this.events = events;
        }

        @Override
        public int getOrder() {
            return 30;
        }

        @Override
        public CommandExecutionDecision before(LifecycleBound annotation,
                                               CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            events.add("annotation.before");
            return CommandExecutionDecision.continueExecution();
        }

        @Override
        public void after(LifecycleBound annotation,
                          CommandExecutionContext context,
                          tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation,
                          Object result) {
            events.add("annotation.after");
        }

        @Override
        public void onError(LifecycleBound annotation,
                            CommandExecutionContext context,
                            tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation,
                            Throwable throwable) {
            events.add("annotation.error");
        }
    }

    static class BindingErrorInterceptor implements CommandInterceptor {
        private final List<String> events = new ArrayList<>();

        @Override
        public CommandExecutionDecision before(CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            events.add("before");
            return CommandExecutionDecision.continueExecution();
        }

        @Override
        public void onError(CommandExecutionContext context,
                            tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation,
                            Throwable throwable) {
            events.add(throwable.getClass().getSimpleName());
        }
    }

    static class ErrorLoggingInterceptor implements CommandInterceptor {
        private final List<String> events;

        ErrorLoggingInterceptor(List<String> events) {
            this.events = events;
        }

        @Override
        public CommandExecutionDecision before(CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            events.add("before");
            return CommandExecutionDecision.continueExecution();
        }

        @Override
        public void onError(CommandExecutionContext context,
                            tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation,
                            Throwable throwable) {
            events.add(throwable.getClass().getSimpleName());
        }
    }

    static class FailingAfterInterceptor implements CommandInterceptor {
        @Override
        public int getOrder() {
            return 100;
        }

        @Override
        public void after(CommandExecutionContext context,
                          tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation,
                          Object result) {
            throw new IllegalStateException("after-fail");
        }
    }

    static class MissingAnnotationInterceptor implements CommandAnnotationInterceptor<MissingBinding> {
    }

    static class WrongTypedAnnotationInterceptor implements CommandAnnotationInterceptor<WrongTypedBinding> {
    }

    static class DualInterceptor implements CommandInterceptor, CommandAnnotationInterceptor<DualBinding> {
        @Override
        public CommandExecutionDecision before(CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            return CommandExecutionDecision.continueExecution();
        }

        @Override
        public CommandExecutionDecision before(DualBinding annotation,
                                               CommandExecutionContext context,
                                               tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan invocation) {
            return CommandExecutionDecision.continueExecution();
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @CommandInterceptedBy(BlockedInterceptor.class)
    @interface Blocked {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @CommandInterceptedBy(CooldownInterceptor.class)
    @interface Cooldown {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CommandInterceptedBy(AuditAnnotationInterceptor.class)
    @interface Audit {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CommandInterceptedBy(TagAnnotationInterceptor.class)
    @interface Tag {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CommandInterceptedBy(SharedAnnotationInterceptor.class)
    @interface SharedAlpha {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CommandInterceptedBy(SharedAnnotationInterceptor.class)
    @interface SharedBeta {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CommandInterceptedBy(OrderedAnnotationLifecycleInterceptor.class)
    @interface LifecycleBound {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CommandInterceptedBy(MissingAnnotationInterceptor.class)
    @interface MissingBinding {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CommandInterceptedBy(WrongTypedAnnotationInterceptor.class)
    @interface WrongTypedBinding {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CommandInterceptedBy(DualInterceptor.class)
    @interface DualBinding {
    }

    static class TestBootPlugin implements BootPlugin {
        @Override
        public String getName() {
            return "TestPlugin";
        }

        @Override
        public Logger getLogger() {
            return Logger.getLogger("TestPlugin");
        }

        @Override
        public File getDataFolder() {
            return new File(".");
        }

        @Override
        public InputStream getResource(String path) {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }

        @Override
        public Class<?> getMainClass() {
            return getClass();
        }

        @Override
        public Object getNativePlugin() {
            return null;
        }
    }
}

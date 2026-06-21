package tech.guilhermekaua.spigotboot.commands.test;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.Name;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.guilhermekaua.spigotboot.commands.test.CommandTestSupport.*;

class CommandMessageDispatchTest {
    enum WidgetKey implements CommandMessageKey {
        BAD;

        @Override public String id() { return "widget.bad"; }
        @Override public String defaultTemplate() { return "No widget '{input}'."; }
        @Override public List<String> placeholders() { return Collections.singletonList("input"); }
    }

    static final class Widget {
    }

    static final class WidgetResolver implements CommandArgumentResolver<Widget> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return Widget.class.equals(parameter.getValueType());
        }

        @Override
        public Widget resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            throw CommandMessageException.of(WidgetKey.BAD).with("input", input);
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class WidgetCommands {
        @Command("get <w>")
        public void get(@Name("w") Widget widget) {
        }
    }

    @Test
    void keyedFailureRendersDefaultWhenNoSource() {
        DependencyManager dependencyManager = new DependencyManager();
        TestSender sender = testSender("Console");

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);

        dispatcher.dispatch(context, compileRoot(new WidgetCommands(), dependencyManager), senderHandleFor(sender), "admin", new String[]{"get", "x"});

        assertEquals(Collections.singletonList("No widget 'x'."), sender.getMessages());
    }

    @Test
    void keyedFailureRendersSourceOverride() {
        DependencyManager dependencyManager = new DependencyManager();
        dependencyManager.registerDependency((CommandMessageSource) (ctx, key) -> "custom {input}", "source", true);
        TestSender sender = testSender("Console");

        CommandDispatcher dispatcher = newDispatcher();
        Context context = newContext(dependencyManager);

        dispatcher.dispatch(context, compileRoot(new WidgetCommands(), dependencyManager), senderHandleFor(sender), "admin", new String[]{"get", "x"});

        assertEquals(Collections.singletonList("custom x"), sender.getMessages());
    }

    private CompiledRootCommand compileRoot(Object handler, DependencyManager dependencyManager) {
        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                new DefaultCommandReplacementRegistry(Collections.emptyList()),
                new CommandInvocationFactory(new CommandParameterRoleResolver(platformSupport()))
        );
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler, dependencyManager);
        CompiledRootCommand root = factory.create(metadata);
        new CommandRouteValidator().validate(Collections.singletonList(root));
        return root;
    }

    private CommandDispatcher newDispatcher() {
        DefaultCommandArgumentResolverRegistry resolverRegistry =
                new DefaultCommandArgumentResolverRegistry(Arrays.asList(new WidgetResolver()), Collections.emptyList());
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
}

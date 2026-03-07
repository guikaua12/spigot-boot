package tech.guilhermekaua.spigotboot.commands.test;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
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
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandDispatcherTest {
    @Test
    void dispatchBindsGreedyStringAndSender() {
        GreedyCommands handler = new GreedyCommands();
        CompiledRootCommand root = compileRoot(handler);
        CommandDispatcher dispatcher = newDispatcher();

        DependencyManager dependencyManager = new DependencyManager();
        Context context = mock(Context.class);
        when(context.getDependencyManager()).thenReturn(dependencyManager);
        when(context.getPlugin()).thenReturn(new TestBootPlugin());

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);

        dispatcher.dispatch(context, root, sender, "admin", new String[]{"say", "hello", "there"});

        assertEquals("hello there", handler.lastMessage);
        assertEquals(sender, handler.lastSender);
        assertEquals("admin", handler.lastLabel);
    }

    private CompiledRootCommand compileRoot(Object handler) {
        CommandReplacementRegistry replacementRegistry = new DefaultCommandReplacementRegistry(Collections.emptyList());
        CommandRouteFactory factory = new CommandRouteFactory(
                new CommandPatternParser(),
                replacementRegistry,
                new CommandInvocationFactory(new CommandParameterRoleResolver())
        );
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler);
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

    @CommandHandler
    @RootCommand("admin")
    static class GreedyCommands {
        private String lastMessage;
        private CommandSender lastSender;
        private String lastLabel;

        @Command("say <message>")
        public void say(@Sender CommandSender sender, String message, CommandExecutionContext context) {
            this.lastSender = sender;
            this.lastMessage = message;
            this.lastLabel = context.getCommandLabel();
        }
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

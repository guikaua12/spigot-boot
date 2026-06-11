package tech.guilhermekaua.spigotboot.commands.test;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.DefaultCommand;
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
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.resolve.DefaultCommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteValidator;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandMapAccessor;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.spigot.BukkitCommandRegistrar;
import tech.guilhermekaua.spigotboot.commands.spigot.RegisteredCommandSet;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.spigot.SpigotBootPlugin;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitCommandRegistrarTest {
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
    void registerAndUnregisterRootCommandAndAlias() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Context context = mock(Context.class);
        when(context.getPlugin()).thenReturn(new SpigotBootPlugin(plugin));
        when(context.getDependencyManager()).thenReturn(new DependencyManager());

        CompiledRootCommand root = compileRoot(new RootCommands());
        CommandDispatcher dispatcher = newDispatcher();
        BukkitCommandMapAccessor accessor = new BukkitCommandMapAccessor();
        BukkitCommandRegistrar registrar = new BukkitCommandRegistrar(accessor, dispatcher, platformSupport);

        RegisteredCommandSet set = registrar.register(context, Collections.singletonList(root));
        CommandMap commandMap = accessor.getCommandMap();
        Map<String, Command> knownCommands = accessor.getKnownCommands(commandMap);

        assertTrue(knownCommands.containsKey("admin"));
        assertTrue(knownCommands.containsKey("adm"));

        registrar.unregister(set);

        assertFalse(knownCommands.containsKey("admin"));
        assertFalse(knownCommands.containsKey("adm"));
    }

    @Test
    void unregisterRemovesCommandsWhenKnownCommandsIteratorDoesNotSupportRemoval() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("TestPlugin");
        Context context = mock(Context.class);
        when(context.getPlugin()).thenReturn(new SpigotBootPlugin(plugin));
        when(context.getDependencyManager()).thenReturn(new DependencyManager());

        CompiledRootCommand root = compileRoot(new RootCommands());
        CommandDispatcher dispatcher = newDispatcher();
        BukkitCommandMapAccessor accessor = new UnsupportedIteratorRemoveAccessor();
        BukkitCommandRegistrar registrar = new BukkitCommandRegistrar(accessor, dispatcher, platformSupport);

        RegisteredCommandSet set = registrar.register(context, Collections.singletonList(root));
        CommandMap commandMap = accessor.getCommandMap();
        Map<String, Command> knownCommands = accessor.getKnownCommands(commandMap);

        assertTrue(knownCommands.containsKey("admin"));
        assertTrue(knownCommands.containsKey("adm"));

        registrar.unregister(set);

        assertFalse(knownCommands.containsKey("admin"));
        assertFalse(knownCommands.containsKey("adm"));
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
        DefaultCommandArgumentResolverRegistry resolverRegistry = new DefaultCommandArgumentResolverRegistry(Collections.emptyList(), Collections.emptyList());
        return new CommandDispatcher(
                new CommandParameterBinder(resolverRegistry),
                new CommandInvocationExecutor(new CommandInterceptorChain()),
                new CommandMessagesProvider(new DefaultCommandMessages()),
                new CompletionResolver(new DefaultCommandCompletionRegistry(Collections.emptyList()), resolverRegistry)
        );
    }

    @CommandHandler
    @RootCommand("admin|adm")
    static class RootCommands {
        @DefaultCommand
        public void root() {
        }
    }

    private static final class UnsupportedIteratorRemoveAccessor extends BukkitCommandMapAccessor {
        private final BukkitCommandMapAccessor delegate = new BukkitCommandMapAccessor();
        private Map<String, Command> knownCommands;

        @Override
        public CommandMap getCommandMap() {
            return delegate.getCommandMap();
        }

        @Override
        public Map<String, Command> getKnownCommands(CommandMap commandMap) {
            if (knownCommands == null) {
                knownCommands = new UnsupportedIteratorRemoveMap(delegate.getKnownCommands(commandMap));
            }

            return knownCommands;
        }
    }

    private static final class UnsupportedIteratorRemoveMap extends AbstractMap<String, Command> {
        private final Map<String, Command> delegate;

        private UnsupportedIteratorRemoveMap(Map<String, Command> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<Map.Entry<String, Command>> entrySet() {
            return new AbstractSet<Map.Entry<String, Command>>() {
                @Override
                public Iterator<Map.Entry<String, Command>> iterator() {
                    Iterator<Map.Entry<String, Command>> iterator = delegate.entrySet().iterator();
                    return new Iterator<Map.Entry<String, Command>>() {
                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public Map.Entry<String, Command> next() {
                            return iterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return delegate.size();
                }
            };
        }

        @Override
        public Command get(Object key) {
            return delegate.get(key);
        }

        @Override
        public Command remove(Object key) {
            return delegate.remove(key);
        }
    }
}

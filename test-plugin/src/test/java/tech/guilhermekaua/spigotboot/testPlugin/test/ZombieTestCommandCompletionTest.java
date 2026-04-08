package tech.guilhermekaua.spigotboot.testPlugin.test;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;
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
import tech.guilhermekaua.spigotboot.commands.spigot.completion.BukkitCommandCompletionRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.spigot.resolve.BukkitPlayerArgumentResolver;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.spigot.SpigotBootPlugin;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.testPlugin.command.ZombieTestCommand;
import tech.guilhermekaua.spigotboot.testPlugin.services.VersionedZombieService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZombieTestCommandCompletionTest {
    private final BukkitCommandPlatformSupport platformSupport = new BukkitCommandPlatformSupport();

    @Test
    void tabComplete_exposesAllCustomEntityBaseTypesForTheDynamicSubcommand() {
        List<String> suggestions = newDispatcher().complete(
                newContext(),
                compileRoot(new ZombieTestCommand(mock(VersionedZombieService.class))),
                new TestSenderHandle(),
                "zombietest",
                new String[]{"spawn-dynamic", ""}
        );

        assertEquals(CustomEntityBaseType.values().length, suggestions.size());
        assertTrue(suggestions.contains("zombie"));
        assertTrue(suggestions.contains("cow"));
        assertTrue(suggestions.contains("zombified_piglin"));
        assertTrue(suggestions.contains("breeze"));
    }

    private Context newContext() {
        JavaPlugin javaPlugin = mock(JavaPlugin.class);
        when(javaPlugin.getName()).thenReturn("TestPlugin");
        when(javaPlugin.getLogger()).thenReturn(Logger.getLogger("ZombieTestCommandCompletionTest"));

        Context context = mock(Context.class);
        when(context.getPlugin()).thenReturn(new SpigotBootPlugin(javaPlugin));
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

    private static final class TestSenderHandle implements CommandSenderHandle {
        @Override
        public String getName() {
            return "Sender";
        }

        @Override
        public String getIdentity() {
            return "sender";
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public <T> Optional<T> unwrap(Class<T> type) {
            return Optional.empty();
        }
    }
}

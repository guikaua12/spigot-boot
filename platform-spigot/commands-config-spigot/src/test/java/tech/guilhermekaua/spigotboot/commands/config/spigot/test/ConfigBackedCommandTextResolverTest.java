package tech.guilhermekaua.spigotboot.commands.config.spigot.test;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;
import tech.guilhermekaua.spigotboot.commands.CommandTextResolverChain;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.Permission;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRoleResolver;
import tech.guilhermekaua.spigotboot.commands.config.spigot.ConfigBackedCommandTextResolver;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandHandlerIntrospector;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.commands.replace.DefaultCommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.route.CommandRouteFactory;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.config.ConfigManager;
import tech.guilhermekaua.spigotboot.config.DefaultConfigManager;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.FolderConfig;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ConfigBackedCommandTextResolverTest {
    @TempDir
    Path tempDir;

    @Mock
    BootPlugin plugin;

    private DefaultConfigManager configManager;
    private CommandRouteFactory factory;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(ConfigBackedCommandTextResolverTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);

        configManager = new DefaultConfigManager(plugin);
        factory = new CommandRouteFactory(
                new CommandPatternParser(),
                new DefaultCommandReplacementRegistry(Collections.emptyList()),
                new CommandTextResolverChain(Collections.singletonList(new ConfigBackedCommandTextResolver())),
                new CommandInvocationFactory(new CommandParameterRoleResolver(new TestPlatformSupport()))
        );
    }

    @Test
    void resolvesConfigReferencesAcrossCommandMetadata() throws Exception {
        writeYaml(Path.of("main.yml"),
                """
                        root: admin
                        root_description: Admin commands
                        root_usage: /admin
                        verb: set
                        command_description: Set coins
                        command_usage: /admin coin set <player>
                        command_permission: plugin.admin.coin.set
                        """);

        configManager.register(MainConfig.class);
        configManager.initializeAll();

        CompiledRootCommand root = compile(new BeanBackedContext().register(configManager), new ConfigBackedCommands());

        assertEquals("admin", root.getAliases().getPrimary());
        assertEquals("Admin commands", root.getDescription());
        assertEquals("/admin", root.getUsage());
        assertEquals("coin set <player>", root.getRoutes().get(0).getPattern().getSource());
        assertEquals("Set coins", root.getRoutes().get(0).getDescription());
        assertEquals("/admin coin set <player>", root.getRoutes().get(0).getUsage());
        assertEquals("plugin.admin.coin.set", root.getRoutes().get(0).getPermission());
    }

    @Test
    void resolvesFolderConfigItemReferencesInsideCommandText() throws Exception {
        Files.createDirectories(tempDir.resolve("items"));
        writeYaml(Path.of("items").resolve("sword.yml"), "name: sword\n");

        configManager.registerFolderConfig(ItemConfig.class, ItemConfig.class.getAnnotation(FolderConfig.class));
        configManager.initializeAll();

        CompiledRootCommand root = compile(new BeanBackedContext().register(configManager), new FolderConfigCommands());

        assertEquals("inspect sword", root.getRoutes().get(0).getPattern().getSource());
    }

    @Test
    void deepResolvesNestedConfigReferencesBeforeStringifying() throws Exception {
        writeYaml(Path.of("main.yml"), "root: admin\nverb: ${shared:action}\ncommand_permission: plugin.admin.coin.set\n");
        writeYaml(Path.of("shared.yml"), "action: set\n");

        configManager.register(MainConfig.class);
        configManager.register(SharedConfig.class);
        configManager.initializeAll();

        CompiledRootCommand root = compile(new BeanBackedContext().register(configManager), new NestedReferenceCommands());

        assertEquals("coin set <player>", root.getRoutes().get(0).getPattern().getSource());
    }

    @Test
    void failsWhenConfigManagerIsMissing() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> compile(new BeanBackedContext(), new ConfigBackedCommands())
        );

        assertTrue(exception.getMessage().contains("no ConfigManager bean is available"));
        assertTrue(exception.getMessage().contains("${main:root}"));
    }

    @Test
    void failsWhenReferenceIsMissing() throws Exception {
        writeYaml(Path.of("main.yml"), "root: admin\n");

        configManager.register(MainConfig.class);
        configManager.initializeAll();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> compile(new BeanBackedContext().register(configManager), new ConfigBackedCommands())
        );

        assertTrue(exception.getMessage().contains("reference not found"));
        assertTrue(exception.getMessage().contains("${main:verb}"));
    }

    @Test
    void failsWhenReferenceResolvesToNonScalarValue() throws Exception {
        writeYaml(Path.of("main.yml"),
                """
                        root: admin
                        command_permission:
                          nested: value
                        """);

        configManager.register(NonScalarConfig.class);
        configManager.initializeAll();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> compile(new BeanBackedContext().register(configManager), new NonScalarCommands())
        );

        assertTrue(exception.getMessage().contains("resolved to a map value"));
        assertTrue(exception.getMessage().contains("${main:command_permission}"));
    }

    @Test
    void failsWhenTokenIsInvalid() throws Exception {
        writeYaml(Path.of("main.yml"), "root: admin\n");

        configManager.register(MainConfig.class);
        configManager.initializeAll();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> compile(new BeanBackedContext().register(configManager), new InvalidTokenCommands())
        );

        assertTrue(exception.getMessage().contains("unclosed config reference token"));
        assertTrue(exception.getMessage().contains("${main:verb <player>"));
    }

    private CompiledRootCommand compile(Context context, Object handler) {
        RootCommandMetadata metadata = new CommandHandlerIntrospector().introspect(handler);
        return factory.create(context, metadata);
    }

    private void writeYaml(Path relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath).normalize();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, content);
    }

    private static final class TestPlatformSupport implements CommandPlatformSupport {
        @Override
        public CommandSenderHandle createSender(Object nativeSender) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSenderType(Class<?> type) {
            return false;
        }
    }

    private static final class BeanBackedContext implements Context {
        private final Map<Class<?>, Object> beans = new HashMap<>();

        private BeanBackedContext register(Object bean) {
            beans.put(ConfigManager.class, bean);
            return this;
        }

        @Override
        public void initialize() {
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public <T> T getBean(@NotNull Class<T> type) {
            return getBean(type, null);
        }

        @Override
        public <T> T getBean(@NotNull Class<T> type, String name) {
            Object bean = beans.get(type);
            if (bean == null) {
                return null;
            }
            return type.cast(bean);
        }

        @Override
        public void registerBean(@NotNull Object instance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerBean(@NotNull Class<?> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> @NotNull List<T> getBeansByType(@NotNull Class<T> type) {
            return Collections.emptyList();
        }

        @Override
        public @NotNull DependencyManager getDependencyManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroy() {
        }

        @Override
        public @NotNull List<Class<? extends Module>> getModulesToLoad() {
            return Collections.emptyList();
        }

        @Override
        public void setModulesToLoad(@NotNull List<Class<? extends Module>> modulesToLoad) {
        }

        @Override
        public BootPlugin getPlugin() {
            return new BootPlugin() {
                @Override
                public String getName() {
                    return "test";
                }

                @Override
                public Logger getLogger() {
                    return Logger.getLogger("test");
                }

                @Override
                public File getDataFolder() {
                    return null;
                }

                @Override
                public InputStream getResource(String path) {
                    return null;
                }

                @Override
                public ClassLoader getClassLoader() {
                    return BeanBackedContext.class.getClassLoader();
                }

                @Override
                public Class<?> getMainClass() {
                    return BeanBackedContext.class;
                }

                @Override
                public Object getNativePlugin() {
                    return null;
                }
            };
        }

        @Override
        public void registerShutdownHook(@NotNull Runnable runnable) {
        }

        @Override
        public void unregisterShutdownHook(@NotNull Runnable runnable) {
        }
    }

    @Config(value = "main.yml", name = "main", generateDefaults = false)
    public static class MainConfig {
        private String root;
        private String rootDescription;
        private String rootUsage;
        private String verb;
        private String commandDescription;
        private String commandUsage;
        private String commandPermission;

        public MainConfig() {
        }
    }

    @Config(value = "main.yml", name = "main", generateDefaults = false)
    public static class NonScalarConfig {
        private String root;
        private PermissionNode commandPermission;

        public NonScalarConfig() {
        }
    }

    public static class PermissionNode {
        private String nested;

        public PermissionNode() {
        }
    }

    @Config(value = "shared.yml", name = "shared", generateDefaults = false)
    public static class SharedConfig {
        private String action;

        public SharedConfig() {
        }
    }

    @FolderConfig(name = "items", folder = "items")
    public static class ItemConfig {
        private String name;

        public ItemConfig() {
        }
    }

    @CommandHandler
    @RootCommand(value = "${main:root}", description = "${main:root_description}", usage = "${main:root_usage}")
    static class ConfigBackedCommands {
        @Command(value = "coin ${main:verb} <player>", description = "${main:command_description}", usage = "${main:command_usage}")
        @Permission("${main:command_permission}")
        public void coin(String player) {
        }
    }

    @CommandHandler
    @RootCommand("${main:root}")
    static class NestedReferenceCommands {
        @Command("coin ${main:verb} <player>")
        @Permission("${main:command_permission}")
        public void coin(String player) {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class FolderConfigCommands {
        @Command("inspect ${items.sword:name}")
        public void inspect() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class NonScalarCommands {
        @Command("coin")
        @Permission("${main:command_permission}")
        public void coin() {
        }
    }

    @CommandHandler
    @RootCommand("admin")
    static class InvalidTokenCommands {
        @Command("coin ${main:verb <player>")
        public void coin(String player) {
        }
    }
}

package tech.guilhermekaua.spigotboot.core.test.context.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.PluginContext;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjectorRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.BeanLifecycleInvoker;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.ContextPhase;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.ContextLifecycle;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.core.context.registration.DefaultBeanRegistrar;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.core.test.context.lifecycle.disablefailure.DisableFailurePluginMain;
import tech.guilhermekaua.spigotboot.core.test.context.lifecycle.enablefailure.EnableFailurePluginMain;
import tech.guilhermekaua.spigotboot.core.test.context.lifecycle.happy.HappyPluginMain;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginContextLifecycleIntegrationTest {

    @AfterEach
    void resetFixtures() {
        HappyPluginMain.reset();
        EnableFailurePluginMain.reset();
        DisableFailurePluginMain.reset();
    }

    @Test
    void lifecycleCallbacks_coverScannedConfigurationBeanProductsAndManualRegistrations() {
        HappyPluginMain.reset();

        PluginContext context = startContext(
                HappyPluginMain.class,
                new TestPluginLogger("happy-lifecycle"),
                HappyPluginMain.HappyLifecycleModule.class
        );

        List<String> startupEvents = HappyPluginMain.snapshot();
        assertTrue(startupEvents.contains("scanned:onEnable:lifecycle-message"));
        assertTrue(startupEvents.contains("config:onEnable:7"));
        assertTrue(startupEvents.contains("product:onEnable:lifecycle-message:7"));
        assertTrue(startupEvents.contains("module:onEnable"));
        assertEquals(1, HappyPluginMain.sharedEnableCalls());

        assertOrder(startupEvents, "order:interface", "order:default");
        assertOrder(startupEvents, "order:default", "order:annotation");
        assertOrder(startupEvents, "product:onEnable:lifecycle-message:7", "contextReady");

        context.destroy();

        List<String> destroyEvents = HappyPluginMain.snapshot();
        assertTrue(destroyEvents.contains("scanned:onDisable"));
        assertTrue(destroyEvents.contains("config:onDisable"));
        assertTrue(destroyEvents.contains("product:onDisable"));
        assertTrue(destroyEvents.contains("module:onDisable"));
        assertEquals(1, HappyPluginMain.sharedDisableCalls());

        assertOrder(destroyEvents, "module:onDisable", "shutdownHook");
        assertOrder(destroyEvents, "shutdownHook", "preDestroy");
        assertEquals(ContextPhase.CLEARED, context.getLifecycle().getCurrentPhase());
        assertFalse(context.isInitialized());
    }

    @Test
    void onEnableFailure_abortsInitializationBeforeContextReady() {
        EnableFailurePluginMain.reset();

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> startContext(
                        EnableFailurePluginMain.class,
                        new TestPluginLogger("enable-failure")
                )
        );

        assertTrue(exception.getMessage().contains("Failed to invoke @OnEnable lifecycle method"));
        assertFalse(EnableFailurePluginMain.snapshot().contains("contextReady"));
    }

    @Test
    void onDisableFailure_isLoggedAndCleanupContinues() {
        DisableFailurePluginMain.reset();

        Logger logger = new TestPluginLogger("disable-failure");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        try {
            PluginContext context = startContext(
                    DisableFailurePluginMain.class,
                    logger,
                    DisableFailurePluginMain.DisableFailureModule.class
            );
            context.destroy();

            List<String> events = DisableFailurePluginMain.snapshot();
            assertTrue(events.contains("failingDisable"));
            assertTrue(events.contains("healthyDisable"));
            assertTrue(events.contains("shutdownHook"));
            assertTrue(events.contains("preDestroy"));
            assertOrder(events, "failingDisable", "shutdownHook");
            assertOrder(events, "shutdownHook", "preDestroy");
            assertTrue(handler.messages.stream().anyMatch(message -> message.contains("Error executing @OnDisable callback")));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @SafeVarargs
    private PluginContext startContext(Class<?> mainClass,
                                       Logger logger,
                                       Class<? extends Module>... modules) {
        PluginContext context = new PluginContext(new TestBootPlugin(mainClass, logger));
        DependencyManager dependencyManager = context.getDependencyManager();

        registerCoreBeans(context);
        installBeanRegistrar(context, dependencyManager);

        ComponentRegistry componentRegistry = dependencyManager.resolveDependency(
                ComponentRegistry.class,
                null,
                ComponentRegistry::new
        );
        ConfigurationProcessor configurationProcessor = dependencyManager.resolveDependency(
                ConfigurationProcessor.class,
                null,
                ConfigurationProcessor::new
        );

        String basePackage = mainClass.getPackage().getName();
        componentRegistry.registerComponents(basePackage, dependencyManager);
        configurationProcessor.processFromPackage(basePackage, dependencyManager);

        for (Class<? extends Module> moduleClass : Arrays.asList(modules)) {
            Module module = instantiateModule(moduleClass);
            try {
                module.onInitialize(context);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize module " + moduleClass.getName(), e);
            }
        }

        componentRegistry.resolveAllComponents(dependencyManager);

        Object nativePlugin = context.getPlugin().getNativePlugin();
        dependencyManager.injectDependencies(ProxyUtils.getRealClass(nativePlugin), nativePlugin);

        new BeanLifecycleInvoker(dependencyManager).invokeOnEnable(
                dependencyManager.getBeanInstanceRegistry().asMapView()
        );
        notifyContextReadyListeners(context);
        prepareContextForDestroy(context);
        return context;
    }

    private void registerCoreBeans(PluginContext context) {
        DependencyManager dependencyManager = context.getDependencyManager();
        Logger logger = context.getPlugin().getLogger();

        dependencyManager.registerDependency(logger, null, false);
        dependencyManager.registerDependency(Logger.class, logger, null, false);
        dependencyManager.registerDependency(BootPlugin.class, context.getPlugin(), null, false);
        dependencyManager.registerDependency(context, null, false);
        dependencyManager.registerDependency(dependencyManager, null, false);

        CustomInjectorRegistry customInjectorRegistry = dependencyManager.getCustomInjectorRegistry();
        dependencyManager.registerDependency(CustomInjectorRegistry.class, customInjectorRegistry, null, false);
    }

    private void installBeanRegistrar(PluginContext context, DependencyManager dependencyManager) {
        setField(context, "beanRegistrar", new DefaultBeanRegistrar(dependencyManager, () -> {
        }));
    }

    private void notifyContextReadyListeners(PluginContext context) {
        List<ContextReadyListener> listeners = new ArrayList<>(context.getBeansByType(ContextReadyListener.class));
        listeners.sort(Comparator.comparingInt(listener -> {
            if (listener instanceof Ordered) {
                return ((Ordered) listener).getOrder();
            }
            return 0;
        }));

        for (ContextReadyListener listener : listeners) {
            try {
                listener.onContextReady(context);
            } catch (Exception e) {
                throw new RuntimeException("Failed to notify ContextReadyListener " + listener.getClass().getName(), e);
            }
        }
    }

    private void prepareContextForDestroy(PluginContext context) {
        setField(context, "lifecycle", new ContextLifecycle(context, context.getDependencyManager(), Collections.emptyList()));
        setField(context, "initialized", true);
    }

    private Module instantiateModule(Class<? extends Module> moduleClass) {
        try {
            return moduleClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate module " + moduleClass.getName(), e);
        }
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' on " + target.getClass().getName(), e);
        }
    }

    private void assertOrder(List<String> events, String first, String second) {
        assertTrue(events.indexOf(first) >= 0, "Missing event: " + first);
        assertTrue(events.indexOf(second) >= 0, "Missing event: " + second);
        assertTrue(events.indexOf(first) < events.indexOf(second), first + " should happen before " + second);
    }

    private static final class TestBootPlugin implements BootPlugin {
        private final Class<?> mainClass;
        private final Logger logger;
        private final File dataFolder;

        private TestBootPlugin(Class<?> mainClass, Logger logger) {
            this.mainClass = mainClass;
            this.logger = logger;
            try {
                this.dataFolder = Files.createTempDirectory("spigot-boot-lifecycle-test").toFile();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create temp data folder for test plugin.", e);
            }
        }

        @Override
        public String getName() {
            return mainClass.getSimpleName();
        }

        @Override
        public Logger getLogger() {
            return logger;
        }

        @Override
        public File getDataFolder() {
            return dataFolder;
        }

        @Override
        public InputStream getResource(String path) {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return mainClass.getClassLoader();
        }

        @Override
        public Class<?> getMainClass() {
            return mainClass;
        }

        @Override
        public Object getNativePlugin() {
            return this;
        }
    }

    private static final class TestPluginLogger extends Logger {
        private TestPluginLogger(String name) {
            super(name, null);
        }
    }

    private static final class TestLogHandler extends Handler {
        private final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}

package tech.guilhermekaua.spigotboot.core.context;

import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.processors.preDestroy.ContextPreDestroyProcessor;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleRegistry;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Getter
public class PluginContext implements Context {
    private final DependencyManager dependencyManager = new DependencyManager();
    private final Plugin plugin;
    private final Logger logger;
    private boolean initialized = false;
    private final List<Runnable> shutdownHooks = new ArrayList<>();
    private final List<Class<? extends Module>> modulesToLoad;

    private ComponentRegistry componentRegistry;

    @SafeVarargs
    public PluginContext(Plugin plugin, @NotNull Class<? extends Module>... modulesToLoad) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.modulesToLoad = Collections.unmodifiableList(Arrays.asList(modulesToLoad));
    }

    @Override
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Context is already initialized.");
        }

        registerBean(logger);
        dependencyManager.registerDependency(Plugin.class, plugin, null, false);
        dependencyManager.registerDependency(ProxyUtils.getRealClass(plugin), plugin, null, false);
        dependencyManager.registerDependency(Logger.class, logger, null, false);

        registerBean(dependencyManager);

        scan(SpigotBoot.class.getPackage().getName());
        scan(ProxyUtils.getRealClass(plugin).getPackage().getName());

        initializeModules();

        componentRegistry.resolveAllComponents(dependencyManager);

        dependencyManager.injectDependencies(ProxyUtils.getRealClass(plugin), plugin);

        initialized = true;
    }

    private void initializeModules() {
        dependencyManager
                .resolveDependency(ModuleRegistry.class, null)
                .initializeModules(this, modulesToLoad);
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void registerBean(Object instance) {
        Class<?> clazz = instance.getClass();


        dependencyManager.registerDependency(
                instance,
                BeanUtils.getQualifier(clazz),
                BeanUtils.getIsPrimary(clazz),
                BeanUtils.createDependencyReloadCallback(clazz)
        );
    }

    @Override
    public void registerBean(Class<?> clazz) {
        dependencyManager.registerDependency(
                clazz,
                BeanUtils.getQualifier(clazz),
                BeanUtils.getIsPrimary(clazz),
                null,
                BeanUtils.createDependencyReloadCallback(clazz)
        );
    }

    @Override
    public @NotNull <T> List<T> getBeansByType(@NotNull Class<T> type) {
        return dependencyManager.getInstancesByType(type);
    }

    @Override
    public void scan(String basePackage) {
        componentRegistry = dependencyManager.resolveDependency(ComponentRegistry.class, null, ComponentRegistry::new);
        componentRegistry.registerComponents(basePackage, dependencyManager);

        dependencyManager.resolveDependency(ConfigurationProcessor.class, null, ConfigurationProcessor::new)
                .processFromPackage(basePackage, dependencyManager);

        MethodHandlerRegistry.registerAll(
                dependencyManager.resolveDependency(MethodHandlerProcessor.class, null, MethodHandlerProcessor::new).processFromPackage(
                        basePackage, dependencyManager
                )
        );
    }

    @Override
    public void destroy() {
        if (!initialized) {
            return;
        }

        for (Runnable hook : shutdownHooks) {
            try {
                hook.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        callPreDestroyProcessors();

        shutdownHooks.clear();

        dependencyManager.clear();

        initialized = false;
    }

    private void callPreDestroyProcessors() {
        for (ContextPreDestroyProcessor processor : getBeansByType(ContextPreDestroyProcessor.class)) {
            try {
                processor.onPreDestroy(this);
            } catch (Exception e) {
                logger.severe("Error executing pre-destroy processor: " + processor.getClass().getName());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void registerShutdownHook(Runnable runnable) {
        shutdownHooks.add(runnable);
    }

    @Override
    public void unregisterShutdownHook(Runnable runnable) {
        shutdownHooks.remove(runnable);
    }
}

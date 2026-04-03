package tech.guilhermekaua.spigotboot.core.context;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.BeanLifecycleInvoker;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.ContextLifecycle;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.processors.preDestroy.ContextPreDestroyProcessor;
import tech.guilhermekaua.spigotboot.core.context.registration.BeanRegistrar;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

@Getter
public class PluginContext implements Context {
    private final DependencyManager dependencyManager = new DependencyManager();
    private final BootPlugin plugin;
    private final Logger logger;
    private boolean initialized = false;
    private final List<Runnable> shutdownHooks = new CopyOnWriteArrayList<>();
    private final List<Class<? extends Module>> modulesToLoad = new ArrayList<>();
    private BeanRegistrar beanRegistrar;
    private ContextLifecycle lifecycle;

    public PluginContext(BootPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Context is already initialized.");
        }

        lifecycle = new ContextLifecycle(this, dependencyManager, modulesToLoad);
        beanRegistrar = lifecycle.getBeanRegistrar();
        lifecycle.initialize();
        initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void registerBean(@NotNull Object instance) {
        if (beanRegistrar == null) {
            throw new IllegalStateException("Cannot register beans before context initialization");
        }
        Class<?> clazz = instance.getClass();
        beanRegistrar.registerInstance(
                instance,
                BeanUtils.getQualifier(clazz),
                BeanUtils.getIsPrimary(clazz)
        );
    }

    @Override
    public void registerBean(@NotNull Class<?> clazz) {
        if (beanRegistrar == null) {
            throw new IllegalStateException("Cannot register beans before context initialization");
        }
        beanRegistrar.registerDefinition(
                clazz,
                BeanUtils.getQualifier(clazz),
                BeanUtils.getIsPrimary(clazz)
        );
    }

    @Override
    public @NotNull <T> List<T> getBeansByType(@NotNull Class<T> type) {
        return dependencyManager.getInstancesByType(type);
    }

    @Override
    public void destroy() {
        if (!initialized) {
            return;
        }

        lifecycle.destroy(
                this::callDisableCallbacks,
                this::callShutdownHooks,
                this::callPreDestroyProcessors
        );

        initialized = false;
    }

    @Override
    public @NotNull List<Class<? extends Module>> getModulesToLoad() {
        return Collections.unmodifiableList(modulesToLoad);
    }

    @Override
    public void setModulesToLoad(@NotNull List<Class<? extends Module>> modulesToLoad) {
        if (initialized) {
            throw new IllegalStateException("Cannot change modules after context initialization");
        }

        this.modulesToLoad.clear();
        this.modulesToLoad.addAll(modulesToLoad);
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

    private void callDisableCallbacks() {
        new BeanLifecycleInvoker(dependencyManager)
                .invokeOnDisable(dependencyManager.getBeanInstanceRegistry().asMapView(), logger);
    }

    private void callShutdownHooks() {
        for (Runnable hook : shutdownHooks) {
            try {
                hook.run();
            } catch (Exception e) {
                logger.severe("Error executing shutdown hook.");
                e.printStackTrace();
            }
        }

        shutdownHooks.clear();
    }

    @Override
    public void registerShutdownHook(@NotNull Runnable runnable) {
        shutdownHooks.add(runnable);
    }

    @Override
    public void unregisterShutdownHook(@NotNull Runnable runnable) {
        shutdownHooks.remove(runnable);
    }
}

package tech.guilhermekaua.spigotboot.core.context;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.ModuleRegistry;
import tech.guilhermekaua.spigotboot.core.service.configuration.ServiceProperties;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

@Getter
public class PluginContext implements Context {
    private final DependencyManager dependencyManager = new DependencyManager();
    private final Plugin plugin;
    private final Logger logger;
    private boolean initialized = false;
    private final List<Runnable> shutdownHooks = new ArrayList<>();
    private final Class<? extends Module>[] modulesToLoad;

    private ComponentRegistry componentRegistry;

    @SafeVarargs
    public PluginContext(Plugin plugin, @NotNull Class<? extends Module>... modulesToLoad) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.modulesToLoad = modulesToLoad;
    }

    @Override
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Context is already initialized.");
        }

        registerBean(this);
        dependencyManager.registerDependency(Plugin.class, plugin, null, false);
        dependencyManager.registerDependency(ProxyUtils.getRealClass(plugin), plugin, null, false);

        registerBean(dependencyManager);

        scan(ProxyUtils.getRealClass(plugin).getPackage().getName());

        // TODO: create a callback like BeanDefinitionRegistryPostProcessor instead of registering manually this
        registerBean(ServiceProperties.builder()
                .executorService(Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(plugin.getName() + "-Service-Thread-%d").build()))
                .build());

        initializeModules();

        componentRegistry.resolveAllComponents(dependencyManager);

        dependencyManager.injectDependencies(ProxyUtils.getRealClass(plugin), plugin);

        initialized = true;
    }

    private void initializeModules() {
        dependencyManager
                .resolveDependency(ModuleRegistry.class, null, () -> new ModuleRegistry(modulesToLoad, logger))
                .initializeModules(this);
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
        shutdownHooks.clear();

        dependencyManager.clear();

        initialized = false;
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

package tech.guilhermekaua.spigotboot.core.context;

import lombok.Getter;
import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Getter
public class PluginContext implements Context {
    private final DependencyManager dependencyManager = new DependencyManager();
    private final Plugin plugin;
    private final Logger logger;
    private final GlobalContext globalContext;
    private boolean initialized = false;
    private final List<Runnable> shutdownHooks = new ArrayList<>();

    private ComponentRegistry componentRegistry;

    public PluginContext(Plugin plugin, GlobalContext globalContext) {
        this.plugin = plugin;
        this.globalContext = globalContext;
        this.logger = plugin.getLogger();
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

        globalContext.initializeModules(this);

        componentRegistry.resolveAllComponents(dependencyManager);

        dependencyManager.injectDependencies(plugin);


        initialized = true;
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
        componentRegistry = globalContext.getBean(ComponentRegistry.class);
        componentRegistry.registerComponents(basePackage, dependencyManager);

        globalContext.getBean(ConfigurationProcessor.class)
                .processFromPackage(basePackage, dependencyManager);

        MethodHandlerRegistry.registerAll(
                globalContext.getBean(MethodHandlerProcessor.class).processFromPackage(
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

    public void registerShutdownHook(Runnable runnable) {
        shutdownHooks.add(runnable);
    }

    public void unregisterShutdownHook(Runnable runnable) {
        shutdownHooks.remove(runnable);
    }
}

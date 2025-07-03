package tech.guilhermekaua.spigotboot.core.context;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.module.ModuleRegistry;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;

import java.util.logging.Logger;

@Getter
public class GlobalContext implements Context {
    private final DependencyManager dependencyManager = new DependencyManager();
    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean initialized = false;

    public GlobalContext(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("GlobalContext is already initialized.");
        }

        registerBean(this);
        registerBean(dependencyManager);
        dependencyManager.registerDependency(Logger.class, logger, null, false);

        scan(SpigotBoot.class.getPackage().getName());
        loadModules();

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
        dependencyManager.resolveDependency(ComponentRegistry.class, null, ComponentRegistry::new)
                .registerComponents(basePackage, dependencyManager);

        dependencyManager.resolveDependency(ConfigurationProcessor.class, null, ConfigurationProcessor::new)
                .processFromPackage(basePackage, dependencyManager);

        MethodHandlerRegistry.registerAll(
                dependencyManager.resolveDependency(MethodHandlerProcessor.class, null, MethodHandlerProcessor::new).processFromPackage(
                        basePackage, dependencyManager
                )
        );
    }

    public void loadModules() {
        dependencyManager.resolveDependency(ModuleRegistry.class, null, () -> new ModuleRegistry(logger)).loadModules(this);
    }

    public void initializeModules(PluginContext pluginContext) {
        ModuleRegistry moduleRegistry = dependencyManager.resolveDependency(ModuleRegistry.class, null);

        if (moduleRegistry == null) {
            logger.severe("ModuleRegistry is not initialized.");
            return;
        }

        if (moduleRegistry.getLoadedModules().isEmpty()) {
            logger.warning("No modules loaded. Please ensure that modules are properly registered.");
            return;
        }

        moduleRegistry.initializeModules(pluginContext);
    }

    @Override
    public void destroy() {
        if (!initialized) {
            return;
        }

        dependencyManager.clear();

        initialized = false;
    }
}

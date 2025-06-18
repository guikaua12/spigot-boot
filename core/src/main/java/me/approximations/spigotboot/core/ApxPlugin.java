package me.approximations.spigotboot.core;

import com.google.common.base.Stopwatch;
import com.google.common.reflect.ClassPath;
import lombok.Getter;
import me.approximations.spigotboot.core.context.component.ComponentManager;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import me.approximations.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import me.approximations.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import me.approximations.spigotboot.core.di.manager.DependencyManager;
import me.approximations.spigotboot.core.listener.manager.ListenerManager;
import me.approximations.spigotboot.core.module.ModuleManager;
import me.approximations.spigotboot.utils.ProxyUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ApxPlugin extends JavaPlugin {
    private final List<Runnable> disableEntries = new ArrayList<>();

    @Getter
    private static ApxPlugin instance;

    @Getter
    private static ClassPath classPath;
    @Getter
    private DependencyManager dependencyManager;
    @Getter
    private ListenerManager listenerManager;

    @Override
    public void onLoad() {
        instance = this;
        try {
            classPath = ClassPath.from(ProxyUtils.getRealClass(this).getClassLoader());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "An error happened while getting ClassPath.");
            throw new RuntimeException(e);
        }
        this.dependencyManager = new DependencyManager(this);
        onPluginLoad();
    }

    @Override
    public void onEnable() {
        final Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            Logger.getLogger("org.hibernate").setLevel(Level.OFF);
            dependencyManager.registerDependency(Plugin.class, this);
            dependencyManager.registerDependency(JavaPlugin.class, this);
            dependencyManager.registerDependency(ApxPlugin.class, this);
            dependencyManager.registerDependency(ProxyUtils.getRealClass(this), this);
            dependencyManager.registerDependency(dependencyManager);

            new ComponentManager(dependencyManager, this).registerComponents();
            dependencyManager.resolveDependency(ConfigurationProcessor.class).processFromPackage(
                    ApxPlugin.class,
                    ProxyUtils.getRealClass(this)
            );

            MethodHandlerRegistry.registerAll(
                    dependencyManager.resolveDependency(MethodHandlerProcessor.class).processFromPackage(
                            ApxPlugin.class,
                            ProxyUtils.getRealClass(this)
                    )
            );

            this.listenerManager = new ListenerManager(this, dependencyManager);

            dependencyManager.resolveDependency(ModuleManager.class).loadModules();

            dependencyManager.injectDependencies(this);
            onPluginEnable();

            getLogger().info(String.format("Plugin enabled successfully! (%s)", stopwatch.stop()));
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "An error happened while enabling.");

            t.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        } finally {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
            }
        }
    }

    protected void onPluginLoad() {
    }

    protected abstract void onPluginEnable();

    @Override
    public void onDisable() {
        for (Runnable runnable : disableEntries) {
            try {
                runnable.run();
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "An error occurred while executing a disable entry.", t);
            }
        }
    }

    public void addDisableEntry(Runnable runnable) {
        disableEntries.add(runnable);
    }
}


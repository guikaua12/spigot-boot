package me.approximations.apxPlugin.core;

import com.google.common.base.Stopwatch;
import com.google.common.reflect.ClassPath;
import lombok.Getter;
import me.approximations.apxPlugin.core.context.component.ComponentManager;
import me.approximations.apxPlugin.core.context.configuration.processor.ConfigurationProcessor;
import me.approximations.apxPlugin.core.di.manager.DependencyManager;
import me.approximations.apxPlugin.core.listener.manager.ListenerManager;
import me.approximations.apxPlugin.core.module.ModuleManager;
import me.approximations.apxPlugin.core.utils.ReflectionUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
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

    protected ApxPlugin() {
    }

    protected ApxPlugin(@NotNull JavaPluginLoader loader, @NotNull PluginDescriptionFile description, @NotNull File dataFolder, @NotNull File file) {
        super(loader, description, dataFolder, file);
    }

    @Override
    public void onLoad() {
        instance = this;
        try {
            classPath = ClassPath.from(ReflectionUtils.getRealPluginClass(this).getClassLoader());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "An error happened while getting ClassPath.");
            throw new RuntimeException(e);
        }
        this.dependencyManager = new DependencyManager();
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
            dependencyManager.registerDependency(ReflectionUtils.getRealPluginClass(this), this);
            dependencyManager.registerDependency(dependencyManager);

            new ComponentManager(dependencyManager, this).registerComponents();
            dependencyManager.resolveDependency(ConfigurationProcessor.class)
                    .processFromPackage(ApxPlugin.class, ReflectionUtils.getRealPluginClass(this));

            this.listenerManager = new ListenerManager(this, dependencyManager);

            dependencyManager.resolveDependency(ModuleManager.class).loadModules();


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


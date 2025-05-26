package me.approximations.apxPlugin;

import com.google.common.base.Stopwatch;
import com.google.common.reflect.ClassPath;
import lombok.Getter;
import me.approximations.apxPlugin.context.component.ComponentManager;
import me.approximations.apxPlugin.di.manager.DependencyManager;
import me.approximations.apxPlugin.listener.manager.ListenerManager;
import me.approximations.apxPlugin.module.ModuleManager;
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
            classPath = ClassPath.from(getClass().getClassLoader());
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
            dependencyManager.registerDependency(getClass(), this);
            dependencyManager.registerDependency(dependencyManager);

            new ComponentManager(dependencyManager, this).registerComponents();

            this.listenerManager = new ListenerManager(this, dependencyManager);

            dependencyManager.resolveDependency(ModuleManager.class).loadModules();

            onPluginEnable();

            getLogger().info(String.format("Plugin enabled successfully! (%s)", stopwatch.stop()));
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "An error happened while enabling.");

            t.printStackTrace();
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


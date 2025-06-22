/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.core;

import com.google.common.base.Stopwatch;
import com.google.common.reflect.ClassPath;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.listener.manager.ListenerManager;
import tech.guilhermekaua.spigotboot.core.module.ModuleManager;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

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
            dependencyManager.registerDependency(Plugin.class, this, null, false);
            dependencyManager.registerDependency(JavaPlugin.class, this, null, false);
            dependencyManager.registerDependency(ApxPlugin.class, this, null, false);
            dependencyManager.registerDependency(ProxyUtils.getRealClass(this), this, null, false);
            dependencyManager.registerDependency(dependencyManager, null, false);

            ComponentRegistry componentRegistry = dependencyManager.resolveDependency(ComponentRegistry.class, null, () -> new ComponentRegistry(dependencyManager, this));

            componentRegistry.registerComponents(ApxPlugin.class, ProxyUtils.getRealClass(this));
            dependencyManager.resolveDependency(ModuleManager.class, null).loadModules();

            componentRegistry.resolveAllComponents();
            dependencyManager.resolveDependency(ConfigurationProcessor.class, null).processFromPackage(
                    ApxPlugin.class,
                    ProxyUtils.getRealClass(this)
            );

            MethodHandlerRegistry.registerAll(
                    dependencyManager.resolveDependency(MethodHandlerProcessor.class, null).processFromPackage(
                            ApxPlugin.class,
                            ProxyUtils.getRealClass(this)
                    )
            );

            this.listenerManager = new ListenerManager(this, dependencyManager);


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


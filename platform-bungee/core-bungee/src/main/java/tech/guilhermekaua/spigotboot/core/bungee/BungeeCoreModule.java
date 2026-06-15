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
package tech.guilhermekaua.spigotboot.core.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

/**
 * Registers the BungeeCord plugin and its {@link TaskScheduler} as injectable beans. Mirrors the
 * Spigot {@code SpigotCoreModule}. There is no scheduler abstraction on a proxy: authors inject the
 * native {@link TaskScheduler} directly.
 */
@Order(-1000)
public class BungeeCoreModule implements Module {

    @Override
    public void onInitialize(Context context) throws Exception {
        BootPlugin bootPlugin = context.getPlugin();
        Object nativePlugin = bootPlugin.getNativePlugin();
        DependencyManager dependencyManager = context.getDependencyManager();

        Plugin plugin = (Plugin) nativePlugin;

        dependencyManager.registerDependency(Plugin.class, plugin, null, false);
        dependencyManager.registerDependency(ProxyUtils.getRealClass(nativePlugin), nativePlugin, null, false);
        dependencyManager.registerDependency(TaskScheduler.class, plugin.getProxy().getScheduler(), null, false);
    }
}

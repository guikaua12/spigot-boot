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

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeCoreModuleTest {

    static class TestPlugin extends Plugin {
    }

    @Test
    void registersNativePluginAndScheduler() throws Exception {
        TestPlugin plugin = mock(TestPlugin.class);
        ProxyServer proxy = mock(ProxyServer.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getScheduler()).thenReturn(scheduler);

        BootPlugin bootPlugin = mock(BootPlugin.class);
        when(bootPlugin.getNativePlugin()).thenReturn(plugin);

        DependencyManager dependencyManager = mock(DependencyManager.class);
        Context context = mock(Context.class);
        when(context.getPlugin()).thenReturn(bootPlugin);
        when(context.getDependencyManager()).thenReturn(dependencyManager);

        new BungeeCoreModule().onInitialize(context);

        verify(dependencyManager).registerDependency(eq(Plugin.class), same(plugin), isNull(), eq(false));
        verify(dependencyManager).registerDependency(eq(TestPlugin.class), same(plugin), isNull(), eq(false));
        verify(dependencyManager).registerDependency(eq(TaskScheduler.class), same(scheduler), isNull(), eq(false));
    }
}

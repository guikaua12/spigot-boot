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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitSettleDispatcherTest {

    private static PageRequest requestWith(Plugin plugin) {
        return new PageRequest(1, 3, 0, null, plugin);
    }

    @Test
    void dispatch_onPrimaryThread_runsInline() {
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        AtomicBoolean ran = new AtomicBoolean();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            dispatcher.dispatch(requestWith(mock(Plugin.class)), () -> ran.set(true));

            bukkit.verify(Bukkit::getScheduler, never());
        }

        assertTrue(ran.get(), "a settle completing on the main thread must run inline");
    }

    @Test
    void dispatch_nullPluginOffThread_runsInline() {
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        AtomicBoolean ran = new AtomicBoolean();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

            dispatcher.dispatch(requestWith(null), () -> ran.set(true));

            bukkit.verify(Bukkit::getScheduler, never());
        }

        assertTrue(ran.get(), "engine-external requests without a plugin must settle on the calling thread");
    }

    @Test
    void dispatch_offMainWithPlugin_schedulesOntoMainThread() {
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        AtomicBoolean ran = new AtomicBoolean();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            dispatcher.dispatch(requestWith(plugin), () -> ran.set(true));

            assertFalse(ran.get(), "the settle must not run inline on the completing thread");
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), task.capture());
            task.getValue().run();
        }

        assertTrue(ran.get(), "the scheduled task must carry the settle onto the main thread");
    }

    @Test
    void dispatch_whilePluginDisabling_dropsSettleInsteadOfThrowing() {
        // the real scheduler rejects tasks registered by a disabling plugin with
        // IllegalPluginAccessException; the dispatcher must swallow it (a throw inside
        // whenComplete or a timeout task would vanish into an unobserved future) and the
        // settle is dropped
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        AtomicBoolean ran = new AtomicBoolean();

        when(scheduler.runTask(any(Plugin.class), any(Runnable.class)))
                .thenThrow(new IllegalPluginAccessException("Plugin attempted to register task while disabled"));

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            assertDoesNotThrow(() -> dispatcher.dispatch(requestWith(plugin), () -> ran.set(true)),
                    "a disabling plugin must not blow up the completing thread");
        }

        assertFalse(ran.get(), "the scheduler rejected the task; the settle is dropped");
    }
}

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

import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformTask;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitSettleDispatcherTest {

    // request with a viewer
    private static PageRequest requestWithViewer(Player viewer) {
        return new PageRequest(1, 3, 0, null, null, viewer);
    }

    // request without a viewer (engine-external test usage)
    private static PageRequest requestNullViewer() {
        return new PageRequest(1, 3, 0, null, null, null);
    }

    @Test
    void dispatch_nullViewer_runsInline() {
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher(scheduler);
        AtomicBoolean ran = new AtomicBoolean();

        dispatcher.dispatch(requestNullViewer(), () -> ran.set(true));

        assertTrue(ran.get(), "a request with no viewer must settle inline on the calling thread");
        verify(scheduler, never()).ownsRegion(any(Player.class));
        verify(scheduler, never()).runOnEntity(any(), any(), any());
    }

    @Test
    void dispatch_ownsRegion_runsInline() {
        Player viewer = mock(Player.class);
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(scheduler.ownsRegion(viewer)).thenReturn(true);

        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher(scheduler);
        AtomicBoolean ran = new AtomicBoolean();

        dispatcher.dispatch(requestWithViewer(viewer), () -> ran.set(true));

        assertTrue(ran.get(), "a settle on the owning region must run inline");
        verify(scheduler, never()).runOnEntity(any(), any(), any());
    }

    @Test
    void dispatch_offRegion_schedulesOnViewerEntity() {
        Player viewer = mock(Player.class);
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(scheduler.ownsRegion(viewer)).thenReturn(false);
        when(scheduler.runOnEntity(eq(viewer), any(), isNull())).thenReturn(mock(PlatformTask.class));

        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher(scheduler);
        AtomicBoolean ran = new AtomicBoolean();

        dispatcher.dispatch(requestWithViewer(viewer), () -> ran.set(true));

        assertFalse(ran.get(), "the settle must not run inline when the current thread does not own the region");
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runOnEntity(eq(viewer), task.capture(), isNull());
        task.getValue().run();
        assertTrue(ran.get(), "the scheduled task must carry the settle onto the region thread");
    }

    @Test
    void dispatch_whilePluginDisabling_dropsSettleInsteadOfThrowing() {
        Player viewer = mock(Player.class);
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(scheduler.ownsRegion(viewer)).thenReturn(false);
        when(scheduler.runOnEntity(any(), any(), any()))
                .thenThrow(new IllegalPluginAccessException("Plugin attempted to register task while disabled"));

        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher(scheduler);
        AtomicBoolean ran = new AtomicBoolean();

        assertDoesNotThrow(() -> dispatcher.dispatch(requestWithViewer(viewer), () -> ran.set(true)),
                "a disabling plugin must not blow up the completing thread");
        assertFalse(ran.get(), "the scheduler rejected the task; the settle is dropped");
    }
}

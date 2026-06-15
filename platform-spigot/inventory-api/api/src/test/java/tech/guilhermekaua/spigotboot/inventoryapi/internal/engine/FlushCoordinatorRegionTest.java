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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformTask;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Region-correctness of the shared-state flush fan-out. An off-region shared write must repaint
 * each open session of the owning view on that session's own region — one {@code runOnEntity(viewer)}
 * dispatch per active session — rather than a single global hop that touches every viewer from one
 * thread. Drives a real {@link ViewEngine} with a recording {@link PlatformScheduler} stand-in for
 * Folia, where no single thread owns more than one viewer's region.
 */
class FlushCoordinatorRegionTest {

    private ServerMock server;
    private MockPlugin plugin;
    private ViewService service;
    private SessionRegistry sessions;
    private RecordingScheduler scheduler;
    private SharedFanOutView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        view = new SharedFanOutView();
        views.register(view);
        scheduler = new RecordingScheduler();
        ViewEngine engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        }, scheduler);
        service = new ViewService(engine, sessions);
    }

    @AfterEach
    void tearDown() {
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    @Test
    void offRegionSharedWrite_dispatchesOneRunOnEntityPerActiveSession() {
        PlayerMock a = server.addPlayer("a");
        PlayerMock b = server.addPlayer("b");
        service.open(a, SharedFanOutView.class, ViewArguments.empty());
        service.open(b, SharedFanOutView.class, ViewArguments.empty());
        assertEquals(2, view.renders.get(), "first render paints both sessions once");

        // simulate Folia: a write from a thread that owns no viewer's region. the flush-hook
        // coalesces and schedules a global-region drain, which re-dispatches per session.
        scheduler.ownsRegion = false;
        view.shared.set("changed");

        // exactly one global-region drain was scheduled (the coalesced flush entry point)
        assertEquals(1, scheduler.globalDispatches.get(),
                "off-region shared write coalesces into a single global-region drain");

        // the drain fanned the repaint out per session: one runOnEntity per active session,
        // each targeting that session's own viewer (not a single global hop painting both)
        assertEquals(2, scheduler.entityDispatches.size(),
                "each open session must be repainted on its own region");
        assertTrue(scheduler.entityDispatches.contains(a), "session a must be dispatched on a's region");
        assertTrue(scheduler.entityDispatches.contains(b), "session b must be dispatched on b's region");

        // and the per-region repaints actually ran, updating both inventories
        assertEquals(4, view.renders.get(), "both sessions repaint exactly once after the fan-out");
    }

    static final class SharedFanOutView extends View {
        final SharedState<String> shared = sharedState("initial");
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("SharedFanOut").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        renders.incrementAndGet();
                        return new ItemStack(Material.PAPER);
                    })
                    .updateOnStateChange(shared);
        }
    }

    /**
     * Records scheduling calls and runs every task inline so the fan-out completes within the test.
     * {@code ownsRegion} is flipped to {@code false} to drive the off-region dispatch paths.
     */
    private static final class RecordingScheduler implements PlatformScheduler {
        private final AtomicInteger globalDispatches = new AtomicInteger();
        private final List<Entity> entityDispatches = new ArrayList<>();
        private volatile boolean ownsRegion = true;

        @Override
        public @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
            entityDispatches.add(entity);
            task.run();
            return NoopTask.INSTANCE;
        }

        @Override
        public @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
            entityDispatches.add(entity);
            task.run();
            return NoopTask.INSTANCE;
        }

        @Override
        public @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks) {
            return NoopTask.INSTANCE;
        }

        @Override
        public @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task) {
            task.run();
            return NoopTask.INSTANCE;
        }

        @Override
        public @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
            task.run();
            return NoopTask.INSTANCE;
        }

        @Override
        public @NotNull PlatformTask runGlobal(@NotNull Runnable task) {
            globalDispatches.incrementAndGet();
            task.run();
            return NoopTask.INSTANCE;
        }

        @Override
        public @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks) {
            globalDispatches.incrementAndGet();
            task.run();
            return NoopTask.INSTANCE;
        }

        @Override
        public boolean ownsRegion(@NotNull Entity entity) {
            return ownsRegion;
        }

        @Override
        public boolean ownsRegion(@NotNull Location location) {
            return ownsRegion;
        }
    }

    private enum NoopTask implements PlatformTask {
        INSTANCE;

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}

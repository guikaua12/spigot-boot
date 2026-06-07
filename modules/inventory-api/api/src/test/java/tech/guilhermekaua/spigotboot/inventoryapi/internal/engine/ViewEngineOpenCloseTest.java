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
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewEngineOpenCloseTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;
    private SimpleView simpleView;
    private ThrowOnOpenView throwOnOpenView;
    private ThrowOnRenderView throwOnRenderView;

    static final class SimpleView extends View {
        CloseReason lastCloseReason;
        int closeCount;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aSimple").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(0, new ItemStack(Material.STONE));
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
            closeCount++;
        }
    }

    static final class CounterView extends View {
        final MutableState<Integer> counter = mutableState(0);

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Counter").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            counter.update(context, value -> value + 1);
            context.slot(0).item(ctx -> new ItemStack(Material.PAPER, counter.get(ctx)));
        }
    }

    static final class ThrowOnOpenView extends View {
        boolean firstRendered;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ThrowOnOpen").rows(1);
        }

        @Override
        protected void onOpen(@NotNull OpenContext context) {
            throw new IllegalStateException("boom");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            firstRendered = true;
        }
    }

    static final class ThrowOnRenderView extends View {
        CloseReason lastCloseReason;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ThrowOnRender").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            throw new IllegalStateException("boom");
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class ScheduledView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Scheduled").rows(1).scheduleUpdate(5L);
        }
    }

    static final class UnregisteredView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Unregistered").rows(1);
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        simpleView = new SimpleView();
        throwOnOpenView = new ThrowOnOpenView();
        throwOnRenderView = new ThrowOnRenderView();
        views.register(simpleView);
        views.register(new CounterView());
        views.register(throwOnOpenView);
        views.register(throwOnRenderView);
        views.register(new ScheduledView());
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow();
    }

    @Test
    void open_registersPaintsAndActivates() {
        engine.open(player, SimpleView.class, ViewArguments.empty());

        ViewSession session = session();
        assertEquals(ViewSession.Status.ACTIVE, session.status());
        assertSame(simpleView, session.registered().instance());
        Inventory inventory = session.inventory();
        assertNotNull(inventory);
        assertNotNull(inventory.getItem(0));
        assertEquals(Material.STONE, inventory.getItem(0).getType());
        assertSame(inventory, player.getOpenInventory().getTopInventory());
    }

    @Test
    void close_runsOnCloseWithReasonUnregistersAndIsIdempotent() {
        engine.open(player, SimpleView.class, ViewArguments.empty());
        ViewSession session = session();

        engine.close(session, CloseReason.API);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertEquals(CloseReason.API, simpleView.lastCloseReason);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());

        // closing an already closed session is a no-op
        engine.close(session, CloseReason.API);
        assertEquals(1, simpleView.closeCount);
    }

    @Test
    void reopen_startsFromFreshPerSessionState() {
        engine.open(player, CounterView.class, ViewArguments.empty());
        ViewSession first = session();
        assertEquals(1, first.inventory().getItem(0).getAmount());

        engine.close(first, CloseReason.API);
        engine.open(player, CounterView.class, ViewArguments.empty());

        ViewSession second = session();
        assertNotSame(first, second);
        // a leaked store would paint amount 2 here
        assertEquals(1, second.inventory().getItem(0).getAmount());
    }

    @Test
    void open_unregisteredView_throwsUnknownViewException() {
        assertThrows(UnknownViewException.class,
                () -> engine.open(player, UnregisteredView.class, ViewArguments.empty()));

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void onOpenThrows_opensNothing() {
        engine.open(player, ThrowOnOpenView.class, ViewArguments.empty());

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertFalse(throwOnOpenView.firstRendered, "a throwing onOpen must cancel before first render");
    }

    @Test
    void onFirstRenderThrows_abortsWithOpenFailedCloseHookAndRegistersNothing() {
        engine.open(player, ThrowOnRenderView.class, ViewArguments.empty());

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(CloseReason.OPEN_FAILED, throwOnRenderView.lastCloseReason);
    }

    @Test
    void scheduledUpdates_taskStartsOnOpenAndIsCancelledOnClose() {
        engine.open(player, ScheduledView.class, ViewArguments.empty());
        ViewSession session = session();
        BukkitTask task = session.updateTask();
        assertNotNull(task);
        assertFalse(task.isCancelled());

        engine.close(session, CloseReason.API);

        assertTrue(task.isCancelled());
        assertNull(session.updateTask());
    }
}

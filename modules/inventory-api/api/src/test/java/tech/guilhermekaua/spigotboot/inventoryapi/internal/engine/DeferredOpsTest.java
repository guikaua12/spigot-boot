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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.CloseContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeferredOpsTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private PlayerMock player;
    private MainView mainView;
    private OtherView otherView;
    private CloseNavView closeNavView;

    static final class MainView extends View {
        int clickCount;
        int closeCount;
        CloseReason lastCloseReason;
        Runnable directAction;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Main").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .onClick(ctx -> {
                        clickCount++;
                        ctx.close();
                    });
            render.slot(1, new ItemStack(Material.PAPER))
                    .onClick(ctx -> {
                        clickCount++;
                        ctx.openView(OtherView.class);
                    });
            render.slot(2, new ItemStack(Material.ARROW))
                    .closeOnClick();
            render.slot(3, new ItemStack(Material.GOLD_INGOT))
                    .onClick(ctx -> {
                        clickCount++;
                        // two deferred ops queued by a single click
                        ctx.close();
                        ctx.close();
                    });
            render.slot(4, new ItemStack(Material.DIAMOND))
                    .onClick(ctx -> {
                        clickCount++;
                        // direct engine call, bypassing the context deferral paths
                        directAction.run();
                    });
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            closeCount++;
            lastCloseReason = context.reason();
        }
    }

    static final class OtherView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Other").rows(1);
        }
    }

    static final class CloseNavView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CloseNav").rows(1);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            // forbidden navigation: the engine must log SEVERE and drop it
            context.openView(OtherView.class);
        }
    }

    static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        mainView = new MainView();
        otherView = new OtherView();
        closeNavView = new CloseNavView();
        views.register(mainView);
        views.register(otherView);
        views.register(closeNavView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private InventoryClickEvent click(int rawSlot) {
        Inventory top = session().inventory();
        Inventory bottom = player.getInventory();
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(bottom);
        when(invView.getPlayer()).thenReturn(player);
        when(invView.convertSlot(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(invView.getInventory(anyInt())).thenAnswer(inv -> {
            int raw = inv.getArgument(0);
            if (raw < 0) {
                return null;
            }
            return raw < top.getSize() ? top : bottom;
        });
        return new InventoryClickEvent(invView, InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    @Test
    void contextCloseInsideClickHandler_isDeferredToEndOfTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();

        engine.click(session, click(0));

        assertEquals(1, mainView.clickCount, "the handler itself runs synchronously");
        assertEquals(ViewSession.Status.TRANSITIONING, session.status(),
                "close inside click dispatch must defer, not tear down inline");
        assertSame(session, sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new));
        assertSame(session.inventory(), player.getOpenInventory().getTopInventory(),
                "the container stays open until the deferred op runs");

        server.getScheduler().performTicks(1);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void contextOpenViewInsideClickHandler_replacesViewAtEndOfTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession oldSession = session();

        engine.click(oldSession, click(1));

        assertEquals(ViewSession.Status.TRANSITIONING, oldSession.status());
        assertSame(oldSession, sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new),
                "the old session stays registered until the deferred open runs");

        server.getScheduler().performTicks(1);

        assertEquals(ViewSession.Status.CLOSED, oldSession.status());
        assertEquals(CloseReason.REPLACED, mainView.lastCloseReason,
                "the old view observes the replacement through onClose");
        ViewSession current = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        assertNotSame(oldSession, current);
        assertSame(otherView, current.registered().instance());
        assertEquals(ViewSession.Status.ACTIVE, current.status());
        assertSame(current.inventory(), player.getOpenInventory().getTopInventory());
    }

    @Test
    void engineOpenInsideClickHandler_selfDefersToEndOfTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession oldSession = session();
        // simulates ViewService.open: a direct engine call, not a context navigation
        mainView.directAction = () -> engine.open(player, OtherView.class, ViewArguments.empty());

        engine.click(oldSession, click(4));

        assertEquals(1, mainView.clickCount, "the handler itself runs synchronously");
        assertEquals(ViewSession.Status.TRANSITIONING, oldSession.status(),
                "a service-path open inside click dispatch must self-defer, not run inline");
        assertSame(oldSession, sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new),
                "no synchronous open: the clicked session stays registered until end of tick");

        server.getScheduler().performTicks(1);

        assertEquals(ViewSession.Status.CLOSED, oldSession.status());
        assertEquals(CloseReason.REPLACED, mainView.lastCloseReason,
                "the old view observes the deferred replacement through onClose");
        ViewSession current = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        assertNotSame(oldSession, current);
        assertSame(otherView, current.registered().instance());
        assertEquals(ViewSession.Status.ACTIVE, current.status());
        assertSame(current.inventory(), player.getOpenInventory().getTopInventory());
    }

    @Test
    void engineCloseInsideClickHandler_selfDefersToEndOfTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();
        // simulates ViewService.close: a direct engine call, not a context close
        mainView.directAction = () -> engine.close(session, CloseReason.API);

        engine.click(session, click(4));

        assertEquals(1, mainView.clickCount, "the handler itself runs synchronously");
        assertEquals(ViewSession.Status.TRANSITIONING, session.status(),
                "a service-path close inside click dispatch must self-defer, not tear down inline");
        assertSame(session, sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new));

        server.getScheduler().performTicks(1);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void closeOnClickPostAction_isDeferredToEndOfTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();

        engine.click(session, click(2));

        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        assertSame(session, sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new));
        assertSame(session.inventory(), player.getOpenInventory().getTopInventory());

        server.getScheduler().performTicks(1);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void clicksWhileTransitioning_areCancelledAndNotRouted() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();
        engine.click(session, click(0));
        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        assertEquals(1, mainView.clickCount);

        InventoryClickEvent second = click(1);
        engine.click(session, second);

        assertTrue(second.isCancelled(), "clicks during a transition are swallowed");
        assertEquals(1, mainView.clickCount, "no component handler may run while TRANSITIONING");
    }

    @Test
    void openViewInsideOnClose_logsSevereAndIsDropped() {
        engine.open(player, CloseNavView.class, ViewArguments.empty());
        ViewSession session = session();
        Logger logger = Logger.getLogger(CloseContextImpl.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);

        try {
            engine.close(session, CloseReason.API);
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(handler.records.stream().anyMatch(record -> record.getLevel() == Level.SEVERE),
                "openView from onClose must emit a SEVERE log");
        assertEquals(ViewSession.Status.CLOSED, session.status());

        server.getScheduler().performTicks(1);

        assertFalse(sessions.find(player.getUniqueId()).isPresent(),
                "no new session may be created from onClose navigation");
    }

    @Test
    void deferredOps_noOpWhenSessionWasClosedBeforeTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();
        engine.click(session, click(3));
        assertEquals(ViewSession.Status.TRANSITIONING, session.status());

        // manual close races ahead of the scheduled deferred ops
        engine.close(session, CloseReason.API);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertEquals(1, mainView.closeCount);
        assertEquals(CloseReason.API, mainView.lastCloseReason);

        assertDoesNotThrow(() -> server.getScheduler().performTicks(1));

        assertEquals(1, mainView.closeCount, "stale deferred ops must not close the session twice");
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void deferredOpen_noOpWhenSessionWasClosedBeforeTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();
        engine.click(session, click(1));
        assertEquals(ViewSession.Status.TRANSITIONING, session.status());

        // manual close races ahead of the scheduled deferred navigation
        engine.close(session, CloseReason.API);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertEquals(CloseReason.API, mainView.lastCloseReason);

        assertDoesNotThrow(() -> server.getScheduler().performTicks(1));

        assertFalse(sessions.find(player.getUniqueId()).isPresent(),
                "a deferred open queued by a session closed before the tick must not run");
    }
}

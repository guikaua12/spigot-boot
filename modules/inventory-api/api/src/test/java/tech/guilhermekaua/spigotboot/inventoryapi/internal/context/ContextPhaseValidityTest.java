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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextPhaseValidityTest {

    private static final Logger CLOSE_LOGGER = Logger.getLogger(CloseContextImpl.class.getName());

    private ServerMock server;
    private Plugin plugin;
    private TitleUpdater titleUpdater;
    private ViewEngine engine;
    private PlayerMock player;

    static final class ProbeView extends View {
        final MutableState<Integer> counter = mutableState(0);
    }

    static final class TargetView extends View {
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Plugin.class);
        titleUpdater = mock(TitleUpdater.class);
        engine = new ViewEngine(plugin, new ViewRegistry(), new SessionRegistry(),
                new SlotPainter(new NoopPlaceholderApplier()), titleUpdater);
        player = server.addPlayer("tester");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ViewConfig config() {
        return new ViewConfigBuilder().title("Probe").rows(2).build();
    }

    private static ViewConfig layoutConfig() {
        return new ViewConfigBuilder().title("Probe").layout("  AAA    ", "         ").build();
    }

    // builds a session exactly like the Task 10 tests: direct RegisteredView + StateStore wiring
    private ViewSession sessionFor(View view, ViewConfig config) {
        RegisteredView registered = new RegisteredView(view.getClass(), view, config);
        ViewSession session = new ViewSession(player, registered, ViewArguments.empty(),
                new StateStore(view.tokenTable().size()));
        session.effectiveConfig(config);
        session.status(ViewSession.Status.OPENING);
        return session;
    }

    private RenderContextImpl renderContext(ViewConfig config) {
        ViewSession session = sessionFor(new ProbeView(), config);
        session.layout(ResolvedLayout.resolve(config));
        return new RenderContextImpl(session, engine);
    }

    private InventoryClickEvent clickEvent(Inventory top, int rawSlot, ItemStack current) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getPlayer()).thenReturn(player);
        when(view.convertSlot(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(view.getItem(rawSlot)).thenReturn(current);
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    @Test
    void baseContext_exposesSessionAndEngineCollaborators() {
        ProbeView view = new ProbeView();
        ViewConfig config = config();
        ViewSession session = sessionFor(view, config);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertSame(player, context.player());
        assertEquals(player.getUniqueId(), context.playerId());
        assertSame(view, context.view());
        assertSame(config, context.config());
        assertSame(plugin, context.plugin());
        assertSame(session.arguments(), context.arguments());
        assertSame(session, context.session());
    }

    @Test
    void inventory_beforeContainerAndAfterClose_throwsIllegalStateException() {
        ViewSession session = sessionFor(new ProbeView(), config());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertThrows(IllegalStateException.class, context::inventory);

        Inventory inventory = Bukkit.createInventory(null, 18);
        session.inventory(inventory);
        session.status(ViewSession.Status.ACTIVE);
        assertSame(inventory, context.inventory());

        session.status(ViewSession.Status.CLOSED);
        assertThrows(IllegalStateException.class, context::inventory);
    }

    @Test
    void contextActive_trueOnlyDuringOpeningAndActive() {
        ViewSession session = sessionFor(new ProbeView(), config());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        session.status(ViewSession.Status.OPENING);
        assertTrue(context.contextActive());
        assertFalse(context.isActive());

        session.status(ViewSession.Status.ACTIVE);
        assertTrue(context.contextActive());
        assertTrue(context.isActive());

        session.status(ViewSession.Status.TRANSITIONING);
        assertFalse(context.contextActive());

        session.status(ViewSession.Status.CLOSED);
        assertFalse(context.contextActive());
    }

    @Test
    void stateWrite_isAllowedDuringOpening() {
        ProbeView view = new ProbeView();
        ViewSession session = sessionFor(view, config());
        OpenContextImpl context = new OpenContextImpl(session, engine);

        // the session is OPENING; a real MutableState token must accept the write
        view.counter.set(context, 5);

        assertEquals(5, view.counter.get(context));
    }

    @Test
    void stateAccess_afterClosed_throwsStaleContextException() {
        ProbeView view = new ProbeView();
        ViewSession session = sessionFor(view, config());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        session.status(ViewSession.Status.ACTIVE);
        view.counter.set(context, 1);

        session.status(ViewSession.Status.CLOSED);

        assertThrows(StaleContextException.class, () -> view.counter.get(context));
        assertThrows(StaleContextException.class, () -> view.counter.set(context, 2));
    }

    @Test
    void updateTitle_delegatesToTheEngineTitleUpdater() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        context.updateTitle("&aNew Title");

        verify(titleUpdater).update(player, "&aNew Title");
    }

    @Test
    void close_duringClickDispatch_isDeferredAndLeavesActive() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        engine.clickDispatch(true);

        // the skeleton engine close throws UnsupportedOperationException; deferral must not reach it
        assertDoesNotThrow(context::close);

        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        assertEquals(1, session.deferredOps().size());
    }

    @Test
    void openView_duringClickDispatch_isDeferred() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        engine.clickDispatch(true);

        // the skeleton engine open throws UnsupportedOperationException; deferral must not reach it
        assertDoesNotThrow(() -> context.openView(TargetView.class));

        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        assertEquals(1, session.deferredOps().size());
    }

    @Test
    void update_duringClickDispatch_isNotDeferred() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        session.inventory(Bukkit.createInventory(null, 18));
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        engine.clickDispatch(true);

        try {
            context.update();
        } catch (UnsupportedOperationException ignored) {
            // reaching the engine's not-yet-implemented update pass proves the call was not deferred
        }

        assertTrue(session.deferredOps().isEmpty());
        assertEquals(ViewSession.Status.ACTIVE, session.status());
    }

    @Test
    void engineClickDispatchFlag_defaultsFalseAndIsToggleable() {
        assertFalse(engine.isInClickDispatch());
        engine.clickDispatch(true);
        assertTrue(engine.isInClickDispatch());
        engine.clickDispatch(false);
        assertFalse(engine.isInClickDispatch());
    }

    @Test
    void assertMainThread_onMainPasses_offMainThrowsNamingTheOperation() throws Exception {
        assertDoesNotThrow(() -> ViewEngine.assertMainThread("test-op"));

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                ViewEngine.assertMainThread("test-op");
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        thread.start();
        thread.join();

        assertTrue(thrown.get() instanceof IllegalStateException,
                "off-main assertMainThread must throw IllegalStateException, got " + thrown.get());
        assertTrue(thrown.get().getMessage().contains("test-op"));
    }

    @Test
    void openContext_inventoryUpdateAndUpdateTitle_throwNamingTheOpenPhase() {
        ViewSession session = sessionFor(new ProbeView(), config());
        OpenContextImpl context = new OpenContextImpl(session, engine);

        IllegalStateException inventoryError = assertThrows(IllegalStateException.class, context::inventory);
        IllegalStateException updateError = assertThrows(IllegalStateException.class, context::update);
        IllegalStateException titleError = assertThrows(IllegalStateException.class,
                () -> context.updateTitle("title"));

        assertTrue(inventoryError.getMessage().contains("onOpen"));
        assertTrue(updateError.getMessage().contains("onOpen"));
        assertTrue(titleError.getMessage().contains("onOpen"));
        verifyNoInteractions(titleUpdater);
    }

    @Test
    void openContext_recordsOverridesAndCancellation() {
        ViewSession session = sessionFor(new ProbeView(), config());
        OpenContextImpl context = new OpenContextImpl(session, engine);

        assertNull(context.overriddenTitle());
        assertNull(context.overriddenRows());
        assertFalse(context.isOpenCancelled());

        context.overrideTitle("Bank");
        context.overrideRows(3);
        context.cancelOpen();

        assertEquals("Bank", context.overriddenTitle());
        assertEquals(Integer.valueOf(3), context.overriddenRows());
        assertTrue(context.isOpenCancelled());
    }

    @Test
    void updateContext_exposesTrigger() {
        ViewSession session = sessionFor(new ProbeView(), config());
        UpdateContextImpl context = new UpdateContextImpl(session, engine, UpdateTrigger.SCHEDULED);

        assertEquals(UpdateTrigger.SCHEDULED, context.trigger());
    }

    @Test
    void slotClickContext_exposesEventDataAndOwnCancellationFlag() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        Inventory top = Bukkit.createInventory(null, 18);
        session.inventory(top);
        ItemStack current = new ItemStack(Material.STONE);
        InventoryClickEvent event = clickEvent(top, 12, current);

        SlotClickContextImpl context = new SlotClickContextImpl(session, engine, event, false, true);

        assertEquals(12, context.slot());
        assertEquals(ClickType.LEFT, context.clickType());
        assertSame(current, context.item());
        assertSame(event, context.rawEvent());
        assertFalse(context.isPlayerInventory());
        assertTrue(context.isCancelled(), "the flag starts at the pre-cancel decision");

        context.setCancelled(false);
        assertFalse(context.isCancelled());
        // the decision lives on the context until the routing phase applies it to the event
        assertFalse(event.isCancelled());
    }

    @Test
    void slotClickContext_bottomInventoryClick_reportsPlayerInventory() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        Inventory top = Bukkit.createInventory(null, 18);
        session.inventory(top);
        InventoryClickEvent event = clickEvent(top, 20, null);

        SlotClickContextImpl context = new SlotClickContextImpl(session, engine, event, true, false);

        assertTrue(context.isPlayerInventory());
        assertFalse(context.isCancelled());
        context.setCancelled(true);
        assertTrue(context.isCancelled());
    }

    @Test
    void closeContext_updateThrowsAndCloseIsNoOp() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        CloseContextImpl context = new CloseContextImpl(session, engine, CloseReason.API);

        assertEquals(CloseReason.API, context.reason());

        IllegalStateException updateError = assertThrows(IllegalStateException.class, context::update);
        assertTrue(updateError.getMessage().contains("onClose"));

        // the session is already closing; close() must not call the engine (which would throw)
        assertDoesNotThrow(context::close);
    }

    @Test
    void closeContext_openViewIsDroppedWithSevereLogAndNoEngineInteraction() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        CloseContextImpl context = new CloseContextImpl(session, engine, CloseReason.PLAYER);

        CapturingHandler handler = new CapturingHandler();
        CLOSE_LOGGER.addHandler(handler);
        try {
            // the skeleton engine open throws UnsupportedOperationException; a dropped
            // navigation must never reach it
            assertDoesNotThrow(() -> context.openView(TargetView.class));
        } finally {
            CLOSE_LOGGER.removeHandler(handler);
        }

        assertEquals(ViewSession.Status.ACTIVE, session.status(), "a dropped navigation must not defer");
        assertTrue(session.deferredOps().isEmpty());
        assertEquals(1, handler.records.size());
        assertEquals(Level.SEVERE, handler.records.get(0).getLevel());
    }

    @Test
    void renderContext_slotOutOfBounds_throwsViewConfigurationException() {
        RenderContextImpl render = renderContext(config()); // 2 rows -> slots 0-17

        assertThrows(ViewConfigurationException.class, () -> render.slot(-1));
        assertThrows(ViewConfigurationException.class, () -> render.slot(18));
    }

    @Test
    void renderContext_slotDeclarations_materializeIntoSessionComponents() {
        RenderContextImpl render = renderContext(config());
        render.slot(0, new ItemStack(Material.STONE));
        render.slot(2, 3).item(new ItemStack(Material.PAPER)); // 1-based row/column -> raw slot 11

        render.materializeAll();

        ViewSession session = render.session();
        assertNotNull(session.components().componentAt(0));
        assertNotNull(session.components().componentAt(11));
        assertEquals(2, session.components().all().size());
    }

    @Test
    void renderContext_layoutSlot_bindsEverySlotOfTheCharacter() {
        RenderContextImpl render = renderContext(layoutConfig()); // 'A' occupies slots 2, 3, 4
        render.layoutSlot('A', new ItemStack(Material.STONE));

        render.materializeAll();

        ViewSession session = render.session();
        assertNotNull(session.components().componentAt(2));
        assertNotNull(session.components().componentAt(3));
        assertNotNull(session.components().componentAt(4));
        assertNull(session.components().componentAt(1));
    }

    @Test
    void renderContext_layoutSlotUnknownChar_throwsViewConfigurationException() {
        RenderContextImpl render = renderContext(layoutConfig());

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> render.layoutSlot('Z'));

        assertTrue(error.getMessage().contains("'Z'"));
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

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
}

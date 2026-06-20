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
import org.bukkit.event.inventory.InventoryDragEvent;
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
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.BukkitPlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClickRoutingTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private PlayerMock player;
    private PolicyView policyView;
    private ThrowingView throwingView;

    static final class PolicyView extends View {
        int componentClicks;
        int typedRightClicks;
        int untypedClicks;
        int explicitDoubleClicks;
        int hiddenClicks;
        int viewClicks;
        boolean uncancelNext;
        Boolean lastPlayerInventory;
        Boolean lastPreCancelled;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Policy").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .cancelOnClick(false)
                    .onClick(ctx -> componentClicks++);
            render.slot(1, new ItemStack(Material.PAPER))
                    .onClick(ClickType.RIGHT, ctx -> typedRightClicks++)
                    .onClick(ctx -> untypedClicks++);
            render.slot(2, new ItemStack(Material.ARROW))
                    .displayIf(ctx -> false)
                    .cancelOnClick(false)
                    .onClick(ctx -> hiddenClicks++);
            render.slot(3, new ItemStack(Material.PAPER))
                    .onClick(ClickType.DOUBLE_CLICK, ctx -> explicitDoubleClicks++);
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
            lastPlayerInventory = context.isPlayerInventory();
            lastPreCancelled = context.isCancelled();
            if (uncancelNext) {
                context.setCancelled(false);
            }
        }
    }

    static final class PermissiveView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Permissive").rows(1).cancelOnClick(false);
        }
    }

    static final class NoDragView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("NoDrag").rows(1).cancelOnDrag(false);
        }
    }

    static final class ThrowingView extends View {
        int handlerCalls;
        int viewClicks;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Throwing").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .cancelOnClick(false)
                    .closeOnClick()
                    .onClick(ctx -> {
                        handlerCalls++;
                        throw new IllegalStateException("boom");
                    });
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
        }
    }

    static final class ErrorThrowingView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ErrorThrowing").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .cancelOnClick(false)
                    .onClick(ctx -> {
                        throw new AssertionError("boom");
                    });
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        policyView = new PolicyView();
        throwingView = new ThrowingView();
        views.register(policyView);
        views.register(throwingView);
        views.register(new PermissiveView());
        views.register(new NoDragView());
        views.register(new ErrorThrowingView());
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        }, new BukkitPlatformScheduler(plugin));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession open(Class<? extends View> type) {
        engine.open(player, type, ViewArguments.empty());
        return session();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private InventoryView mockView(Inventory top) {
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
        return invView;
    }

    private InventoryClickEvent click(int rawSlot, ClickType clickType, InventoryAction action) {
        return new InventoryClickEvent(mockView(session().inventory()),
                InventoryType.SlotType.CONTAINER, rawSlot, clickType, action);
    }

    private InventoryClickEvent click(int rawSlot) {
        return click(rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private InventoryDragEvent drag(int... rawSlots) {
        Map<Integer, ItemStack> slots = new HashMap<>();
        for (int rawSlot : rawSlots) {
            slots.put(rawSlot, new ItemStack(Material.STONE));
        }
        return new InventoryDragEvent(mockView(session().inventory()), null,
                new ItemStack(Material.STONE), false, slots);
    }

    @Test
    void configCancelFalse_plainTopClickIsNotCancelled() {
        open(PermissiveView.class);
        InventoryClickEvent event = click(4);

        engine.click(session(), event);

        assertFalse(event.isCancelled());
    }

    @Test
    void componentCancelFalse_overridesConfigTrue() {
        ViewSession session = open(PolicyView.class);
        InventoryClickEvent onComponent = click(0);
        engine.click(session, onComponent);
        assertFalse(onComponent.isCancelled());
        assertEquals(1, policyView.componentClicks);

        // a component-less slot of the same view stays config-cancelled
        InventoryClickEvent plain = click(5);
        engine.click(session, plain);
        assertTrue(plain.isCancelled());
    }

    @Test
    void handlerUncancel_overturnsConfigPreCancelOnPlainTopClick() {
        ViewSession session = open(PolicyView.class);
        policyView.uncancelNext = true;
        InventoryClickEvent event = click(5);

        engine.click(session, event);

        assertEquals(Boolean.TRUE, policyView.lastPreCancelled, "the context starts at the config pre-cancel");
        assertFalse(event.isCancelled(), "the handler decision is last-writer-wins for non-floor actions");
    }

    @Test
    void floorActions_stayCancelledDespiteHandlerUncancel() {
        ViewSession session = open(PolicyView.class);
        policyView.uncancelNext = true;

        InventoryClickEvent shiftFromBottom = click(12, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);
        engine.click(session, shiftFromBottom);
        assertTrue(shiftFromBottom.isCancelled());

        InventoryClickEvent collect = click(5, ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR);
        engine.click(session, collect);
        assertTrue(collect.isCancelled());

        InventoryClickEvent hotbarSwap = click(5, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP);
        engine.click(session, hotbarSwap);
        assertTrue(hotbarSwap.isCancelled());

        InventoryClickEvent hotbarReadd = click(5, ClickType.NUMBER_KEY,
                InventoryAction.HOTBAR_MOVE_AND_READD);
        engine.click(session, hotbarReadd);
        assertTrue(hotbarReadd.isCancelled());
    }

    @Test
    void typedHandlerWinsForItsClickType_untypedHandlesOthers() {
        ViewSession session = open(PolicyView.class);

        engine.click(session, click(1, ClickType.RIGHT, InventoryAction.PICKUP_HALF));
        assertEquals(1, policyView.typedRightClicks);
        assertEquals(0, policyView.untypedClicks);

        engine.click(session, click(1, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(1, policyView.typedRightClicks);
        assertEquals(1, policyView.untypedClicks);
    }

    @Test
    void untypedHandler_isNotInvokedForCollectToCursorDoubleClick() {
        // a fast double-tap reaches the server as a left click followed by a synthetic
        // DOUBLE_CLICK / COLLECT_TO_CURSOR; the untyped handler must fire only for the
        // real click, not the synthetic second event (otherwise the action runs twice)
        ViewSession session = open(PolicyView.class);

        InventoryClickEvent doubleClick = click(1, ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR);
        engine.click(session, doubleClick);
        assertEquals(0, policyView.untypedClicks,
                "a collect-to-cursor double-click must not re-trigger the untyped handler");
        assertTrue(doubleClick.isCancelled(), "the double-click stays cancelled by the safety floor");

        engine.click(session, click(1, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(1, policyView.untypedClicks, "a real left click still fires the untyped handler");
    }

    @Test
    void explicitDoubleClickHandler_isStillInvokedForCollectToCursorDoubleClick() {
        // an explicit onClick(DOUBLE_CLICK, ...) opt-in is honored: only the untyped fallback
        // is suppressed for the synthetic event, never a deliberately typed handler
        ViewSession session = open(PolicyView.class);

        engine.click(session, click(3, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR));

        assertEquals(1, policyView.explicitDoubleClicks,
                "an explicit DOUBLE_CLICK handler still fires for the collect-to-cursor event");
    }

    @Test
    void viewOnClick_isNotInvokedForCollectToCursorDoubleClick() {
        // slot 5 is component-less, so the click degrades to the view-level onClick
        ViewSession session = open(PolicyView.class);

        engine.click(session, click(5, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR));
        assertEquals(0, policyView.viewClicks,
                "a collect-to-cursor double-click must not re-trigger the view-level onClick");

        engine.click(session, click(5, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(1, policyView.viewClicks, "a real left click still reaches the view-level onClick");
    }

    @Test
    void hiddenComponent_receivesNoClicks_cancelledPerConfig() {
        ViewSession session = open(PolicyView.class);
        InventoryClickEvent event = click(2);

        engine.click(session, event);

        assertEquals(0, policyView.hiddenClicks, "hidden components get no clicks");
        assertTrue(event.isCancelled(), "config cancel wins; the hidden component's override is ignored");
        assertEquals(1, policyView.viewClicks, "the slot degrades to a component-less click");
    }

    @Test
    void bottomClick_reachesViewOnClickPreCancelledAsPlayerInventory() {
        ViewSession session = open(PolicyView.class);
        InventoryClickEvent event = click(20);

        engine.click(session, event);

        assertEquals(1, policyView.viewClicks);
        assertEquals(0, policyView.componentClicks, "bottom clicks never reach component handlers");
        assertEquals(Boolean.TRUE, policyView.lastPlayerInventory);
        assertEquals(Boolean.TRUE, policyView.lastPreCancelled);
        assertTrue(event.isCancelled());
    }

    @Test
    void throwingHandler_forceCancelsSkipsRestAndKeepsSessionUsable() {
        ViewSession session = open(ThrowingView.class);
        InventoryClickEvent first = click(0);

        engine.click(session, first);

        assertEquals(1, throwingView.handlerCalls);
        assertTrue(first.isCancelled(), "a throwing handler force-cancels despite cancelOnClick(false)");
        assertEquals(0, throwingView.viewClicks, "view-level onClick is skipped after a handler throw");
        assertTrue(session.deferredOps().isEmpty(), "the closeOnClick post-action must not be deferred");
        assertEquals(ViewSession.Status.ACTIVE, session.status());

        InventoryClickEvent second = click(0);
        engine.click(session, second);
        assertEquals(2, throwingView.handlerCalls, "the session stays clickable after a handler throw");
    }

    @Test
    void clickWhileTransitioning_isCancelledWithoutDispatch() {
        ViewSession session = open(PolicyView.class);
        session.status(ViewSession.Status.TRANSITIONING);
        InventoryClickEvent event = click(0);

        engine.click(session, event);

        assertTrue(event.isCancelled());
        assertEquals(0, policyView.componentClicks);
        assertEquals(0, policyView.viewClicks);
    }

    @Test
    void drag_touchingTop_isCancelledByDefault() {
        ViewSession session = open(PolicyView.class);
        InventoryDragEvent event = drag(2, 20);

        engine.drag(session, event);

        assertTrue(event.isCancelled());
    }

    @Test
    void drag_bottomOnly_isUntouched() {
        ViewSession session = open(PolicyView.class);
        InventoryDragEvent event = drag(9, 20);

        engine.drag(session, event);

        assertFalse(event.isCancelled());
    }

    @Test
    void drag_topUntouchedWhenCancelOnDragFalse() {
        ViewSession session = open(NoDragView.class);
        InventoryDragEvent event = drag(2);

        engine.drag(session, event);

        assertFalse(event.isCancelled());
    }

    @Test
    void errorThrowingHandler_eventStaysCancelled_errorPropagates() {
        ViewSession session = open(ErrorThrowingView.class);
        InventoryClickEvent first = click(0);

        assertThrows(AssertionError.class, () -> engine.click(session, first));
        assertTrue(first.isCancelled(), "event must stay cancelled even when an Error escapes");

        // session remains usable for subsequent clicks
        InventoryClickEvent second = click(0);
        assertThrows(AssertionError.class, () -> engine.click(session, second));
        assertEquals(ViewSession.Status.ACTIVE, session.status());
    }
}

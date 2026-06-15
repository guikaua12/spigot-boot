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
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.BukkitPlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.FirstRenderPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaginationClickRoutingTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private PlayerMock player;
    private ElementHandlersView elementHandlersView;
    private CancelOverrideView cancelOverrideView;
    private ThrowingElementView throwingElementView;
    private HiddenElementView hiddenElementView;
    private OverlapView overlapView;
    private ForceUncancelView forceUncancelView;
    private ScrollNavView scrollNavView;

    // two items over three 'O' slots: slots 0-1 hold elements, slot 2 holds the frame fallback
    static final class ElementHandlersView extends View {
        int typedRightClicks;
        int untypedClicks;
        int viewClicks;

        final Pagination<String> pagination = paginate(Arrays.asList("first", "second"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.STONE))
                        .onClick(ClickType.RIGHT, ctx -> typedRightClicks++)
                        .onClick(ctx -> untypedClicks++))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ElementHandlers").layout("OOO      ");
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
        }
    }

    static final class CancelOverrideView extends View {
        int elementClicks;

        final Pagination<String> pagination = paginate(Arrays.asList("only"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.PAPER))
                        .cancelOnClick(false)
                        .onClick(ctx -> elementClicks++))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CancelOverride").layout("O        ");
        }
    }

    // cancelOnClick(false) + explicit setCancelled(false) in the handler: both are overridden by the floor
    static final class ForceUncancelView extends View {
        final Pagination<String> pagination = paginate(Arrays.asList("item"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.STONE))
                        .cancelOnClick(false)
                        .onClick(ctx -> ctx.setCancelled(false)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ForceUncancel").layout("O        ");
        }
    }

    // mirrors SampleScrollView: a layout-slot nav button bound to a watched scroll token,
    // with an untyped onClick that advances the pagination
    static final class ScrollNavView extends View {
        int advanceClicks;

        final Pagination<Integer> numbers = paginate(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
                .scroll()
                .itemRenderer((context, item, index, value) -> item.item(new ItemStack(Material.GOLD_INGOT)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ScrollNav").layout("OOOOO   >");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.layoutSlot('>', new ItemStack(Material.ARROW))
                    .updateOnStateChange(numbers)
                    .onClick(ctx -> {
                        advanceClicks++;
                        numbers.advance(ctx);
                    });
        }
    }

    static final class ThrowingElementView extends View {
        int handlerCalls;
        int viewClicks;

        final Pagination<String> pagination = paginate(Arrays.asList("boom"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.STONE))
                        .cancelOnClick(false)
                        .closeOnClick()
                        .onClick(ctx -> {
                            handlerCalls++;
                            throw new IllegalStateException("boom");
                        }))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ThrowingElement").layout("O        ");
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
        }
    }

    static final class CloseOnClickElementView extends View {
        final Pagination<String> pagination = paginate(Arrays.asList("close"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.ARROW))
                        .closeOnClick())
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CloseOnClickElement").layout("O        ");
        }
    }

    static final class HiddenElementView extends View {
        int hiddenClicks;
        int viewClicks;

        final Pagination<String> pagination = paginate(Arrays.asList("ghost"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.STONE))
                        .displayIf(ctx -> false)
                        .cancelOnClick(false)
                        .onClick(ctx -> hiddenClicks++))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("HiddenElement").layout("O        ");
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
        }
    }

    static final class OverlapView extends View {
        CloseReason lastCloseReason;

        final Pagination<String> pagination = paginate(Arrays.asList("a", "b", "c"))
                .itemRenderer((context, item, index, value) -> item.item(new ItemStack(Material.STONE)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Overlap").layout("OOO      ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            // static component on the same 'O' slots the pagination targets
            context.layoutSlot('O', new ItemStack(Material.PAPER));
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    // 'O' is pagination-bound, 'A' is component-bound, 'X' is bound to nothing
    static final class UnboundCharView extends View {
        final Pagination<String> pagination = paginate(Arrays.asList("page"))
                .itemRenderer((context, item, index, value) -> item.item(new ItemStack(Material.STONE)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("UnboundChar").layout("OAX      ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.layoutSlot('A', new ItemStack(Material.PAPER));
        }
    }

    static final class BoundCharsView extends View {
        final Pagination<String> pagination = paginate(Arrays.asList("page"))
                .itemRenderer((context, item, index, value) -> item.item(new ItemStack(Material.STONE)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("BoundChars").layout("OA       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.layoutSlot('A', new ItemStack(Material.PAPER));
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
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        elementHandlersView = new ElementHandlersView();
        cancelOverrideView = new CancelOverrideView();
        throwingElementView = new ThrowingElementView();
        hiddenElementView = new HiddenElementView();
        overlapView = new OverlapView();
        forceUncancelView = new ForceUncancelView();
        scrollNavView = new ScrollNavView();
        views.register(elementHandlersView);
        views.register(cancelOverrideView);
        views.register(throwingElementView);
        views.register(new CloseOnClickElementView());
        views.register(hiddenElementView);
        views.register(overlapView);
        views.register(forceUncancelView);
        views.register(scrollNavView);
        views.register(new UnboundCharView());
        views.register(new BoundCharsView());
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        }, new BukkitPlatformScheduler(plugin));
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; deferred closes left behind by a failed
        // assertion must not run against a torn-down registry
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
        // the once-per-view-class warning set is static; clear it so warning tests are order-independent
        try {
            java.lang.reflect.Field f = FirstRenderPhase.class.getDeclaredField("UNBOUND_CHAR_WARNED");
            f.setAccessible(true);
            ((java.util.Set<?>) f.get(null)).clear();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private static List<LogRecord> warningsOf(CapturingHandler handler) {
        List<LogRecord> warnings = new ArrayList<>();
        for (LogRecord record : handler.records) {
            if (record.getLevel() == Level.WARNING) {
                warnings.add(record);
            }
        }
        return warnings;
    }

    @Test
    void elementTypedHandler_runsOnMatchingClickType() {
        ViewSession session = open(ElementHandlersView.class);

        engine.click(session, click(0, ClickType.RIGHT, InventoryAction.PICKUP_HALF));

        assertEquals(1, elementHandlersView.typedRightClicks);
        assertEquals(0, elementHandlersView.untypedClicks, "the typed handler wins for its click type");
    }

    @Test
    void elementUntypedHandler_runsOnlyWithoutTypedMatch() {
        ViewSession session = open(ElementHandlersView.class);

        engine.click(session, click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL));

        assertEquals(0, elementHandlersView.typedRightClicks);
        assertEquals(1, elementHandlersView.untypedClicks);
    }

    @Test
    void elementCancelFalse_overridesConfigDefaultTrue() {
        ViewSession session = open(CancelOverrideView.class);
        InventoryClickEvent event = click(0);

        engine.click(session, event);

        assertEquals(1, cancelOverrideView.elementClicks);
        assertFalse(event.isCancelled(), "the element override beats the config default");
    }

    @Test
    void elementWithCancelFalse_isStillForceCancelledBySafetyFloor() {
        // HOTBAR_SWAP on a top slot is a safety-floor action (see ClickRoutingPhase line 93).
        // even though the element declares cancelOnClick(false) and its handler calls
        // setCancelled(false), the floor must override both and keep the event cancelled.
        ViewSession session = open(ForceUncancelView.class);
        InventoryClickEvent event = click(0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP);

        engine.click(session, event);

        assertTrue(event.isCancelled(),
                "safety floor must override cancelOnClick(false) and the handler's setCancelled(false)");
    }

    @Test
    void scrollNavButton_doesNotDoubleAdvanceOnFastDoubleClick() {
        // the reported symptom: a fast double-tap on a scroll nav arrow slid the page twice
        // because the synthetic collect-to-cursor event re-ran the untyped onClick handler
        ViewSession session = open(ScrollNavView.class);
        int navSlot = 8; // '>' in "OOOOO   >"

        engine.click(session, click(navSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        engine.click(session, click(navSlot, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR));

        assertEquals(1, scrollNavView.advanceClicks,
                "a fast double-tap must advance the scroll once, not twice");
    }

    @Test
    void throwingElementHandler_forceCancelsAndSkipsRest() {
        ViewSession session = open(ThrowingElementView.class);
        InventoryClickEvent event = click(0);

        engine.click(session, event);

        assertEquals(1, throwingElementView.handlerCalls);
        assertTrue(event.isCancelled(), "a throwing element handler force-cancels despite cancelOnClick(false)");
        assertEquals(0, throwingElementView.viewClicks, "view-level onClick is skipped after the throw");
        assertTrue(session.deferredOps().isEmpty(), "the closeOnClick post-action must not be deferred");
        assertEquals(ViewSession.Status.ACTIVE, session.status());
    }

    @Test
    void elementCloseOnClick_isDeferredToEndOfTick() {
        ViewSession session = open(CloseOnClickElementView.class);

        engine.click(session, click(0));

        assertEquals(ViewSession.Status.TRANSITIONING, session.status(),
                "the session leaves ACTIVE immediately so further clicks are swallowed");
        assertFalse(session.deferredOps().isEmpty(), "the close runs at end of tick, not inline");

        server.getScheduler().performOneTick();

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void hiddenElement_clickIsComponentLess() {
        ViewSession session = open(HiddenElementView.class);
        InventoryClickEvent event = click(0);

        engine.click(session, event);

        assertEquals(0, hiddenElementView.hiddenClicks, "hidden elements get no clicks");
        assertTrue(event.isCancelled(), "config cancel wins; the hidden element's override is ignored");
        assertEquals(1, hiddenElementView.viewClicks, "the slot degrades to a component-less click");
    }

    @Test
    void frameSlotClick_isComponentLess() {
        // slot 2 of the three-slot area holds the frame fallback, not a page element
        ViewSession session = open(ElementHandlersView.class);
        InventoryClickEvent event = click(2);

        engine.click(session, event);

        assertEquals(0, elementHandlersView.typedRightClicks);
        assertEquals(0, elementHandlersView.untypedClicks, "frame slots have no element handlers");
        assertEquals(1, elementHandlersView.viewClicks);
        assertTrue(event.isCancelled());
    }

    @Test
    void staticComponentOnPaginationSlot_abortsOpenAsOpenFailed() {
        // static-vs-element precedence is deliberately untestable: the overlap validation
        // rejects the configuration before any element could shadow a static component
        engine.open(player, OverlapView.class, ViewArguments.empty());

        assertFalse(sessions.find(player.getUniqueId()).isPresent(), "the session must never register");
        assertEquals(CloseReason.OPEN_FAILED, overlapView.lastCloseReason);
    }

    @Test
    void unboundLayoutChar_warnsExactlyOncePerViewClass() {
        Logger logger = Logger.getLogger(FirstRenderPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, UnboundCharView.class, ViewArguments.empty());
            PlayerMock second = server.addPlayer("second");
            engine.open(second, UnboundCharView.class, ViewArguments.empty());
        } finally {
            logger.removeHandler(handler);
        }

        List<LogRecord> warnings = warningsOf(handler);
        assertEquals(1, warnings.size(), "one warning per view class, not one per open");
        assertTrue(warnings.get(0).getMessage().contains("[X]"),
                "the warning must list exactly the unbound chars: " + warnings.get(0).getMessage());
    }

    @Test
    void boundAndPaginationChars_doNotWarn() {
        Logger logger = Logger.getLogger(FirstRenderPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, BoundCharsView.class, ViewArguments.empty());
        } finally {
            logger.removeHandler(handler);
        }

        assertEquals(0, warningsOf(handler).size(),
                "component-bound and pagination-bound chars must not warn");
    }
}

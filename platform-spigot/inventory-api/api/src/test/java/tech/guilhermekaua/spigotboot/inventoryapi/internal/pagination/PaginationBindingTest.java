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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.BukkitPlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationBindingTest {

    private static final Logger BINDING_LOGGER = Logger.getLogger(PaginationBinding.class.getName());

    private ServerMock server;
    private Plugin plugin;
    private ViewEngine engine;
    private PlayerMock player;

    static final class PagedView extends View {
    }

    static final class RecordingView extends View {
        final List<UpdateTrigger> triggers = new ArrayList<>();

        @Override
        protected void onUpdate(@NotNull UpdateContext context) {
            triggers.add(context.trigger());
        }
    }

    static final class WatchingView extends View {
        final MutableState<Integer> counter = mutableState(0);
    }

    /** synchronous source that records every request; totals stay unknown until the first settle (async-shaped) */
    static final class RecordingPageSource implements PageSource<Integer> {
        private final List<Integer> elements;
        final List<PageRequest> requests = new ArrayList<>();
        private boolean settled;

        RecordingPageSource(List<Integer> elements) {
            this.elements = elements;
        }

        @Override
        public void request(PageRequest request, BiConsumer<PageResult<Integer>, Throwable> onSettle) {
            requests.add(request);
            settled = true;
            int from = Math.min(Math.max(0, request.getOffset()), elements.size());
            int to = Math.min(from + request.getPageSize(), elements.size());
            onSettle.accept(PageResult.of(elements.subList(from, to), elements.size()), null);
        }

        @Override
        public int totalElements() {
            return settled ? elements.size() : 0;
        }

        @Override
        public boolean totalsKnown() {
            return settled;
        }

        @Override
        public boolean isLoading() {
            return false;
        }

        @Override
        public Throwable lastError() {
            return null;
        }

        @Override
        public List<Integer> elements() {
            return elements;
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        engine = new ViewEngine(plugin, new ViewRegistry(), new SessionRegistry(),
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        }, new BukkitPlatformScheduler(plugin));
    }

    @AfterEach
    void tearDown() {
        // nothing here schedules tasks today, but unmock drains the scheduler queue;
        // cancel defensively so a future deferred op never runs against a dead registry
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    private static ViewConfig layoutConfig() {
        // 'O' occupies slots 2, 3 and 4 of the single row
        return new ViewConfigBuilder().title("Paged").layout("  OOO    ").build();
    }

    private static ViewConfig rowsConfig() {
        return new ViewConfigBuilder().title("Paged").rows(1).build();
    }

    // builds a session exactly like ContextPhaseValidityTest: direct RegisteredView + StateStore wiring
    private ViewSession sessionFor(View view, ViewConfig config) {
        RegisteredView registered = new RegisteredView(view.getClass(), view, config);
        ViewSession session = new ViewSession(player, registered, ViewArguments.empty(),
                new StateStore(view.tokenTable().size()));
        session.effectiveConfig(config);
        session.layout(ResolvedLayout.resolve(config));
        session.inventory(Bukkit.createInventory(null, config.rows() * Layout.ROW_WIDTH));
        session.status(ViewSession.Status.ACTIVE);
        return session;
    }

    // single construction point for Task 6's package-private all-args constructor
    // (contract accessor order); async-only options stay at their defaults here
    private static <T> PaginationSpec<T> specOf(PaginationSpec.Geometry geometry,
                                                PaginationSpec.Target target,
                                                char layoutChar,
                                                Layout explicitLayout,
                                                List<Layout> patterns,
                                                PaginationItemRenderer<T> renderer,
                                                Function<ViewContext, ItemStack> fallbackItem,
                                                PaginationSourceSpec<T> source) {
        return new PaginationSpec<>(geometry, target, layoutChar, explicitLayout, patterns,
                renderer, fallbackItem, null, source, null, null, null, 128,
                null, new int[0], new int[0]);
    }

    private static <T> PaginationSpec<T> layoutCharSpec(PaginationItemRenderer<T> renderer,
                                                        PaginationSourceSpec<T> source) {
        return specOf(PaginationSpec.Geometry.NORMAL, PaginationSpec.Target.LAYOUT_CHAR, 'O',
                null, Collections.emptyList(), renderer, null, source);
    }

    private static PaginationItemRenderer<Integer> amountRenderer() {
        return (context, item, index, value) -> item.item(new ItemStack(Material.PAPER, value));
    }

    @Test
    void pendingNavigation_startsAtOneRecordsAndClampsAtOne() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);

        assertEquals(1, binding.pendingTarget());
        assertFalse(binding.hasPendingNavigation());
        assertFalse(binding.isInitialized());
        assertNull(binding.paginator());

        binding.recordSwitchTo(-3);
        assertTrue(binding.hasPendingNavigation());
        assertEquals(1, binding.pendingTarget(), "targets below 1 clamp to 1");

        binding.recordSwitchTo(4);
        assertEquals(4, binding.pendingTarget(), "the latest recorded target wins");
    }

    @Test
    void repaint_beforeInitialize_isANoOp() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);

        assertDoesNotThrow(binding::repaint);
        assertNull(session.inventory().getItem(2));
    }

    @Test
    void initialize_layoutChar_paintsEagerItemsIntoTheCharSlots() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3, 4, 5))),
                0, session, engine);

        binding.initialize(session.layout(), session.effectiveConfig());
        assertTrue(binding.isInitialized());
        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(1, inventory.getItem(2).getAmount());
        assertEquals(2, inventory.getItem(3).getAmount());
        assertEquals(3, inventory.getItem(4).getAmount());
        assertNull(inventory.getItem(1), "slots outside the area stay untouched");
        assertNull(inventory.getItem(5));
    }

    @Test
    void targetSlots_emptyBeforeInitialize_resolvedAfter() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);

        assertEquals(0, binding.targetSlots().length);

        binding.initialize(session.layout(), session.effectiveConfig());

        // layoutConfig's 'O' chars sit at slots 2, 3 and 4 (row-major)
        assertArrayEquals(new int[]{2, 3, 4}, binding.targetSlots());
        // defensive copy: mutating the returned array does not corrupt the binding
        binding.targetSlots()[0] = 99;
        assertArrayEquals(new int[]{2, 3, 4}, binding.targetSlots());
    }

    @Test
    void initialize_layoutCharWithoutSlots_throwsViewConfigurationException() {
        // a rows-only config has no layout, so 'O' resolves to zero slots
        ViewSession session = sessionFor(new PagedView(), rowsConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> binding.initialize(session.layout(), session.effectiveConfig()));

        assertTrue(error.getMessage().contains("'O'"));
        assertFalse(binding.isInitialized());
    }

    @Test
    void initialize_explicitLayoutSlotOutOfBounds_throwsNamingSlotAndRows() {
        ViewSession session = sessionFor(new PagedView(), rowsConfig());
        PaginationSpec<Integer> spec = specOf(PaginationSpec.Geometry.NORMAL,
                PaginationSpec.Target.EXPLICIT_LAYOUT, 'O', Layout.ofSlots(0, 9),
                Collections.emptyList(), amountRenderer(), null,
                PaginationSourceSpec.eager(Arrays.asList(1, 2, 3)));
        PaginationBinding binding = new PaginationBinding(spec, 0, session, engine);

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> binding.initialize(session.layout(), session.effectiveConfig()));

        assertTrue(error.getMessage().contains("slot 9"));
        assertTrue(error.getMessage().contains("1-row"));
    }

    @Test
    void initialize_patternSlotOutOfBounds_throwsViewConfigurationException() {
        ViewSession session = sessionFor(new PagedView(), rowsConfig());
        PaginationSpec<Integer> spec = specOf(PaginationSpec.Geometry.PATTERN,
                PaginationSpec.Target.PATTERNS, 'O', null,
                Arrays.asList(Layout.ofSlots(0, 1), Layout.ofSlots(10)),
                amountRenderer(), null, PaginationSourceSpec.eager(Arrays.asList(1, 2, 3)));
        PaginationBinding binding = new PaginationBinding(spec, 0, session, engine);

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> binding.initialize(session.layout(), session.effectiveConfig()));

        assertTrue(error.getMessage().contains("slot 10"));
    }

    @Test
    void initialize_consumesThePendingTargetThroughTheUnboundPaginator() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        RecordingPageSource source = new RecordingPageSource(Arrays.asList(1, 2, 3, 4, 5));
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.custom(context -> source)),
                0, session, engine);

        binding.recordSwitchTo(2);
        binding.initialize(session.layout(), session.effectiveConfig());

        assertEquals(2, binding.paginator().getCurrentPage());
        // bind dispatched exactly one load, already for the recorded page: 3 'O' slots
        // per page, so page 2 starts at offset 3
        assertEquals(1, source.requests.size());
        PageRequest request = source.requests.get(0);
        assertEquals(2, request.getPage());
        assertEquals(3, request.getPageSize());
        assertEquals(3, request.getOffset());
        // the slim request is populated from the host seam
        assertEquals(player.getUniqueId(), request.playerId());
        assertSame(plugin, request.plugin());

        binding.repaint();
        Inventory inventory = session.inventory();
        assertEquals(4, inventory.getItem(2).getAmount());
        assertEquals(5, inventory.getItem(3).getAmount());
        assertNull(inventory.getItem(4), "the empty tail of the last page clears (no fallback declared)");
    }

    @Test
    void repaint_registersElementComponentsAndReplacesThemPerPass() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) ->
                item.item(new ItemStack(Material.PAPER, value)).onClick(click -> {
                });
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(renderer, PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();
        ComponentInstance first = binding.componentAt(2);
        assertNotNull(first);
        assertNotNull(first.handlerFor(ClickType.LEFT), "the element's click handler is materialized");
        assertNull(binding.componentAt(1), "non-area slots hold no element component");

        binding.repaint();
        ComponentInstance second = binding.componentAt(2);
        assertNotNull(second);
        assertNotSame(first, second, "every area repaint materializes fresh element components");
    }

    @Test
    void refreshLazy_swapsTheSourceAndFrameItemsClearStaleElements() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        AtomicReference<List<Integer>> backing = new AtomicReference<>(Arrays.asList(1, 2, 3));
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.lazy(context -> backing.get())),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());
        binding.repaint();
        assertEquals(3, session.inventory().getItem(4).getAmount());
        assertNotNull(binding.componentAt(4));

        backing.set(Collections.singletonList(9));
        binding.refreshLazy(new PlainViewContextImpl(session, engine));
        // mid-plan: the settle pass repaints token watchers only; Task 11 makes it repaint
        // the area, so this test drives the area repaint explicitly
        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(9, inventory.getItem(2).getAmount(), "the re-invoked lazy source renders");
        assertNull(inventory.getItem(3), "the shrunk source clears the now-empty area slots");
        assertNull(inventory.getItem(4));
        assertNull(binding.componentAt(3), "a frame item removes the slot's element component");
        assertNull(binding.componentAt(4));
        assertNotNull(binding.componentAt(2));
    }

    @Test
    void rendererThrow_keepsPreviousContentAndComponentAndLogsOncePerMinute() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        AtomicBoolean fail = new AtomicBoolean(false);
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) -> {
            if (fail.get() && index == 0) {
                throw new IllegalStateException("boom");
            }
            item.item(new ItemStack(Material.PAPER, value)).onClick(click -> {
            });
        };
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(renderer, PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());
        binding.repaint();
        ComponentInstance healthy = binding.componentAt(2);
        assertEquals(1, session.inventory().getItem(2).getAmount());

        CapturingHandler handler = new CapturingHandler();
        BINDING_LOGGER.addHandler(handler);
        try {
            fail.set(true);
            binding.repaint();
            binding.repaint();
        } finally {
            BINDING_LOGGER.removeHandler(handler);
        }

        // the failed element keeps its previous item AND its previous click component
        assertEquals(1, session.inventory().getItem(2).getAmount());
        assertSame(healthy, binding.componentAt(2));
        // the healthy elements of the same passes re-rendered as usual
        assertEquals(2, session.inventory().getItem(3).getAmount());
        // two failing passes inside the same minute log exactly one SEVERE record
        assertEquals(1, handler.records.size());
        assertEquals(Level.SEVERE, handler.records.get(0).getLevel());
    }

    @Test
    void firstPaintFailure_paintsTheFallbackItem() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) -> {
            if (index == 0) {
                throw new IllegalStateException("boom");
            }
            item.item(new ItemStack(Material.PAPER, value));
        };
        PaginationSpec<Integer> spec = specOf(PaginationSpec.Geometry.NORMAL,
                PaginationSpec.Target.LAYOUT_CHAR, 'O', null, Collections.emptyList(),
                renderer, context -> new ItemStack(Material.BARRIER),
                PaginationSourceSpec.eager(Arrays.asList(1, 2, 3)));
        PaginationBinding binding = new PaginationBinding(spec, 0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(Material.BARRIER, inventory.getItem(2).getType());
        assertNull(binding.componentAt(2), "a first-paint failure paints the fallback, not a component");
        assertEquals(2, inventory.getItem(3).getAmount());
    }

    @Test
    void rendererWithoutItemSource_isTreatedAsAFailure() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) -> {
            if (index == 0) {
                // click handler but no item source: pinned rule 7 demands failure semantics
                item.onClick(click -> {
                });
                return;
            }
            item.item(new ItemStack(Material.PAPER, value));
        };
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(renderer, PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        CapturingHandler handler = new CapturingHandler();
        BINDING_LOGGER.addHandler(handler);
        try {
            binding.repaint();
        } finally {
            BINDING_LOGGER.removeHandler(handler);
        }

        // no fallback declared: the first paint of a sourceless element clears the slot
        // and registers no component
        assertNull(session.inventory().getItem(2));
        assertNull(binding.componentAt(2));
        assertEquals(1, handler.records.size());
        assertEquals(Level.SEVERE, handler.records.get(0).getLevel());
    }

    @Test
    void elementWatchersOf_matchesWatchedTokenIds() {
        WatchingView view = new WatchingView();
        ViewSession session = sessionFor(view, layoutConfig());
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) ->
                item.item(new ItemStack(Material.PAPER, value)).updateOnStateChange(view.counter);
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(renderer, PaginationSourceSpec.eager(Arrays.asList(1, 2))),
                7, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());
        binding.repaint();

        assertEquals(7, binding.tokenId());
        // the counter token has id 0; both elements watch it, the empty third slot is a frame
        assertEquals(2, binding.elementWatchersOf(Collections.singleton(0)).size());
        assertTrue(binding.elementWatchersOf(Collections.singleton(99)).isEmpty());
    }

    @Test
    void hostIsActive_trueForActiveAndTransitioningOnly() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1))),
                0, session, engine);

        session.status(ViewSession.Status.OPENING);
        assertFalse(binding.isActive());
        session.status(ViewSession.Status.ACTIVE);
        assertTrue(binding.isActive());
        session.status(ViewSession.Status.TRANSITIONING);
        assertTrue(binding.isActive());
        session.status(ViewSession.Status.CLOSED);
        assertFalse(binding.isActive());
    }

    @Test
    void requestRender_activeSession_firesOnUpdateWithPaginationSettle() {
        RecordingView view = new RecordingView();
        ViewSession session = sessionFor(view, layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());
        assertTrue(view.triggers.isEmpty(), "binding the eager engine must not fire an update");

        binding.requestRender();

        assertEquals(Collections.singletonList(UpdateTrigger.PAGINATION_SETTLE), view.triggers);
    }

    @Test
    void requestRender_closedOrOpeningSession_isDropped() {
        RecordingView view = new RecordingView();
        ViewSession session = sessionFor(view, layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        session.status(ViewSession.Status.OPENING);
        binding.requestRender();
        session.status(ViewSession.Status.CLOSED);
        binding.requestRender();

        assertTrue(view.triggers.isEmpty(), "OPENING and CLOSED sessions drop settles entirely");
    }

    @Test
    void targetSlots_patternGeometry_isTheDeduplicatedUnionInFirstEncounterOrder() {
        // overlapping slots across patterns are not rejected by checkBounds (it only checks
        // bounds, not cross-pattern duplicates), so the overlap path is legal; the
        // LinkedHashSet union in resolveTargetSlots preserves first-encounter order and
        // deduplicates slot 4, which appears in both pattern A and pattern B
        ViewSession session = sessionFor(new PagedView(), rowsConfig());
        // pattern A: slots 2, 3, 4 — pattern B: slots 4, 5; slot 4 overlaps; all fit in 1 row
        PaginationSpec<Integer> spec = specOf(PaginationSpec.Geometry.PATTERN,
                PaginationSpec.Target.PATTERNS, 'O', null,
                Arrays.asList(Layout.ofSlots(2, 3, 4), Layout.ofSlots(4, 5)),
                amountRenderer(), null,
                PaginationSourceSpec.eager(Arrays.asList(1, 2, 3, 4)));
        PaginationBinding binding = new PaginationBinding(spec, 0, session, engine);

        // before initialize the array must be empty
        assertArrayEquals(new int[0], binding.targetSlots());

        binding.initialize(session.layout(), session.effectiveConfig());

        // expected union in first-encounter order: 2, 3, 4 (from A), then 5 (from B); 4 deduped
        assertArrayEquals(new int[]{2, 3, 4, 5}, binding.targetSlots());

        // defensive copy: mutating the returned array does not corrupt the binding
        int[] copy = binding.targetSlots();
        copy[0] = 99;
        assertArrayEquals(new int[]{2, 3, 4, 5}, binding.targetSlots());
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

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

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ItemComponentBuilderImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.NormalPagination;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.PageItemFactory;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.Paginator;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.PatternPagination;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.ScrollPagination;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-(session, token) pagination runtime: records pre-init navigation, builds the
 * per-context page source and geometry engine at open time, owns the page-element
 * components of the current frame and implements the {@link PaginationHost} seam the
 * relocated engine paints and settles through.
 *
 * <p>The binding deliberately does not reference {@code PaginationImpl} — the dependency
 * points the other way (the token resolves its binding from the session's state store);
 * the binding works on the immutable {@link PaginationSpec} and its token id alone. The
 * spec's {@code T} and the paginator's {@code T} originate from the same {@code paginate*}
 * declaration, so the binding pairs them internally on {@code Object} through a documented
 * unchecked cast. Renderer, fallback, loading and lazy-source functions are evaluated
 * against one {@link PlainViewContextImpl} the binding creates at initialize time.
 *
 * <p>Renderer, fallback and loading failures are swallowed and logged at most once per
 * minute per binding (§9 error table); the affected slot keeps its previous content, or
 * shows the fallback item (or clears) when it never held content.
 */
@ApiStatus.Internal
public final class PaginationBinding implements PaginationHost {

    private static final Logger LOGGER = Logger.getLogger(PaginationBinding.class.getName());
    private static final long LOG_INTERVAL_MILLIS = 60_000L;

    private final PaginationSpec<Object> spec;
    private final int tokenId;
    private final ViewSession session;
    private final ViewEngine engine;

    // pre-init pending navigation; consumed once by initialize
    private int pendingTarget = 1;
    private boolean navigationRecorded;

    // initialize-time collaborators; null until initialize ran (or when it aborted mid-way)
    private PlainViewContextImpl plainContext;
    private Supplier<RenderedItem> fallbackSupplier;
    private Supplier<RenderedItem> loadingSupplier;
    private Paginator<Object> paginator;

    // resolved fill slots; empty until initialize ran — FirstRenderPhase validates these
    // against the static component table (overlap check, Task 12)
    private int[] targetSlots = new int[0];

    // frame slots resolved at initialize; emptyState/loading slots may lie outside targetSlots
    private int[] emptyStateSlots = new int[0];
    private int[] loadingSlots = new int[0];
    private int[] ownedOutsideSlots = new int[0];
    private Supplier<RenderedItem> emptyStateSupplier;

    // current frame: the page-element component per slot, in fill order, plus what was
    // last applied per slot — lastApplied keys drive the first-paint-fallback decision
    private final Map<Integer, ComponentInstance> elementComponents = new LinkedHashMap<>();
    private final Map<Integer, RenderedItem> lastApplied = new HashMap<>();

    // rate limit for renderer/fallback/loading failure logs; a racy double log is acceptable
    private volatile long lastLogMillis;

    /**
     * Creates the binding for one pagination token of one session.
     *
     * @param spec    the immutable pagination declaration of the token
     * @param tokenId the token id assigned by the owning view's token table
     * @param session the session this binding belongs to
     * @param engine  the engine providing the painter, the settle entry point and the plugin
     */
    @SuppressWarnings("unchecked")
    public PaginationBinding(@NotNull PaginationSpec<?> spec, int tokenId,
                             @NotNull ViewSession session, @NotNull ViewEngine engine) {
        // documented unchecked cast: the spec's T and the paginator's T originate from the
        // same paginate* declaration; the binding pairs them internally on Object
        this.spec = (PaginationSpec<Object>) Objects.requireNonNull(spec, "spec");
        this.tokenId = tokenId;
        this.session = Objects.requireNonNull(session, "session");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Returns the page navigation will land on once the binding initializes.
     *
     * @return the pending 1-indexed target page; {@code 1} until a navigation was recorded
     */
    public int pendingTarget() {
        return pendingTarget;
    }

    /**
     * Records a pre-init navigation target; consumed once by {@link #initialize}.
     *
     * @param target the requested 1-indexed page; clamped to at least {@code 1}
     */
    public void recordSwitchTo(int target) {
        this.pendingTarget = Math.max(1, target);
        this.navigationRecorded = true;
    }

    /**
     * Returns whether a pre-init navigation was recorded.
     *
     * @return {@code true} once {@link #recordSwitchTo} ran before {@link #initialize}
     */
    public boolean hasPendingNavigation() {
        return navigationRecorded;
    }

    /**
     * Builds the per-context runtime: resolves and validates the fill target, creates the
     * per-context page source and the geometry engine, replays any pending navigation on
     * the still-unbound paginator (the preserved 2.x record-only branch) and binds the
     * engine to this host — which dispatches the initial page load. Eager sources settle
     * inline here so their items are ready before the open's first paint; async sources
     * stay loading so the first paint shows the loading frame.
     *
     * @param viewLayout      the session's resolved config layout
     * @param effectiveConfig the session's effective config
     * @throws ViewConfigurationException when the layout char resolves to no slots, the
     *                                    explicit layout is empty, or an explicit layout
     *                                    or pattern slot lies outside the container
     */
    public void initialize(@NotNull ResolvedLayout viewLayout, @NotNull ViewConfig effectiveConfig) {
        Objects.requireNonNull(viewLayout, "viewLayout");
        Objects.requireNonNull(effectiveConfig, "effectiveConfig");

        Layout fillLayout = null;
        List<Layout> patterns = null;
        if (spec.geometry() == PaginationSpec.Geometry.PATTERN) {
            patterns = spec.patterns();
            for (Layout pattern : patterns) {
                checkBounds(pattern, effectiveConfig);
            }
        } else {
            fillLayout = resolveFillLayout(viewLayout, effectiveConfig);
        }
        this.targetSlots = resolveTargetSlots(fillLayout, patterns);
        this.emptyStateSlots = spec.emptyStateSlots();
        this.loadingSlots = spec.loadingSlots();
        checkFrameSlotBounds(this.emptyStateSlots, "empty-state", effectiveConfig);
        checkFrameSlotBounds(this.loadingSlots, "loading", effectiveConfig);
        this.ownedOutsideSlots = outsideLayout(this.emptyStateSlots, this.loadingSlots, this.targetSlots);

        this.plainContext = new PlainViewContextImpl(session, engine);
        PageSource<Object> source = spec.source().createSource(plainContext, spec, engine.scheduler());
        this.fallbackSupplier = frameSupplier(spec.fallbackItem(), "fallback item");
        this.loadingSupplier = frameSupplier(spec.loadingItem(), "loading item");
        this.emptyStateSupplier = frameSupplier(spec.emptyStateItem(), "empty-state item");
        PageItemFactory<Object> factory = elementFactory();

        Paginator<Object> built;
        switch (spec.geometry()) {
            case NORMAL:
                built = new NormalPagination<>(fallbackSupplier, factory, fillLayout, loadingSupplier, source);
                break;
            case SCROLL:
                built = new ScrollPagination<>(fallbackSupplier, factory, fillLayout, loadingSupplier, source);
                break;
            case PATTERN:
                built = new PatternPagination<>(fallbackSupplier, factory, patterns, loadingSupplier, source);
                break;
            default:
                throw new IllegalStateException("unhandled pagination geometry " + spec.geometry());
        }

        if (navigationRecorded) {
            // replay on the still-unbound paginator: the preserved 2.x record-only branch
            // stores the target; bind then derives the layout state for that page and
            // dispatches the initial load for it
            built.changePage(pendingTarget);
        }
        this.paginator = built;
        built.bind(this);
    }

    /**
     * Returns whether {@link #initialize} completed and the geometry engine is bound.
     *
     * @return {@code true} once the paginator exists
     */
    public boolean isInitialized() {
        return paginator != null;
    }

    /**
     * Returns the bound geometry engine.
     *
     * @return the paginator, or {@code null} before {@link #initialize} completed
     */
    public @Nullable Paginator<?> paginator() {
        return paginator;
    }

    /**
     * Returns the slots this binding paints into: the fill layout's slots for normal and
     * scroll geometry, the union of all pattern slots for pattern geometry.
     *
     * @return a defensive copy of the resolved fill slots; empty before {@link #initialize}
     */
    public @NotNull int[] targetSlots() {
        return targetSlots.clone();
    }

    /**
     * Returns the empty-state and loading frame slots this binding paints into, de-duplicated.
     *
     * @return a defensive copy of the frame slots; empty when no frame slots are declared
     */
    public @NotNull int[] frameSlots() {
        LinkedHashSet<Integer> union = new LinkedHashSet<>();
        for (int slot : emptyStateSlots) {
            union.add(slot);
        }
        for (int slot : loadingSlots) {
            union.add(slot);
        }
        int[] result = new int[union.size()];
        int index = 0;
        for (int slot : union) {
            result[index++] = slot;
        }
        return result;
    }

    /**
     * Returns the page-element component currently occupying a slot.
     *
     * @param slot the raw container slot
     * @return the element component, or {@code null} when the slot holds none
     */
    public @Nullable ComponentInstance componentAt(int slot) {
        return elementComponents.get(slot);
    }

    private static int[] resolveTargetSlots(Layout fillLayout, List<Layout> patterns) {
        if (fillLayout != null) {
            List<Integer> slots = fillLayout.slots();
            int[] resolved = new int[slots.size()];
            for (int i = 0; i < resolved.length; i++) {
                resolved[i] = slots.get(i);
            }
            return resolved;
        }
        // pattern geometry: the union of every pattern's slots, first-encounter order
        Set<Integer> union = new LinkedHashSet<>();
        for (Layout pattern : patterns) {
            union.addAll(pattern.slots());
        }
        int[] resolved = new int[union.size()];
        int i = 0;
        for (Integer slot : union) {
            resolved[i++] = slot;
        }
        return resolved;
    }

    /**
     * Repaints the whole pagination area from the engine's current frame.
     */
    public void repaint() {
        // full passes may reach a binding whose initialize aborted mid-open (the abort
        // tears the session down right after); painting nothing is the safe behavior
        if (paginator == null) {
            return;
        }
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();
        boolean loading = paginator.isLoading();
        boolean empty = !loading && paginator.isCurrentPageEmpty();

        if (empty && emptyStateSlots.length > 0) {
            paintFrameMode(inventory, emptyStateSupplier, emptyStateSlots, applyPlaceholders);
        } else {
            paginator.insertPageItems();
            clearSlots(inventory, ownedOutsideSlots, applyPlaceholders);
        }
    }

    // paints the frame item at each frame slot (one supplier call per slot, so each slot gets a
    // fresh ItemStack) and clears every other slot this binding owns (layout + owned-outside)
    private void paintFrameMode(Inventory inventory, Supplier<RenderedItem> supplier,
                                int[] frameSlots, boolean applyPlaceholders) {
        Set<Integer> chosen = new LinkedHashSet<>();
        for (int slot : frameSlots) {
            chosen.add(slot);
        }
        for (int slot : frameSlots) {
            applyToSlot(inventory, slot, supplier.get(), applyPlaceholders);
        }
        for (int slot : targetSlots) {
            if (!chosen.contains(slot)) {
                applyToSlot(inventory, slot, RenderedItem.ofItem(null), applyPlaceholders);
            }
        }
        for (int slot : ownedOutsideSlots) {
            if (!chosen.contains(slot)) {
                applyToSlot(inventory, slot, RenderedItem.ofItem(null), applyPlaceholders);
            }
        }
    }

    private void clearSlots(Inventory inventory, int[] slots, boolean applyPlaceholders) {
        for (int slot : slots) {
            applyToSlot(inventory, slot, RenderedItem.ofItem(null), applyPlaceholders);
        }
    }

    /**
     * Returns the current page-element components watching any of the given token ids.
     *
     * @param dirtyTokenIds the dirty token ids of a state flush
     * @return the matching element components, each at most once, in fill order
     */
    public @NotNull List<ComponentInstance> elementWatchersOf(@NotNull Set<Integer> dirtyTokenIds) {
        Objects.requireNonNull(dirtyTokenIds, "dirtyTokenIds");
        List<ComponentInstance> watchers = new ArrayList<>();
        for (ComponentInstance component : elementComponents.values()) {
            // watchedTokenIdsInternal() is package-private to internal.component; the
            // public clone accessor is what is visible here, and element watch lists are tiny
            for (int watched : component.watchedTokenIds()) {
                if (dirtyTokenIds.contains(watched)) {
                    watchers.add(component);
                    break;
                }
            }
        }
        return watchers;
    }

    /**
     * Returns the id of the pagination token this binding belongs to.
     *
     * @return the token id
     */
    public int tokenId() {
        return tokenId;
    }

    /**
     * Re-invokes a lazy eager source's function against the given context, replaces the
     * paginator's source with a fresh eager source over the result and re-requests the
     * current page (forced).
     *
     * @param context the context the lazy source function is evaluated against
     * @throws IllegalStateException when the binding's source is not a lazy eager source
     */
    public void refreshLazy(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        Paginator<Object> current = this.paginator;
        if (current == null) {
            // pre-init refresh is a no-op (PaginationImpl gates this; defend anyway)
            return;
        }
        Function<ViewContext, List<Object>> fn = spec.source().lazyFunction();
        if (fn == null) {
            throw new IllegalStateException("refreshLazy is only legal for a lazy eager source");
        }
        current.replaceSource(new EagerPageSource<>(fn.apply(context)));
        current.refresh();
    }

    @Override
    public boolean isActive() {
        ViewSession.Status status = session.status();
        return status == ViewSession.Status.ACTIVE || status == ViewSession.Status.TRANSITIONING;
    }

    @Override
    public void fillPage(@NotNull List<RenderedItem> items, @NotNull Layout layout) {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            // nothing to paint into; component bookkeeping would desync from the container
            return;
        }
        List<Integer> slots = layout.slots();
        // the engine builds exactly slots-many items; bound defensively anyway
        int bound = Math.min(items.size(), slots.size());
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();
        for (int i = 0; i < bound; i++) {
            applyToSlot(inventory, slots.get(i), items.get(i), applyPlaceholders);
        }
    }

    @Override
    public void requestRender() {
        engine.paginationSettle(session, tokenId);
    }

    @Override
    public @Nullable UUID playerId() {
        return player().getUniqueId();
    }

    @Override
    public @NotNull Player player() {
        return session.player();
    }

    @Override
    public @NotNull Plugin plugin() {
        return engine.plugin();
    }

    private void applyToSlot(Inventory inventory, int slot, RenderedItem item, boolean applyPlaceholders) {
        if (item.isFailure()) {
            if (lastApplied.containsKey(slot)) {
                // keep the previous slot content and element component entirely
                return;
            }
            // first paint of a failed slot: the fallback item, or a cleared slot
            RenderedItem fallback = firstPaintFallback();
            engine.painter().paint(session.player(), inventory, slot, fallback.plainItem(), applyPlaceholders);
            lastApplied.put(slot, fallback);
            return;
        }
        ItemComponentBuilderImpl elementBuilder = item.elementBuilder();
        if (elementBuilder == null) {
            // frame item: paint the plain stack (null clears) and drop any element component
            elementComponents.remove(slot);
            engine.painter().paint(session.player(), inventory, slot, item.plainItem(), applyPlaceholders);
            lastApplied.put(slot, item);
            return;
        }
        ComponentInstance component = elementBuilder.materialize(new int[]{slot});
        ItemStack rendered = component.renderForPaint(plainContext);
        if (rendered != ComponentInstance.RENDER_FAILURE) {
            engine.painter().paint(session.player(), inventory, slot, rendered, applyPlaceholders);
        }
        // identity check above: a failed item function keeps the previous slot content,
        // but the freshly materialized component still takes over the slot's handlers
        elementComponents.put(slot, component);
        lastApplied.put(slot, item);
    }

    private Layout resolveFillLayout(ResolvedLayout viewLayout, ViewConfig effectiveConfig) {
        if (spec.target() == PaginationSpec.Target.EXPLICIT_LAYOUT) {
            Layout explicit = spec.explicitLayout();
            if (explicit == null || explicit.slots().isEmpty()) {
                // the builder validates this at build(); defend against a hand-built spec
                throw new ViewConfigurationException("pagination of view "
                        + session.registered().type().getName()
                        + " declares an empty explicit layout");
            }
            checkBounds(explicit, effectiveConfig);
            return explicit;
        }
        // LAYOUT_CHAR: registration already guarantees presence; defend against an empty
        // resolution anyway so a mis-built spec fails loudly instead of painting nothing
        char character = spec.layoutChar();
        int[] slots = viewLayout.slotsOf(character);
        if (slots.length == 0) {
            throw new ViewConfigurationException("pagination layout char '" + character
                    + "' is not present in the layout of view "
                    + session.registered().type().getName());
        }
        return Layout.ofSlots(slots);
    }

    private void checkBounds(Layout layout, ViewConfig effectiveConfig) {
        int size = effectiveConfig.rows() * Layout.ROW_WIDTH;
        for (Integer slot : layout.slots()) {
            if (slot >= size) {
                throw new ViewConfigurationException("pagination layout slot " + slot
                        + " is out of bounds for a " + effectiveConfig.rows() + "-row view of "
                        + session.registered().type().getName());
            }
        }
    }

    private void checkFrameSlotBounds(int[] slots, String label, ViewConfig effectiveConfig) {
        int size = effectiveConfig.rows() * Layout.ROW_WIDTH;
        for (int slot : slots) {
            if (slot >= size) {
                throw new ViewConfigurationException("pagination " + label + " slot " + slot
                        + " is out of bounds for a " + effectiveConfig.rows() + "-row view of "
                        + session.registered().type().getName());
            }
        }
    }

    // the frame slots that fall outside the pagination's own layout, de-duplicated in order
    private static int[] outsideLayout(int[] emptyStateSlots, int[] loadingSlots, int[] layout) {
        Set<Integer> layoutSet = new LinkedHashSet<>();
        for (int slot : layout) {
            layoutSet.add(slot);
        }
        LinkedHashSet<Integer> outside = new LinkedHashSet<>();
        for (int slot : emptyStateSlots) {
            if (!layoutSet.contains(slot)) {
                outside.add(slot);
            }
        }
        for (int slot : loadingSlots) {
            if (!layoutSet.contains(slot)) {
                outside.add(slot);
            }
        }
        int[] result = new int[outside.size()];
        int index = 0;
        for (int slot : outside) {
            result[index++] = slot;
        }
        return result;
    }

    private PageItemFactory<Object> elementFactory() {
        final PaginationItemRenderer<Object> renderer = spec.renderer();
        return (index, value) -> {
            // fresh builder per element per area repaint (pinned rule 7)
            ItemComponentBuilderImpl collected = new ItemComponentBuilderImpl();
            SourceTrackingBuilder builder = new SourceTrackingBuilder(collected);
            try {
                renderer.render(plainContext, builder, index, value);
            } catch (RuntimeException error) {
                logFailure("element renderer", error);
                return RenderedItem.failure();
            }
            if (!builder.itemSourceDeclared()) {
                logFailure("element renderer", new ViewConfigurationException(
                        "pagination element renderer declared no item source; "
                                + "call item(ItemStack) or item(Function)"));
                return RenderedItem.failure();
            }
            return RenderedItem.ofElement(collected);
        };
    }

    private @Nullable Supplier<RenderedItem> frameSupplier(@Nullable final Function<ViewContext, ItemStack> fn,
                                                           final String stage) {
        if (fn == null) {
            // a null supplier lets the engine's emptyOrFallback/loadingOrFallback defaults apply
            return null;
        }
        return () -> {
            try {
                return RenderedItem.ofItem(fn.apply(plainContext));
            } catch (RuntimeException error) {
                logFailure(stage, error);
                return RenderedItem.failure();
            }
        };
    }

    private RenderedItem firstPaintFallback() {
        Supplier<RenderedItem> fallback = this.fallbackSupplier;
        if (fallback == null) {
            return RenderedItem.ofItem(null);
        }
        RenderedItem item = fallback.get();
        // a fallback that itself failed degrades to clearing the slot
        return item.isFailure() ? RenderedItem.ofItem(null) : item;
    }

    private void logFailure(String stage, RuntimeException error) {
        long now = System.currentTimeMillis();
        if (now - lastLogMillis < LOG_INTERVAL_MILLIS) {
            return;
        }
        lastLogMillis = now;
        LOGGER.log(Level.SEVERE, "pagination " + stage + " failed for view "
                + session.registered().type().getName(), error);
    }

    /**
     * Delegating view of the per-element builder handed to the user renderer: records
     * whether an item source was declared — the one builder fact the failure semantics
     * need that neither the builder nor the materialized component exposes publicly —
     * while collecting everything into the wrapped implementation.
     */
    private static final class SourceTrackingBuilder implements ItemComponentBuilder {

        private final ItemComponentBuilderImpl delegate;
        private boolean itemSourceDeclared;

        SourceTrackingBuilder(ItemComponentBuilderImpl delegate) {
            this.delegate = delegate;
        }

        boolean itemSourceDeclared() {
            return itemSourceDeclared;
        }

        @Override
        public @NotNull ItemComponentBuilder item(@NotNull ItemStack item) {
            delegate.item(item);
            itemSourceDeclared = true;
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder item(@NotNull Function<ViewContext, ItemStack> renderer) {
            delegate.item(renderer);
            itemSourceDeclared = true;
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder displayIf(@NotNull Predicate<ViewContext> condition) {
            delegate.displayIf(condition);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder updateOnStateChange(@NotNull StateToken... tokens) {
            delegate.updateOnStateChange(tokens);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder onClick(@NotNull Consumer<SlotClickContext> handler) {
            delegate.onClick(handler);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder onClick(@NotNull ClickType type,
                                                     @NotNull Consumer<SlotClickContext> handler) {
            delegate.onClick(type, handler);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder cancelOnClick(boolean cancel) {
            delegate.cancelOnClick(cancel);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder closeOnClick() {
            delegate.closeOnClick();
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target) {
            delegate.openOnClick(target);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target,
                                                         @NotNull ViewArguments arguments) {
            delegate.openOnClick(target, arguments);
            return this;
        }
    }
}

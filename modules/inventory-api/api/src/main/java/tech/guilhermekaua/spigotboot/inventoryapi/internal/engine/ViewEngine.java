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

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.ClickRoutingPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.ClosePhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.FirstRenderPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.OpenPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.UpdatePhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.SharedStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.util.ThreadUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrator of the view lifecycle: composes the fixed-order phase handlers and is the
 * sole mutator of sessions. All entry points assert the main thread.
 */
@Component
@ApiStatus.Internal
public final class ViewEngine {

    private static final Logger LOGGER = Logger.getLogger(ViewEngine.class.getName());
    private static final int CASCADE_CAP = 8;

    private final Plugin plugin;
    private final ViewRegistry views;
    private final SessionRegistry sessions;
    private final SlotPainter painter;
    private final TitleUpdater titleUpdater;

    private boolean inClickDispatch;

    // re-entrancy guard for main-thread shared flushes: a renderer writing shared state
    // while its view is being flushed must not recurse; main thread only
    private final Set<View> sharedFlushPending = new HashSet<>();
    // per-tick coalescing of off-main shared writes: maps each owner to the accumulated set
    // of dirty token ids; touched from any thread; drained atomically when the scheduled task runs
    private final ConcurrentHashMap<View, Set<Integer>> sharedFlushScheduled = new ConcurrentHashMap<>();

    // fixed-order phase handlers, engine-owned
    final OpenPhase openPhase;
    final FirstRenderPhase firstRenderPhase;
    final UpdatePhase updatePhase;
    final ClickRoutingPhase clickRoutingPhase;
    final ClosePhase closePhase;

    /**
     * Creates the engine and its phase handlers.
     *
     * @param plugin       the plugin owning the inventory-api runtime
     * @param views        the view registry
     * @param sessions     the per-player session registry
     * @param painter      the slot painter used by the rendering phases
     * @param titleUpdater the in-place title update strategy
     */
    public ViewEngine(@NotNull Plugin plugin, @NotNull ViewRegistry views, @NotNull SessionRegistry sessions,
                      @NotNull SlotPainter painter, @NotNull TitleUpdater titleUpdater) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.views = Objects.requireNonNull(views, "views");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.painter = Objects.requireNonNull(painter, "painter");
        this.titleUpdater = Objects.requireNonNull(titleUpdater, "titleUpdater");
        this.closePhase = new ClosePhase(this, sessions);
        this.openPhase = new OpenPhase(this, sessions, painter);
        this.firstRenderPhase = new FirstRenderPhase(this, sessions, painter);
        this.updatePhase = new UpdatePhase(this, painter);
        this.clickRoutingPhase = new ClickRoutingPhase(this);
    }

    /**
     * Opens a registered view for a player, replacing any previous session at the commit
     * point (spec §7). Self-defers to end of tick during click dispatch, so service-path
     * opens made from a click handler never tear down the clicked session mid-dispatch;
     * the registration check then runs when the deferred open executes.
     *
     * @param player    the viewer
     * @param viewType  the registered view class
     * @param arguments the open arguments
     * @throws UnknownViewException     when the view class is not registered
     * @throws IllegalArgumentException when an {@code initialState} argument has a
     *                                  mismatching type
     * @throws IllegalStateException    when called off the main thread
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> viewType,
                     @NotNull ViewArguments arguments) {
        ThreadUtils.assertMainThread("ViewEngine.open");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(arguments, "arguments");
        if (isInClickDispatch()) {
            // self-defer: an inline open would replace the clicked session while its click
            // is still being routed; the deferred op runs at end of tick, when click
            // dispatch is over, so it cannot re-defer
            ViewSession current = sessions.find(player.getUniqueId()).orElse(null);
            if (current != null) {
                defer(current, () -> open(player, viewType, arguments));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> open(player, viewType, arguments));
            }
            return;
        }
        RegisteredView registered = views.find(viewType).orElseThrow(() ->
                new UnknownViewException("view " + viewType.getName() + " is not registered"));
        wireSharedFlush(registered);
        ViewSession session = openPhase.openSession(player, registered, arguments);
        if (session == null) {
            // cancelled with zero side effects; the previous session stays untouched
            return;
        }
        firstRenderPhase.firstRender(session);
    }

    /**
     * Closes a session with the given reason; idempotent on already closed sessions.
     * Self-defers to end of tick during click dispatch, so service-path closes made from a
     * click handler never tear down the clicked session mid-dispatch.
     *
     * @param session the session to close
     * @param reason  the close reason
     */
    public void close(@NotNull ViewSession session, @NotNull CloseReason reason) {
        ThreadUtils.assertMainThread("ViewEngine.close");
        if (isInClickDispatch()) {
            // self-defer: the deferred op runs at end of tick, when click dispatch is
            // over, so it cannot re-defer
            defer(session, () -> close(session, reason));
            return;
        }
        closePhase.close(session, reason);
    }

    /**
     * Updates the container title of a session in place: placeholders are applied for the
     * session's player when the effective config enables them, then legacy {@code &} color
     * codes are translated, and the result is handed to the {@link TitleUpdater}.
     *
     * @param session the session whose container title is updated
     * @param title   the new title, legacy color codes supported
     * @throws IllegalStateException when called off the main thread
     */
    public void updateTitle(@NotNull ViewSession session, @NotNull String title) {
        ThreadUtils.assertMainThread("ViewEngine.updateTitle");
        // placeholders first, then color codes: PAPI output may itself contain '&' codes
        String resolved = ChatColor.translateAlternateColorCodes('&',
                painter.applyText(session.player(), title, session.effectiveConfig().applyPlaceholders()));
        titleUpdater().update(session.player(), resolved);
    }

    /**
     * Runs an update pass on a session, then flushes any state the handlers dirtied.
     *
     * @param session the session to update
     * @param trigger the cause of the update
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger) {
        ThreadUtils.assertMainThread("ViewEngine.update");
        updatePhase.update(session, trigger, null);
        flushDirty(session);
    }

    /**
     * Routes a Bukkit click event into the session per the click policy. Context
     * {@code close()}/{@code openView()} calls and component post-actions made while this
     * method runs are deferred to end of tick; dirty state written by handlers is flushed
     * after dispatch completes.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void click(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        ThreadUtils.assertMainThread("ViewEngine.click");
        clickDispatch(true);
        try {
            clickRoutingPhase.route(session, event);
        } finally {
            clickDispatch(false);
        }
        // coalesced reactive flush: handlers write state during routing and the flush
        // runs once at the end of the entry point (§5.5)
        flushDirty(session);
    }

    /**
     * Applies the drag policy of a session: when {@code cancelOnDrag} is enabled, any drag
     * touching the top container is cancelled (mirrors the 2.x listener behavior).
     *
     * @param session the affected session
     * @param event   the Bukkit event
     */
    public void drag(@NotNull ViewSession session, @NotNull InventoryDragEvent event) {
        ThreadUtils.assertMainThread("ViewEngine.drag");
        if (!session.effectiveConfig().cancelOnDrag()) {
            return;
        }
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }
        int topSize = inventory.getSize();
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Handles a Bukkit close event for a session, guarded by container identity: a close event
     * for a previous container (fired synchronously while opening a new view) must not tear
     * down the session of the view that is being opened.
     *
     * @param session the player's session
     * @param event   the Bukkit event
     */
    public void bukkitClose(@NotNull ViewSession session, @NotNull InventoryCloseEvent event) {
        ThreadUtils.assertMainThread("ViewEngine.bukkitClose");
        if (event.getInventory() != session.inventory()) {
            return;
        }
        close(session, CloseReason.PLAYER);
    }

    /**
     * Flushes dirty state tokens of a session: each pass drains the dirty set and runs a
     * STATE_CHANGE update over the watchers; passes repeat while handlers re-dirty tokens,
     * capped at {@value #CASCADE_CAP} cascades per flush, after which the remaining dirty
     * tokens are dropped with a WARNING.
     *
     * @param session the session to flush
     */
    public void flushDirty(@NotNull ViewSession session) {
        ThreadUtils.assertMainThread("ViewEngine.flushDirty");
        int cascades = 0;
        while (session.stateStore().hasDirty()) {
            if (++cascades > CASCADE_CAP) {
                LOGGER.log(Level.WARNING, "state feedback loop detected for view {0}; "
                                + "dropping remaining dirty tokens after {1} cascaded flush passes",
                        new Object[]{session.registered().type().getName(), CASCADE_CAP});
                session.stateStore().drainDirty();
                break;
            }
            Set<Integer> dirty = session.stateStore().drainDirty();
            updatePhase.update(session, UpdateTrigger.STATE_CHANGE, dirty);
        }
    }

    /**
     * Runs a full STATE_CHANGE repaint pass on every active session of the given view; full-pass
     * fallback — the wired hooks use the watcher-scoped overload.
     *
     * @param owner the view singleton whose sessions should flush
     */
    public void flushShared(@NotNull View owner) {
        ThreadUtils.assertMainThread("ViewEngine.flushShared");
        flushShared(owner, null);
    }

    /**
     * Runs a STATE_CHANGE repaint pass on every active session of the given view, restricting
     * the repaint to watchers of the supplied token id set. Passing {@code null} for
     * {@code tokenIds} triggers a full repaint (same as the no-arg overload).
     *
     * @param owner    the view singleton whose sessions should flush
     * @param tokenIds the dirty token ids to pass to the update phase, or {@code null} for a full pass
     */
    private void flushShared(@NotNull View owner, @Nullable Set<Integer> tokenIds) {
        // snapshot: an onUpdate handler may close a session and mutate the registry
        List<ViewSession> snapshot = new ArrayList<>(sessions.all());
        for (ViewSession session : snapshot) {
            if (session.registered().instance() == owner && session.isActive()) {
                updatePhase.update(session, UpdateTrigger.STATE_CHANGE, tokenIds);
            }
        }
    }

    /**
     * Wires the flush hook of every {@code SharedState} token of the view so writes fan out
     * to all of the view's open sessions: main-thread writes flush immediately (re-entrancy
     * guarded), off-main writes coalesce into one scheduled flush per view per tick. Already
     * wired tokens are skipped to avoid redundant re-wiring. The flush is watcher-scoped: each
     * hook captures its token id and passes it as a singleton dirty set so only components
     * watching that token are repainted.
     *
     * @param registered the registration whose view instance is being opened
     */
    void wireSharedFlush(@NotNull RegisteredView registered) {
        final View owner = registered.instance();
        for (StateToken token : owner.tokenTable().tokens()) {
            if (!(token instanceof SharedStateImpl)) {
                continue;
            }
            SharedStateImpl<?> shared = (SharedStateImpl<?>) token;
            if (shared.flushHookWired()) {
                continue;
            }
            final int tokenId = shared.id();
            shared.flushHook(() -> {
                if (Bukkit.isPrimaryThread()) {
                    if (sharedFlushPending.add(owner)) {
                        try {
                            flushShared(owner, Collections.singleton(tokenId));
                        } finally {
                            sharedFlushPending.remove(owner);
                        }
                    }
                } else {
                    // compute is atomic vs the drain's remove on the same key, so an id can
                    // never land in an already-drained set
                    boolean[] schedule = {false};
                    sharedFlushScheduled.compute(owner, (key, existing) -> {
                        if (existing == null) {
                            Set<Integer> created = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
                            created.add(tokenId);
                            schedule[0] = true;
                            return created;
                        }
                        existing.add(tokenId);
                        return existing;
                    });
                    if (schedule[0]) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Set<Integer> ids = sharedFlushScheduled.remove(owner);
                            if (ids != null) {
                                flushShared(owner, ids);
                            }
                        });
                    }
                }
            });
        }
    }

    /**
     * Defers an operation to the end of the current tick. The session leaves ACTIVE
     * immediately (TRANSITIONING) so further clicks are swallowed until the operation runs;
     * sessions still TRANSITIONING after the drain return to ACTIVE. Every queued operation
     * is guarded so it no-ops when the session was closed before the tick ran (manual close,
     * disconnect, plugin disable, or an earlier deferred operation queued by the same click).
     *
     * @param session the session the operation belongs to
     * @param op      the operation to run at end of tick
     */
    public void defer(@NotNull ViewSession session, @NotNull Runnable op) {
        ThreadUtils.assertMainThread("ViewEngine.defer");
        if (session.status() == ViewSession.Status.ACTIVE) {
            session.status(ViewSession.Status.TRANSITIONING);
        }
        // cleanup safety: a stale op against a closed session must do nothing
        session.deferredOps().add(() -> {
            if (session.status() != ViewSession.Status.CLOSED) {
                op.run();
            }
        });
        Bukkit.getScheduler().runTask(plugin, () -> drainDeferred(session));
    }

    private void drainDeferred(ViewSession session) {
        List<Runnable> ops = session.deferredOps();
        while (!ops.isEmpty()) {
            ops.remove(0).run();
        }
        // deferred ops that neither closed nor replaced the session leave it usable again
        if (session.status() == ViewSession.Status.TRANSITIONING) {
            session.status(ViewSession.Status.ACTIVE);
        }
    }

    /**
     * Returns whether a click event is currently being dispatched (drives operation
     * deferral).
     *
     * @return {@code true} while a click is being dispatched
     */
    public boolean isInClickDispatch() {
        return inClickDispatch;
    }

    /**
     * Marks the engine as inside or outside click dispatch; toggled by {@link #click}
     * around routing.
     *
     * @param active {@code true} while a click is being dispatched
     */
    @ApiStatus.Internal
    public void clickDispatch(boolean active) {
        this.inClickDispatch = active;
    }

    /**
     * Returns the plugin owning the inventory-api runtime.
     *
     * @return the owning plugin
     */
    public @NotNull Plugin plugin() {
        return plugin;
    }

    /**
     * Returns the in-place title update strategy.
     *
     * @return the title updater
     */
    public @NotNull TitleUpdater titleUpdater() {
        return titleUpdater;
    }

}

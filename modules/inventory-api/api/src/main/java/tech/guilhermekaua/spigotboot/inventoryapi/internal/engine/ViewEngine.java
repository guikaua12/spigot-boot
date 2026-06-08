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
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.ClickRoutingPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.ClosePhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.FirstRenderPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.OpenPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.PaginationInitPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.UpdatePhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.util.ThreadUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrator of the view lifecycle: composes the fixed-order phase handlers and is the
 * sole mutator of sessions. All entry points assert the main thread.
 */
@Component
@ApiStatus.Internal
public final class ViewEngine {

    private final Plugin plugin;
    private final ViewRegistry views;
    private final SessionRegistry sessions;
    private final SlotPainter painter;
    private final TitleUpdater titleUpdater;

    private boolean inClickDispatch;

    // fixed-order phase handlers, engine-owned
    final OpenPhase openPhase;
    final FirstRenderPhase firstRenderPhase;
    final UpdatePhase updatePhase;
    final ClickRoutingPhase clickRoutingPhase;
    final ClosePhase closePhase;
    final PaginationInitPhase paginationInitPhase;

    // flush machinery extracted behind a dedicated coordinator (single responsibility);
    // the flush entry points below delegate to it after asserting the main thread
    private final FlushCoordinator flushCoordinator;

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
        this.paginationInitPhase = new PaginationInitPhase(this, sessions);
        this.flushCoordinator = new FlushCoordinator(plugin, sessions, updatePhase);
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
        if (!paginationInitPhase.init(session)) {
            // a failed init already aborted the open with an OPEN_FAILED close
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
     * Runs a pagination-settle update pass scoped to the settled token, then flushes any
     * state the handlers dirtied. ACTIVE and TRANSITIONING sessions receive the settle;
     * CLOSED and OPENING sessions drop it entirely — no paint, no watcher marking, no
     * {@code onUpdate} (§7).
     *
     * @param session the session whose pagination token settled
     * @param tokenId the token id of the settled pagination declaration
     * @throws IllegalStateException when called off the main thread
     */
    public void paginationSettle(@NotNull ViewSession session, int tokenId) {
        ThreadUtils.assertMainThread("ViewEngine.paginationSettle");
        ViewSession.Status status = session.status();
        if (status == ViewSession.Status.CLOSED || status == ViewSession.Status.OPENING) {
            return;
        }
        updatePhase.update(session, UpdateTrigger.PAGINATION_SETTLE, Collections.singleton(tokenId));
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
     * capped at {@value FlushCoordinator#CASCADE_CAP} cascades per flush, after which the
     * remaining dirty tokens are dropped with a WARNING.
     *
     * @param session the session to flush
     */
    public void flushDirty(@NotNull ViewSession session) {
        ThreadUtils.assertMainThread("ViewEngine.flushDirty");
        flushCoordinator.flushDirty(session);
    }

    /**
     * Runs a full STATE_CHANGE repaint pass on every active session of the given view; full-pass
     * fallback — the wired hooks use the watcher-scoped overload.
     *
     * @param owner the view singleton whose sessions should flush
     */
    public void flushShared(@NotNull View owner) {
        ThreadUtils.assertMainThread("ViewEngine.flushShared");
        flushCoordinator.flushShared(owner);
    }

    /**
     * Wires the flush hook of every {@code SharedState} token of the view so writes fan out
     * to all of the view's open sessions; see {@link FlushCoordinator#wireSharedFlush} for
     * the immediate-vs-coalesced dispatch rules.
     *
     * @param registered the registration whose view instance is being opened
     */
    void wireSharedFlush(@NotNull RegisteredView registered) {
        flushCoordinator.wireSharedFlush(registered);
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
     * Returns the slot painter used by the rendering phases; pagination bindings paint
     * their page frames through it.
     *
     * @return the slot painter
     */
    public @NotNull SlotPainter painter() {
        return painter;
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

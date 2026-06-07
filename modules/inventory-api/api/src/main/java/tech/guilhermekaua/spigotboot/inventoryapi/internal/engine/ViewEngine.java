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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

/**
 * Orchestrator of the v3 view lifecycle and sole session mutator. This skeleton pins the
 * contracted surface; the phase handlers filling {@link #open}, {@link #close},
 * {@link #update}, {@link #click}, {@link #drag}, {@link #bukkitClose}, {@link #flushDirty}
 * and {@link #flushShared} are added by plan tasks 12-16.
 */
@Component
@ApiStatus.Internal
public final class ViewEngine {

    private final Plugin plugin;
    // collaborators consumed by the phase handlers added in tasks 12-16
    private final ViewRegistry views;
    private final SessionRegistry sessions;
    private final SlotPainter painter;
    private final TitleUpdater titleUpdater;

    private boolean inClickDispatch;

    /**
     * Creates the engine.
     *
     * @param plugin       the plugin owning the inventory-api runtime
     * @param views        the view class registry
     * @param sessions     the per-player session registry
     * @param painter      the slot painting strategy
     * @param titleUpdater the in-place title update strategy
     */
    public ViewEngine(@NotNull Plugin plugin, @NotNull ViewRegistry views, @NotNull SessionRegistry sessions,
                      @NotNull SlotPainter painter, @NotNull TitleUpdater titleUpdater) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.views = Objects.requireNonNull(views, "views");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.painter = Objects.requireNonNull(painter, "painter");
        this.titleUpdater = Objects.requireNonNull(titleUpdater, "titleUpdater");
    }

    /**
     * Opens a registered view for a player, replacing any current session.
     *
     * @param player    the viewer
     * @param viewType  the registered view class
     * @param arguments the open arguments
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> viewType,
                     @NotNull ViewArguments arguments) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Closes a session with the given reason.
     *
     * @param session the session to close
     * @param reason  the close reason
     */
    public void close(@NotNull ViewSession session, @NotNull CloseReason reason) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Runs an update pass on a session.
     *
     * @param session the session to update
     * @param trigger the cause of the update
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Routes a Bukkit click event into the session per the click policy.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void click(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Applies the drag policy of a session to a Bukkit drag event.
     *
     * @param session the affected session
     * @param event   the Bukkit event
     */
    public void drag(@NotNull ViewSession session, @NotNull InventoryDragEvent event) {
        throw new UnsupportedOperationException("implemented in Task 14");
    }

    /**
     * Handles a Bukkit close event for a session, guarded by container identity.
     *
     * @param session the player's session
     * @param event   the Bukkit event
     */
    public void bukkitClose(@NotNull ViewSession session, @NotNull InventoryCloseEvent event) {
        throw new UnsupportedOperationException("implemented in Task 13");
    }

    /**
     * Flushes dirty state tokens of a session (STATE_CHANGE re-render, cascade cap 8).
     *
     * @param session the session to flush
     */
    public void flushDirty(@NotNull ViewSession session) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }

    /**
     * Flushes shared-state watchers of every open session of a view.
     *
     * @param owner the view singleton whose sessions should flush
     */
    public void flushShared(@NotNull View owner) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }

    /**
     * Defers an operation to the end of the current tick. The session leaves ACTIVE
     * immediately (TRANSITIONING) so further clicks are swallowed until the operation runs.
     *
     * @param session the session the operation belongs to
     * @param op      the operation to run at end of tick
     */
    public void defer(@NotNull ViewSession session, @NotNull Runnable op) {
        session.status(ViewSession.Status.TRANSITIONING);
        session.deferredOps().add(op);
        // the Bukkit scheduler call activates when close() and open() are real (Task 12);
        // the skeleton only establishes the deferral invariants that are tested in Task 11
        // TODO(Task 14): Bukkit.getScheduler().runTask(plugin, op);
    }

    /**
     * Returns whether a click event is currently being dispatched; drives operation deferral.
     *
     * @return {@code true} while a click is being dispatched
     */
    public boolean isInClickDispatch() {
        return inClickDispatch;
    }

    /**
     * Marks the engine as inside or outside click dispatch; toggled by the click routing
     * phase (plan task 14) around handler execution.
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

    /**
     * Throws when called off the main server thread; a missing server (pure unit tests)
     * passes on any thread.
     *
     * @param operation the operation name used in the error message
     * @throws IllegalStateException when called off the main thread
     */
    public static void assertMainThread(@NotNull String operation) {
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(operation + " must run on the main thread");
        }
    }
}

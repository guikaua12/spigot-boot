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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.session;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentTable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime state of one (player, open) pair: lifecycle status, container, effective config,
 * resolved layout, component table, state storage and deferred end-of-tick operations.
 *
 * <p>Created in {@link Status#OPENING} by the open phase and mutated only by the engine on
 * the main thread; everything here is dropped when the session closes.
 */
@ApiStatus.Internal
public final class ViewSession {

    /**
     * Lifecycle status of a session.
     */
    public enum Status {

        /** The open phase is running; no container is shown yet. */
        OPENING,

        /** The container is shown; clicks and updates are routed. */
        ACTIVE,

        /** A deferred close or navigation is pending; further clicks are swallowed. */
        TRANSITIONING,

        /** The session tore down; terminal. */
        CLOSED
    }

    private final Player player;
    private final RegisteredView registered;
    private final ViewArguments arguments;
    private final StateStore stateStore;
    private final ComponentTable components = new ComponentTable();
    private final List<Runnable> deferredOps = new ArrayList<>();

    private Status status = Status.OPENING;
    private Inventory inventory;
    private ViewConfig effectiveConfig;
    private ResolvedLayout layout;
    private BukkitTask updateTask;

    /**
     * Creates a session in {@link Status#OPENING}.
     *
     * @param player     the viewer
     * @param registered the registration of the opened view
     * @param arguments  the arguments this open was requested with
     * @param stateStore the per-session state storage, sized for the view's token table
     */
    public ViewSession(@NotNull Player player, @NotNull RegisteredView registered,
                       @NotNull ViewArguments arguments, @NotNull StateStore stateStore) {
        this.player = Objects.requireNonNull(player, "player");
        this.registered = Objects.requireNonNull(registered, "registered");
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        // the registered config applies until the open phase merges per-open overrides
        this.effectiveConfig = registered.config();
    }

    /**
     * Returns the viewer of this session.
     *
     * @return the player
     */
    public @NotNull Player player() {
        return player;
    }

    /**
     * Returns the registration of the opened view.
     *
     * @return the registered view
     */
    public @NotNull RegisteredView registered() {
        return registered;
    }

    /**
     * Returns the arguments this open was requested with.
     *
     * @return the open arguments; empty when none were passed
     */
    public @NotNull ViewArguments arguments() {
        return arguments;
    }

    /**
     * Returns the per-session state storage.
     *
     * @return the state store
     */
    public @NotNull StateStore stateStore() {
        return stateStore;
    }

    /**
     * Returns this session's component table; one stable instance for the session's lifetime.
     *
     * @return the component table
     */
    public @NotNull ComponentTable components() {
        return components;
    }

    /**
     * Returns the current lifecycle status.
     *
     * @return the status
     */
    public @NotNull Status status() {
        return status;
    }

    /**
     * Sets the lifecycle status; called only by the engine.
     *
     * @param s the new status
     */
    public void status(@NotNull Status s) {
        this.status = Objects.requireNonNull(s, "status");
    }

    /**
     * Returns the top container of this session.
     *
     * @return the container, or {@code null} until the open phase creates it
     */
    public @Nullable Inventory inventory() {
        return inventory;
    }

    /**
     * Sets the top container once it is created.
     *
     * @param inv the created container
     */
    public void inventory(@NotNull Inventory inv) {
        this.inventory = Objects.requireNonNull(inv, "inventory");
    }

    /**
     * Returns the effective configuration of this session: the registered config until the
     * open phase applies per-open overrides.
     *
     * @return the effective config, never {@code null}
     */
    public @NotNull ViewConfig effectiveConfig() {
        return effectiveConfig;
    }

    /**
     * Sets the effective configuration after per-open overrides were merged.
     *
     * @param c the merged config
     */
    public void effectiveConfig(@NotNull ViewConfig c) {
        this.effectiveConfig = Objects.requireNonNull(c, "effectiveConfig");
    }

    /**
     * Returns the resolved layout of this session.
     *
     * @return the resolved layout, or {@code null} until layout resolution ran
     */
    public @Nullable ResolvedLayout layout() {
        return layout;
    }

    /**
     * Sets the resolved layout.
     *
     * @param l the resolved layout
     */
    public void layout(@NotNull ResolvedLayout l) {
        this.layout = Objects.requireNonNull(l, "layout");
    }

    /**
     * Returns the scheduled update task of this session.
     *
     * @return the task, or {@code null} when scheduling is disabled or not started
     */
    public @Nullable BukkitTask updateTask() {
        return updateTask;
    }

    /**
     * Sets or clears the scheduled update task.
     *
     * @param t the task, or {@code null} to clear it
     */
    public void updateTask(@Nullable BukkitTask t) {
        this.updateTask = t;
    }

    /**
     * Returns whether this session is currently active.
     *
     * @return {@code true} only while the status is {@link Status#ACTIVE}
     */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /**
     * Returns the operations deferred to the end of the current tick; the engine appends
     * during click dispatch and drains in FIFO order. The list is not yet drained when ops
     * execute; plan task 15 reconciles the queue with the scheduler.
     *
     * @return the mutable deferred-operations list, one stable instance per session
     */
    public @NotNull List<Runnable> deferredOps() {
        return deferredOps;
    }
}

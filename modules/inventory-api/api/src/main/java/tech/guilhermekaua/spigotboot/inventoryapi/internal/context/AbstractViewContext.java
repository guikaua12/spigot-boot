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

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateBackedContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Objects;
import java.util.UUID;

/**
 * Base implementation shared by every per-phase context: resolves all {@link ViewContext}
 * accessors from the bound session and routes the mutating operations through the engine.
 * Implements the {@link StateBackedContext} seam so state tokens resolve their storage
 * without casting to concrete context classes.
 *
 * <p>Deferral policy (§5.4): {@link #close()} and {@link #openView} are deferred to the end
 * of the tick during click dispatch, and {@link #openView} additionally while the session is
 * not yet active (opening/rendering); {@link #update()} is never deferred because an update
 * pass is safe mid-click.
 */
@ApiStatus.Internal
public abstract class AbstractViewContext implements ViewContext, StateBackedContext {

    private final ViewSession session;
    private final ViewEngine engine;

    /**
     * Binds the context to one session and the engine.
     *
     * @param session the session this context belongs to
     * @param engine  the engine executing lifecycle operations
     */
    protected AbstractViewContext(@NotNull ViewSession session, @NotNull ViewEngine engine) {
        this.session = Objects.requireNonNull(session, "session");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Returns the session this context is bound to.
     *
     * @return the backing session
     */
    public @NotNull ViewSession session() {
        return session;
    }

    /**
     * Returns the engine this context routes operations through.
     *
     * @return the view engine
     */
    protected @NotNull ViewEngine engine() {
        return engine;
    }

    @Override
    public @NotNull Player player() {
        return session.player();
    }

    @Override
    public @NotNull UUID playerId() {
        return session.player().getUniqueId();
    }

    @Override
    public @NotNull View view() {
        return session.registered().instance();
    }

    @Override
    public @NotNull ViewConfig config() {
        return session.effectiveConfig();
    }

    @Override
    public @NotNull Plugin plugin() {
        return engine.plugin();
    }

    @Override
    public @NotNull ViewArguments arguments() {
        return session.arguments();
    }

    @Override
    public @NotNull Inventory inventory() {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            throw new IllegalStateException("the container of this session has not been created yet");
        }
        if (session.status() == ViewSession.Status.CLOSED) {
            throw new IllegalStateException("the session is closed; its container is no longer available");
        }
        return inventory;
    }

    @Override
    public boolean isActive() {
        return session.isActive();
    }

    @Override
    public void update() {
        // explicit updates are safe mid-click; only close/openView defer to end of tick
        engine.update(session, UpdateTrigger.EXPLICIT);
    }

    @Override
    public void close() {
        if (engine.isInClickDispatch()) {
            engine.defer(session, () -> engine.close(session, CloseReason.API));
            return;
        }
        engine.close(session, CloseReason.API);
    }

    @Override
    public void updateTitle(@NotNull String title) {
        engine.titleUpdater().update(player(), title);
    }

    @Override
    public void openView(@NotNull Class<? extends View> target) {
        openView(target, ViewArguments.empty());
    }

    @Override
    public void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments) {
        // also defer while the session is still opening/rendering: an immediate inner open
        // would be orphaned when the outer open completes and activates its own session;
        // deferral keeps last-wins semantics (the inner open later replaces the outer one)
        if (engine.isInClickDispatch() || session.status() != ViewSession.Status.ACTIVE) {
            engine.defer(session, () -> engine.open(player(), target, arguments));
            return;
        }
        engine.open(player(), target, arguments);
    }

    @Override
    public @NotNull StateStore stateStore() {
        return session.stateStore();
    }

    @Override
    public @NotNull View owner() {
        return session.registered().instance();
    }

    @Override
    public boolean contextActive() {
        ViewSession.Status status = session.status();
        return status == ViewSession.Status.OPENING || status == ViewSession.Status.ACTIVE;
    }
}

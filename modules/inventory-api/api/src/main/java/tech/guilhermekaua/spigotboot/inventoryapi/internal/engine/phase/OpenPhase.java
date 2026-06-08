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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.HandlerInvoker;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.OpenContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.InitialStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * First half of the open pipeline (spec §7.2-7.6): session and store creation,
 * initial-state binding, {@code onOpen} with cancellation, previous-session replacement,
 * per-open config merge, layout resolution and container creation. Constructed and
 * invoked only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class OpenPhase {

    private static final Logger LOGGER = Logger.getLogger(OpenPhase.class.getName());

    private final ViewEngine engine;
    private final SessionRegistry sessions;
    private final SlotPainter painter;

    /**
     * Creates the phase.
     *
     * @param engine   the owning engine, used to close a replaced previous session
     * @param sessions the per-player session registry
     * @param painter  the slot painter, used to apply placeholders to the container title
     */
    public OpenPhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions,
                     @NotNull SlotPainter painter) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.painter = Objects.requireNonNull(painter, "painter");
    }

    /**
     * Opens a session up to (and including) container creation; first render and
     * registration happen in {@link FirstRenderPhase}.
     *
     * @param player     the viewer
     * @param registered the registration of the view to open
     * @param arguments  the open arguments
     * @return the new session ready for first render, or {@code null} when the open was
     *         cancelled (by {@code cancelOpen()} or a throwing {@code onOpen}) with zero
     *         side effects
     * @throws IllegalArgumentException   when an {@code initialState} argument is present
     *                                    with a mismatching type (propagates before any side effect)
     * @throws ViewConfigurationException when a per-open override recorded in {@code onOpen}
     *                                    is invalid (propagates before the previous session
     *                                    is replaced)
     */
    public @Nullable ViewSession openSession(@NotNull Player player, @NotNull RegisteredView registered,
                                             @NotNull ViewArguments arguments) {
        View view = registered.instance();
        StateStore store = new StateStore(view.tokenTable().size());
        ViewSession session = new ViewSession(player, registered, arguments, store);
        session.status(ViewSession.Status.OPENING);
        // onOpen reads the registered config until the per-open overrides are committed
        session.effectiveConfig(registered.config());

        // a type mismatch propagates here, before any observable side effect
        bindInitialState(view, arguments, store);
        // pagination bindings exist before onOpen so pre-init navigation calls have a
        // recording target; the init phase replays the recorded target after layout resolution
        bindPaginationTokens(view, session, store);

        OpenContextImpl openContext = new OpenContextImpl(session, engine);
        boolean failed = false;
        try {
            HandlerInvoker.invoke(HandlerInvoker.ON_OPEN, view, openContext);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onOpen failed for view " + view.getClass().getName()
                    + "; treating the open as cancelled", ex);
            failed = true;
        }
        if (failed || openContext.isOpenCancelled()) {
            // zero side effects: the player's previous session, if any, stays untouched
            return null;
        }

        // validate the per-open overrides before the commit point: an invalid override aborts
        // the open with the previous session untouched, the same zero-side-effects semantics
        // as cancelOpen
        ViewConfig effective = registered.config()
                .withOverrides(openContext.overriddenTitle(), openContext.overriddenRows());

        // commit point: replace the previous session before the new container exists
        Optional<ViewSession> previous = sessions.find(player.getUniqueId());
        if (previous.isPresent()) {
            engine.close(previous.get(), CloseReason.REPLACED);
        }

        session.effectiveConfig(effective);
        session.layout(ResolvedLayout.resolve(effective));

        // placeholders first, then color codes: PAPI output may itself contain '&' codes (§7 step 6)
        Inventory inventory = Bukkit.createInventory(null, effective.rows() * Layout.ROW_WIDTH,
                ChatColor.translateAlternateColorCodes('&',
                        painter.applyText(player, effective.title(), effective.applyPlaceholders())));
        session.inventory(inventory);
        return session;
    }

    // seeds initialState tokens from the open arguments; absent keys stay unset (null reads)
    private static void bindInitialState(View view, ViewArguments arguments, StateStore store) {
        for (StateToken token : view.tokenTable().tokens()) {
            if (token instanceof InitialStateImpl) {
                InitialStateImpl<?> initial = (InitialStateImpl<?>) token;
                Object value = arguments.get(initial.key(), initial.type());
                if (value != null) {
                    store.set(initial.tokenId(), value);
                }
            }
        }
    }

    // creates one binding per pagination token; the binding records pre-init navigation
    // and is initialized by the pagination init phase after layout resolution
    private void bindPaginationTokens(View view, ViewSession session, StateStore store) {
        for (StateToken token : view.tokenTable().tokens()) {
            if (token instanceof PaginationImpl) {
                PaginationImpl<?> pagination = (PaginationImpl<?>) token;
                store.set(pagination.tokenId(),
                        new PaginationBinding(pagination.spec(), pagination.tokenId(), session, engine));
            }
        }
    }
}

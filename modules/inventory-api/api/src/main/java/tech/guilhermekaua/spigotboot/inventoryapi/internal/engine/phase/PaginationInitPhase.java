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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pagination init step of the open pipeline (spec §7 step 7): initializes every pagination
 * binding of a freshly opened session between layout resolution and {@code onFirstRender} —
 * target slots resolve against the session layout, the per-context page source is built,
 * the geometry engine is constructed and pending navigation recorded during {@code onOpen}
 * is replayed. A failing initialization aborts the open through the shared OPEN_FAILED
 * path. Constructed and invoked only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class PaginationInitPhase {

    private static final Logger LOGGER = Logger.getLogger(PaginationInitPhase.class.getName());

    private final ViewEngine engine;
    private final SessionRegistry sessions;

    /**
     * Creates the phase.
     *
     * @param engine   the owning engine, used by the abort path
     * @param sessions the per-player session registry, used by the abort path
     */
    public PaginationInitPhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Initializes every pagination binding of the session.
     *
     * @param session the freshly opened session; layout resolved and container created
     * @return {@code true} when every binding initialized; {@code false} when a binding
     *         failed and the open was aborted with an {@code OPEN_FAILED} close
     */
    public boolean init(@NotNull ViewSession session) {
        // openSession resolved the layout before returning; init never runs without it
        ResolvedLayout layout = Objects.requireNonNull(session.layout(), "layout");
        for (PaginationBinding binding : PaginationBindings.of(session)) {
            try {
                binding.initialize(layout, session.effectiveConfig());
            } catch (RuntimeException ex) {
                LOGGER.log(Level.SEVERE, "pagination init failed for view "
                        + session.registered().type().getName() + "; aborting the open", ex);
                OpenFailureHandler.abort(engine, sessions, session);
                return false;
            }
        }
        return true;
    }
}

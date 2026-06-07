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

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.HandlerInvoker;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.UpdateContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Update pass: invokes {@code View.onUpdate} with the trigger, then repaints components —
 * all of them, or only the watchers of a dirty token set during a state flush. An
 * {@code onUpdate} failure is logged and the repaint still runs (§9 error table).
 */
@ApiStatus.Internal
public final class UpdatePhase {

    private static final Logger LOGGER = Logger.getLogger(UpdatePhase.class.getName());

    private final ViewEngine engine;
    private final SlotPainter painter;

    /**
     * Creates the phase.
     *
     * @param engine  the engine providing context plumbing
     * @param painter the slot painter writing repaint results
     */
    public UpdatePhase(@NotNull ViewEngine engine, @NotNull SlotPainter painter) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.painter = Objects.requireNonNull(painter, "painter");
    }

    /**
     * Runs one update pass on a session. Non-active sessions skip every trigger except
     * {@link UpdateTrigger#STATE_CHANGE}, which flushes are allowed to deliver.
     * TRANSITIONING and OPENING sessions still receive STATE_CHANGE passes; CLOSED sessions
     * never repaint.
     *
     * @param session     the session to update
     * @param trigger     the cause of this pass
     * @param dirtyOrNull the dirty token ids restricting the repaint to their watchers,
     *                    or {@code null} to repaint every component
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger,
                       @Nullable Set<Integer> dirtyOrNull) {
        if (!session.isActive() && trigger != UpdateTrigger.STATE_CHANGE) {
            return;
        }

        UpdateContextImpl context = new UpdateContextImpl(session, engine, trigger);
        try {
            HandlerInvoker.invoke(HandlerInvoker.ON_UPDATE,
                    session.registered().instance(), context);
        } catch (RuntimeException error) {
            LOGGER.log(Level.SEVERE, "onUpdate failed for view "
                    + session.registered().type().getName(), error);
        }

        // onUpdate may have closed the session; never paint a torn-down container
        if (session.status() == ViewSession.Status.CLOSED) {
            return;
        }
        repaint(session, context, dirtyOrNull);
    }

    private void repaint(ViewSession session, UpdateContextImpl context,
                         @Nullable Set<Integer> dirtyOrNull) {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }

        List<ComponentInstance> targets = dirtyOrNull == null
                ? session.components().all()
                : session.components().watchersOf(dirtyOrNull);
        Player player = session.player();
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();

        for (ComponentInstance component : targets) {
            ItemStack item = component.renderForPaint(context);
            if (item == ComponentInstance.RENDER_FAILURE) {
                // identity check: render failed, keep the previous slot content (§9)
                continue;
            }
            for (int slot : component.slots()) {
                painter.paint(player, inventory, slot, item, applyPlaceholders);
            }
        }
    }
}

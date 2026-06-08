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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.HandlerInvoker;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.RenderContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.schedule.ViewUpdateTask;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Second half of the open pipeline: {@code onFirstRender}, component materialization,
 * initial paint, session registration, container show, activation and update-task start.
 * A throwing {@code onFirstRender} aborts the open with an {@code OPEN_FAILED} close hook
 * and registers nothing (spec §9). Constructed and invoked only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class FirstRenderPhase {

    private static final Logger LOGGER = Logger.getLogger(FirstRenderPhase.class.getName());

    private final ViewEngine engine;
    private final SessionRegistry sessions;
    private final SlotPainter painter;

    /**
     * Creates the phase.
     *
     * @param engine   the owning engine
     * @param sessions the per-player session registry
     * @param painter  the slot painter used for the initial paint
     */
    public FirstRenderPhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions,
                            @NotNull SlotPainter painter) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.painter = Objects.requireNonNull(painter, "painter");
    }

    /**
     * Renders and shows a freshly opened session produced by {@link OpenPhase#openSession}.
     *
     * @param session the session to render and activate
     */
    public void firstRender(@NotNull ViewSession session) {
        View view = session.registered().instance();
        RenderContextImpl renderContext = new RenderContextImpl(session, engine);
        try {
            HandlerInvoker.invoke(HandlerInvoker.ON_FIRST_RENDER, view, renderContext);
            renderContext.materializeAll();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onFirstRender failed for view " + view.getClass().getName()
                    + "; aborting the open", ex);
            OpenFailureHandler.abort(engine, sessions, session);
            return;
        }

        paintAll(session, renderContext);

        sessions.register(session);
        session.player().openInventory(session.inventory());
        session.status(ViewSession.Status.ACTIVE);
        startScheduledUpdates(session);
    }

    private void paintAll(ViewSession session, RenderContextImpl renderContext) {
        Inventory inventory = session.inventory();
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();
        for (ComponentInstance component : session.components().all()) {
            ItemStack item = component.renderForPaint(renderContext);
            if (item == ComponentInstance.RENDER_FAILURE) {
                // identity sentinel: skip the paint so the slots keep their previous content (§9)
                continue;
            }
            for (int slot : component.slots()) {
                painter.paint(session.player(), inventory, slot, item, applyPlaceholders);
            }
        }
        // pagination areas paint after the static components: eager sources show their
        // items before the container is shown to the player; async sources paint the
        // loading frame
        for (PaginationBinding binding : PaginationBindings.of(session)) {
            if (binding.isInitialized()) {
                binding.repaint();
            }
        }
    }

    private void startScheduledUpdates(ViewSession session) {
        long interval = session.effectiveConfig().updateIntervalTicks();
        if (interval <= 0) {
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(engine.plugin(),
                new ViewUpdateTask(engine, session), interval, interval);
        session.updateTask(task);
    }
}

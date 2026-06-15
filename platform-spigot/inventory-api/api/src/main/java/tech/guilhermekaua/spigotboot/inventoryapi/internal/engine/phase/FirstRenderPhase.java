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

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformTask;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.HandlerInvoker;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.RenderContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationSpec;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.schedule.ViewUpdateTask;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    // warn once per view class per classloader: a full server reload re-creates the plugin
    // classloader and warns again; re-registering views inside the same JVM does not
    private static final Set<Class<?>> UNBOUND_CHAR_WARNED = ConcurrentHashMap.newKeySet();

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
            validatePaginationOverlap(session);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onFirstRender failed for view " + view.getClass().getName()
                    + "; aborting the open", ex);
            OpenFailureHandler.abort(engine, sessions, session);
            return;
        }

        warnUnboundLayoutChars(session, renderContext);

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

    // overlap validation (§5.3/§6): a slot cannot be both statically bound and a pagination
    // target; runs inside the try so the failure flows into the OPEN_FAILED abort path.
    // bindings have no element components yet at this point (the first fill happens in
    // paintAll), so the check uses the binding's resolved target slots
    private void validatePaginationOverlap(ViewSession session) {
        for (PaginationBinding binding : PaginationBindings.of(session)) {
            for (int slot : binding.targetSlots()) {
                if (session.components().componentAt(slot) != null) {
                    throw new ViewConfigurationException(
                            "slot " + slot + " is bound to both a component and pagination");
                }
            }
        }
    }

    // unbound-layout-char warning (§5.3): chars present in the effective layout but bound by
    // neither a layoutSlot(...) declaration nor a LAYOUT_CHAR pagination target
    private void warnUnboundLayoutChars(ViewSession session, RenderContextImpl renderContext) {
        Set<Character> unbound = new LinkedHashSet<>();
        for (String row : session.effectiveConfig().layout()) {
            for (int column = 0; column < row.length(); column++) {
                char character = row.charAt(column);
                if (character != ' ') {
                    unbound.add(character);
                }
            }
        }
        if (unbound.isEmpty()) {
            return;
        }
        unbound.removeAll(renderContext.boundLayoutChars());
        for (StateToken token : session.registered().instance().tokenTable().tokens()) {
            if (!(token instanceof PaginationImpl)) {
                continue;
            }
            PaginationSpec<?> spec = ((PaginationImpl<?>) token).spec();
            if (spec.target() == PaginationSpec.Target.LAYOUT_CHAR) {
                unbound.remove(spec.layoutChar());
            }
        }
        if (unbound.isEmpty()) {
            return;
        }
        Class<?> viewClass = session.registered().type();
        if (UNBOUND_CHAR_WARNED.add(viewClass)) {
            LOGGER.warning("view " + viewClass.getName() + " declares layout chars " + unbound
                    + " that are bound to neither a component nor pagination");
        }
    }

    private void startScheduledUpdates(ViewSession session) {
        long interval = session.effectiveConfig().updateIntervalTicks();
        if (interval <= 0) {
            return;
        }
        PlatformTask task = engine.scheduler().runOnEntityAtFixedRate(session.player(),
                new ViewUpdateTask(engine, session), null, interval, interval);
        session.updateTask(task);
    }
}

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

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.HandlerInvoker;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.SlotClickContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Click policy and routing (spec §6): pre-cancel from config and per-component override,
 * handler dispatch with last-writer-wins cancellation, the safety-floor actions
 * force-cancelled after handlers, and end-of-tick post-actions. Constructed and invoked
 * only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class ClickRoutingPhase {

    private static final Logger LOGGER = Logger.getLogger(ClickRoutingPhase.class.getName());

    private final ViewEngine engine;

    /**
     * Creates the phase.
     *
     * @param engine the owning engine
     */
    public ClickRoutingPhase(@NotNull ViewEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Routes a Bukkit click event into the session per the click policy.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void route(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        // clicks on a non-ACTIVE session (OPENING/TRANSITIONING/CLOSED) are swallowed
        if (session.status() != ViewSession.Status.ACTIVE) {
            event.setCancelled(true);
            return;
        }
        Inventory inventory = session.inventory();
        if (inventory == null) {
            // defensive: an ACTIVE session always has its container; swallow if not
            event.setCancelled(true);
            return;
        }

        int topSize = inventory.getSize();
        boolean bottom = event.getRawSlot() >= topSize;
        InventoryAction action = event.getAction();
        // safety floor (§6): these item movements are force-cancelled after handlers,
        // regardless of any setCancelled(false) decision
        boolean forced = (bottom && action == InventoryAction.MOVE_TO_OTHER_INVENTORY)
                || action == InventoryAction.COLLECT_TO_CURSOR
                || ((action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD)
                && event.getRawSlot() < topSize);

        ComponentInstance component = bottom ? null
                : session.components().componentAt(event.getRawSlot());
        if (!bottom && component == null) {
            // pagination page elements live in the per-token bindings, not the static table
            component = PaginationBindings.componentAt(session, event.getRawSlot());
        }
        SlotClickContextImpl ctx = new SlotClickContextImpl(session, engine, event, bottom,
                preCancel(session, component, bottom) || forced);
        if (component != null && !component.isVisible(ctx)) {
            // hidden components get no clicks; the slot degrades to component-less and
            // the pre-cancel decision falls back to the config default
            component = null;
            ctx = new SlotClickContextImpl(session, engine, event, bottom,
                    preCancel(session, null, bottom) || forced);
        }

        try {
            dispatch(session, component, ctx, event);
        } finally {
            event.setCancelled(forced || ctx.isCancelled());
        }
    }

    // pre-cancel policy: bottom always pre-cancelled; component override beats config
    private static boolean preCancel(ViewSession session, @Nullable ComponentInstance component,
                                     boolean bottom) {
        if (bottom) {
            return true;
        }
        if (component != null && component.cancelOnClick() != null) {
            return component.cancelOnClick();
        }
        return session.effectiveConfig().cancelOnClick();
    }

    // component handler, then view-level onClick, then deferred post-actions; a throw
    // anywhere force-cancels and skips everything remaining (§6, §9)
    private void dispatch(ViewSession session, @Nullable ComponentInstance component,
                          SlotClickContextImpl ctx, InventoryClickEvent event) {
        View view = session.registered().instance();
        try {
            if (component != null) {
                Consumer<SlotClickContext> handler = component.handlerFor(event.getClick());
                if (handler != null) {
                    handler.accept(ctx);
                }
            }
            // the collect-to-cursor double-click is Minecraft's synthetic second event of a
            // fast double-tap; it must not re-fire the view-level onClick, or the click is
            // handled twice (component handlers are filtered in ComponentInstance.handlerFor)
            if (event.getClick() != ClickType.DOUBLE_CLICK) {
                HandlerInvoker.invoke(HandlerInvoker.ON_CLICK, view, ctx);
            }
            if (component != null) {
                queuePostActions(session, component);
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "click handler failed for view " + view.getClass().getName()
                    + " at raw slot " + event.getRawSlot(), ex);
            ctx.setCancelled(true);
        } catch (Error error) {
            // even under abnormal JVM conditions the click must stay cancelled at this boundary
            ctx.setCancelled(true);
            throw error;
        }
    }

    private void queuePostActions(ViewSession session, ComponentInstance component) {
        if (component.closeOnClick()) {
            engine.defer(session, () -> engine.close(session, CloseReason.API));
        }
        Class<? extends View> target = component.openOnClickTarget();
        if (target != null) {
            engine.defer(session, () -> engine.open(session.player(), target,
                    component.openOnClickArguments()));
        }
    }
}

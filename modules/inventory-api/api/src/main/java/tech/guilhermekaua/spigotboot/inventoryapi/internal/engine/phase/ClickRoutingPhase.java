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

import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Click policy and routing (spec §6). Skeleton in this task; the body lands in plan
 * task 14. Constructed and invoked only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class ClickRoutingPhase {

    // stored for the task 14 implementation
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
        throw new UnsupportedOperationException("implemented in Task 14");
    }
}

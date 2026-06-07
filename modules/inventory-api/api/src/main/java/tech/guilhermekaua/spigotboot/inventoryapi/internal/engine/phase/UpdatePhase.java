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
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;
import java.util.Set;

/**
 * Update pass: {@code onUpdate} dispatch plus full or dirty-scoped component repaint.
 * Skeleton in this task; the body lands in plan task 16. Constructed and invoked only by
 * {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class UpdatePhase {

    // stored for the task 16 implementation
    private final ViewEngine engine;

    /**
     * Creates the phase.
     *
     * @param engine the owning engine
     */
    public UpdatePhase(@NotNull ViewEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Runs one update pass over a session.
     *
     * @param session     the session to update
     * @param trigger     the cause of the update
     * @param dirtyOrNull the dirty token ids driving a state-change pass, or {@code null}
     *                    for a full pass
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger,
                       @Nullable Set<Integer> dirtyOrNull) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }
}

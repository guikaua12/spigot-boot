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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.schedule;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Per-session scheduled update runnable, started by the first-render phase when
 * {@code updateIntervalTicks > 0} and cancelled by the close phase.
 */
@ApiStatus.Internal
public final class ViewUpdateTask implements Runnable {

    private final ViewEngine engine;
    private final ViewSession session;

    /**
     * Creates the task for one session.
     *
     * @param engine  the engine running the update pass
     * @param session the session to update on every tick of the timer
     */
    public ViewUpdateTask(@NotNull ViewEngine engine, @NotNull ViewSession session) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public void run() {
        // a timer tick racing the close (or a transitioning session) must not update
        if (!session.isActive()) {
            return;
        }
        engine.update(session, UpdateTrigger.SCHEDULED);
    }
}

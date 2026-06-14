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

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

/**
 * Shared OPEN_FAILED abort path of the open pipeline: tears the failed session down through
 * the close phase and closes the player's screen when the commit point already replaced a
 * previous session, leaving no session protecting the visible container (spec §9). Used by
 * {@link FirstRenderPhase} and {@link PaginationInitPhase}.
 */
final class OpenFailureHandler {

    private OpenFailureHandler() {
    }

    /**
     * Aborts a failed open: closes the session with {@link CloseReason#OPEN_FAILED} and
     * closes the player's screen unless another session took over in the meantime.
     *
     * @param engine   the engine performing the close
     * @param sessions the per-player session registry
     * @param session  the session whose open failed; never registered by an aborted open
     */
    static void abort(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions,
                      @NotNull ViewSession session) {
        // teardown through the close phase; the session was never registered
        engine.close(session, CloseReason.OPEN_FAILED);
        // REPLACED -> OPEN_FAILED dead container: the commit point already closed the
        // previous session, so the player may still be staring at its container with no
        // session protecting it (free item theft); close the screen unless another
        // session took over in the meantime
        if (session.player().isOnline() && !sessions.find(session.player().getUniqueId()).isPresent()) {
            session.player().closeInventory();
        }
    }
}

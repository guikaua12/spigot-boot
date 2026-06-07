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

import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.CloseContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session teardown: idempotent on closed sessions, cancels the update task, marks the
 * session closed, runs {@code onClose} (a throw is logged, teardown always completes),
 * unregisters the session and drops pending deferred operations. Constructed and invoked
 * only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class ClosePhase {

    private static final Logger LOGGER = Logger.getLogger(ClosePhase.class.getName());

    private final ViewEngine engine;
    private final SessionRegistry sessions;

    /**
     * Creates the phase.
     *
     * @param engine   the owning engine
     * @param sessions the per-player session registry
     */
    public ClosePhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Closes a session with the given reason; closing an already closed session is a no-op.
     *
     * @param session the session to close
     * @param reason  the close reason exposed to {@code onClose}
     */
    public void close(@NotNull ViewSession session, @NotNull CloseReason reason) {
        if (session.status() == ViewSession.Status.CLOSED) {
            return;
        }
        BukkitTask updateTask = session.updateTask();
        if (updateTask != null) {
            updateTask.cancel();
            session.updateTask(null);
        }
        session.status(ViewSession.Status.CLOSED);

        View view = session.registered().instance();
        CloseContextImpl closeContext = new CloseContextImpl(session, engine, reason);
        try {
            invokeOnClose(view, closeContext);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onClose failed for view " + view.getClass().getName()
                    + "; teardown continues", ex);
        }

        sessions.unregister(session);
        session.deferredOps().clear();
    }

    // onClose is protected on the public View type; the phase dispatches reflectively
    private static void invokeOnClose(View view, CloseContext context) {
        try {
            Method method = View.class.getDeclaredMethod("onClose", CloseContext.class);
            method.setAccessible(true);
            method.invoke(view, context);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onClose failed for view " + view.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onClose for view " + view.getClass().getName(), ex);
        }
    }
}

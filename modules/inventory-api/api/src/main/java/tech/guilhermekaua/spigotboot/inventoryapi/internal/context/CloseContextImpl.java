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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Context for {@code View.onClose}: the session is tearing down. {@link #update()} and
 * {@link #updateTitle} throw, {@link #close()} is a no-op, and {@link #openView} is dropped
 * with a SEVERE log (§5.4).
 */
@ApiStatus.Internal
public final class CloseContextImpl extends AbstractViewContext implements CloseContext {

    private static final Logger LOGGER = Logger.getLogger(CloseContextImpl.class.getName());

    private final CloseReason reason;

    /**
     * Creates the context for one teardown.
     *
     * @param session the closing session
     * @param engine  the engine executing the close
     * @param reason  why the session is closing
     */
    public CloseContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine,
                            @NotNull CloseReason reason) {
        super(session, engine);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public @NotNull CloseReason reason() {
        return reason;
    }

    @Override
    public void update() {
        throw new IllegalStateException("update() is not allowed during onClose; the session is tearing down");
    }

    @Override
    public void close() {
        // no-op: the session is already closing
    }

    @Override
    public void updateTitle(@NotNull String title) {
        throw new IllegalStateException(
                "updateTitle() is not allowed during onClose; the session is tearing down");
    }

    @Override
    public void openView(@NotNull Class<? extends View> target) {
        openView(target, ViewArguments.empty());
    }

    @Override
    public void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments) {
        // navigation inside onClose is dropped, never deferred or executed (§5.4)
        LOGGER.severe("openView(" + target.getName() + ") called inside onClose of "
                + view().getClass().getName() + "; navigation during close is dropped");
    }
}

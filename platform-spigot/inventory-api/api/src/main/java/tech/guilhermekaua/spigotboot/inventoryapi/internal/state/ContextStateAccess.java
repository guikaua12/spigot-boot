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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;

/**
 * Resolver from a public {@link ViewContext} to its state backing; token impls never cast
 * to concrete context classes, only to {@link StateBackedContext}. Public so token
 * implementations outside this package (the pagination token) can run the shared
 * owner/liveness guard via {@link #storeFor(ViewContext, View)}.
 */
@ApiStatus.Internal
public final class ContextStateAccess {

    private ContextStateAccess() {
    }

    /**
     * Resolves the state store of a context after validating that the context belongs to
     * the given owning view and is still open — the single guard shared by every
     * per-context token implementation.
     *
     * @param context the context a token was invoked with
     * @param owner   the view declaring the token
     * @return the backing state store
     * @throws StaleContextException when the context belongs to another view, is already
     *                               closed, or carries no state backing
     */
    public static @NotNull StateStore storeFor(@NotNull ViewContext context, @NotNull View owner) {
        View contextOwner = ownerOf(context);
        if (contextOwner != owner) {
            throw new StaleContextException("state token of " + owner.getClass().getName()
                    + " used with a context of " + contextOwner.getClass().getName());
        }
        if (!isActive(context)) {
            throw new StaleContextException("context of " + owner.getClass().getName() + " is closed");
        }
        return storeOf(context);
    }

    /**
     * Resolves the state store of a context.
     *
     * @param context the context to resolve
     * @return the backing state store
     * @throws StaleContextException when the context carries no state backing
     */
    static @NotNull StateStore storeOf(@NotNull ViewContext context) {
        return backed(context).stateStore();
    }

    /**
     * Resolves the view owning a context.
     *
     * @param context the context to resolve
     * @return the owning view
     * @throws StaleContextException when the context carries no state backing
     */
    static @NotNull View ownerOf(@NotNull ViewContext context) {
        return backed(context).owner();
    }

    /**
     * Resolves whether a context may still access state.
     *
     * @param context the context to resolve
     * @return {@code true} while state access is legal
     * @throws StaleContextException when the context carries no state backing
     */
    static boolean isActive(@NotNull ViewContext context) {
        return backed(context).contextActive();
    }

    private static StateBackedContext backed(ViewContext context) {
        if (!(context instanceof StateBackedContext)) {
            throw new StaleContextException("context " + context.getClass().getName()
                    + " does not expose state storage");
        }
        return (StateBackedContext) context;
    }
}

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

/**
 * Seam between state token impls and context implementations: a context exposing the
 * session's state store, owning view and liveness. Implemented by
 * {@code AbstractViewContext} in the internal context layer (plan task 11); tests may
 * implement it directly against a real {@link StateStore}.
 */
@ApiStatus.Internal
public interface StateBackedContext {

    /**
     * Returns the per-session state storage backing this context.
     *
     * @return the state store
     */
    @NotNull StateStore stateStore();

    /**
     * Returns the view singleton owning this context, used for foreign-token checks.
     *
     * @return the owning view
     */
    @NotNull View owner();

    /**
     * Returns whether this context may still access state; {@code false} once closed.
     *
     * @return {@code true} while state access is legal
     */
    boolean contextActive();
}

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
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Context for {@code View.onOpen}: runs before any container exists.
 *
 * <p>Phase validity: {@code inventory()}, {@code update()} and {@code updateTitle(String)}
 * throw {@link IllegalStateException} naming this phase. Cancelling aborts the open with
 * zero side effects — the player's current view, if any, stays untouched and protected.
 * {@code close()} is valid but redundant here — prefer {@link #cancelOpen()}, which
 * preserves the player's current view intact.
 */
@ApiStatus.NonExtendable
public interface OpenContext extends ViewContext {

    /**
     * Overrides the configured title for this open only.
     *
     * @param title the per-open title, legacy color codes supported
     */
    void overrideTitle(@NotNull String title);

    /**
     * Overrides the configured row count for this open only.
     *
     * @param rows the per-open row count, 1 to 6
     */
    void overrideRows(int rows);

    /**
     * Aborts this open with zero side effects; the player's current view is untouched.
     */
    void cancelOpen();

    /**
     * Returns whether {@link #cancelOpen()} was called for this open.
     *
     * @return {@code true} when this open is cancelled
     */
    boolean isOpenCancelled();
}

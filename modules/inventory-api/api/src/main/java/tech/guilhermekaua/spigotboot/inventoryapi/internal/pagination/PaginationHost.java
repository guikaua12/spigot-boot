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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.List;
import java.util.UUID;

/**
 * The world-facing seam the relocated pagination engine talks to instead of the 2.x
 * {@code Viewer}/{@code InventoryEditor} pair. Implemented per (session, token) by the
 * pagination binding, so a view declaring several pagination tokens keeps {@code fillPage}
 * and {@code requestRender} disambiguated per token.
 */
@ApiStatus.Internal
public interface PaginationHost {

    /**
     * Returns whether the bound session is currently paintable.
     *
     * @return {@code true} while the session status is ACTIVE or TRANSITIONING
     */
    boolean isActive();

    /**
     * Applies one engine frame: item {@code i} is mapped onto {@code layout.slots().get(i)}.
     *
     * @param items  the rendered slot contents, one per layout slot
     * @param layout the fill order the items map onto
     */
    void fillPage(@NotNull List<RenderedItem> items, @NotNull Layout layout);

    /**
     * Requests a reactive settle pass for the owning token: the engine repaints the token's
     * pagination area and every component watching the token.
     */
    void requestRender();

    /**
     * Returns the player pages are being loaded for; populates the slim {@code PageRequest}.
     *
     * @return the viewing player's id; production hosts never return null (test fixtures may)
     */
    @Nullable UUID playerId();

    /**
     * Returns the plugin that owns the view; asynchronously completed settles are scheduled
     * through it.
     *
     * @return the owning plugin
     */
    @NotNull Plugin plugin();
}

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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable description of one page load issued by a paginator; safe to share across threads.
 *
 * <p>{@code offset} and {@code pageSize} are the <strong>authoritative query
 * bounds</strong> — a backing store should fetch with {@code LIMIT pageSize OFFSET offset}.
 * {@code page} is informational only: scroll paginators advance one element per page, so
 * deriving the offset as {@code (page - 1) * pageSize} is wrong for them.
 *
 * <p>{@code playerId} identifies who the page is loaded for and {@code plugin} owns the load.
 * {@code viewer} is the live {@link Player} the settle dispatcher uses to route the settle to
 * the viewer's region thread (Folia) or the main thread (legacy). All three are {@code null}
 * only for engine-external test usage — requests dispatched by the built-in paginators always
 * populate them.
 */
public final class PageRequest {

    private final int page;
    private final int pageSize;
    private final int offset;
    private final UUID playerId;
    private final Plugin plugin;
    private final Player viewer;

    /**
     * Creates a page request.
     *
     * @param page     the 1-indexed page being requested; informational only
     * @param pageSize the maximum number of items the requested page can display
     * @param offset   the global element offset of the first item on the requested page
     * @param playerId the player the page is being loaded for, or {@code null} only for
     *                 engine-external test usage
     * @param plugin   the plugin owning the load, or {@code null} only for engine-external
     *                 test usage
     * @param viewer   the live {@link Player} the settle dispatcher routes the settle for;
     *                 {@code null} only for engine-external test usage, in which case the
     *                 settle runs inline on the completing thread
     */
    public PageRequest(int page, int pageSize, int offset, @Nullable UUID playerId,
                       @Nullable Plugin plugin, @Nullable Player viewer) {
        this.page = page;
        this.pageSize = pageSize;
        this.offset = offset;
        this.playerId = playerId;
        this.plugin = plugin;
        this.viewer = viewer;
    }

    /**
     * Returns the 1-indexed page being requested. Informational only — scroll paginators
     * slide one element per page, so the offset must never be derived from it.
     *
     * @return the 1-indexed page
     */
    public int getPage() {
        return page;
    }

    /**
     * Returns the maximum number of items the requested page can display.
     *
     * @return the page size
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Returns the global element offset of the first item on the requested page — together
     * with {@link #getPageSize()} the authoritative query bound: {@code LIMIT pageSize
     * OFFSET offset}.
     *
     * @return the element offset
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Returns the unique id of the player the page is being loaded for.
     *
     * @return the player id, or {@code null} only for engine-external test usage
     */
    public @Nullable UUID playerId() {
        return playerId;
    }

    /**
     * Returns the plugin that owns the load. Informational: the built-in
     * {@link BukkitSettleDispatcher} routes settles by {@link #viewer()} via the platform
     * scheduler and does not use this; a custom {@link SettleDispatcher} may use it.
     *
     * @return the owning plugin, or {@code null} only for engine-external test usage
     */
    public @Nullable Plugin plugin() {
        return plugin;
    }

    /**
     * Returns the live viewer the settle dispatcher uses to route the settle to the correct
     * region thread. On Folia each player's inventory is owned by the region containing the
     * player; the settle must run on that region's thread. On legacy Spigot/Paper the
     * dispatcher falls back to the main thread.
     *
     * @return the viewing player, or {@code null} only for engine-external test usage
     *         (in which case the settle runs inline on the completing thread)
     */
    public @Nullable Player viewer() {
        return viewer;
    }
}

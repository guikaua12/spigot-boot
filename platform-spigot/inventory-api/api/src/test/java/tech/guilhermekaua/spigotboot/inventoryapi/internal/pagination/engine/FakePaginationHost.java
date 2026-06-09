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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationHost;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test double for {@link PaginationHost}: records every {@code fillPage} invocation as an
 * (items, layout) pair, counts {@code requestRender} calls and exposes a configurable
 * active flag. Shared by the relocated-engine test classes.
 */
final class FakePaginationHost implements PaginationHost {

    /**
     * One recorded {@code fillPage} invocation: the items and the layout they were mapped onto.
     */
    static final class FillPageCall {
        final List<RenderedItem> items;
        final Layout layout;

        FillPageCall(List<RenderedItem> items, Layout layout) {
            this.items = items;
            this.layout = layout;
        }
    }

    private final List<FillPageCall> fillPageCalls = new ArrayList<>();
    private final UUID playerId;
    private final Plugin plugin;
    private boolean active = true;
    private int requestRenderCount;

    FakePaginationHost(UUID playerId, Plugin plugin) {
        this.playerId = playerId;
        this.plugin = plugin;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void fillPage(@NotNull List<RenderedItem> items, @NotNull Layout layout) {
        fillPageCalls.add(new FillPageCall(new ArrayList<>(items), layout));
    }

    @Override
    public void requestRender() {
        requestRenderCount++;
    }

    @Override
    public @Nullable UUID playerId() {
        return playerId;
    }

    @Override
    public @NotNull Plugin plugin() {
        return plugin;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    List<FillPageCall> fillPageCalls() {
        return fillPageCalls;
    }

    FillPageCall lastFillPage() {
        if (fillPageCalls.isEmpty()) {
            throw new AssertionError("no fillPage call was recorded");
        }
        return fillPageCalls.get(fillPageCalls.size() - 1);
    }

    int requestRenderCount() {
        return requestRenderCount;
    }
}

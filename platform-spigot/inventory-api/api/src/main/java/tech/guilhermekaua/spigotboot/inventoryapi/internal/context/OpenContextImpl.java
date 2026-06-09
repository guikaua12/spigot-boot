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

import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Context for {@code View.onOpen}: records per-open overrides and the cancel decision.
 * No container exists yet, so {@link #inventory()}, {@link #update()} and
 * {@link #updateTitle(String)} throw {@link IllegalStateException} naming the phase.
 */
@ApiStatus.Internal
public final class OpenContextImpl extends AbstractViewContext implements OpenContext {

    private String overriddenTitle;
    private Integer overriddenRows;
    private boolean openCancelled;

    /**
     * Creates the context for one open attempt.
     *
     * @param session the session being opened
     * @param engine  the engine executing the open
     */
    public OpenContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine) {
        super(session, engine);
    }

    @Override
    public void overrideTitle(@NotNull String title) {
        this.overriddenTitle = Objects.requireNonNull(title, "title");
    }

    @Override
    public void overrideRows(int rows) {
        this.overriddenRows = rows;
    }

    @Override
    public void cancelOpen() {
        this.openCancelled = true;
    }

    @Override
    public boolean isOpenCancelled() {
        return openCancelled;
    }

    /**
     * Returns the per-open title override recorded in {@code onOpen}.
     *
     * @return the overridden title, or {@code null} when none was recorded
     */
    public @Nullable String overriddenTitle() {
        return overriddenTitle;
    }

    /**
     * Returns the per-open rows override recorded in {@code onOpen}.
     *
     * @return the overridden row count, or {@code null} when none was recorded
     */
    public @Nullable Integer overriddenRows() {
        return overriddenRows;
    }

    @Override
    public @NotNull Inventory inventory() {
        throw new IllegalStateException(
                "inventory() is not available during onOpen; the container is created after onOpen completes");
    }

    @Override
    public void update() {
        throw new IllegalStateException(
                "update() is not available during onOpen; the first paint happens after onOpen completes");
    }

    @Override
    public void updateTitle(@NotNull String title) {
        throw new IllegalStateException(
                "updateTitle(String) is not available during onOpen; use overrideTitle(String) instead");
    }
}

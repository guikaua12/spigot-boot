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
package tech.guilhermekaua.spigotboot.inventoryapi.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, validated view configuration produced by {@link ViewConfigBuilder#build()};
 * frozen at registration and merged with per-open overrides via {@link #withOverrides}.
 */
public final class ViewConfig {

    private final String title;
    private final int rows;
    private final List<String> layout;
    private final boolean cancelOnClick;
    private final boolean cancelOnDrag;
    private final long updateIntervalTicks;
    private final boolean applyPlaceholders;

    ViewConfig(@NotNull String title, int rows, @NotNull List<String> layout,
               boolean cancelOnClick, boolean cancelOnDrag,
               long updateIntervalTicks, boolean applyPlaceholders) {
        this.title = title;
        this.rows = rows;
        this.layout = layout;
        this.cancelOnClick = cancelOnClick;
        this.cancelOnDrag = cancelOnDrag;
        this.updateIntervalTicks = updateIntervalTicks;
        this.applyPlaceholders = applyPlaceholders;
    }

    /**
     * Returns the inventory title (legacy color codes allowed).
     *
     * @return the title, never {@code null}
     */
    public @NotNull String title() {
        return title;
    }

    /**
     * Returns the resolved row count, explicit or inferred from the layout.
     *
     * @return the row count within 1-6
     */
    public int rows() {
        return rows;
    }

    /**
     * Returns the layout rows declared in {@code onInit}.
     *
     * @return an unmodifiable list of layout rows; empty when no layout was set
     */
    public @NotNull List<String> layout() {
        return layout;
    }

    /**
     * Returns whether clicks are cancelled by default (default {@code true}).
     *
     * @return the cancel-on-click default
     */
    public boolean cancelOnClick() {
        return cancelOnClick;
    }

    /**
     * Returns whether drags are cancelled by default (default {@code true}).
     *
     * @return the cancel-on-drag default
     */
    public boolean cancelOnDrag() {
        return cancelOnDrag;
    }

    /**
     * Returns the scheduled update interval in ticks; {@code 0} means disabled.
     *
     * @return the update interval in ticks
     */
    public long updateIntervalTicks() {
        return updateIntervalTicks;
    }

    /**
     * Returns whether placeholders are applied at paint time (default {@code true}).
     *
     * @return the apply-placeholders flag
     */
    public boolean applyPlaceholders() {
        return applyPlaceholders;
    }

    /**
     * Returns a copy of this config with the given non-null per-open overrides applied;
     * this instance is unchanged.
     *
     * @param title the title override, or {@code null} to keep the current title
     * @param rows  the rows override, or {@code null} to keep the current rows
     * @return a new config with the overrides applied
     * @throws ViewConfigurationException if a non-null rows override is outside 1-6
     */
    public @NotNull ViewConfig withOverrides(@Nullable String title, @Nullable Integer rows) {
        if (rows != null) {
            validateRows(rows);
        }
        return new ViewConfig(
                title != null ? title : this.title,
                rows != null ? rows : this.rows,
                Collections.unmodifiableList(new ArrayList<>(this.layout)),
                this.cancelOnClick,
                this.cancelOnDrag,
                this.updateIntervalTicks,
                this.applyPlaceholders
        );
    }

    /**
     * Validates that rows is between 1 and 6 (inclusive).
     *
     * @param rows the row count to validate
     * @throws ViewConfigurationException if rows is outside 1-6
     */
    static void validateRows(int rows) {
        if (rows < 1 || rows > 6) {
            throw new ViewConfigurationException("rows must be between 1 and 6, but was " + rows);
        }
    }
}

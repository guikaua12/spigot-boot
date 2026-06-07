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
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Fluent, mutable builder for {@link ViewConfig}, used in {@code View.onInit};
 * {@link #build()} validates and freezes the configuration.
 */
public final class ViewConfigBuilder {

    private String title;
    private Integer rows;
    private List<String> layout = Collections.emptyList();
    private boolean cancelOnClick = true;
    private boolean cancelOnDrag = true;
    private long updateIntervalTicks = 0L;
    private boolean applyPlaceholders = true;

    /**
     * Sets the inventory title (legacy color codes allowed).
     *
     * @param title the title
     * @return this builder
     */
    public @NotNull ViewConfigBuilder title(@NotNull String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    /**
     * Sets the explicit row count; optional when a layout is present (inferred).
     *
     * @param rows the row count, validated to 1-6 at {@link #build()}
     * @return this builder
     */
    public @NotNull ViewConfigBuilder rows(int rows) {
        this.rows = rows;
        return this;
    }

    /**
     * Sets the layout rows; each row must be exactly {@link Layout#ROW_WIDTH} characters,
     * validated at {@link #build()}.
     *
     * @param rows the layout rows
     * @return this builder
     */
    public @NotNull ViewConfigBuilder layout(@NotNull String... rows) {
        Objects.requireNonNull(rows, "rows");
        this.layout = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(rows)));
        return this;
    }

    /**
     * Sets whether clicks are cancelled by default (default {@code true}).
     *
     * @param cancel the cancel-on-click default
     * @return this builder
     */
    public @NotNull ViewConfigBuilder cancelOnClick(boolean cancel) {
        this.cancelOnClick = cancel;
        return this;
    }

    /**
     * Sets whether drags are cancelled by default (default {@code true}).
     *
     * @param cancel the cancel-on-drag default
     * @return this builder
     */
    public @NotNull ViewConfigBuilder cancelOnDrag(boolean cancel) {
        this.cancelOnDrag = cancel;
        return this;
    }

    /**
     * Schedules periodic updates; values {@code <= 0} disable scheduling (the default).
     *
     * @param intervalTicks the update interval in ticks
     * @return this builder
     */
    public @NotNull ViewConfigBuilder scheduleUpdate(long intervalTicks) {
        this.updateIntervalTicks = intervalTicks <= 0 ? 0L : intervalTicks;
        return this;
    }

    /**
     * Sets whether placeholders are applied at paint time (default {@code true}).
     *
     * @param apply the apply-placeholders flag
     * @return this builder
     */
    public @NotNull ViewConfigBuilder applyPlaceholders(boolean apply) {
        this.applyPlaceholders = apply;
        return this;
    }

    /**
     * Validates and freezes the configuration.
     *
     * @return the immutable config
     * @throws ViewConfigurationException if the title is missing, rows are outside 1-6,
     *                                    a layout row is not exactly {@link Layout#ROW_WIDTH} characters wide,
     *                                    rows and layout are inconsistent, or neither rows nor layout is set
     */
    public @NotNull ViewConfig build() {
        if (title == null) {
            throw new ViewConfigurationException("view title is required");
        }
        if (rows != null && (rows < 1 || rows > 6)) {
            throw new ViewConfigurationException("rows must be between 1 and 6, got " + rows);
        }
        for (int i = 0; i < layout.size(); i++) {
            String row = layout.get(i);
            if (row.length() != Layout.ROW_WIDTH) {
                throw new ViewConfigurationException(
                        "layout row " + i + " must be exactly " + Layout.ROW_WIDTH
                                + " characters wide, but was " + row.length()
                );
            }
        }
        if (rows == null && layout.isEmpty()) {
            throw new ViewConfigurationException("either rows or layout must be set");
        }
        if (rows != null && !layout.isEmpty() && rows != layout.size()) {
            throw new ViewConfigurationException(
                    "rows (" + rows + ") does not match layout height (" + layout.size() + ")"
            );
        }

        int resolvedRows = rows != null ? rows : layout.size();
        return new ViewConfig(title, resolvedRows, layout, cancelOnClick, cancelOnDrag,
                updateIntervalTicks, applyPlaceholders);
    }
}

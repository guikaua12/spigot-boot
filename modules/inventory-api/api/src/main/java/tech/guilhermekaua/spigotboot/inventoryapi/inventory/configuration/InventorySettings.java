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
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Mutable, fluent description of an inventory's identity and behaviour, populated by a
 * subclass through
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl#configure}.
 *
 * <p>Tick options delegate to the wrapped {@link InventoryConfiguration}, so this type never has
 * to mirror new configuration fields.
 */
public final class InventorySettings {

    private final InventoryConfiguration configuration;
    private String title;
    private int size;
    private int rows;

    /**
     * Creates settings that write tick options through to the given configuration.
     *
     * @param configuration the inventory's configuration instance, not null
     */
    public InventorySettings(@NotNull InventoryConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration cannot be null.");
    }

    /**
     * Sets the inventory title.
     *
     * @param title the title; legacy colour codes are allowed
     * @return this, for chaining
     */
    public InventorySettings title(@NotNull String title) {
        this.title = Objects.requireNonNull(title, "title cannot be null.");
        return this;
    }

    /**
     * Sets the inventory size in slots (a positive multiple of nine).
     *
     * @param size the slot count
     * @return this, for chaining
     */
    public InventorySettings size(int size) {
        this.size = size;
        return this;
    }

    /**
     * Sets the inventory height in rows; each row spans nine slots.
     *
     * @param rows the row count, between 1 and 6 (inclusive)
     * @return this, for chaining
     * @throws IllegalArgumentException if {@code rows} is less than 1 or greater than 6
     */
    public InventorySettings rows(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6, got " + rows);
        }
        this.rows = rows;
        return this;
    }

    /**
     * Sets the periodic update tick rate.
     *
     * @param ticks the tick interval, or a value {@code <= 0} to disable periodic updates
     * @return this, for chaining
     */
    public InventorySettings tickUpdate(int ticks) {
        this.configuration.tickUpdate(ticks);
        return this;
    }

    /**
     * Sets whether the periodic update runs off the main server thread.
     *
     * @param async {@code true} to dispatch periodic updates asynchronously
     * @return this, for chaining
     */
    public InventorySettings tickAsync(boolean async) {
        this.configuration.tickAsync(async);
        return this;
    }

    /**
     * Returns the configured title, or {@code null} if {@link #title} has not been called.
     *
     * @return the inventory title, or {@code null}
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the configured size in slots, or {@code 0} if {@link #size} has not been called.
     *
     * @return the slot count
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the configured row count, or {@code 0} if {@link #rows} has not been called.
     *
     * @return the row count
     */
    public int getRows() {
        return rows;
    }

    /**
     * Returns the wrapped configuration that tick options delegate to.
     *
     * @return the configuration supplied at construction, never null
     */
    @NotNull
    public InventoryConfiguration getConfiguration() {
        return configuration;
    }
}

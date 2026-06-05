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
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder;

import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.GenericInventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.impl.PatternPagination;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds {@link PatternPagination} instances from one or more {@link InventoryLayout} patterns.
 *
 * <p>The paginator cycles through the supplied patterns as the viewer pages forward and fills each
 * page's slots sequentially from the source, so every item is shown exactly once regardless of the
 * pattern shape. Each {@link InventoryLayout} defines its own placement order (grid letters or
 * explicit slot indices); see {@link PatternPagination} for details.
 */
public class PatternPaginationBuilder<T> {
    private InventoryItemSupplier fallbackItem;
    private GenericInventoryItemSupplier<T> itemFactory;
    private List<InventoryLayout> patterns;

    public static <T> PatternPaginationBuilder<T> builder() {
        return new PatternPaginationBuilder<>();
    }

    public PatternPaginationBuilder<T> fallbackItem(InventoryItemSupplier fallbackItem) {
        this.fallbackItem = fallbackItem;
        return this;
    }

    public PatternPaginationBuilder<T> itemFactory(GenericInventoryItemSupplier<T> itemSupplier) {
        this.itemFactory = itemSupplier;
        return this;
    }

    public PatternPaginationBuilder<T> pattern(InventoryLayout pattern) {
        if (this.patterns == null) {
            this.patterns = new ArrayList<>();
        }
        this.patterns.add(pattern);
        return this;
    }

    public PatternPaginationBuilder<T> patterns(List<InventoryLayout> patterns) {
        this.patterns = new ArrayList<>(patterns);
        return this;
    }

    public PatternPagination<T> build() {
        Objects.requireNonNull(this.itemFactory, "itemFactory is required.");
        Objects.requireNonNull(this.patterns, "patterns are required.");
        if (this.patterns.isEmpty()) {
            throw new IllegalArgumentException("patterns list cannot be empty.");
        }
        for (InventoryLayout pattern : this.patterns) {
            InventoryLayout.requireItemSlots(pattern);
        }

        return new PatternPagination<>(this.fallbackItem, this.itemFactory, new ArrayList<>(this.patterns));
    }
}

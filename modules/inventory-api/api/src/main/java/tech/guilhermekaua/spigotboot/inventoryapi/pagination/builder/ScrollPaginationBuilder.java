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
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.impl.ScrollPagination;

import java.util.Objects;

public class ScrollPaginationBuilder<T> {
    private InventoryItemSupplier fallbackItem;
    private GenericInventoryItemSupplier<T> itemFactory;
    private InventoryLayout layout;

    public static <T> ScrollPaginationBuilder<T> builder() {
        return new ScrollPaginationBuilder<>();
    }

    public ScrollPaginationBuilder<T> fallbackItem(InventoryItemSupplier fallbackItem) {
        this.fallbackItem = fallbackItem;
        return this;
    }

    public ScrollPaginationBuilder<T> itemFactory(GenericInventoryItemSupplier<T> itemSupplier) {
        this.itemFactory = itemSupplier;
        return this;
    }

    public ScrollPaginationBuilder<T> layout(InventoryLayout layout) {
        this.layout = layout;
        return this;
    }

    public ScrollPagination<T> build() {
        Objects.requireNonNull(this.itemFactory, "itemFactory is required.");
        Objects.requireNonNull(this.layout, "layout is required.");

        return new ScrollPagination<>(this.fallbackItem, this.itemFactory, this.layout);
    }
}

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
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.Inventory;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.ScrollPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.property.ViewerPropertyMap;

import java.util.LinkedList;
import java.util.List;

@Inventory
public final class SamplePagedInventory extends CustomInventoryImpl {

    private static final String PAGINATION_KEY = "pagination";
    private static final int[] SOURCE = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
            31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50
    };

    private final ScrollPaginationBuilder<Integer> paginationBuilder = new ScrollPaginationBuilder<Integer>()
            .fallbackItem(viewer -> InventoryItem.of(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)))
            .layout(new InventoryLayout(
                    "         ",
                    "         ",
                    "         ",
                    "  OOOOO  ",
                    "         ",
                    "         "
            ))
            .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.GOLD_INGOT, value)));

    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&aSample Paged Inventory")
                .rows(6)
                .tickUpdate(20);
    }

    @Override
    protected void firstOpen(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        ViewerPropertyMap propertyMap = viewer.getPropertyMap();
        Pagination<Integer> pagination = propertyMap.get(PAGINATION_KEY, paginationBuilder::build);
        pagination.init(viewer);
        pagination.setSource(buildSource());
    }

    @Override
    protected void configureInventory(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        Pagination<Integer> pagination = viewer.getPropertyMap().get(PAGINATION_KEY);
        pagination.apply();

        editor.setItem(27, pagination.hasPreviousPage() ? InventoryItem.of(new ItemStack(Material.ARROW))
                .defaultCallback(click -> {
                    viewer.updateTitle("&ePage " + pagination.getCurrentPage());
                    pagination.previousPage();
                })
                : null);

        editor.setItem(35, pagination.hasNextPage() ? InventoryItem.of(new ItemStack(Material.ARROW))
                .defaultCallback(click -> {
                    viewer.updateTitle("&ePage " + pagination.getCurrentPage());
                    pagination.nextPage();
                })
                : null);
    }

    @Override
    protected void update(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        configureInventory(viewer, editor);
    }

    private List<Integer> buildSource() {
        List<Integer> source = new LinkedList<>();
        for (int value : SOURCE) {
            source.add(value);
        }
        return source;
    }
}

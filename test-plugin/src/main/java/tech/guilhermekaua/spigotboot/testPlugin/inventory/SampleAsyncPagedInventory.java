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
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.NormalPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.property.ViewerPropertyMap;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Demonstrates async pagination: every page is "fetched" with a simulated 1.5s database delay,
 * rendering clocks while loading. The page indicator is rendered from {@link #update}, where the
 * page number is settled — never from click callbacks, where it is stale by design.
 */
@Inventory
public final class SampleAsyncPagedInventory extends CustomInventoryImpl {

    private static final Logger LOGGER = Logger.getLogger(SampleAsyncPagedInventory.class.getName());
    private static final String PAGINATION_KEY = "pagination";
    private static final int TOTAL_ELEMENTS = 50;

    private final NormalPaginationBuilder<Integer> paginationBuilder = new NormalPaginationBuilder<Integer>()
            .layout(InventoryLayout.ofGrid(
                    "         ",
                    " OOOOOOO ",
                    " OOOOOOO ",
                    " OOOOOOO ",
                    "         ",
                    "         "
            ))
            .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.EMERALD, value)))
            .async(options -> options
                    .source(SampleAsyncPagedInventory::loadPage)
                    .loadingItem(viewer -> InventoryItem.of(new ItemStack(Material.CLOCK)))
                    .errorCallback((request, error) ->
                            LOGGER.warning("sample page " + request.getPage() + " failed: " + error))
                    .requestTimeout(Duration.ofSeconds(10))
                    .cacheTtl(Duration.ofSeconds(15)));

    private static CompletableFuture<PageResult<Integer>> loadPage(PageRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<Integer> items = new ArrayList<>();
            int end = Math.min(request.getOffset() + request.getPageSize(), TOTAL_ELEMENTS);
            for (int i = request.getOffset(); i < end; i++) {
                items.add(i + 1);
            }
            return PageResult.of(items, TOTAL_ELEMENTS);
        });
    }

    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&bSample Async Paged Inventory")
                .rows(6)
                .tickUpdate(20);
    }

    @Override
    protected void firstOpen(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        ViewerPropertyMap propertyMap = viewer.getPropertyMap();
        Pagination<Integer> pagination = propertyMap.get(PAGINATION_KEY, paginationBuilder::build);
        pagination.init(viewer);
    }

    @Override
    protected void configureInventory(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        Pagination<Integer> pagination = viewer.getPropertyMap().get(PAGINATION_KEY);
        pagination.apply();

        editor.setItem(45, pagination.hasPreviousPage() ? InventoryItem.of(new ItemStack(Material.ARROW))
                .defaultCallback(click -> pagination.previousPage())
                : null);

        editor.setItem(53, pagination.hasNextPage() ? InventoryItem.of(new ItemStack(Material.ARROW))
                .defaultCallback(click -> pagination.nextPage())
                : null);

        editor.setItem(49, InventoryItem.of(new ItemStack(
                pagination.isLoading() ? Material.CLOCK : Material.PAPER,
                Math.max(1, pagination.getCurrentPage()))));
    }

    @Override
    protected void update(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        configureInventory(viewer, editor);
    }
}

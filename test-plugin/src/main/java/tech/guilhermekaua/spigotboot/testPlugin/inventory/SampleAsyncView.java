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
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Async pagination sample: every page is "fetched" with a simulated 1.5s database delay,
 * rendering clocks into the page area while loading. The {@code 'I'} indicator component
 * watches the pagination token, so it flips between a clock (loading) and the settled page
 * number reactively — the 2.x tick-poll workaround is gone.
 */
@RegisterView
public final class SampleAsyncView extends View {

    private static final Logger LOGGER = Logger.getLogger(SampleAsyncView.class.getName());
    private static final int TOTAL_ELEMENTS = 50;

    private final Pagination<Integer> numbers = paginateAsync(SampleAsyncView::loadPage)
            .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.EMERALD, value)))
            .loadingItem(ctx -> new ItemStack(Material.CLOCK))
            .onError((request, error) -> LOGGER.warning("page " + request.getPage() + " failed: " + error))
            .requestTimeout(Duration.ofSeconds(10))
            .cacheTtl(Duration.ofSeconds(15))
            .build();

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&bSample Async View")
                .layout("         ",
                        " OOOOOOO ",
                        " OOOOOOO ",
                        " OOOOOOO ",
                        "         ",
                        "<   I   >");
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.layoutSlot('<', new ItemStack(Material.ARROW))
                .displayIf(numbers::canBack)
                .updateOnStateChange(numbers)
                .onClick(numbers::back);
        render.layoutSlot('>', new ItemStack(Material.ARROW))
                .displayIf(numbers::canAdvance)
                .updateOnStateChange(numbers)
                .onClick(numbers::advance);
        render.layoutSlot('I')
                .item(ctx -> new ItemStack(numbers.isLoading(ctx) ? Material.CLOCK : Material.PAPER,
                        Math.max(1, numbers.currentPage(ctx))))
                .updateOnStateChange(numbers);
    }

    // simulated database query: pages are sliced from 1..TOTAL_ELEMENTS via offset/pageSize
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
}

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

import java.util.ArrayList;
import java.util.List;

/**
 * Scroll-geometry pagination sample: 50 gold ingots slide one element per page through the
 * five {@code 'O'} layout slots. The navigation arrows are ordinary components bound to the
 * {@code '<'} and {@code '>'} layout characters; they repaint reactively because they watch
 * the pagination token — no scheduled update is needed.
 */
@RegisterView
public final class SampleScrollView extends View {

    private final Pagination<Integer> numbers = paginate(numbersUpTo(50))
            .scroll()
            .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.GOLD_INGOT, value)))
            .fallbackItem(ctx -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE))
            .build();

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&aSample Scroll View")
                .layout("         ",
                        "         ",
                        "         ",
                        "< OOOOO >",
                        "         ",
                        "         ");
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
    }

    private static List<Integer> numbersUpTo(int max) {
        List<Integer> numbers = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}

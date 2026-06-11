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
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;

import java.util.ArrayList;
import java.util.List;

/**
 * Pattern-geometry pagination sample: three per-page slot patterns are cycled — a clockwise
 * ring, an X and a serpentine block — with the letter order of each grid defining the fill
 * order within the page. Slots of the previous pattern that the next one does not reuse are
 * cleared automatically on every page change.
 */
@RegisterView
public final class SamplePatternView extends View {

    private final Pagination<Integer> gems = paginate(numbersUpTo(50))
            .patterns(
                    Layout.ofGrid(
                            "  ABCDE  ",
                            "  L   F  ",
                            "  KJIHG  "),
                    Layout.ofGrid(
                            "  A   B  ",
                            "    C    ",
                            "  D   E  "),
                    Layout.ofGrid(
                            "   ABC   ",
                            "   FED   ",
                            "   GHI   "))
            .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.DIAMOND, value)))
            .build();

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&aSample Pattern View")
                .rows(4);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.slot(27, new ItemStack(Material.ARROW))
                .displayIf(gems::canBack)
                .updateOnStateChange(gems)
                .onClick(gems::back);
        render.slot(35, new ItemStack(Material.ARROW))
                .displayIf(gems::canAdvance)
                .updateOnStateChange(gems)
                .onClick(gems::advance);
    }

    private static List<Integer> numbersUpTo(int max) {
        List<Integer> numbers = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}

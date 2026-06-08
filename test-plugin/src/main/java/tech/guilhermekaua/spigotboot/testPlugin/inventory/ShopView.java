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
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

/**
 * Navigation sample (1 of 2): a tiny shop. Clicking an offer opens {@link ConfirmView},
 * passing the chosen material through {@link ViewArguments} — the v3 replacement for
 * stashing the selection in a {@code ViewerPropertyMap}. Navigation is declarative: the
 * click handler calls {@code ctx.openView(target, args)}, which closes this view with
 * {@code REPLACED} and opens the confirm view at end of tick.
 */
@RegisterView
public final class ShopView extends View {

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&2Shop").rows(1);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.slot(2, new ItemStack(Material.DIAMOND))
                .openOnClick(ConfirmView.class, ViewArguments.of("item", Material.DIAMOND.name()));
        render.slot(4, new ItemStack(Material.EMERALD))
                .openOnClick(ConfirmView.class, ViewArguments.of("item", Material.EMERALD.name()));
        render.slot(6, new ItemStack(Material.GOLD_INGOT))
                .openOnClick(ConfirmView.class, ViewArguments.of("item", Material.GOLD_INGOT.name()));
    }
}

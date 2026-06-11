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
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

/**
 * Navigation sample (2 of 2): the confirm screen for {@link ShopView}. The chosen material
 * arrives as an {@code initialState} token bound from the {@code ViewArguments} the shop
 * passed to {@code openView} — type-checked at the open site. The centre slot renders the
 * selection; "confirm" closes the view, "back" reopens the shop.
 */
@RegisterView
public final class ConfirmView extends View {

    private final MutableState<String> item = initialState("item", String.class);

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&eConfirm purchase").rows(1);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.slot(0, new ItemStack(Material.BARRIER))
                .openOnClick(ShopView.class);
        render.slot(4).item(ctx -> new ItemStack(Material.valueOf(item.get(ctx))));
        render.slot(8, new ItemStack(Material.LIME_WOOL))
                .onClick(ctx -> {
                    ctx.player().sendMessage("[ApxPlugin] - purchased " + item.get(ctx));
                    ctx.close();
                });
    }
}

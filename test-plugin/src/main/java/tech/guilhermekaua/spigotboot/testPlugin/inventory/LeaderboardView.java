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
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import java.util.Arrays;
import java.util.List;

/**
 * SharedState sample: a leaderboard whose top entries are one value shared by every viewer
 * of the view (not per-player). {@code sharedState} holds an immutable list; any thread may
 * {@code set}/{@code update} it, and the framework marshals the watcher repaint to the main
 * thread — so calling {@code topThree.set(...)} from a scoreboard task repaints every open
 * leaderboard at once. Components watch the token via {@code updateOnStateChange}; no
 * scheduled update is needed.
 */
@RegisterView
public final class LeaderboardView extends View {

    private final SharedState<List<String>> topThree =
            sharedState(Arrays.asList("—", "—", "—"));

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&6Leaderboard").rows(1);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        for (int i = 0; i < 3; i++) {
            final int rank = i;
            render.slot(3 + i)
                    .item(ctx -> named(new ItemStack(Material.PAPER, rank + 1),
                            "#" + (rank + 1) + " " + entryAt(rank)))
                    .updateOnStateChange(topThree);
        }
    }

    private String entryAt(int rank) {
        List<String> entries = topThree.get();
        return entries != null && rank < entries.size() ? entries.get(rank) : "—";
    }

    private static ItemStack named(ItemStack stack, String name) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

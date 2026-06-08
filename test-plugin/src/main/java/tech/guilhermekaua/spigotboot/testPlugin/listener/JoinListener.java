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
package tech.guilhermekaua.spigotboot.testPlugin.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.LeaderboardView;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.SampleAsyncView;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.SampleNormalView;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.SamplePatternView;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.SampleScrollView;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.ShopView;

@Component
@RequiredArgsConstructor
public class JoinListener implements Listener {
    private final ViewService viewService;

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        final Material blockType = event.getBlock().getType();
        final Player player = event.getPlayer();

        if (blockType == Material.DIAMOND_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening scroll pagination sample");
            viewService.open(player, SampleScrollView.class);
            return;
        }

        if (blockType == Material.EMERALD_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening normal pagination sample");
            viewService.open(player, SampleNormalView.class);
            return;
        }

        if (blockType == Material.GOLD_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening pattern pagination sample");
            viewService.open(player, SamplePatternView.class);
            return;
        }

        if (blockType == Material.NETHERITE_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening async pagination sample");
            viewService.open(player, SampleAsyncView.class);
            return;
        }

        if (blockType == Material.IRON_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening shop navigation sample");
            viewService.open(player, ShopView.class);
            return;
        }

        if (blockType == Material.LAPIS_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening leaderboard sample");
            viewService.open(player, LeaderboardView.class);
        }
    }
}

/*
 * The MIT License
 * Copyright Â© 2025 Guilherme KauÃ£ da Silva
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

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.testPlugin.services.VersionedZombieService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Component
public class ZombieTestListener implements Listener {
    private static final String WAND_NAME = ChatColor.GREEN + "Orbit Zombie Wand";
    private static final List<String> WAND_LORE = Arrays.asList(
            ChatColor.GRAY + "Right click to spawn the demo zombie.",
            ChatColor.GRAY + "Sneak + right click to clear it."
    );

    private final VersionedZombieService versionedZombieService;

    public ZombieTestListener(VersionedZombieService versionedZombieService) {
        this.versionedZombieService = Objects.requireNonNull(versionedZombieService, "versionedZombieService cannot be null");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (hasWand(player)) {
            return;
        }

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(createWand());
        for (ItemStack itemStack : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
        }

        player.sendMessage(ChatColor.GREEN + "You received the Orbit Zombie Wand.");
        player.sendMessage(ChatColor.YELLOW + "Right click to spawn the demo zombie. Sneak-right-click to clear it.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isRightClick(event.getAction())) {
            return;
        }
        if (!isWand(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        try {
            if (player.isSneaking()) {
                boolean cleared = versionedZombieService.clearDemoZombie(player);
                if (cleared) {
                    player.sendMessage(ChatColor.GREEN + "Cleared your demo zombie.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "You do not have an active demo zombie.");
                }
                return;
            }

            Zombie zombie = versionedZombieService.spawnDemoZombie(player);
            player.sendMessage(ChatColor.GREEN + "Spawned the orbit zombie at "
                    + zombie.getLocation().getBlockX() + ", "
                    + zombie.getLocation().getBlockY() + ", "
                    + zombie.getLocation().getBlockZ() + ".");
        } catch (IllegalStateException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        versionedZombieService.clearDemoZombie(event.getPlayer());
    }

    private boolean hasWand(Player player) {
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (isWand(itemStack)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta itemMeta = wand.getItemMeta();
        if (itemMeta == null) {
            return wand;
        }

        itemMeta.setDisplayName(WAND_NAME);
        itemMeta.setLore(WAND_LORE);
        wand.setItemMeta(itemMeta);
        return wand;
    }

    private boolean isWand(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.BLAZE_ROD || !itemStack.hasItemMeta()) {
            return false;
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null || !itemMeta.hasDisplayName()) {
            return false;
        }
        if (!WAND_NAME.equals(itemMeta.getDisplayName())) {
            return false;
        }
        List<String> lore = itemMeta.getLore();
        return lore != null && lore.equals(WAND_LORE);
    }

    private static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }
}

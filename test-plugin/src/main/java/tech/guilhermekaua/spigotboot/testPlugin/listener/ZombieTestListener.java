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
            ChatColor.GRAY + "Right click to spawn the orbit zombie.",
            ChatColor.GRAY + "Sneak + right click to clear the spawned demo.",
            ChatColor.GRAY + "Left click to attach to the nearest zombie.",
            ChatColor.GRAY + "Sneak + left click to clear the attached demo."
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
        player.sendMessage(ChatColor.YELLOW + "Right click spawns the orbit demo.");
        player.sendMessage(ChatColor.YELLOW + "Left click attaches hooks to the nearest zombie.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isSupportedClick(event.getAction())) {
            return;
        }
        if (!isWand(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        try {
            if (isRightClick(event.getAction())) {
                handleSpawnClick(player);
                return;
            }

            handleAttachClick(player);
        } catch (IllegalStateException ex) {
            player.sendMessage(ChatColor.RED + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        versionedZombieService.clearDemoZombie(event.getPlayer());
        versionedZombieService.clearAttachedZombie(event.getPlayer());
    }

    private void handleSpawnClick(Player player) {
        if (player.isSneaking()) {
            boolean cleared = versionedZombieService.clearDemoZombie(player);
            if (cleared) {
                player.sendMessage(ChatColor.GREEN + "Cleared your spawned demo zombie.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "You do not have an active spawned demo zombie.");
            }
            return;
        }

        Zombie zombie = versionedZombieService.spawnDemoZombie(player);
        player.sendMessage(ChatColor.GREEN + "Spawned the orbit zombie at "
                + zombie.getLocation().getBlockX() + ", "
                + zombie.getLocation().getBlockY() + ", "
                + zombie.getLocation().getBlockZ() + ".");
    }

    private void handleAttachClick(Player player) {
        if (player.isSneaking()) {
            boolean cleared = versionedZombieService.clearAttachedZombie(player);
            if (cleared) {
                player.sendMessage(ChatColor.GREEN + "Cleared your attached zombie controller.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "You do not have an attached demo zombie.");
            }
            return;
        }

        Zombie zombie = versionedZombieService.attachNearestZombie(player);
        player.sendMessage(ChatColor.GREEN + "Attached the controller demo to the nearest zombie.");
        player.sendMessage(ChatColor.YELLOW + "Damage or interact with it, then kill it to trigger the explicit base-on-die demo.");
        player.sendMessage(ChatColor.GRAY + "Hooked zombie at "
                + zombie.getLocation().getBlockX() + ", "
                + zombie.getLocation().getBlockY() + ", "
                + zombie.getLocation().getBlockZ() + ".");
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

    private static boolean isSupportedClick(Action action) {
        return isRightClick(action)
                || action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK;
    }
}

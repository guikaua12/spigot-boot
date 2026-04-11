/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.testPlugin.command;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.DefaultCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.testPlugin.services.VersionedZombieService;

import java.util.Locale;
import java.util.Objects;

@CommandHandler
@RootCommand(
        value = "zombietest",
        aliases = {"zombie-test"},
        description = "Controls the orbit zombie demo.",
        usage = "/zombietest <spawn|spawn-dynamic|clear-spawn|attach|clear-attach>"
)
public class ZombieTestCommand {
    private final VersionedZombieService versionedZombieService;

    public ZombieTestCommand(VersionedZombieService versionedZombieService) {
        this.versionedZombieService = Objects.requireNonNull(versionedZombieService, "versionedZombieService cannot be null");
    }

    @DefaultCommand
    public void root(@Sender Player player) {
        player.sendMessage(ChatColor.GREEN + "Zombie demo commands:");
        player.sendMessage(ChatColor.YELLOW + "/zombietest spawn " + ChatColor.GRAY + "- spawn the orbit zombie demo.");
        player.sendMessage(ChatColor.YELLOW + "/zombietest spawn-dynamic <baseType> " + ChatColor.GRAY + "- spawn a one-off demo for the typed base type.");
        player.sendMessage(ChatColor.YELLOW + "/zombietest clear-spawn " + ChatColor.GRAY + "- clear your spawned demo entity.");
        player.sendMessage(ChatColor.YELLOW + "/zombietest attach " + ChatColor.GRAY + "- attach hooks to the nearest zombie.");
        player.sendMessage(ChatColor.YELLOW + "/zombietest clear-attach " + ChatColor.GRAY + "- clear your attached zombie demo.");
    }

    @Command(value = "spawn", description = "Spawns the orbit zombie demo.")
    public void spawn(@Sender Player player) {
        execute(player, new Runnable() {
            @Override
            public void run() {
                Zombie zombie = versionedZombieService.spawnDemoZombie(player);
                player.sendMessage(ChatColor.GREEN + "Spawned the orbit zombie at " + formatLocation(zombie) + ".");
            }
        });
    }

    @Command(value = "clear-spawn", description = "Clears the spawned orbit zombie demo.")
    public void clearSpawn(@Sender Player player) {
        execute(player, new Runnable() {
            @Override
            public void run() {
                boolean cleared = versionedZombieService.clearDemoZombie(player);
                if (cleared) {
                    player.sendMessage(ChatColor.GREEN + "Cleared your spawned demo entity.");
                    return;
                }

                player.sendMessage(ChatColor.YELLOW + "You do not have an active spawned demo entity.");
            }
        });
    }

    @Command(value = "spawn-dynamic <baseType>", description = "Spawns a one-off demo for the supplied base type.")
    public void spawnDynamic(@Sender Player player, CustomEntityBaseType baseType) {
        execute(player, () -> {
            Entity entity = versionedZombieService.spawnDynamicDemoEntity(player, baseType);
            player.sendMessage(
                    ChatColor.GREEN + "Spawned a one-off "
                            + baseType.name().toLowerCase(Locale.ROOT)
                            + " demo at " + formatLocation(entity) + "."
            );
        });
    }

    @Command(value = "attach", description = "Attaches the controller demo to the nearest zombie.")
    public void attach(@Sender Player player) {
        execute(player, new Runnable() {
            @Override
            public void run() {
                Zombie zombie = versionedZombieService.attachNearestZombie(player);
                player.sendMessage(ChatColor.GREEN + "Attached the controller demo to the nearest zombie.");
                player.sendMessage(ChatColor.YELLOW + "Damage or interact with it, then kill it to trigger the explicit base-on-die demo.");
                player.sendMessage(ChatColor.GRAY + "Hooked zombie at " + formatLocation(zombie) + ".");
            }
        });
    }

    @Command(value = "clear-attach", description = "Clears the attached zombie demo.")
    public void clearAttach(@Sender Player player) {
        execute(player, new Runnable() {
            @Override
            public void run() {
                boolean cleared = versionedZombieService.clearAttachedZombie(player);
                if (cleared) {
                    player.sendMessage(ChatColor.GREEN + "Cleared your attached zombie controller.");
                    return;
                }

                player.sendMessage(ChatColor.YELLOW + "You do not have an attached demo zombie.");
            }
        });
    }

    private void execute(Player player, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException ex) {
            String message = ex.getMessage();
            player.sendMessage(ChatColor.RED + (message != null ? message : "The zombie demo action failed."));
        }
    }

    private String formatLocation(Entity entity) {
        Location location = entity.getLocation();
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}

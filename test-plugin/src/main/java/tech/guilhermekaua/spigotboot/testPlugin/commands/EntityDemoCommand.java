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
package tech.guilhermekaua.spigotboot.testPlugin.commands;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.annotations.Command;
import tech.guilhermekaua.spigotboot.commands.annotations.CommandHandler;
import tech.guilhermekaua.spigotboot.commands.annotations.DefaultCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.RootCommand;
import tech.guilhermekaua.spigotboot.commands.annotations.Sender;
import tech.guilhermekaua.spigotboot.testPlugin.services.EntityDemoService;

/**
 * Manual command surface used to exercise the custom-entity subsystem from the sample plugin.
 */
@CommandHandler
@RootCommand(value = "entitydemo", aliases = {"edemo"}, description = "Custom entity demo commands")
public class EntityDemoCommand {
    private final EntityDemoService entityDemoService;

    public EntityDemoCommand(EntityDemoService entityDemoService) {
        this.entityDemoService = entityDemoService;
    }

    /**
     * Shows the basic usage for the demo command.
     *
     * @param player the executing player
     * @param context the command execution context
     */
    @DefaultCommand
    public void root(@Sender Player player, CommandExecutionContext context) {
        player.sendMessage("Use /" + context.getCommandLabel() + " orbit");
        player.sendMessage("Use /" + context.getCommandLabel() + " wrap");
        player.sendMessage("Use /" + context.getCommandLabel() + " deathfx-cow");
        player.sendMessage("Use /" + context.getCommandLabel() + " deathfx <entityType>");
        player.sendMessage("Use /" + context.getCommandLabel() + " metadata-dirty-zombie");
        player.sendMessage("Use /" + context.getCommandLabel() + " viewer-cycle-zombie");
        player.sendMessage("Use /" + context.getCommandLabel() + " goal-builder-zombie");
        player.sendMessage("Use /" + context.getCommandLabel() + " attach-existing-zombie");
    }

    /**
     * Spawns a zombie that orbits around the command sender.
     *
     * @param player the executing player
     */
    @Command("orbit")
    public void orbit(@Sender Player player) {
        try {
            entityDemoService.spawnOrbitScenario(player);
            player.sendMessage("Spawned an orbiting custom zombie around you.");
        } catch (RuntimeException exception) {
            player.sendMessage("Could not spawn the orbiting zombie: " + exception.getMessage());
        }
    }

    /**
     * Spawns a normal Bukkit zombie and wraps it with the entity platform.
     *
     * @param player the executing player
     */
    @Command("wrap")
    public void wrap(@Sender Player player) {
        try {
            entityDemoService.wrapBukkitZombie(player);
            player.sendMessage("Spawned and wrapped a Bukkit zombie in front of you.");
        } catch (RuntimeException exception) {
            player.sendMessage("Could not wrap the Bukkit zombie: " + exception.getMessage());
        }
    }

    /**
     * Spawns a custom living entity that plays a rain effect when killed.
     *
     * @param player the executing player
     * @param entityType the Bukkit entity type to spawn
     */
    @Command("deathfx <entityType>")
    public void deathFx(@Sender Player player, EntityType entityType) {
        try {
            entityDemoService.spawnRainDeathEffectEntity(player, entityType);
            player.sendMessage("Spawned a custom " + entityType.name() + " with a rain death effect.");
        } catch (RuntimeException exception) {
            player.sendMessage("Could not spawn the custom entity: " + exception.getMessage());
        }
    }

    @Command("deathfx-cow")
    public void deathFxCow(@Sender Player player) {
        try {
            entityDemoService.spawnDeathFxCowScenario(player);
            player.sendMessage("Spawned the deathfx-cow scenario.");
        } catch (RuntimeException exception) {
            player.sendMessage("Could not spawn the deathfx-cow scenario: " + exception.getMessage());
        }
    }

    @Command("metadata-dirty-zombie")
    public void metadataDirtyZombie(@Sender Player player) {
        try {
            entityDemoService.spawnMetadataDirtyZombieScenario(player);
            player.sendMessage("Spawned the metadata-dirty-zombie scenario.");
        } catch (RuntimeException exception) {
            player.sendMessage("Could not spawn the metadata-dirty-zombie scenario: " + exception.getMessage());
        }
    }

    @Command("viewer-cycle-zombie")
    public void viewerCycleZombie(@Sender Player player) {
        try {
            entityDemoService.spawnViewerCycleZombieScenario(player);
            player.sendMessage("Spawned the viewer-cycle-zombie scenario.");
        } catch (RuntimeException exception) {
            player.sendMessage("Could not spawn the viewer-cycle-zombie scenario: " + exception.getMessage());
        }
    }

    @Command("goal-builder-zombie")
    public void goalBuilderZombie(@Sender Player player) {
        try {
            entityDemoService.spawnGoalBuilderZombieScenario(player);
            player.sendMessage("Spawned the goal-builder-zombie scenario.");
        } catch (RuntimeException exception) {
            player.sendMessage("Could not spawn the goal-builder-zombie scenario: " + exception.getMessage());
        }
    }

    @Command("attach-existing-zombie")
    public void attachExistingZombie(@Sender Player player) {
        try {
            entityDemoService.attachExistingZombieScenario(player);
            player.sendMessage("Spawned the attach-existing-zombie goal mutation scenario.");
        } catch (RuntimeException exception) {
            player.sendMessage("Could not spawn the attach-existing-zombie goal mutation scenario: " + exception.getMessage());
        }
    }
}

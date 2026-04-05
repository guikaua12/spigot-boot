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
package tech.guilhermekaua.spigotboot.testPlugin.services;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.ZombieEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;
import tech.guilhermekaua.spigotboot.testPlugin.entity.behavior.OrbitingZombieBehavior;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class VersionedZombieService {
    private static final CustomEntityId DEMO_ENTITY_ID = CustomEntityId.of("test-plugin", "orbit-zombie");

    private final Map<UUID, SpawnedZombie> activeZombies = new HashMap<UUID, SpawnedZombie>();
    private final JavaPlugin plugin;
    private final ZombieEntityDefinition demoDefinition;

    private VersionedEntityPlatform entityPlatform;

    public VersionedZombieService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.demoDefinition = ZombieEntityDefinition.builder(DEMO_ENTITY_ID)
                .initializer(context -> configureZombie(context.bukkitEntity()))
                .behaviorFactory(context -> new OrbitingZombieBehavior(
                        context.spawnRequest().data().getRequired("trackedPlayerId", UUID.class)
                ))
                .build();
    }

    public Zombie spawnDemoZombie(Player owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        clearDemoZombie(owner);

        VersionedEntityPlatform platform = resolvePlatform();
        ensureDemoDefinitionRegistered(platform);

        CustomEntityHandle<Zombie> handle = platform.spawn(
                demoDefinition,
                CustomEntitySpawnRequest.builder(resolveSpawnLocation(owner))
                        .put("trackedPlayerId", owner.getUniqueId())
                        .build()
        );

        Zombie zombie = handle.bukkitEntity();
        activeZombies.put(owner.getUniqueId(), new SpawnedZombie(handle));
        return zombie;
    }

    public boolean clearDemoZombie(Player owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        return clearDemoZombie(owner.getUniqueId());
    }

    public void clearAllDemoZombies() {
        for (UUID ownerId : activeZombies.keySet().toArray(new UUID[0])) {
            clearDemoZombie(ownerId);
        }
    }

    private boolean clearDemoZombie(UUID ownerId) {
        SpawnedZombie spawnedZombie = activeZombies.remove(ownerId);
        if (spawnedZombie == null) {
            return false;
        }

        spawnedZombie.handle.remove();
        return true;
    }

    private VersionedEntityPlatform resolvePlatform() {
        if (entityPlatform != null) {
            return entityPlatform;
        }

        try {
            entityPlatform = SpigotEntityBootstrap.boot();
            return entityPlatform;
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "The versions demo only runs on Spigot or Paper 1.8.8 and 1.21.11.",
                    ex
            );
        }
    }

    private void ensureDemoDefinitionRegistered(VersionedEntityPlatform platform) {
        if (platform.definition(DEMO_ENTITY_ID) == null) {
            platform.registerDefinition(demoDefinition);
        }
    }

    private Location resolveSpawnLocation(Player owner) {
        return owner.getLocation().clone()
                .add(owner.getLocation().getDirection().normalize().multiply(4.0D))
                .add(0.0D, 0.5D, 0.0D);
    }

    @SuppressWarnings("deprecation")
    private void configureZombie(Zombie zombie) {
        zombie.setBaby(false);
        zombie.setCustomName("Orbit Zombie");
        zombie.setCustomNameVisible(true);
        zombie.setRemoveWhenFarAway(false);
        zombie.setCanPickupItems(false);
        zombie.setMaxHealth(40.0D);
        zombie.setHealth(40.0D);

        EntityEquipment equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.PUMPKIN));
        }
    }

    private static final class SpawnedZombie {
        private final CustomEntityHandle<Zombie> handle;

        private SpawnedZombie(CustomEntityHandle<Zombie> handle) {
            this.handle = handle;
        }
    }
}

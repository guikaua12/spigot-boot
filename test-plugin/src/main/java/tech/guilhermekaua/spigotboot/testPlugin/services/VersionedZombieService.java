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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;
import tech.guilhermekaua.spigotboot.testPlugin.entity.controller.AttachedHookDemoController;
import tech.guilhermekaua.spigotboot.testPlugin.entity.controller.OrbitingZombieController;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class VersionedZombieService {
    private static final CustomEntityId DEMO_ENTITY_ID = CustomEntityId.of("test-plugin", "orbit-zombie");
    private static final double ATTACH_SEARCH_RADIUS = 12.0D;

    private final Map<UUID, SpawnedDemoEntity> activeDemoEntities = new HashMap<UUID, SpawnedDemoEntity>();
    private final Map<UUID, ControlledEntity<Zombie>> attachedZombies = new HashMap<UUID, ControlledEntity<Zombie>>();
    private final EntityTemplate<Zombie> demoTemplate;

    private VersionedEntityPlatform entityPlatform;

    public VersionedZombieService(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        this.demoTemplate = EntityTemplate.<Zombie>builder(DEMO_ENTITY_ID, CustomEntityBaseType.ZOMBIE)
                .initialize(entity -> configureZombie(entity.bukkitEntity()))
                .controller(context -> new OrbitingZombieController(
                        context.data().getRequired("trackedPlayerId", UUID.class)
                ))
                .build();
    }

    public Zombie spawnDemoZombie(Player owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        clearDemoZombie(owner);

        VersionedEntityPlatform platform = resolvePlatform();
        ensureDemoTemplateRegistered(platform);

        SpawnedEntity<Zombie> entity = platform.spawn(
                demoTemplate,
                resolveSpawnLocation(owner),
                spawn -> spawn.data("trackedPlayerId", owner.getUniqueId())
        );

        Zombie zombie = entity.bukkitEntity();
        activeDemoEntities.put(owner.getUniqueId(), new SpawnedDemoEntity(entity));
        return zombie;
    }

    public Entity spawnDynamicDemoEntity(Player owner, CustomEntityBaseType baseType) {
        Objects.requireNonNull(owner, "owner cannot be null");
        Objects.requireNonNull(baseType, "baseType cannot be null");
        clearDemoZombie(owner);

        VersionedEntityPlatform platform = resolvePlatform();
        if (!platform.supports(baseType)) {
            throw new IllegalStateException(
                    "The active server version does not support base type '" + baseType.name().toLowerCase(Locale.ROOT) + "'."
            );
        }

        SpawnedEntity<?> entity = platform.spawn(
                baseType,
                resolveSpawnLocation(owner),
                spawn -> spawn.initialize(spawned -> configureDynamicEntity(spawned.bukkitEntity(), baseType))
        );

        activeDemoEntities.put(owner.getUniqueId(), new SpawnedDemoEntity(entity));
        return entity.bukkitEntity();
    }

    public Zombie attachNearestZombie(Player owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        clearAttachedZombie(owner);

        Zombie target = findNearestZombie(owner);
        if (target == null) {
            throw new IllegalStateException("No nearby vanilla zombie was found to attach.");
        }

        ControlledEntity<Zombie> attachedEntity = resolvePlatform().get(target);
        attachedEntity.setController(new AttachedHookDemoController());
        attachedZombies.put(owner.getUniqueId(), attachedEntity);
        return attachedEntity.bukkitEntity();
    }

    public boolean clearDemoZombie(Player owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        return clearDemoZombie(owner.getUniqueId());
    }

    public boolean clearAttachedZombie(Player owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        ControlledEntity<Zombie> attachedEntity = attachedZombies.remove(owner.getUniqueId());
        if (attachedEntity == null) {
            return false;
        }

        attachedEntity.clearController();
        return true;
    }

    public void clearAllDemoZombies() {
        for (UUID ownerId : activeDemoEntities.keySet().toArray(new UUID[0])) {
            clearDemoZombie(ownerId);
        }
        for (UUID ownerId : attachedZombies.keySet().toArray(new UUID[0])) {
            ControlledEntity<Zombie> attachedEntity = attachedZombies.remove(ownerId);
            if (attachedEntity != null) {
                attachedEntity.clearController();
            }
        }
    }

    private boolean clearDemoZombie(UUID ownerId) {
        SpawnedDemoEntity spawnedDemoEntity = activeDemoEntities.remove(ownerId);
        if (spawnedDemoEntity == null) {
            return false;
        }

        spawnedDemoEntity.entity.remove();
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

    private void ensureDemoTemplateRegistered(VersionedEntityPlatform platform) {
        if (platform.template(DEMO_ENTITY_ID) == null) {
            platform.register(demoTemplate);
        }
    }

    private Location resolveSpawnLocation(Player owner) {
        return owner.getLocation().clone()
                .add(owner.getLocation().getDirection().normalize().multiply(4.0D))
                .add(0.0D, 0.5D, 0.0D);
    }

    private Zombie findNearestZombie(Player owner) {
        Zombie nearestZombie = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Entity entity : owner.getNearbyEntities(ATTACH_SEARCH_RADIUS, ATTACH_SEARCH_RADIUS, ATTACH_SEARCH_RADIUS)) {
            if (!(entity instanceof Zombie)) {
                continue;
            }

            Zombie zombie = (Zombie) entity;
            if (!zombie.isValid() || zombie.isDead()) {
                continue;
            }

            double distanceSquared = zombie.getLocation().distanceSquared(owner.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestZombie = zombie;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearestZombie;
    }

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

    private void configureDynamicEntity(Entity entity, CustomEntityBaseType baseType) {
        entity.setCustomName("Dynamic " + baseType.logicalId());
        entity.setCustomNameVisible(true);

        if (entity instanceof LivingEntity) {
            ((LivingEntity) entity).setRemoveWhenFarAway(false);
        }
    }

    private static final class SpawnedDemoEntity {
        private final SpawnedEntity<?> entity;

        private SpawnedDemoEntity(SpawnedEntity<?> entity) {
            this.entity = entity;
        }
    }
}

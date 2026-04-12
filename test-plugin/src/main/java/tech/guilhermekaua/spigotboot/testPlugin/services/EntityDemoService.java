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
package tech.guilhermekaua.spigotboot.testPlugin.services;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityController;
import tech.guilhermekaua.spigotboot.entity.api.EntityDieContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityInitializer;
import tech.guilhermekaua.spigotboot.entity.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;

import java.util.UUID;

/**
 * Exposes small custom-entity demos for the sample plugin.
 */
@Service
public class EntityDemoService {
    private volatile VersionedEntityPlatform entityPlatform;

    /**
     * Spawns a zombie that orbits around the supplied player.
     *
     * @param player the player that becomes the orbit center
     * @return the spawned controlled zombie
     */
    public @NotNull SpawnedEntity<Zombie> spawnOrbitingZombie(@NotNull Player player) {
        final UUID playerId = player.getUniqueId();
        return platform().spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, orbitSpawnLocation(player), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Zombie>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Zombie> entity) {
                    Zombie zombie = entity.bukkitEntity();
                    zombie.setAdult();
                    zombie.setAI(false);
                    zombie.setGravity(false);
                    zombie.setSilent(true);
                    zombie.setRemoveWhenFarAway(false);
                    zombie.setCustomName(player.getName() + "'s Orbiting Zombie");
                    zombie.setCustomNameVisible(true);
                }
            });
            spawnBuilder.controller(context -> new OrbitingZombieController(playerId));
        });
    }

    /**
     * Spawns a custom living entity of the supplied Bukkit type that emits a rain effect when killed.
     *
     * @param player the player requesting the spawn
     * @param entityType the Bukkit entity type to spawn
     * @return the spawned controlled entity
     */
    public @NotNull SpawnedEntity<? extends LivingEntity> spawnRainDeathEffectEntity(
            @NotNull Player player,
            @NotNull EntityType entityType
    ) {
        CustomEntityBaseType baseType = CustomEntityBaseType.fromEntityType(entityType);
        if (baseType == null) {
            throw new IllegalArgumentException("Entity type '" + entityType.name() + "' is not supported by the custom entity API.");
        }
        if (!platform().supports(baseType)) {
            throw new IllegalArgumentException("The active entity adapter does not support base type '" + baseType.name() + "'.");
        }

        Class<? extends Entity> entityClass = entityType.getEntityClass();
        if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            throw new IllegalArgumentException("Entity type '" + entityType.name() + "' is not a living entity and cannot be killed.");
        }

        return spawnRainDeathEffectEntity(player, baseType, entityType, entityClass.asSubclass(LivingEntity.class));
    }

    private synchronized @NotNull VersionedEntityPlatform platform() {
        if (entityPlatform == null) {
            entityPlatform = SpigotEntityBootstrap.boot();
        }
        return entityPlatform;
    }

    private <T extends LivingEntity> @NotNull SpawnedEntity<T> spawnRainDeathEffectEntity(
            @NotNull Player player,
            @NotNull CustomEntityBaseType baseType,
            @NotNull EntityType entityType,
            @NotNull Class<T> entityClass
    ) {
        return platform().spawn(baseType, entityClass, effectSpawnLocation(player), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<T>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<T> entity) {
                    T spawnedEntity = entity.bukkitEntity();
                    spawnedEntity.setCustomName("Rain FX " + prettify(entityType));
                    spawnedEntity.setCustomNameVisible(true);
                }
            });
            spawnBuilder.controller(context -> new RainDeathEffectController<T>());
        });
    }

    private static @NotNull Location orbitSpawnLocation(@NotNull Player player) {
        return player.getLocation().clone().add(2.5D, 1.0D, 0.0D);
    }

    private static @NotNull Location effectSpawnLocation(@NotNull Player player) {
        Location location = player.getLocation().clone();
        Vector direction = location.getDirection();
        if (direction.lengthSquared() > 0.0D) {
            direction.normalize().multiply(2.5D);
            location.add(direction);
        }
        return location;
    }

    private static @NotNull String prettify(@NotNull EntityType entityType) {
        String[] pieces = entityType.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(piece.charAt(0))).append(piece.substring(1));
        }
        return builder.toString();
    }

    private static void playRainEffect(@NotNull Location location) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.WATER_DROP, location, 60, 0.6D, 0.5D, 0.6D, 0.05D);
        location.getWorld().playSound(location, Sound.WEATHER_RAIN, 1.0F, 1.0F);
        location.getWorld().playSound(location, Sound.WEATHER_RAIN_ABOVE, 0.75F, 1.1F);
    }

    private static final class OrbitingZombieController extends EntityController<Zombie> {
        private final UUID playerId;
        private double angle;

        private OrbitingZombieController(@NotNull UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void onTick(@NotNull EntityTickContext<Zombie> context) {
            context.base().invoke();

            Player player = Bukkit.getPlayer(playerId);
            Zombie zombie = context.bukkitEntity();
            if (player == null || !player.isOnline() || player.isDead() || !player.isValid()) {
                context.remove();
                return;
            }

            Location center = player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
            angle += 0.18D;

            Location orbitLocation = center.clone().add(
                    Math.cos(angle) * 2.5D,
                    0.35D + (Math.sin(angle * 2.0D) * 0.25D),
                    Math.sin(angle) * 2.5D
            );
            Vector direction = center.toVector().subtract(orbitLocation.toVector());
            if (direction.lengthSquared() > 0.0D) {
                orbitLocation.setDirection(direction);
            }

            zombie.teleport(orbitLocation);
            zombie.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        }
    }

    private static final class RainDeathEffectController<T extends LivingEntity> extends EntityController<T> {
        private boolean effectPlayed;

        @Override
        public void onDie(@NotNull EntityDieContext<T> context) {
            Location deathLocation = context.bukkitEntity().getLocation().clone();
            playEffectOnce(deathLocation);
            context.base().invoke();
        }

        @Override
        public void onRemove(@NotNull EntityRemoveContext<T> context) {
            Location removalLocation = context.bukkitEntity().getLocation().clone();
            if (context.bukkitEntity().isDead()) {
                playEffectOnce(removalLocation);
            }
            context.base().invoke();
        }

        private void playEffectOnce(@Nullable Location location) {
            if (effectPlayed || location == null) {
                return;
            }
            effectPlayed = true;
            playRainEffect(location);
        }
    }
}

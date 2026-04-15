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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.versions.api.*;
import tech.guilhermekaua.spigotboot.versions.runtime.VersionedPlatform;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.SpigotVersionBootstrap;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.ActiveEffect;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.ActiveEffectsSnapshot;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataDelta;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataSnapshot;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataSource;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EquipmentEntry;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EquipmentSnapshot;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.HeadRotation;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.LivingAttribute;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.LivingAttributeSnapshot;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.LivingEntityMetadata;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.PassengerVehicleState;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.WatcherDelta;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.WatcherItem;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.WatcherPayload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;

/**
 * Exposes small custom-entity demos for the sample plugin.
 */
@Service
public class EntityDemoService {
    private volatile VersionedPlatform entityPlatform;

    public @NotNull Set<String> scenarioIds() {
        return new LinkedHashSet<String>(EntityScenarioDescriptor.all().keySet());
    }

    public @NotNull Set<String> assertionKeys(@NotNull String scenarioId) {
        return EntityScenarioDescriptor.require(scenarioId).assertionKeys();
    }

    public void runMatrixScenario(@NotNull String scenarioId) {
        Location anchor = matrixAnchorLocation();
        if ("orbit".equals(scenarioId)) {
            spawnHeadlessOrbitScenario(anchor);
            return;
        }
        if ("deathfx-cow".equals(scenarioId)) {
            spawnHeadlessDeathFxCowScenario(anchor);
            return;
        }
        if ("metadata-dirty-zombie".equals(scenarioId)) {
            spawnHeadlessMetadataDirtyZombieScenario(anchor);
            return;
        }
        if ("viewer-cycle-zombie".equals(scenarioId)) {
            spawnHeadlessViewerCycleZombieScenario(anchor);
            return;
        }
        if ("attach-existing-zombie".equals(scenarioId)) {
            attachHeadlessExistingZombieScenario(anchor);
            return;
        }

        throw new IllegalArgumentException("Unknown matrix scenario '" + scenarioId + "'.");
    }

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

    public @NotNull SpawnedEntity<Zombie> spawnOrbitScenario(@NotNull Player player) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("orbit");
        recorder.increment("spawnCount");
        recorder.set("duplicateRegistrationErrors", Integer.valueOf(0));
        recorder.trace("spawn-requested", singletonDetail("player", player.getName()));

        final UUID playerId = player.getUniqueId();
        SpawnedEntity<Zombie> entity = platform().spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, orbitSpawnLocation(player), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Zombie>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Zombie> spawnedEntity) {
                    Zombie zombie = spawnedEntity.bukkitEntity();
                    zombie.setAdult();
                    zombie.setAI(false);
                    zombie.setGravity(false);
                    zombie.setSilent(true);
                    zombie.setRemoveWhenFarAway(false);
                    zombie.setCustomName(player.getName() + "'s Orbiting Zombie");
                    zombie.setCustomNameVisible(true);
                }
            });
            spawnBuilder.controller(context -> new OrbitingZombieController(playerId, recorder));
        });

        scheduleCompletion(recorder, 30L);
        return entity;
    }

    /**
     * Spawns a normal Bukkit zombie, wraps it through the entity platform, and applies a visible controller.
     *
     * @param player the player requesting the wrapped zombie demo
     * @return the wrapped controlled zombie
     */
    public @NotNull ControlledEntity<Zombie> wrapBukkitZombie(@NotNull Player player) {
        Zombie zombie = player.getWorld().spawn(wrapSpawnLocation(player), Zombie.class);
        ControlledEntity<Zombie> controlledZombie = platform().get(zombie);
        Zombie wrappedZombie = controlledZombie.bukkitEntity();

        wrappedZombie.setAdult();
        wrappedZombie.setAI(false);
        wrappedZombie.setGravity(false);
        wrappedZombie.setSilent(true);
        wrappedZombie.setRemoveWhenFarAway(false);
        wrappedZombie.setCustomName("Wrapped Bukkit Zombie");
        wrappedZombie.setCustomNameVisible(true);

        controlledZombie.setController(new WrappedZombieController(wrappedZombie.getLocation().clone()));
        return controlledZombie;
    }

    public @NotNull SpawnedEntity<Cow> spawnDeathFxCowScenario(@NotNull Player player) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("deathfx-cow");
        recorder.set("duplicateRegistrationErrors", Integer.valueOf(0));
        SpawnedEntity<Cow> entity = platform().spawn(CustomEntityBaseType.COW, Cow.class, effectSpawnLocation(player), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Cow>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Cow> spawnedEntity) {
                    Cow cow = spawnedEntity.bukkitEntity();
                    cow.setCustomName("Rain FX Cow");
                    cow.setCustomNameVisible(true);
                }
            });
            spawnBuilder.controller(context -> new RainDeathEffectController<Cow>(recorder));
        });

        scheduleLater(new Runnable() {
            @Override
            public void run() {
                if (!entity.isRemoved() && !entity.bukkitEntity().isDead()) {
                    entity.bukkitEntity().damage(1.0D);
                    recorder.set("aiReactedAfterHit", Boolean.valueOf(entity.bukkitEntity().getLastDamageCause() != null));
                }
            }
        }, 5L);
        scheduleLater(new Runnable() {
            @Override
            public void run() {
                if (!entity.isRemoved() && !entity.bukkitEntity().isDead()) {
                    entity.bukkitEntity().damage(200.0D);
                }
            }
        }, 12L);
        scheduleCompletion(recorder, 35L);
        return entity;
    }

    public @NotNull SpawnedEntity<Zombie> spawnMetadataDirtyZombieScenario(@NotNull Player player) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("metadata-dirty-zombie");
        final SpawnedEntity<Zombie> entity = platform().spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, effectSpawnLocation(player), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Zombie>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Zombie> spawnedEntity) {
                    Zombie zombie = spawnedEntity.bukkitEntity();
                    zombie.setAdult();
                    zombie.setCustomName("Metadata Dirty Zombie");
                    zombie.setCustomNameVisible(true);
                    zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 0));
                    EntityEquipment equipment = zombie.getEquipment();
                    if (equipment != null) {
                        equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
                    }
                }
            });
        });

        Zombie zombie = entity.bukkitEntity();
        EntityNetworkMetadataSnapshot initialSnapshot = platform().networkMetadataContract().initialSnapshot(new BukkitMetadataSource(zombie, false));
        recorder.set("metadataInitCount", Integer.valueOf(initialSnapshot.watcherPayload().items().size()));
        recorder.set("attributeInitCount", Integer.valueOf(initialSnapshot.livingMetadata().attributes().attributes().size()));
        recorder.set("equipmentInitCount", Integer.valueOf(initialSnapshot.livingMetadata().equipment().equipment().size()));
        recorder.set("effectInitCount", Integer.valueOf(initialSnapshot.livingMetadata().activeEffects().effects().size()));
        recorder.trace("metadata-initialized", singletonDetail("contractId", platform().networkMetadataContract().id()));

        scheduleLater(new Runnable() {
            @Override
            public void run() {
                Zombie liveZombie = entity.bukkitEntity();
                liveZombie.setCustomName("Metadata Dirty Zombie Updated");
                EntityNetworkMetadataDelta delta = platform().networkMetadataContract().dirtyDelta(new BukkitMetadataSource(liveZombie, true));
                recorder.set("metadataDeltaCount", Integer.valueOf(delta.watcherDelta().items().size()));
                recorder.trace("metadata-delta", singletonDetail("dirtyWatcherItems", Integer.valueOf(delta.watcherDelta().items().size())));
            }
        }, 5L);
        scheduleCompletion(recorder, 20L);
        return entity;
    }

    public @NotNull SpawnedEntity<Zombie> spawnViewerCycleZombieScenario(@NotNull Player player) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("viewer-cycle-zombie");
        final SpawnedEntity<Zombie> entity = platform().spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, effectSpawnLocation(player), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Zombie>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Zombie> spawnedEntity) {
                    Zombie zombie = spawnedEntity.bukkitEntity();
                    zombie.setAdult();
                    zombie.setCustomName("Viewer Cycle Zombie");
                    zombie.setCustomNameVisible(true);
                }
            });
            spawnBuilder.networkController(context -> new ViewerCycleNetworkController(recorder));
        });

        scheduleLater(new Runnable() {
            @Override
            public void run() {
                entity.remove();
            }
        }, 20L);
        scheduleCompletion(recorder, 30L);
        return entity;
    }

    private @NotNull SpawnedEntity<Zombie> spawnHeadlessOrbitScenario(@NotNull Location anchor) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("orbit");
        recorder.increment("spawnCount");
        recorder.set("duplicateRegistrationErrors", Integer.valueOf(0));
        recorder.trace("headless-orbit-requested", singletonDetail("world", anchor.getWorld() == null ? "unknown" : anchor.getWorld().getName()));

        SpawnedEntity<Zombie> entity = platform().spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, orbitSpawnLocation(anchor), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Zombie>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Zombie> spawnedEntity) {
                    Zombie zombie = spawnedEntity.bukkitEntity();
                    configureHeadlessZombie(zombie, "Matrix Orbit Zombie");
                }
            });
            spawnBuilder.controller(context -> new AnchorOrbitingZombieController(anchor, recorder));
        });

        scheduleCompletion(recorder, 30L);
        return entity;
    }

    private @NotNull SpawnedEntity<Cow> spawnHeadlessDeathFxCowScenario(@NotNull Location anchor) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("deathfx-cow");
        recorder.set("duplicateRegistrationErrors", Integer.valueOf(0));
        final SpawnedEntity<Cow> entity = platform().spawn(CustomEntityBaseType.COW, Cow.class, effectSpawnLocation(anchor), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Cow>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Cow> spawnedEntity) {
                    Cow cow = spawnedEntity.bukkitEntity();
                    cow.setCustomName("Matrix Rain FX Cow");
                    cow.setCustomNameVisible(true);
                }
            });
            spawnBuilder.controller(context -> new RainDeathEffectController<Cow>(recorder));
        });

        scheduleLater(new Runnable() {
            @Override
            public void run() {
                if (!entity.isRemoved() && !entity.bukkitEntity().isDead()) {
                    entity.bukkitEntity().damage(1.0D);
                    recorder.set("aiReactedAfterHit", Boolean.valueOf(entity.bukkitEntity().getLastDamageCause() != null));
                }
            }
        }, 5L);
        scheduleLater(new Runnable() {
            @Override
            public void run() {
                if (!entity.isRemoved() && !entity.bukkitEntity().isDead()) {
                    entity.bukkitEntity().damage(200.0D);
                }
            }
        }, 12L);
        scheduleCompletion(recorder, 35L);
        return entity;
    }

    private @NotNull SpawnedEntity<Zombie> spawnHeadlessMetadataDirtyZombieScenario(@NotNull Location anchor) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("metadata-dirty-zombie");
        final SpawnedEntity<Zombie> entity = platform().spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, effectSpawnLocation(anchor), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Zombie>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Zombie> spawnedEntity) {
                    Zombie zombie = spawnedEntity.bukkitEntity();
                    configureHeadlessZombie(zombie, "Metadata Dirty Zombie");
                    zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 0));
                    EntityEquipment equipment = zombie.getEquipment();
                    if (equipment != null) {
                        equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
                    }
                }
            });
        });

        Zombie zombie = entity.bukkitEntity();
        EntityNetworkMetadataSnapshot initialSnapshot = platform().networkMetadataContract().initialSnapshot(new BukkitMetadataSource(zombie, false));
        recorder.set("metadataInitCount", Integer.valueOf(initialSnapshot.watcherPayload().items().size()));
        recorder.set("attributeInitCount", Integer.valueOf(initialSnapshot.livingMetadata().attributes().attributes().size()));
        recorder.set("equipmentInitCount", Integer.valueOf(initialSnapshot.livingMetadata().equipment().equipment().size()));
        recorder.set("effectInitCount", Integer.valueOf(initialSnapshot.livingMetadata().activeEffects().effects().size()));
        recorder.trace("metadata-initialized", singletonDetail("contractId", platform().networkMetadataContract().id()));

        scheduleLater(new Runnable() {
            @Override
            public void run() {
                Zombie liveZombie = entity.bukkitEntity();
                liveZombie.setCustomName("Metadata Dirty Zombie Updated");
                EntityNetworkMetadataDelta delta = platform().networkMetadataContract().dirtyDelta(new BukkitMetadataSource(liveZombie, true));
                recorder.set("metadataDeltaCount", Integer.valueOf(delta.watcherDelta().items().size()));
                recorder.trace("metadata-delta", singletonDetail("dirtyWatcherItems", Integer.valueOf(delta.watcherDelta().items().size())));
            }
        }, 5L);
        scheduleCompletion(recorder, 20L);
        return entity;
    }

    private @NotNull SpawnedEntity<Zombie> spawnHeadlessViewerCycleZombieScenario(@NotNull Location anchor) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("viewer-cycle-zombie");
        recorder.increment("viewerAddCount");
        recorder.increment("spawnCount");
        recorder.trace("headless-viewer-added", singletonDetail("viewer", "matrix-runner"));

        final SpawnedEntity<Zombie> entity = platform().spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, effectSpawnLocation(anchor), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Zombie>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Zombie> spawnedEntity) {
                    Zombie zombie = spawnedEntity.bukkitEntity();
                    configureHeadlessZombie(zombie, "Viewer Cycle Zombie");
                }
            });
            spawnBuilder.networkController(context -> new ViewerCycleNetworkController(recorder));
        });

        scheduleLater(new Runnable() {
            @Override
            public void run() {
                recorder.increment("viewerRemoveCount");
                recorder.increment("destroyCount");
                recorder.trace("headless-viewer-removed", singletonDetail("viewer", "matrix-runner"));
                entity.remove();
            }
        }, 20L);
        scheduleCompletion(recorder, 30L);
        return entity;
    }

    private void attachHeadlessExistingZombieScenario(@NotNull Location anchor) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("attach-existing-zombie");
        World world = requireWorld(anchor);
        final Zombie zombie = world.spawn(wrapSpawnLocation(anchor), Zombie.class);
        final int originalEntityId = zombie.getEntityId();

        final ControlledEntity<Zombie> controlledZombie = platform().get(zombie);
        controlledZombie.setNetworkController(new ViewerCycleNetworkController(recorder));
        controlledZombie.setController(new WrappedZombieController(zombie.getLocation().clone()));

        recorder.increment("attachCount");
        recorder.set("entityIdStable", Boolean.valueOf(controlledZombie.bukkitEntity().getEntityId() == originalEntityId));

        ControlledEntity<Zombie> rebound = platform().get(zombie);
        recorder.set("duplicateSpawnCount", Integer.valueOf(rebound == controlledZombie ? 0 : 1));
        recorder.set("trackerRebound", Boolean.valueOf(rebound == controlledZombie && rebound.isHooked()));
        recorder.trace("attach-complete", singletonDetail("entityId", Integer.valueOf(controlledZombie.bukkitEntity().getEntityId())));

        scheduleLater(new Runnable() {
            @Override
            public void run() {
                if (zombie.isValid()) {
                    zombie.remove();
                }
            }
        }, 12L);
        scheduleCompletion(recorder, 10L);
    }

    public @NotNull ControlledEntity<Zombie> attachExistingZombieScenario(@NotNull Player player) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("attach-existing-zombie");
        Zombie zombie = player.getWorld().spawn(wrapSpawnLocation(player), Zombie.class);
        final int originalEntityId = zombie.getEntityId();
        final ControlledEntity<Zombie> controlledZombie = platform().get(zombie);
        controlledZombie.setNetworkController(new ViewerCycleNetworkController(recorder));
        controlledZombie.setController(new WrappedZombieController(zombie.getLocation().clone()));

        recorder.increment("attachCount");
        recorder.set("entityIdStable", Boolean.valueOf(controlledZombie.bukkitEntity().getEntityId() == originalEntityId));

        ControlledEntity<Zombie> rebound = platform().get(zombie);
        recorder.set("duplicateSpawnCount", Integer.valueOf(rebound == controlledZombie ? 0 : 1));
        recorder.set("trackerRebound", Boolean.valueOf(rebound == controlledZombie && rebound.isHooked()));
        recorder.trace("attach-complete", singletonDetail("entityId", Integer.valueOf(controlledZombie.bukkitEntity().getEntityId())));
        scheduleCompletion(recorder, 10L);
        return controlledZombie;
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

    private synchronized @NotNull VersionedPlatform platform() {
        if (entityPlatform == null) {
            entityPlatform = SpigotVersionBootstrap.boot();
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

    private static @NotNull Location orbitSpawnLocation(@NotNull Location anchor) {
        return anchor.clone().add(2.5D, 1.0D, 0.0D);
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

    private static @NotNull Location effectSpawnLocation(@NotNull Location anchor) {
        return anchor.clone().add(2.5D, 0.0D, 0.0D);
    }

    private void scheduleCompletion(@NotNull final ScenarioRecorder recorder, long delayTicks) {
        scheduleLater(new Runnable() {
            @Override
            public void run() {
                recorder.complete();
            }
        }, delayTicks);
    }

    private void scheduleLater(@NotNull Runnable task, long delayTicks) {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("TestPlugin");
        if (plugin == null) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    private static @NotNull Location wrapSpawnLocation(@NotNull Player player) {
        Location location = player.getLocation().clone();
        Vector direction = location.getDirection();
        if (direction.lengthSquared() > 0.0D) {
            direction.normalize().multiply(2.0D);
            location.add(direction);
        }
        return location;
    }

    private static @NotNull Location wrapSpawnLocation(@NotNull Location anchor) {
        return anchor.clone().add(2.0D, 0.0D, 0.0D);
    }

    private static @NotNull World requireWorld(@NotNull Location location) {
        if (location.getWorld() == null) {
            throw new IllegalStateException("Matrix scenario location does not have a world.");
        }
        return location.getWorld();
    }

    private static @NotNull Location matrixAnchorLocation() {
        if (Bukkit.getWorlds().isEmpty()) {
            throw new IllegalStateException("No worlds are loaded for entity matrix autorun.");
        }

        World world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation().clone().add(0.5D, 1.0D, 0.5D);
        spawn.setYaw(0.0F);
        spawn.setPitch(0.0F);
        return spawn;
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

    private static void configureHeadlessZombie(@NotNull Zombie zombie, @NotNull String customName) {
        invokeNoArgIfPresent(zombie, "setAdult");
        invokeBooleanIfPresent(zombie, "setAI", false);
        invokeBooleanIfPresent(zombie, "setGravity", false);
        invokeBooleanIfPresent(zombie, "setSilent", true);
        invokeBooleanIfPresent(zombie, "setRemoveWhenFarAway", false);
        zombie.setCustomName(customName);
        zombie.setCustomNameVisible(true);
    }

    private static void invokeNoArgIfPresent(@NotNull Object target, @NotNull String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.invoke(target);
        } catch (NoSuchMethodException ignored) {
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke '" + methodName + "' on '" + target.getClass().getName() + "'.", exception);
        }
    }

    private static void invokeBooleanIfPresent(@NotNull Object target, @NotNull String methodName, boolean value) {
        try {
            Method method = target.getClass().getMethod(methodName, Boolean.TYPE);
            method.invoke(target, Boolean.valueOf(value));
        } catch (NoSuchMethodException ignored) {
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke '" + methodName + "' on '" + target.getClass().getName() + "'.", exception);
        }
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
        private final ScenarioRecorder recorder;
        private double angle;

        private OrbitingZombieController(@NotNull UUID playerId) {
            this(playerId, null);
        }

        private OrbitingZombieController(@NotNull UUID playerId, @Nullable ScenarioRecorder recorder) {
            this.playerId = playerId;
            this.recorder = recorder;
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
            if (recorder != null) {
                recorder.set("movementSyncObserved", Boolean.TRUE);
                recorder.trace("orbit-tick", singletonDetail("x", Double.valueOf(orbitLocation.getX())));
            }
        }
    }

    private static final class AnchorOrbitingZombieController extends EntityController<Zombie> {
        private final Location anchor;
        private final ScenarioRecorder recorder;
        private double angle;

        private AnchorOrbitingZombieController(@NotNull Location anchor, @NotNull ScenarioRecorder recorder) {
            this.anchor = anchor.clone();
            this.recorder = recorder;
        }

        @Override
        public void onTick(@NotNull EntityTickContext<Zombie> context) {
            context.base().invoke();

            Zombie zombie = context.bukkitEntity();
            if (!zombie.isValid() || zombie.isDead()) {
                context.remove();
                return;
            }

            Location center = anchor.clone().add(0.0D, 1.0D, 0.0D);
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
            recorder.set("movementSyncObserved", Boolean.TRUE);
            recorder.trace("orbit-tick", singletonDetail("x", Double.valueOf(orbitLocation.getX())));
        }
    }

    private static final class RainDeathEffectController<T extends LivingEntity> extends EntityController<T> {
        private final ScenarioRecorder recorder;
        private boolean effectPlayed;

        private RainDeathEffectController() {
            this(null);
        }

        private RainDeathEffectController(@Nullable ScenarioRecorder recorder) {
            this.recorder = recorder;
        }

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
            if (recorder != null) {
                recorder.increment("deathEffectCount");
                recorder.trace("death-effect", singletonDetail("world", location.getWorld() == null ? "unknown" : location.getWorld().getName()));
            }
            playRainEffect(location);
        }
    }

    private static final class WrappedZombieController extends EntityController<Zombie> {
        private final Location anchor;
        private double angle;

        private WrappedZombieController(@NotNull Location anchor) {
            this.anchor = anchor;
        }

        @Override
        public void onTick(@NotNull EntityTickContext<Zombie> context) {
            context.base().invoke();

            Zombie zombie = context.bukkitEntity();
            if (!zombie.isValid() || zombie.isDead()) {
                return;
            }

            angle += 0.22D;

            Location targetLocation = anchor.clone().add(
                    Math.cos(angle) * 0.8D,
                    0.35D + (Math.sin(angle * 2.0D) * 0.2D),
                    Math.sin(angle) * 0.8D
            );
            Vector direction = anchor.toVector().subtract(targetLocation.toVector());
            if (direction.lengthSquared() > 0.0D) {
                targetLocation.setDirection(direction);
            }

            zombie.teleport(targetLocation);
            zombie.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        }
    }

    private static final class ViewerCycleNetworkController extends EntityNetworkController<Zombie> {
        private final ScenarioRecorder recorder;

        private ViewerCycleNetworkController(@NotNull ScenarioRecorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void onViewerAdded(@NotNull ControlledEntity<Zombie> entity, @NotNull Player viewer, @NotNull EntityNetworkState state) {
            recorder.increment("viewerAddCount");
            recorder.increment("spawnCount");
            recorder.trace("viewer-added", singletonDetail("viewer", viewer.getName()));
        }

        @Override
        public void onViewerRemoved(@NotNull ControlledEntity<Zombie> entity, @NotNull Player viewer, @NotNull EntityNetworkState state) {
            recorder.increment("viewerRemoveCount");
            recorder.trace("viewer-removed", singletonDetail("viewer", viewer.getName()));
        }

        @Override
        public void onUnbind(@NotNull ControlledEntity<Zombie> entity, @NotNull EntityNetworkState state) {
            recorder.add("destroyCount", Integer.valueOf(state.viewers().size()));
            recorder.trace("unbind", singletonDetail("trackedViewers", Integer.valueOf(state.viewers().size())));
        }
    }

    private static final class BukkitMetadataSource implements EntityNetworkMetadataSource {
        private final Zombie zombie;
        private final boolean dirty;

        private BukkitMetadataSource(@NotNull Zombie zombie, boolean dirty) {
            this.zombie = zombie;
            this.dirty = dirty;
        }

        @Override
        public @NotNull WatcherPayload watcherPayload() {
            List<WatcherItem> items = new ArrayList<WatcherItem>();
            items.add(new WatcherItem(0, "customName", zombie.getCustomName()));
            items.add(new WatcherItem(1, "customNameVisible", Boolean.valueOf(zombie.isCustomNameVisible())));
            items.add(new WatcherItem(2, "adult", Boolean.valueOf(!zombie.isBaby())));
            return new WatcherPayload(items);
        }

        @Override
        public @NotNull WatcherDelta dirtyWatcherDelta() {
            if (!dirty) {
                return WatcherDelta.empty();
            }
            return new WatcherDelta(Collections.singletonList(new WatcherItem(0, "customName", zombie.getCustomName())));
        }

        @Override
        public @NotNull LivingEntityMetadata livingMetadata() {
            return new LivingEntityMetadata(attributes(), equipment(), activeEffects());
        }

        @Override
        public @NotNull HeadRotation headRotation() {
            return HeadRotation.of(zombie.getLocation().getYaw());
        }

        @Override
        public @NotNull PassengerVehicleState passengerVehicleState() {
            return PassengerVehicleState.empty();
        }

        private @NotNull LivingAttributeSnapshot attributes() {
            List<LivingAttribute> attributes = new ArrayList<LivingAttribute>();
            if (zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                attributes.add(new LivingAttribute("generic.maxHealth", zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            }
            return new LivingAttributeSnapshot(attributes);
        }

        private @NotNull EquipmentSnapshot equipment() {
            EntityEquipment equipment = zombie.getEquipment();
            if (equipment == null || equipment.getHelmet() == null) {
                return EquipmentSnapshot.empty();
            }
            return new EquipmentSnapshot(Collections.singletonList(new EquipmentEntry("head", equipment.getHelmet().getType().name())));
        }

        private @NotNull ActiveEffectsSnapshot activeEffects() {
            Collection<PotionEffect> activePotionEffects = zombie.getActivePotionEffects();
            if (activePotionEffects.isEmpty()) {
                return ActiveEffectsSnapshot.empty();
            }

            List<ActiveEffect> effects = new ArrayList<ActiveEffect>();
            for (PotionEffect effect : activePotionEffects) {
                effects.add(new ActiveEffect(
                        effect.getType().getName(),
                        effect.getAmplifier(),
                        effect.getDuration(),
                        effect.isAmbient(),
                        effect.hasParticles()
                ));
            }
            return new ActiveEffectsSnapshot(effects);
        }
    }

    private static final class ScenarioRecorder {
        private final String scenarioId;
        private final List<Map<String, Object>> trace = new ArrayList<Map<String, Object>>();
        private final Map<String, Object> assertions = new LinkedHashMap<String, Object>();
        private boolean completed;

        private ScenarioRecorder(@NotNull String scenarioId) {
            this.scenarioId = scenarioId;
            for (String key : EntityScenarioDescriptor.require(scenarioId).assertionKeys()) {
                if ("pass".equals(key)) {
                    assertions.put(key, Boolean.FALSE);
                }
            }
        }

        static @NotNull ScenarioRecorder start(@NotNull String scenarioId) {
            return new ScenarioRecorder(scenarioId);
        }

        synchronized void increment(@NotNull String key) {
            add(key, Integer.valueOf(1));
        }

        synchronized void add(@NotNull String key, @NotNull Integer amount) {
            Object current = assertions.get(key);
            int currentValue = current instanceof Number ? ((Number) current).intValue() : 0;
            assertions.put(key, Integer.valueOf(currentValue + amount.intValue()));
        }

        synchronized void set(@NotNull String key, @Nullable Object value) {
            assertions.put(key, value);
        }

        synchronized void trace(@NotNull String event, @Nullable Map<String, Object> details) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("event", event);
            if (details != null && !details.isEmpty()) {
                entry.put("details", new LinkedHashMap<String, Object>(details));
            }
            trace.add(entry);
        }

        synchronized void complete() {
            if (completed) {
                return;
            }
            completed = true;
            assertions.put("pass", Boolean.valueOf(!containsFailureSignal()));
            EntityScenarioArtifacts.write(scenarioId, trace, assertions);
        }

        private boolean containsFailureSignal() {
            for (Map.Entry<String, Object> entry : assertions.entrySet()) {
                if ("pass".equals(entry.getKey())) {
                    continue;
                }
                Object value = entry.getValue();
                if (value == null) {
                    return true;
                }
                if (value instanceof Number && ((Number) value).doubleValue() < 0.0D) {
                    return true;
                }
                if (value instanceof Boolean && !((Boolean) value).booleanValue()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static @NotNull Map<String, Object> singletonDetail(@NotNull String key, @Nullable Object value) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put(key, value);
        return details;
    }
}

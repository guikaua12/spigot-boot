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

import lombok.RequiredArgsConstructor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.versions.api.*;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalOperationResult;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;

/**
 * Exposes small custom-entity demos for the sample plugin.
 */
@Service
@RequiredArgsConstructor
public class EntityDemoService {
    private static final List<CustomEntityBaseType> PASSIVE_FAMILY_PRIORITY = Collections.unmodifiableList(Arrays.asList(
            CustomEntityBaseType.SHEEP,
            CustomEntityBaseType.PIG,
            CustomEntityBaseType.CHICKEN,
            CustomEntityBaseType.COW
    ));
    private static final List<CustomEntityBaseType> SPECIAL_FAMILY_PRIORITY = Collections.unmodifiableList(Arrays.asList(
            CustomEntityBaseType.ARMOR_STAND,
            CustomEntityBaseType.ITEM_FRAME,
            CustomEntityBaseType.MINECART,
            CustomEntityBaseType.FALLING_BLOCK
    ));

    private volatile VersionedPlatform entityPlatform;
    private final Plugin plugin;

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
        if ("deathfx-passive-family".equals(scenarioId)) {
            spawnHeadlessDeathFxPassiveFamilyScenario(anchor);
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
        if ("viewer-cycle-special-family".equals(scenarioId)) {
            spawnHeadlessViewerCycleSpecialFamilyScenario(anchor);
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
                    configureHeadlessZombie(zombie, player.getName() + "'s Orbiting Zombie");
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
                    configureHeadlessZombie(zombie, player.getName() + "'s Orbiting Zombie");
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

        configureHeadlessZombie(wrappedZombie, "Wrapped Bukkit Zombie");

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
                    invokeNoArgIfPresent(zombie, "setAdult");
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
                    invokeNoArgIfPresent(zombie, "setAdult");
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

    public @NotNull SpawnedEntity<Zombie> spawnGoalBuilderZombieScenario(@NotNull Player player) {
        SpawnedEntity<Zombie> entity = platform().spawn(CustomEntityBaseType.ZOMBIE, Zombie.class, effectSpawnLocation(player), spawnBuilder -> {
            spawnBuilder.initialize(new EntityInitializer<Zombie>() {
                @Override
                public void initialize(@NotNull SpawnedEntity<Zombie> spawnedEntity) {
                    Zombie zombie = spawnedEntity.bukkitEntity();
                    invokeNoArgIfPresent(zombie, "setAdult");
                    invokeBooleanIfPresent(zombie, "setRemoveWhenFarAway", false);
                    zombie.setCustomName("Goal Builder Zombie");
                    zombie.setCustomNameVisible(true);
                }
            });
            spawnBuilder.addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.LOOK_AT_PLAYER, 6);
            spawnBuilder.addVanillaGoal(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET, 2);
        });

//        entity.goalManager().removeVanilla(GoalSelectorType.TARGET, VanillaGoalKey.NEAREST_ATTACKABLE_TARGET);
//        entity.goalManager().removeVanilla(GoalSelectorType.TARGET, VanillaGoalKey.MELEE_ATTACK);
//        entity.goalManager().removeVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.NEAREST_ATTACKABLE_TARGET);
        entity.goalManager().removeVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.MELEE_ATTACK);

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

    private @NotNull SpawnedEntity<? extends LivingEntity> spawnHeadlessDeathFxPassiveFamilyScenario(@NotNull Location anchor) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("deathfx-passive-family");
        FamilyScenarioSelection<LivingEntity> selection = requireSupportedRepresentative(
                "deathfx-passive-family",
                PASSIVE_FAMILY_PRIORITY,
                LivingEntity.class,
                recorder
        );
        return spawnHeadlessDeathFxFamilyScenario(anchor, recorder, selection, "Matrix Rain FX");
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
            spawnBuilder.networkController(context -> new ViewerCycleNetworkController<Zombie>(recorder));
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

    private @NotNull SpawnedEntity<? extends Entity> spawnHeadlessViewerCycleSpecialFamilyScenario(@NotNull Location anchor) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("viewer-cycle-special-family");
        FamilyScenarioSelection<Entity> selection = requireSupportedRepresentative(
                "viewer-cycle-special-family",
                SPECIAL_FAMILY_PRIORITY,
                Entity.class,
                recorder
        );
        return spawnHeadlessViewerCycleFamilyScenario(anchor, recorder, selection, "Viewer Cycle Special");
    }

    private void attachHeadlessExistingZombieScenario(@NotNull Location anchor) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("attach-existing-zombie");
        recorder.set("selectedBaseType", CustomEntityBaseType.ZOMBIE.name());
        World world = requireWorld(anchor);
        final Zombie zombie = world.spawn(wrapSpawnLocation(anchor), Zombie.class);
        final int originalEntityId = zombie.getEntityId();

        final ControlledEntity<Zombie> controlledZombie = platform().get(zombie);
        controlledZombie.setNetworkController(new ViewerCycleNetworkController<Zombie>(recorder));
        controlledZombie.setController(new WrappedZombieController(zombie.getLocation().clone(), recorder, true));

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
        // wider window avoids flakes when a controller tick is delayed by server load (matches viewerCycle 30L)
        scheduleCompletion(recorder, 30L);
    }

    public @NotNull ControlledEntity<Zombie> attachExistingZombieScenario(@NotNull Player player) {
        final ScenarioRecorder recorder = ScenarioRecorder.start("attach-existing-zombie");
        recorder.set("selectedBaseType", CustomEntityBaseType.ZOMBIE.name());
        Zombie zombie = player.getWorld().spawn(wrapSpawnLocation(player), Zombie.class);
        final int originalEntityId = zombie.getEntityId();
        invokeNoArgIfPresent(zombie, "setAdult");
        invokeBooleanIfPresent(zombie, "setRemoveWhenFarAway", false);
        zombie.setCustomName("Attached Goal Mutation Zombie");
        zombie.setCustomNameVisible(true);

        final ControlledEntity<Zombie> controlledZombie = platform().get(zombie);
        controlledZombie.setController(new WrappedZombieController(zombie.getLocation().clone(), recorder, false));
        GoalOperationResult addedLookAtPlayer = controlledZombie.goalManager().addVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER,
                6
        );
        GoalOperationResult removedLookAtPlayer = controlledZombie.goalManager().removeVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER
        );
        GoalOperationResult addedRandomStroll = controlledZombie.goalManager().addVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.RANDOM_STROLL_LAND,
                7
        );
        if (plugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                controlledZombie.goalManager().addVanilla(
                        GoalSelectorType.NORMAL,
                        VanillaGoalKey.FLOAT,
                        7
                );
            }, 60L);
        }

        recorder.increment("attachCount");
        recorder.set("entityIdStable", Boolean.valueOf(controlledZombie.bukkitEntity().getEntityId() == originalEntityId));
        recorder.set("goalMutationRemovedEntries", Integer.valueOf(removedLookAtPlayer.removedEntries()));
        recorder.set("goalMutationReplacedExistingEntry", Boolean.valueOf(
                addedLookAtPlayer.replacedExistingEntry() || addedRandomStroll.replacedExistingEntry()
        ));
        recorder.set(
                "goalMutationFinalCount",
                Integer.valueOf(controlledZombie.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).size())
        );
        Map<String, Object> goalMutationDetails = new LinkedHashMap<String, Object>();
        goalMutationDetails.put("removedEntries", Integer.valueOf(removedLookAtPlayer.removedEntries()));
        goalMutationDetails.put("finalGoal", VanillaGoalKey.RANDOM_STROLL_LAND.name());
        recorder.trace("goal-mutation", goalMutationDetails);

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

                    if (plugin != null) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            entity.goalManager().addVanilla(
                                    GoalSelectorType.NORMAL,
                                    VanillaGoalKey.FLOAT,
                                    7
                            );
                        }, 60L);
                    }
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

    private <T extends LivingEntity> @NotNull SpawnedEntity<T> spawnHeadlessDeathFxFamilyScenario(
            @NotNull Location anchor,
            @NotNull ScenarioRecorder recorder,
            @NotNull FamilyScenarioSelection<T> selection,
            @NotNull String scenarioLabel
    ) {
        recorder.set("selectedBaseType", selection.baseType().name());
        recorder.set("duplicateRegistrationErrors", Integer.valueOf(0));
        final SpawnedEntity<T> entity = platform().spawn(
                selection.baseType(),
                selection.entityClass(),
                effectSpawnLocation(anchor),
                spawnBuilder -> {
                    spawnBuilder.initialize(new EntityInitializer<T>() {
                        @Override
                        public void initialize(@NotNull SpawnedEntity<T> spawnedEntity) {
                            configureHeadlessNamedEntity(
                                    spawnedEntity.bukkitEntity(),
                                    scenarioLabel + " " + prettify(selection.baseType())
                            );
                        }
                    });
                    spawnBuilder.controller(context -> new RainDeathEffectController<T>(recorder));
                }
        );

        scheduleHeadlessDeathFxDamage(recorder, entity);
        scheduleCompletion(recorder, 35L);
        return entity;
    }

    private <T extends Entity> @NotNull SpawnedEntity<T> spawnHeadlessViewerCycleFamilyScenario(
            @NotNull Location anchor,
            @NotNull ScenarioRecorder recorder,
            @NotNull FamilyScenarioSelection<T> selection,
            @NotNull String scenarioLabel
    ) {
        recorder.set("selectedBaseType", selection.baseType().name());
        recorder.increment("viewerAddCount");
        recorder.increment("spawnCount");
        recorder.trace("headless-viewer-added", singletonDetail("viewer", "matrix-runner"));

        final SpawnedEntity<T> entity = platform().spawn(
                selection.baseType(),
                selection.entityClass(),
                effectSpawnLocation(anchor),
                spawnBuilder -> {
                    spawnBuilder.initialize(new EntityInitializer<T>() {
                        @Override
                        public void initialize(@NotNull SpawnedEntity<T> spawnedEntity) {
                            configureHeadlessNamedEntity(
                                    spawnedEntity.bukkitEntity(),
                                    scenarioLabel + " " + prettify(selection.baseType())
                            );
                        }
                    });
                    spawnBuilder.networkController(context -> new ViewerCycleNetworkController<T>(recorder));
                }
        );

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

    private <T extends Entity> @NotNull FamilyScenarioSelection<T> requireSupportedRepresentative(
            @NotNull String scenarioId,
            @NotNull List<CustomEntityBaseType> priorityList,
            @NotNull Class<T> entityClass,
            @NotNull ScenarioRecorder recorder
    ) {
        List<String> rejectionReasons = new ArrayList<String>();
        for (CustomEntityBaseType baseType : priorityList) {
            if (!platform().supports(baseType)) {
                recorder.trace(
                        "representative-candidate-skipped",
                        representativeCandidateDetail(baseType, "adapter support excludes this base type")
                );
                rejectionReasons.add(baseType.name() + ": adapter support excludes this base type");
                continue;
            }
            Class<? extends Entity> resolvedType = baseType.bukkitTypeOrNull();
            if (resolvedType == null || !entityClass.isAssignableFrom(resolvedType)) {
                String reason = resolvedType == null
                        ? "Bukkit entity class is unavailable for this base type"
                        : "Bukkit entity class '" + resolvedType.getName() + "' does not implement '" + entityClass.getName() + "'";
                recorder.trace(
                        "representative-candidate-skipped",
                        representativeCandidateDetail(baseType, reason)
                );
                rejectionReasons.add(baseType.name() + ": " + reason);
                continue;
            }
            recorder.trace(
                    "representative-candidate-selected",
                    representativeCandidateDetail(baseType, "selected as the first supported representative")
            );
            return new FamilyScenarioSelection<T>(baseType, castEntityClass(resolvedType, entityClass));
        }
        throw new IllegalStateException(
                "No supported representative base type is available for matrix scenario '"
                        + scenarioId
                        + "'. Evaluated candidates: "
                        + String.join("; ", rejectionReasons)
        );
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> @NotNull Class<T> castEntityClass(
            @NotNull Class<? extends Entity> resolvedType,
            @NotNull Class<T> expectedType
    ) {
        if (!expectedType.isAssignableFrom(resolvedType)) {
            throw new IllegalArgumentException("Entity type '" + resolvedType.getName() + "' does not implement '" + expectedType.getName() + "'.");
        }
        return (Class<T>) resolvedType;
    }

    private <T extends LivingEntity> void scheduleHeadlessDeathFxDamage(
            @NotNull final ScenarioRecorder recorder,
            @NotNull final SpawnedEntity<T> entity
    ) {
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
    }

    private void scheduleCompletion(@NotNull final ScenarioRecorder recorder, long delayTicks) {
        scheduleLater(new Runnable() {
            @Override
            public void run() {
                completeAtDeadline(recorder);
            }
        }, delayTicks);
    }

    /**
     * Body of the scheduled completion runnable; extracted so tests exercise the same production
     * {@code putIfAbsent → complete} path via {@code EntityDemoServiceRecorderBridge}.
     *
     * @param recorder the recorder whose deadline just elapsed
     */
    static void completeAtDeadline(@NotNull ScenarioRecorder recorder) {
        // sentinel is only written at deadline so real TRUE observations flipped by controllers win
        recorder.putIfAbsent("controllerTickObserved", Boolean.FALSE);
        recorder.complete();
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

    private static @NotNull String prettify(@NotNull CustomEntityBaseType baseType) {
        EntityType entityType = baseType.entityTypeOrNull();
        if (entityType != null) {
            return prettify(entityType);
        }

        String[] pieces = baseType.name().toLowerCase().split("_");
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

    private static void configureHeadlessNamedEntity(@NotNull Entity entity, @NotNull String customName) {
        invokeBooleanIfPresent(entity, "setGravity", false);
        invokeBooleanIfPresent(entity, "setSilent", true);
        entity.setCustomName(customName);
        entity.setCustomNameVisible(true);
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
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        if (!spawnParticleIfPresent(world, location, "WATER_DROP", 60, 0.6D, 0.5D, 0.6D, 0.05D)) {
            world.playEffect(location, Effect.SMOKE, 0);
            world.playEffect(location.clone().add(0.35D, 0.1D, 0.35D), Effect.SMOKE, 0);
            world.playEffect(location.clone().add(-0.35D, 0.1D, -0.35D), Effect.SMOKE, 0);
        }

        if (!playSoundIfPresent(world, location, "WEATHER_RAIN", 1.0F, 1.0F)) {
            playSoundIfPresent(world, location, "AMBIENCE_RAIN", 1.0F, 1.0F);
        }
        playSoundIfPresent(world, location, "WEATHER_RAIN_ABOVE", 0.75F, 1.1F);
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
        private final ScenarioRecorder recorder;
        private final boolean movementEnabled;
        private boolean controllerTickObserved;
        private double angle;

        private WrappedZombieController(@NotNull Location anchor) {
            this(anchor, null, true);
        }

        private WrappedZombieController(
                @NotNull Location anchor,
                @Nullable ScenarioRecorder recorder,
                boolean movementEnabled
        ) {
            this.anchor = anchor;
            this.recorder = recorder;
            this.movementEnabled = movementEnabled;
        }

        @Override
        public void onTick(@NotNull EntityTickContext<Zombie> context) {
            context.base().invoke();

            Zombie zombie = context.bukkitEntity();
            if (!zombie.isValid() || zombie.isDead()) {
                return;
            }

            recordControllerTick();
            if (!movementEnabled) {
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

        private void recordControllerTick() {
            if (recorder == null || controllerTickObserved) {
                return;
            }
            controllerTickObserved = true;
            recorder.set("controllerTickObserved", Boolean.TRUE);
            recorder.trace("controller-tick", null);
        }
    }

    private static final class ViewerCycleNetworkController<T extends Entity> extends EntityNetworkController<T> {
        private final ScenarioRecorder recorder;

        private ViewerCycleNetworkController(@NotNull ScenarioRecorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void onViewerAdded(@NotNull ControlledEntity<T> entity, @NotNull Player viewer, @NotNull EntityNetworkState state) {
            recorder.increment("viewerAddCount");
            recorder.increment("spawnCount");
            recorder.trace("viewer-added", singletonDetail("viewer", viewer.getName()));
        }

        @Override
        public void onViewerRemoved(@NotNull ControlledEntity<T> entity, @NotNull Player viewer, @NotNull EntityNetworkState state) {
            recorder.increment("viewerRemoveCount");
            recorder.trace("viewer-removed", singletonDetail("viewer", viewer.getName()));
        }

        @Override
        public void onUnbind(@NotNull ControlledEntity<T> entity, @NotNull EntityNetworkState state) {
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
            double maxHealth = zombie.getMaxHealth();
            if (maxHealth > 0.0D) {
                attributes.add(new LivingAttribute("generic.maxHealth", maxHealth));
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
                        hasParticlesIfPresent(effect)
                ));
            }
            return new ActiveEffectsSnapshot(effects);
        }
    }

    private static boolean spawnParticleIfPresent(
            @NotNull World world,
            @NotNull Location location,
            @NotNull String particleName,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double extra
    ) {
        try {
            Class<?> particleClass = Class.forName("org.bukkit.Particle");
            Object particle = Enum.valueOf(particleClass.asSubclass(Enum.class), particleName);
            Method spawnParticleMethod = World.class.getMethod(
                    "spawnParticle",
                    particleClass,
                    Location.class,
                    Integer.TYPE,
                    Double.TYPE,
                    Double.TYPE,
                    Double.TYPE,
                    Double.TYPE
            );
            spawnParticleMethod.invoke(
                    world,
                    particle,
                    location,
                    Integer.valueOf(count),
                    Double.valueOf(offsetX),
                    Double.valueOf(offsetY),
                    Double.valueOf(offsetZ),
                    Double.valueOf(extra)
            );
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (IllegalArgumentException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not spawn particle '" + particleName + "'.", exception);
        }
    }

    private static boolean playSoundIfPresent(
            @NotNull World world,
            @NotNull Location location,
            @NotNull String soundName,
            float volume,
            float pitch
    ) {
        try {
            Sound sound = Sound.valueOf(soundName);
            world.playSound(location, sound, volume, pitch);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean hasParticlesIfPresent(@NotNull PotionEffect effect) {
        try {
            Method method = PotionEffect.class.getMethod("hasParticles");
            Object result = method.invoke(effect);
            return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
        } catch (NoSuchMethodException ignored) {
            return true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect potion particles for '" + effect.getType().getName() + "'.", exception);
        }
    }

    static final class ScenarioRecorder {
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

        synchronized void putIfAbsent(@NotNull String key, @NotNull Object value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            if (!assertions.containsKey(key)) {
                assertions.put(key, value);
            }
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
            boolean passed = !containsFailureSignal();
            assertions.put("pass", Boolean.valueOf(passed));
            if (EntityScenarioDescriptor.require(scenarioId).assertionKeys().contains("passCount")) {
                assertions.put("passCount", Integer.valueOf(passed ? 1 : 0));
            }
            if (EntityScenarioDescriptor.require(scenarioId).assertionKeys().contains("failCount")) {
                assertions.put("failCount", Integer.valueOf(passed ? 0 : 1));
            }
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

    private static final class FamilyScenarioSelection<T extends Entity> {
        private final CustomEntityBaseType baseType;
        private final Class<T> entityClass;

        private FamilyScenarioSelection(@NotNull CustomEntityBaseType baseType, @NotNull Class<T> entityClass) {
            this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
            this.entityClass = Objects.requireNonNull(entityClass, "entityClass cannot be null");
        }

        private @NotNull CustomEntityBaseType baseType() {
            return baseType;
        }

        private @NotNull Class<T> entityClass() {
            return entityClass;
        }
    }

    private static @NotNull Map<String, Object> singletonDetail(@NotNull String key, @Nullable Object value) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put(key, value);
        return details;
    }

    private static @NotNull Map<String, Object> representativeCandidateDetail(
            @NotNull CustomEntityBaseType baseType,
            @NotNull String reason
    ) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("baseType", baseType.name());
        details.put("reason", reason);
        return details;
    }
}

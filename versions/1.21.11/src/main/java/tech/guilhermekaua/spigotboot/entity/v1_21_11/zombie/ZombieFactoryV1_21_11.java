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
package tech.guilhermekaua.spigotboot.entity.v1_21_11.zombie;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.EntityCollideContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityDamageContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityDieContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityEquipmentSlot;
import tech.guilhermekaua.spigotboot.entity.api.EntityInteractContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityInteractionHand;
import tech.guilhermekaua.spigotboot.entity.api.EntityInteractionResult;
import tech.guilhermekaua.spigotboot.entity.api.EntityInventoryChangeContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityMoveContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityPositionPassengerContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityPushContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.entity.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.FieldCopySupport;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spawns and attaches real 1.21.11 native zombie subclasses and binds them to the shared runtime lifecycle.
 *
 * @since 2.0.2
 */
public final class ZombieFactoryV1_21_11 {
    private static final String GENERATED_CLASS_NAME =
            "tech.guilhermekaua.spigotboot.entity.generated.v1_21_11.SpigotBootZombieV1_21_11";
    private static final String HOOK_TICK = "tick";
    private static final String HOOK_MOVE = "move";
    private static final String HOOK_PUSH = "push";
    private static final String HOOK_DAMAGE = "damage";
    private static final String HOOK_INTERACT = "interact";
    private static final String HOOK_DIE = "die";
    private static final String HOOK_REMOVE = "remove";
    private static final String HOOK_REMOVE_WITH_CAUSE = "removeWithCause";
    private static final String HOOK_COLLIDE = "collide";
    private static final String HOOK_POSITION_PASSENGER = "positionPassenger";
    private static final String HOOK_INVENTORY_CHANGE = "inventoryChange";
    private static final String HOOK_INVENTORY_CHANGE_SILENT = "inventoryChangeSilent";
    private static final Object[] EMPTY_ARGUMENTS = new Object[0];

    private final GeneratedNativeEntityClassFactory classFactory = new GeneratedNativeEntityClassFactory();
    private final ZombieHookBinderV1_21_11 hookBinder = new ZombieHookBinderV1_21_11();

    private volatile Class<?> generatedZombieClass;

    public @NotNull CustomEntityHandle<Zombie> spawn(
            @NotNull CustomEntityDefinition<?> definition,
            @NotNull CustomEntitySpawnRequest spawnRequest,
            @NotNull NativeEntityLifecycle<Zombie> lifecycle
    ) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(spawnRequest, "spawnRequest cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        Location location = spawnRequest.location();
        World bukkitWorld = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        Object worldHandle = resolveWorldHandle(bukkitWorld);

        Class<?> zombieSuperclass = resolveZombieSuperclass();
        Class<?> zombieClass = generatedZombieClass(zombieSuperclass);
        Object nmsZombie = instantiateZombie(zombieClass, worldHandle);
        bindRuntimeLifecycle(nmsZombie, zombieSuperclass, lifecycle);

        Method moveToMethod = ReflectionSupport.requireMethodBySignature(
                zombieSuperclass,
                void.class,
                double.class,
                double.class,
                double.class,
                float.class,
                float.class
        );
        ReflectionSupport.invoke(
                moveToMethod,
                nmsZombie,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );

        ReflectionSupport.invoke(resolveAddEntityMethod(worldHandle.getClass(), zombieSuperclass), worldHandle, nmsZombie);

        Zombie zombie = (Zombie) resolveBukkitEntity(nmsZombie);
        lifecycle.bind(zombie);
        lifecycle.onSpawn();
        return (CustomEntityHandle<Zombie>) lifecycle.handle();
    }

    public @NotNull ControlledEntity<Zombie> attach(
            @NotNull Zombie zombie,
            @NotNull NativeEntityLifecycle<Zombie> lifecycle
    ) {
        Objects.requireNonNull(zombie, "zombie cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        Object currentHandle = resolveNativeHandle(zombie);
        ControlledEntity<Zombie> existing = resolveExistingControlledEntity(currentHandle);
        if (existing != null) {
            return existing;
        }

        Class<?> zombieSuperclass = resolveZombieSuperclass();
        if (currentHandle.getClass() != zombieSuperclass) {
            throw new IllegalArgumentException(
                    "Minecraft 1.21.11 controller attachment currently supports only vanilla zombies and already-hooked Spigot Boot zombies."
            );
        }

        Object worldHandle = resolveWorldHandle(Objects.requireNonNull(zombie.getWorld(), "zombie world cannot be null"));
        Class<?> zombieClass = generatedZombieClass(zombieSuperclass);
        Object replacementHandle = instantiateZombie(zombieClass, worldHandle);

        FieldCopySupport.copyInstanceFields(currentHandle, replacementHandle);
        bindRuntimeLifecycle(replacementHandle, zombieSuperclass, lifecycle);
        rebindBukkitZombie(zombie, replacementHandle);
        replaceModernWorldReferences(currentHandle, replacementHandle);
        rewireModernVehicleAndPassengerReferences(currentHandle, replacementHandle);
        refreshModernBukkitWrappers(zombie);
        markModernEntityRemoved(currentHandle);
        lifecycle.bind((Zombie) resolveBukkitEntity(replacementHandle));
        scheduleRepairPass(lifecycle, zombie, currentHandle, replacementHandle);
        return lifecycle.handle();
    }

    private synchronized @NotNull Class<?> generatedZombieClass(@NotNull Class<?> zombieSuperclass) {
        if (generatedZombieClass != null) {
            return generatedZombieClass;
        }

        generatedZombieClass = classFactory.createSubclass(
                zombieSuperclass,
                GENERATED_CLASS_NAME,
                hookBinder.hookSpecs(zombieSuperclass)
        );
        return generatedZombieClass;
    }

    private void bindRuntimeLifecycle(
            @NotNull Object nativeZombie,
            @NotNull Class<?> zombieSuperclass,
            @NotNull NativeEntityLifecycle<Zombie> lifecycle
    ) {
        AbstractRuntimeControlledEntity<Zombie> controlledEntity = requireRuntimeLifecycle(lifecycle);
        controlledEntity.bindHookBinder(hookBinder);
        classFactory.installInterceptor(nativeZombie, hookBinder.hookSpecs(zombieSuperclass));
        classFactory.bindLifecycle(nativeZombie, lifecycle);
    }

    private static @NotNull AbstractRuntimeControlledEntity<Zombie> requireRuntimeLifecycle(
            @NotNull NativeEntityLifecycle<Zombie> lifecycle
    ) {
        ControlledEntity<Zombie> handle = lifecycle.handle();
        if (!(handle instanceof AbstractRuntimeControlledEntity)) {
            throw new IllegalStateException(
                    "The runtime lifecycle handle must extend AbstractRuntimeControlledEntity for zombie attachment."
            );
        }
        return (AbstractRuntimeControlledEntity<Zombie>) handle;
    }

    private static @Nullable ControlledEntity<Zombie> resolveExistingControlledEntity(@NotNull Object nativeHandle) {
        if (!(nativeHandle instanceof LifecycleAwareNativeEntity)) {
            return null;
        }
        NativeEntityLifecycle<?> lifecycle = ((LifecycleAwareNativeEntity) nativeHandle).spigotBootGetLifecycle();
        if (lifecycle == null || !(lifecycle.handle() instanceof ControlledEntity)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        ControlledEntity<Zombie> controlledEntity = (ControlledEntity<Zombie>) lifecycle.handle();
        return controlledEntity;
    }

    private static @NotNull Object resolveWorldHandle(@NotNull World bukkitWorld) {
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(
                bukkitWorld.getClass(),
                new String[]{"getHandle"}
        );
        return ReflectionSupport.invoke(getHandleMethod, bukkitWorld);
    }

    private static @NotNull Class<?> resolveZombieSuperclass() {
        return ReflectionSupport.requireClass(
                "net.minecraft.world.entity.monster.zombie.Zombie",
                "net.minecraft.world.entity.monster.Zombie",
                "net.minecraft.world.entity.monster.EntityZombie"
        );
    }

    private static @NotNull Object instantiateZombie(@NotNull Class<?> zombieType, @NotNull Object worldHandle) {
        try {
            Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(zombieType, worldHandle.getClass());
            return ReflectionSupport.instantiate(constructor, worldHandle);
        } catch (IllegalStateException ignored) {
            Class<?> entityTypeClass = ReflectionSupport.requireClass("net.minecraft.world.entity.EntityType");
            Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(
                    zombieType,
                    entityTypeClass,
                    worldHandle.getClass()
            );
            return ReflectionSupport.instantiate(constructor, resolveZombieEntityType(entityTypeClass), worldHandle);
        }
    }

    private static @NotNull Object resolveZombieEntityType(@NotNull Class<?> entityTypeClass) {
        try {
            Field field = entityTypeClass.getField("ZOMBIE");
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not resolve EntityType.ZOMBIE for 1.21.11.", exception);
        }
    }

    private static @NotNull Object resolveNativeHandle(@NotNull Zombie zombie) {
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(zombie.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, zombie);
    }

    private static @NotNull Entity resolveBukkitEntity(@NotNull Object nmsEntity) {
        Method getBukkitEntityMethod = ReflectionSupport.requireNamedMethod(
                nmsEntity.getClass(),
                new String[]{"getBukkitEntity"}
        );
        return (Entity) ReflectionSupport.invoke(getBukkitEntityMethod, nmsEntity);
    }

    private static @NotNull Method resolveAddEntityMethod(@NotNull Class<?> worldType, @NotNull Class<?> entityType) {
        List<String> candidateNames = new ArrayList<String>();
        candidateNames.add("addFreshEntity");
        candidateNames.add("addEntity");

        Class<?> current = worldType;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!candidateNames.contains(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || !parameterTypes[0].isAssignableFrom(entityType)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException("Could not resolve ServerLevel#addFreshEntity(Entity) for 1.21.11.");
    }

    private static void rebindBukkitZombie(@NotNull Zombie zombie, @NotNull Object replacementHandle) {
        Method setHandleMethod = ReflectionSupport.requireCompatibleMethod(
                zombie.getClass(),
                new String[]{"setHandle"},
                replacementHandle.getClass()
        );
        ReflectionSupport.invoke(setHandleMethod, zombie, replacementHandle);
    }

    private static void replaceModernWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Object level = ReflectionSupport.readField(ReflectionSupport.requireField(oldHandle.getClass(), "level"), oldHandle);
        Object entityLookup = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(level.getClass(), new String[]{"moonrise$getEntityLookup"}),
                level
        );
        int entityId = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getId"}),
                oldHandle
        )).intValue();
        Object uuid = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getUUID"}),
                oldHandle
        );

        Object entityById = ReflectionSupport.readField(ReflectionSupport.requireField(entityLookup.getClass(), "entityById"), entityLookup);
        ReflectionSupport.invoke(
                ReflectionSupport.requireCompatibleMethod(entityById.getClass(), new String[]{"put"}, long.class, replacementHandle.getClass()),
                entityById,
                Long.valueOf(entityId),
                replacementHandle
        );

        @SuppressWarnings("unchecked")
        Map<Object, Object> entityByUuid = (Map<Object, Object>) ReflectionSupport.readField(
                ReflectionSupport.requireField(entityLookup.getClass(), "entityByUUID"),
                entityLookup
        );
        entityByUuid.put(uuid, replacementHandle);

        Object accessibleEntities = ReflectionSupport.readField(
                ReflectionSupport.requireField(entityLookup.getClass(), "accessibleEntities"),
                entityLookup
        );
        ReflectionSupport.invoke(
                ReflectionSupport.requireCompatibleMethod(accessibleEntities.getClass(), new String[]{"remove"}, oldHandle.getClass()),
                accessibleEntities,
                oldHandle
        );
        ReflectionSupport.invoke(
                ReflectionSupport.requireCompatibleMethod(accessibleEntities.getClass(), new String[]{"add"}, replacementHandle.getClass()),
                accessibleEntities,
                replacementHandle
        );

        int sectionX = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"moonrise$getSectionX"}),
                oldHandle
        )).intValue();
        int sectionY = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"moonrise$getSectionY"}),
                oldHandle
        )).intValue();
        int sectionZ = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"moonrise$getSectionZ"}),
                oldHandle
        )).intValue();

        Object chunkSlices = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(entityLookup.getClass(), new String[]{"getChunk"}, int.class, int.class),
                entityLookup,
                Integer.valueOf(sectionX),
                Integer.valueOf(sectionZ)
        );
        if (chunkSlices == null) {
            chunkSlices = ReflectionSupport.invoke(
                    ReflectionSupport.requireNamedMethod(entityLookup.getClass(), new String[]{"getOrCreateChunk"}, int.class, int.class),
                    entityLookup,
                    Integer.valueOf(sectionX),
                    Integer.valueOf(sectionZ)
            );
        }
        if (chunkSlices != null) {
            ReflectionSupport.invoke(
                    ReflectionSupport.requireCompatibleMethod(chunkSlices.getClass(), new String[]{"removeEntity"}, oldHandle.getClass(), int.class),
                    chunkSlices,
                    oldHandle,
                    Integer.valueOf(sectionY)
            );
            ReflectionSupport.invoke(
                    ReflectionSupport.requireCompatibleMethod(chunkSlices.getClass(), new String[]{"addEntity"}, replacementHandle.getClass(), int.class),
                    chunkSlices,
                    replacementHandle,
                    Integer.valueOf(sectionY)
            );
        }

        if (ReflectionSupport.findField(entityLookup.getClass(), "trackerEntities") != null) {
            Object trackerEntities = ReflectionSupport.readField(
                    ReflectionSupport.requireField(entityLookup.getClass(), "trackerEntities"),
                    entityLookup
            );
            ReflectionSupport.invoke(
                    ReflectionSupport.requireCompatibleMethod(trackerEntities.getClass(), new String[]{"remove"}, oldHandle.getClass()),
                    trackerEntities,
                    oldHandle
            );
            ReflectionSupport.invoke(
                    ReflectionSupport.requireCompatibleMethod(trackerEntities.getClass(), new String[]{"add"}, replacementHandle.getClass()),
                    trackerEntities,
                    replacementHandle
            );
        }

        updateTrackedEntity(level, oldHandle, replacementHandle, entityId);
    }

    private static void updateTrackedEntity(
            @NotNull Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle,
            int entityId
    ) {
        Object trackedEntity = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"moonrise$getTrackedEntity"}),
                oldHandle
        );
        if (trackedEntity == null) {
            return;
        }

        Object chunkSource = ReflectionSupport.readField(ReflectionSupport.requireField(level.getClass(), "chunkSource"), level);
        Object chunkMap = ReflectionSupport.readField(ReflectionSupport.requireField(chunkSource.getClass(), "chunkMap"), chunkSource);
        Object entityMap = ReflectionSupport.readField(ReflectionSupport.requireField(chunkMap.getClass(), "entityMap"), chunkMap);
        ReflectionSupport.invoke(
                ReflectionSupport.requireCompatibleMethod(entityMap.getClass(), new String[]{"put"}, int.class, trackedEntity.getClass()),
                entityMap,
                Integer.valueOf(entityId),
                trackedEntity
        );

        ReflectionSupport.writeField(ReflectionSupport.requireField(trackedEntity.getClass(), "entity"), trackedEntity, replacementHandle);
        Object serverEntity = ReflectionSupport.readField(
                ReflectionSupport.requireField(trackedEntity.getClass(), "serverEntity"),
                trackedEntity
        );
        ReflectionSupport.writeField(ReflectionSupport.requireField(serverEntity.getClass(), "entity"), serverEntity, replacementHandle);

        Method setTrackedEntityMethod = ReflectionSupport.requireCompatibleMethod(
                replacementHandle.getClass(),
                new String[]{"moonrise$setTrackedEntity"},
                trackedEntity.getClass()
        );
        ReflectionSupport.invoke(setTrackedEntityMethod, replacementHandle, trackedEntity);
        ReflectionSupport.invoke(setTrackedEntityMethod, oldHandle, (Object) null);
    }

    private static void rewireModernVehicleAndPassengerReferences(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field passengersField = ReflectionSupport.requireField(oldHandle.getClass(), "passengers");
        Field vehicleField = ReflectionSupport.requireField(oldHandle.getClass(), "vehicle");

        @SuppressWarnings("unchecked")
        List<Object> passengers = (List<Object>) ReflectionSupport.readField(passengersField, oldHandle);
        if (passengers != null) {
            for (Object passenger : passengers) {
                ReflectionSupport.writeField(vehicleField, passenger, replacementHandle);
            }
        }

        Object vehicle = ReflectionSupport.readField(vehicleField, oldHandle);
        if (vehicle != null) {
            @SuppressWarnings("unchecked")
            List<Object> vehiclePassengers = (List<Object>) ReflectionSupport.readField(passengersField, vehicle);
            if (vehiclePassengers != null) {
                List<Object> replacedPassengers = new ArrayList<Object>(vehiclePassengers.size());
                for (Object passenger : vehiclePassengers) {
                    replacedPassengers.add(passenger == oldHandle ? replacementHandle : passenger);
                }
                ReflectionSupport.writeField(passengersField, vehicle, immutablePassengerList(replacedPassengers));
            }
        }
    }

    private static @NotNull Object immutablePassengerList(@NotNull List<Object> passengers) {
        Class<?> immutableListClass = ReflectionSupport.requireClass("com.google.common.collect.ImmutableList");
        Method copyOfMethod = ReflectionSupport.requireCompatibleMethod(
                immutableListClass,
                new String[]{"copyOf"},
                java.util.Collection.class
        );
        return ReflectionSupport.invoke(copyOfMethod, null, passengers);
    }

    private static void refreshModernBukkitWrappers(@NotNull Zombie zombie) {
        Field equipmentField = ReflectionSupport.findField(zombie.getClass(), "equipment");
        if (equipmentField != null) {
            ReflectionSupport.writeField(equipmentField, zombie, null);
        }
    }

    private static void markModernEntityRemoved(@NotNull Object oldHandle) {
        Class<?> removalReasonType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity$RemovalReason");
        Object discarded = ReflectionSupport.readField(ReflectionSupport.requireField(removalReasonType, "DISCARDED"), null);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "removalReason"), oldHandle, discarded);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "valid"), oldHandle, Boolean.FALSE);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "inWorld"), oldHandle, Boolean.FALSE);
        if (ReflectionSupport.findField(oldHandle.getClass(), "pluginRemoved") != null) {
            ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "pluginRemoved"), oldHandle, Boolean.TRUE);
        }
    }

    private void scheduleRepairPass(
            @NotNull NativeEntityLifecycle<Zombie> lifecycle,
            @NotNull Zombie zombie,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        requireRuntimeLifecycle(lifecycle).scheduleNextTickRepair(new Runnable() {
            @Override
            public void run() {
                rebindBukkitZombie(zombie, replacementHandle);
                replaceModernWorldReferences(oldHandle, replacementHandle);
                rewireModernVehicleAndPassengerReferences(oldHandle, replacementHandle);
                refreshModernBukkitWrappers(zombie);
            }
        });
    }

    private final class ZombieHookBinderV1_21_11 implements NativeHookBinder<Zombie> {
        private final Class<?> entityType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity");
        private final Class<?> moverType = ReflectionSupport.requireClass("net.minecraft.world.entity.MoverType");
        private final Class<?> vec3Type = ReflectionSupport.requireClass("net.minecraft.world.phys.Vec3");
        private final Class<?> serverLevelType = ReflectionSupport.requireClass("net.minecraft.server.level.ServerLevel");
        private final Class<?> damageSourceType = ReflectionSupport.requireClass("net.minecraft.world.damagesource.DamageSource");
        private final Class<?> playerType = ReflectionSupport.requireClass("net.minecraft.world.entity.player.Player");
        private final Class<?> interactionHandType = ReflectionSupport.requireClass("net.minecraft.world.InteractionHand");
        private final Class<?> interactionResultType = ReflectionSupport.requireClass("net.minecraft.world.InteractionResult");
        private final Class<?> interactionResultSuccessType =
                ReflectionSupport.requireClass("net.minecraft.world.InteractionResult$Success");
        private final Class<?> removalReasonType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity$RemovalReason");
        private final Class<?> moveFunctionType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity$MoveFunction");
        private final Class<?> equipmentSlotType = ReflectionSupport.requireClass("net.minecraft.world.entity.EquipmentSlot");
        private final Class<?> itemStackType = ReflectionSupport.requireClass("net.minecraft.world.item.ItemStack");
        private final Field interactionResultSuccessField = ReflectionSupport.requireField(interactionResultType, "SUCCESS");
        private final Field interactionResultConsumeField = ReflectionSupport.requireField(interactionResultType, "CONSUME");
        private final Field interactionResultFailField = ReflectionSupport.requireField(interactionResultType, "FAIL");
        private final Field interactionResultPassField = ReflectionSupport.requireField(interactionResultType, "PASS");
        private final Field interactionResultTryWithEmptyHandField =
                ReflectionSupport.requireField(interactionResultType, "TRY_WITH_EMPTY_HAND");
        private final Method interactionResultSuccessSwingSourceMethod =
                ReflectionSupport.requireNamedMethod(interactionResultSuccessType, new String[]{"swingSource"});

        @Override
        public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs(@NotNull Class<?> nativeType) {
            List<GeneratedNativeHookSpec> hookSpecs = new ArrayList<GeneratedNativeHookSpec>();
            hookSpecs.add(GeneratedNativeHookSpec.of(HOOK_TICK, ReflectionSupport.requireNamedMethod(nativeType, new String[]{"tick"})));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_MOVE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"move"}, moverType, vec3Type)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_PUSH,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"push"}, double.class, double.class, double.class)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_DAMAGE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"hurtServer"}, serverLevelType, damageSourceType, float.class)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_INTERACT,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"interact"}, playerType, interactionHandType)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_DIE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"die"}, damageSourceType)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_REMOVE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"remove"}, removalReasonType)
            ));
            Method removeWithCauseMethod = requireMethodByNameAndCount(nativeType, "remove", 2);
            if (removeWithCauseMethod != null) {
                hookSpecs.add(GeneratedNativeHookSpec.of(HOOK_REMOVE_WITH_CAUSE, removeWithCauseMethod));
            }
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_COLLIDE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"push"}, entityType)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_POSITION_PASSENGER,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"positionRider"}, entityType, moveFunctionType)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_INVENTORY_CHANGE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"onEquipItem"}, equipmentSlotType, itemStackType, itemStackType)
            ));
            Method inventoryChangeSilentMethod = requireMethodByNameAndCount(nativeType, "onEquipItem", 4);
            if (inventoryChangeSilentMethod != null) {
                hookSpecs.add(GeneratedNativeHookSpec.of(HOOK_INVENTORY_CHANGE_SILENT, inventoryChangeSilentMethod));
            }
            return hookSpecs;
        }

        @Override
        public @Nullable Object dispatch(
                @NotNull AbstractRuntimeControlledEntity<Zombie> controlledEntity,
                @NotNull LifecycleAwareNativeEntity nativeEntity,
                @NotNull String hookName,
                @Nullable Object[] arguments
        ) {
            if (HOOK_TICK.equals(hookName)) {
                controlledEntity.dispatchTick(new ContextualBaseInvoker<EntityTickContext<Zombie>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityTickContext<Zombie> context) {
                        nativeEntity.spigotBootInvokeBase(HOOK_TICK, EMPTY_ARGUMENTS);
                        return null;
                    }
                });
                return null;
            }

            if (HOOK_MOVE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 2);
                controlledEntity.dispatchMove(
                        resolveVecX(rawArguments[1]),
                        resolveVecY(rawArguments[1]),
                        resolveVecZ(rawArguments[1]),
                        new ContextualBaseInvoker<EntityMoveContext<Zombie>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityMoveContext<Zombie> context) {
                                nativeEntity.spigotBootInvokeBase(
                                        HOOK_MOVE,
                                        new Object[]{rawArguments[0], createVec3(context.x(), context.y(), context.z())}
                                );
                                return null;
                            }
                        }
                );
                return null;
            }

            if (HOOK_PUSH.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 3);
                controlledEntity.dispatchPush(
                        ((Double) rawArguments[0]).doubleValue(),
                        ((Double) rawArguments[1]).doubleValue(),
                        ((Double) rawArguments[2]).doubleValue(),
                        new ContextualBaseInvoker<EntityPushContext<Zombie>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityPushContext<Zombie> context) {
                                nativeEntity.spigotBootInvokeBase(
                                        HOOK_PUSH,
                                        new Object[]{
                                                Double.valueOf(context.x()),
                                                Double.valueOf(context.y()),
                                                Double.valueOf(context.z())
                                        }
                                );
                                return null;
                            }
                        }
                );
                return null;
            }

            if (HOOK_DAMAGE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 3);
                return Boolean.valueOf(controlledEntity.dispatchDamage(
                        ((Float) rawArguments[2]).floatValue(),
                        new ContextualBaseInvoker<EntityDamageContext<Zombie>, Boolean>() {
                            @Override
                            public Boolean invoke(@NotNull EntityDamageContext<Zombie> context) {
                                Object result = nativeEntity.spigotBootInvokeBase(
                                        HOOK_DAMAGE,
                                        new Object[]{rawArguments[0], rawArguments[1], Float.valueOf(context.amount())}
                                );
                                return Boolean.valueOf(result != null && ((Boolean) result).booleanValue());
                            }
                        }
                ));
            }

            if (HOOK_INTERACT.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 2);
                Player player = (Player) resolveBukkitEntity(rawArguments[0]);
                Object[] baseInteractionResultHolder = new Object[1];
                EntityInteractionResult result = controlledEntity.dispatchInteract(
                        player,
                        toApiHand(rawArguments[1]),
                        new ContextualBaseInvoker<EntityInteractContext<Zombie>, EntityInteractionResult>() {
                            @Override
                            public EntityInteractionResult invoke(@NotNull EntityInteractContext<Zombie> context) {
                                Object baseResult = nativeEntity.spigotBootInvokeBase(
                                        HOOK_INTERACT,
                                        new Object[]{rawArguments[0], rawArguments[1]}
                                );
                                baseInteractionResultHolder[0] = baseResult;
                                return toApiInteractionResult(baseResult);
                            }
                        }
                );
                return toNmsInteractionResult(result, baseInteractionResultHolder[0]);
            }

            if (HOOK_DIE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 1);
                controlledEntity.dispatchDie(new ContextualBaseInvoker<EntityDieContext<Zombie>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityDieContext<Zombie> context) {
                        nativeEntity.spigotBootInvokeBase(HOOK_DIE, new Object[]{rawArguments[0]});
                        return null;
                    }
                });
                return null;
            }

            if (HOOK_REMOVE.equals(hookName) || HOOK_REMOVE_WITH_CAUSE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, HOOK_REMOVE.equals(hookName) ? 1 : 2);
                controlledEntity.dispatchRemove(new ContextualBaseInvoker<EntityRemoveContext<Zombie>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityRemoveContext<Zombie> context) {
                        nativeEntity.spigotBootInvokeBase(hookName, rawArguments);
                        return null;
                    }
                });
                return null;
            }

            if (HOOK_COLLIDE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 1);
                controlledEntity.dispatchCollide(
                        resolveBukkitEntity(rawArguments[0]),
                        new ContextualBaseInvoker<EntityCollideContext<Zombie>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityCollideContext<Zombie> context) {
                                nativeEntity.spigotBootInvokeBase(HOOK_COLLIDE, new Object[]{rawArguments[0]});
                                return null;
                            }
                        }
                );
                return null;
            }

            if (HOOK_POSITION_PASSENGER.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 2);
                controlledEntity.dispatchPositionPassenger(
                        resolveBukkitEntity(rawArguments[0]),
                        new ContextualBaseInvoker<EntityPositionPassengerContext<Zombie>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityPositionPassengerContext<Zombie> context) {
                                nativeEntity.spigotBootInvokeBase(HOOK_POSITION_PASSENGER, rawArguments);
                                return null;
                            }
                        }
                );
                return null;
            }

            if (HOOK_INVENTORY_CHANGE.equals(hookName) || HOOK_INVENTORY_CHANGE_SILENT.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, HOOK_INVENTORY_CHANGE.equals(hookName) ? 3 : 4);
                controlledEntity.dispatchInventoryChange(
                        toApiEquipmentSlot(rawArguments[0]),
                        toBukkitItem(rawArguments[1]),
                        toBukkitItem(rawArguments[2]),
                        new ContextualBaseInvoker<EntityInventoryChangeContext<Zombie>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityInventoryChangeContext<Zombie> context) {
                                if (HOOK_INVENTORY_CHANGE_SILENT.equals(hookName)) {
                                    nativeEntity.spigotBootInvokeBase(
                                            HOOK_INVENTORY_CHANGE_SILENT,
                                            new Object[]{
                                                    toNmsEquipmentSlot(context.slot()),
                                                    toNmsItem(context.previousItem()),
                                                    toNmsItem(context.newItem()),
                                                    rawArguments[3]
                                            }
                                    );
                                } else {
                                    nativeEntity.spigotBootInvokeBase(
                                            HOOK_INVENTORY_CHANGE,
                                            new Object[]{
                                                    toNmsEquipmentSlot(context.slot()),
                                                    toNmsItem(context.previousItem()),
                                                    toNmsItem(context.newItem())
                                            }
                                    );
                                }
                                return null;
                            }
                        }
                );
                return null;
            }

            throw new IllegalArgumentException("Unknown 1.21.11 zombie hook: " + hookName);
        }

        private @Nullable Method requireMethodByNameAndCount(
                @NotNull Class<?> type,
                @NotNull String methodName,
                int parameterCount
        ) {
            Class<?> current = type;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (methodName.equals(method.getName()) && method.getParameterTypes().length == parameterCount) {
                        method.setAccessible(true);
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private @NotNull Object[] requireArguments(@Nullable Object[] arguments, int expectedLength) {
            if (arguments == null || arguments.length != expectedLength) {
                throw new IllegalArgumentException(
                        "Expected " + expectedLength + " native hook arguments but received "
                                + (arguments == null ? 0 : arguments.length) + "."
                );
            }
            return arguments;
        }

        private double resolveVecX(@NotNull Object vec3) {
            return ((Double) ReflectionSupport.readField(ReflectionSupport.requireField(vec3Type, "x"), vec3)).doubleValue();
        }

        private double resolveVecY(@NotNull Object vec3) {
            return ((Double) ReflectionSupport.readField(ReflectionSupport.requireField(vec3Type, "y"), vec3)).doubleValue();
        }

        private double resolveVecZ(@NotNull Object vec3) {
            return ((Double) ReflectionSupport.readField(ReflectionSupport.requireField(vec3Type, "z"), vec3)).doubleValue();
        }

        private @NotNull Object createVec3(double x, double y, double z) {
            Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(vec3Type, double.class, double.class, double.class);
            return ReflectionSupport.instantiate(constructor, Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
        }

        private @NotNull EntityInteractionHand toApiHand(@NotNull Object hand) {
            return "OFF_HAND".equals(((Enum<?>) hand).name())
                    ? EntityInteractionHand.OFF_HAND
                    : EntityInteractionHand.MAIN_HAND;
        }

        private @NotNull EntityInteractionResult toApiInteractionResult(@Nullable Object interactionResult) {
            if (interactionResult == null) {
                return EntityInteractionResult.PASS;
            }
            if (interactionResult.equals(readInteractionResult(interactionResultFailField))) {
                return EntityInteractionResult.FAIL;
            }
            if (interactionResult.equals(readInteractionResult(interactionResultPassField))
                    || interactionResult.equals(readInteractionResult(interactionResultTryWithEmptyHandField))) {
                return EntityInteractionResult.PASS;
            }
            if (interactionResultSuccessType.isInstance(interactionResult)) {
                if (interactionResult.equals(readInteractionResult(interactionResultConsumeField))) {
                    return EntityInteractionResult.CONSUME;
                }
                Object swingSource = ReflectionSupport.invoke(
                        interactionResultSuccessSwingSourceMethod,
                        interactionResult
                );
                if (swingSource instanceof Enum<?> && "NONE".equals(((Enum<?>) swingSource).name())) {
                    return EntityInteractionResult.CONSUME;
                }
                return EntityInteractionResult.SUCCESS;
            }
            return EntityInteractionResult.PASS;
        }

        private @NotNull Object toNmsInteractionResult(
                @NotNull EntityInteractionResult result,
                @Nullable Object baseInteractionResult
        ) {
            if (baseInteractionResult != null
                    && interactionResultType.isInstance(baseInteractionResult)
                    && result == toApiInteractionResult(baseInteractionResult)) {
                return baseInteractionResult;
            }
            if (result == EntityInteractionResult.SUCCESS) {
                return readInteractionResult(interactionResultSuccessField);
            }
            if (result == EntityInteractionResult.CONSUME) {
                return readInteractionResult(interactionResultConsumeField);
            }
            if (result == EntityInteractionResult.FAIL) {
                return readInteractionResult(interactionResultFailField);
            }
            return readInteractionResult(interactionResultPassField);
        }

        private @NotNull Object readInteractionResult(@NotNull Field field) {
            return ReflectionSupport.readField(field, null);
        }

        private @Nullable ItemStack toBukkitItem(@Nullable Object nativeItem) {
            if (nativeItem == null) {
                return null;
            }
            Class<?> craftItemStackType = ReflectionSupport.requireClass("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Method asBukkitCopyMethod = ReflectionSupport.requireNamedMethod(
                    craftItemStackType,
                    new String[]{"asBukkitCopy"},
                    nativeItem.getClass()
            );
            return (ItemStack) ReflectionSupport.invoke(asBukkitCopyMethod, null, nativeItem);
        }

        private @Nullable Object toNmsItem(@Nullable ItemStack itemStack) {
            if (itemStack == null) {
                return null;
            }
            Class<?> craftItemStackType = ReflectionSupport.requireClass("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Method asNmsCopyMethod = ReflectionSupport.requireNamedMethod(
                    craftItemStackType,
                    new String[]{"asNMSCopy"},
                    ItemStack.class
            );
            return ReflectionSupport.invoke(asNmsCopyMethod, null, itemStack);
        }

        private @NotNull EntityEquipmentSlot toApiEquipmentSlot(@NotNull Object equipmentSlot) {
            String name = ((Enum<?>) equipmentSlot).name();
            if ("MAINHAND".equals(name)) {
                return EntityEquipmentSlot.MAIN_HAND;
            }
            if ("OFFHAND".equals(name)) {
                return EntityEquipmentSlot.OFF_HAND;
            }
            return EntityEquipmentSlot.valueOf(name);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private @NotNull Object toNmsEquipmentSlot(@NotNull EntityEquipmentSlot slot) {
            String name = slot == EntityEquipmentSlot.MAIN_HAND
                    ? "MAINHAND"
                    : slot == EntityEquipmentSlot.OFF_HAND ? "OFFHAND" : slot.name();
            return Enum.valueOf((Class) equipmentSlotType, name);
        }
    }
}

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
package tech.guilhermekaua.spigotboot.entity.v1_8_8.zombie;

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
 * Spawns and attaches real 1.8.8 native zombie subclasses and binds them to the shared runtime lifecycle.
 *
 * @since 2.0.2
 */
public final class ZombieFactoryV1_8_8 {
    private static final String GENERATED_CLASS_NAME =
            "tech.guilhermekaua.spigotboot.entity.generated.v1_8_8.SpigotBootZombieV1_8_8";
    private static final String HOOK_TICK = "tick";
    private static final String HOOK_MOVE = "move";
    private static final String HOOK_PUSH = "push";
    private static final String HOOK_DAMAGE = "damage";
    private static final String HOOK_INTERACT = "interact";
    private static final String HOOK_DIE = "die";
    private static final String HOOK_REMOVE = "remove";
    private static final String HOOK_COLLIDE = "collide";
    private static final String HOOK_POSITION_PASSENGER = "positionPassenger";
    private static final String HOOK_INVENTORY_CHANGE = "inventoryChange";
    private static final Object[] EMPTY_ARGUMENTS = new Object[0];

    private final GeneratedNativeEntityClassFactory classFactory = new GeneratedNativeEntityClassFactory();
    private final ZombieHookBinderV1_8_8 hookBinder = new ZombieHookBinderV1_8_8();

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

        Method setLocationMethod = ReflectionSupport.requireMethodBySignature(
                zombieSuperclass,
                void.class,
                double.class,
                double.class,
                double.class,
                float.class,
                float.class
        );
        ReflectionSupport.invoke(
                setLocationMethod,
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
                    "Minecraft 1.8.8 controller attachment currently supports only vanilla zombies and already-hooked Spigot Boot zombies."
            );
        }

        Object worldHandle = resolveWorldHandle(Objects.requireNonNull(zombie.getWorld(), "zombie world cannot be null"));
        Class<?> zombieClass = generatedZombieClass(zombieSuperclass);
        Object replacementHandle = instantiateZombie(zombieClass, worldHandle);

        FieldCopySupport.copyInstanceFields(currentHandle, replacementHandle);
        bindRuntimeLifecycle(replacementHandle, zombieSuperclass, lifecycle);
        rebindBukkitZombie(zombie, replacementHandle);
        replaceLegacyWorldReferences(currentHandle, replacementHandle);
        rewireLegacyVehicleAndPassengerReferences(currentHandle, replacementHandle);
        refreshLegacyBukkitWrappers(zombie);
        markLegacyEntityRemoved(currentHandle);
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
        ensureTrackerMappings(zombieSuperclass, generatedZombieClass);
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
        return ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.EntityZombie");
    }

    private static @NotNull Object instantiateZombie(@NotNull Class<?> zombieType, @NotNull Object worldHandle) {
        Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(zombieType, worldHandle.getClass());
        return ReflectionSupport.instantiate(constructor, worldHandle);
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
        candidateNames.add("addEntity");
        candidateNames.add("d");

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
        throw new IllegalStateException("Could not resolve World#addEntity(Entity) for 1.8.8.");
    }

    private static void ensureTrackerMappings(@NotNull Class<?> zombieSuperclass, @NotNull Class<?> generatedType) {
        Map<Class<?>, String> classToName = resolveEntityTypesMap("d");
        Map<Class<?>, Integer> classToId = resolveEntityTypesMap("f");

        String entityName = classToName.get(zombieSuperclass);
        Integer entityId = classToId.get(zombieSuperclass);
        if (entityName == null || entityId == null) {
            throw new IllegalStateException("Could not resolve the legacy zombie EntityTypes mapping for 1.8.8.");
        }

        classToName.put(generatedType, entityName);
        classToId.put(generatedType, entityId);
    }

    @SuppressWarnings("unchecked")
    private static <T> @NotNull Map<Class<?>, T> resolveEntityTypesMap(@NotNull String fieldName) {
        Class<?> entityTypesClass = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.EntityTypes");
        try {
            Field field = entityTypesClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof Map)) {
                throw new IllegalStateException(
                        "Expected EntityTypes." + fieldName + " to be a Map for Minecraft 1.8.8."
                );
            }
            return (Map<Class<?>, T>) value;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not access EntityTypes." + fieldName + " for Minecraft 1.8.8.",
                    exception
            );
        }
    }

    private static void rebindBukkitZombie(@NotNull Zombie zombie, @NotNull Object replacementHandle) {
        Method setHandleMethod = ReflectionSupport.requireCompatibleMethod(
                zombie.getClass(),
                new String[]{"setHandle"},
                replacementHandle.getClass()
        );
        ReflectionSupport.invoke(setHandleMethod, zombie, replacementHandle);
    }

    private static void replaceLegacyWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Object world = ReflectionSupport.readField(ReflectionSupport.requireField(oldHandle.getClass(), "world"), oldHandle);
        int entityId = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getId"}),
                oldHandle
        )).intValue();

        @SuppressWarnings("unchecked")
        List<Object> entityList = (List<Object>) ReflectionSupport.readField(
                ReflectionSupport.requireField(world.getClass(), "entityList"),
                world
        );
        replaceListEntry(entityList, oldHandle, replacementHandle);

        Object entitiesById = ReflectionSupport.readField(ReflectionSupport.requireField(world.getClass(), "entitiesById"), world);
        ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(entitiesById.getClass(), new String[]{"a"}, int.class, Object.class),
                entitiesById,
                Integer.valueOf(entityId),
                replacementHandle
        );

        if (ReflectionSupport.findField(world.getClass(), "entitiesByUUID") != null) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> entitiesByUuid = (Map<Object, Object>) ReflectionSupport.readField(
                    ReflectionSupport.requireField(world.getClass(), "entitiesByUUID"),
                    world
            );
            Object uuid = ReflectionSupport.readField(ReflectionSupport.requireField(oldHandle.getClass(), "uniqueID"), oldHandle);
            entitiesByUuid.put(uuid, replacementHandle);
        }

        replaceLegacyChunkSlice(world, oldHandle, replacementHandle);
        replaceLegacyTracker(world, entityId, replacementHandle);
    }

    private static void replaceLegacyChunkSlice(
            @NotNull Object world,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        int chunkX = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getChunkX"}),
                oldHandle
        )).intValue();
        int chunkY = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getChunkY"}),
                oldHandle
        )).intValue();
        int chunkZ = ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(oldHandle.getClass(), new String[]{"getChunkZ"}),
                oldHandle
        )).intValue();
        Object chunk = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(world.getClass(), new String[]{"getChunkAt"}, int.class, int.class),
                world,
                Integer.valueOf(chunkX),
                Integer.valueOf(chunkZ)
        );
        @SuppressWarnings("unchecked")
        List<Object>[] entitySlices = (List<Object>[]) ReflectionSupport.readField(
                ReflectionSupport.requireField(chunk.getClass(), "entitySlices"),
                chunk
        );
        if (chunkY < 0 || chunkY >= entitySlices.length) {
            return;
        }
        replaceListEntry(entitySlices[chunkY], oldHandle, replacementHandle);
    }

    private static void replaceLegacyTracker(
            @NotNull Object world,
            int entityId,
            @NotNull Object replacementHandle
    ) {
        Field trackerField = ReflectionSupport.findField(world.getClass(), "tracker");
        if (trackerField == null) {
            return;
        }

        Object tracker = ReflectionSupport.readField(trackerField, world);
        if (tracker == null) {
            return;
        }

        Object trackedEntities = ReflectionSupport.readField(
                ReflectionSupport.requireField(tracker.getClass(), "trackedEntities"),
                tracker
        );
        Object trackerEntry = ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(trackedEntities.getClass(), new String[]{"get"}, int.class),
                trackedEntities,
                Integer.valueOf(entityId)
        );
        if (trackerEntry != null) {
            ReflectionSupport.writeField(
                    ReflectionSupport.requireField(trackerEntry.getClass(), "tracker"),
                    trackerEntry,
                    replacementHandle
            );
        }
    }

    private static void rewireLegacyVehicleAndPassengerReferences(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field passengerField = ReflectionSupport.requireField(oldHandle.getClass(), "passenger");
        Field vehicleField = ReflectionSupport.requireField(oldHandle.getClass(), "vehicle");

        Object passenger = ReflectionSupport.readField(passengerField, oldHandle);
        if (passenger != null) {
            ReflectionSupport.writeField(vehicleField, passenger, replacementHandle);
        }

        Object vehicle = ReflectionSupport.readField(vehicleField, oldHandle);
        if (vehicle != null) {
            ReflectionSupport.writeField(passengerField, vehicle, replacementHandle);
        }
    }

    private static void refreshLegacyBukkitWrappers(@NotNull Zombie zombie) {
        Field equipmentField = ReflectionSupport.findField(zombie.getClass(), "equipment");
        if (equipmentField != null) {
            ReflectionSupport.writeField(equipmentField, zombie, null);
        }
    }

    private static void markLegacyEntityRemoved(@NotNull Object oldHandle) {
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "dead"), oldHandle, Boolean.TRUE);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "valid"), oldHandle, Boolean.FALSE);
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
                replaceLegacyWorldReferences(oldHandle, replacementHandle);
                rewireLegacyVehicleAndPassengerReferences(oldHandle, replacementHandle);
                refreshLegacyBukkitWrappers(zombie);
            }
        });
    }

    private static void replaceListEntry(@Nullable List<Object> values, @NotNull Object oldValue, @NotNull Object newValue) {
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) == oldValue) {
                values.set(index, newValue);
            }
        }
    }

    private final class ZombieHookBinderV1_8_8 implements NativeHookBinder<Zombie> {
        private final Class<?> damageSourceType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.DamageSource");
        private final Class<?> entityHumanType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.EntityHuman");
        private final Class<?> entityType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.Entity");
        private final Class<?> itemStackType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.ItemStack");

        @Override
        public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs(@NotNull Class<?> nativeType) {
            List<GeneratedNativeHookSpec> hookSpecs = new ArrayList<GeneratedNativeHookSpec>();
            hookSpecs.add(GeneratedNativeHookSpec.of(HOOK_TICK, ReflectionSupport.requireNamedMethod(nativeType, new String[]{"m"})));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_MOVE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"move"}, double.class, double.class, double.class)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_PUSH,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"g"}, double.class, double.class, double.class)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_DAMAGE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"damageEntity"}, damageSourceType, float.class)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_INTERACT,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"a"}, entityHumanType)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_DIE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"die"}, damageSourceType)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(HOOK_REMOVE, ReflectionSupport.requireNamedMethod(nativeType, new String[]{"die"})));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_COLLIDE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"collide"}, entityType)
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_POSITION_PASSENGER,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"al"})
            ));
            hookSpecs.add(GeneratedNativeHookSpec.of(
                    HOOK_INVENTORY_CHANGE,
                    ReflectionSupport.requireNamedMethod(nativeType, new String[]{"setEquipment"}, int.class, itemStackType)
            ));
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
                Object[] rawArguments = requireArguments(arguments, 3);
                controlledEntity.dispatchMove(
                        ((Double) rawArguments[0]).doubleValue(),
                        ((Double) rawArguments[1]).doubleValue(),
                        ((Double) rawArguments[2]).doubleValue(),
                        new ContextualBaseInvoker<EntityMoveContext<Zombie>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityMoveContext<Zombie> context) {
                                nativeEntity.spigotBootInvokeBase(
                                        HOOK_MOVE,
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
                Object[] rawArguments = requireArguments(arguments, 2);
                return Boolean.valueOf(controlledEntity.dispatchDamage(
                        ((Float) rawArguments[1]).floatValue(),
                        new ContextualBaseInvoker<EntityDamageContext<Zombie>, Boolean>() {
                            @Override
                            public Boolean invoke(@NotNull EntityDamageContext<Zombie> context) {
                                Object result = nativeEntity.spigotBootInvokeBase(
                                        HOOK_DAMAGE,
                                        new Object[]{rawArguments[0], Float.valueOf(context.amount())}
                                );
                                return Boolean.valueOf(result != null && ((Boolean) result).booleanValue());
                            }
                        }
                ));
            }

            if (HOOK_INTERACT.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 1);
                Player player = (Player) resolveBukkitEntity(rawArguments[0]);
                EntityInteractionResult result = controlledEntity.dispatchInteract(
                        player,
                        EntityInteractionHand.MAIN_HAND,
                        new ContextualBaseInvoker<EntityInteractContext<Zombie>, EntityInteractionResult>() {
                            @Override
                            public EntityInteractionResult invoke(@NotNull EntityInteractContext<Zombie> context) {
                                Object baseResult = nativeEntity.spigotBootInvokeBase(HOOK_INTERACT, new Object[]{rawArguments[0]});
                                return baseResult != null && ((Boolean) baseResult).booleanValue()
                                        ? EntityInteractionResult.SUCCESS
                                        : EntityInteractionResult.PASS;
                            }
                        }
                );
                return Boolean.valueOf(result != EntityInteractionResult.PASS);
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

            if (HOOK_REMOVE.equals(hookName)) {
                controlledEntity.dispatchRemove(new ContextualBaseInvoker<EntityRemoveContext<Zombie>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityRemoveContext<Zombie> context) {
                        nativeEntity.spigotBootInvokeBase(HOOK_REMOVE, EMPTY_ARGUMENTS);
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
                controlledEntity.dispatchPositionPassenger(
                        resolveLegacyPassenger(nativeEntity),
                        new ContextualBaseInvoker<EntityPositionPassengerContext<Zombie>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityPositionPassengerContext<Zombie> context) {
                                nativeEntity.spigotBootInvokeBase(HOOK_POSITION_PASSENGER, EMPTY_ARGUMENTS);
                                return null;
                            }
                        }
                );
                return null;
            }

            if (HOOK_INVENTORY_CHANGE.equals(hookName)) {
                Object[] rawArguments = requireArguments(arguments, 2);
                final int rawSlot = ((Integer) rawArguments[0]).intValue();
                controlledEntity.dispatchInventoryChange(
                        mapLegacySlot(rawSlot),
                        toBukkitItem(resolveLegacyEquipmentItem(nativeEntity, rawSlot)),
                        toBukkitItem(rawArguments[1]),
                        new ContextualBaseInvoker<EntityInventoryChangeContext<Zombie>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityInventoryChangeContext<Zombie> context) {
                                nativeEntity.spigotBootInvokeBase(
                                        HOOK_INVENTORY_CHANGE,
                                        new Object[]{
                                                Integer.valueOf(toLegacySlot(context.slot())),
                                                toLegacyItem(context.newItem())
                                        }
                                );
                                return null;
                            }
                        }
                );
                return null;
            }

            throw new IllegalArgumentException("Unknown 1.8.8 zombie hook: " + hookName);
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

        private @Nullable Entity resolveLegacyPassenger(@NotNull LifecycleAwareNativeEntity nativeEntity) {
            Field passengerField = ReflectionSupport.findField(nativeEntity.getClass(), "passenger");
            if (passengerField == null) {
                return null;
            }
            Object passenger = ReflectionSupport.readField(passengerField, nativeEntity);
            return passenger != null ? resolveBukkitEntity(passenger) : null;
        }

        private @Nullable Object resolveLegacyEquipmentItem(
                @NotNull LifecycleAwareNativeEntity nativeEntity,
                int slot
        ) {
            Object equipmentArray = ReflectionSupport.invoke(
                    ReflectionSupport.requireNamedMethod(nativeEntity.getClass(), new String[]{"getEquipment"}),
                    nativeEntity
            );
            if (!(equipmentArray instanceof Object[])) {
                return null;
            }
            Object[] values = (Object[]) equipmentArray;
            return slot >= 0 && slot < values.length ? values[slot] : null;
        }

        private @Nullable ItemStack toBukkitItem(@Nullable Object nativeItem) {
            if (nativeItem == null) {
                return null;
            }
            Class<?> craftItemStackType = ReflectionSupport.requireClass(
                    "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
            );
            Method asBukkitCopyMethod = ReflectionSupport.requireNamedMethod(
                    craftItemStackType,
                    new String[]{"asBukkitCopy"},
                    nativeItem.getClass()
            );
            return (ItemStack) ReflectionSupport.invoke(asBukkitCopyMethod, null, nativeItem);
        }

        private @Nullable Object toLegacyItem(@Nullable ItemStack itemStack) {
            if (itemStack == null) {
                return null;
            }
            Class<?> craftItemStackType = ReflectionSupport.requireClass(
                    "org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack"
            );
            Method asNmsCopyMethod = ReflectionSupport.requireNamedMethod(
                    craftItemStackType,
                    new String[]{"asNMSCopy"},
                    ItemStack.class
            );
            return ReflectionSupport.invoke(asNmsCopyMethod, null, itemStack);
        }

        private @NotNull EntityEquipmentSlot mapLegacySlot(int slot) {
            switch (slot) {
                case 0:
                    return EntityEquipmentSlot.MAIN_HAND;
                case 1:
                    return EntityEquipmentSlot.FEET;
                case 2:
                    return EntityEquipmentSlot.LEGS;
                case 3:
                    return EntityEquipmentSlot.CHEST;
                case 4:
                    return EntityEquipmentSlot.HEAD;
                default:
                    throw new IllegalArgumentException("Unsupported legacy equipment slot index: " + slot);
            }
        }

        private int toLegacySlot(@NotNull EntityEquipmentSlot slot) {
            switch (slot) {
                case MAIN_HAND:
                    return 0;
                case FEET:
                    return 1;
                case LEGS:
                    return 2;
                case CHEST:
                    return 3;
                case HEAD:
                    return 4;
                case OFF_HAND:
                default:
                    throw new IllegalArgumentException("Minecraft 1.8.8 does not support slot " + slot + '.');
            }
        }
    }
}

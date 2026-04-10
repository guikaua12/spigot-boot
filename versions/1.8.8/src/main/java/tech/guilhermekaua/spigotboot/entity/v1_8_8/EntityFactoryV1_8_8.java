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
package tech.guilhermekaua.spigotboot.entity.v1_8_8;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
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
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.entity.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.entity.runtime.controller.LogicalEntityHook;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.FieldCopySupport;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spawns and attaches real 1.8.8 native Entity subclasses and binds them to the shared runtime lifecycle.
 *
 * @since 2.0.2
 */
public final class EntityFactoryV1_8_8 {
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
    private final EntityHookBinderV1_8_8 hookBinder = new EntityHookBinderV1_8_8();
    private final Map<CustomEntityBaseType, EntityMetadata> metadataRegistry = createMetadataRegistry();
    private final Map<Class<?>, ResolvedEntityTypeMetadata> generatedTypes =
            new LinkedHashMap<Class<?>, ResolvedEntityTypeMetadata>();

    public boolean supports(@NotNull CustomEntityBaseType baseType) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return metadataRegistry.containsKey(baseType);
    }

    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        EntityMetadata metadata = requireMetadata(template.baseType());
        T entity = template.bukkitType().cast(spawnVanillaEntity(spawnOptions.location(), metadata));
        try {
            ControlledEntity<T> attached = attachInternal(entity, lifecycle, metadata);
            lifecycle.onSpawn();
            if (!(attached instanceof SpawnedEntity)) {
                throw new IllegalStateException(
                        "Spawn lifecycle did not return a SpawnedEntity for base type '" + template.baseType() + "'."
                );
            }
            return (SpawnedEntity<T>) attached;
        } catch (RuntimeException exception) {
            entity.remove();
            throw exception;
        }
    }

    public <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        return attachInternal(entity, lifecycle, requireMetadata(resolveBaseType(entity)));
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> @NotNull ControlledEntity<T> attachInternal(
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle,
            @NotNull EntityMetadata metadata
    ) {
        Object currentHandle = resolveNativeHandle(entity);
        ControlledEntity<?> existing = resolveExistingControlledEntity(currentHandle);
        if (existing != null) {
            return (ControlledEntity<T>) existing;
        }

        ResolvedEntityTypeMetadata resolvedMetadata = resolveGeneratedTypeMetadata(metadata, currentHandle.getClass());
        Object replacementHandle = ReflectionSupport.allocateInstance(resolvedMetadata.generatedType());
        FieldCopySupport.copyInstanceFields(currentHandle, replacementHandle);
        bindRuntimeLifecycle(replacementHandle, resolvedMetadata, lifecycle);
        rebindBukkitZombie(entity, replacementHandle);
        replaceLegacyWorldReferences(currentHandle, replacementHandle);
        rewireLegacyVehicleAndPassengerReferences(currentHandle, replacementHandle);
        refreshLegacyBukkitWrappers(entity);
        markLegacyEntityRemoved(currentHandle);
        lifecycle.bind((T) resolveBukkitEntity(replacementHandle));
        scheduleRepairPass((NativeEntityLifecycle<Entity>) lifecycle, entity, currentHandle, replacementHandle);
        return lifecycle.handle();
    }

    private synchronized @NotNull ResolvedEntityTypeMetadata resolveGeneratedTypeMetadata(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        ResolvedEntityTypeMetadata resolvedMetadata = generatedTypes.get(nativeType);
        if (resolvedMetadata != null) {
            return resolvedMetadata;
        }

        Collection<GeneratedNativeHookSpec> hookSpecs = hookBinder.hookSpecs(nativeType);
        if (hookSpecs.isEmpty()) {
            throw new UnsupportedOperationException(
                    "Minecraft 1.8.8 does not expose any supported hooks for base type '" + metadata.baseType() + "'."
            );
        }

        Class<?> generatedType = classFactory.createSubclass(
                nativeType,
                generatedClassName(metadata, nativeType),
                hookSpecs
        );
        ensureTrackerMappings(nativeType, generatedType);

        resolvedMetadata = new ResolvedEntityTypeMetadata(
                metadata,
                nativeType,
                generatedType,
                hookSpecs,
                hookBinder.supportedHooks(nativeType)
        );
        generatedTypes.put(nativeType, resolvedMetadata);
        return resolvedMetadata;
    }

    @SuppressWarnings("unchecked")
    private void bindRuntimeLifecycle(
            @NotNull Object nativeEntity,
            @NotNull ResolvedEntityTypeMetadata resolvedMetadata,
            @NotNull NativeEntityLifecycle<?> lifecycle
    ) {
        AbstractRuntimeControlledEntity<?> controlledEntity = requireRuntimeLifecycle(lifecycle);
        controlledEntity.bindHookBinder((NativeHookBinder) hookBinder);
        classFactory.installInterceptor(nativeEntity, resolvedMetadata.hookSpecs());
        classFactory.bindLifecycle(nativeEntity, lifecycle);
    }

    private static @NotNull AbstractRuntimeControlledEntity<?> requireRuntimeLifecycle(
            @NotNull NativeEntityLifecycle<?> lifecycle
    ) {
        ControlledEntity<?> handle = lifecycle.handle();
        if (!(handle instanceof AbstractRuntimeControlledEntity)) {
            throw new IllegalStateException(
                    "The runtime lifecycle handle must extend AbstractRuntimeControlledEntity for entity attachment."
            );
        }
        return (AbstractRuntimeControlledEntity<?>) handle;
    }

    private static @Nullable ControlledEntity<?> resolveExistingControlledEntity(@NotNull Object nativeHandle) {
        if (!(nativeHandle instanceof LifecycleAwareNativeEntity)) {
            return null;
        }
        NativeEntityLifecycle<?> lifecycle = ((LifecycleAwareNativeEntity) nativeHandle).spigotBootGetLifecycle();
        if (lifecycle == null || !(lifecycle.handle() instanceof ControlledEntity)) {
            return null;
        }
        return (ControlledEntity<?>) lifecycle.handle();
    }

    private static @NotNull Entity spawnVanillaEntity(
            @NotNull Location location,
            @NotNull EntityMetadata metadata
    ) {
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        return world.spawnEntity(location.clone(), metadata.entityType());
    }

    private @NotNull EntityMetadata requireMetadata(@NotNull CustomEntityBaseType baseType) {
        EntityMetadata metadata = metadataRegistry.get(baseType);
        if (metadata == null) {
            throw new UnsupportedOperationException(
                    "Minecraft 1.8.8 does not support spawn and attach for base type '" + baseType + "'."
            );
        }
        return metadata;
    }

    private static @NotNull CustomEntityBaseType resolveBaseType(@NotNull Entity entity) {
        CustomEntityBaseType baseType = CustomEntityBaseType.fromEntityType(entity.getType());
        if (baseType == null) {
            throw new UnsupportedOperationException(
                    "Could not resolve a logical base type for Bukkit entity type '" + entity.getType().name() + "'."
            );
        }
        return baseType;
    }

    private static @NotNull Map<CustomEntityBaseType, EntityMetadata> createMetadataRegistry() {
        Map<CustomEntityBaseType, EntityMetadata> metadata = new LinkedHashMap<CustomEntityBaseType, EntityMetadata>();
        for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
            EntityType entityType = baseType.entityTypeOrNull();
            if (entityType == null || entityType.getEntityClass() == null) {
                continue;
            }
            if (baseType == CustomEntityBaseType.UNKNOWN
                    || baseType == CustomEntityBaseType.PLAYER
                    || baseType == CustomEntityBaseType.WEATHER
                    || baseType == CustomEntityBaseType.COMPLEX_PART) {
                continue;
            }
            metadata.put(baseType, new EntityMetadata(baseType, entityType));
        }
        return metadata;
    }

    private static @NotNull String generatedClassName(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        return "tech.guilhermekaua.spigotboot.entity.generated.v1_8_8.SpigotBoot"
                + toGeneratedSuffix(metadata.baseType())
                + "V1_8_8_"
                + Integer.toHexString(nativeType.getName().hashCode()).replace('-', '0');
    }

    private static @NotNull String toGeneratedSuffix(@NotNull CustomEntityBaseType baseType) {
        StringBuilder suffix = new StringBuilder();
        for (String token : baseType.name().toLowerCase().split("_")) {
            if (token.isEmpty()) {
                continue;
            }
            suffix.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                suffix.append(token.substring(1));
            }
        }
        return suffix.toString();
    }

    private static @NotNull Object resolveNativeHandle(@NotNull Entity entity) {
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(entity.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, entity);
    }

    private static @NotNull Entity resolveBukkitEntity(@NotNull Object nmsEntity) {
        Method getBukkitEntityMethod = ReflectionSupport.requireNamedMethod(
                nmsEntity.getClass(),
                new String[]{"getBukkitEntity"}
        );
        return (Entity) ReflectionSupport.invoke(getBukkitEntityMethod, nmsEntity);
    }

    private static void ensureTrackerMappings(@NotNull Class<?> zombieSuperclass, @NotNull Class<?> generatedType) {
        Map<Class<?>, String> classToName = resolveEntityTypesMap("d");
        Map<Class<?>, Integer> classToId = resolveEntityTypesMap("f");

        String entityName = classToName.get(zombieSuperclass);
        Integer entityId = classToId.get(zombieSuperclass);
        if (entityName == null || entityId == null) {
            throw new IllegalStateException("Could not resolve the legacy Entity EntityTypes mapping for 1.8.8.");
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

    private static void rebindBukkitZombie(@NotNull Entity Entity, @NotNull Object replacementHandle) {
        Method setHandleMethod = ReflectionSupport.requireCompatibleMethod(
                Entity.getClass(),
                new String[]{"setHandle"},
                replacementHandle.getClass()
        );
        ReflectionSupport.invoke(setHandleMethod, Entity, replacementHandle);
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

    private static void refreshLegacyBukkitWrappers(@NotNull Entity Entity) {
        Field equipmentField = ReflectionSupport.findField(Entity.getClass(), "equipment");
        if (equipmentField != null) {
            ReflectionSupport.writeField(equipmentField, Entity, null);
        }
    }

    private static void markLegacyEntityRemoved(@NotNull Object oldHandle) {
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "dead"), oldHandle, Boolean.TRUE);
        ReflectionSupport.writeField(ReflectionSupport.requireField(oldHandle.getClass(), "valid"), oldHandle, Boolean.FALSE);
    }

    private void scheduleRepairPass(
            @NotNull NativeEntityLifecycle<Entity> lifecycle,
            @NotNull Entity entity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        requireRuntimeLifecycle(lifecycle).scheduleNextTickRepair(new Runnable() {
            @Override
            public void run() {
                rebindBukkitZombie(entity, replacementHandle);
                replaceLegacyWorldReferences(oldHandle, replacementHandle);
                rewireLegacyVehicleAndPassengerReferences(oldHandle, replacementHandle);
                refreshLegacyBukkitWrappers(entity);
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

    private static final class EntityMetadata {
        private final CustomEntityBaseType baseType;
        private final EntityType entityType;

        private EntityMetadata(@NotNull CustomEntityBaseType baseType, @NotNull EntityType entityType) {
            this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
            this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null");
        }

        public @NotNull CustomEntityBaseType baseType() {
            return baseType;
        }

        public @NotNull EntityType entityType() {
            return entityType;
        }
    }

    private static final class ResolvedEntityTypeMetadata {
        private final EntityMetadata metadata;
        private final Class<?> nativeType;
        private final Class<?> generatedType;
        private final Collection<GeneratedNativeHookSpec> hookSpecs;
        private final EnumSet<LogicalEntityHook> supportedHooks;

        private ResolvedEntityTypeMetadata(
                @NotNull EntityMetadata metadata,
                @NotNull Class<?> nativeType,
                @NotNull Class<?> generatedType,
                @NotNull Collection<GeneratedNativeHookSpec> hookSpecs,
                @NotNull EnumSet<LogicalEntityHook> supportedHooks
        ) {
            this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
            this.nativeType = Objects.requireNonNull(nativeType, "nativeType cannot be null");
            this.generatedType = Objects.requireNonNull(generatedType, "generatedType cannot be null");
            this.hookSpecs = Objects.requireNonNull(hookSpecs, "hookSpecs cannot be null");
            this.supportedHooks = Objects.requireNonNull(supportedHooks, "supportedHooks cannot be null");
        }

        public @NotNull EntityMetadata metadata() {
            return metadata;
        }

        public @NotNull Class<?> nativeType() {
            return nativeType;
        }

        public @NotNull Class<?> generatedType() {
            return generatedType;
        }

        public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs() {
            return hookSpecs;
        }

        public @NotNull EnumSet<LogicalEntityHook> supportedHooks() {
            return supportedHooks.clone();
        }
    }

    private final class EntityHookBinderV1_8_8 implements NativeHookBinder<Entity> {
        private final Class<?> damageSourceType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.DamageSource");
        private final Class<?> entityHumanType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.EntityHuman");
        private final Class<?> entityType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.Entity");
        private final Class<?> itemStackType = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.ItemStack");

        public @NotNull EnumSet<LogicalEntityHook> supportedHooks(@NotNull Class<?> nativeType) {
            return resolveHookCatalog(nativeType).supportedHooks();
        }

        @Override
        public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs(@NotNull Class<?> nativeType) {
            return resolveHookCatalog(nativeType).hookSpecs();
        }

        @Override
        public @Nullable Object dispatch(
                @NotNull AbstractRuntimeControlledEntity<Entity> controlledEntity,
                @NotNull LifecycleAwareNativeEntity nativeEntity,
                @NotNull String hookName,
                @Nullable Object[] arguments
        ) {
            if (HOOK_TICK.equals(hookName)) {
                controlledEntity.dispatchTick(new ContextualBaseInvoker<EntityTickContext<Entity>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityTickContext<Entity> context) {
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
                        new ContextualBaseInvoker<EntityMoveContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityMoveContext<Entity> context) {
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
                        new ContextualBaseInvoker<EntityPushContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityPushContext<Entity> context) {
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
                        new ContextualBaseInvoker<EntityDamageContext<Entity>, Boolean>() {
                            @Override
                            public Boolean invoke(@NotNull EntityDamageContext<Entity> context) {
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
                        new ContextualBaseInvoker<EntityInteractContext<Entity>, EntityInteractionResult>() {
                            @Override
                            public EntityInteractionResult invoke(@NotNull EntityInteractContext<Entity> context) {
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
                controlledEntity.dispatchDie(new ContextualBaseInvoker<EntityDieContext<Entity>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityDieContext<Entity> context) {
                        nativeEntity.spigotBootInvokeBase(HOOK_DIE, new Object[]{rawArguments[0]});
                        return null;
                    }
                });
                return null;
            }

            if (HOOK_REMOVE.equals(hookName)) {
                controlledEntity.dispatchRemove(new ContextualBaseInvoker<EntityRemoveContext<Entity>, Void>() {
                    @Override
                    public Void invoke(@NotNull EntityRemoveContext<Entity> context) {
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
                        new ContextualBaseInvoker<EntityCollideContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityCollideContext<Entity> context) {
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
                        new ContextualBaseInvoker<EntityPositionPassengerContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityPositionPassengerContext<Entity> context) {
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
                        new ContextualBaseInvoker<EntityInventoryChangeContext<Entity>, Void>() {
                            @Override
                            public Void invoke(@NotNull EntityInventoryChangeContext<Entity> context) {
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

            throw new IllegalArgumentException("Unknown 1.8.8 Entity hook: " + hookName);
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

        private @NotNull ResolvedHookCatalog resolveHookCatalog(@NotNull Class<?> nativeType) {
            List<GeneratedNativeHookSpec> hookSpecs = new ArrayList<GeneratedNativeHookSpec>();
            EnumSet<LogicalEntityHook> supportedHooks = EnumSet.noneOf(LogicalEntityHook.class);

            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.TICK,
                    HOOK_TICK,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"m"})
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.MOVE,
                    HOOK_MOVE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"move"}, double.class, double.class, double.class)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.PUSH,
                    HOOK_PUSH,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"g"}, double.class, double.class, double.class)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.DAMAGE,
                    HOOK_DAMAGE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"damageEntity"}, damageSourceType, float.class)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.INTERACT,
                    HOOK_INTERACT,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"a"}, entityHumanType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.DIE,
                    HOOK_DIE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"die"}, damageSourceType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.REMOVE,
                    HOOK_REMOVE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"die"})
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.COLLIDE,
                    HOOK_COLLIDE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"collide"}, entityType)
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.POSITION_PASSENGER,
                    HOOK_POSITION_PASSENGER,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"al"})
            );
            addHookSpec(
                    hookSpecs,
                    supportedHooks,
                    LogicalEntityHook.INVENTORY_CHANGE,
                    HOOK_INVENTORY_CHANGE,
                    ReflectionSupport.findNamedMethod(nativeType, new String[]{"setEquipment"}, int.class, itemStackType)
            );

            return new ResolvedHookCatalog(hookSpecs, supportedHooks);
        }

        private void addHookSpec(
                @NotNull List<GeneratedNativeHookSpec> hookSpecs,
                @NotNull EnumSet<LogicalEntityHook> supportedHooks,
                @NotNull LogicalEntityHook logicalHook,
                @NotNull String hookName,
                @Nullable Method method
        ) {
            if (method == null) {
                return;
            }
            hookSpecs.add(GeneratedNativeHookSpec.of(hookName, method));
            supportedHooks.add(logicalHook);
        }

        private final class ResolvedHookCatalog {
            private final Collection<GeneratedNativeHookSpec> hookSpecs;
            private final EnumSet<LogicalEntityHook> supportedHooks;

            private ResolvedHookCatalog(
                    @NotNull Collection<GeneratedNativeHookSpec> hookSpecs,
                    @NotNull EnumSet<LogicalEntityHook> supportedHooks
            ) {
                this.hookSpecs = Objects.requireNonNull(hookSpecs, "hookSpecs cannot be null");
                this.supportedHooks = Objects.requireNonNull(supportedHooks, "supportedHooks cannot be null");
            }

            public @NotNull Collection<GeneratedNativeHookSpec> hookSpecs() {
                return hookSpecs;
            }

            public @NotNull EnumSet<LogicalEntityHook> supportedHooks() {
                return supportedHooks.clone();
            }
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

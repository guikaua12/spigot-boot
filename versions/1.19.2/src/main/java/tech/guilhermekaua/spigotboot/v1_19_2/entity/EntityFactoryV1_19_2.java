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
package tech.guilhermekaua.spigotboot.v1_19_2.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.versions.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityCollideContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityDamageContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityDieContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityEquipmentSlot;
import tech.guilhermekaua.spigotboot.versions.api.EntityInteractContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityInteractionHand;
import tech.guilhermekaua.spigotboot.versions.api.EntityInteractionResult;
import tech.guilhermekaua.spigotboot.versions.api.EntityInventoryChangeContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityMoveContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityPositionPassengerContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityPushContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityRemoveContext;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.EntityTickContext;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.versions.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.AbstractRuntimeControlledEntity;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.ContextualBaseInvoker;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.NativeHookBinder;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityFreshSpawnBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityReplacementBinding;
import tech.guilhermekaua.spigotboot.versions.runtime.model.NativeEntityConstructorShape;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.GeneratedNativeHookSpec;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityStrategyBundleSelector;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.EntityStrategyBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.VersionEntrypoint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Version-local spawn and attach scaffold for the Minecraft 1.19.2-1.20.6 family.
 *
 * @since 2.0.2
 */
public final class EntityFactoryV1_19_2
        implements VersionEntrypoint,
        PaperTrackingBindingStrategy_1_21_plus.Support,
        PaperFreshSpawnStrategy_1_21_plus.Support,
        PaperReplacementStrategy_1_21_plus.Support {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 19, 2);
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
    private static final String SUPPORTED_FAMILY = "1.19.2-1.20.6";
    private static final VersionCapabilities ENTITY_CAPABILITIES = new VersionCapabilities(
            EntityFreshSpawnPath.CONSTRUCTOR_FIRST,
            true,
            EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
            EntityWorldRegistrationMode.REFERENCE_REWRITE
    );
    private static final VersionBindings ENTITY_BINDINGS = new VersionBindings(
            new EntityFreshSpawnBinding(
                    Arrays.asList(
                            NativeEntityConstructorShape.LEVEL_AND_POSITION,
                            NativeEntityConstructorShape.ENTITY_TYPE_AND_LEVEL,
                            NativeEntityConstructorShape.LEVEL_ONLY
                    ),
                    EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD,
                    true,
                    true
            ),
            new EntityReplacementBinding(
                    EntityWorldRegistrationMode.REFERENCE_REWRITE,
                    true,
                    true
            )
    );
    private static final EntityStrategyBundle ENTITY_STRATEGY_BUNDLE = EntityStrategyBundleSelector.select(
            VERSION,
            ENTITY_CAPABILITIES,
            ENTITY_BINDINGS
    );
    private static final PaperFreshSpawnStrategy_1_21_plus FRESH_SPAWN_STRATEGY =
            EntityStrategyBundleSelector.requirePaperFreshSpawnStrategy(ENTITY_STRATEGY_BUNDLE.freshSpawn());
    private static final PaperReplacementStrategy_1_21_plus REPLACEMENT_STRATEGY =
            EntityStrategyBundleSelector.requirePaperReplacementStrategy(ENTITY_STRATEGY_BUNDLE.replacement());

    private static final GeneratedNativeEntityClassFactory CLASS_FACTORY = new GeneratedNativeEntityClassFactory();
    private static final EntityHookBinderV1_19_2 HOOK_BINDER = new EntityHookBinderV1_19_2();
    private static final Map<Class<?>, ResolvedReplacementMetadata> GENERATED_REPLACEMENT_TYPES =
            new LinkedHashMap<Class<?>, ResolvedReplacementMetadata>();
    private final Map<UUID, ControlledEntity<?>> attachedEntities = new LinkedHashMap<UUID, ControlledEntity<?>>();
    private final Map<CustomEntityBaseType, EntityMetadata> metadataRegistry = createMetadataRegistry();
    private final Map<CustomEntityBaseType, ResolvedSpawnMetadata> spawnMetadataRegistry =
            new LinkedHashMap<CustomEntityBaseType, ResolvedSpawnMetadata>();

    public static @NotNull VersionCapabilities entityCapabilities() {
        return ENTITY_CAPABILITIES;
    }

    public static @NotNull VersionBindings entityBindings() {
        return ENTITY_BINDINGS;
    }

    @Override
    public @NotNull VersionCapabilities capabilities() {
        return ENTITY_CAPABILITIES;
    }

    @Override
    public @NotNull VersionBindings bindings() {
        return ENTITY_BINDINGS;
    }

    @Override
    public boolean supports(@NotNull CustomEntityBaseType baseType) {
        Objects.requireNonNull(baseType, "baseType cannot be null");
        return metadataRegistry.containsKey(baseType);
    }

    @Override
    public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        return FRESH_SPAWN_STRATEGY.spawn(this, template, spawnOptions, lifecycle);
    }

    @Override
    public <T extends Entity> @NotNull ControlledEntity<T> attach(
            @NotNull T entity,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        requireMetadata(resolveBaseType(entity));

        ControlledEntity<?> existing = attachedEntities.get(entity.getUniqueId());
        if (existing != null && !existing.isRemoved() && existing.bukkitEntity().equals(entity)) {
            @SuppressWarnings("unchecked")
            ControlledEntity<T> rebound = (ControlledEntity<T>) existing;
            return rebound;
        }

        final ControlledEntity<T> attached = REPLACEMENT_STRATEGY.attach(this, entity, lifecycle);
        attachedEntities.put(entity.getUniqueId(), attached);
        if (attached instanceof AbstractRuntimeControlledEntity) {
            ((AbstractRuntimeControlledEntity<?>) attached).bindRemovalCallback(new Runnable() {
                @Override
                public void run() {
                    attachedEntities.remove(entity.getUniqueId());
                }
            });
        }
        return attached;
    }

    @Override
    public <T extends Entity> PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn prepareFreshSpawn(
            @NotNull EntityTemplate<T> template,
            @NotNull SpawnOptions spawnOptions
    ) {
        Objects.requireNonNull(template, "template cannot be null");
        Objects.requireNonNull(spawnOptions, "spawnOptions cannot be null");
        EntityMetadata metadata = requireMetadata(template.baseType());
        return new PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn(
                resolveSpawnMetadata(metadata, spawnOptions.location())
        );
    }

    @Override
    public @NotNull Object createNativeEntity(
            @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
            @NotNull Location location
    ) {
        Objects.requireNonNull(preparedSpawn, "preparedSpawn cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        return createFreshNativeEntity(resolvePreparedSpawnMetadata(preparedSpawn), location);
    }

    @Override
    public <T extends Entity> void bindLifecycleToNativeEntity(
            @NotNull Object nativeEntity,
            @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        Objects.requireNonNull(preparedSpawn, "preparedSpawn cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
    }

    @Override
    public @NotNull Entity resolveBukkitWrapper(@NotNull Object nativeEntity) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        return resolveBukkitEntity(nativeEntity);
    }

    @Override
    public @NotNull PaperFreshSpawnStrategy_1_21_plus.TrackingHandles resolveTrackingHandles(@NotNull Object nativeEntity) {
        Objects.requireNonNull(nativeEntity, "nativeEntity cannot be null");
        TrackedEntityState trackedEntityState = resolveTrackedEntityState(nativeEntity);
        return new PaperFreshSpawnStrategy_1_21_plus.TrackingHandles(
                trackedEntityState.trackedEntity(),
                trackedEntityState.serverEntity()
        );
    }

    @Override
    public @NotNull Object resolveNativeWorldHandle(@NotNull Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(world.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, world);
    }

    @Override
    public @NotNull Object resolveCurrentNativeHandle(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        return resolveNativeHandle(entity);
    }

    @Override
    public <T extends Entity> PaperReplacementStrategy_1_21_plus.PreparedReplacement prepareReplacement(
            @NotNull T entity,
            @NotNull Object currentNativeHandle
    ) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        EntityMetadata metadata = requireMetadata(resolveBaseType(entity));
        return new PaperReplacementStrategy_1_21_plus.PreparedReplacement(
                resolveReplacementMetadata(metadata, currentNativeHandle.getClass())
        );
    }

    @Override
    public @NotNull Object allocateReplacementHandle(
            @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
    ) {
        Objects.requireNonNull(preparedReplacement, "preparedReplacement cannot be null");
        return ReflectionSupport.allocateInstance(resolvePreparedReplacementMetadata(preparedReplacement).generatedType());
    }

    @Override
    public <T extends Entity> void bindLifecycleToReplacement(
            @NotNull Object replacementHandle,
            @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement,
            @NotNull NativeEntityLifecycle<T> lifecycle
    ) {
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        Objects.requireNonNull(preparedReplacement, "preparedReplacement cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        bindRuntimeLifecycle(replacementHandle, resolvePreparedReplacementMetadata(preparedReplacement), lifecycle);
    }

    @Override
    public @NotNull PaperReplacementStrategy_1_21_plus.TrackingHandles resolveReplacementTrackingHandles(
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        TrackedEntityState trackedEntityState = resolveTrackedEntityState(replacementHandle);
        return new PaperReplacementStrategy_1_21_plus.TrackingHandles(
                trackedEntityState.trackedEntity(),
                trackedEntityState.serverEntity()
        );
    }

    @Override
    public void rebindBukkitZombie(@NotNull Entity entity, @NotNull Object replacementHandle) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindBukkitZombieInternal(entity, replacementHandle);
    }

    @Override
    public void rebindModernBukkitBridge(
            @NotNull Entity bukkitEntity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(bukkitEntity, "bukkitEntity cannot be null");
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rebindModernBukkitBridgeInternal(bukkitEntity, oldHandle, replacementHandle);
    }

    @Override
    public void replaceWorldReferences(
            @NotNull EntityPublicationFamily family,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(family, "family cannot be null");
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        switch (family) {
            case SECTION_MANAGER:
                replaceSectionManagerWorldReferencesInternal(oldHandle, replacementHandle);
                return;
            case PAPER_CHUNK_SYSTEM:
                replacePaperChunkSystemWorldReferencesInternal(oldHandle, replacementHandle);
                return;
            default:
                throw unsupportedPublicationFamily(family, "world reference replacement");
        }
    }

    @Override
    public void replaceModernWorldReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        replaceModernWorldReferencesInternal(oldHandle, replacementHandle);
    }

    @Override
    public void rewireModernVehicleAndPassengerReferences(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        rewireModernVehicleAndPassengerReferencesInternal(oldHandle, replacementHandle);
    }

    @Override
    public void refreshModernBukkitWrappers(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        refreshModernBukkitWrappersInternal(entity);
    }

    @Override
    public void markModernEntityRemoved(@NotNull Object oldHandle) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        markModernEntityRemovedInternal(oldHandle);
    }

    public static @Nullable Object retargetModernSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        if (oldLevelCallback == null) {
            return null;
        }

        Field entityField = ReflectionSupport.findField(oldLevelCallback.getClass(), "entity", "c");
        if (entityField == null) {
            return null;
        }

        Object callbackEntity = ReflectionSupport.readField(entityField, oldLevelCallback);
        if (callbackEntity != oldHandle) {
            return null;
        }
        if (!entityField.getType().isAssignableFrom(replacementHandle.getClass())) {
            return null;
        }

        ReflectionSupport.writeField(entityField, oldLevelCallback, replacementHandle);
        return oldLevelCallback;
    }

    public static @Nullable Object recreateModernSectionCallback(
            @Nullable Object oldLevelCallback,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        if (oldLevelCallback == null) {
            return null;
        }

        Field managerField = ReflectionSupport.findField(oldLevelCallback.getClass(), "this$0");
        Field currentSectionKeyField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSectionKey", "d");
        Field currentSectionField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSection", "e");
        if (managerField == null || currentSectionKeyField == null || currentSectionField == null) {
            return null;
        }

        Object manager = ReflectionSupport.readField(managerField, oldLevelCallback);
        Object currentSectionKey = ReflectionSupport.readField(currentSectionKeyField, oldLevelCallback);
        Object currentSection = ReflectionSupport.readField(currentSectionField, oldLevelCallback);
        if (!(currentSectionKey instanceof Long)) {
            return null;
        }

        Constructor<?> callbackConstructor = findModernSectionCallbackConstructor(
                oldLevelCallback.getClass(),
                manager,
                replacementHandle,
                currentSection
        );
        if (callbackConstructor == null) {
            return null;
        }

        return ReflectionSupport.instantiate(
                callbackConstructor,
                manager,
                replacementHandle,
                Long.valueOf(((Long) currentSectionKey).longValue()),
                currentSection
        );
    }

    public static void migrateModernSectionMembership(
            @Nullable Object oldLevelCallback,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(oldHandle, "oldHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        if (oldLevelCallback == null) {
            return;
        }

        Field currentSectionField = ReflectionSupport.findField(oldLevelCallback.getClass(), "currentSection", "e");
        if (currentSectionField == null) {
            return;
        }

        Object currentSection = ReflectionSupport.readField(currentSectionField, oldLevelCallback);
        if (currentSection == null) {
            return;
        }

        Method removeMethod = ReflectionSupport.findCompatibleMethod(
                currentSection.getClass(),
                new String[]{"remove", "b"},
                oldHandle.getClass()
        );
        Method addMethod = ReflectionSupport.findCompatibleMethod(
                currentSection.getClass(),
                new String[]{"add", "a"},
                replacementHandle.getClass()
        );
        if (removeMethod == null || addMethod == null) {
            return;
        }

        boolean removed = Boolean.TRUE.equals(ReflectionSupport.invoke(removeMethod, currentSection, oldHandle));
        if (removed || !containsManagedEntry(currentSection, replacementHandle)) {
            ReflectionSupport.invoke(addMethod, currentSection, replacementHandle);
        }
    }

    public static void replaceManagedCollectionEntry(
            @Nullable Object collection,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        Objects.requireNonNull(oldValue, "oldValue cannot be null");
        Objects.requireNonNull(newValue, "newValue cannot be null");
        if (collection == null || !containsManagedEntry(collection, oldValue)) {
            return;
        }

        Method removeMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"remove", "b"},
                oldValue.getClass()
        );
        Method addMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"add", "a"},
                newValue.getClass()
        );
        if (removeMethod != null && addMethod != null) {
            ReflectionSupport.invoke(removeMethod, collection, oldValue);
            ReflectionSupport.invoke(addMethod, collection, newValue);
            return;
        }

        if (collection instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<Object> values = (Collection<Object>) collection;
            if (values.remove(oldValue)) {
                values.add(newValue);
            }
        }
    }

    @Override
    public <T extends Entity> void scheduleRepairPass(
            @NotNull NativeEntityLifecycle<T> lifecycle,
            @NotNull T entity,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    ) {
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(currentNativeHandle, "currentNativeHandle cannot be null");
        Objects.requireNonNull(replacementHandle, "replacementHandle cannot be null");
        schedulePaperReplacementRepairPass(
                lifecycle,
                entity,
                currentNativeHandle,
                replacementHandle
        );
    }

    private static @NotNull ResolvedReplacementMetadata resolvePreparedReplacementMetadata(
            @NotNull PaperReplacementStrategy_1_21_plus.PreparedReplacement preparedReplacement
    ) {
        Object preparedMetadata = preparedReplacement.preparedMetadata();
        if (!(preparedMetadata instanceof ResolvedReplacementMetadata)) {
            throw new IllegalStateException(
                    "Prepared paper replacement metadata did not contain a ResolvedReplacementMetadata instance."
            );
        }
        return (ResolvedReplacementMetadata) preparedMetadata;
    }

    private static synchronized @NotNull ResolvedReplacementMetadata resolveReplacementMetadata(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        ResolvedReplacementMetadata resolvedMetadata = GENERATED_REPLACEMENT_TYPES.get(nativeType);
        if (resolvedMetadata != null) {
            return resolvedMetadata;
        }

        Collection<GeneratedNativeHookSpec> hookSpecs = HOOK_BINDER.hookSpecs(nativeType);
        if (hookSpecs.isEmpty()) {
            throw new UnsupportedOperationException(
                    "Minecraft "
                            + SUPPORTED_FAMILY
                            + " does not expose any supported attach hooks for base type '"
                            + metadata.baseType()
                            + "'."
            );
        }

        Class<?> generatedType = CLASS_FACTORY.createSubclass(
                nativeType,
                replacementBridgeClassName(metadata, nativeType),
                hookSpecs
        );
        resolvedMetadata = new ResolvedReplacementMetadata(metadata, nativeType, generatedType, hookSpecs);
        GENERATED_REPLACEMENT_TYPES.put(nativeType, resolvedMetadata);
        return resolvedMetadata;
    }

    @SuppressWarnings("unchecked")
    private void bindRuntimeLifecycle(
            @NotNull Object nativeEntity,
            @NotNull ResolvedReplacementMetadata resolvedMetadata,
            @NotNull NativeEntityLifecycle<?> lifecycle
    ) {
        AbstractRuntimeControlledEntity<?> controlledEntity = requireRuntimeLifecycle(lifecycle);
        controlledEntity.bindHookBinder((NativeHookBinder) HOOK_BINDER);
        CLASS_FACTORY.installInterceptor(nativeEntity, resolvedMetadata.hookSpecs());
        CLASS_FACTORY.bindLifecycle(nativeEntity, lifecycle);
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

    private static @NotNull String replacementBridgeClassName(
            @NotNull EntityMetadata metadata,
            @NotNull Class<?> nativeType
    ) {
        return "v1_19_2_"
                + metadata.baseType().name()
                + "_"
                + nativeType.getName().replace('.', '_').replace('$', '_')
                + "_ReplacementBridge";
    }

    private static @NotNull ResolvedSpawnMetadata resolvePreparedSpawnMetadata(
            @NotNull PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn
    ) {
        Object preparedMetadata = preparedSpawn.preparedMetadata();
        if (!(preparedMetadata instanceof ResolvedSpawnMetadata)) {
            throw new IllegalStateException(
                    "Prepared paper fresh-spawn metadata did not contain a ResolvedSpawnMetadata instance."
            );
        }
        return (ResolvedSpawnMetadata) preparedMetadata;
    }

    private synchronized @NotNull ResolvedSpawnMetadata resolveSpawnMetadata(
            @NotNull EntityMetadata metadata,
            @NotNull Location location
    ) {
        ResolvedSpawnMetadata spawnMetadata = spawnMetadataRegistry.get(metadata.baseType());
        if (spawnMetadata != null) {
            return spawnMetadata;
        }

        Entity probeEntity = spawnVanillaEntity(location, metadata);
        try {
            Object probeHandle = resolveNativeHandle(probeEntity);
            Object nativeEntityType = resolveNativeEntityType(probeHandle);

            spawnMetadata = new ResolvedSpawnMetadata(metadata, probeHandle.getClass(), nativeEntityType);
            spawnMetadataRegistry.put(metadata.baseType(), spawnMetadata);
            return spawnMetadata;
        } finally {
            probeEntity.remove();
        }
    }

    private static @Nullable Object resolveNativeEntityType(@NotNull Object nativeEntity) {
        Method getTypeMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"getType", "ad"});
        if (getTypeMethod != null) {
            return ReflectionSupport.invoke(getTypeMethod, nativeEntity);
        }

        Method fallbackAccessor = findNoArgumentMethodByReturnTypeName(
                nativeEntity.getClass(),
                "net.minecraft.world.entity.EntityTypes"
        );
        if (fallbackAccessor != null) {
            return ReflectionSupport.invoke(fallbackAccessor, nativeEntity);
        }

        return null;
    }

    private @NotNull Object createFreshNativeEntity(
            @NotNull ResolvedSpawnMetadata spawnMetadata,
            @NotNull Location location
    ) {
        Object levelHandle = resolveNativeWorldHandle(location);
        Object nativeEntity = instantiateNativeEntity(
                spawnMetadata.nativeType(),
                spawnMetadata.nativeEntityType(),
                levelHandle,
                location
        );
        applySpawnLocation(nativeEntity, location);
        return nativeEntity;
    }

    private static @NotNull Object instantiateNativeEntity(
            @NotNull Class<?> nativeType,
            @Nullable Object nativeEntityType,
            @NotNull Object levelHandle,
            @NotNull Location location
    ) {
        Constructor<?> coordinateConstructor = findConstructor(
                nativeType,
                levelHandle.getClass(),
                double.class,
                double.class,
                double.class
        );
        if (coordinateConstructor != null) {
            return ReflectionSupport.instantiate(
                    coordinateConstructor,
                    levelHandle,
                    Double.valueOf(location.getX()),
                    Double.valueOf(location.getY()),
                    Double.valueOf(location.getZ())
            );
        }

        if (nativeEntityType != null) {
            Constructor<?> typedConstructor = findConstructor(
                    nativeType,
                    nativeEntityType.getClass(),
                    levelHandle.getClass()
            );
            if (typedConstructor != null) {
                return ReflectionSupport.instantiate(typedConstructor, nativeEntityType, levelHandle);
            }
        }

        Constructor<?> levelConstructor = findConstructor(nativeType, levelHandle.getClass());
        if (levelConstructor != null) {
            return ReflectionSupport.instantiate(levelConstructor, levelHandle);
        }

        throw new IllegalStateException(
                "Could not resolve a supported constructor for native entity type '" + nativeType.getName() + "'."
        );
    }

    private static @Nullable Constructor<?> findConstructor(
            @NotNull Class<?> type,
            @NotNull Class<?>... argumentTypes
    ) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != argumentTypes.length) {
                continue;
            }

            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!wrap(parameterTypes[index]).isAssignableFrom(wrap(argumentTypes[index]))) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }

            constructor.setAccessible(true);
            return constructor;
        }
        return null;
    }

    private static @Nullable Method findNoArgumentMethodByReturnTypeName(
            @NotNull Class<?> type,
            @NotNull String returnTypeName
    ) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0) {
                    continue;
                }
                if (!returnTypeName.equals(method.getReturnType().getName())) {
                    continue;
                }

                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static @NotNull Class<?> wrap(@NotNull Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static void applySpawnLocation(@NotNull Object nativeEntity, @NotNull Location location) {
        Method moveToMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"moveTo"},
                double.class,
                double.class,
                double.class,
                float.class,
                float.class
        );
        if (moveToMethod != null) {
            ReflectionSupport.invoke(
                    moveToMethod,
                    nativeEntity,
                    Double.valueOf(location.getX()),
                    Double.valueOf(location.getY()),
                    Double.valueOf(location.getZ()),
                    Float.valueOf(location.getYaw()),
                    Float.valueOf(location.getPitch())
            );
            return;
        }

        Method setPosMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"setPos", "setPosRaw"},
                double.class,
                double.class,
                double.class
        );
        if (setPosMethod != null) {
            ReflectionSupport.invoke(
                    setPosMethod,
                    nativeEntity,
                    Double.valueOf(location.getX()),
                    Double.valueOf(location.getY()),
                    Double.valueOf(location.getZ())
            );
        }

        Method setYRotMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"setYRot"}, float.class);
        if (setYRotMethod != null) {
            ReflectionSupport.invoke(setYRotMethod, nativeEntity, Float.valueOf(location.getYaw()));
        }

        Method setXRotMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"setXRot"}, float.class);
        if (setXRotMethod != null) {
            ReflectionSupport.invoke(setXRotMethod, nativeEntity, Float.valueOf(location.getPitch()));
        }
    }

    private static @NotNull TrackedEntityState resolveTrackedEntityState(@NotNull Object nativeEntity) {
        Object trackedEntity = resolveTrackedEntityHandle(nativeEntity);
        if (trackedEntity == null) {
            return TrackedEntityState.untracked();
        }

        Field serverEntityField = ReflectionSupport.findField(trackedEntity.getClass(), "serverEntity", "b");
        Object serverEntity = serverEntityField == null ? null : ReflectionSupport.readField(serverEntityField, trackedEntity);
        return new TrackedEntityState(trackedEntity, serverEntity);
    }

    private static @Nullable Object resolveTrackedEntityHandle(@NotNull Object nativeEntity) {
        Method trackedEntityMethod = ReflectionSupport.findNamedMethod(
                nativeEntity.getClass(),
                new String[]{"moonrise$getTrackedEntity", "getTrackedEntity"}
        );
        if (trackedEntityMethod != null) {
            return ReflectionSupport.invoke(trackedEntityMethod, nativeEntity);
        }

        Field trackedEntityField = ReflectionSupport.findField(nativeEntity.getClass(), "tracker");
        if (trackedEntityField == null) {
            trackedEntityField = findFieldByTypeName(
                    nativeEntity.getClass(),
                    "net.minecraft.server.level.PlayerChunkMap$EntityTracker"
            );
        }
        if (trackedEntityField == null) {
            return null;
        }
        return ReflectionSupport.readField(trackedEntityField, nativeEntity);
    }

    private @NotNull EntityMetadata requireMetadata(@NotNull CustomEntityBaseType baseType) {
        EntityMetadata metadata = metadataRegistry.get(baseType);
        if (metadata == null) {
            throw new UnsupportedOperationException(
                    "Minecraft 1.19.2-1.20.6 does not support spawn and attach for base type '" + baseType + "'."
            );
        }
        return metadata;
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
                    || baseType == CustomEntityBaseType.COMPLEX_PART
                    || baseType == CustomEntityBaseType.COW) {
                continue;
            }
            metadata.put(baseType, new EntityMetadata(baseType, entityType));
        }
        return metadata;
    }

    private static @NotNull Entity spawnVanillaEntity(
            @NotNull Location location,
            @NotNull EntityMetadata metadata
    ) {
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        return world.spawnEntity(location.clone(), metadata.entityType());
    }

    private static @NotNull Object resolveNativeHandle(@NotNull Entity entity) {
        Method getHandleMethod = ReflectionSupport.requireNamedMethod(entity.getClass(), new String[]{"getHandle"});
        return ReflectionSupport.invoke(getHandleMethod, entity);
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

    private static @NotNull Entity resolveBukkitEntity(@NotNull Object nmsEntity) {
        Method getBukkitEntityMethod = ReflectionSupport.requireNamedMethod(
                nmsEntity.getClass(),
                new String[]{"getBukkitEntity"}
        );
        return (Entity) ReflectionSupport.invoke(getBukkitEntityMethod, nmsEntity);
    }

    private static void rebindBukkitZombieInternal(@NotNull Entity entity, @NotNull Object replacementHandle) {
        Method setHandleMethod = ReflectionSupport.requireCompatibleMethod(
                entity.getClass(),
                new String[]{"setHandle"},
                replacementHandle.getClass()
        );
        ReflectionSupport.invoke(setHandleMethod, entity, replacementHandle);
    }

    private static void rebindModernBukkitBridgeInternal(
            @NotNull Entity bukkitEntity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field bukkitEntityField = ReflectionSupport.findField(oldHandle.getClass(), "bukkitEntity");
        if (bukkitEntityField == null) {
            return;
        }
        ReflectionSupport.writeField(bukkitEntityField, replacementHandle, bukkitEntity);
        ReflectionSupport.writeField(bukkitEntityField, oldHandle, null);
    }

    private static void replaceModernWorldReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Object level = resolveEntityLevel(oldHandle);
        replaceSectionManagerVisibleStorage(level, oldHandle, replacementHandle);
        replacePaperChunkSystemLookup(level, oldHandle, replacementHandle);
        updateTrackedEntity(oldHandle, replacementHandle);
        rebindModernLevelCallback(oldHandle, replacementHandle);
        replaceModernLifecycleCollections(level, oldHandle, replacementHandle);
    }

    private static void replaceSectionManagerWorldReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Object level = resolveEntityLevel(oldHandle);
        replaceSectionManagerVisibleStorage(level, oldHandle, replacementHandle);
        updateTrackedEntity(oldHandle, replacementHandle);
        rebindModernLevelCallback(oldHandle, replacementHandle);
        replaceModernLifecycleCollections(level, oldHandle, replacementHandle);
    }

    private static void replacePaperChunkSystemWorldReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Object level = resolveEntityLevel(oldHandle);
        replaceSectionManagerVisibleStorage(level, oldHandle, replacementHandle);
        replacePaperChunkSystemLookup(level, oldHandle, replacementHandle);
        updateTrackedEntity(oldHandle, replacementHandle);
        rebindModernLevelCallback(oldHandle, replacementHandle);
        replaceModernLifecycleCollections(level, oldHandle, replacementHandle);
    }

    private static @Nullable Object resolveEntityLevel(@NotNull Object entityHandle) {
        Field levelField = ReflectionSupport.findField(entityHandle.getClass(), "level", "s", "t");
        return levelField == null ? null : ReflectionSupport.readField(levelField, entityHandle);
    }

    private static void replaceSectionManagerVisibleStorage(
            @Nullable Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (level == null) {
            return;
        }

        Object entityManager = resolveSectionManager(level);
        if (entityManager == null) {
            return;
        }

        Object visibleEntityStorage = resolveSectionManagerVisibleStorage(entityManager);
        if (visibleEntityStorage == null) {
            return;
        }

        int entityId = resolveEntityId(oldHandle);
        Object uuid = resolveEntityUuid(oldHandle);
        replaceMapEntryByKey(
                visibleEntityStorage,
                new String[]{"byId", "b"},
                Integer.valueOf(entityId),
                oldHandle,
                replacementHandle
        );
        replaceMapEntryByKey(
                visibleEntityStorage,
                new String[]{"byUUID", "byUuid", "c"},
                uuid,
                oldHandle,
                replacementHandle
        );
    }

    private static @Nullable Object resolveSectionManager(@NotNull Object level) {
        Field entityManagerField = ReflectionSupport.findField(level.getClass(), "entityManager", "G");
        if (entityManagerField == null) {
            entityManagerField = findFieldByTypeName(
                    level.getClass(),
                    "net.minecraft.world.level.entity.PersistentEntitySectionManager"
            );
        }
        if (entityManagerField == null) {
            return null;
        }
        return ReflectionSupport.readField(entityManagerField, level);
    }

    private static @Nullable Object resolveSectionManagerVisibleStorage(@NotNull Object entityManager) {
        Field visibleEntityStorageField = ReflectionSupport.findField(entityManager.getClass(), "visibleEntityStorage", "e");
        if (visibleEntityStorageField == null) {
            visibleEntityStorageField = findFieldByTypeName(
                    entityManager.getClass(),
                    "net.minecraft.world.level.entity.EntityLookup"
            );
        }
        if (visibleEntityStorageField == null) {
            return null;
        }
        return ReflectionSupport.readField(visibleEntityStorageField, entityManager);
    }

    private static boolean replacePaperChunkSystemLookup(
            @Nullable Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (level == null) {
            return false;
        }

        Object entityLookup = resolvePaperEntityLookup(level);
        if (entityLookup == null) {
            return false;
        }

        int entityId = resolveEntityId(oldHandle);
        Object uuid = resolveEntityUuid(oldHandle);
        replaceMapEntryByKey(
                entityLookup,
                new String[]{"entityById"},
                Integer.valueOf(entityId),
                oldHandle,
                replacementHandle
        );
        replaceMapEntryByKey(
                entityLookup,
                new String[]{"entityByUUID", "entityByUuid"},
                uuid,
                oldHandle,
                replacementHandle
        );
        replaceManagedCollectionField(entityLookup, new String[]{"accessibleEntities"}, oldHandle, replacementHandle);

        Object chunkSlices = resolvePaperChunkSlices(level, entityLookup, oldHandle);
        if (chunkSlices != null) {
            Method removeMethod = ReflectionSupport.findCompatibleMethod(
                    chunkSlices.getClass(),
                    new String[]{"removeEntity"},
                    oldHandle.getClass(),
                    int.class
            );
            Method addMethod = ReflectionSupport.findCompatibleMethod(
                    chunkSlices.getClass(),
                    new String[]{"addEntity"},
                    replacementHandle.getClass(),
                    int.class
            );
            int sectionY = resolveSectionCoordinate(
                    oldHandle,
                    new String[]{"sectionY"},
                    new String[]{"moonrise$getSectionY", "getSectionY"}
            );
            if (removeMethod != null) {
                ReflectionSupport.invoke(removeMethod, chunkSlices, oldHandle, Integer.valueOf(sectionY));
            }
            if (addMethod != null) {
                ReflectionSupport.invoke(addMethod, chunkSlices, replacementHandle, Integer.valueOf(sectionY));
            }
        }

        replaceManagedCollectionField(entityLookup, new String[]{"trackerEntities"}, oldHandle, replacementHandle);
        return true;
    }

    private static @Nullable Object resolvePaperEntityLookup(@NotNull Object level) {
        Method entityLookupMethod = ReflectionSupport.findNamedMethod(
                level.getClass(),
                new String[]{"getEntityLookup", "moonrise$getEntityLookup"}
        );
        if (entityLookupMethod != null) {
            return ReflectionSupport.invoke(entityLookupMethod, level);
        }

        Field entityLookupField = ReflectionSupport.findField(level.getClass(), "entityLookup");
        if (entityLookupField == null) {
            entityLookupField = findFieldByTypeName(
                    level.getClass(),
                    "io.papermc.paper.chunk.system.entity.EntityLookup"
            );
        }
        if (entityLookupField == null) {
            return null;
        }
        return ReflectionSupport.readField(entityLookupField, level);
    }

    private static @Nullable Object resolvePaperChunkSlices(
            @NotNull Object level,
            @NotNull Object entityLookup,
            @NotNull Object oldHandle
    ) {
        int sectionX = resolveSectionCoordinate(
                oldHandle,
                new String[]{"sectionX"},
                new String[]{"moonrise$getSectionX", "getSectionX"}
        );
        int sectionZ = resolveSectionCoordinate(
                oldHandle,
                new String[]{"sectionZ"},
                new String[]{"moonrise$getSectionZ", "getSectionZ"}
        );

        Method getChunkMethod = ReflectionSupport.findNamedMethod(entityLookup.getClass(), new String[]{"getChunk"}, int.class, int.class);
        if (getChunkMethod != null) {
            Object chunkSlices = ReflectionSupport.invoke(
                    getChunkMethod,
                    entityLookup,
                    Integer.valueOf(sectionX),
                    Integer.valueOf(sectionZ)
            );
            if (chunkSlices != null) {
                return chunkSlices;
            }
        }

        Method getOrCreateChunkMethod = ReflectionSupport.findNamedMethod(
                entityLookup.getClass(),
                new String[]{"getOrCreateChunk"},
                int.class,
                int.class
        );
        if (getOrCreateChunkMethod == null) {
            return null;
        }
        return ReflectionSupport.invoke(
                getOrCreateChunkMethod,
                entityLookup,
                Integer.valueOf(sectionX),
                Integer.valueOf(sectionZ)
        );
    }

    private static int resolveSectionCoordinate(
            @NotNull Object handle,
            @NotNull String[] fieldNames,
            @NotNull String[] methodNames
    ) {
        Field sectionField = ReflectionSupport.findField(handle.getClass(), fieldNames);
        if (sectionField != null) {
            return ((Integer) ReflectionSupport.readField(sectionField, handle)).intValue();
        }

        Method sectionMethod = ReflectionSupport.requireNamedMethod(handle.getClass(), methodNames);
        return ((Integer) ReflectionSupport.invoke(sectionMethod, handle)).intValue();
    }

    private static void updateTrackedEntity(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Object trackedEntity = resolveTrackedEntityHandle(oldHandle);
        if (trackedEntity == null) {
            return;
        }

        Field trackerEntityField = ReflectionSupport.findField(trackedEntity.getClass(), "entity", "c");
        if (trackerEntityField != null) {
            ReflectionSupport.writeField(trackerEntityField, trackedEntity, replacementHandle);
        }

        Field serverEntityField = ReflectionSupport.findField(trackedEntity.getClass(), "serverEntity", "b");
        if (serverEntityField != null) {
            Object serverEntity = ReflectionSupport.readField(serverEntityField, trackedEntity);
            if (serverEntity != null) {
                Field serverEntityHandleField = ReflectionSupport.findField(serverEntity.getClass(), "entity", "d");
                if (serverEntityHandleField != null) {
                    ReflectionSupport.writeField(serverEntityHandleField, serverEntity, replacementHandle);
                }
            }
        }

        Field trackerField = ReflectionSupport.findField(replacementHandle.getClass(), "tracker");
        if (trackerField == null) {
            trackerField = findFieldByTypeName(replacementHandle.getClass(), trackedEntity.getClass().getName());
        }
        if (trackerField != null) {
            ReflectionSupport.writeField(trackerField, replacementHandle, trackedEntity);
            ReflectionSupport.writeField(trackerField, oldHandle, null);
        }
    }

    private static void rebindModernLevelCallback(@NotNull Object oldHandle, @NotNull Object replacementHandle) {
        Field levelCallbackField = ReflectionSupport.findField(oldHandle.getClass(), "levelCallback", "aR", "aO");
        if (levelCallbackField == null) {
            levelCallbackField = findFieldByTypeName(
                    oldHandle.getClass(),
                    "net.minecraft.world.level.entity.EntityInLevelCallback"
            );
        }
        if (levelCallbackField == null) {
            return;
        }

        Object oldLevelCallback = ReflectionSupport.readField(levelCallbackField, oldHandle);
        if (oldLevelCallback == null) {
            return;
        }

        migrateModernSectionMembership(oldLevelCallback, oldHandle, replacementHandle);

        Object replacementLevelCallback = retargetModernSectionCallback(oldLevelCallback, oldHandle, replacementHandle);
        if (replacementLevelCallback == null) {
            replacementLevelCallback = recreateModernSectionCallback(oldLevelCallback, replacementHandle);
        }
        if (replacementLevelCallback == null) {
            return;
        }

        Method setReplacementLevelCallbackMethod = ReflectionSupport.requireCompatibleMethod(
                replacementHandle.getClass(),
                new String[]{"setLevelCallback", "a"},
                replacementLevelCallback.getClass()
        );
        ReflectionSupport.invoke(setReplacementLevelCallbackMethod, replacementHandle, replacementLevelCallback);

        Method clearOldLevelCallbackMethod = ReflectionSupport.requireCompatibleMethod(
                oldHandle.getClass(),
                new String[]{"setLevelCallback", "a"},
                oldLevelCallback.getClass()
        );
        ReflectionSupport.invoke(clearOldLevelCallbackMethod, oldHandle, (Object) null);
    }

    private static @Nullable Constructor<?> findModernSectionCallbackConstructor(
            @NotNull Class<?> callbackType,
            @Nullable Object manager,
            @NotNull Object replacementHandle,
            @Nullable Object currentSection
    ) {
        for (Constructor<?> constructor : callbackType.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 4) {
                continue;
            }
            if (manager == null || !parameterTypes[0].isAssignableFrom(manager.getClass())) {
                continue;
            }
            if (!parameterTypes[1].isAssignableFrom(replacementHandle.getClass())) {
                continue;
            }
            if (!(parameterTypes[2] == long.class || parameterTypes[2] == Long.class)) {
                continue;
            }
            if (currentSection != null && !parameterTypes[3].isAssignableFrom(currentSection.getClass())) {
                continue;
            }
            constructor.setAccessible(true);
            return constructor;
        }
        return null;
    }

    private static void replaceModernLifecycleCollections(
            @Nullable Object level,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (level == null) {
            return;
        }

        replaceManagedCollectionField(level, new String[]{"entityTickList", "O", "F"}, oldHandle, replacementHandle);
        replaceManagedCollectionField(level, new String[]{"navigatingMobs", "V", "M"}, oldHandle, replacementHandle);
    }

    private static void replaceManagedCollectionField(
            @NotNull Object owner,
            @NotNull String[] fieldNames,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        Field field = ReflectionSupport.findField(owner.getClass(), fieldNames);
        if (field == null) {
            return;
        }

        Object collection = ReflectionSupport.readField(field, owner);
        replaceManagedCollectionEntry(collection, oldValue, newValue);
    }

    private static boolean containsManagedEntry(@Nullable Object collection, @NotNull Object value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (collection == null) {
            return false;
        }

        Method containsMethod = ReflectionSupport.findCompatibleMethod(
                collection.getClass(),
                new String[]{"contains", "c"},
                value.getClass()
        );
        if (containsMethod != null) {
            Object result = ReflectionSupport.invoke(containsMethod, collection, value);
            return result instanceof Boolean && ((Boolean) result).booleanValue();
        }

        if (collection instanceof Collection) {
            return ((Collection<?>) collection).contains(value);
        }

        Method getEntitiesMethod = ReflectionSupport.findNamedMethod(collection.getClass(), new String[]{"getEntities", "b"});
        if (getEntitiesMethod == null) {
            return false;
        }

        Object entities = ReflectionSupport.invoke(getEntitiesMethod, collection);
        if (entities instanceof Stream) {
            try (Stream<?> stream = (Stream<?>) entities) {
                return stream.anyMatch(candidate -> candidate == value);
            }
        }
        if (entities instanceof Iterable) {
            for (Object candidate : (Iterable<?>) entities) {
                if (candidate == value) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void replaceMapEntryByKey(
            @NotNull Object owner,
            @NotNull String[] fieldNames,
            @Nullable Object key,
            @NotNull Object oldValue,
            @NotNull Object newValue
    ) {
        if (key == null) {
            return;
        }

        Field field = ReflectionSupport.findField(owner.getClass(), fieldNames);
        if (field == null) {
            return;
        }

        Object mapping = ReflectionSupport.readField(field, owner);
        if (mapping == null) {
            return;
        }
        if (mapping instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> values = (Map<Object, Object>) mapping;
            if (values.get(key) == oldValue) {
                values.put(key, newValue);
            }
            return;
        }

        Method getMethod = ReflectionSupport.findCompatibleMethod(mapping.getClass(), new String[]{"get"}, key.getClass());
        Method putMethod = ReflectionSupport.findCompatibleMethod(
                mapping.getClass(),
                new String[]{"put"},
                key.getClass(),
                newValue.getClass()
        );
        if (getMethod != null && putMethod != null) {
            Object currentValue = ReflectionSupport.invoke(getMethod, mapping, key);
            if (currentValue == oldValue) {
                ReflectionSupport.invoke(putMethod, mapping, key, newValue);
            }
            return;
        }

        if (key instanceof Integer) {
            Method primitiveGetMethod = ReflectionSupport.findCompatibleMethod(mapping.getClass(), new String[]{"get"}, int.class);
            Method primitivePutMethod = ReflectionSupport.findCompatibleMethod(
                    mapping.getClass(),
                    new String[]{"put"},
                    int.class,
                    newValue.getClass()
            );
            if (primitiveGetMethod != null && primitivePutMethod != null) {
                Object currentValue = ReflectionSupport.invoke(
                        primitiveGetMethod,
                        mapping,
                        Integer.valueOf(((Integer) key).intValue())
                );
                if (currentValue == oldValue) {
                    ReflectionSupport.invoke(primitivePutMethod, mapping, Integer.valueOf(((Integer) key).intValue()), newValue);
                }
                return;
            }
        }

        if (key instanceof Long) {
            Method primitiveGetMethod = ReflectionSupport.findCompatibleMethod(mapping.getClass(), new String[]{"get"}, long.class);
            Method primitivePutMethod = ReflectionSupport.findCompatibleMethod(
                    mapping.getClass(),
                    new String[]{"put"},
                    long.class,
                    newValue.getClass()
            );
            if (primitiveGetMethod != null && primitivePutMethod != null) {
                Object currentValue = ReflectionSupport.invoke(primitiveGetMethod, mapping, Long.valueOf(((Long) key).longValue()));
                if (currentValue == oldValue) {
                    ReflectionSupport.invoke(primitivePutMethod, mapping, Long.valueOf(((Long) key).longValue()), newValue);
                }
            }
        }
    }

    private static int resolveEntityId(@NotNull Object nativeEntity) {
        return ((Integer) ReflectionSupport.invoke(
                ReflectionSupport.requireNamedMethod(nativeEntity.getClass(), new String[]{"getId"}),
                nativeEntity
        )).intValue();
    }

    private static @Nullable Object resolveEntityUuid(@NotNull Object nativeEntity) {
        Method getUuidMethod = ReflectionSupport.findNamedMethod(nativeEntity.getClass(), new String[]{"getUUID", "getUniqueID"});
        return getUuidMethod == null ? null : ReflectionSupport.invoke(getUuidMethod, nativeEntity);
    }

    private static void rewireModernVehicleAndPassengerReferencesInternal(
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        Field passengersField = ReflectionSupport.findField(oldHandle.getClass(), "passengers", "au");
        Field vehicleField = ReflectionSupport.findField(oldHandle.getClass(), "vehicle", "av", "au");
        if (passengersField == null || vehicleField == null) {
            return;
        }

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

    private static void refreshModernBukkitWrappersInternal(@NotNull Entity entity) {
        Field equipmentField = ReflectionSupport.findField(entity.getClass(), "equipment");
        if (equipmentField != null) {
            ReflectionSupport.writeField(equipmentField, entity, null);
        }
    }

    private static void markModernEntityRemovedInternal(@NotNull Object oldHandle) {
        Class<?> removalReasonType = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity$RemovalReason");
        Object discarded = ReflectionSupport.readField(
                ReflectionSupport.requireField(removalReasonType, "DISCARDED", "c"),
                null
        );
        Method setRemovedMethod = ReflectionSupport.requireCompatibleMethod(
                oldHandle.getClass(),
                new String[]{"setRemoved", "a", "b"},
                removalReasonType
        );
        ReflectionSupport.invoke(setRemovedMethod, oldHandle, discarded);

        Field validField = ReflectionSupport.findField(oldHandle.getClass(), "valid");
        if (validField != null) {
            ReflectionSupport.writeField(validField, oldHandle, Boolean.FALSE);
        }

        Field inWorldField = ReflectionSupport.findField(oldHandle.getClass(), "inWorld");
        if (inWorldField != null) {
            ReflectionSupport.writeField(inWorldField, oldHandle, Boolean.FALSE);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> void schedulePaperReplacementRepairPass(
            @NotNull NativeEntityLifecycle<T> lifecycle,
            @NotNull T entity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        requireRuntimeLifecycle(lifecycle).scheduleNextTickRepair(new Runnable() {
            @Override
            public void run() {
                rebindBukkitZombie(entity, replacementHandle);
                rebindModernBukkitBridge(entity, oldHandle, replacementHandle);
                replaceModernWorldReferences(oldHandle, replacementHandle);
                rewireModernVehicleAndPassengerReferences(oldHandle, replacementHandle);
                refreshModernBukkitWrappers(entity);
            }
        });
    }

    private static @Nullable Field findFieldByTypeName(@NotNull Class<?> type, @NotNull String... candidateTypeNames) {
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                String fieldTypeName = field.getType().getName();
                for (String candidateTypeName : candidateTypeNames) {
                    if (candidateTypeName.equals(fieldTypeName)) {
                        field.setAccessible(true);
                        return field;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static @NotNull UnsupportedOperationException unsupported(@NotNull String operation) {
        return new UnsupportedOperationException(
                "Minecraft " + SUPPORTED_FAMILY + " skeleton does not implement native " + operation + " yet (anchor " + VERSION + ")."
        );
    }

    private static @NotNull UnsupportedOperationException unsupportedPublicationFamily(
            @NotNull EntityPublicationFamily family,
            @NotNull String operation
    ) {
        return new UnsupportedOperationException(
                "Minecraft "
                        + SUPPORTED_FAMILY
                        + " skeleton does not support publication family '"
                        + family.id()
                        + "' for native "
                        + operation
                        + " yet (anchor "
                        + VERSION
                        + ")."
        );
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

    private static final class ResolvedSpawnMetadata {
        private final EntityMetadata metadata;
        private final Class<?> nativeType;
        private final Object nativeEntityType;

        private ResolvedSpawnMetadata(
                @NotNull EntityMetadata metadata,
                @NotNull Class<?> nativeType,
                @Nullable Object nativeEntityType
        ) {
            this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
            this.nativeType = Objects.requireNonNull(nativeType, "nativeType cannot be null");
            this.nativeEntityType = nativeEntityType;
        }

        public @NotNull CustomEntityBaseType baseType() {
            return metadata.baseType();
        }

        public @NotNull Class<?> nativeType() {
            return nativeType;
        }

        public @Nullable Object nativeEntityType() {
            return nativeEntityType;
        }
    }

    private static final class ResolvedReplacementMetadata {
        private final EntityMetadata metadata;
        private final Class<?> nativeType;
        private final Class<?> generatedType;
        private final Collection<GeneratedNativeHookSpec> hookSpecs;

        private ResolvedReplacementMetadata(
                @NotNull EntityMetadata metadata,
                @NotNull Class<?> nativeType,
                @NotNull Class<?> generatedType,
                @NotNull Collection<GeneratedNativeHookSpec> hookSpecs
        ) {
            this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
            this.nativeType = Objects.requireNonNull(nativeType, "nativeType cannot be null");
            this.generatedType = Objects.requireNonNull(generatedType, "generatedType cannot be null");
            this.hookSpecs = Objects.requireNonNull(hookSpecs, "hookSpecs cannot be null");
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
    }

    private static final class TrackedEntityState {
        private static final TrackedEntityState UNTRACKED = new TrackedEntityState(null, null);

        private final Object trackedEntity;
        private final Object serverEntity;

        private TrackedEntityState(@Nullable Object trackedEntity, @Nullable Object serverEntity) {
            this.trackedEntity = trackedEntity;
            this.serverEntity = serverEntity;
        }

        public static @NotNull TrackedEntityState untracked() {
            return UNTRACKED;
        }

        public @Nullable Object trackedEntity() {
            return trackedEntity;
        }

        public @Nullable Object serverEntity() {
            return serverEntity;
        }
    }
}

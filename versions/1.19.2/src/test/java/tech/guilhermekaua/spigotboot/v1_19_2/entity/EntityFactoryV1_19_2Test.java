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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityId;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeNativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EntityFactoryV1_19_2Test {
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUSIONS = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    private static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUSIONS = EnumSet.of(CustomEntityBaseType.COW);
    private static final EnumSet<CustomEntityBaseType> ADVERTISED_SUPPORT = createAdvertisedSupport();


    @Test
    void shouldExposeCapabilitiesAndBindingsAsStaticMetadataDescriptors() {
        assertNotNull(EntityFactoryV1_19_2.entityCapabilities());
        assertNotNull(EntityFactoryV1_19_2.entityBindings());
    }

    @Test
    void shouldMatchTheExplicitModernSupportMatrixContract() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();

        assertSupportMatrix(factory::supports, ADVERTISED_SUPPORT, PRESERVED_EXCLUSIONS);
    }

    @Test
    void shouldDocumentPermanentAndPreservedExclusionsExplicitly() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();

        assertFalse(ADVERTISED_SUPPORT.contains(CustomEntityBaseType.COW));
        assertFalse(factory.supports(CustomEntityBaseType.COW));
        assertFalse(factory.supports(CustomEntityBaseType.UNKNOWN));
        assertFalse(factory.supports(CustomEntityBaseType.PLAYER));
        assertFalse(factory.supports(CustomEntityBaseType.WEATHER));
        assertFalse(factory.supports(CustomEntityBaseType.COMPLEX_PART));
        assertTrue(factory.supports(CustomEntityBaseType.ZOMBIE));
    }

    @Test
    void resolveNativeEntityType_shouldFallbackToObfuscatedAccessorWhenGetTypeIsUnavailable() {
        StubEntityType entityType = new StubEntityType();

        Object resolved = assertDoesNotThrow(() -> invokeStatic(
                "resolveNativeEntityType",
                new Class<?>[]{Object.class},
                new ObfuscatedEntityTypeCarrier(entityType)
        ));

        assertSame(entityType, resolved);
    }

    @Test
    void instantiateNativeEntity_shouldPreferCoordinateConstructorWhenAvailable() {
        StubLevel level = new StubLevel();
        Location location = new Location(null, 1.25D, 2.5D, 3.75D, 0.0F, 0.0F);

        CoordinateConstructorEntity entity = assertDoesNotThrow(() -> (CoordinateConstructorEntity) invokeStatic(
                "instantiateNativeEntity",
                new Class<?>[]{Class.class, Object.class, Object.class, Location.class},
                CoordinateConstructorEntity.class,
                new StubEntityType(),
                level,
                location
        ));

        assertSame(level, entity.level);
        assertEquals(1.25D, entity.spawnX, 0.0D);
        assertEquals(2.5D, entity.spawnY, 0.0D);
        assertEquals(3.75D, entity.spawnZ, 0.0D);
    }

    @Test
    void applySpawnLocation_shouldFallbackToSetPosAndRotationsWhenMoveToIsUnavailable() {
        RotationOnlyEntity entity = new RotationOnlyEntity(new StubLevel());
        Location location = new Location(null, 4.0D, 5.0D, 6.0D, 90.0F, 45.0F);

        assertDoesNotThrow(() -> invokeStatic(
                "applySpawnLocation",
                new Class<?>[]{Object.class, Location.class},
                entity,
                location
        ));

        assertEquals(4.0D, entity.x, 0.0D);
        assertEquals(5.0D, entity.y, 0.0D);
        assertEquals(6.0D, entity.z, 0.0D);
        assertEquals(90.0F, entity.yRot, 0.0F);
        assertEquals(45.0F, entity.xRot, 0.0F);
    }

    @Test
    void resolveTrackingHandles_shouldExposeTrackedEntityStateWhenAccessorExists() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        Object trackedEntity = new StubTrackedEntity("server-state");

        PaperFreshSpawnStrategy_1_21_plus.TrackingHandles trackingHandles = factory.resolveTrackingHandles(
                new StubTrackedEntityCarrier(trackedEntity)
        );

        assertSame(trackedEntity, trackingHandles.trackerEntryHandle());
        assertEquals("server-state", trackingHandles.trackerStateHandle());
    }

    @Test
    void resolveTrackingHandles_shouldFallbackToUntrackedStateWhenAccessorIsMissing() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();

        PaperFreshSpawnStrategy_1_21_plus.TrackingHandles trackingHandles = factory.resolveTrackingHandles(new Object());

        assertNull(trackingHandles.trackerEntryHandle());
        assertNull(trackingHandles.trackerStateHandle());
    }

    @Test
    void bindLifecycleToNativeEntity_shouldFlushSpawnedGoalMutationsThroughTheLiveTickPath() {
        EntityFactoryV1_19_2 factory = new EntityFactoryV1_19_2();
        HandleAwareWorld world = org.mockito.Mockito.mock(HandleAwareWorld.class);
        when(world.getHandle()).thenReturn(new SpawnLevelHandle());
        Location location = new Location(world, 2.0D, 64.0D, 2.0D);
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(
                        CustomEntityId.of("test", "spawn-goal-runtime-1192"),
                        CustomEntityBaseType.ZOMBIE,
                        Zombie.class
                )
                .controller(context -> new EntityController<Zombie>() {
                })
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .build();
        RuntimeGoalMutationExecutor<Zombie> executor = factory.createSpawnGoalMutationExecutor(
                template,
                SpawnOptions.at(location),
                MinecraftVersion.of(1, 19, 2)
        );
        RuntimeNativeEntityLifecycle<Zombie> lifecycle = new RuntimeNativeEntityLifecycle<Zombie>(
                template,
                SpawnOptions.at(location),
                MinecraftVersion.of(1, 19, 2),
                tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver.noop(),
                tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver.noop(),
                executor
        );

        Object preparedSpawnMetadata = createResolvedSpawnMetadata(CustomEntityBaseType.ZOMBIE, SpawnGoalNativeHandle.class);
        PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn preparedSpawn =
                new PaperFreshSpawnStrategy_1_21_plus.PreparedSpawn(preparedSpawnMetadata);
        SpawnGoalNativeHandle nativeEntity = (SpawnGoalNativeHandle) factory.createNativeEntity(preparedSpawn, location);
        HandleAwareZombie bukkitEntity = org.mockito.Mockito.mock(HandleAwareZombie.class);
        when(bukkitEntity.getHandle()).thenReturn(nativeEntity);
        when(bukkitEntity.isValid()).thenReturn(true);

        factory.bindLifecycleToNativeEntity(nativeEntity, preparedSpawn, lifecycle);
        lifecycle.bind(bukkitEntity);

        assertTrue(nativeEntity.goalSelector.containsManagedGoalNamed("ManagedVanillaGoalBridge"));
        assertEquals(0, nativeEntity.baseTickCount);

        CustomGoalKey customGoalKey = CustomGoalKey.of("test", "guard-owner");
        lifecycle.goalManager().removeVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT);
        lifecycle.goalManager().addCustom(GoalSelectorType.NORMAL, customGoalKey, 4);

        assertTrue(nativeEntity.goalSelector.containsManagedGoalNamed("ManagedVanillaGoalBridge"));
        assertFalse(nativeEntity.goalSelector.containsManagedCustomGoal());

        nativeEntity.tick();

        assertEquals(1, nativeEntity.baseTickCount);
        assertFalse(nativeEntity.goalSelector.containsManagedGoalNamed("ManagedVanillaGoalBridge"));
        assertTrue(nativeEntity.goalSelector.containsManagedCustomGoal());

        GoalProfile<HandleAwareZombie> snapshot = factory.createAttachedGoalMutationExecutor(
                CustomEntityBaseType.ZOMBIE,
                bukkitEntity,
                MinecraftVersion.of(1, 19, 2)
        ).initialManagedGoals();
        assertTrue(snapshot.vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertEquals(customGoalKey, snapshot.customGoals(GoalSelectorType.NORMAL).get(0).key());
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = EntityFactoryV1_19_2.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object createResolvedSpawnMetadata(CustomEntityBaseType baseType, Class<?> nativeType) {
        try {
            Class<?> entityMetadataType = Class.forName(EntityFactoryV1_19_2.class.getName() + "$EntityMetadata");
            Constructor<?> entityMetadataConstructor = entityMetadataType.getDeclaredConstructor(
                    CustomEntityBaseType.class,
                    EntityType.class
            );
            entityMetadataConstructor.setAccessible(true);
            Object entityMetadata = entityMetadataConstructor.newInstance(baseType, EntityType.ZOMBIE);

            Method resolveReplacementMetadata = EntityFactoryV1_19_2.class.getDeclaredMethod(
                    "resolveReplacementMetadata",
                    entityMetadataType,
                    Class.class
            );
            resolveReplacementMetadata.setAccessible(true);
            Object resolvedReplacementMetadata = resolveReplacementMetadata.invoke(null, entityMetadata, nativeType);

            Class<?> resolvedSpawnMetadataType = Class.forName(EntityFactoryV1_19_2.class.getName() + "$ResolvedSpawnMetadata");
            Class<?> resolvedReplacementMetadataType = Class.forName(
                    EntityFactoryV1_19_2.class.getName() + "$ResolvedReplacementMetadata"
            );
            Constructor<?> resolvedSpawnMetadataConstructor = resolvedSpawnMetadataType.getDeclaredConstructor(
                    resolvedReplacementMetadataType,
                    Object.class
            );
            resolvedSpawnMetadataConstructor.setAccessible(true);
            return resolvedSpawnMetadataConstructor.newInstance(resolvedReplacementMetadata, null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class StubLevel {
    }

    private static final class SpawnLevelHandle {
    }

    private static final class StubEntityType {
    }

    private static final class ObfuscatedEntityTypeCarrier {
        private final StubEntityType entityType;

        private ObfuscatedEntityTypeCarrier(StubEntityType entityType) {
            this.entityType = entityType;
        }

        public StubEntityType ad() {
            return entityType;
        }
    }

    private static final class CoordinateConstructorEntity {
        private final StubLevel level;
        private final double spawnX;
        private final double spawnY;
        private final double spawnZ;

        private CoordinateConstructorEntity(StubLevel level, double spawnX, double spawnY, double spawnZ) {
            this.level = level;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
        }
    }

    private static final class RotationOnlyEntity {
        private final StubLevel level;
        private double x;
        private double y;
        private double z;
        private float yRot;
        private float xRot;

        private RotationOnlyEntity(StubLevel level) {
            this.level = level;
        }

        public void setPos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void setYRot(float yRot) {
            this.yRot = yRot;
        }

        public void setXRot(float xRot) {
            this.xRot = xRot;
        }
    }

    private static final class StubTrackedEntityCarrier {
        private final Object trackedEntity;

        private StubTrackedEntityCarrier(Object trackedEntity) {
            this.trackedEntity = trackedEntity;
        }

        public Object moonrise$getTrackedEntity() {
            return trackedEntity;
        }
    }

    private static final class StubTrackedEntity {
        private final Object serverEntity;

        private StubTrackedEntity(Object serverEntity) {
            this.serverEntity = serverEntity;
        }
    }

    private interface HandleAwareZombie extends Zombie {
        Object getHandle();
    }

    private interface HandleAwareWorld extends World {
        Object getHandle();
    }

    public static class SpawnGoalNativeHandle {
        private final SelectorHandle goalSelector = new SelectorHandle();
        private final SelectorHandle targetSelector = new SelectorHandle();
        private int baseTickCount;

        public SpawnGoalNativeHandle(SpawnLevelHandle level) {
        }

        public void tick() {
            baseTickCount++;
        }
    }

    private static final class SelectorHandle {
        private final java.util.List<Object> availableGoals = new java.util.ArrayList<Object>();

        public void addGoal(int priority, Object goal) {
            availableGoals.add(new WrappedGoalHandle(priority, goal));
        }

        private boolean containsGoalType(Class<?> goalType) {
            for (Object entry : availableGoals) {
                if (entry instanceof WrappedGoalHandle && goalType.isInstance(((WrappedGoalHandle) entry).getGoal())) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsManagedCustomGoal() {
            return containsManagedGoalNamed("ManagedCustomGoalBridge");
        }

        private boolean containsManagedGoalNamed(String simpleName) {
            for (Object entry : availableGoals) {
                if (entry instanceof WrappedGoalHandle) {
                    String goalSimpleName = ((WrappedGoalHandle) entry).getGoal().getClass().getSimpleName();
                    if (simpleName.equals(goalSimpleName)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static final class WrappedGoalHandle {
        private final int priority;
        private final Object goal;

        private WrappedGoalHandle(int priority, Object goal) {
            this.priority = priority;
            this.goal = goal;
        }

        public int getPriority() {
            return priority;
        }

        public Object getGoal() {
            return goal;
        }
    }

    private static final class FloatGoal {
        private FloatGoal(SpawnGoalNativeHandle mob) {
        }
    }

    private static @NotNull EnumSet<CustomEntityBaseType> createAdvertisedSupport() {
        EnumSet<CustomEntityBaseType> advertisedSupport = EnumSet.noneOf(CustomEntityBaseType.class);
        for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
            if (PERMANENT_EXCLUSIONS.contains(baseType) || PRESERVED_EXCLUSIONS.contains(baseType)) {
                continue;
            }
            if (baseType.entityTypeOrNull() != null) {
                advertisedSupport.add(baseType);
            }
        }
        return advertisedSupport;
    }

    private static void assertSupportMatrix(
            Predicate<CustomEntityBaseType> supportProbe,
            @NotNull EnumSet<CustomEntityBaseType> advertisedSupport,
            @NotNull EnumSet<CustomEntityBaseType> preservedExclusions
    ) {
        EnumSet<CustomEntityBaseType> actualIncluded = EnumSet.noneOf(CustomEntityBaseType.class);
        for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
            SupportExpectation expectation = classify(baseType, advertisedSupport, preservedExclusions);
            boolean supported = supportProbe.test(baseType);

            assertEquals(
                    expectation.included(),
                    supported,
                    "Support matrix mismatch for " + baseType + ": " + expectation.rationale()
            );
            if (supported) {
                actualIncluded.add(baseType);
            }
        }

        assertEquals(advertisedSupport, actualIncluded, "Supported entities should match the advertised contract exactly.");
    }

    private static @NotNull SupportExpectation classify(
            @NotNull CustomEntityBaseType baseType,
            @NotNull EnumSet<CustomEntityBaseType> advertisedSupport,
            @NotNull EnumSet<CustomEntityBaseType> preservedExclusions
    ) {
        if (PERMANENT_EXCLUSIONS.contains(baseType)) {
            return new SupportExpectation(false, "permanent exclusion");
        }
        if (baseType.entityTypeOrNull() == null) {
            return new SupportExpectation(false, "Bukkit EntityType is absent for this version");
        }
        if (advertisedSupport.contains(baseType)) {
            return new SupportExpectation(true, "advertised version contract includes this base type");
        }
        if (preservedExclusions.contains(baseType)) {
            return new SupportExpectation(false, "version-local preserved exclusion");
        }
        return new SupportExpectation(false, "advertised version contract excludes this base type");
    }

    private static final class SupportExpectation {
        private final boolean included;
        private final String rationale;

        private SupportExpectation(boolean included, @NotNull String rationale) {
            this.included = included;
            this.rationale = rationale;
        }

        private boolean included() {
            return included;
        }

        private @NotNull String rationale() {
            return rationale;
        }
    }
}

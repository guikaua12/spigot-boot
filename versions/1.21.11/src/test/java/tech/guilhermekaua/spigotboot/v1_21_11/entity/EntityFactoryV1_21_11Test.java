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
package tech.guilhermekaua.spigotboot.v1_21_11.entity;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFactoryV1_21_11Test {
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUSIONS = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    private static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUSIONS = EnumSet.noneOf(CustomEntityBaseType.class);
    private static final EnumSet<CustomEntityBaseType> ADVERTISED_SUPPORT = createAdvertisedSupport();

    @Test
    void shouldExposeCapabilitiesAndBindingsAsStaticMetadataDescriptors() {
        assertNotNull(EntityFactoryV1_21_11.entityCapabilities());
        assertNotNull(EntityFactoryV1_21_11.entityBindings());
    }

    @Test
    void shouldMatchTheExplicitLatestSupportMatrixContract() {
        EntityFactoryV1_21_11 factory = allocateFactoryWithoutConstructor();

        assertSupportMatrix(factory::supports, ADVERTISED_SUPPORT, PRESERVED_EXCLUSIONS);
    }

    @Test
    void metadataRegistry_shouldMatchTheExplicitLatestSupportContractExactly() {
        assertEquals(ADVERTISED_SUPPORT, readMetadataRegistry().keySet());
    }

    @Test
    void shouldDocumentRepresentativeHostilePassiveAndSpecialCaseFamiliesExplicitly() {
        EntityFactoryV1_21_11 factory = allocateFactoryWithoutConstructor();

        assertTrue(factory.supports(CustomEntityBaseType.ZOMBIE));
        assertTrue(factory.supports(CustomEntityBaseType.SKELETON));
        assertTrue(factory.supports(CustomEntityBaseType.CREEPER));
        assertTrue(factory.supports(CustomEntityBaseType.ENDERMAN));

        assertTrue(factory.supports(CustomEntityBaseType.COW));
        assertTrue(factory.supports(CustomEntityBaseType.VILLAGER));
        assertTrue(factory.supports(CustomEntityBaseType.SHEEP));

        assertTrue(factory.supports(CustomEntityBaseType.ARMOR_STAND));
        assertTrue(factory.supports(CustomEntityBaseType.BOAT));
        assertTrue(factory.supports(CustomEntityBaseType.ITEM_FRAME));
        assertTrue(factory.supports(CustomEntityBaseType.FIREBALL));
        assertTrue(factory.supports(CustomEntityBaseType.LIGHTNING_BOLT));
        assertTrue(factory.supports(CustomEntityBaseType.ENDER_DRAGON));

        assertFalse(factory.supports(CustomEntityBaseType.UNKNOWN));
        assertFalse(factory.supports(CustomEntityBaseType.PLAYER));
        assertFalse(factory.supports(CustomEntityBaseType.WEATHER));
        assertFalse(factory.supports(CustomEntityBaseType.COMPLEX_PART));
    }

    @Test
    void recreateModernSectionCallback_shouldBindTheReplacementEntity() {
        StubSectionManager manager = new StubSectionManager();
        StubSection section = new StubSection();
        StubEntity oldEntity = new StubEntity(10);
        StubEntity replacementEntity = new StubEntity(10);
        StubCallback callback = new StubCallback(manager, oldEntity, 42L, section);

        Object rebound = EntityFactoryV1_21_11.recreateModernSectionCallback(callback, replacementEntity);

        StubCallback reboundCallback = assertInstanceOf(StubCallback.class, rebound);
        assertSame(manager, reboundCallback.this$0);
        assertSame(replacementEntity, reboundCallback.entity);
        assertEquals(42L, reboundCallback.currentSectionKey);
        assertSame(section, reboundCallback.currentSection);
    }

    @Test
    void retargetModernSectionCallback_shouldReuseTheExistingCallback() {
        StubSectionManager manager = new StubSectionManager();
        StubSection section = new StubSection();
        StubEntity oldEntity = new StubEntity(14);
        StubEntity replacementEntity = new StubEntity(14);
        StubCallback callback = new StubCallback(manager, oldEntity, 9L, section);

        Object rebound = EntityFactoryV1_21_11.retargetModernSectionCallback(
                callback,
                oldEntity,
                replacementEntity
        );

        StubCallback reboundCallback = assertInstanceOf(StubCallback.class, rebound);
        assertSame(callback, reboundCallback);
        assertSame(replacementEntity, reboundCallback.entity);
    }

    @Test
    void migrateModernSectionMembership_shouldReplaceTheTrackedEntity() {
        StubSection section = new StubSection();
        StubEntity oldEntity = new StubEntity(12);
        StubEntity replacementEntity = new StubEntity(12);
        StubCallback callback = new StubCallback(new StubSectionManager(), oldEntity, 7L, section);
        section.add(oldEntity);

        EntityFactoryV1_21_11.migrateModernSectionMembership(callback, oldEntity, replacementEntity);

        assertFalse(section.contains(oldEntity));
        assertTrue(section.contains(replacementEntity));
        assertEquals(1, section.size());
    }

    @Test
    void migrateModernSectionMembership_shouldStayIdempotentAfterExtraction() {
        StubSection section = new StubSection();
        StubEntity oldEntity = new StubEntity(12);
        StubEntity replacementEntity = new StubEntity(12);
        StubCallback callback = new StubCallback(new StubSectionManager(), oldEntity, 7L, section);
        section.add(oldEntity);

        EntityFactoryV1_21_11.migrateModernSectionMembership(callback, oldEntity, replacementEntity);
        EntityFactoryV1_21_11.migrateModernSectionMembership(callback, oldEntity, replacementEntity);

        assertFalse(section.contains(oldEntity));
        assertTrue(section.contains(replacementEntity));
        assertEquals(1, section.size());
    }

    @Test
    void migrateModernSectionMembership_shouldAddTheReplacementWhenTheOriginalWasAlreadyExtracted() {
        StubSection section = new StubSection();
        StubEntity oldEntity = new StubEntity(12);
        StubEntity replacementEntity = new StubEntity(12);
        StubCallback callback = new StubCallback(new StubSectionManager(), oldEntity, 7L, section);

        EntityFactoryV1_21_11.migrateModernSectionMembership(callback, oldEntity, replacementEntity);

        assertFalse(section.contains(oldEntity));
        assertTrue(section.contains(replacementEntity));
        assertEquals(1, section.size());
    }

    @Test
    void replaceManagedCollectionEntry_shouldOnlySwapWhenTheOriginalIsPresent() {
        StubEntity oldEntity = new StubEntity(3);
        StubEntity replacementEntity = new StubEntity(3);
        StubEntityTickList tickList = new StubEntityTickList();
        tickList.add(oldEntity);

        EntityFactoryV1_21_11.replaceManagedCollectionEntry(tickList, oldEntity, replacementEntity);
        EntityFactoryV1_21_11.replaceManagedCollectionEntry(tickList, oldEntity, replacementEntity);

        assertFalse(tickList.contains(oldEntity));
        assertTrue(tickList.contains(replacementEntity));
        assertEquals(1, tickList.size());

        StubNavigatingMobs navigatingMobs = new StubNavigatingMobs();
        EntityFactoryV1_21_11.replaceManagedCollectionEntry(navigatingMobs, oldEntity, replacementEntity);
        assertFalse(navigatingMobs.contains(replacementEntity));
    }

    private static final class StubSectionManager {
    }

    private static final class StubEntity {
        private final int id;

        private StubEntity(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    private static final class StubSection {
        private final Set<StubEntity> entities = new LinkedHashSet<StubEntity>();

        public void add(StubEntity entity) {
            entities.add(entity);
        }

        public boolean remove(StubEntity entity) {
            return entities.remove(entity);
        }

        public Stream<StubEntity> getEntities() {
            return entities.stream();
        }

        public boolean contains(StubEntity entity) {
            return entities.contains(entity);
        }

        public int size() {
            return entities.size();
        }
    }

    private static final class StubCallback {
        private final StubSectionManager this$0;
        private final StubEntity entity;
        private final long currentSectionKey;
        private final StubSection currentSection;

        private StubCallback(
                StubSectionManager manager,
                StubEntity entity,
                long currentSectionKey,
                StubSection currentSection
        ) {
            this.this$0 = manager;
            this.entity = entity;
            this.currentSectionKey = currentSectionKey;
            this.currentSection = currentSection;
        }
    }

    private static final class StubEntityTickList {
        private final Set<StubEntity> entities = new LinkedHashSet<StubEntity>();

        public void add(StubEntity entity) {
            entities.add(entity);
        }

        public void remove(StubEntity entity) {
            entities.remove(entity);
        }

        public boolean contains(StubEntity entity) {
            return entities.contains(entity);
        }

        public int size() {
            return entities.size();
        }
    }

    private static final class StubNavigatingMobs extends LinkedHashSet<StubEntity> {
    }

    @SuppressWarnings("unchecked")
    private static @NotNull EntityFactoryV1_21_11 allocateFactoryWithoutConstructor() {
        return (EntityFactoryV1_21_11) ReflectionSupport.allocateInstance(EntityFactoryV1_21_11.class);
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Map<CustomEntityBaseType, Object> readMetadataRegistry() {
        try {
            Field field = EntityFactoryV1_21_11.class.getDeclaredField("METADATA_REGISTRY");
            field.setAccessible(true);
            return (Map<CustomEntityBaseType, Object>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static @NotNull EnumSet<CustomEntityBaseType> createAdvertisedSupport() {
        EnumSet<CustomEntityBaseType> supportedBaseTypes = EnumSet.noneOf(CustomEntityBaseType.class);
        for (CustomEntityBaseType baseType : CustomEntityBaseType.values()) {
            if (PERMANENT_EXCLUSIONS.contains(baseType)) {
                continue;
            }
            EntityType entityType = baseType.entityTypeOrNull();
            if (entityType == null || entityType.getEntityClass() == null) {
                continue;
            }
            supportedBaseTypes.add(baseType);
        }
        return supportedBaseTypes;
    }

    private static void assertSupportMatrix(
            @NotNull Predicate<CustomEntityBaseType> supportProbe,
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

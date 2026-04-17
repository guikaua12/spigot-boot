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
package tech.guilhermekaua.spigotboot.v1_8_8.entity;

import net.minecraft.server.v1_8_R3.EntityTypes;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class EntityFactoryV1_8_8Test {
    private static final EnumSet<CustomEntityBaseType> PERMANENT_EXCLUSIONS = EnumSet.of(
            CustomEntityBaseType.UNKNOWN,
            CustomEntityBaseType.PLAYER,
            CustomEntityBaseType.WEATHER,
            CustomEntityBaseType.COMPLEX_PART
    );
    private static final EnumSet<CustomEntityBaseType> PRESERVED_EXCLUSIONS = EnumSet.noneOf(CustomEntityBaseType.class);
    private static final EnumSet<CustomEntityBaseType> ADVERTISED_SUPPORT = EnumSet.of(
            CustomEntityBaseType.ITEM,
            CustomEntityBaseType.EXPERIENCE_ORB,
            CustomEntityBaseType.LEASH_KNOT,
            CustomEntityBaseType.PAINTING,
            CustomEntityBaseType.ARROW,
            CustomEntityBaseType.SNOWBALL,
            CustomEntityBaseType.FIREBALL,
            CustomEntityBaseType.SMALL_FIREBALL,
            CustomEntityBaseType.ENDER_PEARL,
            CustomEntityBaseType.EYE_OF_ENDER,
            CustomEntityBaseType.EXPERIENCE_BOTTLE,
            CustomEntityBaseType.ITEM_FRAME,
            CustomEntityBaseType.WITHER_SKULL,
            CustomEntityBaseType.TNT,
            CustomEntityBaseType.FALLING_BLOCK,
            CustomEntityBaseType.FIREWORK_ROCKET,
            CustomEntityBaseType.ARMOR_STAND,
            CustomEntityBaseType.COMMAND_BLOCK_MINECART,
            CustomEntityBaseType.BOAT,
            CustomEntityBaseType.MINECART,
            CustomEntityBaseType.CHEST_MINECART,
            CustomEntityBaseType.FURNACE_MINECART,
            CustomEntityBaseType.TNT_MINECART,
            CustomEntityBaseType.HOPPER_MINECART,
            CustomEntityBaseType.SPAWNER_MINECART,
            CustomEntityBaseType.CREEPER,
            CustomEntityBaseType.SKELETON,
            CustomEntityBaseType.SPIDER,
            CustomEntityBaseType.GIANT,
            CustomEntityBaseType.ZOMBIE,
            CustomEntityBaseType.SLIME,
            CustomEntityBaseType.GHAST,
            CustomEntityBaseType.ZOMBIFIED_PIGLIN,
            CustomEntityBaseType.ENDERMAN,
            CustomEntityBaseType.CAVE_SPIDER,
            CustomEntityBaseType.SILVERFISH,
            CustomEntityBaseType.BLAZE,
            CustomEntityBaseType.MAGMA_CUBE,
            CustomEntityBaseType.ENDER_DRAGON,
            CustomEntityBaseType.WITHER,
            CustomEntityBaseType.BAT,
            CustomEntityBaseType.WITCH,
            CustomEntityBaseType.ENDERMITE,
            CustomEntityBaseType.GUARDIAN,
            CustomEntityBaseType.PIG,
            CustomEntityBaseType.SHEEP,
            CustomEntityBaseType.COW,
            CustomEntityBaseType.CHICKEN,
            CustomEntityBaseType.SQUID,
            CustomEntityBaseType.WOLF,
            CustomEntityBaseType.MOOSHROOM,
            CustomEntityBaseType.SNOW_GOLEM,
            CustomEntityBaseType.OCELOT,
            CustomEntityBaseType.IRON_GOLEM,
            CustomEntityBaseType.HORSE,
            CustomEntityBaseType.RABBIT,
            CustomEntityBaseType.VILLAGER,
            CustomEntityBaseType.END_CRYSTAL,
            CustomEntityBaseType.POTION,
            CustomEntityBaseType.EGG,
            CustomEntityBaseType.FISHING_BOBBER,
            CustomEntityBaseType.LIGHTNING_BOLT
    );


    @Test
    void shouldExposeCapabilitiesAndBindingsAsStaticMetadataDescriptors() {
        assertNotNull(EntityFactoryV1_8_8.entityCapabilities());
        assertNotNull(EntityFactoryV1_8_8.entityBindings());
    }

    @Test
    void shouldMatchTheExplicitSupportMatrixContract() {
        EntityFactoryV1_8_8 factory = allocateFactoryWithoutConstructor();

        assertSupportMatrix(factory::supports, ADVERTISED_SUPPORT, PRESERVED_EXCLUSIONS);
    }

    @Test
    void metadataRegistry_shouldMatchTheAdvertisedSupportContractExactly() {
        assertEquals(ADVERTISED_SUPPORT, readMetadataRegistry().keySet());
    }

    @Test
    void prepareReplacement_shouldRejectPermanentPlayerAttachParity() {
        EntityFactoryV1_8_8 factory = allocateFactoryWithoutConstructor();
        Player entity = Mockito.mock(Player.class);

        when(entity.getType()).thenReturn(EntityType.PLAYER);

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> factory.prepareReplacement(entity, new ProbeNativeEntity())
        );

        assertEquals(
                "Minecraft 1.8.8 does not support replacement for base type 'PLAYER'.",
                exception.getMessage()
        );
    }

    @Test
    void ensureTrackerMappings_shouldCopyLegacyEntityTypesMappingsForGeneratedSubclasses() {
        Map<Class<?>, String> originalClassToName = new LinkedHashMap<Class<?>, String>(EntityTypes.d);
        Map<Class<?>, Integer> originalClassToId = new LinkedHashMap<Class<?>, Integer>(EntityTypes.f);
        try {
            EntityTypes.d.clear();
            EntityTypes.f.clear();
            EntityTypes.d.put(ProbeNativeEntity.class, "ProbeZombie");
            EntityTypes.f.put(ProbeNativeEntity.class, Integer.valueOf(54));

            invokeStatic(
                    "ensureTrackerMappings",
                    new Class<?>[]{Class.class, Class.class},
                    ProbeNativeEntity.class,
                    GeneratedProbeEntity.class
            );

            assertEquals("ProbeZombie", EntityTypes.d.get(GeneratedProbeEntity.class));
            assertEquals(Integer.valueOf(54), EntityTypes.f.get(GeneratedProbeEntity.class));
        } finally {
            EntityTypes.d.clear();
            EntityTypes.d.putAll(originalClassToName);
            EntityTypes.f.clear();
            EntityTypes.f.putAll(originalClassToId);
        }
    }

    @SuppressWarnings("unchecked")
    private static @NotNull EntityFactoryV1_8_8 allocateFactoryWithoutConstructor() {
        return (EntityFactoryV1_8_8) ReflectionSupport.allocateInstance(EntityFactoryV1_8_8.class);
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Map<CustomEntityBaseType, Object> readMetadataRegistry() {
        try {
            Field field = EntityFactoryV1_8_8.class.getDeclaredField("METADATA_REGISTRY");
            field.setAccessible(true);
            return (Map<CustomEntityBaseType, Object>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = EntityFactoryV1_8_8.class.getDeclaredMethod(methodName, parameterTypes);
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

    private static class ProbeNativeEntity {
    }

    private static final class GeneratedProbeEntity extends ProbeNativeEntity {
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

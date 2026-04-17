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

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeNativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityFactoryV1_21_11GoalSupportTest {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 21, 11);

    @Test
    void shouldApplyBuilderTimeGoalsWhenTheLatestFamilyNativeHandleBinds() {
        VersionGoalSupportProvider provider = assertInstanceOf(
                VersionGoalSupportProvider.class,
                new SpigotVersionAdapterV1_21_11()
        );
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(CustomEntityBaseType.ZOMBIE, Zombie.class)
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .addVanillaGoal(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET, 2)
                .addCustomGoal(GoalSelectorType.NORMAL, CustomGoalKey.of("test", "follow-owner"), 5)
                .build();
        RuntimeNativeEntityLifecycle<GoalSupportFixturesV1_21_11.HandleAwareZombie> lifecycle =
                new RuntimeNativeEntityLifecycle<GoalSupportFixturesV1_21_11.HandleAwareZombie>(
                        castTemplate(template),
                        SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)),
                        VERSION,
                        EntityTransportResolver.noop(),
                        EntityPublicationBackendResolver.noop(),
                        provider.createSpawnGoalMutationExecutor(castTemplate(template), SpawnOptions.at(
                                new Location(Mockito.mock(World.class), 1.0D, 65.0D, 1.0D)
                        ), VERSION)
                );
        GoalSupportFixturesV1_21_11.StubMobHandle handle = new GoalSupportFixturesV1_21_11.StubMobHandle();
        GoalSupportFixturesV1_21_11.HandleAwareZombie entity = GoalSupportFixturesV1_21_11.createEntity(handle);
        lifecycle.bind(entity);

        LatestEntityGoalSupportV1_21_11.bindNativeHandle(lifecycle, handle);

        GoalProfile<GoalSupportFixturesV1_21_11.HandleAwareZombie> snapshot = provider.createAttachedGoalMutationExecutor(
                CustomEntityBaseType.ZOMBIE,
                entity,
                VERSION
        ).initialManagedGoals();

        assertEquals(VanillaGoalKey.FLOAT, snapshot.vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(CustomGoalKey.of("test", "follow-owner"), snapshot.customGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(VanillaGoalKey.HURT_BY_TARGET, snapshot.vanillaGoals(GoalSelectorType.TARGET).get(0).key());
        assertEquals(2, handle.size(GoalSelectorType.NORMAL));
        assertEquals(1, handle.size(GoalSelectorType.TARGET));
        assertTrue(handle.goalSelector.availableGoals.size() >= 2);
    }

    @Test
    void shouldCreateSpawnGoalExecutorsForRepresentativeBroadFamilies() {
        assertRepresentativeSpawnGoalBinding(CustomEntityBaseType.ZOMBIE);
        assertRepresentativeSpawnGoalBinding(CustomEntityBaseType.SKELETON);
        assertRepresentativeSpawnGoalBinding(CustomEntityBaseType.COW);
        assertRepresentativeSpawnGoalBinding(CustomEntityBaseType.ARMOR_STAND);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityTemplate<GoalSupportFixturesV1_21_11.HandleAwareZombie> castTemplate(EntityTemplate<Zombie> template) {
        return (EntityTemplate) template;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertRepresentativeSpawnGoalBinding(@NotNull CustomEntityBaseType baseType) {
        VersionGoalSupportProvider provider = assertInstanceOf(
                VersionGoalSupportProvider.class,
                new SpigotVersionAdapterV1_21_11()
        );
        EntityTemplate<Entity> template = (EntityTemplate) EntityTemplate.builder(baseType, resolveEntityClass(baseType))
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .addVanillaGoal(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET, 2)
                .addCustomGoal(GoalSelectorType.NORMAL, CustomGoalKey.of("test", "follow-owner"), 5)
                .build();
        RuntimeNativeEntityLifecycle<Entity> lifecycle = new RuntimeNativeEntityLifecycle<Entity>(
                template,
                SpawnOptions.at(new Location(Mockito.mock(World.class), 0.0D, 64.0D, 0.0D)),
                VERSION,
                EntityTransportResolver.noop(),
                EntityPublicationBackendResolver.noop(),
                provider.createSpawnGoalMutationExecutor(
                        template,
                        SpawnOptions.at(new Location(Mockito.mock(World.class), 1.0D, 65.0D, 1.0D)),
                        VERSION
                )
        );
        GoalSupportFixturesV1_21_11.StubMobHandle handle = new GoalSupportFixturesV1_21_11.StubMobHandle();
        GoalSupportFixturesV1_21_11.HandleAwareZombie entity = GoalSupportFixturesV1_21_11.createEntity(handle);
        lifecycle.bind(entity);

        LatestEntityGoalSupportV1_21_11.bindNativeHandle(lifecycle, handle);

        GoalProfile<?> snapshot = provider.createAttachedGoalMutationExecutor(baseType, entity, VERSION).initialManagedGoals();

        assertEquals(VanillaGoalKey.FLOAT, snapshot.vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(CustomGoalKey.of("test", "follow-owner"), snapshot.customGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(VanillaGoalKey.HURT_BY_TARGET, snapshot.vanillaGoals(GoalSelectorType.TARGET).get(0).key());
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Class<? extends Entity> resolveEntityClass(@NotNull CustomEntityBaseType baseType) {
        return (Class<? extends Entity>) baseType.entityTypeOrNull().getEntityClass().asSubclass(Entity.class);
    }
}

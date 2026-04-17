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
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalOperationResult;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.runtime.goal.RuntimeGoalMutationExecutor;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityGoalAttachV1_21_11Test {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 21, 11);

    @Test
    void shouldCaptureOnlyRecognizedAttachGoalsThroughTheLatestFamilyProvider() {
        VersionGoalSupportProvider provider = assertInstanceOf(
                VersionGoalSupportProvider.class,
                new SpigotVersionAdapterV1_21_11()
        );
        GoalSupportFixturesV1_21_11.StubMobHandle handle = new GoalSupportFixturesV1_21_11.StubMobHandle();
        handle.addGoal(GoalSelectorType.NORMAL, 6, new GoalSupportFixturesV1_21_11.LookAtPlayerGoal());
        handle.addGoal(GoalSelectorType.NORMAL, 4, new GoalSupportFixturesV1_21_11.WaterAvoidingRandomStrollGoal());
        handle.addGoal(GoalSelectorType.NORMAL, 1, new GoalSupportFixturesV1_21_11.UnknownGoal());
        handle.addGoal(GoalSelectorType.TARGET, 2, new GoalSupportFixturesV1_21_11.HurtByTargetGoal());
        GoalSupportFixturesV1_21_11.HandleAwareZombie entity = GoalSupportFixturesV1_21_11.createEntity(handle);

        RuntimeGoalMutationExecutor<GoalSupportFixturesV1_21_11.HandleAwareZombie> executor = provider.createAttachedGoalMutationExecutor(
                CustomEntityBaseType.ZOMBIE,
                entity,
                VERSION
        );
        GoalProfile<GoalSupportFixturesV1_21_11.HandleAwareZombie> managedGoals = executor.initialManagedGoals();

        assertEquals(2, managedGoals.vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(VanillaGoalKey.LOOK_AT_PLAYER, managedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(6, managedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(0).priority());
        assertEquals(VanillaGoalKey.RANDOM_STROLL_LAND, managedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(1).key());
        assertEquals(4, managedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(1).priority());
        assertEquals(1, managedGoals.vanillaGoals(GoalSelectorType.TARGET).size());
        assertEquals(VanillaGoalKey.HURT_BY_TARGET, managedGoals.vanillaGoals(GoalSelectorType.TARGET).get(0).key());
        assertTrue(managedGoals.customGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(managedGoals.customGoals(GoalSelectorType.TARGET).isEmpty());
    }

    @Test
    void shouldExposeAttachTimeRecognizedGoalRemovalThroughTheSharedRuntimeManager() {
        VersionGoalSupportProvider provider = assertInstanceOf(
                VersionGoalSupportProvider.class,
                new SpigotVersionAdapterV1_21_11()
        );
        GoalSupportFixturesV1_21_11.StubMobHandle handle = new GoalSupportFixturesV1_21_11.StubMobHandle();
        GoalSupportFixturesV1_21_11.StubWrappedGoal normalUnknown = handle.addGoal(
                GoalSelectorType.NORMAL,
                7,
                new GoalSupportFixturesV1_21_11.UnknownGoal()
        );
        GoalSupportFixturesV1_21_11.StubWrappedGoal targetUnknown = handle.addGoal(
                GoalSelectorType.TARGET,
                9,
                new GoalSupportFixturesV1_21_11.UnknownGoal()
        );
        handle.addGoal(GoalSelectorType.NORMAL, 4, new GoalSupportFixturesV1_21_11.LookAtPlayerGoal());
        handle.addGoal(GoalSelectorType.TARGET, 2, new GoalSupportFixturesV1_21_11.HurtByTargetGoal());
        GoalSupportFixturesV1_21_11.HandleAwareZombie entity = GoalSupportFixturesV1_21_11.createEntity(handle);

        RuntimeAttachedEntityLifecycle<GoalSupportFixturesV1_21_11.HandleAwareZombie> lifecycle = new RuntimeAttachedEntityLifecycle<GoalSupportFixturesV1_21_11.HandleAwareZombie>(
                CustomEntityBaseType.ZOMBIE,
                VERSION,
                new EntityController<GoalSupportFixturesV1_21_11.HandleAwareZombie>() {
                },
                EntityTransportResolver.noop(),
                EntityPublicationBackendResolver.noop(),
                provider.createAttachedGoalMutationExecutor(CustomEntityBaseType.ZOMBIE, entity, VERSION)
        );
        lifecycle.bind(entity);
        lifecycle.bindHookBinder(GoalSupportFixturesV1_21_11.tickOnlyHookBinder());

        GoalOperationResult result = lifecycle.goalManager().removeVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER
        );

        assertTrue(handle.containsGoalType(GoalSelectorType.NORMAL, GoalSupportFixturesV1_21_11.LookAtPlayerGoal.class));
        lifecycle.onNativeHook("tick", handle, new Object[0]);

        assertEquals(1, result.removedEntries());
        assertTrue(lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertEquals(1, lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.TARGET).size());
        assertFalse(handle.containsGoalType(GoalSelectorType.NORMAL, GoalSupportFixturesV1_21_11.LookAtPlayerGoal.class));
        assertTrue(handle.containsUnknownEntry(GoalSelectorType.NORMAL, normalUnknown));
        assertTrue(handle.containsUnknownEntry(GoalSelectorType.TARGET, targetUnknown));
        assertEquals(1, handle.size(GoalSelectorType.NORMAL));
        assertEquals(2, handle.size(GoalSelectorType.TARGET));

        GoalProfile<GoalSupportFixturesV1_21_11.HandleAwareZombie> refreshedManagedGoals = provider
                .createAttachedGoalMutationExecutor(CustomEntityBaseType.ZOMBIE, entity, VERSION)
                .initialManagedGoals();
        assertTrue(refreshedManagedGoals.vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertEquals(VanillaGoalKey.HURT_BY_TARGET, refreshedManagedGoals.vanillaGoals(GoalSelectorType.TARGET).get(0).key());
    }

    @Test
    void shouldAcceptRepresentativeBroadFamiliesForAttachGoalSnapshots() {
        assertAttachSnapshotAccepted(CustomEntityBaseType.ZOMBIE);
        assertAttachSnapshotAccepted(CustomEntityBaseType.SKELETON);
        assertAttachSnapshotAccepted(CustomEntityBaseType.COW);
        assertAttachSnapshotAccepted(CustomEntityBaseType.ARMOR_STAND);
    }

    private static void assertAttachSnapshotAccepted(@NotNull CustomEntityBaseType baseType) {
        VersionGoalSupportProvider provider = assertInstanceOf(
                VersionGoalSupportProvider.class,
                new SpigotVersionAdapterV1_21_11()
        );
        GoalSupportFixturesV1_21_11.StubMobHandle handle = new GoalSupportFixturesV1_21_11.StubMobHandle();
        handle.addGoal(GoalSelectorType.NORMAL, 4, new GoalSupportFixturesV1_21_11.LookAtPlayerGoal());
        handle.addGoal(GoalSelectorType.TARGET, 2, new GoalSupportFixturesV1_21_11.HurtByTargetGoal());
        GoalSupportFixturesV1_21_11.HandleAwareZombie entity = GoalSupportFixturesV1_21_11.createEntity(handle);

        GoalProfile<GoalSupportFixturesV1_21_11.HandleAwareZombie> managedGoals = provider.createAttachedGoalMutationExecutor(
                baseType,
                entity,
                VERSION
        ).initialManagedGoals();

        assertEquals(VanillaGoalKey.LOOK_AT_PLAYER, managedGoals.vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(VanillaGoalKey.HURT_BY_TARGET, managedGoals.vanillaGoals(GoalSelectorType.TARGET).get(0).key());
    }
}

/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of this software, and to permit persons to whom the Software is
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

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.CustomEntityBaseType;
import tech.guilhermekaua.spigotboot.versions.api.EntityController;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalOperationResult;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;
import tech.guilhermekaua.spigotboot.versions.runtime.lifecycle.RuntimeAttachedEntityLifecycle;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionGoalSupportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.EntityTransportResolver;
import tech.guilhermekaua.spigotboot.versions.runtime.publication.EntityPublicationBackendResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityGoalQueueV1_21_11Test {
    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 21, 11);

    @Test
    void shouldQueueLatestFamilyMutationsUntilTheNextSafeTickBoundary() {
        VersionGoalSupportProvider provider = assertInstanceOf(
                VersionGoalSupportProvider.class,
                new SpigotVersionAdapterV1_21_11()
        );
        GoalSupportFixturesV1_21_11.StubMobHandle handle = new GoalSupportFixturesV1_21_11.StubMobHandle();
        GoalSupportFixturesV1_21_11.StubWrappedGoal normalUnknown = handle.addGoal(
                GoalSelectorType.NORMAL,
                8,
                new GoalSupportFixturesV1_21_11.UnknownGoal()
        );
        handle.addGoal(GoalSelectorType.NORMAL, 5, new GoalSupportFixturesV1_21_11.LookAtPlayerGoal());
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
        CustomGoalKey customGoalKey = CustomGoalKey.of("test", "guard-owner");

        GoalOperationResult removeVanillaResult = lifecycle.goalManager().removeVanilla(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER
        );
        GoalOperationResult addCustomResult = lifecycle.goalManager().addCustom(
                GoalSelectorType.TARGET,
                customGoalKey,
                2
        );
        GoalOperationResult removeCustomResult = lifecycle.goalManager().removeCustom(
                GoalSelectorType.TARGET,
                customGoalKey
        );

        assertEquals(1, removeVanillaResult.removedEntries());
        assertFalse(addCustomResult.replacedExistingEntry());
        assertEquals(1, removeCustomResult.removedEntries());
        assertTrue(lifecycle.goalManager().managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(lifecycle.goalManager().managedGoals().customGoals(GoalSelectorType.TARGET).isEmpty());
        assertTrue(handle.containsGoalType(GoalSelectorType.NORMAL, GoalSupportFixturesV1_21_11.LookAtPlayerGoal.class));
        assertTrue(handle.containsUnknownEntry(GoalSelectorType.NORMAL, normalUnknown));
        assertEquals(2, handle.size(GoalSelectorType.NORMAL));
        assertEquals(0, handle.size(GoalSelectorType.TARGET));

        lifecycle.onNativeHook("tick", handle, new Object[0]);

        assertFalse(handle.containsGoalType(GoalSelectorType.NORMAL, GoalSupportFixturesV1_21_11.LookAtPlayerGoal.class));
        assertTrue(handle.containsUnknownEntry(GoalSelectorType.NORMAL, normalUnknown));
        assertEquals(1, handle.size(GoalSelectorType.NORMAL));
        assertEquals(0, handle.size(GoalSelectorType.TARGET));

        GoalSupportFixturesV1_21_11.HandleAwareZombie resnapshotEntity = GoalSupportFixturesV1_21_11.createEntity(handle);
        assertTrue(provider.createAttachedGoalMutationExecutor(CustomEntityBaseType.ZOMBIE, resnapshotEntity, VERSION)
                .initialManagedGoals()
                .vanillaGoals(GoalSelectorType.NORMAL)
                .isEmpty());
        assertTrue(provider.createAttachedGoalMutationExecutor(CustomEntityBaseType.ZOMBIE, resnapshotEntity, VERSION)
                .initialManagedGoals()
                .customGoals(GoalSelectorType.TARGET)
                .isEmpty());
    }
}

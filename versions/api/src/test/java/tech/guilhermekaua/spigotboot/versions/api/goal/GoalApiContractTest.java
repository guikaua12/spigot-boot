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
package tech.guilhermekaua.spigotboot.versions.api.goal;

import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalApiContractTest {

    @Test
    void shouldExposeTheFrozenVanillaGoalCatalog() {
        assertEquals(
                Arrays.asList(
                        VanillaGoalKey.FLOAT,
                        VanillaGoalKey.MELEE_ATTACK,
                        VanillaGoalKey.RANDOM_STROLL_LAND,
                        VanillaGoalKey.LOOK_AT_PLAYER,
                        VanillaGoalKey.RANDOM_LOOK_AROUND,
                        VanillaGoalKey.HURT_BY_TARGET,
                        VanillaGoalKey.NEAREST_ATTACKABLE_TARGET
                ),
                Arrays.asList(VanillaGoalKey.values())
        );
    }

    @Test
    void shouldBuildImmutableProfilesForBothSelectorTypes() {
        CustomGoalKey customGoalKey = CustomGoalKey.of("test", "follow-owner");
        GoalProfile<Zombie> goalProfile = GoalProfile.<Zombie>builder(Zombie.class)
                .addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .addCustom(GoalSelectorType.NORMAL, customGoalKey, 4)
                .addVanilla(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET, 1)
                .build();

        List<VanillaGoalSpec> normalVanillaGoals = goalProfile.vanillaGoals(GoalSelectorType.NORMAL);
        List<CustomGoalSpec> normalCustomGoals = goalProfile.customGoals(GoalSelectorType.NORMAL);
        List<VanillaGoalSpec> targetVanillaGoals = goalProfile.vanillaGoals(GoalSelectorType.TARGET);

        assertSame(Zombie.class, goalProfile.entityType());
        assertEquals(1, normalVanillaGoals.size());
        assertEquals(1, normalCustomGoals.size());
        assertEquals(1, targetVanillaGoals.size());
        assertEquals(VanillaGoalKey.FLOAT, normalVanillaGoals.get(0).key());
        assertEquals(customGoalKey, normalCustomGoals.get(0).key());
        assertEquals(VanillaGoalKey.HURT_BY_TARGET, targetVanillaGoals.get(0).key());
        assertThrows(UnsupportedOperationException.class, () -> normalVanillaGoals.add(VanillaGoalSpec.of(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.LOOK_AT_PLAYER,
                5
        )));
    }

    @Test
    void shouldReplaceManagedEntriesByKeyInsideTheSameSelector() {
        CustomGoalKey customGoalKey = CustomGoalKey.of("test", "follow-owner");
        GoalProfile<Entity> goalProfile = GoalProfile.<Entity>builder(Entity.class)
                .addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.MELEE_ATTACK, 2)
                .addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.MELEE_ATTACK, 7)
                .addCustom(GoalSelectorType.TARGET, customGoalKey, 1)
                .addCustom(GoalSelectorType.TARGET, customGoalKey, 9)
                .build();

        assertEquals(1, goalProfile.vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(7, goalProfile.vanillaGoals(GoalSelectorType.NORMAL).get(0).priority());
        assertEquals(1, goalProfile.customGoals(GoalSelectorType.TARGET).size());
        assertEquals(9, goalProfile.customGoals(GoalSelectorType.TARGET).get(0).priority());
    }

    @Test
    void shouldRepresentSupportedButAbsentRemovalWithZeroRemovals() {
        GoalOperationResult result = GoalOperationResult.removed(
                GoalSelectorType.NORMAL,
                VanillaGoalKey.RANDOM_LOOK_AROUND,
                0
        );

        assertSame(GoalOperationResult.Operation.REMOVE, result.operation());
        assertSame(GoalSelectorType.NORMAL, result.selectorType());
        assertSame(VanillaGoalKey.RANDOM_LOOK_AROUND, result.vanillaKeyOrNull());
        assertNull(result.customKeyOrNull());
        assertEquals(0, result.removedEntries());
        assertFalse(result.replacedExistingEntry());
    }

    @Test
    void shouldExposeExplicitUnsupportedGoalExceptions() {
        UnsupportedGoalOperationException exception = new UnsupportedGoalOperationException(
                "Unsupported target goal.",
                GoalSelectorType.TARGET,
                VanillaGoalKey.NEAREST_ATTACKABLE_TARGET,
                Zombie.class,
                MinecraftVersion.of(1, 8, 8)
        );

        assertSame(GoalSelectorType.TARGET, exception.selectorType());
        assertSame(VanillaGoalKey.NEAREST_ATTACKABLE_TARGET, exception.vanillaKeyOrNull());
        assertNull(exception.customKeyOrNull());
        assertSame(Zombie.class, exception.entityTypeOrNull());
        assertEquals(MinecraftVersion.of(1, 8, 8), exception.minecraftVersionOrNull());
        assertTrue(exception.getMessage().contains("Unsupported target goal"));
    }

    @Test
    void shouldExposeUnsupportedGoalFailuresForNonAiArmorStands() {
        MinecraftVersion minecraftVersion = MinecraftVersion.of(1, 21, 11);
        GoalManager<ArmorStand> goalManager = GoalManager.unsupported(ArmorStand.class, minecraftVersion);
        CustomGoalKey customGoalKey = CustomGoalKey.of("test", "pose-watcher");

        UnsupportedGoalOperationException addVanillaException = assertThrows(
                UnsupportedGoalOperationException.class,
                () -> goalManager.addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
        );
        UnsupportedGoalOperationException addCustomException = assertThrows(
                UnsupportedGoalOperationException.class,
                () -> goalManager.addCustom(GoalSelectorType.TARGET, customGoalKey, 5)
        );
        UnsupportedGoalOperationException clearException = assertThrows(
                UnsupportedGoalOperationException.class,
                () -> goalManager.clear(GoalSelectorType.NORMAL)
        );

        assertSame(ArmorStand.class, goalManager.managedGoals().entityType());
        assertTrue(goalManager.managedGoals().vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(goalManager.managedGoals().customGoals(GoalSelectorType.TARGET).isEmpty());
        assertSame(GoalSelectorType.NORMAL, addVanillaException.selectorType());
        assertSame(VanillaGoalKey.FLOAT, addVanillaException.vanillaKeyOrNull());
        assertSame(ArmorStand.class, addVanillaException.entityTypeOrNull());
        assertEquals(minecraftVersion, addVanillaException.minecraftVersionOrNull());
        assertSame(GoalSelectorType.TARGET, addCustomException.selectorType());
        assertEquals(customGoalKey, addCustomException.customKeyOrNull());
        assertSame(ArmorStand.class, addCustomException.entityTypeOrNull());
        assertEquals(minecraftVersion, addCustomException.minecraftVersionOrNull());
        assertSame(GoalSelectorType.NORMAL, clearException.selectorType());
        assertSame(ArmorStand.class, clearException.entityTypeOrNull());
        assertEquals(minecraftVersion, clearException.minecraftVersionOrNull());
    }
}

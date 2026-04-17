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
package tech.guilhermekaua.spigotboot.versions.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class SpawnBuilderGoalProfileTest {

    @Test
    void shouldAppendSpawnGoalsWithoutMutatingTheSourceTemplate() {
        CustomGoalKey templateGoalKey = CustomGoalKey.of("test", "follow-owner");
        CustomGoalKey spawnGoalKey = CustomGoalKey.of("test", "protect-owner");
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(CustomEntityBaseType.ZOMBIE)
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .addCustomGoal(GoalSelectorType.NORMAL, templateGoalKey, 4)
                .build();

        SpawnBuilder<Zombie> spawnBuilder = SpawnBuilder.fromTemplate(template, location());
        spawnBuilder.addVanillaGoal(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET, 1);
        spawnBuilder.addCustomGoal(GoalSelectorType.TARGET, spawnGoalKey, 2);

        EntityTemplate<Zombie> effectiveTemplate = spawnBuilder.template();

        assertNotSame(template, effectiveTemplate);
        assertNotSame(template.goalProfile(), effectiveTemplate.goalProfile());
        assertEquals(1, template.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(0, template.goalProfile().vanillaGoals(GoalSelectorType.TARGET).size());
        assertEquals(0, template.goalProfile().customGoals(GoalSelectorType.TARGET).size());
        assertEquals(1, effectiveTemplate.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(1, effectiveTemplate.goalProfile().vanillaGoals(GoalSelectorType.TARGET).size());
        assertEquals(1, effectiveTemplate.goalProfile().customGoals(GoalSelectorType.NORMAL).size());
        assertEquals(1, effectiveTemplate.goalProfile().customGoals(GoalSelectorType.TARGET).size());
        assertEquals(
                VanillaGoalKey.HURT_BY_TARGET,
                effectiveTemplate.goalProfile().vanillaGoals(GoalSelectorType.TARGET).get(0).key()
        );
        assertEquals(
                spawnGoalKey,
                effectiveTemplate.goalProfile().customGoals(GoalSelectorType.TARGET).get(0).key()
        );
    }

    @Test
    void shouldMergeSpawnOverridesByStableKeyWithoutMutatingTheSourceTemplate() {
        CustomGoalKey followOwnerKey = CustomGoalKey.of("test", "follow-owner");
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(CustomEntityBaseType.ZOMBIE)
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .addCustomGoal(GoalSelectorType.TARGET, followOwnerKey, 2)
                .build();

        SpawnBuilder<Zombie> spawnBuilder = SpawnBuilder.fromTemplate(template, location());
        spawnBuilder.addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 7);
        spawnBuilder.addCustomGoal(GoalSelectorType.TARGET, followOwnerKey, 9);

        EntityTemplate<Zombie> effectiveTemplate = spawnBuilder.template();

        assertEquals(1, template.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(0, template.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).get(0).priority());
        assertEquals(1, template.goalProfile().customGoals(GoalSelectorType.TARGET).size());
        assertEquals(2, template.goalProfile().customGoals(GoalSelectorType.TARGET).get(0).priority());
        assertEquals(1, effectiveTemplate.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(7, effectiveTemplate.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).get(0).priority());
        assertEquals(1, effectiveTemplate.goalProfile().customGoals(GoalSelectorType.TARGET).size());
        assertEquals(9, effectiveTemplate.goalProfile().customGoals(GoalSelectorType.TARGET).get(0).priority());
    }

    @Test
    void shouldAllowReplacingTheSpawnGoalProfileAndPreserveSpawnOptionsBehavior() {
        Location location = location();
        GoalProfile<Zombie> replacementProfile = GoalProfile.<Zombie>builder(Zombie.class)
                .addVanilla(GoalSelectorType.TARGET, VanillaGoalKey.NEAREST_ATTACKABLE_TARGET, 5)
                .build();
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(CustomEntityBaseType.ZOMBIE)
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .build();

        SpawnBuilder<Zombie> spawnBuilder = SpawnBuilder.fromTemplate(template, location);
        spawnBuilder.goalProfile(replacementProfile);
        spawnBuilder.data("reason", "inline-customizer");

        EntityTemplate<Zombie> effectiveTemplate = spawnBuilder.template();
        SpawnOptions spawnOptions = spawnBuilder.spawnOptions();

        assertSame(template.id(), effectiveTemplate.id());
        assertSame(template.baseType(), effectiveTemplate.baseType());
        assertSame(template.bukkitType(), effectiveTemplate.bukkitType());
        assertSame(template.controllerFactory(), effectiveTemplate.controllerFactory());
        assertSame(template.networkControllerFactory(), effectiveTemplate.networkControllerFactory());
        assertSame(template.initializer(), effectiveTemplate.initializer());
        assertSame(replacementProfile, effectiveTemplate.goalProfile());
        assertEquals(0, effectiveTemplate.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(1, effectiveTemplate.goalProfile().vanillaGoals(GoalSelectorType.TARGET).size());
        assertEquals("inline-customizer", spawnOptions.data().get("reason"));
        assertNotSame(location, spawnOptions.location());
        assertSame(location.getWorld(), spawnOptions.location().getWorld());
        assertEquals(location.getX(), spawnOptions.location().getX());
        assertEquals(location.getY(), spawnOptions.location().getY());
        assertEquals(location.getZ(), spawnOptions.location().getZ());
    }

    private static Location location() {
        return new Location(Mockito.mock(World.class), 12.5D, 64.0D, -3.0D);
    }
}

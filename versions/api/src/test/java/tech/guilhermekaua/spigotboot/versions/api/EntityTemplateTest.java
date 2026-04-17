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

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityTemplateTest {

    @Test
    void shouldBuildLogicalTemplatesWithoutMandatoryRegistrationOrControllerFactory() {
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(CustomEntityBaseType.ZOMBIE).build();

        assertNull(template.id());
        assertSame(CustomEntityBaseType.ZOMBIE, template.baseType());
        assertSame(Zombie.class, template.bukkitType());
        assertNotNull(template.goalProfile());
        assertSame(Zombie.class, template.goalProfile().entityType());
        assertEquals(0, template.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(0, template.goalProfile().vanillaGoals(GoalSelectorType.TARGET).size());
        assertEquals(0, template.goalProfile().customGoals(GoalSelectorType.NORMAL).size());
        assertEquals(0, template.goalProfile().customGoals(GoalSelectorType.TARGET).size());
    }

    @Test
    void shouldBuildRegisteredTemplatesFromBukkitEntityType() {
        CustomEntityId templateId = CustomEntityId.of("test", "entity-type");
        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(
                        templateId,
                        EntityType.ZOMBIE
                )
                .build();

        assertSame(templateId, template.id());
        assertSame(CustomEntityBaseType.ZOMBIE, template.baseType());
        assertSame(Zombie.class, template.bukkitType());
    }

    @Test
    void shouldCarryTemplateTimeGoalConfigurationIntoBuiltTemplates() {
        CustomGoalKey customGoalKey = CustomGoalKey.of("test", "follow-owner");

        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(CustomEntityBaseType.ZOMBIE)
                .addVanillaGoal(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
                .addCustomGoal(GoalSelectorType.TARGET, customGoalKey, 2)
                .build();

        assertEquals(1, template.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).size());
        assertEquals(VanillaGoalKey.FLOAT, template.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).get(0).key());
        assertEquals(1, template.goalProfile().customGoals(GoalSelectorType.TARGET).size());
        assertEquals(customGoalKey, template.goalProfile().customGoals(GoalSelectorType.TARGET).get(0).key());
        assertThrows(
                UnsupportedOperationException.class,
                () -> template.goalProfile().vanillaGoals(GoalSelectorType.NORMAL).add(null)
        );
    }

    @Test
    void shouldAllowReplacingTheFullTemplateGoalProfile() {
        GoalProfile<Zombie> goalProfile = GoalProfile.<Zombie>builder(Zombie.class)
                .addVanilla(GoalSelectorType.TARGET, VanillaGoalKey.HURT_BY_TARGET, 3)
                .build();

        EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(CustomEntityBaseType.ZOMBIE)
                .goalProfile(goalProfile)
                .build();

        assertSame(goalProfile, template.goalProfile());
        assertEquals(1, template.goalProfile().vanillaGoals(GoalSelectorType.TARGET).size());
        assertEquals(
                VanillaGoalKey.HURT_BY_TARGET,
                template.goalProfile().vanillaGoals(GoalSelectorType.TARGET).get(0).key()
        );
    }
}

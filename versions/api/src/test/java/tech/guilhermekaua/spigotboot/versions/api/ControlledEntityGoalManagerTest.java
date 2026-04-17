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

import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.guilhermekaua.spigotboot.versions.api.goal.CustomGoalKey;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalManager;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalProfile;
import tech.guilhermekaua.spigotboot.versions.api.goal.GoalSelectorType;
import tech.guilhermekaua.spigotboot.versions.api.goal.UnsupportedGoalOperationException;
import tech.guilhermekaua.spigotboot.versions.api.goal.VanillaGoalKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledEntityGoalManagerTest {

    @Test
    void shouldExposeNonNullDefaultGoalManager() {
        ControlledEntity<Zombie> controlledEntity = new TestControlledEntity();

        GoalManager<Zombie> goalManager = controlledEntity.goalManager();

        assertNotNull(goalManager);
    }

    @Test
    void shouldFailFastForUnsupportedDefaultMutations() {
        ControlledEntity<Zombie> controlledEntity = new TestControlledEntity();

        UnsupportedGoalOperationException addVanillaException = assertThrows(
                UnsupportedGoalOperationException.class,
                () -> controlledEntity.goalManager().addVanilla(GoalSelectorType.NORMAL, VanillaGoalKey.FLOAT, 0)
        );
        UnsupportedGoalOperationException removeCustomException = assertThrows(
                UnsupportedGoalOperationException.class,
                () -> controlledEntity.goalManager().removeCustom(
                        GoalSelectorType.TARGET,
                        CustomGoalKey.of("test", "follow-owner")
                )
        );
        UnsupportedGoalOperationException clearException = assertThrows(
                UnsupportedGoalOperationException.class,
                () -> controlledEntity.goalManager().clear(GoalSelectorType.NORMAL)
        );

        assertSame(GoalSelectorType.NORMAL, addVanillaException.selectorType());
        assertSame(VanillaGoalKey.FLOAT, addVanillaException.vanillaKeyOrNull());
        assertEquals(MinecraftVersion.of(1, 21, 11), addVanillaException.minecraftVersionOrNull());
        assertTrue(Zombie.class.isAssignableFrom(addVanillaException.entityTypeOrNull()));
        assertSame(GoalSelectorType.TARGET, removeCustomException.selectorType());
        assertEquals(CustomGoalKey.of("test", "follow-owner"), removeCustomException.customKeyOrNull());
        assertSame(GoalSelectorType.NORMAL, clearException.selectorType());
        assertNull(clearException.vanillaKeyOrNull());
        assertNull(clearException.customKeyOrNull());
    }

    @Test
    void shouldInspectOnlyEmptyManagedRecognizedSetByDefault() {
        ControlledEntity<Zombie> controlledEntity = new TestControlledEntity();

        GoalProfile<Zombie> managedGoals = controlledEntity.goalManager().managedGoals();

        assertTrue(managedGoals.vanillaGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(managedGoals.vanillaGoals(GoalSelectorType.TARGET).isEmpty());
        assertTrue(managedGoals.customGoals(GoalSelectorType.NORMAL).isEmpty());
        assertTrue(managedGoals.customGoals(GoalSelectorType.TARGET).isEmpty());
    }

    private static final class TestControlledEntity implements ControlledEntity<Zombie> {
        private final Zombie bukkitEntity = Mockito.mock(Zombie.class);
        private final CustomEntityState state = Mockito.mock(CustomEntityState.class);

        @Override
        public Zombie bukkitEntity() {
            return bukkitEntity;
        }

        @Override
        public CustomEntityState state() {
            return state;
        }

        @Override
        public void remove() {
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public CustomEntityBaseType baseType() {
            return CustomEntityBaseType.ZOMBIE;
        }

        @Override
        public MinecraftVersion minecraftVersion() {
            return MinecraftVersion.of(1, 21, 11);
        }

        @Override
        public EntityController<Zombie> controller() {
            return new EntityController<Zombie>() {
            };
        }

        @Override
        public void setController(EntityController<Zombie> controller) {
        }

        @Override
        public void clearController() {
        }

        @Override
        public boolean isHooked() {
            return true;
        }
    }
}

/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
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
package tech.guilhermekaua.spigotboot.entity.v1_21_11.zombie;

import org.bukkit.entity.Zombie;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

/**
 * Caches reflected NMS references for a Minecraft 1.21.11 zombie entity running on Paper.
 *
 * <p>Paper 1.21.11 uses Mojang mappings, so all NMS class and field names are human-readable.
 * The {@code moveTo} operation uses Paper's stable {@code Pathfinder} API instead of NMS
 * reflection. Goal selector access still requires reflection because the Bukkit API does
 * not expose {@code GoalSelector} directly.
 *
 * @since 2.0.2
 */
final class NmsHandleV1_21_11 {

    // Mojang-mapped NMS package for 1.21.x on Paper
    private static final String MOB_CLASS = "net.minecraft.world.entity.Mob";
    private static final String GOAL_SELECTOR_CLASS = "net.minecraft.world.entity.ai.goal.GoalSelector";
    private static final String WRAPPED_GOAL_CLASS = "net.minecraft.world.entity.ai.goal.WrappedGoal";

    private final Object entityHandle;         // net.minecraft.world.entity.monster.Zombie
    private final Object goalSelector;         // net.minecraft.world.entity.ai.goal.GoalSelector
    private final Set<Object> availableGoals;  // GoalSelector.availableGoals (LinkedHashSet<WrappedGoal>)
    private final Method addGoalMethod;        // GoalSelector.addGoal(int, Goal)
    private final Method getGoalMethod;        // WrappedGoal.getGoal()

    NmsHandleV1_21_11(Zombie bukkit) {
        try {
            // 1. Resolve the NMS entity handle via CraftEntity.getHandle()
            Method getHandle = bukkit.getClass().getMethod("getHandle");
            this.entityHandle = getHandle.invoke(bukkit);

            // 2. Load GoalSelector class (Mojang-mapped, Paper exposes this at runtime)
            Class<?> goalSelectorClass = Class.forName(GOAL_SELECTOR_CLASS);

            // 3. Find goalSelector field on Mob by type (Mojang name: "goalSelector")
            Class<?> mobClass = Class.forName(MOB_CLASS);
            Field goalSelectorField = findFieldByTypeInHierarchy(entityHandle.getClass(),
                    mobClass, goalSelectorClass);
            goalSelectorField.setAccessible(true);
            this.goalSelector = goalSelectorField.get(entityHandle);

            // 4. Find the availableGoals Set field in GoalSelector
            Field availableGoalsField = findSetField(goalSelectorClass);
            availableGoalsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<Object> goals = (Set<Object>) availableGoalsField.get(this.goalSelector);
            this.availableGoals = goals;

            // 5. Find GoalSelector.addGoal(int, Goal)
            Class<?> goalBaseClass = Class.forName("net.minecraft.world.entity.ai.goal.Goal");
            this.addGoalMethod = findAddGoalMethod(goalSelectorClass, goalBaseClass);

            // 6. Find WrappedGoal.getGoal()
            Class<?> wrappedGoalClass = Class.forName(WRAPPED_GOAL_CLASS);
            this.getGoalMethod = wrappedGoalClass.getMethod("getGoal");

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Failed to initialise NMS handle for 1.21.11 zombie. "
                    + "Ensure the server is running Paper 1.21.x. Cause: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the raw NMS entity handle.
     *
     * @return the NMS Zombie instance
     */
    Object entityHandle() {
        return entityHandle;
    }

    /**
     * Adds an NMS goal to the zombie's goal selector.
     *
     * @param priority the goal priority (lower = higher priority)
     * @param nmsGoal  the NMS {@code Goal} instance
     */
    void addGoal(int priority, Object nmsGoal) {
        try {
            addGoalMethod.invoke(goalSelector, priority, nmsGoal);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to add goal to 1.21.11 zombie", e);
        }
    }

    /**
     * Removes all available goals from the zombie's goal selector.
     */
    void clearGoals() {
        availableGoals.clear();
    }

    /**
     * Removes all goals of the given NMS class from the zombie's goal selector.
     *
     * @param nmsGoalClass the NMS {@code Goal} subclass to remove
     */
    void removeGoalsByClass(Class<?> nmsGoalClass) {
        Iterator<Object> it = availableGoals.iterator();
        while (it.hasNext()) {
            Object wrapped = it.next();
            try {
                Object innerGoal = getGoalMethod.invoke(wrapped);
                if (nmsGoalClass.isInstance(innerGoal)) {
                    it.remove();
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to inspect wrapped goal during removal", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Walks the class hierarchy starting from {@code startClass} up to (and including)
     * {@code stopClass} to find the first field whose type matches {@code fieldType}.
     */
    private static Field findFieldByTypeInHierarchy(
            Class<?> startClass, Class<?> stopClass, Class<?> fieldType) {
        Class<?> current = startClass;
        while (current != null) {
            for (Field f : current.getDeclaredFields()) {
                if (fieldType.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            }
            if (current.equals(stopClass)) {
                break;
            }
            current = current.getSuperclass();
        }
        throw new RuntimeException(
                "No field of type " + fieldType.getName()
                + " found between " + startClass.getName()
                + " and " + stopClass.getName());
    }

    /**
     * Finds the first {@code Set}-typed field in {@code goalSelectorClass}.
     */
    private static Field findSetField(Class<?> goalSelectorClass) {
        for (Field f : goalSelectorClass.getDeclaredFields()) {
            if (Set.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new RuntimeException(
                "Set field (availableGoals) not found in " + goalSelectorClass.getName());
    }

    /**
     * Finds the {@code addGoal(int, Goal)} method on {@code GoalSelector}.
     */
    private static Method findAddGoalMethod(Class<?> selectorClass, Class<?> goalBaseClass) {
        for (Method m : selectorClass.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2
                    && params[0] == int.class
                    && goalBaseClass.isAssignableFrom(params[1])) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new RuntimeException(
                "addGoal(int, Goal) method not found on " + selectorClass.getName());
    }
}

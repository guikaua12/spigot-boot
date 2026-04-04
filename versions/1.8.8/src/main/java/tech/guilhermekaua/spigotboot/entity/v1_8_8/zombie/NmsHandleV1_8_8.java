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
package tech.guilhermekaua.spigotboot.entity.v1_8_8.zombie;

import org.bukkit.entity.Zombie;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

/**
 * Caches reflected NMS references for a Minecraft 1.8.8 zombie entity.
 *
 * <p>All reflected fields and methods are resolved once in the constructor and cached.
 * NMS symbol names are in {@code net.minecraft.server.v1_8_R3.*}. No imports from
 * that package appear here; the dependency is entirely runtime-based.
 *
 * @since 2.0.2
 */
final class NmsHandleV1_8_8 {

    // NMS package prefix for 1.8.8
    private static final String NMS_PKG = "net.minecraft.server.v1_8_R3.";

    private final Object entityHandle;
    private final Object goalSelector;
    private final Set<Object> registeredGoals;   // PathfinderGoalSelector internal goal set (b)
    private final Set<Object> runningGoals;      // PathfinderGoalSelector internal running set (c)
    private final Method addGoalMethod;          // PathfinderGoalSelector.a(int, PathfinderGoal)
    private final Field goalFieldInItem;         // PathfinderGoalSelectorItem's PathfinderGoal field
    private final Class<?> pathfinderGoalClass;  // net.minecraft.server.v1_8_R3.PathfinderGoal
    private final Method navigationGetter;       // EntityInsentient.getNavigation()
    private final Method navigateMoveToMethod;   // NavigationAbstract.a(double,double,double,double)

    NmsHandleV1_8_8(Zombie bukkit) {
        try {
            // 1. Resolve the NMS entity handle via CraftEntity.getHandle()
            Method getHandle = bukkit.getClass().getMethod("getHandle");
            this.entityHandle = getHandle.invoke(bukkit);

            // 2. Load key NMS classes
            Class<?> goalSelectorClass = Class.forName(NMS_PKG + "PathfinderGoalSelector");
            this.pathfinderGoalClass = Class.forName(NMS_PKG + "PathfinderGoal");

            // 3. Find the goalSelector field on EntityInsentient by type
            Field goalSelectorField = findFirstFieldByType(entityHandle.getClass(), goalSelectorClass);
            goalSelectorField.setAccessible(true);
            this.goalSelector = goalSelectorField.get(entityHandle);

            // 4. Find the two internal Set fields inside PathfinderGoalSelector
            //    Field "b" = registered goals, field "c" = currently running goals
            Set<Object>[] sets = findSetFields(goalSelectorClass, this.goalSelector);
            this.registeredGoals = sets[0];
            this.runningGoals = sets[1];

            // 5. Find addGoal method: a(int priority, PathfinderGoal goal)
            this.addGoalMethod = findAddGoalMethod(goalSelectorClass, pathfinderGoalClass);

            // 6. Determine the field in PathfinderGoalSelectorItem that holds the PathfinderGoal
            //    PGSSelectorItem has an int priority field and a PathfinderGoal field.
            Object sampleItem = registeredGoals.isEmpty() ? null : registeredGoals.iterator().next();
            if (sampleItem != null) {
                this.goalFieldInItem = findGoalFieldInItem(sampleItem.getClass(), pathfinderGoalClass);
            } else {
                // Resolve class by inner class naming convention and find the goal field
                Class<?> itemClass = findInnerClass(goalSelectorClass, "PathfinderGoalSelectorItem");
                this.goalFieldInItem = findGoalFieldInItem(itemClass, pathfinderGoalClass);
            }

            // 7. Find getNavigation() on the entity by looking for a no-arg method returning a Navigation type
            this.navigationGetter = findNavigationGetter(entityHandle.getClass());

            // 8. Invoke getNavigation() once to get the class, then find the moveTo method
            Object navigation = navigationGetter.invoke(entityHandle);
            this.navigateMoveToMethod = findNavigateMoveToMethod(navigation.getClass());

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Failed to initialise NMS handle for 1.8.8 zombie. "
                    + "Ensure the server is running Minecraft 1.8.8. Cause: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the raw NMS entity handle.
     *
     * @return the NMS EntityZombie instance
     */
    Object entityHandle() {
        return entityHandle;
    }

    /**
     * Adds an NMS goal to the zombie's goal selector.
     *
     * @param priority the goal priority (lower = higher priority)
     * @param nmsGoal  the NMS PathfinderGoal instance
     */
    void addGoal(int priority, Object nmsGoal) {
        try {
            addGoalMethod.invoke(goalSelector, priority, nmsGoal);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to add goal to 1.8.8 zombie", e);
        }
    }

    /**
     * Removes all registered and running goals from the zombie's goal selector.
     */
    void clearGoals() {
        runningGoals.clear();
        registeredGoals.clear();
    }

    /**
     * Removes all goals of the given NMS class from the zombie's goal selector.
     *
     * @param nmsGoalClass the NMS PathfinderGoal subclass to remove
     */
    void removeGoalsByClass(Class<?> nmsGoalClass) {
        removeByClass(runningGoals, nmsGoalClass);
        removeByClass(registeredGoals, nmsGoalClass);
    }

    /**
     * Navigates the zombie to the given coordinates.
     *
     * @param x     target x coordinate
     * @param y     target y coordinate
     * @param z     target z coordinate
     * @param speed pathfinding speed multiplier
     */
    void navigateTo(double x, double y, double z, double speed) {
        try {
            Object navigation = navigationGetter.invoke(entityHandle);
            navigateMoveToMethod.invoke(navigation, x, y, z, speed);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to navigate 1.8.8 zombie", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void removeByClass(Set<Object> goalSet, Class<?> nmsGoalClass) {
        Iterator<Object> it = goalSet.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            try {
                Object innerGoal = goalFieldInItem.get(item);
                if (nmsGoalClass.isInstance(innerGoal)) {
                    it.remove();
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to inspect goal item during removal", e);
            }
        }
    }

    /**
     * Walks the class hierarchy to find the first declared field whose type is assignable
     * from {@code fieldType}.
     */
    private static Field findFirstFieldByType(Class<?> clazz, Class<?> fieldType) {
        Class<?> current = clazz;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (fieldType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        throw new RuntimeException(
                "No field of type " + fieldType.getName() + " found in " + clazz.getName());
    }

    /**
     * Finds the two {@code Set} fields in {@code PathfinderGoalSelector} and returns their
     * current values cast to {@code Set<Object>}. The first Set is the registered-goals set;
     * the second is the running-goals set.
     */
    @SuppressWarnings("unchecked")
    private static Set<Object>[] findSetFields(Class<?> goalSelectorClass, Object goalSelector)
            throws ReflectiveOperationException {
        Set<Object> first = null;
        Set<Object> second = null;
        for (Field field : goalSelectorClass.getDeclaredFields()) {
            if (Set.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                if (first == null) {
                    first = (Set<Object>) field.get(goalSelector);
                } else if (second == null) {
                    second = (Set<Object>) field.get(goalSelector);
                    break;
                }
            }
        }
        if (first == null || second == null) {
            throw new RuntimeException(
                    "Expected two Set fields in PathfinderGoalSelector but found fewer");
        }
        return new Set[]{first, second};
    }

    /**
     * Finds the {@code a(int, PathfinderGoal)} method on {@code PathfinderGoalSelector}.
     */
    private static Method findAddGoalMethod(Class<?> selectorClass, Class<?> pathfinderGoalClass) {
        for (Method m : selectorClass.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2
                    && params[0] == int.class
                    && pathfinderGoalClass.isAssignableFrom(params[1])) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new RuntimeException(
                "addGoal method not found on PathfinderGoalSelector");
    }

    /**
     * Finds the field in {@code PathfinderGoalSelectorItem} that holds the {@code PathfinderGoal}.
     */
    private static Field findGoalFieldInItem(Class<?> itemClass, Class<?> pathfinderGoalClass) {
        for (Field f : itemClass.getDeclaredFields()) {
            if (pathfinderGoalClass.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new RuntimeException(
                "PathfinderGoal field not found in " + itemClass.getName());
    }

    /**
     * Attempts to find a nested class by simple name within {@code outer}.
     */
    private static Class<?> findInnerClass(Class<?> outer, String simpleName) {
        for (Class<?> inner : outer.getDeclaredClasses()) {
            if (inner.getSimpleName().equals(simpleName)) {
                return inner;
            }
        }
        // Fallback: try loading by canonical name pattern
        try {
            return Class.forName(outer.getName() + "$" + simpleName);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(
                    "Inner class " + simpleName + " not found in " + outer.getName(), ex);
        }
    }

    /**
     * Walks the entity hierarchy to find a no-arg method that returns a type whose
     * class name contains "Navigation".
     */
    private static Method findNavigationGetter(Class<?> entityClass) {
        Class<?> current = entityClass;
        while (current != null) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().contains("Navigation")) {
                    m.setAccessible(true);
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        throw new RuntimeException(
                "getNavigation() method not found on " + entityClass.getName());
    }

    /**
     * Walks the navigation class hierarchy to find a method that accepts
     * {@code (double, double, double, double)}.
     */
    private static Method findNavigateMoveToMethod(Class<?> navigationClass) {
        Class<?> current = navigationClass;
        while (current != null) {
            for (Method m : current.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 4
                        && params[0] == double.class
                        && params[1] == double.class
                        && params[2] == double.class
                        && params[3] == double.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        throw new RuntimeException(
                "moveTo(double,double,double,double) method not found on "
                + navigationClass.getName());
    }
}

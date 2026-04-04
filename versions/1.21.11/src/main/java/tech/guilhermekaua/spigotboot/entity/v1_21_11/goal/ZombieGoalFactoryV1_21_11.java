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
package tech.guilhermekaua.spigotboot.entity.v1_21_11.goal;

import org.bukkit.World;
import tech.guilhermekaua.spigotboot.entity.api.exception.UnsupportedGoalException;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKey;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKeys;
import tech.guilhermekaua.spigotboot.entity.api.goal.GoalDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Translates logical {@link GoalDefinition} instances into native NMS {@code Goal} objects
 * for Minecraft 1.21.11 on Paper.
 *
 * <p>Uses Mojang-mapped class names accessible through Paper's runtime classpath.
 * All NMS references go through {@link Class#forName} at the point of use; no
 * {@code net.minecraft.*} imports appear at compile time.
 *
 * @since 2.0.2
 */
public final class ZombieGoalFactoryV1_21_11 {

    private static final String GOAL_PKG = "net.minecraft.world.entity.ai.goal.";
    private static final String ENTITY_PKG = "net.minecraft.world.entity.";

    /**
     * Creates a new factory.
     */
    public ZombieGoalFactoryV1_21_11() {
    }

    /**
     * Returns the fully-qualified NMS class name for the given goal key.
     *
     * @param key the logical goal key
     * @return the NMS class name
     * @throws UnsupportedGoalException when the goal key is not available in 1.21.11
     */
    public String goalClassName(EntityGoalKey key) {
        if (EntityGoalKeys.FLOAT.equals(key)) return GOAL_PKG + "FloatGoal";
        if (EntityGoalKeys.LOOK_AT_PLAYER.equals(key)) return GOAL_PKG + "LookAtPlayerGoal";
        if (EntityGoalKeys.MELEE_ATTACK.equals(key)) return GOAL_PKG + "MeleeAttackGoal";
        if (EntityGoalKeys.RANDOM_LOOK_AROUND.equals(key)) return GOAL_PKG + "RandomLookAroundGoal";
        if (EntityGoalKeys.BREAK_DOOR.equals(key)) return GOAL_PKG + "BreakDoorGoal";
        if (EntityGoalKeys.CLIMB_ON_TOP_OF_POWDER_SNOW.equals(key)) {
            return GOAL_PKG + "ClimbOnTopOfPowderSnowGoal";
        }
        throw new UnsupportedGoalException(
                "Goal '" + key + "' is not supported for zombie on Minecraft 1.21.11. "
                + "Supported goals: float, look_at_player, melee_attack, random_look_around, "
                + "break_door, climb_on_top_of_powder_snow.");
    }

    /**
     * Creates a native NMS goal for the supplied definition.
     *
     * @param nmsEntity  the NMS Zombie instance
     * @param definition the logical goal definition
     * @param world      the Bukkit world used by the entity (needed for some goals)
     * @return the instantiated NMS {@code Goal}
     * @throws UnsupportedGoalException when the goal key is not supported in 1.21.11
     * @throws RuntimeException         when NMS reflection fails
     */
    public Object createGoal(Object nmsEntity, GoalDefinition definition, World world) {
        EntityGoalKey key = definition.key();
        Map<String, Object> p = definition.parameters();

        try {
            if (EntityGoalKeys.FLOAT.equals(key)) {
                return createFloat(nmsEntity);
            }
            if (EntityGoalKeys.LOOK_AT_PLAYER.equals(key)) {
                return createLookAtPlayer(nmsEntity, p);
            }
            if (EntityGoalKeys.MELEE_ATTACK.equals(key)) {
                return createMeleeAttack(nmsEntity, p);
            }
            if (EntityGoalKeys.RANDOM_LOOK_AROUND.equals(key)) {
                return createRandomLookAround(nmsEntity);
            }
            if (EntityGoalKeys.BREAK_DOOR.equals(key)) {
                return createBreakDoor(nmsEntity);
            }
            if (EntityGoalKeys.CLIMB_ON_TOP_OF_POWDER_SNOW.equals(key)) {
                return createClimbOnTopOfPowderSnow(nmsEntity, world);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Failed to create NMS goal '" + key + "' for 1.21.11 zombie. "
                    + "Cause: " + e.getMessage(), e);
        }

        throw new UnsupportedGoalException(
                "Goal '" + key + "' is not supported for zombie on Minecraft 1.21.11. "
                + "Supported goals: float, look_at_player, melee_attack, random_look_around, "
                + "break_door, climb_on_top_of_powder_snow.");
    }

    // -------------------------------------------------------------------------
    // Private factory methods
    // -------------------------------------------------------------------------

    /**
     * FloatGoal(Mob mob)
     */
    private Object createFloat(Object nmsEntity) throws ReflectiveOperationException {
        Class<?> goalClass = Class.forName(GOAL_PKG + "FloatGoal");
        Class<?> mobClass = Class.forName(ENTITY_PKG + "Mob");
        Constructor<?> ctor = goalClass.getConstructor(mobClass);
        return ctor.newInstance(nmsEntity);
    }

    /**
     * LookAtPlayerGoal(Mob mob, Class&lt;?&gt; lookAtType, float range)
     * <p>Supported parameters:
     * <ul>
     *   <li>{@code "range"} (float, default 8.0) — maximum look distance</li>
     * </ul>
     */
    private Object createLookAtPlayer(Object nmsEntity, Map<String, Object> p)
            throws ReflectiveOperationException {
        float range = getFloat(p, "range", 8.0f);
        Class<?> goalClass = Class.forName(GOAL_PKG + "LookAtPlayerGoal");
        Class<?> mobClass = Class.forName(ENTITY_PKG + "Mob");
        Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
        Constructor<?> ctor = goalClass.getConstructor(mobClass, Class.class, float.class);
        return ctor.newInstance(nmsEntity, playerClass, range);
    }

    /**
     * MeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen)
     * <p>Supported parameters:
     * <ul>
     *   <li>{@code "speed"} (double, default 1.0) — movement speed modifier</li>
     *   <li>{@code "followIfNotSeen"} (boolean, default false) — follow target even when not seen</li>
     * </ul>
     */
    private Object createMeleeAttack(Object nmsEntity, Map<String, Object> p)
            throws ReflectiveOperationException {
        double speed = getDouble(p, "speed", 1.0);
        boolean followIfNotSeen = getBoolean(p, "followIfNotSeen", false);
        Class<?> goalClass = Class.forName(GOAL_PKG + "MeleeAttackGoal");
        Class<?> pathfinderMobClass = Class.forName(ENTITY_PKG + "PathfinderMob");
        Constructor<?> ctor = goalClass.getConstructor(
                pathfinderMobClass, double.class, boolean.class);
        return ctor.newInstance(nmsEntity, speed, followIfNotSeen);
    }

    /**
     * RandomLookAroundGoal(Mob mob)
     */
    private Object createRandomLookAround(Object nmsEntity) throws ReflectiveOperationException {
        Class<?> goalClass = Class.forName(GOAL_PKG + "RandomLookAroundGoal");
        Class<?> mobClass = Class.forName(ENTITY_PKG + "Mob");
        Constructor<?> ctor = goalClass.getConstructor(mobClass);
        return ctor.newInstance(nmsEntity);
    }

    /**
     * BreakDoorGoal(Mob mob, Predicate&lt;Difficulty&gt; validDifficulties)
     *
     * <p>The predicate is always-true; all difficulty levels allow door breaking.
     */
    private Object createBreakDoor(Object nmsEntity) throws ReflectiveOperationException {
        Class<?> goalClass = Class.forName(GOAL_PKG + "BreakDoorGoal");
        Class<?> mobClass = Class.forName(ENTITY_PKG + "Mob");

        // Find the constructor that takes (Mob, Predicate)
        Constructor<?> target = null;
        for (Constructor<?> ctor : goalClass.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2 && mobClass.isAssignableFrom(params[0])) {
                target = ctor;
                break;
            }
        }
        if (target == null) {
            throw new RuntimeException("BreakDoorGoal constructor(Mob, Predicate) not found");
        }

        // Create an always-true Predicate via java.lang.reflect.Proxy on java.util.function.Predicate
        java.util.function.Predicate<Object> alwaysTrue = new java.util.function.Predicate<Object>() {
            @Override
            public boolean test(Object o) {
                return true;
            }
        };
        return target.newInstance(nmsEntity, alwaysTrue);
    }

    /**
     * ClimbOnTopOfPowderSnowGoal(Mob mob, Level level)
     *
     * <p>The NMS {@code Level} is obtained from the Bukkit world via {@code CraftWorld.getHandle()}.
     */
    private Object createClimbOnTopOfPowderSnow(Object nmsEntity, World world)
            throws ReflectiveOperationException {
        Class<?> goalClass = Class.forName(GOAL_PKG + "ClimbOnTopOfPowderSnowGoal");
        Class<?> mobClass = Class.forName(ENTITY_PKG + "Mob");
        Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
        Constructor<?> ctor = goalClass.getConstructor(mobClass, levelClass);

        // Obtain the NMS Level from CraftWorld.getHandle()
        Method getHandle = world.getClass().getMethod("getHandle");
        Object nmsLevel = getHandle.invoke(world);

        return ctor.newInstance(nmsEntity, nmsLevel);
    }

    // -------------------------------------------------------------------------
    // Parameter helpers
    // -------------------------------------------------------------------------

    private static float getFloat(Map<String, Object> params, String key, float defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }

    private static double getDouble(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}

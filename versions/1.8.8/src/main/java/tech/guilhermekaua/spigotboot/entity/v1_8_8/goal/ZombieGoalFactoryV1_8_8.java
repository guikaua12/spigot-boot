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
package tech.guilhermekaua.spigotboot.entity.v1_8_8.goal;

import tech.guilhermekaua.spigotboot.entity.api.exception.UnsupportedGoalException;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKey;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKeys;
import tech.guilhermekaua.spigotboot.entity.api.goal.GoalDefinition;

import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * Translates logical {@link GoalDefinition} instances into native NMS
 * {@code PathfinderGoal} objects for Minecraft 1.8.8.
 *
 * <p>All NMS class references go through {@link Class#forName} so no
 * {@code net.minecraft.server.v1_8_R3.*} imports appear at compile time.
 *
 * @since 2.0.2
 */
public final class ZombieGoalFactoryV1_8_8 {

    private static final String NMS_PKG = "net.minecraft.server.v1_8_R3.";

    /**
     * Creates a new factory.
     */
    public ZombieGoalFactoryV1_8_8() {
    }

    /**
     * Returns the fully-qualified NMS class name for the given goal key.
     *
     * <p>Used by the entity handle to remove goals by class identity.
     *
     * @param key the logical goal key
     * @return the NMS class name
     * @throws UnsupportedGoalException when the goal is not available in 1.8.8
     */
    public String goalClassName(EntityGoalKey key) {
        if (EntityGoalKeys.FLOAT.equals(key)) {
            return NMS_PKG + "PathfinderGoalFloat";
        }
        if (EntityGoalKeys.LOOK_AT_PLAYER.equals(key)) {
            return NMS_PKG + "PathfinderGoalLookAtPlayer";
        }
        if (EntityGoalKeys.MELEE_ATTACK.equals(key)) {
            return NMS_PKG + "PathfinderGoalMeleeAttack";
        }
        if (EntityGoalKeys.RANDOM_LOOK_AROUND.equals(key)) {
            return NMS_PKG + "PathfinderGoalRandomLookAround";
        }
        if (EntityGoalKeys.MOVE_THROUGH_VILLAGE.equals(key)) {
            return NMS_PKG + "PathfinderGoalMoveThroughVillage";
        }
        throw new UnsupportedGoalException(
                "Goal '" + key + "' is not supported for zombie on Minecraft 1.8.8. "
                + "Supported goals: float, look_at_player, melee_attack, "
                + "random_look_around, move_through_village.");
    }

    /**
     * Creates a native NMS goal for the supplied definition.
     *
     * @param nmsEntity  the NMS EntityZombie instance
     * @param definition the logical goal definition
     * @return the instantiated NMS {@code PathfinderGoal}
     * @throws UnsupportedGoalException when the goal key is not supported in 1.8.8
     * @throws RuntimeException         when NMS reflection fails
     */
    public Object createGoal(Object nmsEntity, GoalDefinition definition) {
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
            if (EntityGoalKeys.MOVE_THROUGH_VILLAGE.equals(key)) {
                return createMoveThroughVillage(nmsEntity, p);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Failed to create NMS goal '" + key + "' for 1.8.8 zombie. "
                    + "Cause: " + e.getMessage(), e);
        }

        throw new UnsupportedGoalException(
                "Goal '" + key + "' is not supported for zombie on Minecraft 1.8.8. "
                + "Supported goals: float, look_at_player, melee_attack, "
                + "random_look_around, move_through_village.");
    }

    // -------------------------------------------------------------------------
    // Private factory methods
    // -------------------------------------------------------------------------

    /**
     * PathfinderGoalFloat(EntityCreature entity)
     */
    private Object createFloat(Object nmsEntity) throws ReflectiveOperationException {
        Class<?> goalClass = Class.forName(NMS_PKG + "PathfinderGoalFloat");
        Class<?> creatureClass = Class.forName(NMS_PKG + "EntityCreature");
        Constructor<?> ctor = goalClass.getConstructor(creatureClass);
        return ctor.newInstance(nmsEntity);
    }

    /**
     * PathfinderGoalLookAtPlayer(EntityInsentient entity, Class lookAtType, float range)
     * <p>Supported parameters:
     * <ul>
     *   <li>{@code "range"} (float, default 8.0) — maximum look distance</li>
     * </ul>
     */
    private Object createLookAtPlayer(Object nmsEntity, Map<String, Object> p)
            throws ReflectiveOperationException {
        float range = getFloat(p, "range", 8.0f);
        Class<?> goalClass = Class.forName(NMS_PKG + "PathfinderGoalLookAtPlayer");
        Class<?> insentientClass = Class.forName(NMS_PKG + "EntityInsentient");
        Class<?> entityHumanClass = Class.forName(NMS_PKG + "EntityHuman");
        Constructor<?> ctor = goalClass.getConstructor(insentientClass, Class.class, float.class);
        return ctor.newInstance(nmsEntity, entityHumanClass, range);
    }

    /**
     * PathfinderGoalMeleeAttack(EntityCreature entity, Class targetType, double speed, boolean flag)
     * <p>Supported parameters:
     * <ul>
     *   <li>{@code "speed"} (double, default 1.0) — movement speed modifier</li>
     *   <li>{@code "pauseWhenIdle"} (boolean, default false) — whether to pause when no target</li>
     * </ul>
     */
    private Object createMeleeAttack(Object nmsEntity, Map<String, Object> p)
            throws ReflectiveOperationException {
        double speed = getDouble(p, "speed", 1.0);
        boolean pauseWhenIdle = getBoolean(p, "pauseWhenIdle", false);
        Class<?> goalClass = Class.forName(NMS_PKG + "PathfinderGoalMeleeAttack");
        Class<?> creatureClass = Class.forName(NMS_PKG + "EntityCreature");
        Class<?> entityHumanClass = Class.forName(NMS_PKG + "EntityHuman");
        Constructor<?> ctor = goalClass.getConstructor(
                creatureClass, Class.class, double.class, boolean.class);
        return ctor.newInstance(nmsEntity, entityHumanClass, speed, pauseWhenIdle);
    }

    /**
     * PathfinderGoalRandomLookAround(EntityInsentient entity)
     */
    private Object createRandomLookAround(Object nmsEntity) throws ReflectiveOperationException {
        Class<?> goalClass = Class.forName(NMS_PKG + "PathfinderGoalRandomLookAround");
        Class<?> insentientClass = Class.forName(NMS_PKG + "EntityInsentient");
        Constructor<?> ctor = goalClass.getConstructor(insentientClass);
        return ctor.newInstance(nmsEntity);
    }

    /**
     * PathfinderGoalMoveThroughVillage(EntityCreature entity, double speed, boolean nightOnly)
     * <p>Supported parameters:
     * <ul>
     *   <li>{@code "speed"} (double, default 1.0) — movement speed modifier</li>
     *   <li>{@code "nightOnly"} (boolean, default false) — whether to activate at night only</li>
     * </ul>
     */
    private Object createMoveThroughVillage(Object nmsEntity, Map<String, Object> p)
            throws ReflectiveOperationException {
        double speed = getDouble(p, "speed", 1.0);
        boolean nightOnly = getBoolean(p, "nightOnly", false);
        Class<?> goalClass = Class.forName(NMS_PKG + "PathfinderGoalMoveThroughVillage");
        Class<?> creatureClass = Class.forName(NMS_PKG + "EntityCreature");
        Constructor<?> ctor = goalClass.getConstructor(creatureClass, double.class, boolean.class);
        return ctor.newInstance(nmsEntity, speed, nightOnly);
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

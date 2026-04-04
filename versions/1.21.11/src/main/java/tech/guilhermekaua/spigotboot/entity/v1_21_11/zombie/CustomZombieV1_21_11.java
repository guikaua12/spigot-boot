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

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKey;
import tech.guilhermekaua.spigotboot.entity.api.goal.GoalDefinition;
import tech.guilhermekaua.spigotboot.entity.api.zombie.AbstractCustomZombie;
import tech.guilhermekaua.spigotboot.entity.v1_21_11.goal.ZombieGoalFactoryV1_21_11;

import java.util.Objects;

/**
 * Minecraft 1.21.11 implementation of {@link tech.guilhermekaua.spigotboot.entity.api.zombie.CustomZombie}.
 *
 * <p>Goal management is backed by NMS reflection against Mojang-mapped Paper classes.
 * Pathfinding uses Paper's stable {@code Pathfinder} API. Health attributes use the
 * modern {@code Attribute.GENERIC_MAX_HEALTH} API instead of the deprecated
 * {@link org.bukkit.entity.LivingEntity#getMaxHealth()} methods.
 *
 * @since 2.0.2
 */
public final class CustomZombieV1_21_11 extends AbstractCustomZombie {

    private static final MinecraftVersion VERSION = MinecraftVersion.of(1, 21, 11);

    private final NmsHandleV1_21_11 handle;
    private final ZombieGoalFactoryV1_21_11 goalFactory;

    /**
     * Creates a new 1.21.11 zombie wrapper around the given Bukkit entity.
     *
     * @param bukkit the already-spawned Bukkit zombie; must not be {@code null}
     * @throws NullPointerException when {@code bukkit} is {@code null}
     * @throws RuntimeException     when NMS reflection initialisation fails
     */
    public CustomZombieV1_21_11(@NotNull Zombie bukkit) {
        super(Objects.requireNonNull(bukkit, "bukkit cannot be null"), VERSION);
        this.handle = new NmsHandleV1_21_11(bukkit);
        this.goalFactory = new ZombieGoalFactoryV1_21_11();
    }

    /**
     * Adds an AI goal to this zombie.
     *
     * <p>Supported goals: {@code float}, {@code look_at_player}, {@code melee_attack},
     * {@code random_look_around}, {@code break_door}, {@code climb_on_top_of_powder_snow}.
     *
     * @param goalDefinition the goal to add
     * @throws NullPointerException  when {@code goalDefinition} is {@code null}
     * @throws tech.guilhermekaua.spigotboot.entity.api.exception.UnsupportedGoalException
     *         when the goal key is not available on 1.21.11
     */
    @Override
    public void addGoal(@NotNull GoalDefinition goalDefinition) {
        Objects.requireNonNull(goalDefinition, "goalDefinition cannot be null");
        Object nmsGoal = goalFactory.createGoal(
                handle.entityHandle(), goalDefinition, bukkit.getWorld());
        handle.addGoal(goalDefinition.priority(), nmsGoal);
    }

    /**
     * Removes all goals with the given key from this zombie.
     *
     * @param goalKey the key of the goal to remove
     * @throws NullPointerException  when {@code goalKey} is {@code null}
     * @throws tech.guilhermekaua.spigotboot.entity.api.exception.UnsupportedGoalException
     *         when the goal key is not known on 1.21.11
     */
    @Override
    public void removeGoal(@NotNull EntityGoalKey goalKey) {
        Objects.requireNonNull(goalKey, "goalKey cannot be null");
        try {
            String className = goalFactory.goalClassName(goalKey);
            Class<?> nmsGoalClass = Class.forName(className);
            handle.removeGoalsByClass(nmsGoalClass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "NMS goal class not found for key '" + goalKey + "' on 1.21.11", e);
        }
    }

    /**
     * Removes all available AI goals from this zombie.
     */
    @Override
    public void clearGoals() {
        handle.clearGoals();
    }

    /**
     * Navigates this zombie towards the given coordinates using Paper's {@code Pathfinder} API.
     *
     * @param x     target x coordinate
     * @param y     target y coordinate
     * @param z     target z coordinate
     * @param speed pathfinding speed multiplier
     */
    @Override
    public void moveTo(double x, double y, double z, double speed) {
        Location target = new Location(bukkit.getWorld(), x, y, z);
        ((Mob) bukkit).getPathfinder().moveTo(target, speed);
    }

    /**
     * Returns the maximum health using the modern attribute API.
     *
     * @return the maximum health value
     */
    @Override
    public double getMaxHealth() {
        AttributeInstance attr = bukkit.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getBaseValue() : 20.0;
    }

    /**
     * Sets the maximum health using the modern attribute API.
     *
     * @param maxHealth the maximum health; must be positive
     */
    @Override
    public void setMaxHealth(double maxHealth) {
        AttributeInstance attr = bukkit.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHealth);
        }
    }
}

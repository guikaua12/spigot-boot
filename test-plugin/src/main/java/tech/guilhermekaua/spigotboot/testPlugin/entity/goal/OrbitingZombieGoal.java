/*
 * The MIT License
 * Copyright Â© 2025 Guilherme KauÃ£ da Silva
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
package tech.guilhermekaua.spigotboot.testPlugin.entity.goal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

public final class OrbitingZombieGoal implements Goal<Zombie> {
    private static final EnumSet<GoalType> GOAL_TYPES = EnumSet.of(GoalType.MOVE, GoalType.LOOK);
    private static final double MAX_DISTANCE_SQUARED = 32.0D * 32.0D;

    private final GoalKey<Zombie> key;
    private final Zombie zombie;
    private final UUID trackedPlayerId;

    private double angle;
    private int ticks;

    public OrbitingZombieGoal(Plugin plugin, Zombie zombie, UUID trackedPlayerId) {
        this.key = GoalKey.of(
                Zombie.class,
                new NamespacedKey(Objects.requireNonNull(plugin, "plugin cannot be null"), "orbiting_demo_zombie")
        );
        this.zombie = Objects.requireNonNull(zombie, "zombie cannot be null");
        this.trackedPlayerId = Objects.requireNonNull(trackedPlayerId, "trackedPlayerId cannot be null");
        this.angle = Math.random() * Math.PI * 2.0D;
    }

    @Override
    public boolean shouldActivate() {
        return resolveTarget() != null;
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        zombie.getPathfinder().stopPathfinding();
    }

    @Override
    public void tick() {
        Player target = resolveTarget();
        if (target == null) {
            return;
        }

        ticks++;
        angle += Math.PI / 18.0D;

        double radius = 2.5D + Math.sin(ticks / 8.0D) * 0.75D;
        Location targetLocation = target.getLocation();
        Vector offset = new Vector(Math.cos(angle), 0.0D, Math.sin(angle)).multiply(radius);
        Location orbitLocation = targetLocation.clone().add(offset);
        orbitLocation.setY(targetLocation.getY());

        zombie.getPathfinder().moveTo(orbitLocation, 1.35D);
        zombie.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                zombie.getLocation().add(0.0D, 1.0D, 0.0D),
                3,
                0.2D,
                0.35D,
                0.2D,
                0.01D
        );
    }

    @Override
    public GoalKey<Zombie> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return GOAL_TYPES;
    }

    private Player resolveTarget() {
        if (!zombie.isValid() || zombie.isDead()) {
            return null;
        }

        Player player = Bukkit.getPlayer(trackedPlayerId);
        if (player == null || !player.isOnline() || player.isDead()) {
            return null;
        }
        if (!player.getWorld().equals(zombie.getWorld())) {
            return null;
        }
        if (player.getLocation().distanceSquared(zombie.getLocation()) > MAX_DISTANCE_SQUARED) {
            return null;
        }
        return player;
    }
}

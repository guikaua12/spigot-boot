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
package tech.guilhermekaua.spigotboot.testPlugin.entity.behavior;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityBehavior;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityContext;

import java.util.UUID;

/**
 * Shared demo behavior that visibly orbits a tracked player from inside the native entity tick.
 */
public final class OrbitingZombieBehavior implements CustomEntityBehavior<Zombie> {
    private static final double MAX_DISTANCE_SQUARED = 32.0D * 32.0D;

    private final UUID trackedPlayerId;

    private double angle;
    private int ticks;

    public OrbitingZombieBehavior(UUID trackedPlayerId) {
        this.trackedPlayerId = trackedPlayerId;
        this.angle = Math.random() * Math.PI * 2.0D;
    }

    @Override
    public void onTick(CustomEntityContext<Zombie> context) {
        Zombie zombie = context.bukkitEntity();
        Player target = resolveTarget(zombie);
        if (target == null) {
            return;
        }

        ticks++;
        angle += Math.PI / 20.0D;

        double radius = 2.4D + Math.sin(ticks / 7.0D) * 0.6D;
        Location targetLocation = target.getLocation();
        Vector offset = new Vector(Math.cos(angle), 0.0D, Math.sin(angle)).multiply(radius);

        Location orbitLocation = targetLocation.clone().add(offset);
        orbitLocation.setY(targetLocation.getY());
        orbitLocation.setDirection(targetLocation.toVector().subtract(orbitLocation.toVector()));

        zombie.teleport(orbitLocation);
        zombie.setTarget(target);
    }

    private Player resolveTarget(Zombie zombie) {
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

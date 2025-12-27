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
package tech.guilhermekaua.spigotboot.config.spigot.serialization;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;

/**
 * Serializer for Bukkit Location.
 * <p>
 * Supports both scalar format "world,x,y,z[,yaw,pitch]" and section format.
 */
public class LocationSerializer implements TypeSerializer<Location> {

    @Override
    public Location deserialize(@NotNull ConfigNode node, @NotNull Class<Location> type) throws SerializationException {
        if (node.isScalar()) {
            String value = node.get(String.class);
            if (value == null || value.isEmpty()) {
                return null;
            }
            // "world,x,y,z" or "world,x,y,z,yaw,pitch"
            String[] parts = value.split(",");
            if (parts.length < 4) {
                throw new SerializationException("Invalid location format: " + value +
                        ". Expected: world,x,y,z[,yaw,pitch]");
            }

            try {
                World world = Bukkit.getWorld(parts[0].trim());
                double x = Double.parseDouble(parts[1].trim());
                double y = Double.parseDouble(parts[2].trim());
                double z = Double.parseDouble(parts[3].trim());
                float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0f;
                float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0f;

                return new Location(world, x, y, z, yaw, pitch);
            } catch (NumberFormatException e) {
                throw new SerializationException("Invalid number in location: " + value, e);
            }
        }

        String worldName = node.node("world").get(String.class);
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;

        double x = node.node("x").get(Double.class, 0.0);
        double y = node.node("y").get(Double.class, 0.0);
        double z = node.node("z").get(Double.class, 0.0);
        float yaw = node.node("yaw").get(Float.class, 0f);
        float pitch = node.node("pitch").get(Float.class, 0f);

        return new Location(world, x, y, z, yaw, pitch);
    }

    @Override
    public void serialize(@NotNull Location value, @NotNull MutableConfigNode node) throws SerializationException {
        if (value.getWorld() != null) {
            node.node("world").set(value.getWorld().getName());
        }
        node.node("x").set(value.getX());
        node.node("y").set(value.getY());
        node.node("z").set(value.getZ());
        if (value.getYaw() != 0 || value.getPitch() != 0) {
            node.node("yaw").set(value.getYaw());
            node.node("pitch").set(value.getPitch());
        }
    }
}

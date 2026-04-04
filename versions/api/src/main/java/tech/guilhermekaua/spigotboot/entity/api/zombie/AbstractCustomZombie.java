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
package tech.guilhermekaua.spigotboot.entity.api.zombie;

import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKey;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKeys;

import java.util.Objects;

/**
 * Base implementation for version-specific {@link CustomZombie} wrappers.
 *
 * <p>All Bukkit-API-backed operations (health, name, baby state) are implemented
 * here so that each version-specific subclass only needs to provide the NMS-backed
 * operations: goal management and pathfinding navigation.
 *
 * @since 2.0.2
 */
public abstract class AbstractCustomZombie implements CustomZombie {

    /**
     * The underlying Bukkit zombie entity; available to subclasses for Bukkit API calls.
     */
    protected final Zombie bukkit;
    private final MinecraftVersion version;

    /**
     * Creates a new abstract zombie wrapper.
     *
     * @param bukkit  the underlying Bukkit zombie entity; must not be {@code null}
     * @param version the Minecraft version that created this entity; must not be {@code null}
     */
    protected AbstractCustomZombie(@NotNull Zombie bukkit, @NotNull MinecraftVersion version) {
        this.bukkit = Objects.requireNonNull(bukkit, "bukkit cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
    }

    @Override
    public @NotNull EntityTypeKey type() {
        return EntityTypeKeys.ZOMBIE;
    }

    @Override
    public @NotNull MinecraftVersion version() {
        return version;
    }

    @Override
    public boolean isBaby() {
        return bukkit.isBaby();
    }

    @Override
    public void setBaby(boolean baby) {
        bukkit.setBaby(baby);
    }

    @Override
    public @Nullable String getName() {
        return bukkit.getCustomName();
    }

    @Override
    public void setName(@Nullable String name) {
        bukkit.setCustomName(name);
    }

    @Override
    public void setNameVisible(boolean visible) {
        bukkit.setCustomNameVisible(visible);
    }

    @Override
    public double getHealth() {
        return bukkit.getHealth();
    }

    @Override
    public void setHealth(double health) {
        bukkit.setHealth(health);
    }

    @Override
    @SuppressWarnings("deprecation")
    public double getMaxHealth() {
        return bukkit.getMaxHealth();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setMaxHealth(double maxHealth) {
        bukkit.setMaxHealth(maxHealth);
    }

    @Override
    public void despawn() {
        bukkit.remove();
    }

    @Override
    public @NotNull Object unwrap() {
        return bukkit;
    }
}

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
package tech.guilhermekaua.spigotboot.entity.api;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a concrete Minecraft server version.
 *
 * @since 2.0.2
 */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final int major;
    private final int minor;
    private final int patch;
    private final String canonicalName;

    private MinecraftVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.canonicalName = major + "." + minor + "." + patch;
    }

    /**
     * Creates a new version instance.
     *
     * @param major the major component
     * @param minor the minor component
     * @param patch the patch component
     * @return the created version
     * @throws IllegalArgumentException when any component is negative
     */
    public static @NotNull MinecraftVersion of(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components cannot be negative.");
        }
        return new MinecraftVersion(major, minor, patch);
    }

    /**
     * Parses a Minecraft version from a raw server version string.
     *
     * @param value the raw value to parse
     * @return the parsed version
     * @throws IllegalArgumentException when the value does not contain a semantic Minecraft version
     */
    public static @NotNull MinecraftVersion parse(@NotNull String value) {
        Objects.requireNonNull(value, "value cannot be null");

        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Could not parse a Minecraft version from '" + value + "'.");
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return of(major, minor, patch);
    }

    /**
     * Returns the major version component.
     *
     * @return the major component
     */
    public int major() {
        return major;
    }

    /**
     * Returns the minor version component.
     *
     * @return the minor component
     */
    public int minor() {
        return minor;
    }

    /**
     * Returns the patch version component.
     *
     * @return the patch component
     */
    public int patch() {
        return patch;
    }

    /**
     * Checks whether this version is equal to or newer than the supplied version.
     *
     * @param other the version to compare against
     * @return {@code true} when this version is at least the supplied version
     */
    public boolean isAtLeast(@NotNull MinecraftVersion other) {
        Objects.requireNonNull(other, "other cannot be null");
        return compareTo(other) >= 0;
    }

    /**
     * Checks whether this version is within the supplied inclusive range.
     *
     * @param minimum the lower bound
     * @param maximum the upper bound
     * @return {@code true} when this version is between both bounds
     */
    public boolean isBetween(@NotNull MinecraftVersion minimum, @NotNull MinecraftVersion maximum) {
        Objects.requireNonNull(minimum, "minimum cannot be null");
        Objects.requireNonNull(maximum, "maximum cannot be null");
        return compareTo(minimum) >= 0 && compareTo(maximum) <= 0;
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion other) {
        Objects.requireNonNull(other, "other cannot be null");
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MinecraftVersion)) {
            return false;
        }
        MinecraftVersion other = (MinecraftVersion) obj;
        return major == other.major && minor == other.minor && patch == other.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return canonicalName;
    }
}

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
package tech.guilhermekaua.spigotboot.core.spigot.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable, comparable view of the running Minecraft server's version.
 *
 * <p>The version triple is parsed from {@code Bukkit.getBukkitVersion()} (e.g.
 * {@code "1.21.4-R0.1-SNAPSHOT"}) with an open numeric scheme — every {@code major.minor.patch}
 * is accepted, so no per-release code change is ever needed. The optional legacy CraftBukkit
 * package suffix (e.g. {@code v1_19_R3}) is also captured; it is empty on modern un-versioned
 * servers (Paper 1.20.5+) and on renumbered schemes.
 *
 * <p>Ordering and equality are defined over the {@code (major, minor, patch)} triple only; the
 * package suffix is metadata and does not participate.
 *
 * <p>When the version string cannot be parsed the components are {@code -1} and
 * {@link #isAtLeast(int, int)} always returns {@code false}; construction never throws.
 */
public final class ServerVersion implements Comparable<ServerVersion> {

    private static final Pattern BUKKIT_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern PACKAGE_SUFFIX = Pattern.compile("v(\\d+)_(\\d+)_R(\\d+)");

    /** sentinel for a version component that could not be parsed */
    private static final int UNKNOWN = -1;

    private final int major;
    private final int minor;
    private final int patch;
    private final String rawVersion;
    private final String nmsPackageSuffix;

    private ServerVersion(int major, int minor, int patch, @NotNull String rawVersion,
                          @Nullable String nmsPackageSuffix) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.rawVersion = rawVersion;
        this.nmsPackageSuffix = nmsPackageSuffix;
    }

    /**
     * Parses the server coordinates without touching Bukkit, so the logic is unit-testable
     * without a live server.
     *
     * @param bukkitVersion     the {@code Bukkit.getBukkitVersion()} string, or {@code null}
     * @param serverPackageName the {@code Bukkit.getServer().getClass().getPackage().getName()}
     *                          value, or {@code null}
     * @return the parsed version; components are {@code -1} when {@code bukkitVersion} is null or
     *         unparseable
     */
    static @NotNull ServerVersion parse(@Nullable String bukkitVersion, @Nullable String serverPackageName) {
        String raw = bukkitVersion == null ? "" : bukkitVersion;
        int major = UNKNOWN;
        int minor = UNKNOWN;
        int patch = UNKNOWN;

        if (bukkitVersion != null) {
            Matcher matcher = BUKKIT_VERSION.matcher(bukkitVersion);
            if (matcher.find()) {
                major = parseIntOrUnknown(matcher.group(1));
                minor = parseIntOrUnknown(matcher.group(2));
                String patchGroup = matcher.group(3);
                patch = patchGroup == null ? 0 : parseIntOrUnknown(patchGroup);
            }
        }

        // a partially-parsed triple is meaningless, so collapse it to fully unknown
        if (major == UNKNOWN || minor == UNKNOWN) {
            major = UNKNOWN;
            minor = UNKNOWN;
            patch = UNKNOWN;
        }

        return new ServerVersion(major, minor, patch, raw, parsePackageSuffix(serverPackageName));
    }

    private static @Nullable String parsePackageSuffix(@Nullable String serverPackageName) {
        if (serverPackageName == null) {
            return null;
        }
        int lastDot = serverPackageName.lastIndexOf('.');
        String last = lastDot < 0 ? serverPackageName : serverPackageName.substring(lastDot + 1);
        return PACKAGE_SUFFIX.matcher(last).matches() ? last : null;
    }

    private static int parseIntOrUnknown(@Nullable String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }

    /**
     * @return the major version component (the {@code 1} in {@code 1.21.4}), or {@code -1} if
     *         the version could not be parsed
     */
    public int getMajor() {
        return major;
    }

    /**
     * @return the minor version component (the {@code 21} in {@code 1.21.4}), or {@code -1} if
     *         the version could not be parsed
     */
    public int getMinor() {
        return minor;
    }

    /**
     * @return the patch version component (the {@code 4} in {@code 1.21.4}; {@code 0} when the
     *         version omits a patch), or {@code -1} if the version could not be parsed
     */
    public int getPatch() {
        return patch;
    }

    /**
     * @return the raw {@code Bukkit.getBukkitVersion()} string this was parsed from (empty string
     *         when the source was {@code null})
     */
    public @NotNull String getRawVersion() {
        return rawVersion;
    }

    /**
     * @return the legacy CraftBukkit package suffix (e.g. {@code v1_19_R3}), or an empty
     *         {@link Optional} on un-versioned modern servers
     */
    public @NotNull Optional<String> getNmsPackageSuffix() {
        return Optional.ofNullable(nmsPackageSuffix);
    }

    /**
     * @return {@code true} when the server relocates CraftBukkit into a versioned
     *         {@code vX_Y_RZ} package (pre-Paper-1.20.5 style)
     */
    public boolean hasVersionedNmsPackage() {
        return nmsPackageSuffix != null;
    }

    /**
     * @param major the major version to compare against
     * @param minor the minor version to compare against
     * @return {@code true} if this version is at least {@code major.minor.0}; always {@code false}
     *         when this version could not be parsed
     */
    public boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    /**
     * @param major the major version to compare against
     * @param minor the minor version to compare against
     * @param patch the patch version to compare against
     * @return {@code true} if this version is at least {@code major.minor.patch}; always
     *         {@code false} when this version could not be parsed
     */
    public boolean isAtLeast(int major, int minor, int patch) {
        if (this.major == UNKNOWN || this.minor == UNKNOWN) {
            return false;
        }
        return compareTriple(this.major, this.minor, this.patch, major, minor, patch) >= 0;
    }

    /**
     * @param major the major version to compare against
     * @param minor the minor version to compare against
     * @return the logical complement of {@link #isAtLeast(int, int)} — {@code true} when this
     *         version is strictly older than {@code major.minor.0} (and when it is unparseable)
     */
    public boolean isOlderThan(int major, int minor) {
        return !isAtLeast(major, minor);
    }

    @Override
    public int compareTo(@NotNull ServerVersion other) {
        Objects.requireNonNull(other, "other");
        return compareTriple(this.major, this.minor, this.patch, other.major, other.minor, other.patch);
    }

    private static int compareTriple(int aMajor, int aMinor, int aPatch,
                                     int bMajor, int bMinor, int bPatch) {
        int cmp = Integer.compare(aMajor, bMajor);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(aMinor, bMinor);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(aPatch, bPatch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerVersion)) {
            return false;
        }
        ServerVersion that = (ServerVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return "ServerVersion{" + major + "." + minor + "." + patch +
                (nmsPackageSuffix != null ? " (" + nmsPackageSuffix + ")" : "") + "}";
    }
}

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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionTest {

    @Test
    void parses_full_legacy_versioned_server() {
        ServerVersion version = ServerVersion.parse("1.19.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit.v1_19_R3");

        assertEquals(1, version.getMajor());
        assertEquals(19, version.getMinor());
        assertEquals(4, version.getPatch());
        assertEquals("1.19.4-R0.1-SNAPSHOT", version.getRawVersion());
        assertEquals(Optional.of("v1_19_R3"), version.getNmsPackageSuffix());
        assertTrue(version.hasVersionedNmsPackage());
    }

    @Test
    void parses_modern_unversioned_server() {
        ServerVersion version = ServerVersion.parse("1.21.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertEquals(1, version.getMajor());
        assertEquals(21, version.getMinor());
        assertEquals(4, version.getPatch());
        assertFalse(version.getNmsPackageSuffix().isPresent());
        assertFalse(version.hasVersionedNmsPackage());
    }

    @Test
    void missing_patch_defaults_to_zero() {
        ServerVersion version = ServerVersion.parse("1.21-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertEquals(1, version.getMajor());
        assertEquals(21, version.getMinor());
        assertEquals(0, version.getPatch());
    }

    @Test
    void parses_renumbered_scheme_faithfully() {
        ServerVersion version = ServerVersion.parse("26.1.2-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertEquals(26, version.getMajor());
        assertEquals(1, version.getMinor());
        assertEquals(2, version.getPatch());
        assertFalse(version.hasVersionedNmsPackage());
    }

    @Test
    void unparseable_version_yields_unknown_components_and_never_throws() {
        ServerVersion fromNull = ServerVersion.parse(null, null);
        ServerVersion fromGarbage = ServerVersion.parse("not-a-version", "weird.package");

        assertEquals(-1, fromNull.getMajor());
        assertEquals(-1, fromNull.getMinor());
        assertEquals(-1, fromNull.getPatch());
        assertEquals("", fromNull.getRawVersion());
        assertFalse(fromNull.isAtLeast(1, 8));

        assertEquals(-1, fromGarbage.getMajor());
        assertEquals("not-a-version", fromGarbage.getRawVersion());
        assertFalse(fromGarbage.isAtLeast(1, 8));
    }

    @Test
    void null_package_name_yields_empty_suffix() {
        ServerVersion version = ServerVersion.parse("1.20.1-R0.1-SNAPSHOT", null);

        assertFalse(version.hasVersionedNmsPackage());
        assertFalse(version.getNmsPackageSuffix().isPresent());
    }

    @Test
    void malformed_package_suffix_yields_empty_suffix() {
        // the R group requires at least one digit, so a truncated "v1_19_R" must not match
        ServerVersion version = ServerVersion.parse("1.19.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit.v1_19_R");

        assertFalse(version.hasVersionedNmsPackage());
        assertFalse(version.getNmsPackageSuffix().isPresent());
    }

    @Test
    void overflowing_numeric_component_collapses_to_unknown() {
        // 99999999999 overflows int, so parseInt throws and the whole triple collapses to unknown
        ServerVersion version = ServerVersion.parse("99999999999.1-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertEquals(-1, version.getMajor());
        assertEquals(-1, version.getMinor());
        assertEquals(-1, version.getPatch());
        assertFalse(version.isAtLeast(1, 8));
    }

    @Test
    void is_at_least_compares_against_major_minor() {
        ServerVersion version = ServerVersion.parse("1.20.1-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertTrue(version.isAtLeast(1, 20));
        assertTrue(version.isAtLeast(1, 8));
        assertFalse(version.isAtLeast(1, 21));
        assertFalse(version.isAtLeast(2, 0));
    }

    @Test
    void is_at_least_compares_against_patch() {
        ServerVersion version = ServerVersion.parse("1.20.1-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertTrue(version.isAtLeast(1, 20, 1));
        assertTrue(version.isAtLeast(1, 20, 0));
        assertFalse(version.isAtLeast(1, 20, 2));
    }

    @Test
    void is_older_than_is_complement_of_is_at_least() {
        ServerVersion version = ServerVersion.parse("1.19.4-R0.1-SNAPSHOT", "org.bukkit.craftbukkit");

        assertTrue(version.isOlderThan(1, 20));
        assertFalse(version.isOlderThan(1, 19));
        assertFalse(version.isOlderThan(1, 8));
    }

    @Test
    void compare_to_orders_by_triple() {
        ServerVersion older = ServerVersion.parse("1.19.4", "org.bukkit.craftbukkit");
        ServerVersion newer = ServerVersion.parse("1.20.1", "org.bukkit.craftbukkit");
        ServerVersion newerPatch = ServerVersion.parse("1.20.2", "org.bukkit.craftbukkit");

        assertTrue(older.compareTo(newer) < 0);
        assertTrue(newer.compareTo(older) > 0);
        assertTrue(newer.compareTo(newerPatch) < 0);
        assertEquals(0, newer.compareTo(ServerVersion.parse("1.20.1", "org.bukkit.craftbukkit")));
    }

    @Test
    void equals_and_hash_code_ignore_suffix() {
        ServerVersion versioned = ServerVersion.parse("1.19.4", "org.bukkit.craftbukkit.v1_19_R3");
        ServerVersion unversioned = ServerVersion.parse("1.19.4", "org.bukkit.craftbukkit");
        ServerVersion different = ServerVersion.parse("1.20.1", "org.bukkit.craftbukkit");

        assertEquals(versioned, unversioned);
        assertEquals(versioned.hashCode(), unversioned.hashCode());
        assertNotEquals(versioned, different);
    }
}

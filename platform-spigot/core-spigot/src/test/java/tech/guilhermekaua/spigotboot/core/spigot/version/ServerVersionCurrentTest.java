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

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionCurrentTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void detect_matches_parse_of_running_server() {
        // tie detect() to the same inputs parse() would receive, so the assertion does not
        // hard-code MockBukkit's reported numbers
        Object server = Bukkit.getServer();
        ServerVersion expected = ServerVersion.parse(
                Bukkit.getBukkitVersion(), server.getClass().getPackage().getName());

        ServerVersion detected = ServerVersion.detect();

        assertNotNull(detected);
        assertEquals(expected, detected);
        assertEquals(Bukkit.getBukkitVersion(), detected.getRawVersion());
        // MockBukkit-v1.20 reports a modern 1.x server
        assertTrue(detected.isAtLeast(1, 8));
    }

    @Test
    void current_returns_cached_singleton() {
        ServerVersion first = ServerVersion.current();
        ServerVersion second = ServerVersion.current();

        assertNotNull(first);
        assertSame(first, second);
    }
}

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
package tech.guilhermekaua.spigotboot.core.spigot.utils;

import org.bukkit.Sound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SoundCompatTest {

    @Test
    void resolves_enum_sound_by_name() {
        Sound sound = SoundCompat.resolve("ENTITY_PLAYER_LEVELUP");
        assertNotNull(sound);
    }

    @Test
    void serializes_enum_sound_to_its_name() {
        Sound sound = SoundCompat.resolve("ENTITY_PLAYER_LEVELUP");
        assertEquals("ENTITY_PLAYER_LEVELUP", SoundCompat.toKey(sound));
    }

    @Test
    void resolves_namespaced_key_on_enum_server() {
        // the enum branch upper-cases, replaces separators, and strips a MINECRAFT_ prefix
        Sound sound = SoundCompat.resolve("minecraft:entity.player.levelup");
        assertNotNull(sound);
        assertEquals("ENTITY_PLAYER_LEVELUP", SoundCompat.toKey(sound));
    }

    @Test
    void returns_null_for_unknown_sound() {
        assertNull(SoundCompat.resolve("not_a_real_sound_xyz"));
    }
}

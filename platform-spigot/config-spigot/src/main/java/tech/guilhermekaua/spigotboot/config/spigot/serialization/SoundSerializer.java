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

import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.core.spigot.utils.SoundCompat;

/**
 * Cross-version serializer for {@link Sound}. {@code Sound} is an enum up to MC 1.21.2 and an
 * interface from 1.21.3 on; resolution is delegated to {@link SoundCompat} so the same config
 * works on every supported server.
 */
public class SoundSerializer implements TypeSerializer<Sound> {

    @Override
    public Sound deserialize(@NotNull ConfigNode node, @NotNull Class<Sound> type) throws SerializationException {
        String value = node.get(String.class);
        if (value == null || value.isEmpty()) {
            return null;
        }
        Sound sound = SoundCompat.resolve(value);
        if (sound == null) {
            throw new SerializationException("Unknown sound: " + value);
        }
        return sound;
    }

    @Override
    public void serialize(@NotNull Sound value, @NotNull MutableConfigNode node) throws SerializationException {
        node.set(SoundCompat.toKey(value));
    }
}

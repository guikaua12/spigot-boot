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

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;

/**
 * Serializer for {@link Material} enum.
 */
public class MaterialSerializer implements TypeSerializer<Material> {

    @Override
    public Material deserialize(@NotNull ConfigNode node, @NotNull Class<Material> type) throws SerializationException {
        String value = node.get(String.class);
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            return Material.valueOf("LEGACY_" + value);
        } catch (Exception e) {
            try {
                return Material.getMaterial(value);
            } catch (Exception exception) {
                throw new SerializationException("Unknown material: " + value);
            }
        }
    }

    @Override
    public void serialize(@NotNull Material value, @NotNull MutableConfigNode node) throws SerializationException {
        node.set(value.name());
    }
}

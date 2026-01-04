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
package tech.guilhermekaua.spigotboot.config.serialization.scalars;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;

import java.util.Objects;

/**
 * Built-in serializers for primitive types and common Java types.
 */
public final class PrimitiveSerializers {

    private PrimitiveSerializers() {
    }

    /**
     * Registers all primitive serializers to the given registry.
     *
     * @param registry the registry to populate
     */
    public static void registerAll(@NotNull TypeSerializerRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");

        registry.register(String.class, new StringSerializer());

        registry.register(Boolean.class, new BooleanSerializer());
        registry.register(boolean.class, new BooleanSerializer());

        registry.register(Integer.class, new IntegerSerializer());
        registry.register(int.class, new IntegerSerializer());

        registry.register(Long.class, new LongSerializer());
        registry.register(long.class, new LongSerializer());

        registry.register(Double.class, new DoubleSerializer());
        registry.register(double.class, new DoubleSerializer());

        registry.register(Float.class, new FloatSerializer());
        registry.register(float.class, new FloatSerializer());

        registry.register(Short.class, new ShortSerializer());
        registry.register(short.class, new ShortSerializer());

        registry.register(Byte.class, new ByteSerializer());
        registry.register(byte.class, new ByteSerializer());

        registry.register(Character.class, new CharacterSerializer());
        registry.register(char.class, new CharacterSerializer());
    }

    /**
     * Serializer for String values.
     */
    public static class StringSerializer implements TypeSerializer<String> {
        @Override
        public String deserialize(@NotNull ConfigNode node, @NotNull Class<String> type) {
            Object raw = node.raw();
            return raw != null ? String.valueOf(raw) : null;
        }

        @Override
        public void serialize(@NotNull String value, @NotNull MutableConfigNode node) {
            node.set(value);
        }
    }

    /**
     * Serializer for Boolean values.
     */
    public static class BooleanSerializer implements TypeSerializer<Boolean> {
        @Override
        public Boolean deserialize(@NotNull ConfigNode node, @NotNull Class<Boolean> type) {
            Object raw = node.raw();
            if (raw == null) {
                return type.isPrimitive() ? false : null;
            }
            if (raw instanceof Boolean) {
                return (Boolean) raw;
            }
            String str = String.valueOf(raw).toLowerCase();
            return "true".equals(str) || "yes".equals(str) || "1".equals(str) || "on".equals(str);
        }

        @Override
        public void serialize(@NotNull Boolean value, @NotNull MutableConfigNode node) {
            node.set(value);
        }
    }

    /**
     * Serializer for Integer values.
     */
    public static class IntegerSerializer implements TypeSerializer<Integer> {
        @Override
        public Integer deserialize(@NotNull ConfigNode node, @NotNull Class<Integer> type) {
            Object raw = node.raw();
            if (raw == null) {
                return type.isPrimitive() ? 0 : null;
            }
            if (raw instanceof Number) {
                return ((Number) raw).intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SerializationException("Cannot parse integer: " + raw, e);
            }
        }

        @Override
        public void serialize(@NotNull Integer value, @NotNull MutableConfigNode node) {
            node.set(value);
        }
    }

    /**
     * Serializer for Long values.
     */
    public static class LongSerializer implements TypeSerializer<Long> {
        @Override
        public Long deserialize(@NotNull ConfigNode node, @NotNull Class<Long> type) {
            Object raw = node.raw();
            if (raw == null) {
                return type.isPrimitive() ? 0L : null;
            }
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            }
            try {
                return Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SerializationException("Cannot parse long: " + raw, e);
            }
        }

        @Override
        public void serialize(@NotNull Long value, @NotNull MutableConfigNode node) {
            node.set(value);
        }
    }

    /**
     * Serializer for Double values.
     */
    public static class DoubleSerializer implements TypeSerializer<Double> {
        @Override
        public Double deserialize(@NotNull ConfigNode node, @NotNull Class<Double> type) {
            Object raw = node.raw();
            if (raw == null) {
                return type.isPrimitive() ? 0.0 : null;
            }
            if (raw instanceof Number) {
                return ((Number) raw).doubleValue();
            }
            try {
                return Double.parseDouble(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SerializationException("Cannot parse double: " + raw, e);
            }
        }

        @Override
        public void serialize(@NotNull Double value, @NotNull MutableConfigNode node) {
            node.set(value);
        }
    }

    /**
     * Serializer for Float values.
     */
    public static class FloatSerializer implements TypeSerializer<Float> {
        @Override
        public Float deserialize(@NotNull ConfigNode node, @NotNull Class<Float> type) {
            Object raw = node.raw();
            if (raw == null) {
                return type.isPrimitive() ? 0.0f : null;
            }
            if (raw instanceof Number) {
                return ((Number) raw).floatValue();
            }
            try {
                return Float.parseFloat(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SerializationException("Cannot parse float: " + raw, e);
            }
        }

        @Override
        public void serialize(@NotNull Float value, @NotNull MutableConfigNode node) {
            node.set(value);
        }
    }

    /**
     * Serializer for Short values.
     */
    public static class ShortSerializer implements TypeSerializer<Short> {
        @Override
        public Short deserialize(@NotNull ConfigNode node, @NotNull Class<Short> type) {
            Object raw = node.raw();
            if (raw == null) {
                return type.isPrimitive() ? (short) 0 : null;
            }
            if (raw instanceof Number) {
                return ((Number) raw).shortValue();
            }
            try {
                return Short.parseShort(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SerializationException("Cannot parse short: " + raw, e);
            }
        }

        @Override
        public void serialize(@NotNull Short value, @NotNull MutableConfigNode node) {
            node.set(value);
        }
    }

    /**
     * Serializer for Byte values.
     */
    public static class ByteSerializer implements TypeSerializer<Byte> {
        @Override
        public Byte deserialize(@NotNull ConfigNode node, @NotNull Class<Byte> type) {
            Object raw = node.raw();
            if (raw == null) {
                return type.isPrimitive() ? (byte) 0 : null;
            }
            if (raw instanceof Number) {
                return ((Number) raw).byteValue();
            }
            try {
                return Byte.parseByte(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new SerializationException("Cannot parse byte: " + raw, e);
            }
        }

        @Override
        public void serialize(@NotNull Byte value, @NotNull MutableConfigNode node) {
            node.set(value);
        }
    }

    /**
     * Serializer for Character values.
     */
    public static class CharacterSerializer implements TypeSerializer<Character> {
        @Override
        public Character deserialize(@NotNull ConfigNode node, @NotNull Class<Character> type) {
            Object raw = node.raw();
            if (raw == null) {
                return type.isPrimitive() ? '\0' : null;
            }
            if (raw instanceof Character) {
                return (Character) raw;
            }
            String str = String.valueOf(raw);
            if (str.isEmpty()) {
                return type.isPrimitive() ? '\0' : null;
            }
            return str.charAt(0);
        }

        @Override
        public void serialize(@NotNull Character value, @NotNull MutableConfigNode node) {
            node.set(String.valueOf(value));
        }
    }
}

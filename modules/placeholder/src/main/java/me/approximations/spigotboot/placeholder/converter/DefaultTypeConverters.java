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
package me.approximations.spigotboot.placeholder.converter;

public final class DefaultTypeConverters {
    private DefaultTypeConverters() {
    }

    public static final TypeConverter<String> STRING = value -> value;
    public static final TypeConverter<Integer> INTEGER = value -> {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer value: " + value, e);
        }
    };

    public static final TypeConverter<Boolean> BOOLEAN = value -> {
        if (value == null) return null;
        return Boolean.parseBoolean(value);
    };

    public static final TypeConverter<Double> DOUBLE = value -> {
        if (value == null) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid double value: " + value, e);
        }
    };

    public static final TypeConverter<Long> LONG = value -> {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid long value: " + value, e);
        }
    };

    public static final TypeConverter<Float> FLOAT = value -> {
        if (value == null) return null;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid float value: " + value, e);
        }
    };

    public static final TypeConverter<Short> SHORT = value -> {
        if (value == null) return null;
        try {
            return Short.parseShort(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid short value: " + value, e);
        }
    };

    public static final TypeConverter<Byte> BYTE = value -> {
        if (value == null) return null;
        try {
            return Byte.parseByte(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid byte value: " + value, e);
        }
    };

    public static final TypeConverter<Character> CHARACTER = value -> {
        if (value == null || value.length() != 1) {
            throw new IllegalArgumentException("Invalid character value: " + value);
        }
        return value.charAt(0);
    };

    public static final TypeConverter<String[]> STRING_ARRAY = value -> {
        if (value == null) return null;
        return value.split(",");
    };

    public static final TypeConverter<Integer[]> INTEGER_ARRAY = value -> {
        if (value == null) return null;
        String[] parts = value.split(",");
        Integer[] integers = new Integer[parts.length];
        for (int i = 0; i < parts.length; i++) {
            integers[i] = INTEGER.convert(parts[i].trim());
        }
        return integers;
    };

    public static final TypeConverter<Boolean[]> BOOLEAN_ARRAY = value -> {
        if (value == null) return null;
        String[] parts = value.split(",");
        Boolean[] booleans = new Boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            booleans[i] = BOOLEAN.convert(parts[i].trim());
        }
        return booleans;
    };

    public static final TypeConverter<Double[]> DOUBLE_ARRAY = value -> {
        if (value == null) return null;
        String[] parts = value.split(",");
        Double[] doubles = new Double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            doubles[i] = DOUBLE.convert(parts[i].trim());
        }
        return doubles;
    };

    public static final TypeConverter<Long[]> LONG_ARRAY = value -> {
        if (value == null) return null;
        String[] parts = value.split(",");
        Long[] longs = new Long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            longs[i] = LONG.convert(parts[i].trim());
        }
        return longs;
    };

    public static final TypeConverter<Float[]> FLOAT_ARRAY = value -> {
        if (value == null) return null;
        String[] parts = value.split(",");
        Float[] floats = new Float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            floats[i] = FLOAT.convert(parts[i].trim());
        }
        return floats;
    };

    public static final TypeConverter<Short[]> SHORT_ARRAY = value -> {
        if (value == null) return null;
        String[] parts = value.split(",");
        Short[] shorts = new Short[parts.length];
        for (int i = 0; i < parts.length; i++) {
            shorts[i] = SHORT.convert(parts[i].trim());
        }
        return shorts;
    };

    public static final TypeConverter<Byte[]> BYTE_ARRAY = value -> {
        if (value == null) return null;
        String[] parts = value.split(",");
        Byte[] bytes = new Byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = BYTE.convert(parts[i].trim());
        }
        return bytes;
    };

    public static final TypeConverter<Character[]> CHARACTER_ARRAY = value -> {
        if (value == null) return null;
        String[] parts = value.split(",");
        Character[] characters = new Character[parts.length];
        for (int i = 0; i < parts.length; i++) {
            characters[i] = CHARACTER.convert(parts[i].trim());
        }
        return characters;
    };
}

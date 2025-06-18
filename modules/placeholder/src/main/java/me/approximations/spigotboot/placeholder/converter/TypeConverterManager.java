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

import me.approximations.spigotboot.core.di.annotations.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TypeConverterManager {
    private final Map<Class<?>, TypeConverter<?>> converters = new HashMap<>();

    public TypeConverterManager() {
        registerConverter(String.class, DefaultTypeConverters.STRING);
        registerConverter(Integer.class, DefaultTypeConverters.INTEGER);
        registerConverter(Boolean.class, DefaultTypeConverters.BOOLEAN);
        registerConverter(Double.class, DefaultTypeConverters.DOUBLE);
        registerConverter(Long.class, DefaultTypeConverters.LONG);
        registerConverter(Float.class, DefaultTypeConverters.FLOAT);
        registerConverter(Short.class, DefaultTypeConverters.SHORT);
        registerConverter(Byte.class, DefaultTypeConverters.BYTE);
        registerConverter(Character.class, DefaultTypeConverters.CHARACTER);

        registerConverter(String[].class, DefaultTypeConverters.STRING_ARRAY);
        registerConverter(Integer[].class, DefaultTypeConverters.INTEGER_ARRAY);
        registerConverter(Boolean[].class, DefaultTypeConverters.BOOLEAN_ARRAY);
        registerConverter(Double[].class, DefaultTypeConverters.DOUBLE_ARRAY);
        registerConverter(Long[].class, DefaultTypeConverters.LONG_ARRAY);
        registerConverter(Float[].class, DefaultTypeConverters.FLOAT_ARRAY);
        registerConverter(Short[].class, DefaultTypeConverters.SHORT_ARRAY);
        registerConverter(Byte[].class, DefaultTypeConverters.BYTE_ARRAY);
        registerConverter(Character[].class, DefaultTypeConverters.CHARACTER_ARRAY);
    }

    @SuppressWarnings("unchecked")
    public <T> T convert(Class<T> targetType, String value) throws IllegalArgumentException {
        if (!converters.containsKey(targetType)) {
            throw new IllegalArgumentException("No converter registered for type: " + targetType.getName());
        }
        return (T) converters.get(targetType).convert(value);
    }

    public <T> void registerConverter(Class<T> type, TypeConverter<T> converter) {
        if (converters.containsKey(type)) {
            throw new IllegalArgumentException("Converter for type " + type.getName() + " is already registered.");
        }
        converters.put(type, converter);
    }
}

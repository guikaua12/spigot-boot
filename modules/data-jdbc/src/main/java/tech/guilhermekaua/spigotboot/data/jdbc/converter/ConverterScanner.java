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
package tech.guilhermekaua.spigotboot.data.jdbc.converter;

import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.data.converter.AttributeConverter;
import tech.guilhermekaua.spigotboot.data.converter.Converter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConverterScanner {
    private static final Logger LOGGER = Logger.getLogger(ConverterScanner.class.getName());

    private ConverterScanner() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void scanAndRegister(String basePackage, TypeConverterRegistry registry) {
        Set<Class<?>> classes = ReflectionUtils.getClassesAnnotatedWith(basePackage, Converter.class);

        for (Class<?> clazz : classes) {
            Converter converterAnnotation = clazz.getAnnotation(Converter.class);
            if (converterAnnotation == null || !converterAnnotation.autoApply()) {
                continue;
            }

            if (!AttributeConverter.class.isAssignableFrom(clazz)) {
                LOGGER.log(Level.WARNING, "Class {0} is annotated with @Converter but does not implement AttributeConverter. Skipping.", clazz.getName());
                continue;
            }

            Class<?> sourceType = resolveSourceType(clazz);
            if (sourceType == null) {
                LOGGER.log(Level.WARNING, "Could not resolve source type for converter {0}. Skipping.", clazz.getName());
                continue;
            }

            try {
                AttributeConverter converter = (AttributeConverter) clazz.getDeclaredConstructor().newInstance();
                registry.register(sourceType, converter);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to instantiate converter " + clazz.getName() + ". Skipping.", e);
            }
        }
    }

    private static Class<?> resolveSourceType(Class<?> converterClass) {
        return resolveSourceType((Type) converterClass);
    }

    private static Class<?> resolveSourceType(Type type) {
        if (type == null) {
            return null;
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> && AttributeConverter.class.isAssignableFrom((Class<?>) rawType)) {
                Type sourceType = parameterizedType.getActualTypeArguments()[0];
                if (sourceType instanceof Class<?>) {
                    return (Class<?>) sourceType;
                }
            }

            if (rawType instanceof Class<?>) {
                return resolveSourceType((Class<?>) rawType);
            }

            return null;
        }

        if (!(type instanceof Class<?>)) {
            return null;
        }

        Class<?> converterClass = (Class<?>) type;
        for (Type genericInterface : converterClass.getGenericInterfaces()) {
            Class<?> sourceType = resolveSourceType(genericInterface);
            if (sourceType != null) {
                return sourceType;
            }
        }

        return resolveSourceType(converterClass.getGenericSuperclass());
    }
}

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
package tech.guilhermekaua.spigotboot.core.utils;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Map;

public final class CollectionTypeUtils {

    @Getter
    public static class CollectionTypeInfo {
        private final Class<?> collectionClass;
        private final Class<?> elementType;

        CollectionTypeInfo(Class<?> collectionClass, Class<?> elementType) {
            this.collectionClass = collectionClass;
            this.elementType = elementType;
        }
    }

    @Getter
    public static class MapTypeInfo {
        private final Class<?> keyType;
        private final Class<?> valueType;

        MapTypeInfo(Class<?> keyType, Class<?> valueType) {
            this.keyType = keyType;
            this.valueType = valueType;
        }
    }

    /**
     * Extracts collection type information from a {@link Type}, identifying both the collection class
     * and its element type.
     * This method analyzes parameterized types to determine if they represent a collection (e.g., {@code List},
     * {@code Set}, {@code Collection}) and extracts the generic element type.
     * The method handles:
     * <ul>
     *   <li>Parameterized collection types (e.g., {@code List<String>}, {@code Set<Service>})</li>
     *   <li>Wildcard types with upper bounds (e.g., {@code List<? extends Service>})</li>
     *   <li>Raw types and non-collection types return {@code null}</li>
     * </ul>
     * Examples:
     * <ul>
     *   <li>{@code extractCollectionTypeInfo(new TypeToken<List<String>>(){}.getType())} returns
     *       {@code CollectionTypeInfo} with collection class {@code List.class} and element type {@code String.class}</li>
     *   <li>{@code extractCollectionTypeInfo(new TypeToken<Set<Service>>(){}.getType())} returns
     *       {@code CollectionTypeInfo} with collection class {@code Set.class} and element type {@code Service.class}</li>
     *   <li>{@code extractCollectionTypeInfo(String.class)} returns {@code null} (not a collection)</li>
     *   <li>{@code extractCollectionTypeInfo(List.class)} returns {@code null} (raw type, no generics)</li>
     * </ul>
     *
     * @param type the type to analyze, not null
     * @return a {@link CollectionTypeInfo} containing the collection class and element type,
     * or {@code null} if the type is not a parameterized collection type
     * @throws NullPointerException if {@code type} is null
     */
    public static @Nullable CollectionTypeInfo extractCollectionTypeInfo(@NotNull Type type) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type rawType = parameterizedType.getRawType();

        if (!(rawType instanceof Class)) {
            return null;
        }

        Class<?> rawClass = (Class<?>) rawType;
        if (!Collection.class.isAssignableFrom(rawClass)) {
            return null;
        }

        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length == 0) {
            return null;
        }

        Type elementType = typeArguments[0];
        if (!(elementType instanceof Class)) {
            if (elementType instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) elementType;
                Type[] upperBounds = wildcardType.getUpperBounds();
                if (upperBounds.length > 0 && upperBounds[0] instanceof Class) {
                    elementType = upperBounds[0];
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        return new CollectionTypeInfo(rawClass, (Class<?>) elementType);
    }

    /**
     * Extracts the raw class from a {@link Type}, handling both simple classes and parameterized types.
     * This method is useful when you need to work with the actual class type, ignoring generic type parameters.
     * For parameterized types (e.g., {@code List<String>}), it returns the raw type (e.g., {@code List.class}).
     * For simple class types, it returns the class itself.
     * Examples:
     * <ul>
     *   <li>{@code getRawClass(String.class)} returns {@code String.class}</li>
     *   <li>{@code getRawClass(new TypeToken<List<String>>(){}.getType())} returns {@code List.class}</li>
     *   <li>{@code getRawClass(new TypeToken<Map<String, Integer>>(){}.getType())} returns {@code Map.class}</li>
     * </ul>
     *
     * @param type the type to extract the raw class from, not null
     * @param <T>  the type parameter for the returned class
     * @return the raw class, or {@code null} if the type cannot be converted to a class
     * @throws NullPointerException if {@code type} is null
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable Class<T> getRawClass(@NotNull Type type) {
        if (type instanceof Class) {
            return (Class<T>) type;
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class) {
                return (Class<T>) rawType;
            }
        }
        return null;
    }

    /**
     * Extracts map type information from a {@link Type}, identifying both the key and value types.
     * <p>
     * This method analyzes parameterized types to determine if they represent a {@link Map}
     * and extracts the generic key and value types.
     * <p>
     * The method handles:
     * <ul>
     *   <li>Parameterized map types (e.g., {@code Map<String, Location>})</li>
     *   <li>Wildcard types with upper bounds (e.g., {@code Map<String, ? extends Location>})</li>
     *   <li>Raw types and non-map types return {@code null}</li>
     * </ul>
     *
     * @param type the type to analyze, not null
     * @return a {@link MapTypeInfo} containing the key and value types,
     * or {@code null} if the type is not a parameterized map type
     * @throws NullPointerException if {@code type} is null
     */
    public static @Nullable MapTypeInfo extractMapTypeInfo(@NotNull Type type) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type rawType = parameterizedType.getRawType();

        if (!(rawType instanceof Class)) {
            return null;
        }

        Class<?> rawClass = (Class<?>) rawType;
        if (!Map.class.isAssignableFrom(rawClass)) {
            return null;
        }

        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length < 2) {
            return null;
        }

        Class<?> keyType = resolveTypeToClass(typeArguments[0]);
        Class<?> valueType = resolveTypeToClass(typeArguments[1]);

        if (keyType == null || valueType == null) {
            return null;
        }

        return new MapTypeInfo(keyType, valueType);
    }

    private static @Nullable Class<?> resolveTypeToClass(@NotNull Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0 && upperBounds[0] instanceof Class) {
                return (Class<?>) upperBounds[0];
            }
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class) {
                return (Class<?>) rawType;
            }
        }
        return null;
    }
}


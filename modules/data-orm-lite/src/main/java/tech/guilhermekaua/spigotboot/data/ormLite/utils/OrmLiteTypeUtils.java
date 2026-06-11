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
package tech.guilhermekaua.spigotboot.data.ormLite.utils;

import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.data.ormLite.repository.OrmLiteRepository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

public final class OrmLiteTypeUtils {

    /**
     * Resolves the concrete entity type (first type argument of {@link OrmLiteRepository})
     * from a class or interface that extends/implements OrmLiteRepository.
     * <p>
     * Walks the full type hierarchy, resolving type variables through intermediate
     * classes and interfaces.
     *
     * @param clazz the class/interface that implements OrmLiteRepository
     * @return the concrete entity class, or null if it cannot be resolved
     */
    public static @Nullable Class<?> resolveEntityType(Class<?> clazz) {
        Type[] typeArgs = findTypeArguments(clazz, OrmLiteRepository.class);
        if (typeArgs == null || typeArgs.length == 0) {
            return null;
        }

        return toConcreteClass(typeArgs[0]);
    }

    private static @Nullable Type[] findTypeArguments(Class<?> clazz, Class<?> targetClass) {
        if (clazz == null || clazz == Object.class) return null;

        for (Type genericInterface : clazz.getGenericInterfaces()) {
            Type[] result = extractFromType(genericInterface, targetClass);
            if (result != null) return result;
        }

        Type genericSuperclass = clazz.getGenericSuperclass();
        if (genericSuperclass != null) {
            Type[] result = extractFromType(genericSuperclass, targetClass);
            if (result != null) return result;
        }

        return null;
    }

    private static Type[] extractFromType(Type type, Class<?> targetClass) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> rawType = (Class<?>) pt.getRawType();

            if (rawType == targetClass) {
                return pt.getActualTypeArguments();
            }

            Type[] parentResult = findTypeArguments(rawType, targetClass);
            if (parentResult != null) {
                return substituteTypeVariables(parentResult, rawType, pt.getActualTypeArguments());
            }
        } else if (type instanceof Class) {
            return findTypeArguments((Class<?>) type, targetClass);
        }
        return null;
    }

    private static Type[] substituteTypeVariables(Type[] types, Class<?> declaringClass, Type[] actualArgs) {
        TypeVariable<?>[] typeParams = declaringClass.getTypeParameters();
        Map<String, Type> typeVarMap = new HashMap<>();
        for (int i = 0; i < typeParams.length && i < actualArgs.length; i++) {
            typeVarMap.put(typeParams[i].getName(), actualArgs[i]);
        }

        Type[] result = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] instanceof TypeVariable) {
                Type substitution = typeVarMap.get(((TypeVariable<?>) types[i]).getName());
                result[i] = substitution != null ? substitution : types[i];
            } else {
                result[i] = types[i];
            }
        }
        return result;
    }

    private static @Nullable Class<?> toConcreteClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        return null;
    }
}

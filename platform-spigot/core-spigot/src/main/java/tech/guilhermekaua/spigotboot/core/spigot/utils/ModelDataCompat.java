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

import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Version-safe item model data helpers for the Bukkit/Spigot API surface.
 */
public final class ModelDataCompat {

    private static final String NAMESPACED_KEY_CLASS = "org.bukkit.NamespacedKey";
    private static final Class<?> NO_ARGUMENT = NoArgument.class;

    private static final ConcurrentMap<String, Optional<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ConstructorKey, Optional<Constructor<?>>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<MethodKey, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

    private ModelDataCompat() {
    }

    /**
     * Sets the legacy durability/data value used by old pre-flattening item variants.
     *
     * @param item the item to mutate
     * @param data the data value, or null to leave it unchanged
     * @return true when the value was applied
     */
    @SuppressWarnings("deprecation")
    public static boolean setLegacyData(ItemStack item, Integer data) {
        if (item == null || data == null) {
            return false;
        }
        item.setDurability(data.shortValue());
        return true;
    }

    /**
     * Sets integer custom model data. On 1.21.5+ this writes the equivalent single float into the
     * custom model data component when that API is available; on 1.14-1.21.4 it falls back to
     * {@code ItemMeta#setCustomModelData(Integer)} reflectively.
     *
     * @param meta the meta to mutate
     * @param data the custom model data value, or null to leave it unchanged
     * @return true when a compatible API accepted the value
     */
    public static boolean setCustomModelData(ItemMeta meta, Integer data) {
        if (meta == null || data == null) {
            return false;
        }
        if (setCustomModelDataComponent(
                meta,
                Collections.singletonList(Float.valueOf(data.floatValue())),
                null,
                null,
                null
        )) {
            return true;
        }
        return invokeSingleArgumentMethod(meta, "setCustomModelData", data);
    }

    /**
     * Sets the custom model data component values introduced for the component-backed model data API.
     * Any null list is left unchanged on the component snapshot.
     *
     * @param meta    the meta to mutate
     * @param floats  range dispatch float values, or null to leave unchanged
     * @param flags   condition flag values, or null to leave unchanged
     * @param strings select string values, or null to leave unchanged
     * @param colors  tint colors, or null to leave unchanged
     * @return true when the component API is present and accepted the values
     */
    public static boolean setCustomModelDataComponent(
            ItemMeta meta,
            List<Float> floats,
            List<Boolean> flags,
            List<String> strings,
            List<Color> colors
    ) {
        if (meta == null || (floats == null && flags == null && strings == null && colors == null)) {
            return false;
        }

        Object component = invokeNoArgumentMethod(meta, "getCustomModelDataComponent");
        if (component == null) {
            return false;
        }

        if (!setComponentList(component, "setFloats", floats)) {
            return false;
        }
        if (!setComponentList(component, "setFlags", flags)) {
            return false;
        }
        if (!setComponentList(component, "setStrings", strings)) {
            return false;
        }
        if (!setComponentList(component, "setColors", colors)) {
            return false;
        }

        return invokeSingleArgumentMethod(meta, "setCustomModelDataComponent", component);
    }

    /**
     * Sets the direct item model key added by newer Spigot/Paper APIs.
     *
     * @param meta      the meta to mutate
     * @param itemModel a namespaced key such as {@code my_pack:bronze_sword}; keys without a
     *                  namespace use {@code minecraft}
     * @return true when the item model API is present and accepted the key
     */
    public static boolean setItemModel(ItemMeta meta, String itemModel) {
        if (meta == null || itemModel == null) {
            return false;
        }

        String key = itemModel.trim();
        if (key.isEmpty()) {
            return false;
        }

        Object namespacedKey = createNamespacedKey(key);
        if (namespacedKey == null) {
            return false;
        }

        return invokeSingleArgumentMethod(meta, "setItemModel", namespacedKey);
    }

    private static boolean setComponentList(Object component, String methodName, List<?> values) {
        if (values == null) {
            return true;
        }
        return invokeSingleArgumentMethod(component, methodName, new ArrayList<Object>(values));
    }

    private static Object createNamespacedKey(String rawKey) {
        Class<?> namespacedKeyClass = resolveClass(NAMESPACED_KEY_CLASS);
        if (namespacedKeyClass == null) {
            return null;
        }
        Object key = createNamespacedKeyWithFactory(namespacedKeyClass, rawKey);
        if (key != null) {
            return key;
        }
        return createNamespacedKeyWithConstructor(namespacedKeyClass, rawKey);
    }

    private static Object createNamespacedKeyWithConstructor(Class<?> namespacedKeyClass, String rawKey) {
        String[] parts = splitNamespacedKey(rawKey);
        if (parts == null) {
            return null;
        }
        Constructor<?> constructor = resolveConstructor(namespacedKeyClass, String.class, String.class);
        if (constructor == null) {
            return null;
        }
        try {
            return constructor.newInstance(parts[0], parts[1]);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object createNamespacedKeyWithFactory(Class<?> namespacedKeyClass, String rawKey) {
        Method fromString = resolveSingleArgumentMethod(namespacedKeyClass, "fromString", String.class);
        if (fromString == null || !Modifier.isStatic(fromString.getModifiers())) {
            return null;
        }
        try {
            return fromString.invoke(null, rawKey);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static String[] splitNamespacedKey(String rawKey) {
        int separatorIndex = rawKey.indexOf(':');
        if (separatorIndex < 0) {
            return new String[]{"minecraft", rawKey};
        }
        if (separatorIndex == 0 || separatorIndex == rawKey.length() - 1) {
            return null;
        }
        if (rawKey.indexOf(':', separatorIndex + 1) >= 0) {
            return null;
        }
        return new String[]{rawKey.substring(0, separatorIndex), rawKey.substring(separatorIndex + 1)};
    }

    private static Object invokeNoArgumentMethod(Object target, String methodName) {
        Method method = resolveNoArgumentMethod(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean invokeSingleArgumentMethod(Object target, String methodName, Object argument) {
        Method method = resolveSingleArgumentMethod(target.getClass(), methodName, argument == null ? null : argument.getClass());
        if (method == null) {
            return false;
        }
        try {
            method.invoke(target, argument);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return false;
        }
    }

    private static Class<?> resolveClass(String className) {
        Optional<Class<?>> cached = CLASS_CACHE.computeIfAbsent(className, ModelDataCompat::loadClass);
        return cached.orElse(null);
    }

    private static Optional<Class<?>> loadClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError ignored) {
            return Optional.empty();
        }
    }

    private static Constructor<?> resolveConstructor(Class<?> type, Class<?>... parameterTypes) {
        ConstructorKey key = new ConstructorKey(type, parameterTypes);
        Optional<Constructor<?>> cached = CONSTRUCTOR_CACHE.computeIfAbsent(key, ModelDataCompat::findConstructor);
        return cached.orElse(null);
    }

    private static Optional<Constructor<?>> findConstructor(ConstructorKey key) {
        try {
            return Optional.of(key.type.getConstructor(key.parameterTypes));
        } catch (NoSuchMethodException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static Method resolveNoArgumentMethod(Class<?> type, String methodName) {
        MethodKey key = new MethodKey(type, methodName, NO_ARGUMENT);
        Optional<Method> cached = METHOD_CACHE.computeIfAbsent(key, ModelDataCompat::findNoArgumentMethod);
        return cached.orElse(null);
    }

    private static Method resolveSingleArgumentMethod(Class<?> type, String methodName, Class<?> argumentType) {
        MethodKey key = new MethodKey(type, methodName, argumentType);
        Optional<Method> cached = METHOD_CACHE.computeIfAbsent(key, ModelDataCompat::findSingleArgumentMethod);
        return cached.orElse(null);
    }

    private static Optional<Method> findNoArgumentMethod(MethodKey key) {
        Method[] methods = key.type.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(key.name) && method.getParameterTypes().length == 0) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static Optional<Method> findSingleArgumentMethod(MethodKey key) {
        Method[] methods = key.type.getMethods();
        for (Method method : methods) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!method.getName().equals(key.name) || parameterTypes.length != 1) {
                continue;
            }
            if (isArgumentTypeCompatible(parameterTypes[0], key.argumentType)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static boolean isArgumentTypeCompatible(Class<?> parameterType, Class<?> argumentType) {
        if (argumentType == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(argumentType);
        }
        Class<?> wrapperType = primitiveWrapper(parameterType);
        return wrapperType != null && wrapperType.isAssignableFrom(argumentType);
    }

    private static Class<?> primitiveWrapper(Class<?> primitiveType) {
        if (primitiveType == Boolean.TYPE) {
            return Boolean.class;
        }
        if (primitiveType == Byte.TYPE) {
            return Byte.class;
        }
        if (primitiveType == Character.TYPE) {
            return Character.class;
        }
        if (primitiveType == Double.TYPE) {
            return Double.class;
        }
        if (primitiveType == Float.TYPE) {
            return Float.class;
        }
        if (primitiveType == Integer.TYPE) {
            return Integer.class;
        }
        if (primitiveType == Long.TYPE) {
            return Long.class;
        }
        if (primitiveType == Short.TYPE) {
            return Short.class;
        }
        return null;
    }

    private static final class MethodKey {
        private final Class<?> type;
        private final String name;
        private final Class<?> argumentType;

        private MethodKey(Class<?> type, String name, Class<?> argumentType) {
            this.type = type;
            this.name = name;
            this.argumentType = argumentType;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MethodKey)) {
                return false;
            }
            MethodKey other = (MethodKey) obj;
            return type.equals(other.type)
                    && name.equals(other.name)
                    && (argumentType == null ? other.argumentType == null : argumentType.equals(other.argumentType));
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + (argumentType == null ? 0 : argumentType.hashCode());
            return result;
        }
    }

    private static final class ConstructorKey {
        private final Class<?> type;
        private final Class<?>[] parameterTypes;

        private ConstructorKey(Class<?> type, Class<?>[] parameterTypes) {
            this.type = type;
            this.parameterTypes = parameterTypes.clone();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ConstructorKey)) {
                return false;
            }
            ConstructorKey other = (ConstructorKey) obj;
            return type.equals(other.type) && parameterTypesEqual(parameterTypes, other.parameterTypes);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            for (Class<?> parameterType : parameterTypes) {
                result = 31 * result + parameterType.hashCode();
            }
            return result;
        }
    }

    private static boolean parameterTypesEqual(Class<?>[] left, Class<?>[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!left[i].equals(right[i])) {
                return false;
            }
        }
        return true;
    }

    private static final class NoArgument {
    }
}

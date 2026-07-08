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

/**
 * Version-safe item model data helpers for the Bukkit/Spigot API surface.
 */
public final class ModelDataCompat {

    private static final String NAMESPACED_KEY_CLASS = "org.bukkit.NamespacedKey";

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
        try {
            Class<?> namespacedKeyClass = Class.forName(NAMESPACED_KEY_CLASS);
            Object key = createNamespacedKeyWithFactory(namespacedKeyClass, rawKey);
            if (key != null) {
                return key;
            }
            return createNamespacedKeyWithConstructor(namespacedKeyClass, rawKey);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object createNamespacedKeyWithConstructor(Class<?> namespacedKeyClass, String rawKey)
            throws ReflectiveOperationException {
        String[] parts = splitNamespacedKey(rawKey);
        if (parts == null) {
            return null;
        }
        Constructor<?> constructor = namespacedKeyClass.getConstructor(String.class, String.class);
        return constructor.newInstance(parts[0], parts[1]);
    }

    private static Object createNamespacedKeyWithFactory(Class<?> namespacedKeyClass, String rawKey)
            throws ReflectiveOperationException {
        try {
            Method fromString = namespacedKeyClass.getMethod("fromString", String.class);
            if (!Modifier.isStatic(fromString.getModifiers())) {
                return null;
            }
            return fromString.invoke(null, rawKey);
        } catch (NoSuchMethodException ignored) {
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
        Method method = findNoArgumentMethod(target.getClass(), methodName);
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
        Method method = findSingleArgumentMethod(target.getClass(), methodName, argument);
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

    private static Method findNoArgumentMethod(Class<?> type, String methodName) {
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName) && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        return null;
    }

    private static Method findSingleArgumentMethod(Class<?> type, String methodName, Object argument) {
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!method.getName().equals(methodName) || parameterTypes.length != 1) {
                continue;
            }
            if (isArgumentCompatible(parameterTypes[0], argument)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isArgumentCompatible(Class<?> parameterType, Object argument) {
        if (argument == null) {
            return !parameterType.isPrimitive();
        }
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(argument.getClass());
        }
        Class<?> wrapperType = primitiveWrapper(parameterType);
        return wrapperType != null && wrapperType.isInstance(argument);
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
}

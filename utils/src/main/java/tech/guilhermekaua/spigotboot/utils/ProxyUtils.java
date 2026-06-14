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
package tech.guilhermekaua.spigotboot.utils;

public final class ProxyUtils {
    // javassist marks every generated proxy with the ProxyObject interface. it is matched by name (not by a
    // compile-time class reference) so detection keeps working after the javassist package is relocated into
    // a downstream plugin jar (e.g. tech.guilhermekaua.spigotboot.shaded.javassist.util.proxy.ProxyObject).
    private static final String PROXY_OBJECT_CLASS_NAME = "javassist.util.proxy.ProxyObject";
    private static final String RELOCATED_PROXY_OBJECT_SUFFIX = "." + PROXY_OBJECT_CLASS_NAME;

    public static boolean isProxy(Object object) {
        try {
            Class<?> clazz = object.getClass();

            if (isJavassistProxy(clazz)) {
                return true;
            }

            ClassLoader classLoader = clazz.getClassLoader();
            String classLoaderName = classLoader.getClass().getName().toLowerCase();

            return classLoaderName.contains("mockbukkit");
        } catch (Throwable t) {
            return false;
        }
    }

    public static Class<?> unwrapProxyType(Class<?> type) {
        if (isJavassistProxy(type)) {
            return type.getSuperclass();
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> getRealClass(T object) {
        if (!isProxy(object)) {
            return (Class<T>) object.getClass();
        }

        return (Class<T>) object.getClass().getSuperclass();
    }

    // walks the type hierarchy looking for javassist's ProxyObject marker interface, matching by name so
    // both the original (javassist.util.proxy.ProxyObject) and the shaded/relocated name are recognized.
    private static boolean isJavassistProxy(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
                String name = iface.getName();
                if (name.equals(PROXY_OBJECT_CLASS_NAME) || name.endsWith(RELOCATED_PROXY_OBJECT_SUFFIX)) {
                    return true;
                }
            }
        }
        return false;
    }
}

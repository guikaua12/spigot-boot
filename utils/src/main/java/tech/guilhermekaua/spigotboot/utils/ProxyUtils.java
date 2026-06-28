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
    // spigot-boot marks every generated proxy with the SpigotBootProxy marker interface. it is matched by name
    // (not by a compile-time class reference) so detection keeps working after the package is relocated into
    // a downstream plugin jar (e.g. tech.guilhermekaua.spigotboot.shaded.core.proxy.SpigotBootProxy).
    private static final String PROXY_MARKER_CLASS_NAME = "tech.guilhermekaua.spigotboot.core.proxy.SpigotBootProxy";

    public static boolean isProxy(Object object) {
        try {
            Class<?> clazz = object.getClass();

            if (isSpigotBootProxy(clazz)) {
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
        Class<?> current = type;
        while (current != null && isSpigotBootProxy(current) && current.getSuperclass() != null) {
            current = current.getSuperclass();
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> getRealClass(T object) {
        return (Class<T>) unwrapProxyType(object.getClass());
    }

    // walks the type hierarchy looking for the SpigotBootProxy marker interface, matching by name so
    // both the original and the shaded/relocated name are recognized.
    private static boolean isSpigotBootProxy(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
                String name = iface.getName();
                if (name.equals(PROXY_MARKER_CLASS_NAME) || name.endsWith(".SpigotBootProxy")) {
                    return true;
                }
            }
        }
        return false;
    }
}

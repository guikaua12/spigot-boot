package me.approximations.apxPlugin.utils;

import javassist.util.proxy.ProxyObject;

public final class ProxyUtils {
    public static boolean isProxy(Object object) {
        try {
            Class<?> clazz = object.getClass();

            if (ProxyObject.class.isAssignableFrom(clazz)) {
                return true;
            }

            ClassLoader classLoader = clazz.getClassLoader();
            String classLoaderName = classLoader.getClass().getName().toLowerCase();

            return classLoaderName.contains("mockbukkit");
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> getRealClass(T object) {
        if (!isProxy(object)) {
            return (Class<T>) object.getClass();
        }

        return (Class<T>) object.getClass().getSuperclass();
    }
}

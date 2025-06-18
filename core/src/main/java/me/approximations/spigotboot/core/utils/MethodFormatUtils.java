package me.approximations.spigotboot.core.utils;

import java.lang.reflect.Method;

public final class MethodFormatUtils {
    public static String formatMethod(Method method) {
        return String.format("%s#%s", method.getDeclaringClass().getName(), method.getName());
    }
}

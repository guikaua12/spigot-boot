package me.approximations.apxPlugin.utils;

import java.lang.reflect.Method;

public final class MethodFormatUtils {
    public static String formatMethod(Method method) {
        return String.format("%s#%s", method.getDeclaringClass().getName(), method.getName());
    }
}

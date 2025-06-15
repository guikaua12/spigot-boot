package me.approximations.apxPlugin.utils;

import java.lang.reflect.Field;

public final class ReflectionUtils {
    public static void setFieldValue(Object object, String fieldName, Object value) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field value: " + fieldName, e);
        }
    }
}

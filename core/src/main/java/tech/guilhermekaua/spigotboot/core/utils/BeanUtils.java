package tech.guilhermekaua.spigotboot.core.utils;

import tech.guilhermekaua.spigotboot.core.context.annotations.Primary;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;

import java.lang.reflect.AnnotatedElement;

public final class BeanUtils {
    public static String getQualifier(AnnotatedElement element) {
        return element.isAnnotationPresent(Qualifier.class) ?
                element.getAnnotation(Qualifier.class).value() :
                null;
    }

    public static boolean getIsPrimary(AnnotatedElement element) {
        return element.isAnnotationPresent(Primary.class);
    }
}

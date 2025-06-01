package me.approximations.apxPlugin.core.context.component.proxy.methodHandler;

import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MethodHandlerRegistry {
    private static final List<MethodHandlerProcessResult> handlers = new ArrayList<>();

    private MethodHandlerRegistry() {
    }

    public static void registerAll(Map<Class<?>, List<MethodHandlerProcessResult>> map) {
        map.values().forEach(handlers::addAll);
    }

    public static List<MethodHandlerProcessResult> getHandlersFor(Class<?> target) {
        List<MethodHandlerProcessResult> result = new ArrayList<>();
        for (MethodHandlerProcessResult r : handlers) {
            if (r.getTargetClass().isAssignableFrom(target)) {
                result.add(r);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
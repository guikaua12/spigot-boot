package me.approximations.apxPlugin.core.context.component.proxy.methodHandler;

import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class MethodHandlerRegistry {
    private static final List<RegisteredMethodHandler> handlers = new ArrayList<>();

    private MethodHandlerRegistry() {
    }

    public static void registerAll(List<RegisteredMethodHandler> handlers) {
        MethodHandlerRegistry.handlers.addAll(handlers);
    }

    public static List<RegisteredMethodHandler> getHandlersFor(@NotNull MethodHandlerContext context) {
        return handlers.stream()
                .filter(handler -> handler.canHandle(context))
                .collect(Collectors.toList());
    }
}
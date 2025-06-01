package me.approximations.apxPlugin.core.context.component.proxy.methodHandler.processor;

import lombok.Data;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.MethodHandler;

@Data
public class MethodHandlerProcessResult {
    private final Class<?> targetClass;
    private final MethodHandler<?> handlerInstance;
}
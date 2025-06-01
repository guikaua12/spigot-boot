package me.approximations.apxPlugin.core.context.component.proxy.methodHandler;

import java.lang.reflect.Method;

public interface MethodHandler<T> {
    boolean canHandle(T self, Method thisMethod, Method proceed, Object[] args) throws Throwable;

    Object handle(T self, Method thisMethod, Method proceed, Object[] args) throws Throwable;
}

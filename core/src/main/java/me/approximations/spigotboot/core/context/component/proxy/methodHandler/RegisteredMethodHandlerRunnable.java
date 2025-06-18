package me.approximations.spigotboot.core.context.component.proxy.methodHandler;

import me.approximations.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;

public interface RegisteredMethodHandlerRunnable {
    Object handle(MethodHandlerContext context) throws Throwable;
}

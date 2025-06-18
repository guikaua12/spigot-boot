package me.approximations.spigotboot.core.context.component.proxy.methodHandler.context;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.lang.reflect.Method;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public class MethodHandlerContext {
    private final Object self;
    private final Method thisMethod;
    private final Method proceed;
    private final Object[] args;
}

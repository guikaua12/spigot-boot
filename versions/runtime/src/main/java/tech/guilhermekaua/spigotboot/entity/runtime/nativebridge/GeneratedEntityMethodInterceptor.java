/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.entity.runtime.nativebridge;

import javassist.util.proxy.MethodHandler;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Intercepts generated native entity methods and forwards them into the shared runtime lifecycle.
 *
 * @since 2.0.2
 */
public final class GeneratedEntityMethodInterceptor implements MethodHandler {
    private final Method tickMethod;
    private final List<Method> removalMethods;

    private NativeEntityLifecycle<?> lifecycle;

    /**
     * Creates a new method interceptor for a generated native entity subclass.
     *
     * @param tickMethod the native tick method to intercept
     * @param removalMethods the native removal methods to intercept
     */
    public GeneratedEntityMethodInterceptor(
            @NotNull Method tickMethod,
            @NotNull Collection<Method> removalMethods
    ) {
        this.tickMethod = Objects.requireNonNull(tickMethod, "tickMethod cannot be null");
        Objects.requireNonNull(removalMethods, "removalMethods cannot be null");
        this.removalMethods = new ArrayList<Method>(removalMethods);
    }

    /**
     * Binds the runtime lifecycle delegate to this interceptor instance.
     *
     * @param lifecycle the runtime lifecycle delegate
     */
    public void bindLifecycle(@NotNull NativeEntityLifecycle<?> lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (matches(thisMethod, tickMethod)) {
            Object result = invokeProceed(self, proceed, args);
            if (lifecycle != null) {
                lifecycle.onNativeTick();
            }
            return result;
        }

        if (matchesAny(thisMethod, removalMethods)) {
            try {
                return invokeProceed(self, proceed, args);
            } finally {
                if (lifecycle != null) {
                    lifecycle.onNativeRemove();
                }
            }
        }

        return invokeProceed(self, proceed, args);
    }

    private static Object invokeProceed(Object self, Method proceed, Object[] args) throws Throwable {
        Objects.requireNonNull(self, "self cannot be null");
        if (proceed == null) {
            throw new IllegalStateException("Could not resolve the intercepted superclass method.");
        }

        try {
            proceed.setAccessible(true);
            return proceed.invoke(self, args);
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }

    private static boolean matchesAny(@NotNull Method candidate, @NotNull Collection<Method> methods) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        Objects.requireNonNull(methods, "methods cannot be null");

        for (Method method : methods) {
            if (matches(candidate, method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(@NotNull Method candidate, @NotNull Method reference) {
        Objects.requireNonNull(candidate, "candidate cannot be null");
        Objects.requireNonNull(reference, "reference cannot be null");
        if (!candidate.getName().equals(reference.getName())) {
            return false;
        }
        if (!candidate.getReturnType().equals(reference.getReturnType())) {
            return false;
        }

        Class<?>[] candidateParameters = candidate.getParameterTypes();
        Class<?>[] referenceParameters = reference.getParameterTypes();
        if (candidateParameters.length != referenceParameters.length) {
            return false;
        }

        for (int index = 0; index < candidateParameters.length; index++) {
            if (!candidateParameters[index].equals(referenceParameters[index])) {
                return false;
            }
        }
        return true;
    }
}

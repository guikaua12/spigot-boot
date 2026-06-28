/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
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
package tech.guilhermekaua.spigotboot.core.context.component.proxy;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class ComponentProxy implements MethodHandler {
    private final Object realObject;

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> clazz, @Nullable Object realObject, Class<?>[] ctorArgs, Object[] ctorValues) {
        ProxyFactory factory = new ProxyFactory();
        if (clazz.isInterface()) {
            factory.setInterfaces(new Class<?>[]{clazz});
        } else {
            factory.setSuperclass(clazz);
        }

        try {
            ComponentProxy handler = new ComponentProxy(realObject);

            if (realObject == null || clazz.isInterface()) {
                return (T) factory.create(ctorArgs, ctorValues, handler);
            }

            Class<?> proxyClass = factory.createClass();
            Object proxy = allocateWithoutConstructor(proxyClass);
            ((ProxyObject) proxy).setHandler(handler);
            return (T) proxy;
        } catch (Throwable e) {
            throw new RuntimeException("Proxy creation failed for " + clazz.getName(), e);
        }
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        boolean delegating = realObject != null;
        Object invocationTarget = delegating ? realObject : self;
        Method invokeMethod = delegating
                ? resolveInvokeMethod(invocationTarget, thisMethod, proceed)
                : proceed;

        if (thisMethod.getName().equals("toString")) {
            return invocationTarget.getClass().getSimpleName() + "@" + Integer.toHexString(invocationTarget.hashCode());
        }

        final MethodHandlerContext context = new MethodHandlerContext(invocationTarget, thisMethod, invokeMethod, args);

        List<RegisteredMethodHandler> handlers = MethodHandlerRegistry.getHandlersFor(context);
        if (handlers.isEmpty()) {
            return invokeTerminal(self, delegating, invocationTarget, thisMethod, proceed, invokeMethod, args);
        }

        try {
            Object result = invokeHandlerChain(
                    handlers,
                    0,
                    self,
                    delegating,
                    invocationTarget,
                    thisMethod,
                    proceed,
                    invokeMethod,
                    args
            );
            return normalizeResult(self, delegating, invocationTarget, result);
        } catch (Throwable t) {
            throw new RuntimeException("Error handling method " + thisMethod.getName() + " in " + invocationTarget.getClass().getName(), t);
        }
    }

    private Object invokeHandlerChain(List<RegisteredMethodHandler> handlers,
                                      int handlerIndex,
                                      Object self,
                                      boolean delegating,
                                      Object invocationTarget,
                                      Method thisMethod,
                                      Method proceed,
                                      Method invokeMethod,
                                      Object[] args) throws Throwable {
        if (handlerIndex >= handlers.size()) {
            return invokeTerminal(self, delegating, invocationTarget, thisMethod, proceed, invokeMethod, args);
        }

        RegisteredMethodHandler handler = handlers.get(handlerIndex);
        MethodHandlerContext chainedContext = new MethodHandlerContext(
                invocationTarget,
                thisMethod,
                invokeMethod,
                args,
                () -> invokeHandlerChain(handlers, handlerIndex + 1, self, delegating, invocationTarget, thisMethod, proceed, invokeMethod, args)
        );
        return handler.getRunnable().handle(chainedContext);
    }

    private Object invokeTerminal(Object self,
                                  boolean delegating,
                                  Object invocationTarget,
                                  Method thisMethod,
                                  Method proceed,
                                  Method invokeMethod,
                                  Object[] args) throws Throwable {
        if (!delegating) {
            if (proceed == null) {
                throw new IllegalStateException("No proceed method available for: " + thisMethod);
            }

            proceed.setAccessible(true);
            return proceed.invoke(self, args);
        }

        if (invokeMethod == null) {
            throw new IllegalStateException("No target method available for: " + thisMethod);
        }

        invokeMethod.setAccessible(true);
        Object result = invokeMethod.invoke(invocationTarget, args);
        return normalizeResult(self, delegating, invocationTarget, result);
    }

    private Object normalizeResult(Object self, boolean delegating, Object invocationTarget, Object result) {
        if (delegating && result != null && (result == invocationTarget || result == realObject)) {
            return self;
        }

        return result;
    }

    private Method resolveInvokeMethod(Object invocationTarget, Method thisMethod, Method proceed) {
        if (proceed != null && invocationTarget != null && proceed.getDeclaringClass().isInstance(invocationTarget)) {
            return proceed;
        }

        if (invocationTarget == null) {
            return proceed;
        }

        Method signatureSource = thisMethod != null ? thisMethod : proceed;
        if (signatureSource == null) {
            return null;
        }

        Method resolved = findMethod(
                invocationTarget.getClass(),
                signatureSource.getName(),
                signatureSource.getParameterTypes()
        );
        if (resolved != null) {
            return resolved;
        }

        if (realObject != null) {
            return thisMethod;
        }

        return proceed;
    }

    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
        }

        try {
            return clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object allocateWithoutConstructor(Class<?> proxyClass) throws ReflectiveOperationException {
        Objects.requireNonNull(proxyClass, "proxyClass cannot be null");

        Class<?> unsafeClass = null;
        Field theUnsafe = null;
        Method allocateInstance = null;
        Object unsafe = null;
        ReflectiveOperationException unsafeFailure = null;

        try {
            unsafeClass = Class.forName("sun.misc.Unsafe");
        } catch (ClassNotFoundException e) {
            unsafeFailure = e;
        }

        if (unsafeClass != null) {
            try {
                theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                unsafe = theUnsafe.get(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                unsafeFailure = new ReflectiveOperationException("unable to access theUnsafe from unsafeClass", e);
            }
        }

        if (unsafeClass != null && unsafe != null) {
            try {
                allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            } catch (NoSuchMethodException | RuntimeException e) {
                unsafeFailure = new ReflectiveOperationException("unable to resolve allocateInstance from unsafeClass", e);
            }
        }

        if (unsafeClass != null && unsafe != null && allocateInstance != null) {
            try {
                return allocateInstance.invoke(unsafe, proxyClass);
            } catch (ReflectiveOperationException | RuntimeException e) {
                unsafeFailure = new ReflectiveOperationException("unsafe allocateInstance invocation failed for proxyClass " + proxyClass.getName(), e);
            }
        }

        ReflectiveOperationException constructorFailure = null;
        try {
            Constructor<?> constructor = proxyClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            constructorFailure = new ReflectiveOperationException(
                    "constructor fallback allocation failed for proxyClass " + proxyClass.getName(),
                    e
            );
        }

        ReflectiveOperationException allocationFailure = new ReflectiveOperationException(
                "unable to allocate proxy instance for proxyClass " + proxyClass.getName()
                        + "; Unsafe allocation failed and constructor fallback could not be used"
                        + " (unsafeClass=" + (unsafeClass == null ? "unavailable" : unsafeClass.getName())
                        + ", theUnsafe=" + (theUnsafe == null ? "unavailable" : "resolved")
                        + ", allocateInstance=" + (allocateInstance == null ? "unavailable" : "resolved") + ")"
        );
        if (unsafeFailure != null) {
            allocationFailure.addSuppressed(unsafeFailure);
        }
        if (constructorFailure != null) {
            allocationFailure.addSuppressed(constructorFailure);
        }
        throw allocationFailure;
    }
}

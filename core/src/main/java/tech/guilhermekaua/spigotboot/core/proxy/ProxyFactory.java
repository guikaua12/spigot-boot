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
package tech.guilhermekaua.spigotboot.core.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates and caches proxy subclasses at runtime using {@link ProxyGenerator}.
 * Supports non-final, non-primitive, non-array class targets.
 */
public final class ProxyFactory {

    private static final ConcurrentHashMap<Class<?>, Class<?>> CLASS_CACHE = new ConcurrentHashMap<Class<?>, Class<?>>();
    private static final AtomicLong COUNTER = new AtomicLong();

    private static final Object UNSAFE;
    private static final Method ALLOCATE_INSTANCE;
    private static final Method UNSAFE_DEFINE_CLASS;

    static {
        Object u = null;
        Method ai = null;
        Method dc = null;
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            u = theUnsafe.get(null);
            ai = unsafeClass.getMethod("allocateInstance", Class.class);
            dc = unsafeClass.getMethod("defineClass",
                    String.class, byte[].class, int.class, int.class,
                    ClassLoader.class, ProtectionDomain.class);
        } catch (Exception ignored) {
        }
        UNSAFE = u;
        ALLOCATE_INSTANCE = ai;
        UNSAFE_DEFINE_CLASS = dc;
    }

    /**
     * @param target the class to proxy (must not be final, primitive, or array)
     * @return the generated proxy subclass, cached for subsequent calls
     * @throws IllegalArgumentException if the target cannot be proxied
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<? extends T> createProxyClass(Class<T> target) {
        Objects.requireNonNull(target, "target cannot be null");
        if (target.isPrimitive() || target.isArray() || Modifier.isFinal(target.getModifiers())) {
            throw new IllegalArgumentException("target cannot be proxied: " + target.getName());
        }
        return (Class<? extends T>) CLASS_CACHE.computeIfAbsent(target, ProxyFactory::generateAndDefine);
    }

    /**
     * @param target        the class to proxy
     * @param ctorArgTypes  constructor parameter types, or {@code null}/{@code empty} for no-arg
     * @param ctorArgValues constructor argument values matching {@code ctorArgTypes}
     * @param handler       the interceptor (must not be null)
     * @return a new proxy instance with the handler installed
     * @throws RuntimeException if constructor invocation fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> target, Class<?>[] ctorArgTypes,
                                    Object[] ctorArgValues, MethodInterceptor handler) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");

        Class<? extends T> proxyClass = createProxyClass(target);

        try {
            T proxy;
            if (ctorArgTypes == null || ctorArgTypes.length == 0) {
                Constructor<? extends T> ctor = proxyClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                proxy = ctor.newInstance();
            } else {
                Constructor<? extends T> ctor = proxyClass.getDeclaredConstructor(ctorArgTypes);
                ctor.setAccessible(true);
                proxy = ctor.newInstance(ctorArgValues);
            }

            ((SpigotBootProxy) proxy).setHandler(handler);
            return proxy;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create proxy for " + target.getName(), e);
        }
    }

    /**
     * Allocates a proxy instance without calling any constructor (via {@code sun.misc.Unsafe}).
     *
     * @param proxyClass a proxy class returned by {@link #createProxyClass}
     * @return an uninitialized instance
     * @throws RuntimeException if allocation fails
     */
    public static Object allocateWithoutConstructor(Class<?> proxyClass) {
        Objects.requireNonNull(proxyClass, "proxyClass cannot be null");

        if (UNSAFE != null && ALLOCATE_INSTANCE != null) {
            try {
                return ALLOCATE_INSTANCE.invoke(UNSAFE, proxyClass);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("allocateInstance failed for " + proxyClass.getName(), e);
            }
        }

        try {
            Constructor<?> constructor = proxyClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("unable to allocate proxy instance for " + proxyClass.getName()
                    + " (Unsafe unavailable, no accessible no-arg constructor)", e);
        }
    }

    // ================================================================

    private static Class<?> generateAndDefine(Class<?> target) {
        String targetInternal = target.getName().replace('.', '/');
        String proxyInternal = targetInternal + "$$SBProxy" + COUNTER.getAndIncrement();
        byte[] bytecode = ProxyGenerator.generate(proxyInternal, target);
        String proxyClassName = proxyInternal.replace('/', '.');
        return defineClass(proxyClassName, bytecode, target.getClassLoader(), target);
    }

    private static Class<?> defineClass(String name, byte[] bytecode, ClassLoader loader, Class<?> neighbor) {
        if (UNSAFE != null && UNSAFE_DEFINE_CLASS != null) {
            try {
                return (Class<?>) UNSAFE_DEFINE_CLASS.invoke(UNSAFE, name, bytecode, 0, bytecode.length,
                        loader, neighbor.getProtectionDomain());
            } catch (Exception ignored) {
                // fall through
            }
        }

        // fallback: MethodHandles.Lookup.defineClass (Java 9+)
        // the lookup must target the neighbor class so the proxy is defined in the same package/module
        try {
            Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");
            Class<?> mhClass = Class.forName("java.lang.invoke.MethodHandles");
            Method privateLookupIn = mhClass.getMethod("privateLookupIn", Class.class, lookupClass);
            Method lookup = mhClass.getMethod("lookup");
            Object theLookup = lookup.invoke(null);
            Object targetLookup = privateLookupIn.invoke(null, neighbor, theLookup);
            Method defineClassMethod = lookupClass.getMethod("defineClass", byte[].class);
            return (Class<?>) defineClassMethod.invoke(targetLookup, (Object) bytecode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to define proxy class " + name
                    + ": neither Unsafe nor MethodHandles could load it", e);
        }
    }
}

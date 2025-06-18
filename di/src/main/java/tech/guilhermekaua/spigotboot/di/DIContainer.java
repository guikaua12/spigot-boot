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
package tech.guilhermekaua.spigotboot.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class DIContainer {
    private final Map<Class<?>, Class<?>> typeMappings = new HashMap<>();
    private final Map<Class<?>, Object> singletons = new HashMap<>();

    public <T> void register(Class<T> baseType, Class<? extends T> implType) {
        typeMappings.put(baseType, implType);
    }

    public <T> T resolve(Class<T> type) {
        try {
            if (singletons.containsKey(type)) {
                return type.cast(singletons.get(type));
            }
            Class<?> implType = typeMappings.getOrDefault(type, type);
            T instance = (T) createInstance(implType);
            singletons.put(type, instance);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve type: " + type, e);
        }
    }

    private <T> T createInstance(Class<T> type) throws Exception {
        // constructor injection
        Constructor<?> injectCtor = null;
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (ctor.isAnnotationPresent(Inject.class)) {
                injectCtor = ctor;
                break;
            }
        }
        if (injectCtor != null) {
            return instantiateWithConstructor(injectCtor, type);
        }

        Constructor<?>[] ctors = type.getDeclaredConstructors();
        Constructor<?> selectedCtor = null;
        int maxParams = -1;
        for (Constructor<?> ctor : ctors) {
            int paramCount = ctor.getParameterCount();
            if (paramCount > maxParams) {
                maxParams = paramCount;
                selectedCtor = ctor;
            }
        }
        if (selectedCtor != null && maxParams > 0) {
            return instantiateWithConstructor(selectedCtor, type);
        }

        Constructor<T> ctor = type.getDeclaredConstructor();
        T instance = ctor.newInstance();

        // setter injection
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Inject.class) && method.getParameterCount() == 1) {
                Class<?> depType = method.getParameterTypes()[0];
                Object dep = resolve(depType);
                method.setAccessible(true);
                method.invoke(instance, dep);
            }
        }
        // field injection
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                Object dep = resolve(field.getType());
                field.setAccessible(true);
                field.set(instance, dep);
            }
        }
        return instance;
    }

    private <T> T instantiateWithConstructor(Constructor<?> ctor, Class<T> type) throws Exception {
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = resolve(paramTypes[i]);
        }
        ctor.setAccessible(true);
        T instance = type.cast(ctor.newInstance(params));
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Inject.class) && method.getParameterCount() == 1) {
                Object dep = resolve(method.getParameterTypes()[0]);
                method.setAccessible(true);
                method.invoke(instance, dep);
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                Object dep = resolve(field.getType());
                field.setAccessible(true);
                field.set(instance, dep);
            }
        }
        return instance;
    }
}


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

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generates concrete native subclasses that delegate real lifecycle callbacks into the runtime bridge.
 *
 * @since 2.0.2
 */
public final class GeneratedNativeEntityClassFactory {
    private static final String GENERATED_PACKAGE_NAME = GeneratedNativeEntityClassFactory.class.getPackage().getName();
    private static final String LIFECYCLE_FIELD_NAME = "spigotBoot$lifecycle";
    private static final String LIFECYCLE_BINDING_METHOD_NAME = "spigotBootBindLifecycle";

    /**
     * Generates a subclass of the supplied native entity type.
     *
     * @param superclass the native superclass to extend
     * @param generatedName the generated class name
     * @param tickMethod the native tick method to override
     * @param removalMethods the removal methods to override
     * @return the generated subclass
     */
    public @NotNull Class<?> createSubclass(
            @NotNull Class<?> superclass,
            @NotNull String generatedName,
            @NotNull Method tickMethod,
            @NotNull Collection<Method> removalMethods
    ) {
        Objects.requireNonNull(superclass, "superclass cannot be null");
        Objects.requireNonNull(generatedName, "generatedName cannot be null");
        Objects.requireNonNull(tickMethod, "tickMethod cannot be null");
        Objects.requireNonNull(removalMethods, "removalMethods cannot be null");
        String effectiveGeneratedName = resolveGeneratedName(generatedName);

        ClassPool classPool = new ClassPool(false);
        classPool.appendSystemPath();
        appendLoaderClassPath(classPool, superclass.getClassLoader());
        appendLoaderClassPath(classPool, GeneratedNativeEntityClassFactory.class.getClassLoader());

        CtClass generatedType = classPool.makeClass(effectiveGeneratedName);
        try {
            generatedType.setSuperclass(toCtClass(classPool, superclass));
            addLifecycleField(classPool, generatedType);
            addConstructors(classPool, generatedType, superclass);
            addLifecycleBindingMethod(classPool, generatedType);
            addInterceptedMethod(classPool, generatedType, tickMethod, LifecycleCallback.TICK);

            Map<String, Method> uniqueRemovalMethods = new LinkedHashMap<String, Method>();
            for (Method removalMethod : removalMethods) {
                uniqueRemovalMethods.put(methodKey(removalMethod), removalMethod);
            }
            for (Method removalMethod : uniqueRemovalMethods.values()) {
                addInterceptedMethod(classPool, generatedType, removalMethod, LifecycleCallback.REMOVE);
            }

            return classPool.toClass(
                    generatedType,
                    GeneratedNativeEntityClassFactory.class,
                    GeneratedNativeEntityClassFactory.class.getClassLoader(),
                    GeneratedNativeEntityClassFactory.class.getProtectionDomain()
            );
        } catch (CannotCompileException exception) {
            throw new IllegalStateException(
                    "Could not generate the native entity subclass '" + effectiveGeneratedName + "'.",
                    exception
            );
        } catch (NotFoundException exception) {
            throw new IllegalStateException(
                    "Could not resolve the bytecode model for '" + superclass.getName() + "'.",
                    exception
            );
        } finally {
            generatedType.detach();
        }
    }

    /**
     * Validates that the generated native entity exposes the lifecycle bridge installed at generation time.
     *
     * @param generatedEntity the generated native entity instance
     * @param tickMethod the native tick method to intercept
     * @param removalMethods the native removal methods to intercept
     */
    public void installInterceptor(
            @NotNull Object generatedEntity,
            @NotNull Method tickMethod,
            @NotNull Collection<Method> removalMethods
    ) {
        Objects.requireNonNull(generatedEntity, "generatedEntity cannot be null");
        Objects.requireNonNull(tickMethod, "tickMethod cannot be null");
        Objects.requireNonNull(removalMethods, "removalMethods cannot be null");
        requireLifecycleBindingMethod(generatedEntity.getClass());
    }

    /**
     * Binds the runtime lifecycle delegate to a generated native entity instance.
     *
     * @param generatedEntity the generated native entity instance
     * @param lifecycle the runtime lifecycle delegate
     */
    public void bindLifecycle(@NotNull Object generatedEntity, @NotNull NativeEntityLifecycle<?> lifecycle) {
        Objects.requireNonNull(generatedEntity, "generatedEntity cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        Method bindingMethod = requireLifecycleBindingMethod(generatedEntity.getClass());
        try {
            bindingMethod.setAccessible(true);
            bindingMethod.invoke(generatedEntity, lifecycle);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not bind the runtime lifecycle to '" + generatedEntity.getClass().getName() + "'.",
                    exception
            );
        }
    }

    private static void appendLoaderClassPath(@NotNull ClassPool classPool, ClassLoader classLoader) {
        if (classLoader != null) {
            classPool.appendClassPath(new LoaderClassPath(classLoader));
        }
    }

    private static void addLifecycleField(@NotNull ClassPool classPool, @NotNull CtClass generatedType)
            throws CannotCompileException, NotFoundException {
        CtField lifecycleField = new CtField(
                toCtClass(classPool, NativeEntityLifecycle.class),
                LIFECYCLE_FIELD_NAME,
                generatedType
        );
        lifecycleField.setModifiers(Modifier.PRIVATE);
        generatedType.addField(lifecycleField);
    }

    private static void addConstructors(
            @NotNull ClassPool classPool,
            @NotNull CtClass generatedType,
            @NotNull Class<?> superclass
    ) throws CannotCompileException, NotFoundException {
        boolean constructorAdded = false;

        for (Constructor<?> constructor : superclass.getDeclaredConstructors()) {
            if (Modifier.isPrivate(constructor.getModifiers())) {
                continue;
            }

            CtConstructor generatedConstructor = new CtConstructor(
                    toCtClasses(classPool, constructor.getParameterTypes()),
                    generatedType
            );
            generatedConstructor.setModifiers(constructor.getModifiers());
            generatedConstructor.setExceptionTypes(toCtClasses(classPool, constructor.getExceptionTypes()));
            generatedConstructor.setBody("{ super($$); }");
            generatedType.addConstructor(generatedConstructor);
            constructorAdded = true;
        }

        if (!constructorAdded) {
            throw new IllegalStateException(
                    "Could not generate a usable constructor bridge for '" + superclass.getName() + "'."
            );
        }
    }

    private static void addLifecycleBindingMethod(@NotNull ClassPool classPool, @NotNull CtClass generatedType)
            throws CannotCompileException, NotFoundException {
        CtMethod bindingMethod = new CtMethod(
                CtClass.voidType,
                LIFECYCLE_BINDING_METHOD_NAME,
                new CtClass[]{toCtClass(classPool, NativeEntityLifecycle.class)},
                generatedType
        );
        bindingMethod.setModifiers(Modifier.PUBLIC);
        bindingMethod.setBody("{ this." + LIFECYCLE_FIELD_NAME + " = $1; }");
        generatedType.addMethod(bindingMethod);
    }

    private static void addInterceptedMethod(
            @NotNull ClassPool classPool,
            @NotNull CtClass generatedType,
            @NotNull Method method,
            @NotNull LifecycleCallback callback
    ) throws CannotCompileException, NotFoundException {
        Objects.requireNonNull(method, "method cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");

        CtMethod generatedMethod = new CtMethod(
                toCtClass(classPool, method.getReturnType()),
                method.getName(),
                toCtClasses(classPool, method.getParameterTypes()),
                generatedType
        );

        int modifiers = method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.NATIVE;
        generatedMethod.setModifiers(modifiers);
        generatedMethod.setExceptionTypes(toCtClasses(classPool, method.getExceptionTypes()));
        generatedMethod.setBody(buildMethodBody(method, callback));
        generatedType.addMethod(generatedMethod);
    }

    private static @NotNull Method requireLifecycleBindingMethod(@NotNull Class<?> generatedType) {
        Objects.requireNonNull(generatedType, "generatedType cannot be null");
        try {
            return generatedType.getDeclaredMethod(LIFECYCLE_BINDING_METHOD_NAME, NativeEntityLifecycle.class);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException(
                    "The generated entity does not expose the expected lifecycle bridge: " + generatedType.getName(),
                    exception
            );
        }
    }

    private static @NotNull String buildMethodBody(@NotNull Method method, @NotNull LifecycleCallback callback) {
        String lifecycleInvocation = "if (this." + LIFECYCLE_FIELD_NAME + " != null) { "
                + "this." + LIFECYCLE_FIELD_NAME + "." + callback.methodName + "(); }";

        if (callback == LifecycleCallback.TICK) {
            if (method.getReturnType() == void.class) {
                return "{ super." + method.getName() + "($$); " + lifecycleInvocation + " }";
            }
            return "{ "
                    + sourceTypeName(method.getReturnType()) + " result = super." + method.getName() + "($$); "
                    + lifecycleInvocation
                    + " return result; }";
        }

        if (method.getReturnType() == void.class) {
            return "{ try { super." + method.getName() + "($$); } finally { " + lifecycleInvocation + " } }";
        }

        return "{ try { return super." + method.getName() + "($$); } finally { " + lifecycleInvocation + " } }";
    }

    private static @NotNull CtClass[] toCtClasses(@NotNull ClassPool classPool, @NotNull Class<?>[] types)
            throws NotFoundException {
        CtClass[] ctClasses = new CtClass[types.length];
        for (int index = 0; index < types.length; index++) {
            ctClasses[index] = toCtClass(classPool, types[index]);
        }
        return ctClasses;
    }

    private static @NotNull CtClass toCtClass(@NotNull ClassPool classPool, @NotNull Class<?> type)
            throws NotFoundException {
        Objects.requireNonNull(classPool, "classPool cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (type == void.class) {
            return CtClass.voidType;
        }
        if (type == boolean.class) {
            return CtClass.booleanType;
        }
        if (type == byte.class) {
            return CtClass.byteType;
        }
        if (type == char.class) {
            return CtClass.charType;
        }
        if (type == short.class) {
            return CtClass.shortType;
        }
        if (type == int.class) {
            return CtClass.intType;
        }
        if (type == long.class) {
            return CtClass.longType;
        }
        if (type == float.class) {
            return CtClass.floatType;
        }
        if (type == double.class) {
            return CtClass.doubleType;
        }

        String typeName = type.isArray() ? type.getCanonicalName() : type.getName();
        return classPool.get(typeName);
    }

    private static @NotNull String sourceTypeName(@NotNull Class<?> type) {
        Objects.requireNonNull(type, "type cannot be null");
        String canonicalName = type.getCanonicalName();
        return canonicalName != null ? canonicalName : type.getName();
    }

    private static @NotNull String methodKey(@NotNull Method method) {
        Objects.requireNonNull(method, "method cannot be null");
        StringBuilder builder = new StringBuilder(method.getName());
        builder.append('#').append(method.getReturnType().getName());
        for (Class<?> parameterType : method.getParameterTypes()) {
            builder.append(':').append(parameterType.getName());
        }
        return builder.toString();
    }

    private static @NotNull String resolveGeneratedName(@NotNull String requestedName) {
        Objects.requireNonNull(requestedName, "requestedName cannot be null");
        int separatorIndex = requestedName.lastIndexOf('.');
        String simpleName = separatorIndex >= 0
                ? requestedName.substring(separatorIndex + 1)
                : requestedName;
        return GENERATED_PACKAGE_NAME + "." + simpleName;
    }

    private enum LifecycleCallback {
        TICK("onNativeTick"),
        REMOVE("onNativeRemove");

        private final String methodName;

        LifecycleCallback(String methodName) {
            this.methodName = methodName;
        }
    }
}

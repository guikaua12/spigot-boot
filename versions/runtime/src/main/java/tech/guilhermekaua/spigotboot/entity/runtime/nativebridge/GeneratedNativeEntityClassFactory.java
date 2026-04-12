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
import tech.guilhermekaua.spigotboot.entity.api.spi.LifecycleAwareNativeEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generates concrete native subclasses that delegate real native hooks into the shared runtime bridge.
 *
 * @since 2.0.2
 */
public final class GeneratedNativeEntityClassFactory {
    private static final String GENERATED_PACKAGE_NAME = GeneratedNativeEntityClassFactory.class.getPackage().getName();
    private static final String LIFECYCLE_FIELD_NAME = "spigotBoot$lifecycle";
    private static final String LIFECYCLE_BINDING_METHOD_NAME = "spigotBootBindLifecycle";
    private static final String LIFECYCLE_ACCESSOR_METHOD_NAME = "spigotBootGetLifecycle";
    private static final String BASE_INVOKER_METHOD_NAME = "spigotBootInvokeBase";
    private static final String BASE_BRIDGE_METHOD_PREFIX = "spigotBoot$base$";

    /**
     * Generates a subclass of the supplied native entity type.
     *
     * @param superclass the native superclass to extend
     * @param generatedName the generated class name
     * @param hookSpecs the native hook catalog to override
     * @return the generated subclass
     */
    public @NotNull Class<?> createSubclass(
            @NotNull Class<?> superclass,
            @NotNull String generatedName,
            @NotNull Collection<GeneratedNativeHookSpec> hookSpecs
    ) {
        Objects.requireNonNull(superclass, "superclass cannot be null");
        Objects.requireNonNull(generatedName, "generatedName cannot be null");
        Objects.requireNonNull(hookSpecs, "hookSpecs cannot be null");
        if (hookSpecs.isEmpty()) {
            throw new IllegalArgumentException("At least one hook spec is required.");
        }

        Collection<GeneratedNativeHookSpec> uniqueHookSpecs = uniqueHookSpecs(hookSpecs);
        String effectiveGeneratedName = resolveGeneratedName(generatedName);

        ClassPool classPool = new ClassPool(false);
        classPool.appendSystemPath();
        appendLoaderClassPath(classPool, superclass.getClassLoader());
        appendLoaderClassPath(classPool, GeneratedNativeEntityClassFactory.class.getClassLoader());

        CtClass generatedType = classPool.makeClass(effectiveGeneratedName);
        try {
            generatedType.setSuperclass(toCtClass(classPool, superclass));
            generatedType.addInterface(toCtClass(classPool, LifecycleAwareNativeEntity.class));

            addLifecycleField(classPool, generatedType);
            addConstructors(classPool, generatedType, superclass);
            addLifecycleBindingMethod(classPool, generatedType);
            addLifecycleAccessorMethod(classPool, generatedType);

            Collection<GeneratedHookBridge> bridges = new ArrayList<GeneratedHookBridge>();
            int bridgeIndex = 0;
            for (GeneratedNativeHookSpec hookSpec : uniqueHookSpecs) {
                Method method = hookSpec.method();
                validateOverridableMethod(method);

                String bridgeMethodName = BASE_BRIDGE_METHOD_PREFIX + bridgeIndex++;
                addBaseBridgeMethod(classPool, generatedType, method, bridgeMethodName);
                addInterceptedMethod(classPool, generatedType, hookSpec, bridgeMethodName);
                bridges.add(new GeneratedHookBridge(hookSpec, bridgeMethodName));
            }

            addBaseInvokerMethod(classPool, generatedType, bridges);

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
     * Validates that the generated native entity exposes the runtime bridge installed at generation time.
     *
     * @param generatedEntity the generated native entity instance
     * @param hookSpecs the hook specs used during generation
     */
    public void installInterceptor(
            @NotNull Object generatedEntity,
            @NotNull Collection<GeneratedNativeHookSpec> hookSpecs
    ) {
        Objects.requireNonNull(generatedEntity, "generatedEntity cannot be null");
        Objects.requireNonNull(hookSpecs, "hookSpecs cannot be null");
        if (hookSpecs.isEmpty()) {
            throw new IllegalArgumentException("At least one hook spec is required.");
        }

        Class<?> generatedType = generatedEntity.getClass();
        requireLifecycleBindingMethod(generatedType);
        requireLifecycleAccessorMethod(generatedType);
        requireBaseInvokerMethod(generatedType);
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

        if (generatedEntity instanceof LifecycleAwareNativeEntity) {
            ((LifecycleAwareNativeEntity) generatedEntity).spigotBootBindLifecycle(lifecycle);
            return;
        }

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

    private static @NotNull Collection<GeneratedNativeHookSpec> uniqueHookSpecs(
            @NotNull Collection<GeneratedNativeHookSpec> hookSpecs
    ) {
        Map<String, GeneratedNativeHookSpec> uniqueHookSpecs = new LinkedHashMap<String, GeneratedNativeHookSpec>();
        for (GeneratedNativeHookSpec hookSpec : hookSpecs) {
            Objects.requireNonNull(hookSpec, "hookSpecs cannot contain null elements");
            uniqueHookSpecs.put(methodKey(hookSpec.method()), hookSpec);
        }
        return uniqueHookSpecs.values();
    }

    private static void validateOverridableMethod(@NotNull Method method) {
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers)) {
            throw new IllegalArgumentException(
                    "Cannot generate a hook bridge for non-overridable method '" + method.toGenericString() + "'."
            );
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

    private static void addLifecycleAccessorMethod(@NotNull ClassPool classPool, @NotNull CtClass generatedType)
            throws CannotCompileException, NotFoundException {
        CtMethod accessorMethod = new CtMethod(
                toCtClass(classPool, NativeEntityLifecycle.class),
                LIFECYCLE_ACCESSOR_METHOD_NAME,
                new CtClass[0],
                generatedType
        );
        accessorMethod.setModifiers(Modifier.PUBLIC);
        accessorMethod.setBody("{ return this." + LIFECYCLE_FIELD_NAME + "; }");
        generatedType.addMethod(accessorMethod);
    }

    private static void addBaseBridgeMethod(
            @NotNull ClassPool classPool,
            @NotNull CtClass generatedType,
            @NotNull Method method,
            @NotNull String bridgeMethodName
    ) throws CannotCompileException, NotFoundException {
        CtMethod bridgeMethod = new CtMethod(
                toCtClass(classPool, method.getReturnType()),
                bridgeMethodName,
                toCtClasses(classPool, method.getParameterTypes()),
                generatedType
        );
        bridgeMethod.setModifiers(Modifier.PRIVATE);
        bridgeMethod.setExceptionTypes(toCtClasses(classPool, method.getExceptionTypes()));
        bridgeMethod.setBody(buildBaseBridgeMethodBody(method));
        generatedType.addMethod(bridgeMethod);
    }

    private static void addInterceptedMethod(
            @NotNull ClassPool classPool,
            @NotNull CtClass generatedType,
            @NotNull GeneratedNativeHookSpec hookSpec,
            @NotNull String bridgeMethodName
    ) throws CannotCompileException, NotFoundException {
        Method method = hookSpec.method();
        CtMethod generatedMethod = new CtMethod(
                toCtClass(classPool, method.getReturnType()),
                method.getName(),
                toCtClasses(classPool, method.getParameterTypes()),
                generatedType
        );

        int modifiers = method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.NATIVE;
        generatedMethod.setModifiers(modifiers);
        generatedMethod.setExceptionTypes(toCtClasses(classPool, method.getExceptionTypes()));
        generatedMethod.setBody(buildInterceptedMethodBody(hookSpec, bridgeMethodName));
        generatedType.addMethod(generatedMethod);
    }

    private static void addBaseInvokerMethod(
            @NotNull ClassPool classPool,
            @NotNull CtClass generatedType,
            @NotNull Collection<GeneratedHookBridge> bridges
    ) throws CannotCompileException, NotFoundException {
        CtMethod baseInvokerMethod = new CtMethod(
                toCtClass(classPool, Object.class),
                BASE_INVOKER_METHOD_NAME,
                new CtClass[]{
                        toCtClass(classPool, String.class),
                        toCtClass(classPool, Object[].class)
                },
                generatedType
        );
        baseInvokerMethod.setModifiers(Modifier.PUBLIC);
        baseInvokerMethod.setBody(buildBaseInvokerMethodBody(bridges));
        generatedType.addMethod(baseInvokerMethod);
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

    private static @NotNull Method requireLifecycleAccessorMethod(@NotNull Class<?> generatedType) {
        Objects.requireNonNull(generatedType, "generatedType cannot be null");
        try {
            return generatedType.getDeclaredMethod(LIFECYCLE_ACCESSOR_METHOD_NAME);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException(
                    "The generated entity does not expose the expected lifecycle accessor: " + generatedType.getName(),
                    exception
            );
        }
    }

    private static @NotNull Method requireBaseInvokerMethod(@NotNull Class<?> generatedType) {
        Objects.requireNonNull(generatedType, "generatedType cannot be null");
        try {
            return generatedType.getDeclaredMethod(BASE_INVOKER_METHOD_NAME, String.class, Object[].class);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException(
                    "The generated entity does not expose the expected base invoker: " + generatedType.getName(),
                    exception
            );
        }
    }

    private static @NotNull String buildBaseBridgeMethodBody(@NotNull Method method) {
        if (method.getReturnType() == void.class) {
            return "{ super." + method.getName() + "($$); }";
        }
        return "{ return super." + method.getName() + "($$); }";
    }

    private static @NotNull String buildInterceptedMethodBody(
            @NotNull GeneratedNativeHookSpec hookSpec,
            @NotNull String bridgeMethodName
    ) {
        Method method = hookSpec.method();
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("if (this.").append(LIFECYCLE_FIELD_NAME).append(" == null) {");
        if (method.getReturnType() == void.class) {
            builder.append("this.").append(bridgeMethodName).append("($$); return;");
        } else {
            builder.append("return this.").append(bridgeMethodName).append("($$);");
        }
        builder.append('}');
        builder.append("Object spigotBoot$result = this.").append(LIFECYCLE_FIELD_NAME)
                .append(".onNativeHook(\"").append(hookSpec.hookName()).append("\", this, ")
                .append(buildArgumentArrayExpression(method.getParameterTypes())).append(");");
        builder.append(buildReturnStatement(method.getReturnType(), "spigotBoot$result"));
        builder.append('}');
        return builder.toString();
    }

    private static @NotNull String buildBaseInvokerMethodBody(@NotNull Collection<GeneratedHookBridge> bridges) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("Object[] spigotBoot$args = $2 != null ? $2 : new Object[0];");
        for (GeneratedHookBridge bridge : bridges) {
            Method method = bridge.hookSpec.method();
            builder.append("if (\"").append(bridge.hookSpec.hookName()).append("\".equals($1)) {");
            if (method.getReturnType() == void.class) {
                builder.append("this.").append(bridge.bridgeMethodName)
                        .append('(').append(buildBridgeArgumentList(method.getParameterTypes(), "spigotBoot$args")).append(");");
                builder.append("return null;");
            } else {
                builder.append("return ").append(boxReturnExpression(method.getReturnType(), "this." + bridge.bridgeMethodName
                        + '(' + buildBridgeArgumentList(method.getParameterTypes(), "spigotBoot$args") + ')')).append(';');
            }
            builder.append('}');
        }
        builder.append("throw new IllegalArgumentException(\"Unknown native hook: \" + $1);");
        builder.append('}');
        return builder.toString();
    }

    private static @NotNull String buildArgumentArrayExpression(@NotNull Class<?>[] parameterTypes) {
        if (parameterTypes.length == 0) {
            return "new Object[0]";
        }

        StringBuilder builder = new StringBuilder("new Object[]{");
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(boxParameterExpression(parameterTypes[index], "$" + (index + 1)));
        }
        builder.append('}');
        return builder.toString();
    }

    private static @NotNull String buildBridgeArgumentList(
            @NotNull Class<?>[] parameterTypes,
            @NotNull String arrayExpression
    ) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parameterTypes.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(unboxExpression(parameterTypes[index], arrayExpression + "[" + index + "]"));
        }
        return builder.toString();
    }

    private static @NotNull String buildReturnStatement(@NotNull Class<?> returnType, @NotNull String resultVariableName) {
        if (returnType == void.class) {
            return "return;";
        }
        if (!returnType.isPrimitive()) {
            return "return (" + sourceTypeName(returnType) + ") " + resultVariableName + ';';
        }
        if (returnType == boolean.class) {
            return "return " + resultVariableName + " != null && ((java.lang.Boolean) " + resultVariableName + ").booleanValue();";
        }
        if (returnType == byte.class) {
            return "return " + resultVariableName + " == null ? ((byte) 0) : ((java.lang.Byte) " + resultVariableName + ").byteValue();";
        }
        if (returnType == char.class) {
            return "return " + resultVariableName + " == null ? ((char) 0) : ((java.lang.Character) " + resultVariableName + ").charValue();";
        }
        if (returnType == short.class) {
            return "return " + resultVariableName + " == null ? ((short) 0) : ((java.lang.Short) " + resultVariableName + ").shortValue();";
        }
        if (returnType == int.class) {
            return "return " + resultVariableName + " == null ? 0 : ((java.lang.Integer) " + resultVariableName + ").intValue();";
        }
        if (returnType == long.class) {
            return "return " + resultVariableName + " == null ? 0L : ((java.lang.Long) " + resultVariableName + ").longValue();";
        }
        if (returnType == float.class) {
            return "return " + resultVariableName + " == null ? 0.0F : ((java.lang.Float) " + resultVariableName + ").floatValue();";
        }
        if (returnType == double.class) {
            return "return " + resultVariableName + " == null ? 0.0D : ((java.lang.Double) " + resultVariableName + ").doubleValue();";
        }
        throw new IllegalStateException("Unsupported primitive return type: " + returnType.getName() + ".");
    }

    private static @NotNull String boxParameterExpression(@NotNull Class<?> parameterType, @NotNull String parameterExpression) {
        if (!parameterType.isPrimitive()) {
            return parameterExpression;
        }
        if (parameterType == boolean.class) {
            return "java.lang.Boolean.valueOf(" + parameterExpression + ')';
        }
        if (parameterType == byte.class) {
            return "java.lang.Byte.valueOf(" + parameterExpression + ')';
        }
        if (parameterType == char.class) {
            return "java.lang.Character.valueOf(" + parameterExpression + ')';
        }
        if (parameterType == short.class) {
            return "java.lang.Short.valueOf(" + parameterExpression + ')';
        }
        if (parameterType == int.class) {
            return "java.lang.Integer.valueOf(" + parameterExpression + ')';
        }
        if (parameterType == long.class) {
            return "java.lang.Long.valueOf(" + parameterExpression + ')';
        }
        if (parameterType == float.class) {
            return "java.lang.Float.valueOf(" + parameterExpression + ')';
        }
        if (parameterType == double.class) {
            return "java.lang.Double.valueOf(" + parameterExpression + ')';
        }
        throw new IllegalStateException("Unsupported primitive parameter type: " + parameterType.getName() + ".");
    }

    private static @NotNull String boxReturnExpression(@NotNull Class<?> returnType, @NotNull String valueExpression) {
        if (!returnType.isPrimitive()) {
            return valueExpression;
        }
        if (returnType == boolean.class) {
            return "java.lang.Boolean.valueOf(" + valueExpression + ')';
        }
        if (returnType == byte.class) {
            return "java.lang.Byte.valueOf(" + valueExpression + ')';
        }
        if (returnType == char.class) {
            return "java.lang.Character.valueOf(" + valueExpression + ')';
        }
        if (returnType == short.class) {
            return "java.lang.Short.valueOf(" + valueExpression + ')';
        }
        if (returnType == int.class) {
            return "java.lang.Integer.valueOf(" + valueExpression + ')';
        }
        if (returnType == long.class) {
            return "java.lang.Long.valueOf(" + valueExpression + ')';
        }
        if (returnType == float.class) {
            return "java.lang.Float.valueOf(" + valueExpression + ')';
        }
        if (returnType == double.class) {
            return "java.lang.Double.valueOf(" + valueExpression + ')';
        }
        throw new IllegalStateException("Unsupported primitive return type: " + returnType.getName() + ".");
    }

    private static @NotNull String unboxExpression(@NotNull Class<?> parameterType, @NotNull String valueExpression) {
        if (!parameterType.isPrimitive()) {
            return "(" + sourceTypeName(parameterType) + ") " + valueExpression;
        }
        if (parameterType == boolean.class) {
            return "((java.lang.Boolean) " + valueExpression + ").booleanValue()";
        }
        if (parameterType == byte.class) {
            return "((java.lang.Byte) " + valueExpression + ").byteValue()";
        }
        if (parameterType == char.class) {
            return "((java.lang.Character) " + valueExpression + ").charValue()";
        }
        if (parameterType == short.class) {
            return "((java.lang.Short) " + valueExpression + ").shortValue()";
        }
        if (parameterType == int.class) {
            return "((java.lang.Integer) " + valueExpression + ").intValue()";
        }
        if (parameterType == long.class) {
            return "((java.lang.Long) " + valueExpression + ").longValue()";
        }
        if (parameterType == float.class) {
            return "((java.lang.Float) " + valueExpression + ").floatValue()";
        }
        if (parameterType == double.class) {
            return "((java.lang.Double) " + valueExpression + ").doubleValue()";
        }
        throw new IllegalStateException("Unsupported primitive parameter type: " + parameterType.getName() + ".");
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
        String canonicalName = type.getCanonicalName();
        return canonicalName != null ? canonicalName : type.getName();
    }

    private static @NotNull String methodKey(@NotNull Method method) {
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

    private static final class GeneratedHookBridge {
        private final GeneratedNativeHookSpec hookSpec;
        private final String bridgeMethodName;

        private GeneratedHookBridge(
                @NotNull GeneratedNativeHookSpec hookSpec,
                @NotNull String bridgeMethodName
        ) {
            this.hookSpec = hookSpec;
            this.bridgeMethodName = bridgeMethodName;
        }
    }
}

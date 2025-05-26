package me.approximations.apxPlugin.di;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
public class DIContainer {
    private final Map<Class<?>, ClassMetadata<?>> typeMappings = new HashMap<>();
    private final Map<Class<?>, Object> singletons = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> ClassMetadata<T> register(Class<T> baseType, Class<? extends T> implType) {
        if (implType.isInterface()) {
            throw new IllegalArgumentException("Cannot register an interface: " + implType.getName());
        }

        ClassMetadata<T> classMetadata = new ClassMetadata<>((Class<T>) implType, getInjectConstructor((Class<T>) implType));
        typeMappings.put(baseType, classMetadata);

        return classMetadata;
    }

    @SuppressWarnings("unchecked")
    public <T> ClassMetadata<T> register(Class<? extends T> baseType, T dependency) {
        ClassMetadata<T> classMetadata = new ClassMetadata<>((Class<T>) dependency.getClass(), getInjectConstructor((Class<T>) dependency.getClass()));
        typeMappings.put(baseType, classMetadata);
        singletons.put(baseType, dependency);

        return classMetadata;
    }

    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> type) {
        if (!typeMappings.containsKey(type)) {
            return null;
        }

        try {
            if (singletons.containsKey(type)) {
                return type.cast(singletons.get(type));
            }

            ClassMetadata<?> implMetadata = typeMappings.get(type);

            T instance = (T) createInstance(implMetadata);
            singletons.put(type, instance);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve type: " + type, e);
        }
    }

    private <T> T createInstance(ClassMetadata<T> metadata) throws Exception {
        Constructor<T> constructor = metadata.getInjectConstructor();

        T instance = instantiateWithConstructor(metadata.getClazz(), constructor);

        injectDependencies(instance);

        return instance;
    }

    @SuppressWarnings("unchecked")
    public <T> void injectDependencies(T instance) {
        Objects.requireNonNull(instance, "instance cannot be null.");
        Class<T> type = (Class<T>) instance.getClass();

        try {
            setterInject(type, instance);
            fieldInject(type, instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to inject dependencies for: " + type.getName(), e);
        }
    }

    private <T> void setterInject(Class<T> type, T instance) throws IllegalAccessException, InvocationTargetException {
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Inject.class) && method.getParameterCount() == 1) {
                Class<?> depType = method.getParameterTypes()[0];
                Object dep = resolve(depType);
                method.setAccessible(true);
                method.invoke(instance, dep);
            }
        }
    }

    private <T> void fieldInject(Class<T> type, T instance) throws IllegalAccessException {
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                Object dep = resolve(field.getType());
                field.setAccessible(true);
                field.set(instance, dep);
            }
        }
    }

    private <T> T instantiateWithConstructor(Class<T> type, Constructor<?> ctor) throws Exception {
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = resolve(paramTypes[i]);
        }
        ctor.setAccessible(true);
        return type.cast(ctor.newInstance(params));
    }

    @SuppressWarnings("unchecked")
    public <T> @NotNull Constructor<T> getInjectConstructor(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null.");

        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (ctor.isAnnotationPresent(Inject.class)) {
                return (Constructor<T>) ctor;
            }
        }

        Constructor<T>[] ctors = (Constructor<T>[]) type.getDeclaredConstructors();
        Constructor<T> selectedCtor = null;
        int maxParams = -1;
        for (Constructor<T> ctor : ctors) {
            int paramCount = ctor.getParameterCount();
            if (paramCount > maxParams) {
                maxParams = paramCount;
                selectedCtor = ctor;
            }
        }

        return selectedCtor;
    }
}


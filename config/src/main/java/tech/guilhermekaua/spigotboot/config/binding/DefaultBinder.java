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
package tech.guilhermekaua.spigotboot.config.binding;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.annotation.Comment;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigProperty;
import tech.guilhermekaua.spigotboot.config.annotation.Default;
import tech.guilhermekaua.spigotboot.config.annotation.NodeKey;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;
import tech.guilhermekaua.spigotboot.core.utils.CollectionTypeUtils;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;
import tech.guilhermekaua.spigotboot.core.validation.ValidationError;
import tech.guilhermekaua.spigotboot.core.validation.ValidationResult;
import tech.guilhermekaua.spigotboot.core.validation.Validator;

import java.lang.reflect.*;
import java.util.*;

/**
 * Default implementation of the {@link Binder} interface.
 */
public class DefaultBinder implements Binder {

    private final TypeSerializerRegistry serializers;
    private final Validator validator;
    private final boolean implicitDefaults;
    private final boolean useConstructorBinding;

    private DefaultBinder(
            @NotNull TypeSerializerRegistry serializers,
            @Nullable Validator validator,
            boolean implicitDefaults,
            boolean useConstructorBinding
    ) {
        this.serializers = serializers;
        this.validator = validator;
        this.implicitDefaults = implicitDefaults;
        this.useConstructorBinding = useConstructorBinding;
    }

    @Override
    public <T> @NotNull BindingResult<T> bind(@NotNull ConfigNode node, @NotNull Class<T> type, @NotNull NamingStrategy namingStrategy) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(namingStrategy, "namingStrategy cannot be null");

        List<BindingError> errors = new ArrayList<>();
        T instance;

        try {
            instance = createInstance(type, node, namingStrategy, errors);
            if (instance == null) {
                return BindingResult.failure(errors);
            }

            bindFields(instance, node, PropertyPath.root(), namingStrategy, errors);

            List<ValidationError> validationErrors = Collections.emptyList();
            if (validator != null) {
                ValidationResult result = validator.validate(instance);
                if (result.hasErrors()) {
                    validationErrors = result.errors();
                }
            }

            if (errors.isEmpty()) {
                if (validationErrors.isEmpty()) {
                    return BindingResult.success(instance);
                } else {
                    return BindingResult.withValidationErrors(instance, validationErrors);
                }
            } else {
                return BindingResult.of(instance, errors, validationErrors);
            }

        } catch (Exception e) {
            errors.add(new BindingError(PropertyPath.root(), type.getSimpleName(), "Failed to create instance", e));
            return BindingResult.failure(errors);
        }
    }

    @Override
    public <T> @NotNull BindingResult<T> bind(@NotNull ConfigNode node, @NotNull T instance, @NotNull NamingStrategy namingStrategy) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");
        Objects.requireNonNull(namingStrategy, "namingStrategy cannot be null");

        List<BindingError> errors = new ArrayList<>();
        bindFields(instance, node, PropertyPath.root(), namingStrategy, errors);

        List<ValidationError> validationErrors = Collections.emptyList();
        if (validator != null) {
            ValidationResult result = validator.validate(instance);
            if (result.hasErrors()) {
                validationErrors = result.errors();
            }
        }

        if (errors.isEmpty() && validationErrors.isEmpty()) {
            return BindingResult.success(instance);
        } else if (errors.isEmpty()) {
            return BindingResult.withValidationErrors(instance, validationErrors);
        } else {
            return BindingResult.of(instance, errors, validationErrors);
        }
    }

    @Override
    public <T> void unbind(@NotNull T object, @NotNull MutableConfigNode node, @NotNull NamingStrategy namingStrategy) {
        Objects.requireNonNull(object, "object cannot be null");
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(namingStrategy, "namingStrategy cannot be null");

        Class<?> type = object.getClass();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(NodeKey.class)) {
                continue;
            }

            ConfigProperty property = field.getAnnotation(ConfigProperty.class);
            if (property != null && property.hidden()) {
                continue;
            }

            String configKey = getConfigKey(field, namingStrategy);
            field.setAccessible(true);

            try {
                Object value = field.get(object);
                if (value == null) {
                    continue;
                }

                MutableConfigNode childNode = node.node(configKey);

                Comment comment = field.getAnnotation(Comment.class);
                if (comment != null) {
                    childNode.setComment(comment.value());
                }

                serializeValue(value, childNode, field.getType(), namingStrategy);
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable T createInstance(@NotNull Class<T> type, @NotNull ConfigNode node, @NotNull NamingStrategy namingStrategy, @NotNull List<BindingError> errors) {
        try {
            Constructor<T> noArgCtor = null;

            try {
                noArgCtor = type.getDeclaredConstructor();
                noArgCtor.setAccessible(true);
                return noArgCtor.newInstance();
            } catch (NoSuchMethodException e) {
            }

            if (useConstructorBinding) {
                Constructor<?>[] constructors = type.getDeclaredConstructors();
                if (constructors.length == 1) {
                    Constructor<T> ctor = (Constructor<T>) constructors[0];
                    ctor.setAccessible(true);
                    Object[] args = resolveConstructorArgs(ctor, node, namingStrategy, errors);
                    if (args != null) {
                        return ctor.newInstance(args);
                    }
                }
            }

            errors.add(new BindingError(PropertyPath.root(), type.getSimpleName(),
                    "No suitable constructor found", null));
            return null;

        } catch (Exception e) {
            errors.add(new BindingError(PropertyPath.root(), type.getSimpleName(),
                    "Failed to instantiate", e));
            return null;
        }
    }

    private @Nullable Object[] resolveConstructorArgs(
            @NotNull Constructor<?> ctor,
            @NotNull ConfigNode node,
            @NotNull NamingStrategy namingStrategy,
            @NotNull List<BindingError> errors
    ) {
        Parameter[] params = ctor.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            String paramName = param.getName();
            String configKey = namingStrategy.toConfig(paramName);
            ConfigNode childNode = node.node(configKey);

            try {
                args[i] = deserializeValue(childNode, param.getParameterizedType(), PropertyPath.of(configKey), namingStrategy, errors);
            } catch (Exception e) {
                errors.add(new BindingError(PropertyPath.of(configKey), paramName,
                        "Failed to deserialize constructor parameter", e));
                return null;
            }
        }

        return args;
    }

    private void bindFields(
            @NotNull Object instance,
            @NotNull ConfigNode node,
            @NotNull PropertyPath basePath,
            @NotNull NamingStrategy namingStrategy,
            @NotNull List<BindingError> errors
    ) {
        Class<?> type = instance.getClass();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            String configKey = getConfigKey(field, namingStrategy);
            PropertyPath fieldPath = basePath.child(configKey);
            ConfigNode childNode = node.node(configKey);
            field.setAccessible(true);

            try {
                Object value = deserializeField(field, childNode, fieldPath, instance, namingStrategy, errors);
                if (value != null || childNode.isNull()) {
                    field.set(instance, value);
                }
            } catch (Exception e) {
                errors.add(new BindingError(fieldPath, field.getName(), "Failed to bind field", e));
            }
        }
    }

    private @Nullable Object deserializeField(
            @NotNull Field field,
            @NotNull ConfigNode node,
            @NotNull PropertyPath path,
            @NotNull Object instance,
            @NotNull NamingStrategy namingStrategy,
            @NotNull List<BindingError> errors
    ) throws IllegalAccessException {
        if (node.isVirtual() || node.isNull()) {
            Default defaultAnn = field.getAnnotation(Default.class);

            if (defaultAnn != null) {
                return parseDefaultValue(defaultAnn.value(), field.getType());
            }
            if (implicitDefaults) {
                return field.get(instance);
            }
            return null;
        }

        return deserializeValue(node, field.getGenericType(), path, namingStrategy, errors);
    }

    @SuppressWarnings("unchecked")
    private @Nullable Object deserializeValue(
            @NotNull ConfigNode node,
            @NotNull Type type,
            @NotNull PropertyPath path,
            @NotNull NamingStrategy namingStrategy,
            @NotNull List<BindingError> errors
    ) {
        if (node.isVirtual() || node.isNull()) {
            return null;
        }

        Class<?> rawClass = CollectionTypeUtils.getRawClass(type);
        if (rawClass == null) {
            return null;
        }

        TypeSerializer<?> serializer = serializers.getWithInheritance(rawClass);
        if (serializer != null) {
            try {
                return ((TypeSerializer<Object>) serializer).deserialize(node, (Class<Object>) rawClass);
            } catch (SerializationException e) {
                errors.add(new BindingError(path, rawClass.getSimpleName(), e.getMessage(), e));
                return null;
            }
        }

        if (List.class.isAssignableFrom(rawClass)) {
            return deserializeList(node, type, path, namingStrategy, errors);
        }
        if (Set.class.isAssignableFrom(rawClass)) {
            return new HashSet<>(deserializeList(node, type, path, namingStrategy, errors));
        }
        if (Map.class.isAssignableFrom(rawClass)) {
            return deserializeMap(node, type, path, namingStrategy, errors);
        }

        if (!rawClass.isPrimitive() && !rawClass.getName().startsWith("java.")) {
            BindingResult<?> result = bind(node, rawClass, namingStrategy);
            if (result.hasErrors()) {
                errors.addAll(result.errors());
            }
            return result.orElse(null);
        }

        return node.get(rawClass);
    }

    private @NotNull List<Object> deserializeList(
            @NotNull ConfigNode node,
            @NotNull Type genericType,
            @NotNull PropertyPath path,
            @NotNull NamingStrategy namingStrategy,
            @NotNull List<BindingError> errors
    ) {
        CollectionTypeUtils.CollectionTypeInfo typeInfo = CollectionTypeUtils.extractCollectionTypeInfo(genericType);
        Class<?> elementType = typeInfo != null ? typeInfo.getElementType() : Object.class;

        List<Object> result = new ArrayList<>();
        int index = 0;
        for (ConfigNode child : node.childrenList()) {
            Object value = deserializeValue(child, elementType, path.child(index), namingStrategy, errors);
            if (value != null) {
                result.add(value);
            }
            index++;
        }
        return result;
    }

    private @NotNull Map<String, Object> deserializeMap(
            @NotNull ConfigNode node,
            @NotNull Type genericType,
            @NotNull PropertyPath path,
            @NotNull NamingStrategy namingStrategy,
            @NotNull List<BindingError> errors
    ) {
        CollectionTypeUtils.MapTypeInfo typeInfo = CollectionTypeUtils.extractMapTypeInfo(genericType);
        Class<?> valueType = typeInfo != null ? typeInfo.getValueType() : Object.class;

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends ConfigNode> entry : node.childrenMap().entrySet()) {
            Object value = deserializeValue(entry.getValue(), valueType, path.child(entry.getKey()), namingStrategy, errors);
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private @Nullable Object parseDefaultValue(@NotNull String defaultStr, @NotNull Class<?> type) {
        if (defaultStr.isEmpty()) {
            return null;
        }
        if (type == String.class) {
            return defaultStr;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(defaultStr);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(defaultStr);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(defaultStr);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(defaultStr);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void serializeValue(@NotNull Object value, @NotNull MutableConfigNode node, @NotNull Class<?> type, @NotNull NamingStrategy namingStrategy) {
        TypeSerializer<Object> serializer = (TypeSerializer<Object>) serializers.getWithInheritance(type);
        if (serializer != null) {
            serializer.serialize(value, node);
            return;
        }

        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                MutableConfigNode itemNode = node.appendListItem();
                if (item != null) {
                    serializeValue(item, itemNode, item.getClass(), namingStrategy);
                }
            }
            return;
        }

        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object val = entry.getValue();
                if (val != null) {
                    serializeValue(val, node.node(key), val.getClass(), namingStrategy);
                }
            }
            return;
        }

        if (!type.isPrimitive() && !type.getName().startsWith("java.")) {
            unbind(value, node, namingStrategy);
            return;
        }

        node.set(value);
    }

    private @NotNull String getConfigKey(@NotNull Field field, @NotNull NamingStrategy namingStrategy) {
        ConfigProperty property = field.getAnnotation(ConfigProperty.class);
        if (property != null && !property.value().isEmpty()) {
            return property.value();
        }
        return namingStrategy.toConfig(field.getName());
    }

    /**
     * Builder for DefaultBinder.
     */
    public static class Builder implements Binder.Builder {
        private TypeSerializerRegistry serializers = TypeSerializerRegistry.defaults();
        private Validator validator = null;
        private boolean implicitDefaults = true;
        private boolean useConstructorBinding = true;

        @Override
        public @NotNull Builder serializers(@NotNull TypeSerializerRegistry registry) {
            this.serializers = Objects.requireNonNull(registry);
            return this;
        }

        @Override
        public @NotNull Builder validator(@NotNull Validator validator) {
            this.validator = Objects.requireNonNull(validator);
            return this;
        }

        @Override
        public @NotNull Builder implicitDefaults(boolean enabled) {
            this.implicitDefaults = enabled;
            return this;
        }

        @Override
        public @NotNull Builder useConstructorBinding(boolean enabled) {
            this.useConstructorBinding = enabled;
            return this;
        }

        @Override
        public @NotNull Binder build() {
            return new DefaultBinder(serializers, validator, implicitDefaults, useConstructorBinding);
        }
    }
}

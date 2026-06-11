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
package tech.guilhermekaua.spigotboot.core.context.dependency.injector;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.core.utils.CollectionTypeUtils;

import java.lang.reflect.*;
import java.util.Objects;

/**
 * Represents an injection point in the dependency injection system.
 * <p>
 * An injection point encapsulates information about where a dependency is being injected:
 * the target type, the annotated element (field, parameter, or method), and the qualifier if any.
 * This enables custom injectors to make decisions based on the full context of the injection site.
 */
@Getter
public final class InjectionPoint {
    /**
     * The generic type of the dependency being requested.
     */
    private final Type type;

    /**
     * The annotated element representing the injection site (Field, Parameter, or Method).
     */
    private final AnnotatedElement annotatedElement;

    /**
     * The qualifier for this injection point, or null if none specified.
     */
    private final @Nullable String qualifier;

    /**
     * Creates an injection point with the specified type, element, and qualifier.
     *
     * @param type             the generic type of the dependency, not null
     * @param annotatedElement the annotated element (field/parameter/method), not null
     * @param qualifier        the qualifier value, or null if none
     */
    public InjectionPoint(@NotNull Type type,
                          @NotNull AnnotatedElement annotatedElement,
                          @Nullable String qualifier) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.annotatedElement = Objects.requireNonNull(annotatedElement, "annotatedElement cannot be null");
        this.qualifier = qualifier;
    }

    /**
     * Creates an injection point from a constructor or method parameter.
     *
     * @param parameter the parameter to create the injection point from, not null
     * @return the injection point representing this parameter
     */
    public static @NotNull InjectionPoint fromParameter(@NotNull Parameter parameter) {
        Objects.requireNonNull(parameter, "parameter cannot be null");

        Type paramType = parameter.getParameterizedType();
        if (paramType == null) {
            paramType = parameter.getType();
        }

        return new InjectionPoint(paramType, parameter, BeanUtils.getQualifier(parameter));
    }

    /**
     * Creates an injection point from an injected field.
     *
     * @param field the field to create the injection point from, not null
     * @return the injection point representing this field
     */
    public static @NotNull InjectionPoint fromField(@NotNull Field field) {
        Objects.requireNonNull(field, "field cannot be null");

        return new InjectionPoint(field.getGenericType(), field, BeanUtils.getQualifier(field));
    }

    /**
     * Creates an injection point from a setter method.
     * <p>
     * The injection point is created from the first (and expected only) parameter of the setter.
     * The qualifier is extracted from the method itself.
     *
     * @param method the setter method to create the injection point from, not null
     * @return the injection point representing the setter's parameter
     * @throws IllegalArgumentException if the method has no parameters
     */
    public static @NotNull InjectionPoint fromSetterMethod(@NotNull Method method) {
        Objects.requireNonNull(method, "method cannot be null");

        Type[] genericParameterTypes = method.getGenericParameterTypes();
        if (genericParameterTypes.length == 0) {
            throw new IllegalArgumentException("Setter method must have at least one parameter: " + method);
        }

        Type paramType = genericParameterTypes[0];
        return new InjectionPoint(paramType, method, BeanUtils.getQualifier(method));
    }

    /**
     * Returns the raw class of this injection point's type.
     *
     * @return the raw class, or null if the type cannot be resolved to a class
     */
    public @Nullable Class<?> getRawType() {
        return CollectionTypeUtils.getRawClass(type);
    }
}

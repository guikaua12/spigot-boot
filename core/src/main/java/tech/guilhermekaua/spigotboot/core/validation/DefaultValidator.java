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
package tech.guilhermekaua.spigotboot.core.validation;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.exceptions.ValidationException;
import tech.guilhermekaua.spigotboot.core.validation.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Default implementation of the Validator interface.
 */
public class DefaultValidator implements Validator {

    private final Map<Class<? extends Annotation>, ConstraintFactory<?>> factories = new HashMap<>();

    public DefaultValidator() {
        registerDefaultFactories();
    }

    private void registerDefaultFactories() {
        factories.put(tech.guilhermekaua.spigotboot.core.validation.annotation.NotNull.class,
                new ConstraintFactory<tech.guilhermekaua.spigotboot.core.validation.annotation.NotNull>() {
                    @Override
                    public @NotNull Constraint<?> create(@NotNull
                                                         tech.guilhermekaua.spigotboot.core.validation.annotation.NotNull annotation) {
                        return new Constraint<Object>() {
                            @Override
                            public boolean isValid(Object value) {
                                return value != null;
                            }

                            @Override
                            public @NotNull String message(Object value) {
                                return annotation.message();
                            }

                            @Override
                            public boolean isFailFast() {
                                return annotation.failFast();
                            }
                        };
                    }

                    @Override
                    public @NotNull Class<tech.guilhermekaua.spigotboot.core.validation.annotation.NotNull> getAnnotationType() {
                        return tech.guilhermekaua.spigotboot.core.validation.annotation.NotNull.class;
                    }
                });

        factories.put(Min.class, new ConstraintFactory<Min>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull Min annotation) {
                return new Constraint<Number>() {
                    @Override
                    public boolean isValid(Number value) {
                        return value == null || value.longValue() >= annotation.value();
                    }

                    @Override
                    public @NotNull String message(Number value) {
                        return annotation.message().replace("{value}", String.valueOf(annotation.value()));
                    }

                    @Override
                    public boolean isFailFast() {
                        return false;
                    }

                    @Override
                    public String suggestedFix(Number value) {
                        return "Use a value >= " + annotation.value();
                    }
                };
            }

            @Override
            public @NotNull Class<Min> getAnnotationType() {
                return Min.class;
            }
        });

        factories.put(Max.class, new ConstraintFactory<Max>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull Max annotation) {
                return new Constraint<Number>() {
                    @Override
                    public boolean isValid(Number value) {
                        return value == null || value.longValue() <= annotation.value();
                    }

                    @Override
                    public @NotNull String message(Number value) {
                        return annotation.message().replace("{value}", String.valueOf(annotation.value()));
                    }

                    @Override
                    public boolean isFailFast() {
                        return false;
                    }

                    @Override
                    public String suggestedFix(Number value) {
                        return "Use a value <= " + annotation.value();
                    }
                };
            }

            @Override
            public @NotNull Class<Max> getAnnotationType() {
                return Max.class;
            }
        });

        factories.put(Range.class, new ConstraintFactory<Range>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull Range annotation) {
                return new Constraint<Number>() {
                    @Override
                    public boolean isValid(Number value) {
                        if (value == null) return true;
                        long v = value.longValue();
                        return v >= annotation.min() && v <= annotation.max();
                    }

                    @Override
                    public @NotNull String message(Number value) {
                        return annotation.message()
                                .replace("{min}", String.valueOf(annotation.min()))
                                .replace("{max}", String.valueOf(annotation.max()));
                    }

                    @Override
                    public boolean isFailFast() {
                        return false;
                    }

                    @Override
                    public String suggestedFix(Number value) {
                        return "Use a value between " + annotation.min() + " and " + annotation.max();
                    }
                };
            }

            @Override
            public @NotNull Class<Range> getAnnotationType() {
                return Range.class;
            }
        });

        factories.put(Pattern.class, new ConstraintFactory<Pattern>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull Pattern annotation) {
                final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                        annotation.value(), annotation.flags());
                return new Constraint<String>() {
                    @Override
                    public boolean isValid(String value) {
                        if (value == null) return true;
                        Matcher matcher = pattern.matcher(value);
                        return matcher.matches();
                    }

                    @Override
                    public @NotNull String message(String value) {
                        return annotation.message().replace("{value}", annotation.value());
                    }

                    @Override
                    public boolean isFailFast() {
                        return false;
                    }
                };
            }

            @Override
            public @NotNull Class<Pattern> getAnnotationType() {
                return Pattern.class;
            }
        });

        factories.put(OneOf.class, new ConstraintFactory<OneOf>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull OneOf annotation) {
                return new Constraint<String>() {
                    @Override
                    public boolean isValid(String value) {
                        if (value == null) return true;
                        for (String allowed : annotation.value()) {
                            if (annotation.ignoreCase() ? allowed.equalsIgnoreCase(value) : allowed.equals(value)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public @NotNull String message(String value) {
                        return annotation.message().replace("{value}", Arrays.toString(annotation.value()));
                    }

                    @Override
                    public boolean isFailFast() {
                        return false;
                    }

                    @Override
                    public String suggestedFix(String value) {
                        return "Use one of: " + Arrays.toString(annotation.value());
                    }
                };
            }

            @Override
            public @NotNull Class<OneOf> getAnnotationType() {
                return OneOf.class;
            }
        });

        factories.put(Size.class, new ConstraintFactory<Size>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull Size annotation) {
                return new Constraint<Object>() {
                    @Override
                    public boolean isValid(Object value) {
                        if (value == null) return true;
                        int size = getSize(value);
                        return size >= annotation.min() && size <= annotation.max();
                    }

                    private int getSize(Object value) {
                        if (value instanceof String) {
                            return ((String) value).length();
                        } else if (value instanceof Collection) {
                            return ((Collection<?>) value).size();
                        } else if (value instanceof Map) {
                            return ((Map<?, ?>) value).size();
                        } else if (value.getClass().isArray()) {
                            return java.lang.reflect.Array.getLength(value);
                        }
                        return 0;
                    }

                    @Override
                    public @NotNull String message(Object value) {
                        return annotation.message()
                                .replace("{min}", String.valueOf(annotation.min()))
                                .replace("{max}", String.valueOf(annotation.max()));
                    }

                    @Override
                    public boolean isFailFast() {
                        return false;
                    }
                };
            }

            @Override
            public @NotNull Class<Size> getAnnotationType() {
                return Size.class;
            }
        });

        factories.put(NotEmpty.class, new ConstraintFactory<NotEmpty>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull NotEmpty annotation) {
                return new Constraint<Object>() {
                    @Override
                    public boolean isValid(Object value) {
                        if (value == null) return false;
                        return !isEmpty(value);
                    }

                    private boolean isEmpty(Object value) {
                        if (value instanceof CharSequence) {
                            return ((CharSequence) value).length() == 0;
                        } else if (value instanceof Collection) {
                            return ((Collection<?>) value).isEmpty();
                        } else if (value instanceof Map) {
                            return ((Map<?, ?>) value).isEmpty();
                        } else if (value.getClass().isArray()) {
                            return java.lang.reflect.Array.getLength(value) == 0;
                        }
                        return true; // unmeasurable type: treat as empty -> invalid
                    }

                    @Override
                    public @NotNull String message(Object value) {
                        return annotation.message();
                    }

                    @Override
                    public boolean isFailFast() {
                        return annotation.failFast();
                    }

                    @Override
                    public String suggestedFix(Object value) {
                        return "Provide at least one value";
                    }
                };
            }

            @Override
            public @NotNull Class<NotEmpty> getAnnotationType() {
                return NotEmpty.class;
            }
        });

        factories.put(AssertTrue.class, new ConstraintFactory<AssertTrue>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull AssertTrue annotation) {
                return new Constraint<Object>() {
                    @Override
                    public boolean isValid(Object value) {
                        if (value == null) return true;
                        if (value instanceof Boolean) return (Boolean) value;
                        return false;
                    }

                    @Override
                    public @NotNull String message(Object value) {
                        return annotation.message();
                    }

                    @Override
                    public boolean isFailFast() {
                        return annotation.failFast();
                    }

                    @Override
                    public String suggestedFix(Object value) {
                        return "Ensure the assertion evaluates to true";
                    }
                };
            }

            @Override
            public @NotNull Class<AssertTrue> getAnnotationType() {
                return AssertTrue.class;
            }
        });

        factories.put(AssertFalse.class, new ConstraintFactory<AssertFalse>() {
            @Override
            public @NotNull Constraint<?> create(@NotNull AssertFalse annotation) {
                return new Constraint<Object>() {
                    @Override
                    public boolean isValid(Object value) {
                        if (value == null) return true;
                        if (value instanceof Boolean) return !(Boolean) value;
                        return false;
                    }

                    @Override
                    public @NotNull String message(Object value) {
                        return annotation.message();
                    }

                    @Override
                    public boolean isFailFast() {
                        return annotation.failFast();
                    }

                    @Override
                    public String suggestedFix(Object value) {
                        return "Ensure the assertion evaluates to false";
                    }
                };
            }

            @Override
            public @NotNull Class<AssertFalse> getAnnotationType() {
                return AssertFalse.class;
            }
        });
    }

    @Override
    public @NotNull ValidationResult validate(@NotNull Object object) {
        return validate(object, PropertyPath.root());
    }

    @Override
    public @NotNull ValidationResult validate(@NotNull Object object,
                                              @NotNull PropertyPath basePath) {
        return validate(object, basePath, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @SuppressWarnings("unchecked")
    private @NotNull ValidationResult validate(@NotNull Object object,
                                               @NotNull PropertyPath basePath,
                                               @NotNull Set<Object> visited) {
        Objects.requireNonNull(object, "object cannot be null");
        Objects.requireNonNull(basePath, "basePath cannot be null");

        if (!visited.add(object)) {
            return ValidationResult.of(Collections.emptyList());
        }

        List<ValidationError> errors = new ArrayList<>();

        for (Field field : getAllFields(object.getClass())) {
            PropertyPath fieldPath = basePath.child(field.getName());
            field.setAccessible(true);

            Object value;
            try {
                value = field.get(object);
            } catch (IllegalAccessException e) {
                continue;
            }

            for (Annotation annotation : field.getAnnotations()) {
                ConstraintFactory<?> factory = factories.get(annotation.annotationType());
                if (factory != null) {
                    Constraint<Object> constraint = (Constraint<Object>) createConstraint(factory, annotation);
                    if (!constraint.isValid(value)) {
                        errors.add(new ValidationError(
                                fieldPath,
                                field.getName(),
                                value,
                                constraint.message(value),
                                constraint.isFailFast(),
                                constraint.suggestedFix(value)
                        ));
                    }
                }
            }

            if (value != null && field.isAnnotationPresent(Valid.class)) {
                ValidationResult nested = validate(value, fieldPath, visited);
                errors.addAll(nested.errors());
            }
        }

        errors.addAll(validateAssertMethods(object, basePath));

        return ValidationResult.of(errors);
    }

    @SuppressWarnings("unchecked")
    private @NotNull List<ValidationError> validateAssertMethods(@NotNull Object object,
                                                                 @NotNull PropertyPath basePath) {
        List<ValidationError> errors = new ArrayList<>();

        for (Method method : getAllAssertMethods(object.getClass())) {
            AssertTrue assertTrue = method.getAnnotation(AssertTrue.class);
            AssertFalse assertFalse = method.getAnnotation(AssertFalse.class);
            if (assertTrue == null && assertFalse == null) {
                continue;
            }

            String pathAttr = assertTrue != null ? assertTrue.path() : assertFalse.path();
            boolean failFast = assertTrue != null ? assertTrue.failFast() : assertFalse.failFast();
            PropertyPath errorPath = resolveAssertPath(basePath, method, pathAttr);
            String fieldName = resolveAssertFieldName(errorPath, method);

            if (method.getParameterCount() != 0 || !isBooleanReturnType(method)) {
                String message = "Assert method must return boolean/Boolean and take no parameters: "
                        + method.getDeclaringClass().getSimpleName() + "." + method.getName();
                errors.add(new ValidationError(
                        errorPath,
                        fieldName,
                        null,
                        message,
                        failFast,
                        "Change the method to a zero-arg boolean-returning method"
                ));
                continue;
            }

            Object returnValue;
            try {
                method.setAccessible(true);
                returnValue = method.invoke(object);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String message = "Assert method threw: " + cause.getClass().getSimpleName()
                        + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
                errors.add(new ValidationError(
                        errorPath,
                        fieldName,
                        null,
                        message,
                        failFast,
                        "Fix the assert method so it does not throw"
                ));
                continue;
            } catch (IllegalAccessException e) {
                errors.add(new ValidationError(
                        errorPath,
                        fieldName,
                        null,
                        "Assert method is not accessible: " + method.getName(),
                        failFast,
                        "Make the assert method accessible"
                ));
                continue;
            }

            Annotation annotation = assertTrue != null ? assertTrue : assertFalse;
            ConstraintFactory<?> factory = factories.get(annotation.annotationType());
            if (factory == null) {
                continue;
            }
            Constraint<Object> constraint = (Constraint<Object>) createConstraint(factory, annotation);
            if (!constraint.isValid(returnValue)) {
                errors.add(new ValidationError(
                        errorPath,
                        fieldName,
                        returnValue,
                        constraint.message(returnValue),
                        constraint.isFailFast(),
                        constraint.suggestedFix(returnValue)
                ));
            }
        }

        return errors;
    }

    private @NotNull PropertyPath resolveAssertPath(@NotNull PropertyPath basePath,
                                                    @NotNull Method method,
                                                    @NotNull String pathAttr) {
        if (pathAttr.isEmpty()) {
            return basePath.child(method.getName());
        }
        PropertyPath relative = PropertyPath.parse(pathAttr);
        PropertyPath result = basePath;
        for (Object element : relative.elements()) {
            result = result.child(element);
        }
        return result;
    }

    private @NotNull String resolveAssertFieldName(@NotNull PropertyPath errorPath,
                                                   @NotNull Method method) {
        Object last = errorPath.last();
        if (last != null) {
            return String.valueOf(last);
        }
        return method.getName();
    }

    private boolean isBooleanReturnType(@NotNull Method method) {
        Class<?> returnType = method.getReturnType();
        return returnType == boolean.class || returnType == Boolean.class;
    }

    /**
     * Collects methods that may carry {@link AssertTrue}/{@link AssertFalse},
     * walking the hierarchy subclass-first and skipping overridden signatures.
     */
    private @NotNull List<Method> getAllAssertMethods(@NotNull Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                if (method.getAnnotation(AssertTrue.class) == null
                        && method.getAnnotation(AssertFalse.class) == null) {
                    continue;
                }
                String signature = methodSignature(method);
                if (!seenSignatures.add(signature)) {
                    continue;
                }
                methods.add(method);
            }
            current = current.getSuperclass();
        }
        return methods;
    }

    private @NotNull String methodSignature(@NotNull Method method) {
        StringBuilder sb = new StringBuilder(method.getName());
        sb.append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params[i].getName());
        }
        sb.append(')');
        return sb.toString();
    }

    private @NotNull List<Field> getAllFields(@NotNull Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private <A extends Annotation> Constraint<?> createConstraint(ConstraintFactory<?> factory, A annotation) {
        return ((ConstraintFactory<A>) factory).create(annotation);
    }

    @Override
    public void validateOrThrow(@NotNull Object object) throws ValidationException {
        ValidationResult result = validate(object);
        List<ValidationError> failFastErrors = result.getFailFastErrors();
        if (!failFastErrors.isEmpty()) {
            throw new ValidationException("Validation failed:\n" +
                    ValidationResult.of(failFastErrors).formatErrors("  "));
        }
    }

}

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

        for (Field field : object.getClass().getDeclaredFields()) {
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

        return ValidationResult.of(errors);
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

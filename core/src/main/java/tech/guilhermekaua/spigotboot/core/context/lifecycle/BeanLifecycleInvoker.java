/*
 * The MIT License
 * Copyright Â© 2025 Guilherme KauÃ£ da Silva
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
package tech.guilhermekaua.spigotboot.core.context.lifecycle;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnEnable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.MethodFormatUtils;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class BeanLifecycleInvoker {
    private final DependencyManager dependencyManager;

    public BeanLifecycleInvoker(@NotNull DependencyManager dependencyManager) {
        this.dependencyManager = Objects.requireNonNull(dependencyManager, "dependencyManager cannot be null.");
    }

    public void invokeOnEnable(@NotNull Map<BeanDefinition, Object> beanInstances) {
        invokeLifecycleMethods(beanInstances, OnEnable.class);
    }

    public void invokeOnDisable(@NotNull Map<BeanDefinition, Object> beanInstances, @NotNull Logger logger) {
        Objects.requireNonNull(logger, "logger cannot be null.");

        for (LifecycleTarget target : collectTargets(beanInstances)) {
            try {
                invokeLifecycleMethod(target, OnDisable.class);
            } catch (RuntimeException e) {
                logger.severe(String.format(
                        "Error executing @OnDisable callback for bean '%s' (%s)",
                        target.beanIdentifier,
                        target.realClass.getName()
                ));
                e.printStackTrace();
            }
        }
    }

    public void invokeLifecycleMethods(@NotNull Map<BeanDefinition, Object> beanInstances,
                                       @NotNull Class<? extends Annotation> annotationType) {
        Objects.requireNonNull(annotationType, "annotationType cannot be null.");

        for (LifecycleTarget target : collectTargets(beanInstances)) {
            invokeLifecycleMethod(target, annotationType);
        }
    }

    private void invokeLifecycleMethod(@NotNull LifecycleTarget target,
                                       @NotNull Class<? extends Annotation> annotationType) {
        Method lifecycleMethod = findLifecycleMethod(target, annotationType);
        if (lifecycleMethod == null) {
            return;
        }

        try {
            lifecycleMethod.setAccessible(true);
            lifecycleMethod.invoke(target.instance, dependencyManager.resolveArguments(lifecycleMethod));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(String.format(
                    "Failed to invoke %s lifecycle method on bean '%s' (%s)",
                    annotationName(annotationType),
                    target.beanIdentifier,
                    target.realClass.getName()
            ), cause);
        } catch (Exception e) {
            throw new RuntimeException(String.format(
                    "Failed to invoke %s lifecycle method on bean '%s' (%s)",
                    annotationName(annotationType),
                    target.beanIdentifier,
                    target.realClass.getName()
            ), e);
        }
    }

    private Method findLifecycleMethod(@NotNull LifecycleTarget target,
                                       @NotNull Class<? extends Annotation> annotationType) {
        Method selected = null;
        for (Method method : target.realClass.getDeclaredMethods()) {
            boolean hasOnEnable = method.isAnnotationPresent(OnEnable.class);
            boolean hasOnDisable = method.isAnnotationPresent(OnDisable.class);

            if (hasOnEnable && hasOnDisable) {
                throw invalidLifecycleDeclaration(
                        target,
                        method,
                        "A single method cannot be annotated with both @OnEnable and @OnDisable."
                );
            }

            if (!method.isAnnotationPresent(annotationType)) {
                continue;
            }

            validateLifecycleMethod(target, method, annotationType);

            if (selected != null) {
                throw new IllegalStateException(String.format(
                        "Invalid lifecycle declaration on bean '%s' (%s): multiple %s methods found (%s, %s).",
                        target.beanIdentifier,
                        target.realClass.getName(),
                        annotationName(annotationType),
                        MethodFormatUtils.formatMethod(selected),
                        MethodFormatUtils.formatMethod(method)
                ));
            }

            selected = method;
        }

        return selected;
    }

    private void validateLifecycleMethod(@NotNull LifecycleTarget target,
                                         @NotNull Method method,
                                         @NotNull Class<? extends Annotation> annotationType) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw invalidLifecycleDeclaration(
                    target,
                    method,
                    "Lifecycle methods must be instance methods, not static."
            );
        }

        if (!void.class.equals(method.getReturnType())) {
            throw invalidLifecycleDeclaration(
                    target,
                    method,
                    "Lifecycle methods must return void."
            );
        }

        if (method.isAnnotationPresent(Bean.class)) {
            throw invalidLifecycleDeclaration(
                    target,
                    method,
                    annotationName(annotationType) + " methods may not also be annotated with @Bean."
            );
        }
    }

    private RuntimeException invalidLifecycleDeclaration(@NotNull LifecycleTarget target,
                                                         @NotNull Method method,
                                                         @NotNull String message) {
        return new IllegalStateException(String.format(
                "Invalid lifecycle declaration on bean '%s' (%s) for method %s: %s",
                target.beanIdentifier,
                target.realClass.getName(),
                MethodFormatUtils.formatMethod(method),
                message
        ));
    }

    private List<LifecycleTarget> collectTargets(@NotNull Map<BeanDefinition, Object> beanInstances) {
        Objects.requireNonNull(beanInstances, "beanInstances cannot be null.");

        IdentityHashMap<Object, LifecycleTarget> targetsByInstance = new IdentityHashMap<>();
        for (Map.Entry<BeanDefinition, Object> entry : beanInstances.entrySet()) {
            Object instance = entry.getValue();
            if (instance == null) {
                continue;
            }

            LifecycleTarget target = targetsByInstance.get(instance);
            if (target == null) {
                targetsByInstance.put(instance, new LifecycleTarget(instance, entry.getKey().identifier()));
                continue;
            }

            if (entry.getKey().identifier().compareTo(target.beanIdentifier) < 0) {
                target.beanIdentifier = entry.getKey().identifier();
            }
        }

        List<LifecycleTarget> targets = new ArrayList<>(targetsByInstance.values());
        targets.sort(Comparator
                .comparingInt((LifecycleTarget target) -> target.order)
                .thenComparing(target -> target.beanIdentifier));
        return targets;
    }

    private static int resolveOrder(@NotNull Object instance, @NotNull Class<?> realClass) {
        if (instance instanceof Ordered) {
            return ((Ordered) instance).getOrder();
        }

        Order order = realClass.getAnnotation(Order.class);
        return order == null ? 0 : order.value();
    }

    private static String annotationName(@NotNull Class<? extends Annotation> annotationType) {
        return "@" + annotationType.getSimpleName();
    }

    private static final class LifecycleTarget {
        private final Object instance;
        private final Class<?> realClass;
        private final int order;
        private String beanIdentifier;

        private LifecycleTarget(@NotNull Object instance, @NotNull String beanIdentifier) {
            this.instance = instance;
            this.realClass = ProxyUtils.getRealClass(instance);
            this.order = resolveOrder(instance, realClass);
            this.beanIdentifier = beanIdentifier;
        }
    }
}

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
package tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler;

import javassist.util.proxy.ProxyObject;
import lombok.Getter;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;

@Getter
public class RegisteredMethodHandler {
    private final RegisteredMethodHandlerRunnable runnable;
    private final Class<?> targetClass;
    private final Class<? extends Annotation> classTargetAnnotation;
    private final Class<? extends Annotation> methodTargetAnnotation;

    public RegisteredMethodHandler(RegisteredMethodHandlerRunnable runnable,
                                   Class<?> targetClass,
                                   Class<? extends Annotation> classTargetAnnotation,
                                   Class<? extends Annotation> methodTargetAnnotation
    ) {
        this.runnable = runnable;
        this.targetClass = targetClass != void.class ? targetClass : null;
        this.classTargetAnnotation = classTargetAnnotation != Annotation.class ? classTargetAnnotation : null;
        this.methodTargetAnnotation = methodTargetAnnotation != Annotation.class ? methodTargetAnnotation : null;
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean canHandle(MethodHandlerContext context) {
        Objects.requireNonNull(context, "context cannot be null.");

        Object self = context.self();
        Method thisMethod = context.thisMethod();
        Method proceed = context.proceed();

        if (targetClass != null && (self == null || !targetClass.isInstance(self))) {
            return false;
        }

        Class<?> realClass = self != null ? getRealClass(self) : null;

        if (classTargetAnnotation != null && (realClass == null || !realClass.isAnnotationPresent(classTargetAnnotation))) {
            return false;
        }

        if (methodTargetAnnotation != null && !isMethodAnnotated(realClass, thisMethod, proceed, methodTargetAnnotation)) {
            return false;
        }

        return true;
    }

    private Class<?> getRealClass(Object self) {
        if (self == null) {
            return null;
        }

        Class<?> clazz = self.getClass();
        if (self instanceof ProxyObject && clazz.getSuperclass() != null) {
            return clazz.getSuperclass();
        }

        return clazz;
    }

    private boolean isMethodAnnotated(Class<?> realClass,
                                      Method thisMethod,
                                      Method proceed,
                                      Class<? extends Annotation> annotation) {
        if (annotation == null) {
            return false;
        }

        if (thisMethod != null && thisMethod.isAnnotationPresent(annotation)) {
            return true;
        }

        if (proceed != null && proceed.isAnnotationPresent(annotation)) {
            return true;
        }

        if (realClass != null && thisMethod != null) {
            Method real = findMethod(realClass, thisMethod);
            if (real != null && real.isAnnotationPresent(annotation)) {
                return true;
            }

            for (Class<?> iface : realClass.getInterfaces()) {
                Method ifaceMethod = findMethod(iface, thisMethod);
                if (ifaceMethod != null && ifaceMethod.isAnnotationPresent(annotation)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Method findMethod(Class<?> type, Method signatureSource) {
        try {
            return type.getMethod(signatureSource.getName(), signatureSource.getParameterTypes());
        } catch (NoSuchMethodException ignored) {
            // fall through
        }

        try {
            return type.getDeclaredMethod(signatureSource.getName(), signatureSource.getParameterTypes());
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}

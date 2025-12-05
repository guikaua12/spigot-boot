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

import lombok.Getter;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;

import java.lang.annotation.Annotation;

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
        if (targetClass != null && !targetClass.isInstance(context.self())) {
            return false;
        }

        if (classTargetAnnotation != null && context.thisMethod().getAnnotation(classTargetAnnotation) == null) {
            return false;
        }

        if (methodTargetAnnotation != null && context.thisMethod().getAnnotation(methodTargetAnnotation) == null) {
            return false;
        }

        return true;
    }
}

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
package tech.guilhermekaua.spigotboot.core.context.condition;

import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnClass;
import tech.guilhermekaua.spigotboot.core.utils.ClassUtils;

import java.lang.reflect.AnnotatedElement;

public class OnClassCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedElement metadata) {
        ConditionalOnClass annotation = metadata.getAnnotation(ConditionalOnClass.class);
        if (annotation == null) {
            return true;
        }

        String[] classNames = annotation.value();
        if (classNames.length == 0) {
            return true;
        }

        ClassLoader classLoader = context.getClassLoader();
        for (String className : classNames) {
            if (!ClassUtils.isPresent(className, classLoader)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public LogLevel getLogLevel(AnnotatedElement metadata) {
        ConditionalOnClass annotation = metadata.getAnnotation(ConditionalOnClass.class);
        return annotation != null ? annotation.logLevel() : LogLevel.DEBUG;
    }

    @Override
    public String describeFailure(AnnotatedElement metadata) {
        ConditionalOnClass annotation = metadata.getAnnotation(ConditionalOnClass.class);
        if (annotation != null && !annotation.message().isEmpty()) {
            return annotation.message();
        }
        if (annotation != null && annotation.value().length > 0) {
            return "@ConditionalOnClass did not find required class(es): " + String.join(", ", annotation.value());
        }
        return "@ConditionalOnClass condition not met";
    }
}

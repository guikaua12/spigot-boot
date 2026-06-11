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
package tech.guilhermekaua.spigotboot.core.context.condition;

import tech.guilhermekaua.spigotboot.core.context.annotations.Conditional;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class ConditionEvaluator {
    private static final Logger LOGGER = Logger.getLogger(ConditionEvaluator.class.getName());
    private static ConditionDebugReport debugReport;

    private ConditionEvaluator() {
    }

    public static void setDebugReport(ConditionDebugReport report) {
        debugReport = report;
    }

    public static ConditionDebugReport getDebugReport() {
        return debugReport;
    }

    public static boolean shouldSkip(AnnotatedElement element, ConditionContext context) {
        return shouldSkip(element, context, null);
    }

    public static boolean shouldSkip(AnnotatedElement element, ConditionContext context, String source) {
        List<Class<? extends Condition>> conditionClasses = new ArrayList<>();

        Conditional directConditional = element.getAnnotation(Conditional.class);
        if (directConditional != null) {
            Collections.addAll(conditionClasses, directConditional.value());
        }

        for (Annotation annotation : element.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            Conditional metaConditional = annotationType.getAnnotation(Conditional.class);
            if (metaConditional != null) {
                conditionClasses.addAll(Arrays.asList(metaConditional.value()));
            }
        }

        if (conditionClasses.isEmpty()) {
            return false;
        }

        for (Class<? extends Condition> conditionClass : conditionClasses) {
            Condition condition;
            try {
                Constructor<? extends Condition> constructor = conditionClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                condition = constructor.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate condition: " + conditionClass.getName(), e);
            }

            boolean matches = condition.matches(context, element);
            if (!matches) {
                String elementName = getElementName(element);
                String failureReason = condition.describeFailure(element);
                LogLevel logLevel = condition.getLogLevel(element);

                // record to debug report if active
                if (debugReport != null && source != null) {
                    debugReport.record(source, elementName, false, failureReason);
                }

                logConditionFailure(source, elementName, failureReason, logLevel);
                return true; // should skip
            }
        }

        if (debugReport != null && source != null && !conditionClasses.isEmpty()) {
            String elementName = getElementName(element);
            debugReport.record(source, elementName, true, null);
        }

        return false; // don't skip
    }

    private static void logConditionFailure(String source, String elementName,
                                            String failureReason, LogLevel logLevel) {
        if (logLevel == LogLevel.SILENT || source == null) {
            return;
        }

        String message = "[" + source + "] Skipping " + elementName + ": " + failureReason;
        LOGGER.log(logLevel.toJulLevel(), message);
    }

    private static String getElementName(AnnotatedElement element) {
        if (element instanceof Class) {
            return ((Class<?>) element).getSimpleName();
        }
        if (element instanceof Method) {
            Method method = (Method) element;
            return method.getDeclaringClass().getSimpleName() + "." + method.getName() + "()";
        }
        return element.toString();
    }
}

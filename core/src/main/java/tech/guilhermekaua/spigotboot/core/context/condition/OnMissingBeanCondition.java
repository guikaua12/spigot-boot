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

import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnMissingBean;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

public class OnMissingBeanCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedElement metadata) {
        ConditionalOnMissingBean annotation = metadata.getAnnotation(ConditionalOnMissingBean.class);
        if (annotation == null) {
            return true;
        }

        Class<?>[] types = annotation.value();
        String[] names = annotation.name();

        boolean hasTypes = types.length > 0;
        boolean hasNames = names.length > 0;

        if (!hasTypes && !hasNames) {
            return true;
        }

        BeanDefinitionRegistry registry = context.getBeanDefinitionRegistry();

        if (hasTypes && !hasNames) {
            return allTypesAbsent(types, registry);
        }
        if (!hasTypes) {
            return allNamesAbsent(names, registry);
        }
        // both specified: OR logic (match if type requirements pass OR name requirements pass)
        return allTypesAbsent(types, registry) || allNamesAbsent(names, registry);
    }

    private boolean allTypesAbsent(Class<?>[] types, BeanDefinitionRegistry registry) {
        for (Class<?> targetType : types) {
            if (hasAssignableBean(targetType, registry)) {
                return false;
            }
        }
        return true;
    }

    private boolean allNamesAbsent(String[] names, BeanDefinitionRegistry registry) {
        for (String name : names) {
            if (hasBeanWithName(name, registry)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAssignableBean(Class<?> targetType, BeanDefinitionRegistry registry) {
        return registry.streamEntries()
                .map(Map.Entry::getValue)
                .anyMatch(definition -> {
                    Class<?> beanType = ProxyUtils.unwrapProxyType(definition.getType());
                    return targetType.isAssignableFrom(beanType);
                });
    }

    private boolean hasBeanWithName(String name, BeanDefinitionRegistry registry) {
        return registry.streamEntries()
                .map(Map.Entry::getValue)
                .anyMatch(definition -> name.equals(definition.getQualifierName()));
    }

    @Override
    public LogLevel getLogLevel(AnnotatedElement metadata) {
        ConditionalOnMissingBean annotation = metadata.getAnnotation(ConditionalOnMissingBean.class);
        return annotation != null ? annotation.logLevel() : LogLevel.DEBUG;
    }

    @Override
    public String describeFailure(AnnotatedElement metadata) {
        ConditionalOnMissingBean annotation = metadata.getAnnotation(ConditionalOnMissingBean.class);
        if (annotation != null && !annotation.message().isEmpty()) {
            return annotation.message();
        }
        return "@ConditionalOnMissingBean condition not met";
    }
}

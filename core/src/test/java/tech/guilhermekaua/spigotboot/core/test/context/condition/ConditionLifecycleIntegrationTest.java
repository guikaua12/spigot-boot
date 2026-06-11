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
package tech.guilhermekaua.spigotboot.core.test.context.condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Conditional;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.component.registry.ComponentRegistry;
import tech.guilhermekaua.spigotboot.core.context.condition.Condition;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.DependencyResolveResolver;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.reflect.AnnotatedElement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConditionLifecycleIntegrationTest {

    private DependencyManager dependencyManager;
    private ComponentRegistry componentRegistry;
    private ConfigurationProcessor configurationProcessor;

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
        componentRegistry = new ComponentRegistry();
        configurationProcessor = new ConfigurationProcessor();
    }

    @Test
    void componentWithFailingCondition_notRegistered() {
        tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext context =
                new tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext(
                        dependencyManager.getBeanDefinitionRegistry(),
                        null,
                        Thread.currentThread().getContextClassLoader()
                );

        boolean shouldSkip = tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator
                .shouldSkip(SkippedComponent.class, context);

        assertTrue(shouldSkip, "Component with AlwaysFalseCondition should be skipped");

        if (!shouldSkip) {
            dependencyManager.registerDependency(SkippedComponent.class, null, false, (DependencyResolveResolver<SkippedComponent>) null);
        }

        assertTrue(dependencyManager.getBeanDefinitionRegistry().getDefinitions(SkippedComponent.class).isEmpty(),
                "SkippedComponent should not be in registry");
    }

    @Test
    void componentWithPassingCondition_registeredNormally() {
        tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext context =
                new tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext(
                        dependencyManager.getBeanDefinitionRegistry(),
                        null,
                        Thread.currentThread().getContextClassLoader()
                );

        boolean shouldSkip = tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator
                .shouldSkip(NormalComponent.class, context);

        assertFalse(shouldSkip, "Component without condition should not be skipped");

        if (!shouldSkip) {
            dependencyManager.registerDependency(NormalComponent.class, null, false, (DependencyResolveResolver<NormalComponent>) null);
        }

        assertFalse(dependencyManager.getBeanDefinitionRegistry().getDefinitions(NormalComponent.class).isEmpty(),
                "NormalComponent should be in registry");
    }

    @Test
    void configurationWithFailingCondition_allBeanMethodsSkipped() {
        configurationProcessor.processClass(SkippedConfiguration.class, dependencyManager);

        assertTrue(dependencyManager.getBeanDefinitionRegistry().getDefinitions(SkippedConfiguration.class).isEmpty(),
                "Configuration with failing condition should not be registered");

        assertTrue(dependencyManager.getBeanDefinitionRegistry().getDefinitions(String.class).isEmpty(),
                "Bean from skipped configuration should not be registered");
    }

    @Test
    void beanMethodWithFailingCondition_onlyThatMethodSkipped() {
        configurationProcessor.processClass(MixedConfiguration.class, dependencyManager);

        assertFalse(dependencyManager.getBeanDefinitionRegistry().getDefinitions(MixedConfiguration.class).isEmpty(),
                "Configuration class should be registered");

        boolean hasSkippedBean = dependencyManager.getBeanDefinitionRegistry()
                .getDefinitions(String.class)
                .stream()
                .anyMatch(def -> "skippedBean".equals(def.getQualifierName()));

        assertFalse(hasSkippedBean, "Bean method with failing condition should not be registered");

        boolean hasNormalBean = dependencyManager.getBeanDefinitionRegistry()
                .getDefinitions(Integer.class)
                .stream()
                .anyMatch(def -> "normalBean".equals(def.getQualifierName()));

        assertTrue(hasNormalBean, "Bean method without condition should be registered");
    }

    public static class AlwaysTrueCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedElement metadata) {
            return true;
        }
    }

    public static class AlwaysFalseCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedElement metadata) {
            return false;
        }
    }

    @Component
    @Conditional(AlwaysFalseCondition.class)
    static class SkippedComponent {
    }

    @Component
    static class NormalComponent {
    }

    @Configuration
    @Conditional(AlwaysFalseCondition.class)
    static class SkippedConfiguration {
        @Bean
        public String skippedConfigBean() {
            return "should not be registered";
        }
    }

    @Configuration
    static class MixedConfiguration {
        @Bean
        @Conditional(AlwaysFalseCondition.class)
        public String skippedBean() {
            return "should not be registered";
        }

        @Bean
        public Integer normalBean() {
            return 42;
        }
    }
}

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
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnBean;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnMissingBean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests verifying that @ConditionalOnBean and @ConditionalOnMissingBean
 * work correctly through the full condition evaluation lifecycle:
 * annotation -> ConditionEvaluator meta-annotation discovery -> condition instantiation
 * -> BeanDefinitionRegistry query -> skip/register decision.
 */
public class BeanAwareConditionIntegrationTest {

    private DependencyManager dependencyManager;
    private ConfigurationProcessor configurationProcessor;
    private BeanDefinitionRegistry registry;
    private SimpleConditionContext conditionContext;

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
        configurationProcessor = new ConfigurationProcessor();
        registry = dependencyManager.getBeanDefinitionRegistry();
        conditionContext = new SimpleConditionContext(
                registry,
                null,
                Thread.currentThread().getContextClassLoader()
        );
    }

    private void registerBeanDefinition(Class<?> requestedType, Class<?> implType, String qualifier) {
        registry.register(requestedType, new BeanDefinition(requestedType, implType, qualifier, false, null, null));
    }

    @Test
    void conditionalOnBean_beanPresent_doesNotSkip() {
        registerBeanDefinition(SomeService.class, SomeServiceImpl.class, null);

        boolean shouldSkip = ConditionEvaluator.shouldSkip(RequiresSomeService.class, conditionContext);

        assertFalse(shouldSkip, "@ConditionalOnBean should NOT skip when required bean type is present in registry");
    }

    @Test
    void conditionalOnBean_beanAbsent_skips() {
        boolean shouldSkip = ConditionEvaluator.shouldSkip(RequiresSomeService.class, conditionContext);

        assertTrue(shouldSkip, "@ConditionalOnBean should skip when required bean type is absent from registry");
    }

    @Test
    void conditionalOnMissingBean_beanAbsent_doesNotSkip() {
        boolean shouldSkip = ConditionEvaluator.shouldSkip(RequiresNoSomeService.class, conditionContext);

        assertFalse(shouldSkip, "@ConditionalOnMissingBean should NOT skip when specified bean type is absent from registry");
    }

    @Test
    void conditionalOnMissingBean_beanPresent_skips() {
        registerBeanDefinition(SomeService.class, SomeServiceImpl.class, null);

        boolean shouldSkip = ConditionEvaluator.shouldSkip(RequiresNoSomeService.class, conditionContext);

        assertTrue(shouldSkip, "@ConditionalOnMissingBean should skip when specified bean type already exists in registry");
    }

    @Test
    void conditionalOnBean_assignableTypeMatching_matchesSubtype() {
        registerBeanDefinition(SomeServiceImpl.class, SomeServiceImpl.class, null);

        // @ConditionalOnBean(SomeService.class) should still match because
        // SomeService.isAssignableFrom(SomeServiceImpl) is true
        boolean shouldSkip = ConditionEvaluator.shouldSkip(RequiresSomeService.class, conditionContext);

        assertFalse(shouldSkip, "@ConditionalOnBean should match implementation type via isAssignableFrom check");
    }

    @Test
    void conditionalOnBean_orderDependent_beanNotYetRegistered_skips() {
        boolean shouldSkipA = ConditionEvaluator.shouldSkip(DependsOnBeanB.class, conditionContext);
        assertTrue(shouldSkipA, "Should skip when BeanB is not yet registered (order-dependent behavior)");

        // simulate: BeanA was skipped, so it was NOT registered
        // now BeanB gets registered later
        registerBeanDefinition(BeanB.class, BeanB.class, null);

        assertFalse(registry.getDefinitions(BeanB.class).isEmpty(), "BeanB should now be in registry");

        assertTrue(registry.getDefinitions(DependsOnBeanB.class).isEmpty(),
                "DependsOnBeanB should NOT be in registry - it was skipped before BeanB was registered");
    }

    @Test
    void conditionalOnMissingBean_beanMethodLevel_skipsWhenPresent() {
        registerBeanDefinition(SomeService.class, SomeServiceImpl.class, null);

        configurationProcessor.processClass(ConditionalBeanMethodConfig.class, dependencyManager);

        boolean hasConditionalString = registry.getDefinitions(String.class).stream()
                .anyMatch(def -> "conditionalString".equals(def.getQualifierName()));
        assertFalse(hasConditionalString,
                "@Bean method with @ConditionalOnMissingBean should be skipped when bean is already present");

        boolean hasUnconditionalInteger = registry.getDefinitions(Integer.class).stream()
                .anyMatch(def -> "unconditionalInteger".equals(def.getQualifierName()));
        assertTrue(hasUnconditionalInteger,
                "Unconditional @Bean method should be registered regardless of other conditions");
    }

    @Test
    void conditionalOnBean_beanMethodLevel_registersWhenPresent() {
        registerBeanDefinition(SomeService.class, SomeServiceImpl.class, null);

        configurationProcessor.processClass(OnBeanMethodConfig.class, dependencyManager);

        boolean hasConditionalString = registry.getDefinitions(String.class).stream()
                .anyMatch(def -> "conditionalOnBeanString".equals(def.getQualifierName()));
        assertTrue(hasConditionalString,
                "@Bean method with @ConditionalOnBean should be registered when required bean is present");
    }

    @Test
    void conditionalOnBean_beanMethodLevel_skipsWhenAbsent() {
        configurationProcessor.processClass(OnBeanMethodConfig.class, dependencyManager);

        boolean hasConditionalString = registry.getDefinitions(String.class).stream()
                .anyMatch(def -> "conditionalOnBeanString".equals(def.getQualifierName()));
        assertFalse(hasConditionalString,
                "@Bean method with @ConditionalOnBean should be skipped when required bean is absent");
    }

    @Test
    void conditionalOnMissingBean_beanMethodLevel_registersWhenAbsent() {
        configurationProcessor.processClass(ConditionalBeanMethodConfig.class, dependencyManager);

        boolean hasConditionalString = registry.getDefinitions(String.class).stream()
                .anyMatch(def -> "conditionalString".equals(def.getQualifierName()));
        assertTrue(hasConditionalString,
                "@Bean method with @ConditionalOnMissingBean should be registered when bean is absent");
    }

    interface SomeService {
    }

    static class SomeServiceImpl implements SomeService {
    }

    static class AnotherService {
    }

    @ConditionalOnBean(SomeService.class)
    static class RequiresSomeService {
    }

    @ConditionalOnMissingBean(SomeService.class)
    static class RequiresNoSomeService {
    }

    @ConditionalOnBean(BeanB.class)
    static class DependsOnBeanB {
    }

    static class BeanB {
    }

    @Configuration
    static class ConditionalBeanMethodConfig {
        @Bean
        @ConditionalOnMissingBean(SomeService.class)
        public String conditionalString() {
            return "conditional";
        }

        @Bean
        public Integer unconditionalInteger() {
            return 42;
        }
    }

    @Configuration
    static class OnBeanMethodConfig {
        @Bean
        @ConditionalOnBean(SomeService.class)
        public String conditionalOnBeanString() {
            return "present";
        }
    }
}

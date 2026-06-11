package tech.guilhermekaua.spigotboot.core.test.context.condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnClass;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OnClassConditionIntegrationTest {

    private ConditionContext conditionContext;

    @BeforeEach
    void setUp() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();
        conditionContext = new SimpleConditionContext(registry, null, getClass().getClassLoader());
    }

    @Test
    void shouldSkip_conditionalOnClassPresent_returnsFalse() {
        boolean result = ConditionEvaluator.shouldSkip(ClassPresentModule.class, conditionContext);
        assertFalse(result, "Should not skip when @ConditionalOnClass specifies a present class");
    }

    @Test
    void shouldSkip_conditionalOnClassMissing_returnsTrue() {
        boolean result = ConditionEvaluator.shouldSkip(ClassMissingModule.class, conditionContext);
        assertTrue(result, "Should skip when @ConditionalOnClass specifies a missing class");
    }

    @Test
    void shouldSkip_multipleClassesAllPresent_returnsFalse() {
        boolean result = ConditionEvaluator.shouldSkip(MultipleClassesPresentModule.class, conditionContext);
        assertFalse(result, "Should not skip when all @ConditionalOnClass classes are present");
    }

    @Test
    void shouldSkip_multipleClassesOneMissing_returnsTrue() {
        boolean result = ConditionEvaluator.shouldSkip(MultipleClassesOneMissingModule.class, conditionContext);
        assertTrue(result, "Should skip when any @ConditionalOnClass class is missing");
    }

    @Test
    void shouldSkip_noConditionalOnClass_returnsFalse() {
        boolean result = ConditionEvaluator.shouldSkip(PlainModule.class, conditionContext);
        assertFalse(result, "Should not skip when no @ConditionalOnClass is present");
    }

    @ConditionalOnClass("java.lang.String")
    static class ClassPresentModule {
    }

    @ConditionalOnClass("com.nonexistent.totally.FakeLibraryClass")
    static class ClassMissingModule {
    }

    @ConditionalOnClass({"java.lang.String", "java.util.List"})
    static class MultipleClassesPresentModule {
    }

    @ConditionalOnClass({"java.lang.String", "com.nonexistent.totally.FakeLibraryClass"})
    static class MultipleClassesOneMissingModule {
    }

    static class PlainModule {
    }
}

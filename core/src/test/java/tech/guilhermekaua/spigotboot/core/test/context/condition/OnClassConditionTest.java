package tech.guilhermekaua.spigotboot.core.test.context.condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnClass;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.OnClassCondition;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OnClassConditionTest {

    private ConditionContext context;
    private OnClassCondition condition;

    @BeforeEach
    void setUp() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();
        context = new SimpleConditionContext(registry, null, getClass().getClassLoader());
        condition = new OnClassCondition();
    }

    @Test
    void matches_noAnnotation_returnsTrue() {
        assertTrue(condition.matches(context, UnannotatedClass.class),
                "Should return true when no @ConditionalOnClass annotation present");
    }

    @Test
    void matches_singleClassPresent_returnsTrue() {
        assertTrue(condition.matches(context, SingleClassPresentClass.class),
                "Should return true when specified class is present on classloader");
    }

    @Test
    void matches_singleClassAbsent_returnsFalse() {
        assertFalse(condition.matches(context, SingleClassAbsentClass.class),
                "Should return false when specified class is absent from classloader");
    }

    @Test
    void matches_multipleClassesAllPresent_returnsTrue() {
        assertTrue(condition.matches(context, MultipleClassesPresentClass.class),
                "Should return true when all specified classes are present");
    }

    @Test
    void matches_multipleClassesOneAbsent_returnsFalse() {
        assertFalse(condition.matches(context, MultipleClassesOneAbsentClass.class),
                "Should return false when any specified class is absent (AND logic)");
    }

    @Test
    void matches_emptyValue_returnsTrue() {
        assertTrue(condition.matches(context, EmptyValueClass.class),
                "Should return true when value array is empty (nothing to check)");
    }

    static class UnannotatedClass {
    }

    @ConditionalOnClass("java.lang.String")
    static class SingleClassPresentClass {
    }

    @ConditionalOnClass("com.nonexistent.totally.FakeClass")
    static class SingleClassAbsentClass {
    }

    @ConditionalOnClass({"java.lang.String", "java.util.List"})
    static class MultipleClassesPresentClass {
    }

    @ConditionalOnClass({"java.lang.String", "com.nonexistent.totally.FakeClass"})
    static class MultipleClassesOneAbsentClass {
    }

    @ConditionalOnClass({})
    static class EmptyValueClass {
    }
}

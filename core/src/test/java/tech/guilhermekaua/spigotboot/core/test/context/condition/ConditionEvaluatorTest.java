package tech.guilhermekaua.spigotboot.core.test.context.condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Conditional;
import tech.guilhermekaua.spigotboot.core.context.condition.Condition;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionEvaluator;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ConditionEvaluatorTest {

    private ConditionContext context;

    @BeforeEach
    void setUp() {
        BeanDefinitionRegistry registry = new BeanDefinitionRegistry();
        ClassLoader classLoader = getClass().getClassLoader();
        context = new SimpleConditionContext(registry, null, classLoader);
        CountingCondition.resetCount();
    }

    @Test
    void shouldSkip_noAnnotation_returnsFalse() {
        boolean result = ConditionEvaluator.shouldSkip(UnannotatedClass.class, context);
        assertFalse(result);
    }

    @Test
    void shouldSkip_emptyConditionArray_returnsFalse() {
        boolean result = ConditionEvaluator.shouldSkip(EmptyConditionalClass.class, context);
        assertFalse(result);
    }

    @Test
    void shouldSkip_singleTrueCondition_returnsFalse() {
        boolean result = ConditionEvaluator.shouldSkip(AlwaysTrueClass.class, context);
        assertFalse(result);
    }

    @Test
    void shouldSkip_singleFalseCondition_returnsTrue() {
        boolean result = ConditionEvaluator.shouldSkip(AlwaysFalseClass.class, context);
        assertTrue(result);
    }

    @Test
    void shouldSkip_multipleAllTrue_returnsFalse() {
        boolean result = ConditionEvaluator.shouldSkip(MultipleTrueClass.class, context);
        assertFalse(result);
    }

    @Test
    void shouldSkip_multipleWithFailure_returnsTrue_shortCircuits() {
        CountingCondition.resetCount();

        boolean result = ConditionEvaluator.shouldSkip(MultipleWithFailureClass.class, context);

        assertTrue(result);
        // first condition (AlwaysTrueCondition) evaluates, second (AlwaysFalseCondition) evaluates and fails,
        // third (CountingCondition) should NOT be evaluated due to short-circuit
        assertEquals(0, CountingCondition.getCount(), "CountingCondition should not have been evaluated due to short-circuit");
    }

    @Test
    void shouldSkip_metaAnnotation_evaluatesCondition() {
        boolean result = ConditionEvaluator.shouldSkip(MetaAnnotatedClass.class, context);
        assertFalse(result);
    }

    @Test
    void shouldSkip_conditionThrows_propagatesException() {
        Exception exception = assertThrows(RuntimeException.class, () -> {
            ConditionEvaluator.shouldSkip(ThrowingConditionClass.class, context);
        });
        assertEquals("Throwing condition", exception.getMessage());
    }

    @Test
    void shouldSkip_noDefaultConstructor_throwsRuntimeException() {
        Exception exception = assertThrows(RuntimeException.class, () -> {
            ConditionEvaluator.shouldSkip(NoDefaultConstructorClass.class, context);
        });
        assertTrue(exception.getMessage().contains("Failed to instantiate condition"));
    }

    static class UnannotatedClass {
    }

    @Conditional({})
    static class EmptyConditionalClass {
    }

    @Conditional(AlwaysTrueCondition.class)
    static class AlwaysTrueClass {
    }

    @Conditional(AlwaysFalseCondition.class)
    static class AlwaysFalseClass {
    }

    @Conditional({AlwaysTrueCondition.class, AlwaysTrueCondition.class})
    static class MultipleTrueClass {
    }

    @Conditional({AlwaysTrueCondition.class, AlwaysFalseCondition.class, CountingCondition.class})
    static class MultipleWithFailureClass {
    }

    @ConditionalAlwaysTrue
    static class MetaAnnotatedClass {
    }

    @Conditional(ThrowingCondition.class)
    static class ThrowingConditionClass {
    }

    @Conditional(NoDefaultConstructorCondition.class)
    static class NoDefaultConstructorClass {
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

    public static class ThrowingCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedElement metadata) {
            throw new RuntimeException("Throwing condition");
        }
    }

    public static class NoDefaultConstructorCondition implements Condition {
        public NoDefaultConstructorCondition(String required) {
            // no default constructor
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedElement metadata) {
            return true;
        }
    }

    public static class CountingCondition implements Condition {
        private static final AtomicInteger count = new AtomicInteger(0);

        @Override
        public boolean matches(ConditionContext context, AnnotatedElement metadata) {
            count.incrementAndGet();
            return true;
        }

        public static int getCount() {
            return count.get();
        }

        public static void resetCount() {
            count.set(0);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Conditional(AlwaysTrueCondition.class)
    public @interface ConditionalAlwaysTrue {
    }
}

package tech.guilhermekaua.spigotboot.core.test.context.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnEnable;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.BeanLifecycleInvoker;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanLifecycleInvokerTest {

    private DependencyManager dependencyManager;
    private BeanLifecycleInvoker beanLifecycleInvoker;

    @BeforeEach
    void setUp() {
        dependencyManager = new DependencyManager();
        beanLifecycleInvoker = new BeanLifecycleInvoker(dependencyManager);
        dependencyManager.registerDependency(String.class, "lifecycle-message", null, false);
        dependencyManager.registerDependency(Integer.class, 7, null, false);
    }

    @Test
    void invokeLifecycleMethods_resolvesArgumentsAndInvokesNonPublicMethod() {
        ValidLifecycleBean bean = new ValidLifecycleBean();

        beanLifecycleInvoker.invokeOnEnable(singleBean(bean));

        assertEquals("lifecycle-message", bean.message);
        assertEquals(7, bean.number);
    }

    @Test
    void invokeLifecycleMethods_rejectsStaticLifecycleMethods() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> beanLifecycleInvoker.invokeOnEnable(singleBean(new StaticLifecycleBean()))
        );

        assertTrue(exception.getMessage().contains("must be instance methods"));
    }

    @Test
    void invokeLifecycleMethods_rejectsNonVoidLifecycleMethods() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> beanLifecycleInvoker.invokeOnEnable(singleBean(new NonVoidLifecycleBean()))
        );

        assertTrue(exception.getMessage().contains("must return void"));
    }

    @Test
    void invokeLifecycleMethods_rejectsDuplicateLifecycleAnnotations() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> beanLifecycleInvoker.invokeOnEnable(singleBean(new DuplicateOnEnableBean()))
        );

        assertTrue(exception.getMessage().contains("multiple @OnEnable methods found"));
    }

    @Test
    void invokeLifecycleMethods_rejectsBeanFactoryMethods() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> beanLifecycleInvoker.invokeOnEnable(singleBean(new BeanFactoryLifecycleBean()))
        );

        assertTrue(exception.getMessage().contains("may not also be annotated with @Bean"));
    }

    @Test
    void invokeLifecycleMethods_rejectsDualLifecycleAnnotationsOnSameMethod() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> beanLifecycleInvoker.invokeOnEnable(singleBean(new DualAnnotatedLifecycleBean()))
        );

        assertTrue(exception.getMessage().contains("both @OnEnable and @OnDisable"));
    }

    private Map<BeanDefinition, Object> singleBean(Object instance) {
        return Collections.singletonMap(
                new BeanDefinition(instance.getClass(), instance.getClass(), "testBean", false, null, null),
                instance
        );
    }

    static class ValidLifecycleBean {
        private String message;
        private Integer number;

        @OnEnable
        private void onEnable(String message, Integer number) {
            this.message = message;
            this.number = number;
        }
    }

    static class StaticLifecycleBean {
        @OnEnable
        private static void onEnable() {
        }
    }

    static class NonVoidLifecycleBean {
        @OnEnable
        private String onEnable() {
            return "invalid";
        }
    }

    static class DuplicateOnEnableBean {
        @OnEnable
        private void first() {
        }

        @OnEnable
        private void second() {
        }
    }

    static class BeanFactoryLifecycleBean {
        @Bean
        @OnEnable
        private void invalidFactoryMethod() {
        }
    }

    static class DualAnnotatedLifecycleBean {
        @OnEnable
        @OnDisable
        private void invalidLifecycle() {
        }
    }
}

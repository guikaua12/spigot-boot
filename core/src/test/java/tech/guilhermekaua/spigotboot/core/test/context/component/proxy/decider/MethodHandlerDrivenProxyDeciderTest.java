package tech.guilhermekaua.spigotboot.core.test.context.component.proxy.decider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.impl.MethodHandlerDrivenProxyDecider;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.annotation.*;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MethodHandlerDrivenProxyDeciderTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Intercept {
    }

    static class BeanWithAnnotatedMethod {
        @Intercept
        public void run() {
        }
    }

    static class BeanWithoutAnnotatedMethod {
        public void run() {
        }
    }

    @BeforeEach
    void setUp() {
        MethodHandlerRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        MethodHandlerRegistry.clear();
    }

    @Test
    void shouldReturnTrueWhenAtLeastOneRegisteredMethodHandlerCouldApply() {
        RegisteredMethodHandler handler = new RegisteredMethodHandler(
                context -> null,
                void.class,
                Annotation.class,
                Intercept.class
        );

        MethodHandlerRegistry.registerAll(Collections.singletonList(handler));

        MethodHandlerDrivenProxyDecider decider = new MethodHandlerDrivenProxyDecider();
        DependencyManager dependencyManager = new DependencyManager();

        BeanDefinition beanDefinition = new BeanDefinition(
                BeanWithAnnotatedMethod.class,
                BeanWithAnnotatedMethod.class,
                null,
                false,
                null,
                null
        );

        assertTrue(decider.shouldProxy(beanDefinition, dependencyManager));
    }

    @Test
    void shouldReturnFalseWhenNoRegisteredMethodHandlerCouldApply() {
        RegisteredMethodHandler handler = new RegisteredMethodHandler(
                context -> null,
                void.class,
                Annotation.class,
                Intercept.class
        );

        MethodHandlerRegistry.registerAll(Collections.singletonList(handler));

        MethodHandlerDrivenProxyDecider decider = new MethodHandlerDrivenProxyDecider();
        DependencyManager dependencyManager = new DependencyManager();

        BeanDefinition beanDefinition = new BeanDefinition(
                BeanWithoutAnnotatedMethod.class,
                BeanWithoutAnnotatedMethod.class,
                null,
                false,
                null,
                null
        );

        assertFalse(decider.shouldProxy(beanDefinition, dependencyManager));
    }
}



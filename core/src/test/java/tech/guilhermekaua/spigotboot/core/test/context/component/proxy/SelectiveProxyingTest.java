package tech.guilhermekaua.spigotboot.core.test.context.component.proxy;

import javassist.util.proxy.ProxyObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.impl.MethodHandlerDrivenProxyDecider;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.annotation.*;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class SelectiveProxyingTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Intercept {
    }

    static class NeedsProxy {
        @Intercept
        public String hello() {
            return "original";
        }

        public String other() {
            return "other";
        }
    }

    static class NoProxy {
        public String hello() {
            return "original";
        }
    }

    private DependencyManager dependencyManager;

    @BeforeEach
    void setUp() {
        MethodHandlerRegistry.clear();

        dependencyManager = new DependencyManager();

        // register the default decider (in production this is discovered via @Component scan)
        dependencyManager.registerDependency(new MethodHandlerDrivenProxyDecider(), null, true);

        RegisteredMethodHandler handler = new RegisteredMethodHandler(
                context -> "intercepted",
                void.class,
                Annotation.class,
                Intercept.class
        );

        MethodHandlerRegistry.registerAll(Collections.singletonList(handler));
    }

    @AfterEach
    void tearDown() {
        MethodHandlerRegistry.clear();
    }

    @Test
    void beanWithAnnotatedMethodShouldBeProxiedAndIntercepted() {
        dependencyManager.registerDependency(NeedsProxy.class, (String) null, false, null, null);

        NeedsProxy bean = dependencyManager.resolveDependency(NeedsProxy.class, null);
        assertNotNull(bean);

        assertInstanceOf(ProxyObject.class, bean, "Bean should be proxied due to matching MethodHandler metadata");
        assertEquals("intercepted", bean.hello(), "Annotated method should be intercepted by handler");
        assertEquals("other", bean.other(), "Non-annotated method should proceed normally");
    }

    @Test
    void beanWithoutAnnotatedMethodShouldNotBeProxied() {
        dependencyManager.registerDependency(NoProxy.class, (String) null, false, null, null);

        NoProxy bean = dependencyManager.resolveDependency(NoProxy.class, null);
        assertNotNull(bean);

        assertFalse(bean instanceof ProxyObject, "Bean should not be proxied when no handlers could apply");
        assertEquals("original", bean.hello());
    }
}



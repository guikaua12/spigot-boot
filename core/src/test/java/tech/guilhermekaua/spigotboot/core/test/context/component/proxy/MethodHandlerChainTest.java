package tech.guilhermekaua.spigotboot.core.test.context.component.proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.RegisterMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodHandlerChainTest {
    private static final List<String> CALL_LOG = new ArrayList<>();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Chained {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ShortCircuit {
    }

    public static class TargetBean {
        @Chained
        public String chained() {
            CALL_LOG.add("target");
            return "target";
        }

        @ShortCircuit
        public String shortCircuited() {
            CALL_LOG.add("short-target");
            return "short-target";
        }
    }

    @RegisterMethodHandler
    public static class TestHandlers {
        @MethodHandler(methodAnnotatedWith = Chained.class, order = -100)
        public Object outer(MethodHandlerContext context) throws Throwable {
            CALL_LOG.add("outer-before");
            Object result = context.invokeNext();
            CALL_LOG.add("outer-after");
            return result;
        }

        @MethodHandler(methodAnnotatedWith = Chained.class, order = 100)
        public Object inner(MethodHandlerContext context) throws Throwable {
            CALL_LOG.add("inner-before");
            Object result = context.invokeNext();
            CALL_LOG.add("inner-after");
            return result;
        }

        @MethodHandler(methodAnnotatedWith = ShortCircuit.class, order = -100)
        public Object shortCircuit(MethodHandlerContext context) {
            CALL_LOG.add("short-circuit");
            return "blocked";
        }
    }

    @BeforeEach
    void setUp() {
        CALL_LOG.clear();
        MethodHandlerRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        CALL_LOG.clear();
        MethodHandlerRegistry.clear();
    }

    @Test
    void shouldRunMatchingHandlersInAscendingAnnotationOrder() {
        registerHandlers();

        TargetBean proxy = ComponentProxy.createProxy(TargetBean.class, new TargetBean(), new Class<?>[0], new Object[0]);

        assertEquals("target", proxy.chained());
        assertEquals(
                Arrays.asList("outer-before", "inner-before", "target", "inner-after", "outer-after"),
                CALL_LOG
        );
    }

    @Test
    void shouldAllowHandlersToShortCircuitWithoutInvokingNext() {
        registerHandlers();

        TargetBean proxy = ComponentProxy.createProxy(TargetBean.class, new TargetBean(), new Class<?>[0], new Object[0]);

        assertEquals("blocked", proxy.shortCircuited());
        assertEquals(Arrays.asList("short-circuit"), CALL_LOG);
    }

    private void registerHandlers() {
        DependencyManager dependencyManager = new DependencyManager();
        dependencyManager.registerDependency(TestHandlers.class, null, false, null, null);

        MethodHandlerProcessor processor = new MethodHandlerProcessor();
        MethodHandlerRegistry.registerAll(
                processor.processFromPackage(TestHandlers.class.getPackage().getName(), dependencyManager)
        );
    }
}

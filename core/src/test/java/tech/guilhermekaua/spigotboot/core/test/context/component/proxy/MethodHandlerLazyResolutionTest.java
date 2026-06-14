package tech.guilhermekaua.spigotboot.core.test.context.component.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.RegisterMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// regression: method-handler beans must not be instantiated while processClass runs (the SCAN phase),
// because their transitive dependencies are only registered later (MODULES phase). see
// CustomPersistenceConfig-style null injection bug.
class MethodHandlerLazyResolutionTest {

    static int instantiations;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Marker {
    }

    @RegisterMethodHandler
    public static class CountingHandler {
        public CountingHandler() {
            instantiations++;
        }

        @MethodHandler(methodAnnotatedWith = Marker.class)
        public Object handle(MethodHandlerContext context) {
            return "handled";
        }
    }

    @BeforeEach
    void setUp() {
        instantiations = 0;
    }

    @Test
    void processClassDoesNotInstantiateHandlerEagerly() throws Throwable {
        DependencyManager dependencyManager = new DependencyManager();
        dependencyManager.registerDependency(CountingHandler.class, null, false, null, null);

        MethodHandlerProcessor processor = new MethodHandlerProcessor();
        List<RegisteredMethodHandler> handlers = processor.processClass(CountingHandler.class, dependencyManager);

        assertEquals(0, instantiations,
                "handler bean must not be instantiated during processClass (scan phase)");
        assertFalse(handlers.isEmpty(), "expected the @MethodHandler method to be registered");

        // a minimal, non-null context: the handler ignores it, but passing null would break the
        // moment a handler (or a copy of this test) starts reading the context.
        MethodHandlerContext context = new MethodHandlerContext(new Object(), null, null, new Object[0], () -> null);

        // the bean is resolved lazily, on first invocation
        Object result = handlers.get(0).getRunnable().handle(context);

        assertEquals("handled", result);
        assertEquals(1, instantiations, "handler bean must be instantiated exactly once, on first invocation");

        // a second invocation must reuse the cached singleton, not re-instantiate the handler
        Object secondResult = handlers.get(0).getRunnable().handle(context);

        assertEquals("handled", secondResult);
        assertEquals(1, instantiations, "handler bean must remain a singleton across invocations");
    }
}

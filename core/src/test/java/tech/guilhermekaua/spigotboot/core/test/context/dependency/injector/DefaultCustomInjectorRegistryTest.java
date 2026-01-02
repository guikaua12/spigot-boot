
package tech.guilhermekaua.spigotboot.core.test.context.dependency.injector;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.CustomInjector;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.DefaultCustomInjectorRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultCustomInjectorRegistryTest {

    static class Target {
        String value;
    }

    @Test
    void testRegisterNullThrows() {
        DefaultCustomInjectorRegistry registry = new DefaultCustomInjectorRegistry();

        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    void testGetInjectorsSortedAndStableForSameOrder() {
        DefaultCustomInjectorRegistry registry = new DefaultCustomInjectorRegistry();

        CustomInjector first = createTestInjector(0, false);
        CustomInjector higherPriority = createTestInjector(-1, false);
        CustomInjector second = createTestInjector(0, false);

        registry.register(first);
        registry.register(higherPriority);
        registry.register(second);

        List<CustomInjector> sorted = registry.getInjectors();
        assertEquals(3, sorted.size());
        assertSame(higherPriority, sorted.get(0));
        assertSame(first, sorted.get(1));
        assertSame(second, sorted.get(2));
    }

    @Test
    void testGetInjectorsIsUnmodifiable() {
        DefaultCustomInjectorRegistry registry = new DefaultCustomInjectorRegistry();
        registry.register(createTestInjector(0, false));

        List<CustomInjector> view = registry.getInjectors();

        assertThrows(UnsupportedOperationException.class, () -> view.add(createTestInjector(0, false)));
    }

    @Test
    void testUnregisterRemovesInjector() {
        DefaultCustomInjectorRegistry registry = new DefaultCustomInjectorRegistry();
        CustomInjector injector = createTestInjector(0, false);

        registry.register(injector);

        assertTrue(registry.unregister(injector));
        assertTrue(registry.isEmpty());
        assertEquals(0, registry.size());
        assertFalse(registry.getInjectors().contains(injector));
    }

    @Test
    void testCustomInjectorSupportedReturnsTrueWhenAnySupports() throws NoSuchFieldException {
        Field field = Target.class.getDeclaredField("value");
        InjectionPoint injectionPoint = InjectionPoint.fromField(field);

        DefaultCustomInjectorRegistry registry = new DefaultCustomInjectorRegistry();
        registry.register(createTestInjector(0, false));

        assertFalse(registry.customInjectorSupported(injectionPoint));

        registry.register(createTestInjector(0, true));

        assertTrue(registry.customInjectorSupported(injectionPoint));
    }

    @Test
    void testClearResetsRegistry() {
        DefaultCustomInjectorRegistry registry = new DefaultCustomInjectorRegistry();
        registry.register(createTestInjector(1, false));
        registry.register(createTestInjector(-1, false));

        registry.clear();

        assertTrue(registry.isEmpty());
        assertEquals(0, registry.size());
        assertTrue(registry.getInjectors().isEmpty());
    }

    private static @NotNull CustomInjector createTestInjector(int order, boolean supports) {
        return new CustomInjector() {
            @Override
            public boolean supports(@NotNull InjectionPoint injectionPoint) {
                return supports;
            }

            @Override
            public @NotNull InjectionResult resolve(@NotNull InjectionPoint injectionPoint) {
                return InjectionResult.notHandled();
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }
}


package tech.guilhermekaua.spigotboot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.module.Module;
import tech.guilhermekaua.spigotboot.core.module.OrderedTestModule;
import tech.guilhermekaua.spigotboot.core.module.TestModule;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SpigotBootBuilderTest {

    private BootPlugin mockPlugin;

    @BeforeEach
    void setUp() {
        mockPlugin = new BootPlugin() {
            @Override
            public String getName() {
                return "TestPlugin";
            }

            @Override
            public Logger getLogger() {
                return Logger.getLogger("TestPlugin");
            }

            @Override
            public File getDataFolder() {
                return new File(".");
            }

            @Override
            public InputStream getResource(String path) {
                return null;
            }

            @Override
            public ClassLoader getClassLoader() {
                return SpigotBootBuilderTest.class.getClassLoader();
            }

            @Override
            public Class<?> getMainClass() {
                return SpigotBootBuilderTest.class;
            }

            @Override
            public Object getNativePlugin() {
                return null;
            }
        };
    }

    @Test
    void resolveModules_noModulesReturnsEmptyList() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        List<Class<? extends Module>> result = builder.resolveModules();

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveModules_explicitModulesPreservesAll() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        builder.modules(TestModule.class, OrderedTestModule.class);
        List<Class<? extends Module>> result = builder.resolveModules();

        assertEquals(2, result.size());
        assertTrue(result.contains(TestModule.class));
        assertTrue(result.contains(OrderedTestModule.class));
    }

    @Test
    void resolveModules_sortsByOrderAnnotation() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        // TestModule has no @Order (defaults to 0), OrderedTestModule has @Order(-500)
        builder.modules(TestModule.class, OrderedTestModule.class);
        List<Class<? extends Module>> result = builder.resolveModules();

        assertEquals(OrderedTestModule.class, result.get(0), "OrderedTestModule (-500) should come first");
        assertEquals(TestModule.class, result.get(1), "TestModule (0) should come second");
    }

    @Test
    void resolveModules_explicitOrderOverridesAnnotation() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        // give TestModule a lower order than OrderedTestModule's @Order(-500)
        builder.module(TestModule.class, -1000);
        builder.module(OrderedTestModule.class);

        List<Class<? extends Module>> result = builder.resolveModules();

        assertEquals(TestModule.class, result.get(0), "TestModule (explicit -1000) should come first");
        assertEquals(OrderedTestModule.class, result.get(1), "OrderedTestModule (-500) should come second");
    }

    @Test
    void resolveModules_excludeRemovesModules() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        builder.autoDiscover();
        builder.exclude(TestModule.class);

        List<Class<? extends Module>> result = builder.resolveModules();

        assertFalse(result.contains(TestModule.class), "TestModule should be excluded");
        assertTrue(result.contains(OrderedTestModule.class), "OrderedTestModule should remain");
    }

    @Test
    void resolveModules_autoDiscoverFindsModules() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        builder.autoDiscover();

        List<Class<? extends Module>> result = builder.resolveModules();

        assertFalse(result.isEmpty(), "auto-discover should find modules");
        assertTrue(result.contains(TestModule.class));
        assertTrue(result.contains(OrderedTestModule.class));
    }

    @Test
    void resolveModules_autoDiscoverSortsByOrder() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        builder.autoDiscover();

        List<Class<? extends Module>> result = builder.resolveModules();

        int orderedIdx = result.indexOf(OrderedTestModule.class);
        int testIdx = result.indexOf(TestModule.class);
        assertTrue(orderedIdx < testIdx, "OrderedTestModule (@Order(-500)) should come before TestModule (default 0)");
    }

    @Test
    void resolveModules_autoDiscoverWithExplicitOrderOverride() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        builder.autoDiscover();
        // override TestModule to have lower order than OrderedTestModule
        builder.module(TestModule.class, -1000);

        List<Class<? extends Module>> result = builder.resolveModules();

        assertEquals(TestModule.class, result.get(0),
                "TestModule (explicit -1000) should come before OrderedTestModule (-500)");
    }

    @Test
    void resolveModules_autoDiscoverWithAdditionalModule() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        builder.autoDiscover();
        builder.module(ExtraTestModule.class, 100);

        List<Class<? extends Module>> result = builder.resolveModules();

        assertTrue(result.contains(ExtraTestModule.class), "Extra module should be added");
    }

    @Test
    void resolveModules_bulkAndPerModuleAccumulate() {
        SpigotBootBuilder builder = new SpigotBootBuilder(mockPlugin);
        builder.modules(TestModule.class, OrderedTestModule.class);
        builder.module(ExtraTestModule.class, -100);

        List<Class<? extends Module>> result = builder.resolveModules();

        assertEquals(3, result.size());
        assertEquals(OrderedTestModule.class, result.get(0)); // @Order(-500)
        assertEquals(ExtraTestModule.class, result.get(1));    // explicit -100
        assertEquals(TestModule.class, result.get(2));          // default 0
    }

    @Order(50)
    static class ExtraTestModule implements Module {
        @Override
        public void onInitialize(Context context) {
        }
    }
}

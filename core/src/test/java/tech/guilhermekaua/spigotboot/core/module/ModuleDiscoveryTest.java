package tech.guilhermekaua.spigotboot.core.module;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleDiscoveryTest {

    @Test
    void discover_findsMarkerFilesFromTestClasspath() {
        List<Class<? extends Module>> modules = new ModuleDiscovery(getClass().getClassLoader()).discover();

        assertFalse(modules.isEmpty(), "should discover at least one module");
        assertTrue(modules.contains(TestModule.class), "should discover TestModule");
        assertTrue(modules.contains(OrderedTestModule.class), "should discover OrderedTestModule");
    }

    @Test
    void discover_skipsClassNotFound() {
        Set<String> blockedClasses = Set.of(
                "tech.guilhermekaua.spigotboot.core.module.TestModule",
                "tech.guilhermekaua.spigotboot.core.module.OrderedTestModule"
        );

        // classloader that finds marker resources but cannot load the module classes
        ClassLoader blockingLoader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (blockedClasses.contains(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }

            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return getParent().getResources(name);
            }
        };

        List<Class<? extends Module>> modules = new ModuleDiscovery(blockingLoader).discover();

        // both module classes are blocked, so the result should be empty
        assertTrue(modules.isEmpty(), "should return empty list when classes cannot be loaded");
    }

    @Test
    void discover_returnsEmptyForClassloaderWithoutMarkers() {
        // empty classloader with no parent
        ClassLoader emptyLoader = new URLClassLoader(new URL[0], null);
        List<Class<? extends Module>> modules = new ModuleDiscovery(emptyLoader).discover();

        assertTrue(modules.isEmpty(), "should return empty list when no markers found");
    }
}

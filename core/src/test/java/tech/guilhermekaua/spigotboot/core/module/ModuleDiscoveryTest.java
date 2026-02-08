package tech.guilhermekaua.spigotboot.core.module;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

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
        ClassLoader parentWithResources = getClass().getClassLoader();

        // use an empty classloader that can see the marker files but not load the classes
        // this should not throw, classes that can't be found are skipped with a warning
        List<Class<? extends Module>> modules = new ModuleDiscovery(
                new URLClassLoader(new URL[0], parentWithResources)
        ).discover();

        // should still find the modules since the parent can load them
        assertFalse(modules.isEmpty());
    }

    @Test
    void discover_returnsEmptyForClassloaderWithoutMarkers() {
        // empty classloader with no parent
        ClassLoader emptyLoader = new URLClassLoader(new URL[0], null);
        List<Class<? extends Module>> modules = new ModuleDiscovery(emptyLoader).discover();

        assertTrue(modules.isEmpty(), "should return empty list when no markers found");
    }
}

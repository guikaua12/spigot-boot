package tech.guilhermekaua.spigotboot.core.context.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextLifecyclePackageScanTest {

    @Test
    void minimizePackageRoots_filtersNestedPackages() {
        List<String> roots = ContextLifecycle.minimizePackageRoots(Arrays.asList(
                "tech.guilhermekaua.spigotboot.core",
                "com.example.plugin",
                "com.example.plugin.modules.config",
                "com.example.plugin.modules.data"
        ));

        assertEquals(Arrays.asList(
                "tech.guilhermekaua.spigotboot.core",
                "com.example.plugin"
        ), roots);
    }

    @Test
    void minimizePackageRoots_keepsSiblingRoots() {
        List<String> roots = ContextLifecycle.minimizePackageRoots(Arrays.asList(
                "com.example.plugin",
                "com.example.shared",
                "com.example.plugin.module"
        ));

        assertEquals(Arrays.asList(
                "com.example.plugin",
                "com.example.shared"
        ), roots);
    }

    @Test
    void minimizePackageRoots_handlesParentAppearingAfterChild() {
        List<String> roots = ContextLifecycle.minimizePackageRoots(Arrays.asList(
                "com.example.plugin.modules.config",
                "com.example.plugin",
                "com.example.shared"
        ));

        assertEquals(Arrays.asList(
                "com.example.plugin",
                "com.example.shared"
        ), roots);
    }

    @Test
    void minimizePackageRoots_ignoresBlankEntries() {
        List<String> roots = ContextLifecycle.minimizePackageRoots(Arrays.asList(
                " ",
                null,
                "com.example.plugin",
                "com.example.plugin.module"
        ));

        assertEquals(Arrays.asList("com.example.plugin"), roots);
    }
}

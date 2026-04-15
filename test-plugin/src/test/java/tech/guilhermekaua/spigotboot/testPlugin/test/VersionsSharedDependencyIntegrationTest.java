package tech.guilhermekaua.spigotboot.testPlugin.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.runtime.VersionedPlatform;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.SpigotVersionBootstrap;
import tech.guilhermekaua.spigotboot.versions.runtime.registry.VersionAdapterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsSharedDependencyIntegrationTest {

    @AfterEach
    void tearDown() {
        VersionAdapterRegistry.clear();
    }

    @Test
    void boot_discoversSharedVersionAdaptersWithoutExplicitRegistryRegistration() {
        VersionAdapterRegistry.clear();
        assertTrue(VersionAdapterRegistry.registeredAdapters().isEmpty());
        assertEquals(
                "tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter",
                VersionAdapter.class.getName()
        );

        VersionedPlatform platform = SpigotVersionBootstrap.boot("1.21.11");
        VersionAdapter adapter = assertInstanceOf(VersionAdapter.class, platform.adapter());

        assertEquals(
                "tech.guilhermekaua.spigotboot.v1_21_11.entity.SpigotVersionAdapterV1_21_11",
                adapter.getClass().getName()
        );
        assertTrue(
                VersionAdapterRegistry.registeredAdapters().isEmpty(),
                "shared dependency resolution should not require explicit registry registration"
        );
    }
}

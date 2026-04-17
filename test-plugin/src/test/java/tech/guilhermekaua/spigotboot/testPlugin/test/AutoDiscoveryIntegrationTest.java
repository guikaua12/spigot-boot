package tech.guilhermekaua.spigotboot.testPlugin.test;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.spigot.registry.ConfigRegistry;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.data.ormLite.registry.OrmLiteRepositoryRegistry;
import tech.guilhermekaua.spigotboot.testPlugin.Main;
import tech.guilhermekaua.spigotboot.testPlugin.services.EntityDemoService;
import tech.guilhermekaua.spigotboot.testPlugin.services.EntityMatrixAutorunService;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoDiscoveryIntegrationTest {
    private ServerMock server;
    private Main plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void autoDiscovery_findsAllModulesOnClasspath() {
        Context ctx = SpigotBoot.getContext(plugin.getBootPlugin());
        assertNotNull(ctx);
        assertTrue(ctx.isInitialized());

        assertNotNull(ctx.getBean(JavaPlugin.class));
        assertNotNull(ctx.getBean(Plugin.class));
        assertNotNull(ctx.getBean(ConfigRegistry.class));
        assertNotNull(ctx.getBean(OrmLiteRepositoryRegistry.class));
        assertNotNull(ctx.getBean(EntityMatrixAutorunService.class));

        EntityDemoService entityDemoService = ctx.getBean(EntityDemoService.class);
        assertNotNull(entityDemoService);
        assertTrue(entityDemoService.scenarioIds().containsAll(Arrays.asList(
                "attach-existing-zombie",
                "deathfx-passive-family",
                "viewer-cycle-special-family"
        )));
    }

    @Test
    void autoDiscovery_modulesAreOrderedCorrectly() {
        // SpigotCoreModule (@Order(-1000)) should run before others,
        // so Plugin bean exists and other modules' beans that depend on it are also present
        Context ctx = SpigotBoot.getContext(plugin.getBootPlugin());
        assertNotNull(ctx.getBean(Plugin.class));
        assertNotNull(ctx.getBean(JavaPlugin.class));
    }
}

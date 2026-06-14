package tech.guilhermekaua.spigotboot.testPlugin.test;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.SpigotBoot;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.data.config.PersistenceConfig;
import tech.guilhermekaua.spigotboot.testPlugin.Main;
import tech.guilhermekaua.spigotboot.testPlugin.configuration.CustomPersistenceConfig;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// regression: a PersistenceConfig component must receive its constructor dependencies. it used to be
// instantiated during the scan phase (pulled in by data-jdbc's @RegisterMethodHandler beans), before
// the modules that register JavaPlugin and @Config beans had run, so both came in null.
class PersistenceConfigInjectionTest {
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
    void persistenceConfigComponentReceivesItsConstructorDependencies() throws Exception {
        Context ctx = SpigotBoot.getContext(plugin.getBootPlugin());
        assertNotNull(ctx);
        assertTrue(ctx.isInitialized());

        PersistenceConfig persistenceConfig = ctx.getBean(PersistenceConfig.class);
        assertInstanceOf(CustomPersistenceConfig.class, persistenceConfig);

        assertNotNull(readField(persistenceConfig, "config"), "MainConfig dependency must be injected, not null");
        assertNotNull(readField(persistenceConfig, "plugin"), "JavaPlugin dependency must be injected, not null");
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

package me.approximations.spigotboot.placeholder;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.approximations.spigotboot.core.ApxPlugin;
import me.approximations.spigotboot.placeholder.registry.PlaceholderRegistry;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.logging.Logger;

import static org.mockito.Mockito.*;

class PlaceholderModuleTest {

    private ServerMock server;
    private ApxPlugin plugin;
    private PlaceholderRegistry registry;
    private Logger logger;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(ApxPlugin.class);

        registry = mock(PlaceholderRegistry.class);

        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testInitialize_PlaceholderApiPresent() throws Exception {
        Plugin papi = MockBukkit.createMockPlugin("PlaceholderAPI");

        PlaceholderModule module = new PlaceholderModule(plugin, registry);
        module.initialize();

        verify(logger).info("Initializing Placeholder Module...");
        verify(registry).initialize();
        verify(plugin).addDisableEntry(any());
    }

    @Test
    void testInitialize_PlaceholderApiAbsent() throws Exception {
        PlaceholderModule module = new PlaceholderModule(plugin, registry);
        module.initialize();

        verify(logger).info("Initializing Placeholder Module...");
        verify(logger).info("PlaceholderAPI not found, skipping registration of placeholders.");
        verify(registry, never()).initialize();
        verify(plugin, never()).addDisableEntry(any());
    }

    @Test
    void testDisableEntry_UnregistersIfPluginEnabled() throws Exception {
        Plugin papi = MockBukkit.createMockPlugin("PlaceholderAPI");

        PlaceholderModule module = new PlaceholderModule(plugin, registry);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        module.initialize();
        verify(plugin).addDisableEntry(captor.capture());

        Runnable disableEntry = captor.getValue();
        disableEntry.run();

        verify(logger).info("Unregistering PlaceholderAPI expansion...");
        verify(registry).unregister();
    }

    @Test
    void testDisableEntry_DoesNotUnregisterIfPluginDisabled() throws Exception {
        Plugin papi = MockBukkit.createMockPlugin("PlaceholderAPI");

        PlaceholderModule module = new PlaceholderModule(plugin, registry);
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        module.initialize();
        verify(plugin).addDisableEntry(captor.capture());

        server.getPluginManager().disablePlugin(papi);

        Runnable disableEntry = captor.getValue();
        disableEntry.run();

        verify(logger).info("Unregistering PlaceholderAPI expansion...");
        verify(registry, never()).unregister();
    }
}


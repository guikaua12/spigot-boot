/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.config.bungee.test.reload;

import net.md_5.bungee.api.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.OnConfigReload;
import tech.guilhermekaua.spigotboot.config.bungee.BungeeConfigManager;
import tech.guilhermekaua.spigotboot.config.bungee.reload.OnConfigReloadProcessor;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * End-to-end integration test for {@code @OnConfigReload} that exercises the real config machinery:
 * a real {@link BungeeConfigManager} over a temp data folder, a real {@code @Config} fixture loaded
 * from a YAML file, the real {@link OnConfigReloadProcessor} binding listeners, and a real
 * {@link BungeeConfigManager#reload(Class)} dispatching to those listeners. Nothing about the config
 * manager or its reload pipeline is mocked.
 */
@ExtendWith(MockitoExtension.class)
class OnConfigReloadProcessorIntegrationTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private BungeeConfigManager configManager;
    private OnConfigReloadProcessor processor;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(OnConfigReloadProcessorIntegrationTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new BungeeConfigManager(plugin);
        processor = new OnConfigReloadProcessor(configManager, logger);
    }

    @Test
    void reload_invokesConfigParamCallbackWithNewValue() throws IOException {
        writeYaml("reload-config.yml", "message: initial\n");
        configManager.register(ReloadConfig.class);
        configManager.initializeAll();

        // sanity: initial value bound from disk before any reload
        assertEquals("initial", configManager.get(ReloadConfig.class).getMessage());

        ConfigParamListener bean = new ConfigParamListener();
        runProcessor(bean);

        // real reload: overwrite the backing file with a new value, then reload the config
        writeYaml("reload-config.yml", "message: reloaded\n");
        configManager.reload(ReloadConfig.class);

        ReloadConfig received = bean.received.get();
        assertNotNull(received, "callback should have fired and received the reloaded config");
        assertEquals("reloaded", received.getMessage(),
                "callback must receive a config bound to the NEW on-disk value, not the old one");
        // and the manager itself reflects the new value too
        assertEquals("reloaded", configManager.get(ReloadConfig.class).getMessage());
    }

    @Test
    void reload_invokesZeroArgCallback() throws IOException {
        writeYaml("reload-config.yml", "message: initial\n");
        configManager.register(ReloadConfig.class);
        configManager.initializeAll();

        ZeroArgListener bean = new ZeroArgListener();
        runProcessor(bean);

        assertFalse(bean.fired, "callback must not fire before a reload");
        assertEquals(0, bean.count.get());

        writeYaml("reload-config.yml", "message: reloaded\n");
        configManager.reload(ReloadConfig.class);

        assertTrue(bean.fired, "zero-arg callback should have fired on reload");
        assertEquals(1, bean.count.get(), "callback should fire exactly once per reload");
    }

    private void runProcessor(Object bean) {
        BeanDefinition definition = mock(BeanDefinition.class);
        DependencyManager dependencyManager = mock(DependencyManager.class);
        Object result = processor.postProcess(definition, bean, dependencyManager);
        assertSame(bean, result, "processor must return the same bean instance");
    }

    private void writeYaml(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }

    @Config(value = "reload-config.yml", name = "reload", generateDefaults = false)
    public static class ReloadConfig {
        private String message = "default";

        public ReloadConfig() {
        }

        public String getMessage() {
            return message;
        }
    }

    static class ConfigParamListener {
        final AtomicReference<ReloadConfig> received = new AtomicReference<>();

        @OnConfigReload(ReloadConfig.class)
        void onReload(ReloadConfig cfg) {
            received.set(cfg);
        }
    }

    static class ZeroArgListener {
        volatile boolean fired = false;
        final AtomicInteger count = new AtomicInteger();

        @OnConfigReload(ReloadConfig.class)
        void onReload() {
            fired = true;
            count.incrementAndGet();
        }
    }
}

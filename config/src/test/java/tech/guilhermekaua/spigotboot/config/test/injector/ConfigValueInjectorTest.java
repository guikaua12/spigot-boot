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
package tech.guilhermekaua.spigotboot.config.test.injector;

import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.DefaultConfigManager;
import tech.guilhermekaua.spigotboot.config.injector.ConfigValueInjector;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionResult;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ConfigValueInjectorTest {

    @TempDir
    Path tempDir;

    @Mock
    BootPlugin plugin;

    private DefaultConfigManager configManager;
    private ConfigValueInjector injector;

    @BeforeEach
    void setUp() throws IOException {
        MethodHandlerRegistry.clear();
        Logger logger = Logger.getLogger(ConfigValueInjectorTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new DefaultConfigManager(plugin);
        Files.writeString(tempDir.resolve("app.yml"), "name: hello\nport: 25565\n");
        configManager.register(AppConfig.class);
        configManager.initializeAll();
        injector = new ConfigValueInjector(configManager);
    }

    @AfterEach
    void tearDown() {
        MethodHandlerRegistry.clear();
    }

    @Test
    void supports_onlyWhenAnnotationPresent() throws NoSuchFieldException {
        assertTrue(injector.supports(InjectionPoint.fromField(FieldBean.class.getDeclaredField("name"))));
        assertFalse(injector.supports(InjectionPoint.fromField(FieldBean.class.getDeclaredField("untouched"))));
    }

    @Test
    void order_isMinus100() {
        assertEquals(-100, injector.getOrder());
    }

    @Test
    void resolve_returnsHandledValue() throws NoSuchFieldException {
        InjectionResult result = injector.resolve(InjectionPoint.fromField(FieldBean.class.getDeclaredField("name")));
        assertTrue(result.isHandled());
        assertEquals("hello", result.getValue());
    }

    @Test
    void di_injectsFieldWithoutInjectAnnotation() {
        DependencyManager dm = new DependencyManager();
        dm.registerInjector(injector);
        dm.registerDependency(FieldBean.class, null, false, null, null);

        FieldBean bean = dm.resolveDependency(FieldBean.class, null);

        assertNotNull(bean);
        assertEquals("hello", bean.name);
        assertEquals(25565, bean.port);
        assertNull(bean.untouched);
    }

    @Test
    void di_injectsConstructorParameter() {
        DependencyManager dm = new DependencyManager();
        dm.registerInjector(injector);
        dm.registerDependency(CtorBean.class, null, false, null, null);

        CtorBean bean = dm.resolveDependency(CtorBean.class, null);

        assertNotNull(bean);
        assertEquals("hello", bean.name);
    }

    @Test
    void di_requiredMissing_failsBeanCreation() {
        DependencyManager dm = new DependencyManager();
        dm.registerInjector(injector);
        dm.registerDependency(RequiredMissingBean.class, null, false, null, null);

        assertThrows(RuntimeException.class, () -> dm.resolveDependency(RequiredMissingBean.class, null));
    }

    // ---- fixtures ----

    @Config(value = "app.yml", name = "app", generateDefaults = false)
    public static class AppConfig {
        private String name;
        private int port;
        public AppConfig() {}
    }

    static class FieldBean {
        @ConfigValue("app:name") String name;
        @ConfigValue("app:port") int port;
        String untouched;
    }

    static class CtorBean {
        final String name;
        CtorBean(@ConfigValue("app:name") String name) {
            this.name = name;
        }
    }

    static class RequiredMissingBean {
        @ConfigValue("app:missing") String missing;
    }
}

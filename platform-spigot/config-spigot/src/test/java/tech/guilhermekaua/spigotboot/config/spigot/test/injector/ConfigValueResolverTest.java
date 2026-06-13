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
package tech.guilhermekaua.spigotboot.config.spigot.test.injector;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.annotation.ConfigValue;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.config.spigot.injector.ConfigValueResolver;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ConfigValueResolverTest {

    @TempDir
    Path tempDir;

    @Mock
    Plugin plugin;

    private SpigotConfigManager configManager;
    private ConfigValueResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        Logger logger = Logger.getLogger(ConfigValueResolverTest.class.getName());
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(logger);
        configManager = new SpigotConfigManager(plugin);
        Files.writeString(tempDir.resolve("app.yml"),
                "name: hello\nport: 25565\nenabled: true\nratio: 1.5\n");
        configManager.register(AppConfig.class);
        configManager.initializeAll();
        resolver = new ConfigValueResolver(configManager);
    }

    private Object resolveField(Class<?> beanType, String fieldName) throws NoSuchFieldException {
        return resolver.resolve(InjectionPoint.fromField(beanType.getDeclaredField(fieldName)));
    }

    @Test
    void valueMode_resolvesScalars() throws NoSuchFieldException {
        assertEquals("hello", resolveField(ValueModeBean.class, "name"));
        assertEquals(25565, ((Number) resolveField(ValueModeBean.class, "port")).intValue());
        assertEquals(Boolean.TRUE, resolveField(ValueModeBean.class, "enabled"));
        assertEquals(1.5, ((Number) resolveField(ValueModeBean.class, "ratio")).doubleValue());
    }

    @Test
    void configMode_resolvesByClassAndPath() throws NoSuchFieldException {
        assertEquals("hello", resolveField(ConfigModeBean.class, "name"));
    }

    @Test
    void default_appliedWhenAbsent() throws NoSuchFieldException {
        assertEquals("fallback", resolveField(DefaultBean.class, "missing"));
        assertEquals(7, ((Number) resolveField(DefaultBean.class, "missingNum")).intValue());
        assertEquals("", resolveField(DefaultBean.class, "emptyDefault"));
    }

    @Test
    void requiredMissing_throwsNamingSite() throws NoSuchFieldException {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(RequiredMissingBean.class, "missing"));
        assertTrue(ex.getMessage().contains(RequiredMissingBean.class.getName() + "#missing"));
    }

    @Test
    void neitherValueNorConfig_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(NeitherBean.class, "x"));
        assertTrue(ex.getMessage().contains("value() or config()"));
    }

    @Test
    void configWithoutPath_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(ConfigNoPathBean.class, "x"));
        assertTrue(ex.getMessage().contains("path()"));
    }

    @Test
    void unsupportedType_throws() throws NoSuchFieldException {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(UnsupportedTypeBean.class, "list"));
        assertTrue(ex.getMessage().contains(UnsupportedTypeBean.class.getName() + "#list"));
    }

    @Test
    void arrayType_throws() throws NoSuchFieldException {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(ArrayTypeBean.class, "arr"));
        assertTrue(ex.getMessage().contains("not supported"));
    }

    @Test
    void unknownConfigClass_throws() {
        ConfigException ex = assertThrows(ConfigException.class,
                () -> resolveField(UnknownConfigBean.class, "x"));
        assertTrue(ex.getMessage().contains(UnregisteredConfig.class.getName()));
    }

    @Test
    void notInitialized_throws() throws IOException, NoSuchFieldException {
        Files.writeString(tempDir.resolve("late.yml"), "name: x\n");
        SpigotConfigManager notInit = new SpigotConfigManager(plugin);
        notInit.register(LateConfig.class); // registered but NOT initialized
        ConfigValueResolver lateResolver = new ConfigValueResolver(notInit);

        ConfigException ex = assertThrows(ConfigException.class, () -> lateResolver.resolve(
                InjectionPoint.fromField(LateBean.class.getDeclaredField("name"))));
        assertTrue(ex.getMessage().contains("not initialized"));
    }

    @Test
    void barePathWithMultipleConfigs_throwsNamingSite() throws IOException, NoSuchFieldException {
        Files.writeString(tempDir.resolve("a.yml"), "k: 1\n");
        Files.writeString(tempDir.resolve("b.yml"), "k: 2\n");
        SpigotConfigManager multi = new SpigotConfigManager(plugin);
        multi.register(AConfig.class);
        multi.register(BConfig.class);
        multi.initializeAll();
        ConfigValueResolver multiResolver = new ConfigValueResolver(multi);

        ConfigException ex = assertThrows(ConfigException.class, () -> multiResolver.resolve(
                InjectionPoint.fromField(BarePathBean.class.getDeclaredField("k"))));
        assertTrue(ex.getMessage().contains("Ambiguous"));
        assertTrue(ex.getMessage().contains(BarePathBean.class.getName()));
    }

    // ---- fixtures ----

    @Config(value = "app.yml", name = "app", generateDefaults = false)
    public static class AppConfig {
        private String name;
        private int port;
        private boolean enabled;
        private double ratio;
        public AppConfig() {}
    }

    @Config(value = "late.yml", name = "late", generateDefaults = false)
    public static class LateConfig {
        private String name;
        public LateConfig() {}
    }

    @Config(value = "unregistered.yml", name = "unregistered", generateDefaults = false)
    public static class UnregisteredConfig {
        private String name;
        public UnregisteredConfig() {}
    }

    @Config(value = "a.yml", name = "a", generateDefaults = false)
    public static class AConfig {
        private int k;
        public AConfig() {}
    }

    @Config(value = "b.yml", name = "b", generateDefaults = false)
    public static class BConfig {
        private int k;
        public BConfig() {}
    }

    static class ValueModeBean {
        @ConfigValue("app:name") String name;
        @ConfigValue("app:port") int port;
        @ConfigValue("app:enabled") boolean enabled;
        @ConfigValue("app:ratio") double ratio;
    }

    static class ConfigModeBean {
        @ConfigValue(config = AppConfig.class, path = "name") String name;
    }

    static class DefaultBean {
        @ConfigValue(value = "app:missing", defaultValue = "fallback") String missing;
        @ConfigValue(value = "app:missingNum", defaultValue = "7") int missingNum;
        @ConfigValue(value = "app:emptyDefault", defaultValue = "") String emptyDefault;
    }

    static class RequiredMissingBean {
        @ConfigValue("app:missing") String missing;
    }

    static class NeitherBean {
        @ConfigValue String x;
    }

    static class ConfigNoPathBean {
        @ConfigValue(config = AppConfig.class) String x;
    }

    static class UnsupportedTypeBean {
        @ConfigValue("app:name") List<String> list;
    }

    static class ArrayTypeBean {
        @ConfigValue("app:name") String[] arr;
    }

    static class UnknownConfigBean {
        @ConfigValue(config = UnregisteredConfig.class, path = "name") String x;
    }

    static class LateBean {
        @ConfigValue("late:name") String name;
    }

    static class BarePathBean {
        @ConfigValue("k") int k;
    }
}

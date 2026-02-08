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
package tech.guilhermekaua.spigotboot.config.spigot.test.registry;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.guilhermekaua.spigotboot.config.ConfigManager;
import tech.guilhermekaua.spigotboot.config.annotation.Config;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.reload.ConfigRef;
import tech.guilhermekaua.spigotboot.config.spigot.registry.ConfigRegistry;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Primary;
import tech.guilhermekaua.spigotboot.core.context.annotations.Qualifier;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConfigRegistryTest {

    private ConfigRegistry configRegistry;
    private DependencyManager dependencyManager;

    @Mock
    private Context context;

    @Mock
    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        configRegistry = new ConfigRegistry();
        dependencyManager = new DependencyManager();

        lenient().when(context.getBean(ConfigManager.class)).thenReturn(configManager);
        lenient().when(context.getDependencyManager()).thenReturn(dependencyManager);
    }

    @Config("valid-config.yml")
    public static class ValidConfig {
        private String name;
        private int value;

        public ValidConfig() {
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    @Config("invalid-config.yml")
    public static class InvalidConfigWithPublicField {
        public String publicField;
        private String privateField;
    }

    @Config("invalid-config2.yml")
    public static class InvalidConfigWithProtectedField {
        protected String protectedField;
    }

    @Config("invalid-config3.yml")
    public static class InvalidConfigWithPackagePrivateField {
        String packagePrivateField;
    }

    @Config("nested-config.yml")
    public static class NestedConfig {
        private NestedSection section;

        public NestedConfig() {
        }

        public NestedSection getSection() {
            return section;
        }

        public static class NestedSection {
            private String value;

            public String getValue() {
                return value;
            }
        }
    }

    @Config("qualified-config.yml")
    @Qualifier("qualified")
    public static class QualifiedConfig {
        private String data;

        public QualifiedConfig() {
        }

        public String getData() {
            return data;
        }
    }

    @Config("primary-config.yml")
    @Primary
    public static class PrimaryConfig {
        private String data;

        public PrimaryConfig() {
        }

        public String getData() {
            return data;
        }
    }

    @Config("empty-config.yml")
    public static class EmptyConfig {
        public EmptyConfig() {
        }
    }

    static class MockConfigRef<T> implements ConfigRef<T> {
        private final Class<T> configClass;
        private T instance;

        @SuppressWarnings("unchecked")
        MockConfigRef(Class<T> configClass) {
            this.configClass = configClass;
            try {
                this.instance = configClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                this.instance = null;
            }
        }

        @Override
        public @NotNull T get() {
            return instance;
        }

        @Override
        public void addListener(@NotNull Consumer<T> listener) {
        }

        @Override
        public void removeListener(@NotNull Consumer<T> listener) {
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public void reload() {
        }

        @Override
        public @NotNull Class<T> getConfigClass() {
            return configClass;
        }
    }

    @Test
    void testProcessConfigClass_WithValidConfig() {
        ConfigRef<ValidConfig> configRef = new MockConfigRef<>(ValidConfig.class);
        when(configManager.getRef(ValidConfig.class)).thenReturn(configRef);

        assertDoesNotThrow(() -> configRegistry.processConfigClass(ValidConfig.class, context, configManager));

        verify(configManager).register(ValidConfig.class);
        verify(configManager).getRef(ValidConfig.class);

        ValidConfig resolved = dependencyManager.resolveDependency(ValidConfig.class, null);
        assertNotNull(resolved);
    }

    @Test
    void testProcessConfigClass_WithQualifiedConfig() {
        ConfigRef<QualifiedConfig> configRef = new MockConfigRef<>(QualifiedConfig.class);
        when(configManager.getRef(QualifiedConfig.class)).thenReturn(configRef);

        assertDoesNotThrow(() -> configRegistry.processConfigClass(QualifiedConfig.class, context, configManager));

        QualifiedConfig resolved = dependencyManager.resolveDependency(QualifiedConfig.class, "qualified");
        assertNotNull(resolved);
    }

    @Test
    void testProcessConfigClass_WithPrimaryConfig() {
        ConfigRef<PrimaryConfig> configRef = new MockConfigRef<>(PrimaryConfig.class);
        when(configManager.getRef(PrimaryConfig.class)).thenReturn(configRef);

        assertDoesNotThrow(() -> configRegistry.processConfigClass(PrimaryConfig.class, context, configManager));

        PrimaryConfig resolved = dependencyManager.resolveDependency(PrimaryConfig.class, null);
        assertNotNull(resolved);
    }

    @Test
    void testProcessConfigClass_WithEmptyConfig() {
        ConfigRef<EmptyConfig> configRef = new MockConfigRef<>(EmptyConfig.class);
        when(configManager.getRef(EmptyConfig.class)).thenReturn(configRef);

        assertDoesNotThrow(() -> configRegistry.processConfigClass(EmptyConfig.class, context, configManager));

        verify(configManager).register(EmptyConfig.class);

        EmptyConfig resolved = dependencyManager.resolveDependency(EmptyConfig.class, null);
        assertNotNull(resolved);
    }

    @Test
    void testValidateConfigClass_WithPublicField_ThrowsException() {
        ConfigException exception = assertThrows(ConfigException.class, () ->
                configRegistry.processConfigClass(InvalidConfigWithPublicField.class, context, configManager)
        );

        assertTrue(exception.getMessage().contains("non-private field"));
        assertTrue(exception.getMessage().contains("publicField"));
        assertTrue(exception.getMessage().contains("reload safety"));
    }

    @Test
    void testValidateConfigClass_WithProtectedField_ThrowsException() {
        ConfigException exception = assertThrows(ConfigException.class, () ->
                configRegistry.processConfigClass(InvalidConfigWithProtectedField.class, context, configManager)
        );

        assertTrue(exception.getMessage().contains("non-private field"));
        assertTrue(exception.getMessage().contains("protectedField"));
    }

    @Test
    void testValidateConfigClass_WithPackagePrivateField_ThrowsException() {
        ConfigException exception = assertThrows(ConfigException.class, () ->
                configRegistry.processConfigClass(InvalidConfigWithPackagePrivateField.class, context, configManager)
        );

        assertTrue(exception.getMessage().contains("non-private field"));
        assertTrue(exception.getMessage().contains("packagePrivateField"));
    }

    @Test
    void testProcessConfigClass_WithNestedConfig() {
        ConfigRef<NestedConfig> configRef = new MockConfigRef<>(NestedConfig.class);
        when(configManager.getRef(NestedConfig.class)).thenReturn(configRef);

        assertDoesNotThrow(() -> configRegistry.processConfigClass(NestedConfig.class, context, configManager));

        verify(configManager).register(NestedConfig.class);

        NestedConfig resolved = dependencyManager.resolveDependency(NestedConfig.class, null);
        assertNotNull(resolved);
    }

    @Test
    void testProcessConfigClass_WhenConfigManagerThrows_PropagatesException() {
        doThrow(new ConfigException("Config loading failed")).when(configManager).register(ValidConfig.class);

        assertThrows(ConfigException.class, () ->
                configRegistry.processConfigClass(ValidConfig.class, context, configManager)
        );
    }

    @Test
    void testProcessConfigClass_ConfigExceptionNotWrapped() {
        ConfigException originalException = new ConfigException("Original error");
        doThrow(originalException).when(configManager).register(ValidConfig.class);

        ConfigException thrownException = assertThrows(ConfigException.class, () ->
                configRegistry.processConfigClass(ValidConfig.class, context, configManager)
        );

        assertSame(originalException, thrownException);
    }

    @Test
    void testFieldModifierValidation() {
        for (var field : InvalidConfigWithPublicField.class.getDeclaredFields()) {
            if (field.getName().equals("publicField")) {
                assertFalse(Modifier.isPrivate(field.getModifiers()));
            }
            if (field.getName().equals("privateField")) {
                assertTrue(Modifier.isPrivate(field.getModifiers()));
            }
        }
    }

    @Test
    void testConfigRegistryIsComponent() {
        assertTrue(ConfigRegistry.class.isAnnotationPresent(Component.class));
    }
}

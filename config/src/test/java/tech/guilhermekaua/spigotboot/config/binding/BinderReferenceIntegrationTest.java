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
package tech.guilhermekaua.spigotboot.config.binding;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceParser;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceResolver;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.test.TestConfigNode;
import tech.guilhermekaua.spigotboot.config.test.TestConfigReferenceLookup;
import tech.guilhermekaua.spigotboot.config.test.TrackingConfigReferenceErrorHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Binder + Reference Integration")
class BinderReferenceIntegrationTest {

    private TestConfigReferenceLookup lookup;
    private ConfigReferenceParser parser;
    private TrackingConfigReferenceErrorHandler errorHandler;
    private ConfigReferenceResolver resolver;
    private Binder binder;

    @BeforeEach
    void setUp() {
        lookup = new TestConfigReferenceLookup();
        parser = new ConfigReferenceParser();
        errorHandler = new TrackingConfigReferenceErrorHandler();
        resolver = new ConfigReferenceResolver(lookup, parser, errorHandler);

        ConfigNodePreprocessor preprocessor = new TestReferencePreprocessor(resolver);

        binder = Binder.builder()
                .nodePreprocessor(preprocessor)
                .implicitDefaults(true)
                .build();
    }

    @Nested
    @DisplayName("Field binding with references")
    class FieldBindingWithReferences {

        @Test
        @DisplayName("resolves string reference to string field")
        void resolvesStringReferenceToStringField() {
            lookup.addConfig("other", "resolved value");

            Map<String, Object> data = new HashMap<>();
            data.put("name", "${other}");

            ConfigNode node = testNode(data);
            BindingResult<SimpleConfig> result = binder.bind(node, SimpleConfig.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertEquals("resolved value", result.get().getName());
        }

        @Test
        @DisplayName("resolves integer reference")
        void resolvesIntegerReference() {
            lookup.addConfig("count", 42);

            Map<String, Object> data = new HashMap<>();
            data.put("count", "${count}");

            ConfigNode node = testNode(data);
            BindingResult<ConfigWithInt> result = binder.bind(node, ConfigWithInt.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertEquals(42, result.get().getCount());
        }

        @Test
        @DisplayName("resolves nested object reference")
        void resolvesNestedObjectReference() {
            Map<String, Object> nestedData = new HashMap<>();
            nestedData.put("host", "localhost");
            nestedData.put("port", 3306);
            lookup.addConfig("db", nestedData);

            Map<String, Object> data = new HashMap<>();
            data.put("database", "${db}");

            ConfigNode node = testNode(data);
            BindingResult<ConfigWithNestedObject> result = binder.bind(node, ConfigWithNestedObject.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertNotNull(result.get().getDatabase());
            assertEquals("localhost", result.get().getDatabase().getHost());
            assertEquals(3306, result.get().getDatabase().getPort());
        }

        @Test
        @DisplayName("resolves path reference")
        void resolvesPathReference() {
            Map<String, Object> dbConfig = new HashMap<>();
            dbConfig.put("host", "production.db.com");
            dbConfig.put("port", 5432);
            Map<String, Object> settings = new HashMap<>();
            settings.put("database", dbConfig);
            lookup.addConfig("settings", settings);

            Map<String, Object> data = new HashMap<>();
            data.put("name", "${settings:database.host}");

            ConfigNode node = testNode(data);
            BindingResult<SimpleConfig> result = binder.bind(node, SimpleConfig.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertEquals("production.db.com", result.get().getName());
        }

        @Test
        @DisplayName("handles missing reference gracefully")
        void handlesMissingReferenceGracefully() {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "${nonexistent}");

            ConfigNode node = testNode(data);
            BindingResult<SimpleConfig> result = binder.bind(node, SimpleConfig.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess() || result.hasErrors());
            assertTrue(errorHandler.notFoundCalled);
        }

        @Test
        @DisplayName("keeps non-reference values unchanged")
        void keepsNonReferenceValuesUnchanged() {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "plain value");
            data.put("count", 100);

            ConfigNode node = testNode(data);
            BindingResult<ConfigWithInt> result = binder.bind(node, ConfigWithInt.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertEquals("plain value", result.get().getName());
            assertEquals(100, result.get().getCount());
        }

        @Test
        @DisplayName("resolves reference inside map value")
        void resolvesReferenceInsideMapValue() {
            lookup.addConfig("count", 42);

            Map<String, Object> values = new HashMap<>();
            values.put("a", "${count}");

            Map<String, Object> data = new HashMap<>();
            data.put("values", values);

            ConfigNode node = testNode(data);
            BindingResult<ConfigWithMapOfInt> result = binder.bind(node, ConfigWithMapOfInt.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertNotNull(result.get().getValues());
            assertEquals(42, result.get().getValues().get("a"));
        }

        @Test
        @DisplayName("resolves reference inside list element")
        void resolvesReferenceInsideListElement() {
            lookup.addConfig("other", "resolved value");

            Map<String, Object> data = new HashMap<>();
            data.put("values", Arrays.asList("${other}", "plain"));

            ConfigNode node = testNode(data);
            BindingResult<ConfigWithStringList> result = binder.bind(node, ConfigWithStringList.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertEquals(Arrays.asList("resolved value", "plain"), result.get().getValues());
        }
    }

    @Nested
    @DisplayName("Folder config item references")
    class FolderConfigItemReferences {

        @Test
        @DisplayName("resolves folder config item root reference")
        void resolvesFolderConfigItemRootReference() {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Diamond Sword");
            itemData.put("damage", 15);
            lookup.addFolderConfigItem("items", "diamond_sword", itemData);

            Map<String, Object> data = new HashMap<>();
            data.put("weapon", "${items.diamond_sword}");

            ConfigNode node = testNode(data);
            BindingResult<ConfigWithWeapon> result = binder.bind(node, ConfigWithWeapon.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertNotNull(result.get().getWeapon());
            assertEquals("Diamond Sword", result.get().getWeapon().getName());
        }

        @Test
        @DisplayName("resolves folder config item path reference")
        void resolvesFolderConfigItemPathReference() {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Iron Axe");
            itemData.put("damage", 10);
            lookup.addFolderConfigItem("items", "iron_axe", itemData);

            Map<String, Object> data = new HashMap<>();
            data.put("name", "${items.iron_axe:name}");

            ConfigNode node = testNode(data);
            BindingResult<SimpleConfig> result = binder.bind(node, SimpleConfig.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertEquals("Iron Axe", result.get().getName());
        }
    }

    private ConfigNode testNode(@Nullable Object value) {
        return new TestConfigNode(value);
    }

    private static class TestReferencePreprocessor implements ConfigNodePreprocessor {
        private final ConfigReferenceResolver resolver;

        TestReferencePreprocessor(ConfigReferenceResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public @Nullable ConfigNode preprocess(@NotNull ConfigNode node, @Nullable Field field, @NotNull Type expectedType) {
            if (!node.isScalar()) {
                return null;
            }
            String value = node.get(String.class);
            if (value == null || !resolver.isReference(value)) {
                return null;
            }
            return resolver.resolveIfReference(node, ReferenceKey.singleConfig("test"), field);
        }
    }

    @Setter
    @Getter
    public static class SimpleConfig {
        private String name;

    }

    @Setter
    @Getter
    public static class ConfigWithInt {
        private String name;
        private int count;

    }

    @Setter
    @Getter
    public static class ConfigWithNestedObject {
        private DatabaseConfig database;

    }

    @Setter
    @Getter
    public static class DatabaseConfig {
        private String host;
        private int port;

    }

    @Setter
    @Getter
    public static class ConfigWithWeapon {
        private WeaponConfig weapon;

    }

    @Setter
    @Getter
    public static class WeaponConfig {
        private String name;
        private int damage;

    }

    @Setter
    @Getter
    public static class ConfigWithMapOfInt {
        private Map<String, Integer> values;

    }

    @Setter
    @Getter
    public static class ConfigWithStringList {
        private List<String> values;

    }
}

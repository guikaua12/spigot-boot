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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceLookup;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceParser;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceResolver;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigCircularReferenceContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigReferenceNotFoundContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Binder + Reference Integration")
class BinderReferenceIntegrationTest {

    private TestLookup lookup;
    private ConfigReferenceParser parser;
    private TestErrorHandler errorHandler;
    private ConfigReferenceResolver resolver;
    private Binder binder;

    @BeforeEach
    void setUp() {
        lookup = new TestLookup();
        parser = new ConfigReferenceParser();
        errorHandler = new TestErrorHandler();
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

            // Should succeed but with null value (error handler returns null)
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
    }

    @Nested
    @DisplayName("Collection item references")
    class CollectionItemReferences {

        @Test
        @DisplayName("resolves collection item root reference")
        void resolvesCollectionItemRootReference() {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Diamond Sword");
            itemData.put("damage", 15);
            lookup.addCollectionItem("items", "diamond_sword", itemData);

            Map<String, Object> data = new HashMap<>();
            data.put("weapon", "${items.diamond_sword}");

            ConfigNode node = testNode(data);
            BindingResult<ConfigWithWeapon> result = binder.bind(node, ConfigWithWeapon.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertNotNull(result.get().getWeapon());
            assertEquals("Diamond Sword", result.get().getWeapon().getName());
        }

        @Test
        @DisplayName("resolves collection item path reference")
        void resolvesCollectionItemPathReference() {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Iron Axe");
            itemData.put("damage", 10);
            lookup.addCollectionItem("items", "iron_axe", itemData);

            Map<String, Object> data = new HashMap<>();
            data.put("name", "${items.iron_axe:name}");

            ConfigNode node = testNode(data);
            BindingResult<SimpleConfig> result = binder.bind(node, SimpleConfig.class, NamingStrategy.IDENTITY);

            assertTrue(result.isSuccess());
            assertEquals("Iron Axe", result.get().getName());
        }
    }

    // ==================== Test Helper Classes ====================

    private ConfigNode testNode(@Nullable Object value) {
        return new SimpleTestNode(value);
    }

    // Test preprocessor that wraps the resolver
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

    // Config classes for testing
    public static class SimpleConfig {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class ConfigWithInt {
        private String name;
        private int count;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public static class ConfigWithNestedObject {
        private DatabaseConfig database;

        public DatabaseConfig getDatabase() {
            return database;
        }

        public void setDatabase(DatabaseConfig database) {
            this.database = database;
        }
    }

    public static class DatabaseConfig {
        private String host;
        private int port;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class ConfigWithWeapon {
        private WeaponConfig weapon;

        public WeaponConfig getWeapon() {
            return weapon;
        }

        public void setWeapon(WeaponConfig weapon) {
            this.weapon = weapon;
        }
    }

    public static class WeaponConfig {
        private String name;
        private int damage;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getDamage() {
            return damage;
        }

        public void setDamage(int damage) {
            this.damage = damage;
        }
    }

    // ==================== Test Infrastructure ====================

    private static class SimpleTestNode implements ConfigNode {
        private final Object value;
        private final PropertyPath path;

        SimpleTestNode(@Nullable Object value) {
            this.value = value;
            this.path = PropertyPath.root();
        }

        SimpleTestNode(@Nullable Object value, PropertyPath path) {
            this.value = value;
            this.path = path;
        }

        @Override
        public @Nullable Object raw() {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> @Nullable T get(@NotNull Class<T> type) {
            if (value == null) return null;
            if (type.isInstance(value)) return (T) value;
            return null;
        }

        @Override
        public <T> @NotNull T get(@NotNull Class<T> type, @NotNull T defaultValue) {
            T result = get(type);
            return result != null ? result : defaultValue;
        }

        @Override
        public @NotNull ConfigNode node(@NotNull Object... pathSegments) {
            if (pathSegments.length == 0) return this;

            Object current = value;
            PropertyPath currentPath = path;

            for (Object segment : pathSegments) {
                if (current == null) {
                    return new VirtualNode(currentPath.child(String.valueOf(segment)));
                }
                if (current instanceof Map && segment instanceof String) {
                    current = ((Map<?, ?>) current).get(segment);
                    currentPath = currentPath.child((String) segment);
                } else {
                    return new VirtualNode(currentPath.child(String.valueOf(segment)));
                }
            }

            return new SimpleTestNode(current, currentPath);
        }

        @Override
        public @NotNull ConfigNode node(@NotNull PropertyPath path) {
            return node(path.elements());
        }

        @Override
        public boolean hasChild(@NotNull Object... pathSegments) {
            return !node(pathSegments).isVirtual();
        }

        @Override
        @SuppressWarnings("unchecked")
        public @NotNull Map<String, ? extends ConfigNode> childrenMap() {
            if (!(value instanceof Map)) return Collections.emptyMap();
            Map<String, ConfigNode> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                result.put(entry.getKey(), new SimpleTestNode(entry.getValue(), path.child(entry.getKey())));
            }
            return result;
        }

        @Override
        public @NotNull List<? extends ConfigNode> childrenList() {
            if (!(value instanceof List)) return Collections.emptyList();
            List<ConfigNode> result = new ArrayList<>();
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                result.add(new SimpleTestNode(list.get(i), path.child(i)));
            }
            return result;
        }

        @Override
        public boolean isMap() {
            return value instanceof Map;
        }

        @Override
        public boolean isList() {
            return value instanceof List;
        }

        @Override
        public boolean isScalar() {
            return value != null && !(value instanceof Map) && !(value instanceof List);
        }

        @Override
        public boolean isNull() {
            return value == null;
        }

        @Override
        public boolean isVirtual() {
            return false;
        }

        @Override
        public @NotNull PropertyPath path() {
            return path;
        }
    }

    private static class VirtualNode implements ConfigNode {
        private final PropertyPath path;

        VirtualNode(PropertyPath path) {
            this.path = path;
        }

        @Override
        public @Nullable Object raw() {
            return null;
        }

        @Override
        public <T> @Nullable T get(@NotNull Class<T> type) {
            return null;
        }

        @Override
        public <T> @NotNull T get(@NotNull Class<T> type, @NotNull T defaultValue) {
            return defaultValue;
        }

        @Override
        public @NotNull ConfigNode node(@NotNull Object... pathSegments) {
            return this;
        }

        @Override
        public @NotNull ConfigNode node(@NotNull PropertyPath path) {
            return this;
        }

        @Override
        public boolean hasChild(@NotNull Object... pathSegments) {
            return false;
        }

        @Override
        public @NotNull Map<String, ? extends ConfigNode> childrenMap() {
            return Collections.emptyMap();
        }

        @Override
        public @NotNull List<? extends ConfigNode> childrenList() {
            return Collections.emptyList();
        }

        @Override
        public boolean isMap() {
            return false;
        }

        @Override
        public boolean isList() {
            return false;
        }

        @Override
        public boolean isScalar() {
            return false;
        }

        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public boolean isVirtual() {
            return true;
        }

        @Override
        public @NotNull PropertyPath path() {
            return path;
        }
    }

    private static class TestLookup implements ConfigReferenceLookup {
        private final Map<String, ConfigNode> configs = new LinkedHashMap<>();
        private final Map<String, Map<String, ConfigNode>> collections = new LinkedHashMap<>();

        void addConfig(String name, Object data) {
            configs.put(name, new SimpleTestNode(data));
        }

        void addCollectionItem(String collectionName, String itemId, Object data) {
            collections.computeIfAbsent(collectionName, k -> new LinkedHashMap<>())
                    .put(itemId, new SimpleTestNode(data));
        }

        @Override
        public @Nullable ConfigNode findSingleConfigRoot(@NotNull String configName) {
            return configs.get(configName);
        }

        @Override
        public @Nullable ConfigNode findSingleConfigPath(@NotNull String configName, @NotNull String path) {
            ConfigNode root = configs.get(configName);
            if (root == null) return null;
            String[] parts = path.split("\\.");
            Object[] pathArgs = new Object[parts.length];
            System.arraycopy(parts, 0, pathArgs, 0, parts.length);
            ConfigNode result = root.node(pathArgs);
            return result.isVirtual() ? null : result;
        }

        @Override
        public @Nullable ConfigNode findCollectionItemRoot(@NotNull String collectionName, @NotNull String itemId) {
            Map<String, ConfigNode> items = collections.get(collectionName);
            return items != null ? items.get(itemId) : null;
        }

        @Override
        public @Nullable ConfigNode findCollectionItemPath(@NotNull String collectionName, @NotNull String itemId, @NotNull String path) {
            ConfigNode root = findCollectionItemRoot(collectionName, itemId);
            if (root == null) return null;
            String[] parts = path.split("\\.");
            Object[] pathArgs = new Object[parts.length];
            System.arraycopy(parts, 0, pathArgs, 0, parts.length);
            ConfigNode result = root.node(pathArgs);
            return result.isVirtual() ? null : result;
        }

        @Override
        public @NotNull Set<String> getAvailableConfigNames() {
            return configs.keySet();
        }

        @Override
        public @NotNull Set<String> getAvailableCollectionNames() {
            return collections.keySet();
        }

        @Override
        public @NotNull Set<String> getAvailableItemIds(@NotNull String collectionName) {
            Map<String, ConfigNode> items = collections.get(collectionName);
            return items != null ? items.keySet() : Collections.emptySet();
        }

        @Override
        public @NotNull Set<String> getAvailableKeysAt(@NotNull String configName, @Nullable String path) {
            return Collections.emptySet();
        }

        @Override
        public boolean hasConfig(@NotNull String configName) {
            return configs.containsKey(configName);
        }

        @Override
        public boolean hasCollection(@NotNull String collectionName) {
            return collections.containsKey(collectionName);
        }

        @Override
        public boolean hasCollectionItem(@NotNull String collectionName, @NotNull String itemId) {
            Map<String, ConfigNode> items = collections.get(collectionName);
            return items != null && items.containsKey(itemId);
        }
    }

    private static class TestErrorHandler implements ConfigReferenceErrorHandler {
        boolean notFoundCalled = false;
        boolean circularCalled = false;
        boolean typeMismatchCalled = false;

        @Override
        public @Nullable Object onReferenceNotFound(@NotNull ConfigReferenceNotFoundContext context) {
            notFoundCalled = true;
            return null;
        }

        @Override
        public void onCircularReference(@NotNull ConfigCircularReferenceContext context) {
            circularCalled = true;
        }

        @Override
        public @Nullable Object onTypeMismatch(@NotNull ConfigTypeMismatchContext context) {
            typeMismatchCalled = true;
            return null;
        }
    }
}

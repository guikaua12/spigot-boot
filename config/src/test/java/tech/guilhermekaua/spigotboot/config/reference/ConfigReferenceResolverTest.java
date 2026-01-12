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
package tech.guilhermekaua.spigotboot.config.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigCircularReferenceContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigReferenceNotFoundContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigReferenceResolver")
class ConfigReferenceResolverTest {

    private TestLookup lookup;
    private ConfigReferenceParser parser;
    private TestErrorHandler errorHandler;
    private ConfigReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        lookup = new TestLookup();
        parser = new ConfigReferenceParser();
        errorHandler = new TestErrorHandler();
        resolver = new ConfigReferenceResolver(lookup, parser, errorHandler);
    }

    @Nested
    @DisplayName("resolveIfReference")
    class ResolveIfReference {

        @Test
        @DisplayName("returns original node if not a reference")
        void returnsOriginalNodeIfNotReference() {
            ConfigNode node = testNode("hello world");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertEquals("hello world", result.get(String.class));
        }

        @Test
        @DisplayName("returns original node if null value")
        void returnsOriginalNodeIfNullValue() {
            ConfigNode node = testNode(null);
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertNull(result.raw());
        }

        @Test
        @DisplayName("returns original node if map")
        void returnsOriginalNodeIfMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("key", "value");
            ConfigNode node = testNode(map);
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(result.isMap());
        }

        @Test
        @DisplayName("resolves single config root reference")
        void resolvesSingleConfigRootReference() {
            Map<String, Object> itemsData = new HashMap<>();
            itemsData.put("name", "sword");
            itemsData.put("damage", 10);
            lookup.addConfig("items", itemsData);

            ConfigNode node = testNode("${items}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(result.isMap());
            assertEquals("sword", result.node("name").get(String.class));
        }

        @Test
        @DisplayName("resolves single config path reference")
        void resolvesSingleConfigPathReference() {
            Map<String, Object> database = new HashMap<>();
            database.put("host", "localhost");
            database.put("port", 3306);
            Map<String, Object> config = new HashMap<>();
            config.put("database", database);
            lookup.addConfig("settings", config);

            ConfigNode node = testNode("${settings:database.host}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertEquals("localhost", result.get(String.class));
        }

        @Test
        @DisplayName("resolves collection item root reference")
        void resolvesCollectionItemRootReference() {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Diamond Sword");
            itemData.put("damage", 15);
            lookup.addCollectionItem("items", "diamond_sword", itemData);

            ConfigNode node = testNode("${items.diamond_sword}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(result.isMap());
            assertEquals("Diamond Sword", result.node("name").get(String.class));
        }

        @Test
        @DisplayName("resolves collection item path reference")
        void resolvesCollectionItemPathReference() {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Diamond Sword");
            itemData.put("damage", 15);
            lookup.addCollectionItem("items", "diamond_sword", itemData);

            ConfigNode node = testNode("${items.diamond_sword:damage}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertEquals(15, result.get(Integer.class));
        }

        @Test
        @DisplayName("returns null and calls handler for missing reference")
        void returnsNullAndCallsHandlerForMissingReference() {
            ConfigNode node = testNode("${nonexistent}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertNull(result);
            assertTrue(errorHandler.notFoundCalled);
            assertEquals("${nonexistent}", errorHandler.notFoundContext.getFullReference());
        }

        @Test
        @DisplayName("detects self-cycle")
        void detectsSelfCycle() {
            // Config A references itself
            lookup.addConfig("configA", "${configA}");

            ConfigNode node = testNode("${configA}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("configA");

            resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(errorHandler.circularCalled);
        }

        @Test
        @DisplayName("detects multi-node cycle")
        void detectsMultiNodeCycle() {
            // A -> B -> C -> A
            lookup.addConfig("configA", "${configB}");
            lookup.addConfig("configB", "${configC}");
            lookup.addConfig("configC", "${configA}");

            ConfigNode node = testNode("${configA}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            // This should trigger cycle detection when resolving deep references
            resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(errorHandler.circularCalled);
        }
    }

    @Nested
    @DisplayName("deepResolve")
    class DeepResolve {

        @Test
        @DisplayName("returns same node for scalar non-reference")
        void returnsSameNodeForScalarNonReference() {
            ConfigNode node = testNode("plain value");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.deepResolve(node, sourceKey);

            assertEquals("plain value", result.get(String.class));
        }

        @Test
        @DisplayName("resolves scalar reference")
        void resolvesScalarReference() {
            lookup.addConfig("other", "resolved value");

            ConfigNode node = testNode("${other}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.deepResolve(node, sourceKey);

            assertEquals("resolved value", result.get(String.class));
        }

        @Test
        @DisplayName("returns null node unchanged")
        void returnsNullNodeUnchanged() {
            ConfigNode node = testNode(null);
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.deepResolve(node, sourceKey);

            assertTrue(result.isNull());
        }
    }

    @Nested
    @DisplayName("isReference")
    class IsReference {

        @Test
        @DisplayName("returns true for valid reference")
        void returnsTrueForValidReference() {
            assertTrue(resolver.isReference("${config}"));
            assertTrue(resolver.isReference("${config:path}"));
            assertTrue(resolver.isReference("${collection.item}"));
            assertTrue(resolver.isReference("${collection.item:path}"));
        }

        @Test
        @DisplayName("returns false for non-reference")
        void returnsFalseForNonReference() {
            assertFalse(resolver.isReference("plain text"));
            assertFalse(resolver.isReference("${incomplete"));
            assertFalse(resolver.isReference("${}"));
            assertFalse(resolver.isReference(null));
        }
    }

    @Nested
    @DisplayName("alternatives suggestions")
    class AlternativesSuggestions {

        @Test
        @DisplayName("suggests available config names for missing single config")
        void suggestsConfigNamesForMissingSingleConfig() {
            lookup.addConfig("items", "data");
            lookup.addConfig("boosters", "data");

            ConfigNode node = testNode("${nonexistent}");
            resolver.resolveIfReference(node, ReferenceKey.singleConfig("myconfig"), null);

            assertTrue(errorHandler.notFoundContext.getAvailableAlternatives().contains("items"));
            assertTrue(errorHandler.notFoundContext.getAvailableAlternatives().contains("boosters"));
        }

        @Test
        @DisplayName("suggests available item IDs for missing collection item")
        void suggestsItemIdsForMissingCollectionItem() {
            lookup.addCollectionItem("items", "sword", "data");
            lookup.addCollectionItem("items", "axe", "data");

            ConfigNode node = testNode("${items.nonexistent}");
            resolver.resolveIfReference(node, ReferenceKey.singleConfig("myconfig"), null);

            assertTrue(errorHandler.notFoundContext.getAvailableAlternatives().contains("sword"));
            assertTrue(errorHandler.notFoundContext.getAvailableAlternatives().contains("axe"));
        }
    }

    // ==================== Helper Classes ====================

    private ConfigNode testNode(@Nullable Object value) {
        return new SimpleTestNode(value);
    }

    /**
     * Simple test implementation of ConfigNode.
     */
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
            return node(path.elements());
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

    /**
     * Test implementation of ConfigReferenceLookup.
     */
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

    /**
     * Test implementation of ConfigReferenceErrorHandler that tracks calls.
     */
    private static class TestErrorHandler implements ConfigReferenceErrorHandler {
        boolean notFoundCalled = false;
        boolean circularCalled = false;
        boolean typeMismatchCalled = false;

        ConfigReferenceNotFoundContext notFoundContext;
        ConfigCircularReferenceContext circularContext;
        ConfigTypeMismatchContext typeMismatchContext;

        @Override
        public @Nullable Object onReferenceNotFound(@NotNull ConfigReferenceNotFoundContext context) {
            notFoundCalled = true;
            notFoundContext = context;
            return null;
        }

        @Override
        public void onCircularReference(@NotNull ConfigCircularReferenceContext context) {
            circularCalled = true;
            circularContext = context;
            // Don't throw in test to allow verification
        }

        @Override
        public @Nullable Object onTypeMismatch(@NotNull ConfigTypeMismatchContext context) {
            typeMismatchCalled = true;
            typeMismatchContext = context;
            return null;
        }
    }
}

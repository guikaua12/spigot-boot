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
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.core.exceptions.CycleDetectedException;
import tech.guilhermekaua.spigotboot.core.utils.DependencyGraph;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ConfigReferenceDependencyScanner")
class ConfigReferenceDependencyScannerTest {

    private ConfigReferenceDependencyScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = ConfigReferenceDependencyScanner.create();
    }

    @Nested
    @DisplayName("scanDependencies")
    class ScanDependencies {

        @Test
        @DisplayName("finds single config reference")
        void findsSingleConfigReference() {
            Map<String, Object> data = new HashMap<>();
            data.put("ref", "${other}");

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertEquals(1, deps.size());
            assertTrue(deps.contains(ReferenceKey.singleConfig("other")));
        }

        @Test
        @DisplayName("finds config path reference")
        void findsConfigPathReference() {
            Map<String, Object> data = new HashMap<>();
            data.put("value", "${settings:database.host}");

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertEquals(1, deps.size());
            assertTrue(deps.contains(ReferenceKey.singleConfig("settings")));
        }

        @Test
        @DisplayName("finds collection item reference")
        void findsCollectionItemReference() {
            Map<String, Object> data = new HashMap<>();
            data.put("weapon", "${items.sword}");

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertEquals(1, deps.size());
            assertTrue(deps.contains(ReferenceKey.collectionItem("items", "sword")));
        }

        @Test
        @DisplayName("finds multiple references")
        void findsMultipleReferences() {
            Map<String, Object> data = new HashMap<>();
            data.put("a", "${configA}");
            data.put("b", "${configB}");
            data.put("c", "${items.weapon}");

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertEquals(3, deps.size());
            assertTrue(deps.contains(ReferenceKey.singleConfig("configA")));
            assertTrue(deps.contains(ReferenceKey.singleConfig("configB")));
            assertTrue(deps.contains(ReferenceKey.collectionItem("items", "weapon")));
        }

        @Test
        @DisplayName("finds nested references")
        void findsNestedReferences() {
            Map<String, Object> nested = new HashMap<>();
            nested.put("deep", "${nestedRef}");

            Map<String, Object> data = new HashMap<>();
            data.put("outer", "${outerRef}");
            data.put("nested", nested);

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertEquals(2, deps.size());
            assertTrue(deps.contains(ReferenceKey.singleConfig("outerRef")));
            assertTrue(deps.contains(ReferenceKey.singleConfig("nestedRef")));
        }

        @Test
        @DisplayName("finds references in lists")
        void findsReferencesInLists() {
            List<Object> items = Arrays.asList("${ref1}", "plain", "${ref2}");

            Map<String, Object> data = new HashMap<>();
            data.put("items", items);

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertEquals(2, deps.size());
            assertTrue(deps.contains(ReferenceKey.singleConfig("ref1")));
            assertTrue(deps.contains(ReferenceKey.singleConfig("ref2")));
        }

        @Test
        @DisplayName("ignores non-reference strings")
        void ignoresNonReferenceStrings() {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "plain value");
            data.put("count", 42);
            data.put("valid", "${actualRef}");

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertEquals(1, deps.size());
            assertTrue(deps.contains(ReferenceKey.singleConfig("actualRef")));
        }

        @Test
        @DisplayName("deduplicates references")
        void deduplicatesReferences() {
            Map<String, Object> data = new HashMap<>();
            data.put("a", "${same}");
            data.put("b", "${same}");
            data.put("c", "${same}");

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertEquals(1, deps.size());
        }

        @Test
        @DisplayName("returns empty for no references")
        void returnsEmptyForNoReferences() {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "plain");
            data.put("count", 100);

            Set<ReferenceKey> deps = scanner.scanDependencies(testNode(data));

            assertTrue(deps.isEmpty());
        }
    }

    @Nested
    @DisplayName("scanAndAddToGraph")
    class ScanAndAddToGraph {

        @Test
        @DisplayName("adds edges to graph")
        void addsEdgesToGraph() throws CycleDetectedException {
            DependencyGraph graph = new DependencyGraph();

            Map<String, Object> configAData = new HashMap<>();
            configAData.put("ref", "${configB}");

            scanner.scanAndAddToGraph(
                    ReferenceKey.singleConfig("configA"),
                    testNode(configAData),
                    graph);

            // configA depends on configB, so B should come before A in topo order
            List<ReferenceKey> order = graph.topologicalOrder();
            int indexB = order.indexOf(ReferenceKey.singleConfig("configB"));
            int indexA = order.indexOf(ReferenceKey.singleConfig("configA"));

            assertTrue(indexB < indexA, "configB should be loaded before configA");
        }

        @Test
        @DisplayName("builds correct load order for chain")
        void buildsCorrectLoadOrderForChain() throws CycleDetectedException {
            DependencyGraph graph = new DependencyGraph();

            // C -> B -> A (C depends on B, B depends on A)
            Map<String, Object> configCData = new HashMap<>();
            configCData.put("ref", "${configB}");
            scanner.scanAndAddToGraph(ReferenceKey.singleConfig("configC"), testNode(configCData), graph);

            Map<String, Object> configBData = new HashMap<>();
            configBData.put("ref", "${configA}");
            scanner.scanAndAddToGraph(ReferenceKey.singleConfig("configB"), testNode(configBData), graph);

            List<ReferenceKey> order = graph.topologicalOrder();

            int indexA = order.indexOf(ReferenceKey.singleConfig("configA"));
            int indexB = order.indexOf(ReferenceKey.singleConfig("configB"));
            int indexC = order.indexOf(ReferenceKey.singleConfig("configC"));

            assertTrue(indexA < indexB, "A should be before B");
            assertTrue(indexB < indexC, "B should be before C");
        }
    }

    // ==================== Helper ====================

    private ConfigNode testNode(@Nullable Object value) {
        return new SimpleTestNode(value);
    }

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
                    return new VirtualTestNode(currentPath.child(String.valueOf(segment)));
                }
                if (current instanceof Map && segment instanceof String) {
                    current = ((Map<?, ?>) current).get(segment);
                    currentPath = currentPath.child((String) segment);
                } else {
                    return new VirtualTestNode(currentPath.child(String.valueOf(segment)));
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
        @SuppressWarnings("unchecked")
        public @NotNull List<? extends ConfigNode> childrenList() {
            if (!(value instanceof List)) return Collections.emptyList();
            List<ConfigNode> result = new ArrayList<>();
            List<Object> list = (List<Object>) value;
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

    private static class VirtualTestNode implements ConfigNode {
        private final PropertyPath path;

        VirtualTestNode(PropertyPath path) {
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
}

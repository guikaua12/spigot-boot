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

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.test.TestConfigNode;
import tech.guilhermekaua.spigotboot.core.exceptions.CycleDetectedException;
import tech.guilhermekaua.spigotboot.core.utils.DependencyGraph;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

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
            DependencyGraph<ReferenceKey> graph = new DependencyGraph<>();

            Map<String, Object> configAData = new HashMap<>();
            configAData.put("ref", "${configB}");

            scanner.scanAndAddToGraph(
                    ReferenceKey.singleConfig("configA"),
                    testNode(configAData),
                    graph);

            List<ReferenceKey> order = graph.topologicalOrder();
            int indexB = order.indexOf(ReferenceKey.singleConfig("configB"));
            int indexA = order.indexOf(ReferenceKey.singleConfig("configA"));

            assertTrue(indexB < indexA, "configB should be loaded before configA");
        }

        @Test
        @DisplayName("builds correct load order for chain")
        void buildsCorrectLoadOrderForChain() throws CycleDetectedException {
            DependencyGraph<ReferenceKey> graph = new DependencyGraph<>();

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

        @Test
        @DisplayName("does not create cycle for same-config path reference")
        void doesNotCreateCycleForSameConfigPathReference() throws CycleDetectedException {
            DependencyGraph<ReferenceKey> graph = new DependencyGraph<>();

            Map<String, Object> configData = new HashMap<>();
            configData.put("spawn", "${foo:bar.baz}");

            scanner.scanAndAddToGraph(
                    ReferenceKey.singleConfig("foo"),
                    testNode(configData),
                    graph);

            graph.topologicalOrder();

            assertTrue(graph.getDependencies(ReferenceKey.singleConfig("foo")).isEmpty());
        }

        @Test
        @DisplayName("still detects cycle for same-config root reference")
        void stillDetectsCycleForSameConfigRootReference() {
            DependencyGraph<ReferenceKey> graph = new DependencyGraph<>();

            Map<String, Object> configData = new HashMap<>();
            configData.put("self", "${foo}");

            scanner.scanAndAddToGraph(
                    ReferenceKey.singleConfig("foo"),
                    testNode(configData),
                    graph);

            assertThrows(CycleDetectedException.class, graph::topologicalOrder);
        }
    }

    private ConfigNode testNode(@Nullable Object value) {
        return new TestConfigNode(value);
    }
}

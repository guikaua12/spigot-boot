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
package tech.guilhermekaua.spigotboot.core.test.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.exceptions.CycleDetectedException;
import tech.guilhermekaua.spigotboot.core.utils.DependencyGraph;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphTest {

    private DependencyGraph<String> graph;

    @BeforeEach
    void setUp() {
        graph = new DependencyGraph<>();
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("empty graph has no nodes")
        void emptyGraph() {
            assertTrue(graph.isEmpty());
            assertEquals(0, graph.size());
            assertTrue(graph.getAllNodes().isEmpty());
        }

        @Test
        @DisplayName("addNode adds a node without edges")
        void addNode() {
            String key = "config";
            graph.addNode(key);

            assertEquals(1, graph.size());
            assertTrue(graph.getAllNodes().contains(key));
            assertTrue(graph.getDependencies(key).isEmpty());
            assertTrue(graph.getDirectDependents(key).isEmpty());
        }

        @Test
        @DisplayName("addEdge creates both forward and reverse edges")
        void addEdge() {
            String a = "a";
            String b = "b";

            graph.addEdge(a, b);

            assertEquals(2, graph.size());
            assertTrue(graph.getDependencies(a).contains(b));
            assertTrue(graph.getDirectDependents(b).contains(a));
        }

        @Test
        @DisplayName("addEdge implicitly adds nodes")
        void addEdgeImplicitNodes() {
            String a = "a";
            String b = "b";

            graph.addEdge(a, b);

            assertTrue(graph.getAllNodes().contains(a));
            assertTrue(graph.getAllNodes().contains(b));
        }

        @Test
        @DisplayName("clear removes all nodes and edges")
        void clear() {
            graph.addEdge("a", "b");
            graph.clear();

            assertTrue(graph.isEmpty());
        }
    }

    @Nested
    @DisplayName("Topological Sort - Acyclic Graphs")
    class TopologicalSortAcyclic {

        @Test
        @DisplayName("single node returns that node")
        void singleNode() throws CycleDetectedException {
            String a = "a";
            graph.addNode(a);

            List<String> order = graph.topologicalOrder();

            assertEquals(1, order.size());
            assertEquals(a, order.get(0));
        }

        @Test
        @DisplayName("linear chain orders dependencies first")
        void linearChain() throws CycleDetectedException {
            String a = "a";
            String b = "b";
            String c = "c";

            graph.addEdge(a, b);
            graph.addEdge(b, c);

            List<String> order = graph.topologicalOrder();
            assertTrue(order.indexOf(c) < order.indexOf(b), "C should come before B");
            assertTrue(order.indexOf(b) < order.indexOf(a), "B should come before A");
        }

        @Test
        @DisplayName("diamond dependency graph orders correctly")
        void diamondDependency() throws CycleDetectedException {
            //     A
            //    / \
            //   B   C
            //    \ /
            //     D
            String a = "a";
            String b = "b";
            String c = "c";
            String d = "d";

            graph.addEdge(a, b);
            graph.addEdge(a, c);
            graph.addEdge(b, d);
            graph.addEdge(c, d);

            List<String> order = graph.topologicalOrder();

            assertTrue(order.indexOf(d) < order.indexOf(b), "D should come before B");
            assertTrue(order.indexOf(d) < order.indexOf(c), "D should come before C");
            assertTrue(order.indexOf(b) < order.indexOf(a), "B should come before A");
            assertTrue(order.indexOf(c) < order.indexOf(a), "C should come before A");
        }

        @Test
        @DisplayName("disconnected components are all included")
        void disconnectedComponents() throws CycleDetectedException {
            String a = "a";
            String b = "b";
            String c = "c";
            String d = "d";

            graph.addEdge(a, b);
            graph.addEdge(c, d);

            List<String> order = graph.topologicalOrder();

            assertEquals(4, order.size());
            assertTrue(order.indexOf(b) < order.indexOf(a));
            assertTrue(order.indexOf(d) < order.indexOf(c));
        }

        @Test
        @DisplayName("works with arbitrary node values")
        void arbitraryNodes() throws CycleDetectedException {
            String config = "config:items";
            String item = "folder-config:boosters.2x";

            graph.addEdge(item, config);

            List<String> order = graph.topologicalOrder();

            assertTrue(order.indexOf(config) < order.indexOf(item));
        }
    }

    @Nested
    @DisplayName("Cycle Detection")
    class CycleDetection {

        @Test
        @DisplayName("detects self-cycle")
        void selfCycle() {
            String a = "a";
            graph.addEdge(a, a);

            Optional<List<String>> cycle = graph.detectCycle();

            assertTrue(cycle.isPresent());
            assertTrue(cycle.get().contains(a));
        }

        @Test
        @DisplayName("detects simple two-node cycle")
        void twoNodeCycle() {
            String a = "a";
            String b = "b";

            graph.addEdge(a, b);
            graph.addEdge(b, a);

            Optional<List<String>> cycle = graph.detectCycle();

            assertTrue(cycle.isPresent());
            List<String> cycleList = cycle.get();
            assertTrue(cycleList.contains(a));
            assertTrue(cycleList.contains(b));
        }

        @Test
        @DisplayName("detects three-node cycle")
        void threeNodeCycle() {
            String a = "a";
            String b = "b";
            String c = "c";

            graph.addEdge(a, b);
            graph.addEdge(b, c);
            graph.addEdge(c, a);

            Optional<List<String>> cycle = graph.detectCycle();

            assertTrue(cycle.isPresent());
            List<String> cycleList = cycle.get();
            assertTrue(cycleList.contains(a));
            assertTrue(cycleList.contains(b));
            assertTrue(cycleList.contains(c));
        }

        @Test
        @DisplayName("no cycle in acyclic graph")
        void noCycle() {
            String a = "a";
            String b = "b";
            String c = "c";

            graph.addEdge(a, b);
            graph.addEdge(b, c);

            Optional<List<String>> cycle = graph.detectCycle();

            assertFalse(cycle.isPresent());
        }

        @Test
        @DisplayName("topologicalOrder throws on cycle")
        void topologicalOrderThrowsOnCycle() {
            String a = "a";
            String b = "b";

            graph.addEdge(a, b);
            graph.addEdge(b, a);

            CycleDetectedException ex = assertThrows(CycleDetectedException.class, graph::topologicalOrder);

            assertNotNull(ex.getCycle());
            assertFalse(ex.getCycle().isEmpty());
            assertTrue(ex.getMessage().contains("Circular reference"));
        }

        @Test
        @DisplayName("CycleDetectedException contains readable cycle path")
        void cycleExceptionMessage() {
            String a = "configA";
            String b = "configB";

            graph.addEdge(a, b);
            graph.addEdge(b, a);

            CycleDetectedException ex = assertThrows(CycleDetectedException.class, graph::topologicalOrder);

            String message = ex.formatCycle();
            assertTrue(message.contains("->"), "Message should contain arrow: " + message);
        }
    }

    @Nested
    @DisplayName("Reverse Dependencies")
    class ReverseDependencies {

        @Test
        @DisplayName("getTransitiveDependents returns all indirect dependents")
        void transitiveDependents() {
            // A -> B -> C (A depends on B, B depends on C)
            // so C has dependents: B, A
            String a = "a";
            String b = "b";
            String c = "c";

            graph.addEdge(a, b);
            graph.addEdge(b, c);

            Set<String> dependentsOfC = graph.getTransitiveDependents(c);

            assertTrue(dependentsOfC.contains(b));
            assertTrue(dependentsOfC.contains(a));
            assertEquals(2, dependentsOfC.size());
        }

        @Test
        @DisplayName("getTransitiveDependents does not include the node itself")
        void transitiveDependentsExcludesSelf() {
            String a = "a";
            String b = "b";

            graph.addEdge(a, b);

            Set<String> dependentsOfB = graph.getTransitiveDependents(b);

            assertFalse(dependentsOfB.contains(b));
        }

        @Test
        @DisplayName("getReverseDependencies returns correct map")
        void reverseDependenciesMap() {
            String a = "a";
            String b = "b";
            String c = "c";

            graph.addEdge(a, b);
            graph.addEdge(c, b);

            Map<String, Set<String>> reverseDeps = graph.getReverseDependencies();

            assertTrue(reverseDeps.get(b).contains(a));
            assertTrue(reverseDeps.get(b).contains(c));
        }
    }

    @Nested
    @DisplayName("Subset Operations")
    class SubsetOperations {

        @Test
        @DisplayName("topologicalOrderSubset orders subset correctly")
        void subsetOrder() throws CycleDetectedException {
            String a = "a";
            String b = "b";
            String c = "c";
            String d = "d";

            graph.addEdge(a, b);
            graph.addEdge(b, c);
            graph.addEdge(c, d);

            Set<String> subset = new LinkedHashSet<>(Arrays.asList(a, c));
            List<String> order = graph.topologicalOrderSubset(subset);

            assertEquals(2, order.size());
            // order is not deterministic here because b is excluded from the subset
            assertTrue(order.contains(a));
            assertTrue(order.contains(c));
        }

        @Test
        @DisplayName("empty subset returns empty list")
        void emptySubset() throws CycleDetectedException {
            graph.addEdge("a", "b");

            List<String> order = graph.topologicalOrderSubset(Collections.emptySet());

            assertTrue(order.isEmpty());
        }
    }
}

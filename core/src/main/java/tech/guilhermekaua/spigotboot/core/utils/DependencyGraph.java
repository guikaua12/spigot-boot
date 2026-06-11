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
package tech.guilhermekaua.spigotboot.core.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.exceptions.CycleDetectedException;

import java.util.*;
import java.util.function.Function;

/**
 * Directed graph representing dependencies between two nodes.
 * <p>
 * An edge from A to B means "A depends on B" (A references B).
 * <p>
 */
public class DependencyGraph<T> {

    private final Map<T, Set<T>> adjacency = new LinkedHashMap<>();
    private final Map<T, Set<T>> reverseAdjacency = new LinkedHashMap<>();
    private final Set<T> allNodes = new LinkedHashSet<>();
    private final Function<T, String> nodeLabeler;

    public DependencyGraph(@Nullable Function<T, String> nodeLabeler) {
        this.nodeLabeler = nodeLabeler != null ? nodeLabeler : String::valueOf;
    }

    public DependencyGraph() {
        this(null);
    }

    /**
     * Adds a node to the graph without any edges.
     *
     * @param key the node key
     */
    public void addNode(@NotNull T key) {
        Objects.requireNonNull(key, "key cannot be null");
        allNodes.add(key);
        adjacency.computeIfAbsent(key, k -> new LinkedHashSet<>());
        reverseAdjacency.computeIfAbsent(key, k -> new LinkedHashSet<>());
    }

    /**
     * Adds a directed edge from source to target.
     * <p>
     * This represents that {@code from} depends on {@code to}.
     *
     * @param from the source node (the one that has the reference)
     * @param to   the target node (the one being referenced)
     */
    public void addEdge(@NotNull T from, @NotNull T to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");

        addNode(from);
        addNode(to);

        adjacency.get(from).add(to);
        reverseAdjacency.get(to).add(from);
    }

    /**
     * Gets all nodes in the graph.
     *
     * @return unmodifiable set of all nodes
     */
    public @NotNull Set<T> getAllNodes() {
        return Collections.unmodifiableSet(allNodes);
    }

    /**
     * Gets direct dependencies of a node.
     *
     * @param key the node key
     * @return unmodifiable set of dependencies
     */
    public @NotNull Set<T> getDependencies(@NotNull T key) {
        Set<T> deps = adjacency.get(key);
        return deps != null ? Collections.unmodifiableSet(deps) : Collections.emptySet();
    }

    /**
     * Gets direct dependents of a node (nodes that depend on this node).
     *
     * @param key the node key
     * @return unmodifiable set of dependents
     */
    public @NotNull Set<T> getDirectDependents(@NotNull T key) {
        Set<T> deps = reverseAdjacency.get(key);
        return deps != null ? Collections.unmodifiableSet(deps) : Collections.emptySet();
    }

    /**
     * Gets all transitive dependents of a node.
     * <p>
     * This computes the closure of all nodes that directly or indirectly
     * depend on the given node.
     *
     * @param key the node key
     * @return set of all transitive dependents (not including the node itself)
     */
    public @NotNull Set<T> getTransitiveDependents(@NotNull T key) {
        Set<T> result = new LinkedHashSet<>();
        Queue<T> queue = new LinkedList<>();
        queue.add(key);

        while (!queue.isEmpty()) {
            T current = queue.poll();
            for (T dependent : getDirectDependents(current)) {
                if (result.add(dependent)) {
                    queue.add(dependent);
                }
            }
        }

        return result;
    }

    /**
     * Gets reverse dependencies map for reload propagation.
     *
     * @return unmodifiable map of node -> set of nodes that depend on it
     */
    public @NotNull Map<T, Set<T>> getReverseDependencies() {
        Map<T, Set<T>> result = new LinkedHashMap<>();
        for (Map.Entry<T, Set<T>> entry : reverseAdjacency.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Computes topological order of all nodes.
     * <p>
     * Returns nodes in an order where dependencies come before dependents.
     * This means if A depends on B, then B will appear before A in the result.
     *
     * @return ordered list of nodes
     * @throws CycleDetectedException if a cycle is detected
     */
    @SuppressWarnings("unchecked")
    public @NotNull List<T> topologicalOrder() throws CycleDetectedException {
        Optional<List<T>> cycle = detectCycle();
        if (cycle.isPresent()) {
            throw new CycleDetectedException(cycle.get(), nodeLabeler);
        }

        // Kahn's algorithm
        Map<T, Integer> inDegree = new LinkedHashMap<>();
        for (T node : allNodes) {
            inDegree.put(node, 0);
        }

        for (Set<T> targets : adjacency.values()) {
            for (T target : targets) {
                inDegree.merge(target, 1, Integer::sum);
            }
        }

        // queue of nodes with no incoming edges
        Queue<T> queue = new LinkedList<>();
        for (Map.Entry<T, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<T> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            T node = queue.poll();
            result.add(node);

            for (T neighbor : getDependencies(node)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        Collections.reverse(result);
        return result;
    }

    /**
     * Computes topological order for a subset of nodes.
     * <p>
     * Only considers edges between nodes in the subset.
     *
     * @param subset the subset of nodes to order
     * @return ordered list
     * @throws CycleDetectedException if a cycle is detected
     */
    @SuppressWarnings("unchecked")
    public @NotNull List<T> topologicalOrderSubset(@NotNull Set<T> subset) throws CycleDetectedException {
        if (subset.isEmpty()) {
            return Collections.emptyList();
        }

        Map<T, Integer> inDegree = new LinkedHashMap<>();
        for (T node : subset) {
            inDegree.put(node, 0);
        }

        for (T node : subset) {
            for (T dep : getDependencies(node)) {
                if (subset.contains(dep)) {
                    inDegree.merge(dep, 1, Integer::sum);
                }
            }
        }

        Queue<T> queue = new LinkedList<>();
        for (Map.Entry<T, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<T> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            T node = queue.poll();
            result.add(node);

            for (T neighbor : getDependencies(node)) {
                if (!subset.contains(neighbor)) continue;
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (result.size() != subset.size()) {
            Set<T> remaining = new LinkedHashSet<>(subset);
            remaining.removeAll(result);
            List<T> cycle = findCycleInSubset(remaining);
            throw new CycleDetectedException(cycle, nodeLabeler);
        }

        Collections.reverse(result);
        return result;
    }

    /**
     * Detects if the graph contains a cycle.
     *
     * @return the cycle path if found, or empty if acyclic
     */
    public @NotNull Optional<List<T>> detectCycle() {
        Set<T> visited = new HashSet<>();
        Set<T> recursionStack = new HashSet<>();
        Map<T, T> parent = new HashMap<>();

        for (T node : allNodes) {
            if (!visited.contains(node)) {
                List<T> cycle = dfsDetectCycle(node, visited, recursionStack, parent);
                if (cycle != null) {
                    return Optional.of(cycle);
                }
            }
        }

        return Optional.empty();
    }

    private List<T> dfsDetectCycle(
            T node,
            Set<T> visited,
            Set<T> recursionStack,
            Map<T, T> parent) {

        visited.add(node);
        recursionStack.add(node);

        for (T neighbor : getDependencies(node)) {
            if (!visited.contains(neighbor)) {
                parent.put(neighbor, node);
                List<T> cycle = dfsDetectCycle(neighbor, visited, recursionStack, parent);
                if (cycle != null) {
                    return cycle;
                }
            } else if (recursionStack.contains(neighbor)) {
                // found cycle
                return reconstructCycle(node, neighbor, parent);
            }
        }

        recursionStack.remove(node);
        return null;
    }

    private List<T> reconstructCycle(T end, T start, Map<T, T> parent) {
        List<T> cycle = new ArrayList<>();
        cycle.add(start);

        T current = end;
        while (current != null && !current.equals(start)) {
            cycle.add(current);
            current = parent.get(current);
        }

        cycle.add(start);
        Collections.reverse(cycle);
        return cycle;
    }

    private List<T> findCycleInSubset(Set<T> subset) {
        Set<T> visited = new HashSet<>();
        Set<T> recursionStack = new HashSet<>();
        Map<T, T> parent = new HashMap<>();

        for (T node : subset) {
            if (!visited.contains(node)) {
                List<T> cycle = dfsDetectCycleSubset(node, visited, recursionStack, parent, subset);
                if (cycle != null) {
                    return cycle;
                }
            }
        }

        return new ArrayList<>(subset);
    }

    private List<T> dfsDetectCycleSubset(
            T node,
            Set<T> visited,
            Set<T> recursionStack,
            Map<T, T> parent,
            Set<T> subset) {

        visited.add(node);
        recursionStack.add(node);

        for (T neighbor : getDependencies(node)) {
            if (!subset.contains(neighbor)) continue;

            if (!visited.contains(neighbor)) {
                parent.put(neighbor, node);
                List<T> cycle = dfsDetectCycleSubset(neighbor, visited, recursionStack, parent, subset);
                if (cycle != null) {
                    return cycle;
                }
            } else if (recursionStack.contains(neighbor)) {
                return reconstructCycle(node, neighbor, parent);
            }
        }

        recursionStack.remove(node);
        return null;
    }

    /**
     * Clears all nodes and edges from the graph.
     */
    public void clear() {
        adjacency.clear();
        reverseAdjacency.clear();
        allNodes.clear();
    }

    /**
     * Removes all outgoing edges from a node.
     *
     * @param from the source node
     */
    public void removeOutgoingEdges(@NotNull T from) {
        Objects.requireNonNull(from, "from cannot be null");

        Set<T> oldTargets = adjacency.get(from);
        if (oldTargets != null) {
            for (T target : oldTargets) {
                Set<T> reverseSet = reverseAdjacency.get(target);
                if (reverseSet != null) {
                    reverseSet.remove(from);
                }
            }
            oldTargets.clear();
        }
    }

    /**
     * Replaces all outgoing edges from a node with new targets.
     * <p>
     * Equivalent to {@code removeOutgoingEdges(from)} followed by
     * {@code addEdge(from, target)} for each new target.
     *
     * @param from       the source node
     * @param newTargets the new set of target nodes
     */
    public void replaceOutgoingEdges(@NotNull T from, @NotNull Set<T> newTargets) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(newTargets, "newTargets cannot be null");

        removeOutgoingEdges(from);

        for (T target : newTargets) {
            addEdge(from, target);
        }
    }

    /**
     * Removes a node completely from the graph (including all its edges).
     *
     * @param key the node to remove
     */
    public void removeNode(@NotNull T key) {
        Objects.requireNonNull(key, "key cannot be null");

        removeOutgoingEdges(key);

        // remove incoming edges (from other nodes to this one)
        Set<T> dependents = reverseAdjacency.remove(key);
        if (dependents != null) {
            for (T dependent : dependents) {
                Set<T> depTargets = adjacency.get(dependent);
                if (depTargets != null) {
                    depTargets.remove(key);
                }
            }
        }

        allNodes.remove(key);
        adjacency.remove(key);
    }

    /**
     * Gets the number of nodes in the graph.
     *
     * @return node count
     */
    public int size() {
        return allNodes.size();
    }

    /**
     * Checks if the graph is empty.
     *
     * @return true if no nodes
     */
    public boolean isEmpty() {
        return allNodes.isEmpty();
    }
}

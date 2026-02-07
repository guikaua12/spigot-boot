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
package tech.guilhermekaua.spigotboot.config.spigot.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.*;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.spigot.SpigotConfigManager;
import tech.guilhermekaua.spigotboot.core.exceptions.CycleDetectedException;
import tech.guilhermekaua.spigotboot.core.utils.DependencyGraph;

import java.util.*;
import java.util.logging.Logger;

/**
 * Manages config reference resolution infrastructure.
 */
public class ConfigReferenceManager {

    private final SpigotConfigReferenceLookup lookup;
    private final ConfigReferenceParser parser;
    private final ConfigReferenceErrorHandler errorHandler;
    private final ConfigReferenceResolver resolver;
    private final ConfigReferenceDependencyScanner scanner;
    private final ReferenceResolvingPreprocessor preprocessor;
    private final DependencyGraph<ReferenceKey> dependencyGraph;

    /**
     * Creates a new reference manager with default error handling.
     *
     * @param configManager the config manager for lookups
     * @param logger        the logger for error messages
     */
    public ConfigReferenceManager(@NotNull SpigotConfigManager configManager, @NotNull Logger logger) {
        this(configManager, new DefaultConfigReferenceErrorHandler(logger));
    }

    /**
     * Creates a new reference manager with custom error handling.
     *
     * @param configManager the config manager for lookups
     * @param errorHandler  the error handler for reference resolution errors
     */
    public ConfigReferenceManager(@NotNull SpigotConfigManager configManager,
                                  @NotNull ConfigReferenceErrorHandler errorHandler) {
        Objects.requireNonNull(configManager, "configManager cannot be null");
        Objects.requireNonNull(errorHandler, "errorHandler cannot be null");

        this.lookup = new SpigotConfigReferenceLookup(configManager);
        this.parser = new ConfigReferenceParser();
        this.errorHandler = errorHandler;
        this.resolver = new ConfigReferenceResolver(lookup, parser, errorHandler);
        this.scanner = new ConfigReferenceDependencyScanner(parser);
        this.preprocessor = new ReferenceResolvingPreprocessor(resolver);
        this.dependencyGraph = new DependencyGraph<>();
    }

    /**
     * Gets the preprocessor for binder integration.
     * <p>
     * This preprocessor resolves {@code ${...}} references during binding.
     *
     * @return the preprocessor
     */
    public @NotNull ReferenceResolvingPreprocessor getPreprocessor() {
        return preprocessor;
    }

    /**
     * Gets the reference resolver.
     *
     * @return the resolver
     */
    public @NotNull ConfigReferenceResolver getResolver() {
        return resolver;
    }

    /**
     * Gets the dependency graph.
     *
     * @return the graph
     */
    public @NotNull DependencyGraph<ReferenceKey> getDependencyGraph() {
        return dependencyGraph;
    }

    /**
     * Scans a config node for references and registers them in the graph.
     *
     * @param sourceKey the key of the config being scanned
     * @param node      the config node to scan
     */
    public void scanAndRegister(@NotNull ReferenceKey sourceKey, @NotNull ConfigNode node) {
        scanner.scanAndAddToGraph(sourceKey, node, dependencyGraph);
    }

    /**
     * Gets the topological load order for all registered configs.
     * <p>
     * Configs that are depended upon come first.
     *
     * @return the load order
     * @throws CycleDetectedException if a cycle is detected
     */
    public @NotNull List<ReferenceKey> getLoadOrder() throws CycleDetectedException {
        return dependencyGraph.topologicalOrder();
    }

    /**
     * Checks if the dependency graph has a cycle.
     *
     * @return the cycle chain if exists, empty otherwise
     */
    public @NotNull Optional<List<ReferenceKey>> detectCycle() {
        return dependencyGraph.detectCycle();
    }

    /**
     * Gets all configs that depend (transitively) on the given config.
     * <p>
     * Used for reload propagation - when config X changes, all its
     * dependents need to be re-resolved.
     *
     * @param key the config key
     * @return set of dependent config keys
     */
    public @NotNull Set<ReferenceKey> getDependentsOf(@NotNull ReferenceKey key) {
        return dependencyGraph.getTransitiveDependents(key);
    }

    /**
     * Gets the reverse dependencies map.
     *
     * @return map from each key to its dependents
     */
    public @NotNull Map<ReferenceKey, Set<ReferenceKey>> getReverseDependencies() {
        return dependencyGraph.getReverseDependencies();
    }

    /**
     * Clears all dependency tracking.
     * <p>
     * Call this before a full reload to rebuild the graph.
     */
    public void clearDependencies() {
        dependencyGraph.clear();
    }

    /**
     * Prepares for full reload by clearing all dependencies.
     * <p>
     * Alias for {@link #clearDependencies()} with a more descriptive name.
     */
    public void resetDependencies() {
        dependencyGraph.clear();
    }

    /**
     * Updates dependencies for a key after its node changed.
     * <p>
     * Removes old outgoing edges and scans for new references.
     *
     * @param key     the key that changed
     * @param newNode the new node to scan for references
     */
    public void updateDependenciesForKey(@NotNull ReferenceKey key, @NotNull ConfigNode newNode) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(newNode, "newNode cannot be null");
        dependencyGraph.removeOutgoingEdges(key);
        scanner.scanAndAddToGraph(key, newNode, dependencyGraph);
    }

    /**
     * Gets keys impacted by a change to the given key.
     * <p>
     * Includes the key itself plus all transitive dependents.
     *
     * @param changedKey the key that changed
     * @return set of impacted keys (includes changedKey)
     */
    public @NotNull Set<ReferenceKey> getImpactedKeys(@NotNull ReferenceKey changedKey) {
        Objects.requireNonNull(changedKey, "changedKey cannot be null");
        Set<ReferenceKey> impacted = new LinkedHashSet<>();
        impacted.add(changedKey);
        impacted.addAll(dependencyGraph.getTransitiveDependents(changedKey));
        return impacted;
    }

    /**
     * Gets topological order for a subset of keys.
     *
     * @param keys the keys to order
     * @return ordered list (dependencies first)
     * @throws CycleDetectedException if a cycle exists in the subset
     */
    public @NotNull List<ReferenceKey> getLoadOrderForSubset(@NotNull Set<ReferenceKey> keys)
            throws CycleDetectedException {
        Objects.requireNonNull(keys, "keys cannot be null");
        return dependencyGraph.topologicalOrderSubset(keys);
    }

    /**
     * Sets the current source key for resolution context.
     * <p>
     * Call this before binding a config to provide context for error messages.
     *
     * @param sourceKey the key of the config being bound
     */
    public void setCurrentSourceKey(@NotNull ReferenceKey sourceKey) {
        preprocessor.setCurrentSourceKey(sourceKey);
    }

    /**
     * Clears the current source key.
     */
    public void clearCurrentSourceKey() {
        preprocessor.clearCurrentSourceKey();
    }

    /**
     * Resolves a reference string to its target node.
     *
     * @param reference the reference string (e.g., "${items:custom_item}")
     * @param sourceKey the key of the config containing the reference
     * @return the resolved node, or null if not found
     */
    public @Nullable ConfigNode resolveReference(@NotNull String reference, @NotNull ReferenceKey sourceKey) {
        Optional<ConfigReference> parsed = parser.tryParse(reference);
        if (!parsed.isPresent()) {
            return null;
        }
        return lookup.resolve(parsed.get());
    }

    /**
     * Checks if a string is a reference pattern.
     *
     * @param value the string to check
     * @return true if it matches the reference pattern
     */
    public boolean isReference(@Nullable String value) {
        return resolver.isReference(value);
    }
}

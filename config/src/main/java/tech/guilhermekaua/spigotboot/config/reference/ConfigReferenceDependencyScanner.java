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
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.core.utils.DependencyGraph;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Scans config nodes for references and builds a dependency graph.
 * <p>
 * This scanner walks the tree of a config node and finds all {@code ${...}}
 * reference patterns, then adds them as edges in the dependency graph.
 */
public class ConfigReferenceDependencyScanner {

    private final ConfigReferenceParser parser;

    /**
     * Creates a new scanner.
     *
     * @param parser the reference parser
     */
    public ConfigReferenceDependencyScanner(@NotNull ConfigReferenceParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser cannot be null");
    }

    /**
     * Creates a scanner with the default parser.
     *
     * @return a new scanner
     */
    public static @NotNull ConfigReferenceDependencyScanner create() {
        return new ConfigReferenceDependencyScanner(new ConfigReferenceParser());
    }

    /**
     * Scans a config node for references and returns the set of dependencies.
     *
     * @param node the node to scan
     * @return set of reference keys that this node depends on
     */
    public @NotNull Set<ReferenceKey> scanDependencies(@NotNull ConfigNode node) {
        Set<ReferenceKey> dependencies = new LinkedHashSet<>();
        scanNode(node, ref -> dependencies.add(ReferenceKey.fromReference(ref)));
        return dependencies;
    }

    /**
     * Scans a config node and adds edges to the graph.
     *
     * @param sourceKey the key of the config being scanned
     * @param node      the node to scan
     * @param graph     the graph to add edges to
     */
    public void scanAndAddToGraph(
            @NotNull ReferenceKey sourceKey,
            @NotNull ConfigNode node,
            @NotNull DependencyGraph<ReferenceKey> graph) {
        graph.addNode(sourceKey);
        scanNode(node, ref -> {
            ReferenceKey targetKey = ReferenceKey.fromReference(ref);
            if (sourceKey.equals(targetKey) && !ref.isRootReference()) {
                return;
            }
            graph.addEdge(sourceKey, targetKey);
        });
    }

    private void scanNode(@NotNull ConfigNode node, @NotNull Consumer<ConfigReference> onReference) {
        if (node.isVirtual() || node.isNull()) {
            return;
        }

        if (node.isScalar()) {
            String value = node.get(String.class);
            if (value != null) {
                Optional<ConfigReference> ref = parser.tryParse(value);
                ref.ifPresent(onReference);
            }
        } else if (node.isMap()) {
            for (ConfigNode child : node.childrenMap().values()) {
                scanNode(child, onReference);
            }
        } else if (node.isList()) {
            for (ConfigNode child : node.childrenList()) {
                scanNode(child, onReference);
            }
        }
    }
}

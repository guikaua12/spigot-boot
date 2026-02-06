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
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigCircularReferenceContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigReferenceNotFoundContext;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.reference.key.ResolutionTarget;
import tech.guilhermekaua.spigotboot.config.spigot.node.SnapshotConfigNode;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Resolves config references ({@code ${...}}) to their target values.
 * <p>
 * This resolver supports:
 * <ul>
 *   <li>Single config references: {@code ${configName}} or {@code ${configName:path}}</li>
 *   <li>Folder config item references: {@code ${folderConfigName.itemId}} or {@code ${folderConfigName.itemId:path}}</li>
 *   <li>Deep/recursive resolution: references inside resolved values are also resolved</li>
 *   <li>Cycle detection: prevents infinite loops in circular references</li>
 * </ul>
 */
public class ConfigReferenceResolver {

    private final ConfigReferenceLookup lookup;
    private final ConfigReferenceParser parser;
    private final ConfigReferenceErrorHandler errorHandler;

    /**
     * Creates a new resolver.
     *
     * @param lookup       the lookup for finding config nodes
     * @param parser       the parser for parsing reference tokens
     * @param errorHandler the handler for resolution errors
     */
    public ConfigReferenceResolver(
            @NotNull ConfigReferenceLookup lookup,
            @NotNull ConfigReferenceParser parser,
            @NotNull ConfigReferenceErrorHandler errorHandler) {
        this.lookup = Objects.requireNonNull(lookup, "lookup cannot be null");
        this.parser = Objects.requireNonNull(parser, "parser cannot be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler cannot be null");
    }

    /**
     * Resolves a config node if it's a reference.
     * <p>
     * If the node is a scalar string that matches the reference pattern,
     * it will be resolved to the target node. Otherwise, the original node
     * is returned unchanged.
     *
     * @param node        the node to potentially resolve
     * @param sourceKey   the key of the config containing this node
     * @param sourceField the field being bound (may be null)
     * @return the resolved node, or null if reference not found
     */
    public @Nullable ConfigNode resolveIfReference(
            @NotNull ConfigNode node,
            @NotNull ReferenceKey sourceKey,
            @Nullable Field sourceField) {
        return resolveIfReference(node, sourceKey, sourceField, new LinkedHashSet<>());
    }

    /**
     * Resolves a config node if it's a reference, with cycle detection.
     *
     * @param node            the node to potentially resolve
     * @param sourceKey       the key of the config containing this node
     * @param sourceField     the field being bound (may be null)
     * @param resolutionStack the stack of keys currently being resolved (for cycle detection)
     * @return the resolved node, or null if reference not found
     */
    private @Nullable ConfigNode resolveIfReference(
            @NotNull ConfigNode node,
            @NotNull ReferenceKey sourceKey,
            @Nullable Field sourceField,
            @NotNull Set<ResolutionTarget> resolutionStack) {

        if (!node.isScalar()) {
            return node;
        }

        String value = node.get(String.class);
        if (value == null) {
            return node;
        }

        Optional<ConfigReference> maybeRef = parser.tryParse(value);
        if (!maybeRef.isPresent()) {
            return node;
        }

        ConfigReference ref = maybeRef.get();
        ReferenceKey targetKey = ReferenceKey.fromReference(ref);
        ResolutionTarget target = ResolutionTarget.of(targetKey, ref.getPath());

        if (resolutionStack.contains(target)) {
            List<ReferenceKey> chain = buildCycleChain(resolutionStack, target);
            ConfigCircularReferenceContext ctx = new ConfigCircularReferenceContext(
                    sourceKey, sourceField, ref.getRawToken(), chain);
            errorHandler.onCircularReference(ctx);
            return null;
        }

        ConfigNode targetNode = lookup.resolve(ref);
        if (targetNode == null) {
            Set<String> alternatives = findAlternatives(ref);
            ConfigReferenceNotFoundContext ctx = new ConfigReferenceNotFoundContext(
                    sourceKey, sourceField, ref.getRawToken(), alternatives);
            errorHandler.onReferenceNotFound(ctx);
            return null;
        }

        Set<ResolutionTarget> newStack = new LinkedHashSet<>(resolutionStack);
        newStack.add(target);

        return deepResolve(targetNode, targetKey, newStack);
    }

    /**
     * Deeply resolves all references in a config node tree.
     * <p>
     * This walks the entire tree and resolves any reference values found,
     * including nested references.
     *
     * @param node      the root node to resolve
     * @param sourceKey the key of the config this node belongs to
     * @return the resolved node tree
     */
    public @NotNull ConfigNode deepResolve(
            @NotNull ConfigNode node,
            @NotNull ReferenceKey sourceKey) {
        return deepResolve(node, sourceKey, new LinkedHashSet<>());
    }

    /**
     * Deeply resolves all references in a config node tree with cycle detection.
     *
     * @param node            the root node to resolve
     * @param sourceKey       the key of the config this node belongs to
     * @param resolutionStack the stack of keys currently being resolved
     * @return the resolved node tree
     */
    private @NotNull ConfigNode deepResolve(
            @NotNull ConfigNode node,
            @NotNull ReferenceKey sourceKey,
            @NotNull Set<ResolutionTarget> resolutionStack) {

        if (node.isVirtual() || node.isNull()) {
            return node;
        }

        if (node.isScalar()) {
            ConfigNode resolved = resolveIfReference(node, sourceKey, null, resolutionStack);
            return resolved != null ? resolved : node;
        }

        if (node.isMap()) {
            Map<String, Object> resolvedChildren = new LinkedHashMap<>();
            boolean changed = false;

            for (Map.Entry<String, ? extends ConfigNode> entry : node.childrenMap().entrySet()) {
                ConfigNode child = entry.getValue();
                ConfigNode resolvedChild = deepResolve(child, sourceKey, resolutionStack);
                if (resolvedChild != child) {
                    changed = true;
                }
                resolvedChildren.put(entry.getKey(), toDetachedRaw(resolvedChild));
            }

            if (!changed) {
                return node;
            }

            return SnapshotConfigNode.of(resolvedChildren, node.path());
        }

        if (node.isList()) {
            List<Object> resolvedChildren = new ArrayList<>();
            boolean changed = false;

            for (ConfigNode child : node.childrenList()) {
                ConfigNode resolvedChild = deepResolve(child, sourceKey, resolutionStack);
                if (resolvedChild != child) {
                    changed = true;
                }
                resolvedChildren.add(toDetachedRaw(resolvedChild));
            }

            if (!changed) {
                return node;
            }

            return SnapshotConfigNode.of(resolvedChildren, node.path());
        }

        return node;
    }

    /**
     * Checks if a string value is a reference.
     *
     * @param value the string value
     * @return true if it's a reference pattern
     */
    public boolean isReference(@Nullable String value) {
        if (value == null) {
            return false;
        }
        return parser.tryParse(value).isPresent();
    }

    private List<ReferenceKey> buildCycleChain(Set<ResolutionTarget> stack, ResolutionTarget cyclePoint) {
        List<ReferenceKey> chain = new ArrayList<>(stack.size() + 1);
        for (ResolutionTarget target : stack) {
            chain.add(target.getKey());
        }
        chain.add(cyclePoint.getKey());
        return chain;
    }

    private Set<String> findAlternatives(ConfigReference ref) {
        Set<String> alternatives = new LinkedHashSet<>();

        if (ref.isSingleConfig()) {
            alternatives.addAll(lookup.getAvailableConfigNames());
        } else {
            String folderConfigName = ref.getFolderConfigName();
            if (lookup.hasFolderConfig(folderConfigName)) {
                alternatives.addAll(lookup.getAvailableItemIds(folderConfigName));
            } else {
                alternatives.addAll(lookup.getAvailableFolderConfigNames());
            }
        }

        return alternatives;
    }

    private @Nullable Object toDetachedRaw(@NotNull ConfigNode node) {
        if (node.isVirtual() || node.isNull()) {
            return null;
        }

        if (node.isMap()) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, ? extends ConfigNode> entry : node.childrenMap().entrySet()) {
                copy.put(entry.getKey(), toDetachedRaw(entry.getValue()));
            }
            return copy;
        }

        if (node.isList()) {
            List<Object> copy = new ArrayList<>();
            for (ConfigNode child : node.childrenList()) {
                copy.add(toDetachedRaw(child));
            }
            return copy;
        }

        return node.raw();
    }
}

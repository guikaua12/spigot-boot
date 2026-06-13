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
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.reference.key.ResolutionTarget;
import tech.guilhermekaua.spigotboot.config.spigot.node.SnapshotConfigNode;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
        return resolveIfReference(node, sourceKey, sourceField, null);
    }

    /**
     * Resolves a config node if it's a reference, checking the resolved value against
     * an expected target type.
     * <p>
     * Behaves like {@link #resolveIfReference(ConfigNode, ReferenceKey, Field)}, but when
     * {@code expectedType} is provided and the resolved value cannot be coerced into it,
     * {@link ConfigReferenceErrorHandler#onTypeMismatch(ConfigTypeMismatchContext)} is invoked.
     *
     * @param node         the node to potentially resolve
     * @param sourceKey    the key of the config containing this node
     * @param sourceField  the field being bound (may be null)
     * @param expectedType the expected target type, or null to skip the type-mismatch check
     * @return the resolved node, or null if reference not found
     */
    public @Nullable ConfigNode resolveIfReference(
            @NotNull ConfigNode node,
            @NotNull ReferenceKey sourceKey,
            @Nullable Field sourceField,
            @Nullable Type expectedType) {
        return resolveIfReference(node, sourceKey, sourceField, expectedType, new LinkedHashSet<>());
    }

    /**
     * Resolves a config node if it's a reference, with cycle detection.
     *
     * @param node            the node to potentially resolve
     * @param sourceKey       the key of the config containing this node
     * @param sourceField     the field being bound (may be null)
     * @param expectedType    the expected target type, or null to skip the type-mismatch check
     * @param resolutionStack the stack of keys currently being resolved (for cycle detection)
     * @return the resolved node, or null if reference not found
     */
    private @Nullable ConfigNode resolveIfReference(
            @NotNull ConfigNode node,
            @NotNull ReferenceKey sourceKey,
            @Nullable Field sourceField,
            @Nullable Type expectedType,
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

        ConfigNode resolved = deepResolve(targetNode, targetKey, newStack);
        return checkTypeMismatch(resolved, ref, sourceKey, sourceField, expectedType);
    }

    /**
     * Verifies a resolved reference value against the expected target type.
     * <p>
     * When the resolved value is a scalar that cannot be coerced into the expected
     * scalar/primitive type, {@link ConfigReferenceErrorHandler#onTypeMismatch} is
     * invoked and its fallback value (typically null) is used instead.
     *
     * @param resolved     the resolved node (may be null)
     * @param ref          the reference that produced the value
     * @param sourceKey    the key of the config containing the reference
     * @param sourceField  the field being bound (may be null)
     * @param expectedType the expected target type, or null to skip the check
     * @return the resolved node, the wrapped fallback value, or null
     */
    private @Nullable ConfigNode checkTypeMismatch(
            @Nullable ConfigNode resolved,
            @NotNull ConfigReference ref,
            @NotNull ReferenceKey sourceKey,
            @Nullable Field sourceField,
            @Nullable Type expectedType) {

        if (resolved == null || expectedType == null || !resolved.isScalar()) {
            return resolved;
        }

        Class<?> expectedClass = rawClassOf(expectedType);
        if (expectedClass == null || !isScalarTarget(expectedClass)) {
            return resolved;
        }

        Object actualValue = resolved.raw();
        if (actualValue == null || canCoerce(actualValue, expectedClass)) {
            return resolved;
        }

        ConfigTypeMismatchContext ctx = new ConfigTypeMismatchContext(
                sourceKey, sourceField, ref.getRawToken(), expectedType, actualValue.getClass(), actualValue);
        Object fallback = errorHandler.onTypeMismatch(ctx);
        if (fallback == null) {
            return null;
        }
        return SnapshotConfigNode.of(fallback, resolved.path());
    }

    private static @Nullable Class<?> rawClassOf(@NotNull Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            if (raw instanceof Class) {
                return (Class<?>) raw;
            }
        }
        return null;
    }

    // only primitives and java.lang scalar wrappers are coerced by the binder via
    // ConfigNode#get; complex types, collections, and enums use other binding paths
    private static boolean isScalarTarget(@NotNull Class<?> type) {
        return type.isPrimitive() || type.getName().startsWith("java.lang.");
    }

    // mirrors AbstractValueConfigNode#get coercion rules so detection is independent
    // of the concrete ConfigNode implementation
    private static boolean canCoerce(@NotNull Object value, @NotNull Class<?> targetType) {
        if (targetType.isInstance(value)) {
            return true;
        }
        if (targetType == String.class || targetType == CharSequence.class || targetType == Object.class) {
            return true;
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return true;
        }
        if (isNumericType(targetType)) {
            return value instanceof Number || canParseNumber(String.valueOf(value), targetType);
        }
        return false;
    }

    private static boolean isNumericType(@NotNull Class<?> type) {
        return type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Double.class || type == double.class
                || type == Float.class || type == float.class;
    }

    private static boolean canParseNumber(@NotNull String value, @NotNull Class<?> targetType) {
        try {
            if (targetType == Integer.class || targetType == int.class) {
                Integer.valueOf(value);
            } else if (targetType == Long.class || targetType == long.class) {
                Long.valueOf(value);
            } else if (targetType == Double.class || targetType == double.class) {
                Double.valueOf(value);
            } else if (targetType == Float.class || targetType == float.class) {
                Float.valueOf(value);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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
            ConfigNode resolved = resolveIfReference(node, sourceKey, null, null, resolutionStack);
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

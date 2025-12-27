package tech.guilhermekaua.spigotboot.config.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.util.List;
import java.util.Map;

/**
 * Immutable representation of a configuration node.
 * <p>
 * A ConfigNode wraps a value in the configuration tree and provides
 * type-safe access to its data. Nodes can be scalars, maps, or lists.
 */
public interface ConfigNode {

    /**
     * Gets the raw underlying value.
     *
     * @return the raw value, or null if the node is empty
     */
    @Nullable Object raw();

    /**
     * Gets the value converted to the specified type.
     *
     * @param type the target type
     * @param <T>  the type parameter
     * @return the converted value, or null if conversion fails or value is null
     */
    <T> @Nullable T get(@NotNull Class<T> type);

    /**
     * Gets the value converted to the specified type, with a default fallback.
     *
     * @param type         the target type
     * @param defaultValue the default value if conversion fails or value is null
     * @param <T>          the type parameter
     * @return the converted value or the default
     */
    <T> @NotNull T get(@NotNull Class<T> type, @NotNull T defaultValue);

    /**
     * Navigates to a child node by path segments.
     * <p>
     * If the path doesn't exist, returns a virtual (empty) node.
     *
     * @param path the path segments (strings for map keys, integers for list indices)
     * @return the child node, never null
     */
    @NotNull ConfigNode node(@NotNull Object... path);

    /**
     * Checks if this node has a child at the given path.
     *
     * @param path the path segments
     * @return true if the child exists and is not virtual
     */
    boolean hasChild(@NotNull Object... path);

    /**
     * Gets all children as a map (for map nodes).
     *
     * @return the children map, or empty map if not a map node
     */
    @NotNull Map<String, ? extends ConfigNode> childrenMap();

    /**
     * Gets all children as a list (for list nodes).
     *
     * @return the children list, or empty list if not a list node
     */
    @NotNull List<? extends ConfigNode> childrenList();

    /**
     * Checks if this node is a map (object) node.
     *
     * @return true if this node contains key-value pairs
     */
    boolean isMap();

    /**
     * Checks if this node is a list (array) node.
     *
     * @return true if this node contains a list of values
     */
    boolean isList();

    /**
     * Checks if this node is a scalar (primitive/string) value.
     *
     * @return true if this node contains a scalar value
     */
    boolean isScalar();

    /**
     * Checks if this node's value is null.
     *
     * @return true if the value is null
     */
    boolean isNull();

    /**
     * Checks if this node is virtual (doesn't exist in the source).
     * <p>
     * Virtual nodes are returned when navigating to non-existent paths.
     *
     * @return true if this node is virtual
     */
    boolean isVirtual();

    /**
     * Gets the path to this node from root.
     *
     * @return the path to this node
     */
    @NotNull PropertyPath path();
}

package tech.guilhermekaua.spigotboot.config.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable configuration node that supports modification operations.
 * <p>
 * Used during serialization to build configuration trees.
 */
public interface MutableConfigNode extends ConfigNode {

    /**
     * Sets the value of this node.
     *
     * @param value the value to set, or null to clear
     * @return this node for chaining
     */
    @NotNull MutableConfigNode set(@Nullable Object value);

    /**
     * {@inheritDoc}
     * <p>
     * For mutable nodes, this creates the path if it doesn't exist.
     */
    @Override
    @NotNull MutableConfigNode node(@NotNull Object... path);

    /**
     * Sets a comment for this node (if supported by the format).
     *
     * @param lines the comment lines, or null to remove
     * @return this node for chaining
     */
    @NotNull MutableConfigNode setComment(@Nullable String... lines);

    /**
     * Removes this node from its parent.
     *
     * @return this node for chaining
     */
    @NotNull MutableConfigNode remove();

    /**
     * Appends a new item to this list node.
     * <p>
     * If this node is not a list, it will be converted to one.
     *
     * @return the new list item node
     */
    @NotNull MutableConfigNode appendListItem();
}

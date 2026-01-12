package tech.guilhermekaua.spigotboot.config.reference.key;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Key for an item within a {@code @ConfigCollection}.
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class CollectionItemKey extends ReferenceKey {
    private final String collectionName;
    private final String itemId;

    public CollectionItemKey(@NotNull String collectionName, @NotNull String itemId) {
        this.collectionName = Objects.requireNonNull(collectionName, "collectionName cannot be null");
        this.itemId = Objects.requireNonNull(itemId, "itemId cannot be null");
    }

    /**
     * Gets the collection name.
     *
     * @return the collection name
     */
    public @NotNull String getCollectionName() {
        return collectionName;
    }

    /**
     * Gets the item ID.
     *
     * @return the item ID
     */
    public @NotNull String getItemId() {
        return itemId;
    }

    @Override
    public boolean isSingleConfig() {
        return false;
    }

    @Override
    public boolean isCollectionItem() {
        return true;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "collection:" + collectionName + "." + itemId;
    }
}
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

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable representation of a parsed config reference token.
 * <p>
 * Reference syntax supports:
 * <ul>
 *   <li>{@code ${configName}} - single config root</li>
 *   <li>{@code ${configName:path.to.value}} - single config with path</li>
 *   <li>{@code ${collectionName.itemId}} - collection item root</li>
 *   <li>{@code ${collectionName.itemId:path.to.value}} - collection item with path</li>
 * </ul>
 */
@EqualsAndHashCode
@ToString
public final class ConfigReference {

    private final String rawToken;
    private final ReferenceTargetKind kind;
    private final String targetName;
    private final String itemId;
    private final String path;

    /**
     * Creates a reference to a single config.
     *
     * @param rawToken   the original token string (e.g., "${configName:path}")
     * @param configName the config name
     * @param path       the path within the config, or null for root
     * @return the config reference
     */
    public static @NotNull ConfigReference singleConfig(
            @NotNull String rawToken,
            @NotNull String configName,
            @Nullable String path) {
        return new ConfigReference(rawToken, ReferenceTargetKind.SINGLE_CONFIG, configName, null, path);
    }

    /**
     * Creates a reference to a collection item.
     *
     * @param rawToken       the original token string
     * @param collectionName the collection name
     * @param itemId         the item ID within the collection
     * @param path           the path within the item, or null for root
     * @return the config reference
     */
    public static @NotNull ConfigReference collectionItem(
            @NotNull String rawToken,
            @NotNull String collectionName,
            @NotNull String itemId,
            @Nullable String path) {
        return new ConfigReference(rawToken, ReferenceTargetKind.COLLECTION_ITEM, collectionName, itemId, path);
    }

    private ConfigReference(
            @NotNull String rawToken,
            @NotNull ReferenceTargetKind kind,
            @NotNull String targetName,
            @Nullable String itemId,
            @Nullable String path) {
        this.rawToken = Objects.requireNonNull(rawToken, "rawToken cannot be null");
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
        this.targetName = Objects.requireNonNull(targetName, "targetName cannot be null");
        this.itemId = itemId;
        this.path = path;
    }

    /**
     * Gets the original raw token string (e.g., "${items:items.custom_exp_bottle}").
     *
     * @return the raw token
     */
    public @NotNull String getRawToken() {
        return rawToken;
    }

    /**
     * Gets the kind of reference target.
     *
     * @return the target kind
     */
    public @NotNull ReferenceTargetKind getKind() {
        return kind;
    }

    /**
     * Gets the target name.
     * <p>
     * For {@link ReferenceTargetKind#SINGLE_CONFIG}, this is the config name.
     * For {@link ReferenceTargetKind#COLLECTION_ITEM}, this is the collection name.
     *
     * @return the target name
     */
    public @NotNull String getTargetName() {
        return targetName;
    }

    /**
     * Gets the config name for single config references.
     *
     * @return the config name
     * @throws IllegalStateException if this is not a single config reference
     */
    public @NotNull String getConfigName() {
        if (kind != ReferenceTargetKind.SINGLE_CONFIG) {
            throw new IllegalStateException("Not a single config reference: " + rawToken);
        }
        return targetName;
    }

    /**
     * Gets the collection name for collection item references.
     *
     * @return the collection name
     * @throws IllegalStateException if this is not a collection item reference
     */
    public @NotNull String getCollectionName() {
        if (kind != ReferenceTargetKind.COLLECTION_ITEM) {
            throw new IllegalStateException("Not a collection item reference: " + rawToken);
        }
        return targetName;
    }

    /**
     * Gets the item ID for collection item references.
     *
     * @return the item ID, or null if this is a single config reference
     */
    public @Nullable String getItemId() {
        return itemId;
    }

    /**
     * Gets the path within the target config/item.
     *
     * @return the path, or null if referencing the root
     */
    public @Nullable String getPath() {
        return path;
    }

    /**
     * Checks if this reference targets the root of the config/item.
     *
     * @return true if no path is specified
     */
    public boolean isRootReference() {
        return path == null || path.isEmpty();
    }

    /**
     * Checks if this reference targets a single config.
     *
     * @return true if single config reference
     */
    public boolean isSingleConfig() {
        return kind == ReferenceTargetKind.SINGLE_CONFIG;
    }

    /**
     * Checks if this reference targets a collection item.
     *
     * @return true if collection item reference
     */
    public boolean isCollectionItem() {
        return kind == ReferenceTargetKind.COLLECTION_ITEM;
    }
}

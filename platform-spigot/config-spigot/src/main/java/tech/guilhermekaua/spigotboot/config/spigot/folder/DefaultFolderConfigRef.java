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
package tech.guilhermekaua.spigotboot.config.spigot.folder;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.folder.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Default implementation of {@link FolderConfigRef}.
 * <p>
 * This class provides thread-safe access to the folder config snapshot using
 * atomic reference swapping. All reads are lock-free.
 *
 * @param <T> the item type
 */
public final class DefaultFolderConfigRef<T> implements FolderConfigRef<T> {

    private final Class<T> itemType;
    private final String folderConfigName;
    private final AtomicReference<FolderConfigSnapshot<T>> snapshotRef;
    private final List<FolderConfigChangeListener<T>> listeners = new CopyOnWriteArrayList<>();
    private final FolderConfigEditor<T> editor;
    private final Runnable reloadAllHandler;
    private final Consumer<String> reloadItemHandler;

    /**
     * Creates a new folder config reference.
     *
     * @param itemType          the item type class
     * @param folderConfigName  the folder config name
     * @param editor            the folder config editor
     * @param reloadAllHandler  handler for reloading all items
     * @param reloadItemHandler handler for reloading a single item
     */
    public DefaultFolderConfigRef(
            @NotNull Class<T> itemType,
            @NotNull String folderConfigName,
            @NotNull FolderConfigEditor<T> editor,
            @NotNull Runnable reloadAllHandler,
            @NotNull Consumer<String> reloadItemHandler) {
        this.itemType = Objects.requireNonNull(itemType, "itemType cannot be null");
        this.folderConfigName = Objects.requireNonNull(folderConfigName, "folderConfigName cannot be null");
        this.editor = Objects.requireNonNull(editor, "editor cannot be null");
        this.reloadAllHandler = Objects.requireNonNull(reloadAllHandler, "reloadAllHandler cannot be null");
        this.reloadItemHandler = Objects.requireNonNull(reloadItemHandler, "reloadItemHandler cannot be null");
        this.snapshotRef = new AtomicReference<>(
                DefaultFolderConfigSnapshot.empty(itemType, folderConfigName)
        );
    }

    @Override
    public @NotNull FolderConfigSnapshot<T> get() {
        return snapshotRef.get();
    }

    @Override
    public @NotNull FolderConfigEditor<T> edit() {
        return editor;
    }

    @Override
    public void reloadAll() {
        reloadAllHandler.run();
    }

    @Override
    public void reloadItem(@NotNull String id) {
        Objects.requireNonNull(id, "id cannot be null");
        reloadItemHandler.accept(id);
    }

    @Override
    public void addListener(@NotNull FolderConfigChangeListener<T> listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.add(listener);
    }

    @Override
    public void removeListener(@NotNull FolderConfigChangeListener<T> listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.remove(listener);
    }

    @Override
    public @NotNull Class<T> getItemType() {
        return itemType;
    }

    @Override
    public @NotNull String getFolderConfigName() {
        return folderConfigName;
    }

    /**
     * Updates the snapshot and notifies listeners of changes.
     *
     * @param newSnapshot the new snapshot
     * @param changes     the list of changes
     */
    public void updateSnapshot(
            @NotNull FolderConfigSnapshot<T> newSnapshot,
            @NotNull List<FolderConfigItemChange<T>> changes) {
        snapshotRef.set(newSnapshot);

        for (FolderConfigItemChange<T> change : changes) {
            notifyListeners(change);
        }
    }

    /**
     * Updates the snapshot without notifying listeners.
     *
     * @param newSnapshot the new snapshot
     */
    public void setSnapshot(@NotNull FolderConfigSnapshot<T> newSnapshot) {
        snapshotRef.set(newSnapshot);
    }

    /**
     * Notifies listeners of a single change.
     *
     * @param change the change
     */
    public void notifyListeners(@NotNull FolderConfigItemChange<T> change) {
        for (FolderConfigChangeListener<T> listener : listeners) {
            try {
                listener.onItemChange(change);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

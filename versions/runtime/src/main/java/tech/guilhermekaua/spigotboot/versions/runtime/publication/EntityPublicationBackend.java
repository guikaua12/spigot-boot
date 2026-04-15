/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.versions.runtime.publication;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;

/**
 * Explicit publication/add-remove backend selected independently from spawn and replacement strategies.
 *
 * @since 2.0.2
 */
public interface EntityPublicationBackend {

    /**
     * Returns the publication family implemented by this backend.
     *
     * @return the publication family
     */
    @NotNull EntityPublicationFamily family();

    /**
     * Publishes a freshly spawned native entity into the destination world.
     *
     * @param support the version-local fresh publication bridge
     * @param nativeEntity the native entity to add
     * @param location the target location
     */
    void addFreshEntity(
            @NotNull EntityPublicationFreshSupport support,
            @NotNull Object nativeEntity,
            @NotNull Location location
    );

    /**
     * Publishes a replacement native entity through the selected publication family.
     *
     * @param support the version-local replacement publication bridge
     * @param entity the Bukkit entity wrapper being replaced
     * @param currentNativeHandle the original native handle
     * @param replacementHandle the replacement native handle
     */
    void publishReplacement(
            @NotNull EntityPublicationReplacementSupport support,
            @NotNull Entity entity,
            @NotNull Object currentNativeHandle,
            @NotNull Object replacementHandle
    );
}

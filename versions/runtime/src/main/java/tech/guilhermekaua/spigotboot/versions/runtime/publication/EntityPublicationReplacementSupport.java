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

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyWorldAddStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperWorldAddStrategy_1_21_plus;

/**
 * Version-local bridge used by explicit publication backends during attach and replacement publication.
 *
 * @since 2.0.2
 */
public interface EntityPublicationReplacementSupport {

    /**
     * Rebinds the Bukkit wrapper to the replacement native handle.
     *
     * @param entity the Bukkit entity wrapper
     * @param replacementHandle the replacement native handle
     */
    default void rebindBukkitEntity(@NotNull Entity entity, @NotNull Object replacementHandle) {
        if (this instanceof LegacyWorldAddStrategy_1_8_to_1_12.ReplacementSupport) {
            ((LegacyWorldAddStrategy_1_8_to_1_12.ReplacementSupport) this).rebindBukkitZombie(entity, replacementHandle);
            return;
        }
        if (this instanceof PaperWorldAddStrategy_1_21_plus.ReplacementSupport) {
            ((PaperWorldAddStrategy_1_21_plus.ReplacementSupport) this).rebindBukkitZombie(entity, replacementHandle);
            return;
        }
        throw unsupportedBridgeOperation("Bukkit entity rebinding");
    }

    /**
     * Rebinds Bukkit bridge fields from the original handle to the replacement handle.
     *
     * @param family the selected publication family
     * @param bukkitEntity the Bukkit entity wrapper
     * @param oldHandle the original native handle
     * @param replacementHandle the replacement native handle
     */
    default void rebindBukkitBridge(
            @NotNull EntityPublicationFamily family,
            @NotNull Entity bukkitEntity,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (family == EntityPublicationFamily.LEGACY_WORLD_LISTENER) {
            requireLegacyReplacementSupport().rebindLegacyBukkitBridge(bukkitEntity, oldHandle, replacementHandle);
            return;
        }
        requireModernReplacementSupport().rebindModernBukkitBridge(bukkitEntity, oldHandle, replacementHandle);
    }

    /**
     * Replaces world and publication registries from the original handle to the replacement handle.
     *
     * @param family the selected publication family
     * @param oldHandle the original native handle
     * @param replacementHandle the replacement native handle
     */
    default void replaceWorldReferences(
            @NotNull EntityPublicationFamily family,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (family == EntityPublicationFamily.LEGACY_WORLD_LISTENER) {
            requireLegacyReplacementSupport().replaceLegacyWorldReferences(oldHandle, replacementHandle);
            return;
        }
        requireModernReplacementSupport().replaceModernWorldReferences(oldHandle, replacementHandle);
    }

    /**
     * Rewrites vehicle and passenger references from the original handle to the replacement handle.
     *
     * @param family the selected publication family
     * @param oldHandle the original native handle
     * @param replacementHandle the replacement native handle
     */
    default void rewireVehicleAndPassengerReferences(
            @NotNull EntityPublicationFamily family,
            @NotNull Object oldHandle,
            @NotNull Object replacementHandle
    ) {
        if (family == EntityPublicationFamily.LEGACY_WORLD_LISTENER) {
            requireLegacyReplacementSupport().rewireLegacyVehicleAndPassengerReferences(oldHandle, replacementHandle);
            return;
        }
        requireModernReplacementSupport().rewireModernVehicleAndPassengerReferences(oldHandle, replacementHandle);
    }

    /**
     * Refreshes cached Bukkit wrappers after replacement publication.
     *
     * @param family the selected publication family
     * @param entity the Bukkit entity wrapper
     */
    default void refreshBukkitWrappers(@NotNull EntityPublicationFamily family, @NotNull Entity entity) {
        if (family == EntityPublicationFamily.LEGACY_WORLD_LISTENER) {
            requireLegacyReplacementSupport().refreshLegacyBukkitWrappers(entity);
            return;
        }
        requireModernReplacementSupport().refreshModernBukkitWrappers(entity);
    }

    /**
     * Marks the original native entity as removed after replacement publication.
     *
     * @param family the selected publication family
     * @param oldHandle the original native handle
     */
    default void markEntityRemoved(@NotNull EntityPublicationFamily family, @NotNull Object oldHandle) {
        if (family == EntityPublicationFamily.LEGACY_WORLD_LISTENER) {
            requireLegacyReplacementSupport().markLegacyEntityRemoved(oldHandle);
            return;
        }
        requireModernReplacementSupport().markModernEntityRemoved(oldHandle);
    }

    /**
     * Returns the legacy replacement bridge when available.
     *
     * @return the legacy replacement bridge
     */
    default @NotNull LegacyWorldAddStrategy_1_8_to_1_12.ReplacementSupport requireLegacyReplacementSupport() {
        if (this instanceof LegacyWorldAddStrategy_1_8_to_1_12.ReplacementSupport) {
            return (LegacyWorldAddStrategy_1_8_to_1_12.ReplacementSupport) this;
        }
        throw unsupportedBridgeOperation("legacy replacement publication");
    }

    /**
     * Returns the modern replacement bridge when available.
     *
     * @return the modern replacement bridge
     */
    default @NotNull PaperWorldAddStrategy_1_21_plus.ReplacementSupport requireModernReplacementSupport() {
        if (this instanceof PaperWorldAddStrategy_1_21_plus.ReplacementSupport) {
            return (PaperWorldAddStrategy_1_21_plus.ReplacementSupport) this;
        }
        throw unsupportedBridgeOperation("modern replacement publication");
    }

    /**
     * Creates a stable exception for unsupported publication bridge operations.
     *
     * @param operation the missing operation description
     * @return the exception to throw
     */
    default @NotNull IllegalStateException unsupportedBridgeOperation(@NotNull String operation) {
        return new IllegalStateException(
                "Publication backend operation '" + operation + "' is not supported by '"
                        + getClass().getName()
                        + "'."
        );
    }
}

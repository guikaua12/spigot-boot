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
package tech.guilhermekaua.spigotboot.versions.runtime.network.metadata;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Explicit living-entity metadata payload covering attributes, equipment, and active effects.
 *
 * @since 2.0.2
 */
public final class LivingEntityMetadata {
    private static final LivingEntityMetadata EMPTY = new LivingEntityMetadata(
            LivingAttributeSnapshot.empty(),
            EquipmentSnapshot.empty(),
            ActiveEffectsSnapshot.empty()
    );

    private final LivingAttributeSnapshot attributes;
    private final EquipmentSnapshot equipment;
    private final ActiveEffectsSnapshot activeEffects;

    /**
     * Creates a new living-entity metadata payload.
     *
     * @param attributes the living attributes
     * @param equipment the equipment snapshot
     * @param activeEffects the active effects snapshot
     */
    public LivingEntityMetadata(
            @NotNull LivingAttributeSnapshot attributes,
            @NotNull EquipmentSnapshot equipment,
            @NotNull ActiveEffectsSnapshot activeEffects
    ) {
        this.attributes = Objects.requireNonNull(attributes, "attributes cannot be null");
        this.equipment = Objects.requireNonNull(equipment, "equipment cannot be null");
        this.activeEffects = Objects.requireNonNull(activeEffects, "activeEffects cannot be null");
    }

    /**
     * Returns the shared empty living metadata payload.
     *
     * @return the empty living metadata payload
     */
    public static @NotNull LivingEntityMetadata empty() {
        return EMPTY;
    }

    /**
     * Returns the living attributes.
     *
     * @return the living attributes
     */
    public @NotNull LivingAttributeSnapshot attributes() {
        return attributes;
    }

    /**
     * Returns the equipment snapshot.
     *
     * @return the equipment snapshot
     */
    public @NotNull EquipmentSnapshot equipment() {
        return equipment;
    }

    /**
     * Returns the active effects snapshot.
     *
     * @return the active effects snapshot
     */
    public @NotNull ActiveEffectsSnapshot activeEffects() {
        return activeEffects;
    }

    /**
     * Returns whether the living metadata payload is empty.
     *
     * @return {@code true} when attributes, equipment, and effects are all empty
     */
    public boolean isEmpty() {
        return attributes.isEmpty() && equipment.isEmpty() && activeEffects.isEmpty();
    }
}

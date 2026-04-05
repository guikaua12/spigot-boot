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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Logical definition for a custom zombie backed by a real native zombie subclass.
 *
 * @since 2.0.2
 */
public final class ZombieEntityDefinition extends CustomEntityDefinition<Zombie> {

    private ZombieEntityDefinition(
            @NotNull CustomEntityId id,
            @NotNull CustomEntityBehaviorFactory<Zombie> behaviorFactory,
            @NotNull CustomEntityInitializer<Zombie> initializer
    ) {
        super(id, CustomEntityBaseType.ZOMBIE, Zombie.class, behaviorFactory, initializer);
    }

    /**
     * Creates a new builder for a zombie definition.
     *
     * @param id the logical custom entity id
     * @return the builder
     */
    public static @NotNull Builder builder(@NotNull CustomEntityId id) {
        return new Builder(id);
    }

    /**
     * Builds zombie definitions.
     */
    public static final class Builder {
        private final CustomEntityId id;
        private CustomEntityBehaviorFactory<Zombie> behaviorFactory;
        private CustomEntityInitializer<Zombie> initializer = CustomEntityInitializer.noop();

        private Builder(CustomEntityId id) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
        }

        /**
         * Sets the behavior factory used to create one behavior instance per spawn.
         *
         * @param behaviorFactory the behavior factory
         * @return the builder
         */
        public @NotNull Builder behaviorFactory(@NotNull CustomEntityBehaviorFactory<Zombie> behaviorFactory) {
            this.behaviorFactory = Objects.requireNonNull(behaviorFactory, "behaviorFactory cannot be null");
            return this;
        }

        /**
         * Sets the initializer that configures the Bukkit zombie view after spawn.
         *
         * @param initializer the initializer
         * @return the builder
         */
        public @NotNull Builder initializer(@NotNull CustomEntityInitializer<Zombie> initializer) {
            this.initializer = Objects.requireNonNull(initializer, "initializer cannot be null");
            return this;
        }

        /**
         * Creates the immutable definition.
         *
         * @return the immutable definition
         */
        public @NotNull ZombieEntityDefinition build() {
            if (behaviorFactory == null) {
                throw new IllegalStateException("behaviorFactory cannot be null");
            }
            return new ZombieEntityDefinition(id, behaviorFactory, initializer);
        }
    }
}

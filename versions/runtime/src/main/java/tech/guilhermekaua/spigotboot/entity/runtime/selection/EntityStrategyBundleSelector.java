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
package tech.guilhermekaua.spigotboot.entity.runtime.selection;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.entity.api.ControlledEntity;
import tech.guilhermekaua.spigotboot.entity.api.EntityTemplate;
import tech.guilhermekaua.spigotboot.entity.api.SpawnOptions;
import tech.guilhermekaua.spigotboot.entity.api.SpawnedEntity;
import tech.guilhermekaua.spigotboot.entity.api.spi.EntityVersionAdapter;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityFreshSpawnPath;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityVersionCapabilities;
import tech.guilhermekaua.spigotboot.entity.runtime.capability.EntityWorldRegistrationMode;
import tech.guilhermekaua.spigotboot.entity.runtime.model.EntityVersionBindings;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.EntityStrategyBundle;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.FreshSpawnStrategy;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyTrackingBindingStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.LegacyWorldAddStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperTrackingBindingStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.PaperWorldAddStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.ReplacementStrategy;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.TrackingBindingStrategy;
import tech.guilhermekaua.spigotboot.entity.runtime.strategy.WorldAddStrategy;

import java.util.Locale;
import java.util.Objects;

/**
 * Central shared runtime selector that composes spawn/replacement strategy bundles from capability and binding
 * metadata.
 *
 * <p>Transport-facing tracker-hook, publication, packet transport, and metadata families are composed separately by
 * {@link EntityNetworkRuntimeBundleSelector}.</p>
 *
 * @since 2.0.2
 */
public final class EntityStrategyBundleSelector {

    private EntityStrategyBundleSelector() {
    }

    /**
     * Selects a composed spawn/replacement strategy bundle for the supplied runtime metadata.
     *
     * @param minecraftVersion the resolved Minecraft version
     * @param capabilities the resolved runtime capabilities
     * @param bindings the resolved runtime bindings
     * @return the selected strategy bundle
     */
    public static @NotNull EntityStrategyBundle select(
            @NotNull MinecraftVersion minecraftVersion,
            @NotNull EntityVersionCapabilities capabilities,
            @NotNull EntityVersionBindings bindings
    ) {
        return select(new EntityStrategySelectionContext(minecraftVersion, capabilities, bindings));
    }

    /**
     * Selects a composed spawn/replacement strategy bundle for the supplied runtime metadata.
     *
     * @param context the runtime selection context
     * @return the selected strategy bundle
     */
    public static @NotNull EntityStrategyBundle select(@NotNull EntityStrategySelectionContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        WorldAddStrategy worldAddStrategy = selectWorldAddStrategy(context);
        TrackingBindingStrategy trackingBindingStrategy = selectTrackingBindingStrategy(context);
        FreshSpawnStrategy freshSpawnStrategy = selectFreshSpawnStrategy(context, worldAddStrategy, trackingBindingStrategy);
        ReplacementStrategy replacementStrategy = selectReplacementStrategy(context, worldAddStrategy, trackingBindingStrategy);
        return new EntityStrategyBundle(
                freshSpawnStrategy,
                replacementStrategy,
                worldAddStrategy,
                trackingBindingStrategy
        );
    }

    /**
     * Narrows a selected fresh-spawn strategy to the shared legacy implementation.
     *
     * @param freshSpawnStrategy the selected fresh-spawn strategy
     * @return the shared legacy fresh-spawn strategy
     */
    public static @NotNull LegacyFreshSpawnStrategy_1_8_to_1_12 requireLegacyFreshSpawnStrategy(
            @NotNull FreshSpawnStrategy freshSpawnStrategy
    ) {
        if (freshSpawnStrategy instanceof LegacyFreshSpawnStrategy_1_8_to_1_12) {
            return (LegacyFreshSpawnStrategy_1_8_to_1_12) freshSpawnStrategy;
        }
        throw new IllegalStateException(
                "Legacy strategy bundle expected a LegacyFreshSpawnStrategy_1_8_to_1_12 but received '"
                        + freshSpawnStrategy.getClass().getName()
                        + "'."
        );
    }

    /**
     * Narrows a selected fresh-spawn strategy to the shared paper-like implementation.
     *
     * @param freshSpawnStrategy the selected fresh-spawn strategy
     * @return the shared paper-like fresh-spawn strategy
     */
    public static @NotNull PaperFreshSpawnStrategy_1_21_plus requirePaperFreshSpawnStrategy(
            @NotNull FreshSpawnStrategy freshSpawnStrategy
    ) {
        if (freshSpawnStrategy instanceof PaperFreshSpawnStrategy_1_21_plus) {
            return (PaperFreshSpawnStrategy_1_21_plus) freshSpawnStrategy;
        }
        throw new IllegalStateException(
                "Paper strategy bundle expected a PaperFreshSpawnStrategy_1_21_plus but received '"
                        + freshSpawnStrategy.getClass().getName()
                        + "'."
        );
    }

    /**
     * Narrows a selected replacement strategy to the shared legacy implementation.
     *
     * @param replacementStrategy the selected replacement strategy
     * @return the shared legacy replacement strategy
     */
    public static @NotNull LegacyReplacementStrategy_1_8_to_1_12 requireLegacyReplacementStrategy(
            @NotNull ReplacementStrategy replacementStrategy
    ) {
        if (replacementStrategy instanceof LegacyReplacementStrategy_1_8_to_1_12) {
            return (LegacyReplacementStrategy_1_8_to_1_12) replacementStrategy;
        }
        throw new IllegalStateException(
                "Legacy strategy bundle expected a LegacyReplacementStrategy_1_8_to_1_12 but received '"
                        + replacementStrategy.getClass().getName()
                        + "'."
        );
    }

    /**
     * Narrows a selected replacement strategy to the shared paper-like implementation.
     *
     * @param replacementStrategy the selected replacement strategy
     * @return the shared paper-like replacement strategy
     */
    public static @NotNull PaperReplacementStrategy_1_21_plus requirePaperReplacementStrategy(
            @NotNull ReplacementStrategy replacementStrategy
    ) {
        if (replacementStrategy instanceof PaperReplacementStrategy_1_21_plus) {
            return (PaperReplacementStrategy_1_21_plus) replacementStrategy;
        }
        throw new IllegalStateException(
                "Paper strategy bundle expected a PaperReplacementStrategy_1_21_plus but received '"
                        + replacementStrategy.getClass().getName()
                        + "'."
        );
    }

    private static @NotNull FreshSpawnStrategy selectFreshSpawnStrategy(
            @NotNull EntityStrategySelectionContext context,
            @NotNull WorldAddStrategy worldAddStrategy,
            @NotNull TrackingBindingStrategy trackingBindingStrategy
    ) {
        EntityFreshSpawnPath freshSpawnPath = context.capabilities().freshSpawnPath();
        if (isLegacyBundle(context)) {
            return new LegacyFreshSpawnStrategy_1_8_to_1_12(
                    "legacy-constructor-first",
                    requireLegacyWorldAddStrategy(worldAddStrategy),
                    requireLegacyTrackingBindingStrategy(trackingBindingStrategy)
            );
        }
        if (isPaperLikeBundle(context)) {
            return new PaperFreshSpawnStrategy_1_21_plus(
                    "paper-constructor-first",
                    requirePaperWorldAddStrategy(worldAddStrategy),
                    requirePaperTrackingBindingStrategy(trackingBindingStrategy)
            );
        }
        if (freshSpawnPath == EntityFreshSpawnPath.CONSTRUCTOR_FIRST) {
            return new AdapterDelegatingFreshSpawnStrategy("constructor-first");
        }
        if (freshSpawnPath == EntityFreshSpawnPath.BUKKIT_SPAWN_THEN_ATTACH) {
            return new AdapterDelegatingFreshSpawnStrategy("bukkit-spawn-attach");
        }
        return new AdapterDelegatingFreshSpawnStrategy("unspecified");
    }

    private static @NotNull ReplacementStrategy selectReplacementStrategy(
            @NotNull EntityStrategySelectionContext context,
            @NotNull WorldAddStrategy worldAddStrategy,
            @NotNull TrackingBindingStrategy trackingBindingStrategy
    ) {
        EntityWorldRegistrationMode replacementMode = context.bindings().replacement().worldRegistrationMode();
        if (isLegacyBundle(context)) {
            return new LegacyReplacementStrategy_1_8_to_1_12(
                    "legacy-reference-rewrite",
                    requireLegacyWorldAddStrategy(worldAddStrategy),
                    requireLegacyTrackingBindingStrategy(trackingBindingStrategy)
            );
        }
        if (isPaperLikeBundle(context)) {
            return new PaperReplacementStrategy_1_21_plus(
                    "paper-reference-rewrite",
                    requirePaperWorldAddStrategy(worldAddStrategy),
                    requirePaperTrackingBindingStrategy(trackingBindingStrategy)
            );
        }
        if (replacementMode == EntityWorldRegistrationMode.REFERENCE_REWRITE
                && context.bindings().replacement().trackerStateHandleAvailable()) {
            return new AdapterDelegatingReplacementStrategy("reference-rewrite-with-state");
        }
        if (replacementMode == EntityWorldRegistrationMode.REFERENCE_REWRITE) {
            return new AdapterDelegatingReplacementStrategy("reference-rewrite");
        }
        return new AdapterDelegatingReplacementStrategy("unspecified");
    }

    private static @NotNull WorldAddStrategy selectWorldAddStrategy(@NotNull EntityStrategySelectionContext context) {
        EntityWorldRegistrationMode freshSpawnMode = context.bindings().freshSpawn().worldRegistrationMode();
        EntityWorldRegistrationMode replacementMode = context.bindings().replacement().worldRegistrationMode();
        if (isLegacyBundle(context)) {
            return new LegacyWorldAddStrategy_1_8_to_1_12("legacy-constructor-add-and-rewrite");
        }
        if (isPaperLikeBundle(context)) {
            return new PaperWorldAddStrategy_1_21_plus("paper-chunk-preload-and-rewrite");
        }
        if (freshSpawnMode == EntityWorldRegistrationMode.UNSPECIFIED
                && replacementMode == EntityWorldRegistrationMode.UNSPECIFIED) {
            return new DescriptiveWorldAddStrategy("unspecified", freshSpawnMode, replacementMode);
        }
        return new DescriptiveWorldAddStrategy(
                normalize(freshSpawnMode) + "-and-" + normalize(replacementMode),
                freshSpawnMode,
                replacementMode
        );
    }

    private static @NotNull TrackingBindingStrategy selectTrackingBindingStrategy(
            @NotNull EntityStrategySelectionContext context
    ) {
        boolean freshSpawnTrackerEntry = context.bindings().freshSpawn().trackerEntryHandleAvailable();
        boolean freshSpawnTrackerState = context.bindings().freshSpawn().trackerStateHandleAvailable();
        boolean replacementTrackerEntry = context.bindings().replacement().trackerEntryHandleAvailable();
        boolean replacementTrackerState = context.bindings().replacement().trackerStateHandleAvailable();

        if (isLegacyBundle(context)) {
            return new LegacyTrackingBindingStrategy_1_8_to_1_12(
                    "legacy-entry-only",
                    freshSpawnTrackerEntry,
                    freshSpawnTrackerState,
                    replacementTrackerEntry,
                    replacementTrackerState
            );
        }
        if (isPaperLikeBundle(context)) {
            return new PaperTrackingBindingStrategy_1_21_plus(
                    "paper-entry-and-state",
                    freshSpawnTrackerEntry,
                    freshSpawnTrackerState,
                    replacementTrackerEntry,
                    replacementTrackerState
            );
        }
        if (!freshSpawnTrackerEntry && !freshSpawnTrackerState && !replacementTrackerEntry && !replacementTrackerState) {
            return new DescriptiveTrackingBindingStrategy(
                    "unspecified",
                    freshSpawnTrackerEntry,
                    freshSpawnTrackerState,
                    replacementTrackerEntry,
                    replacementTrackerState
            );
        }
        if (freshSpawnTrackerState || replacementTrackerState) {
            return new DescriptiveTrackingBindingStrategy(
                    "entry-and-state",
                    freshSpawnTrackerEntry,
                    freshSpawnTrackerState,
                    replacementTrackerEntry,
                    replacementTrackerState
            );
        }
        return new DescriptiveTrackingBindingStrategy(
                "entry-only",
                freshSpawnTrackerEntry,
                freshSpawnTrackerState,
                replacementTrackerEntry,
                replacementTrackerState
        );
    }

    private static boolean isLegacyBundle(@NotNull EntityStrategySelectionContext context) {
        return context.capabilities().freshSpawnPath() == EntityFreshSpawnPath.CONSTRUCTOR_FIRST
                && context.bindings().freshSpawn().hasConstructorBindings()
                && context.bindings().replacement().worldRegistrationMode() == EntityWorldRegistrationMode.REFERENCE_REWRITE
                && !context.capabilities().trackerStateHandleAvailable()
                && !context.bindings().freshSpawn().trackerStateHandleAvailable()
                && !context.bindings().replacement().trackerStateHandleAvailable();
    }

    private static boolean isPaperLikeBundle(@NotNull EntityStrategySelectionContext context) {
        return context.capabilities().freshSpawnPath() == EntityFreshSpawnPath.CONSTRUCTOR_FIRST
                && context.bindings().freshSpawn().hasConstructorBindings()
                && context.bindings().freshSpawn().worldRegistrationMode() == EntityWorldRegistrationMode.CHUNK_PRELOAD_AND_ADD
                && context.bindings().replacement().worldRegistrationMode() == EntityWorldRegistrationMode.REFERENCE_REWRITE
                && context.capabilities().trackerStateHandleAvailable()
                && context.bindings().freshSpawn().trackerStateHandleAvailable()
                && context.bindings().replacement().trackerStateHandleAvailable();
    }

    private static @NotNull LegacyWorldAddStrategy_1_8_to_1_12 requireLegacyWorldAddStrategy(
            @NotNull WorldAddStrategy worldAddStrategy
    ) {
        if (worldAddStrategy instanceof LegacyWorldAddStrategy_1_8_to_1_12) {
            return (LegacyWorldAddStrategy_1_8_to_1_12) worldAddStrategy;
        }
        throw new IllegalStateException(
                "Legacy strategy bundle expected a LegacyWorldAddStrategy_1_8_to_1_12 but received '"
                        + worldAddStrategy.getClass().getName()
                        + "'."
        );
    }

    private static @NotNull PaperWorldAddStrategy_1_21_plus requirePaperWorldAddStrategy(
            @NotNull WorldAddStrategy worldAddStrategy
    ) {
        if (worldAddStrategy instanceof PaperWorldAddStrategy_1_21_plus) {
            return (PaperWorldAddStrategy_1_21_plus) worldAddStrategy;
        }
        throw new IllegalStateException(
                "Paper strategy bundle expected a PaperWorldAddStrategy_1_21_plus but received '"
                        + worldAddStrategy.getClass().getName()
                        + "'."
        );
    }

    private static @NotNull LegacyTrackingBindingStrategy_1_8_to_1_12 requireLegacyTrackingBindingStrategy(
            @NotNull TrackingBindingStrategy trackingBindingStrategy
    ) {
        if (trackingBindingStrategy instanceof LegacyTrackingBindingStrategy_1_8_to_1_12) {
            return (LegacyTrackingBindingStrategy_1_8_to_1_12) trackingBindingStrategy;
        }
        throw new IllegalStateException(
                "Legacy strategy bundle expected a LegacyTrackingBindingStrategy_1_8_to_1_12 but received '"
                        + trackingBindingStrategy.getClass().getName()
                        + "'."
        );
    }

    private static @NotNull PaperTrackingBindingStrategy_1_21_plus requirePaperTrackingBindingStrategy(
            @NotNull TrackingBindingStrategy trackingBindingStrategy
    ) {
        if (trackingBindingStrategy instanceof PaperTrackingBindingStrategy_1_21_plus) {
            return (PaperTrackingBindingStrategy_1_21_plus) trackingBindingStrategy;
        }
        throw new IllegalStateException(
                "Paper strategy bundle expected a PaperTrackingBindingStrategy_1_21_plus but received '"
                        + trackingBindingStrategy.getClass().getName()
                        + "'."
        );
    }

    private static @NotNull String normalize(@NotNull Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static final class AdapterDelegatingFreshSpawnStrategy implements FreshSpawnStrategy {
        private final String id;

        private AdapterDelegatingFreshSpawnStrategy(@NotNull String id) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public <T extends Entity> @NotNull SpawnedEntity<T> spawn(
                @NotNull EntityVersionAdapter adapter,
                @NotNull EntityTemplate<T> template,
                @NotNull SpawnOptions spawnOptions,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            return adapter.spawn(template, spawnOptions, lifecycle);
        }
    }

    private static final class AdapterDelegatingReplacementStrategy implements ReplacementStrategy {
        private final String id;

        private AdapterDelegatingReplacementStrategy(@NotNull String id) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public <T extends Entity> @NotNull ControlledEntity<T> attach(
                @NotNull EntityVersionAdapter adapter,
                @NotNull T entity,
                @NotNull NativeEntityLifecycle<T> lifecycle
        ) {
            return adapter.attach(entity, lifecycle);
        }
    }

    private static final class DescriptiveWorldAddStrategy implements WorldAddStrategy {
        private final String id;
        private final EntityWorldRegistrationMode freshSpawnWorldRegistrationMode;
        private final EntityWorldRegistrationMode replacementWorldRegistrationMode;

        private DescriptiveWorldAddStrategy(
                @NotNull String id,
                @NotNull EntityWorldRegistrationMode freshSpawnWorldRegistrationMode,
                @NotNull EntityWorldRegistrationMode replacementWorldRegistrationMode
        ) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.freshSpawnWorldRegistrationMode = Objects.requireNonNull(
                    freshSpawnWorldRegistrationMode,
                    "freshSpawnWorldRegistrationMode cannot be null"
            );
            this.replacementWorldRegistrationMode = Objects.requireNonNull(
                    replacementWorldRegistrationMode,
                    "replacementWorldRegistrationMode cannot be null"
            );
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public @NotNull EntityWorldRegistrationMode freshSpawnWorldRegistrationMode() {
            return freshSpawnWorldRegistrationMode;
        }

        @Override
        public @NotNull EntityWorldRegistrationMode replacementWorldRegistrationMode() {
            return replacementWorldRegistrationMode;
        }
    }

    private static final class DescriptiveTrackingBindingStrategy implements TrackingBindingStrategy {
        private final String id;
        private final boolean freshSpawnTrackerEntryHandleAvailable;
        private final boolean freshSpawnTrackerStateHandleAvailable;
        private final boolean replacementTrackerEntryHandleAvailable;
        private final boolean replacementTrackerStateHandleAvailable;

        private DescriptiveTrackingBindingStrategy(
                @NotNull String id,
                boolean freshSpawnTrackerEntryHandleAvailable,
                boolean freshSpawnTrackerStateHandleAvailable,
                boolean replacementTrackerEntryHandleAvailable,
                boolean replacementTrackerStateHandleAvailable
        ) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.freshSpawnTrackerEntryHandleAvailable = freshSpawnTrackerEntryHandleAvailable;
            this.freshSpawnTrackerStateHandleAvailable = freshSpawnTrackerStateHandleAvailable;
            this.replacementTrackerEntryHandleAvailable = replacementTrackerEntryHandleAvailable;
            this.replacementTrackerStateHandleAvailable = replacementTrackerStateHandleAvailable;
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public boolean freshSpawnTrackerEntryHandleAvailable() {
            return freshSpawnTrackerEntryHandleAvailable;
        }

        @Override
        public boolean freshSpawnTrackerStateHandleAvailable() {
            return freshSpawnTrackerStateHandleAvailable;
        }

        @Override
        public boolean replacementTrackerEntryHandleAvailable() {
            return replacementTrackerEntryHandleAvailable;
        }

        @Override
        public boolean replacementTrackerStateHandleAvailable() {
            return replacementTrackerStateHandleAvailable;
        }
    }
}

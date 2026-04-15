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
package tech.guilhermekaua.spigotboot.versions.runtime.support;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.versions.api.MinecraftVersion;
import tech.guilhermekaua.spigotboot.versions.api.spi.VersionAdapter;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.VersionRuntimeProfile;
import tech.guilhermekaua.spigotboot.versions.runtime.bootstrap.RuntimeServerFlavor;
import tech.guilhermekaua.spigotboot.versions.runtime.model.EntityVersionLegacyTransportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionNetworkMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionTransportProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.network.metadata.EntityNetworkMetadataContract;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.LegacyTransportSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.network.transport.ModernTransportSupport;
import tech.guilhermekaua.spigotboot.versions.runtime.capability.VersionCapabilities;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionBindings;
import tech.guilhermekaua.spigotboot.versions.runtime.model.VersionMetadataProvider;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityMetadataFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundle;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityNetworkRuntimeBundleSelector;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityPublicationFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTrackerHookFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.selection.EntityTransportFamily;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyFreshSpawnStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.LegacyReplacementStrategy_1_8_to_1_12;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperFreshSpawnStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.strategy.PaperReplacementStrategy_1_21_plus;
import tech.guilhermekaua.spigotboot.versions.runtime.tracker.legacy.LegacyTrackerHookSupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Explicit source of truth for runtime support claims and release gating.
 *
 * @since 2.0.2
 */
public final class RuntimeSupportMatrix {
    private static final ReleaseCriteria RELEASE_CRITERIA = new ReleaseCriteria(
            Arrays.asList(
                    "SpigotVersionBootstrapTest",
                    "VersionedPlatformTest",
                    "RuntimeSupportMatrixTest"
            ),
            true
    );
    private static final List<SupportDeclaration> DECLARATIONS = Collections.unmodifiableList(
            Arrays.asList(
                    SupportDeclaration.anyFlavor(
                            "legacy-1.8.8-1.12.2",
                            MinecraftVersion.of(1, 8, 8),
                            MinecraftVersion.of(1, 12, 2),
                            StrategySupportKind.LEGACY,
                            EntityTrackerHookFamily.LEGACY_ENTRY_HOOK,
                            EntityPublicationFamily.LEGACY_WORLD_LISTENER,
                            EntityTransportFamily.LEGACY_1_8_TO_1_13_2,
                            EntityMetadataFamily.LEGACY_DATA_WATCHER,
                            RELEASE_CRITERIA
                    ),
                    SupportDeclaration.anyFlavor(
                            "transitional-1.13.0-1.13.2",
                            MinecraftVersion.of(1, 13, 0),
                            MinecraftVersion.of(1, 13, 2),
                            StrategySupportKind.LEGACY,
                            EntityTrackerHookFamily.LEGACY_ENTRY_HOOK,
                            EntityPublicationFamily.LEGACY_WORLD_LISTENER,
                            EntityTransportFamily.LEGACY_1_8_TO_1_13_2,
                            EntityMetadataFamily.TRANSITIONAL_DATA_WATCHER_1_13,
                            RELEASE_CRITERIA
                    ),
                    SupportDeclaration.anyFlavor(
                            "modern-1.14.0-1.16.5",
                            MinecraftVersion.of(1, 14, 0),
                            MinecraftVersion.of(1, 16, 5),
                            StrategySupportKind.MODERN,
                            EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE,
                            EntityPublicationFamily.ENTITIES_BY_UUID,
                            EntityTransportFamily.MODERN_1_14_TO_1_16_5,
                            EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_14_TO_1_16_5,
                            RELEASE_CRITERIA
                    ),
                    SupportDeclaration.anyFlavor(
                            "modern-1.17.0-1.18.2",
                            MinecraftVersion.of(1, 17, 0),
                            MinecraftVersion.of(1, 18, 2),
                            StrategySupportKind.MODERN,
                            EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE,
                            EntityPublicationFamily.SECTION_MANAGER,
                            EntityTransportFamily.MODERN_1_17_TO_1_18_2,
                            EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6,
                            RELEASE_CRITERIA
                    ),
                    SupportDeclaration.forFlavor(
                            "spigot-1.19.2-1.20.6",
                            RuntimeServerFlavor.SPIGOT,
                            MinecraftVersion.of(1, 19, 2),
                            MinecraftVersion.of(1, 20, 6),
                            StrategySupportKind.MODERN,
                            EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE,
                            EntityPublicationFamily.SECTION_MANAGER,
                            EntityTransportFamily.MODERN_1_19_2_TO_1_20_6,
                            EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6,
                            RELEASE_CRITERIA
                    ),
                    SupportDeclaration.forFlavor(
                            "paper-chunk-system-1.19.2-1.20.6",
                            RuntimeServerFlavor.PAPER,
                            MinecraftVersion.of(1, 19, 2),
                            MinecraftVersion.of(1, 20, 6),
                            StrategySupportKind.MODERN,
                            EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE,
                            EntityPublicationFamily.PAPER_CHUNK_SYSTEM,
                            EntityTransportFamily.MODERN_1_19_2_TO_1_20_6,
                            EntityMetadataFamily.MODERN_SYNCHED_ENTITY_DATA_1_17_TO_1_20_6,
                            RELEASE_CRITERIA
                    ),
                    SupportDeclaration.forFlavor(
                            "spigot-1.21.x",
                            RuntimeServerFlavor.SPIGOT,
                            MinecraftVersion.of(1, 21, 0),
                            MinecraftVersion.of(1, 21, 99),
                            StrategySupportKind.MODERN,
                            EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE,
                            EntityPublicationFamily.SECTION_MANAGER,
                            EntityTransportFamily.LATEST_1_21_X,
                            EntityMetadataFamily.LATEST_SYNCHED_ENTITY_DATA_1_21_X,
                            RELEASE_CRITERIA
                    ),
                    SupportDeclaration.forFlavor(
                            "paper-chunk-system-1.21.x",
                            RuntimeServerFlavor.PAPER,
                            MinecraftVersion.of(1, 21, 0),
                            MinecraftVersion.of(1, 21, 99),
                            StrategySupportKind.MODERN,
                            EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE,
                            EntityPublicationFamily.PAPER_CHUNK_SYSTEM,
                            EntityTransportFamily.LATEST_1_21_X,
                            EntityMetadataFamily.LATEST_SYNCHED_ENTITY_DATA_1_21_X,
                            RELEASE_CRITERIA
                    ),
                    SupportDeclaration.forFlavor(
                            "paper-moonrise-1.21.x",
                            RuntimeServerFlavor.PAPER,
                            MinecraftVersion.of(1, 21, 0),
                            MinecraftVersion.of(1, 21, 99),
                            StrategySupportKind.MODERN,
                            EntityTrackerHookFamily.MODERN_ENTRY_AND_STATE,
                            EntityPublicationFamily.PAPER_MOONRISE_CHUNK_SYSTEM,
                            EntityTransportFamily.LATEST_1_21_X,
                            EntityMetadataFamily.LATEST_SYNCHED_ENTITY_DATA_1_21_X,
                            RELEASE_CRITERIA
                    )
            )
    );

    private RuntimeSupportMatrix() {
    }

    /**
     * Returns the explicit release criteria required before any profile can be claimed as supported.
     *
     * @return the release criteria
     */
    public static @NotNull ReleaseCriteria releaseCriteria() {
        return RELEASE_CRITERIA;
    }

    /**
     * Returns every declared support profile.
     *
     * @return the declared support profiles
     */
    public static @NotNull List<SupportDeclaration> declarations() {
        return DECLARATIONS;
    }

    /**
     * Resolves and validates the explicit support declaration for one runtime profile.
     *
     * @param runtimeProfile the resolved runtime profile
     * @param adapter the selected version adapter
     * @return the resolved support declaration
     */
    public static @NotNull SupportDeclaration requireSupported(
            @NotNull VersionRuntimeProfile runtimeProfile,
            @NotNull VersionAdapter adapter
    ) {
        Objects.requireNonNull(runtimeProfile, "runtimeProfile cannot be null");
        Objects.requireNonNull(adapter, "adapter cannot be null");

        VersionCapabilities capabilities = resolveCapabilities(adapter);
        VersionBindings bindings = resolveBindings(adapter);
        EntityNetworkMetadataContract metadataContract = resolveMetadataContract(adapter);
        EntityNetworkRuntimeBundle networkRuntime = EntityNetworkRuntimeBundleSelector.select(
                runtimeProfile,
                capabilities,
                bindings
        );
        return requireSupported(runtimeProfile, adapter, networkRuntime, metadataContract);
    }

    /**
     * Resolves and validates the explicit support declaration for one runtime profile using an already selected
     * network runtime bundle and metadata contract.
     *
     * @param runtimeProfile the resolved runtime profile
     * @param adapter the selected version adapter
     * @param networkRuntime the selected network runtime bundle
     * @param metadataContract the resolved metadata contract
     * @return the resolved support declaration
     */
    public static @NotNull SupportDeclaration requireSupported(
            @NotNull VersionRuntimeProfile runtimeProfile,
            @NotNull VersionAdapter adapter,
            @NotNull EntityNetworkRuntimeBundle networkRuntime,
            @NotNull EntityNetworkMetadataContract metadataContract
    ) {
        Objects.requireNonNull(runtimeProfile, "runtimeProfile cannot be null");
        Objects.requireNonNull(adapter, "adapter cannot be null");
        Objects.requireNonNull(networkRuntime, "networkRuntime cannot be null");
        Objects.requireNonNull(metadataContract, "metadataContract cannot be null");

        List<String> issues = new ArrayList<String>();
        SupportDeclaration declaration = null;
        IllegalStateException declarationFailure = null;

        collectBundleIssues(networkRuntime, metadataContract, issues);
        try {
            declaration = requireDeclaredSupport(runtimeProfile, networkRuntime);
        } catch (IllegalStateException exception) {
            declarationFailure = exception;
        }

        if (declaration != null) {
            validateStrategySupport(declaration, adapter, issues);
            validateTransportSupport(runtimeProfile, networkRuntime.transportFamily(), adapter, issues);
            validateMetadataSupport(adapter, metadataContract, issues);
        }

        if (!issues.isEmpty()) {
            throw partiallyWired(runtimeProfile, declaration, networkRuntime, issues);
        }
        if (declarationFailure != null) {
            throw declarationFailure;
        }
        return declaration;
    }

    private static @NotNull VersionCapabilities resolveCapabilities(@NotNull VersionAdapter adapter) {
        if (adapter instanceof VersionMetadataProvider) {
            return ((VersionMetadataProvider) adapter).entityCapabilities();
        }
        return VersionCapabilities.unspecified();
    }

    private static @NotNull VersionBindings resolveBindings(@NotNull VersionAdapter adapter) {
        if (adapter instanceof VersionMetadataProvider) {
            return ((VersionMetadataProvider) adapter).entityBindings();
        }
        return VersionBindings.unspecified();
    }

    private static @NotNull EntityNetworkMetadataContract resolveMetadataContract(@NotNull VersionAdapter adapter) {
        if (adapter instanceof VersionNetworkMetadataProvider) {
            return ((VersionNetworkMetadataProvider) adapter).entityNetworkMetadataContract();
        }
        return EntityNetworkMetadataContract.unspecified();
    }

    private static @NotNull SupportDeclaration requireDeclaredSupport(
            @NotNull VersionRuntimeProfile runtimeProfile,
            @NotNull EntityNetworkRuntimeBundle networkRuntime
    ) {
        List<SupportDeclaration> matches = new ArrayList<SupportDeclaration>();
        for (SupportDeclaration declaration : DECLARATIONS) {
            if (declaration.matches(runtimeProfile, networkRuntime)) {
                matches.add(declaration);
            }
        }

        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    "No runtime support declaration claims profile "
                            + runtimeProfile
                            + " with network bundle "
                            + describeNetworkRuntime(networkRuntime)
                            + "."
            );
        }
        if (matches.size() > 1) {
            List<String> identifiers = new ArrayList<String>();
            for (SupportDeclaration declaration : matches) {
                identifiers.add(declaration.id());
            }
            Collections.sort(identifiers);
            throw new IllegalStateException(
                    "Ambiguous runtime support declarations for profile "
                            + runtimeProfile
                            + " with network bundle "
                            + describeNetworkRuntime(networkRuntime)
                            + ": "
                            + identifiers
                            + '.'
            );
        }
        return matches.get(0);
    }

    private static void collectBundleIssues(
            @NotNull EntityNetworkRuntimeBundle networkRuntime,
            @NotNull EntityNetworkMetadataContract metadataContract,
            @NotNull List<String> issues
    ) {
        if (networkRuntime.trackerHookFamily() == EntityTrackerHookFamily.UNSPECIFIED) {
            issues.add("tracker hook backend is unspecified");
        }
        if (networkRuntime.publicationFamily() == EntityPublicationFamily.UNSPECIFIED) {
            issues.add("publication backend is unspecified");
        }
        if (networkRuntime.transportFamily() == EntityTransportFamily.UNSPECIFIED) {
            issues.add("transport backend is unspecified");
        }
        if (networkRuntime.metadataFamily() == EntityMetadataFamily.UNSPECIFIED) {
            issues.add("metadata backend family is unspecified");
        }
        if (!metadataContract.isSpecified()) {
            issues.add("metadata backend contract is unspecified");
        }
    }

    private static void validateStrategySupport(
            @NotNull SupportDeclaration declaration,
            @NotNull VersionAdapter adapter,
            @NotNull List<String> issues
    ) {
        if (declaration.strategySupportKind() == StrategySupportKind.LEGACY) {
            validateLegacyStrategySupport(adapter, issues);
            return;
        }
        validateModernStrategySupport(adapter, issues);
    }

    private static void validateLegacyStrategySupport(
            @NotNull VersionAdapter adapter,
            @NotNull List<String> issues
    ) {
        if (!(adapter instanceof LegacyFreshSpawnStrategy_1_8_to_1_12.Provider)) {
            issues.add(
                    "legacy fresh-spawn runtime bridge is missing (required for publication and tracker hook backends)"
            );
        } else {
            LegacyFreshSpawnStrategy_1_8_to_1_12.Support support =
                    ((LegacyFreshSpawnStrategy_1_8_to_1_12.Provider) adapter).legacyFreshSpawnSupport();
            if (support == null) {
                issues.add("legacy fresh-spawn runtime bridge resolved null");
            } else {
                validateLegacyTrackerHookSupport("fresh-spawn", support.legacyTrackerHookSupport(), issues);
            }
        }

        if (!(adapter instanceof LegacyReplacementStrategy_1_8_to_1_12.Provider)) {
            issues.add(
                    "legacy replacement runtime bridge is missing (required for publication and tracker hook backends)"
            );
        } else {
            LegacyReplacementStrategy_1_8_to_1_12.Support support =
                    ((LegacyReplacementStrategy_1_8_to_1_12.Provider) adapter).legacyReplacementSupport();
            if (support == null) {
                issues.add("legacy replacement runtime bridge resolved null");
            } else {
                validateLegacyTrackerHookSupport("replacement", support.legacyTrackerHookSupport(), issues);
            }
        }
    }

    private static void validateLegacyTrackerHookSupport(
            @NotNull String phase,
            LegacyTrackerHookSupport support,
            @NotNull List<String> issues
    ) {
        if (support == null) {
            issues.add("legacy " + phase + " tracker hook bridge is missing");
        }
    }

    private static void validateModernStrategySupport(
            @NotNull VersionAdapter adapter,
            @NotNull List<String> issues
    ) {
        if (!(adapter instanceof PaperFreshSpawnStrategy_1_21_plus.Provider)) {
            issues.add(
                    "modern fresh-spawn runtime bridge is missing (required for publication and tracker hook backends)"
            );
        } else if (((PaperFreshSpawnStrategy_1_21_plus.Provider) adapter).paperFreshSpawnSupport() == null) {
            issues.add("modern fresh-spawn runtime bridge resolved null");
        }

        if (!(adapter instanceof PaperReplacementStrategy_1_21_plus.Provider)) {
            issues.add(
                    "modern replacement runtime bridge is missing (required for publication and tracker hook backends)"
            );
        } else if (((PaperReplacementStrategy_1_21_plus.Provider) adapter).paperReplacementSupport() == null) {
            issues.add("modern replacement runtime bridge resolved null");
        }
    }

    private static void validateTransportSupport(
            @NotNull VersionRuntimeProfile runtimeProfile,
            @NotNull EntityTransportFamily transportFamily,
            @NotNull VersionAdapter adapter,
            @NotNull List<String> issues
    ) {
        if (transportFamily == EntityTransportFamily.LEGACY_1_8_TO_1_13_2) {
            if (!(adapter instanceof EntityVersionLegacyTransportProvider)) {
                issues.add("legacy transport backend bridge is missing");
                return;
            }

            LegacyTransportSupport support = ((EntityVersionLegacyTransportProvider) adapter).legacyTransportSupport();
            if (support == null) {
                issues.add("legacy transport backend bridge resolved null");
            }
            return;
        }

        if (!(adapter instanceof VersionTransportProvider)) {
            issues.add("modern transport backend bridge is missing");
            return;
        }

        ModernTransportSupport support = ((VersionTransportProvider) adapter).entityTransportSupport(runtimeProfile);
        if (support == null) {
            issues.add("modern transport backend bridge resolved null");
            return;
        }
        if (support.family() != transportFamily) {
            issues.add(
                    "modern transport backend bridge '"
                            + support.id()
                            + "' exposes family '"
                            + support.family().id()
                            + "' instead of '"
                            + transportFamily.id()
                            + "'"
            );
        }
    }

    private static void validateMetadataSupport(
            @NotNull VersionAdapter adapter,
            @NotNull EntityNetworkMetadataContract metadataContract,
            @NotNull List<String> issues
    ) {
        if (!(adapter instanceof VersionNetworkMetadataProvider)) {
            issues.add("metadata backend contract provider is missing");
        }
        if (!metadataContract.isSpecified()) {
            issues.add("metadata backend contract is unspecified");
        }
    }

    private static @NotNull IllegalStateException partiallyWired(
            @NotNull VersionRuntimeProfile runtimeProfile,
            SupportDeclaration declaration,
            @NotNull EntityNetworkRuntimeBundle networkRuntime,
            @NotNull List<String> issues
    ) {
        StringBuilder message = new StringBuilder();
        message.append("Runtime profile ").append(runtimeProfile);
        if (declaration != null) {
            message.append(" matches support declaration '").append(declaration.id()).append('\'');
        }
        message.append(" but is only partially wired for network bundle ")
                .append(describeNetworkRuntime(networkRuntime))
                .append(": ")
                .append(issues)
                .append('.');
        return new IllegalStateException(message.toString());
    }

    private static @NotNull String describeNetworkRuntime(@NotNull EntityNetworkRuntimeBundle networkRuntime) {
        return "[trackerHook="
                + networkRuntime.trackerHookFamily().id()
                + ", publication="
                + networkRuntime.publicationFamily().id()
                + ", transport="
                + networkRuntime.transportFamily().id()
                + ", metadata="
                + networkRuntime.metadataFamily().id()
                + ']';
    }

    private enum StrategySupportKind {
        LEGACY,
        MODERN
    }

    private enum FlavorConstraint {
        ANY,
        SPIGOT,
        PAPER;

        private boolean matches(@NotNull RuntimeServerFlavor serverFlavor) {
            switch (this) {
                case SPIGOT:
                    return serverFlavor == RuntimeServerFlavor.SPIGOT;
                case PAPER:
                    return serverFlavor == RuntimeServerFlavor.PAPER;
                case ANY:
                default:
                    return true;
            }
        }
    }

    /**
     * Explicit release gate that every support declaration inherits.
     *
     * @since 2.0.2
     */
    public static final class ReleaseCriteria {
        private final List<String> requiredUnitSuites;
        private final boolean matrixEvidenceRequired;

        private ReleaseCriteria(@NotNull List<String> requiredUnitSuites, boolean matrixEvidenceRequired) {
            this.requiredUnitSuites = Collections.unmodifiableList(new ArrayList<String>(Objects.requireNonNull(
                    requiredUnitSuites,
                    "requiredUnitSuites cannot be null"
            )));
            this.matrixEvidenceRequired = matrixEvidenceRequired;
        }

        /**
         * Returns the runtime test suites that must pass before support can be claimed.
         *
         * @return the required runtime test suites
         */
        public @NotNull List<String> requiredUnitSuites() {
            return requiredUnitSuites;
        }

        /**
         * Returns whether representative matrix evidence is also required.
         *
         * @return {@code true} when matrix evidence is required
         */
        public boolean matrixEvidenceRequired() {
            return matrixEvidenceRequired;
        }
    }

    /**
     * One explicit support claim in the runtime support matrix.
     *
     * @since 2.0.2
     */
    public static final class SupportDeclaration {
        private final String id;
        private final FlavorConstraint flavorConstraint;
        private final MinecraftVersion minimumVersion;
        private final MinecraftVersion maximumVersion;
        private final StrategySupportKind strategySupportKind;
        private final EntityTrackerHookFamily trackerHookFamily;
        private final EntityPublicationFamily publicationFamily;
        private final EntityTransportFamily transportFamily;
        private final EntityMetadataFamily metadataFamily;
        private final ReleaseCriteria releaseCriteria;

        private SupportDeclaration(
                @NotNull String id,
                @NotNull FlavorConstraint flavorConstraint,
                @NotNull MinecraftVersion minimumVersion,
                @NotNull MinecraftVersion maximumVersion,
                @NotNull StrategySupportKind strategySupportKind,
                @NotNull EntityTrackerHookFamily trackerHookFamily,
                @NotNull EntityPublicationFamily publicationFamily,
                @NotNull EntityTransportFamily transportFamily,
                @NotNull EntityMetadataFamily metadataFamily,
                @NotNull ReleaseCriteria releaseCriteria
        ) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.flavorConstraint = Objects.requireNonNull(flavorConstraint, "flavorConstraint cannot be null");
            this.minimumVersion = Objects.requireNonNull(minimumVersion, "minimumVersion cannot be null");
            this.maximumVersion = Objects.requireNonNull(maximumVersion, "maximumVersion cannot be null");
            this.strategySupportKind = Objects.requireNonNull(
                    strategySupportKind,
                    "strategySupportKind cannot be null"
            );
            this.trackerHookFamily = Objects.requireNonNull(trackerHookFamily, "trackerHookFamily cannot be null");
            this.publicationFamily = Objects.requireNonNull(publicationFamily, "publicationFamily cannot be null");
            this.transportFamily = Objects.requireNonNull(transportFamily, "transportFamily cannot be null");
            this.metadataFamily = Objects.requireNonNull(metadataFamily, "metadataFamily cannot be null");
            this.releaseCriteria = Objects.requireNonNull(releaseCriteria, "releaseCriteria cannot be null");
        }

        private static @NotNull SupportDeclaration anyFlavor(
                @NotNull String id,
                @NotNull MinecraftVersion minimumVersion,
                @NotNull MinecraftVersion maximumVersion,
                @NotNull StrategySupportKind strategySupportKind,
                @NotNull EntityTrackerHookFamily trackerHookFamily,
                @NotNull EntityPublicationFamily publicationFamily,
                @NotNull EntityTransportFamily transportFamily,
                @NotNull EntityMetadataFamily metadataFamily,
                @NotNull ReleaseCriteria releaseCriteria
        ) {
            return new SupportDeclaration(
                    id,
                    FlavorConstraint.ANY,
                    minimumVersion,
                    maximumVersion,
                    strategySupportKind,
                    trackerHookFamily,
                    publicationFamily,
                    transportFamily,
                    metadataFamily,
                    releaseCriteria
            );
        }

        private static @NotNull SupportDeclaration forFlavor(
                @NotNull String id,
                @NotNull RuntimeServerFlavor serverFlavor,
                @NotNull MinecraftVersion minimumVersion,
                @NotNull MinecraftVersion maximumVersion,
                @NotNull StrategySupportKind strategySupportKind,
                @NotNull EntityTrackerHookFamily trackerHookFamily,
                @NotNull EntityPublicationFamily publicationFamily,
                @NotNull EntityTransportFamily transportFamily,
                @NotNull EntityMetadataFamily metadataFamily,
                @NotNull ReleaseCriteria releaseCriteria
        ) {
            return new SupportDeclaration(
                    id,
                    serverFlavor == RuntimeServerFlavor.PAPER ? FlavorConstraint.PAPER : FlavorConstraint.SPIGOT,
                    minimumVersion,
                    maximumVersion,
                    strategySupportKind,
                    trackerHookFamily,
                    publicationFamily,
                    transportFamily,
                    metadataFamily,
                    releaseCriteria
            );
        }

        /**
         * Returns the stable support declaration id.
         *
         * @return the declaration id
         */
        public @NotNull String id() {
            return id;
        }

        /**
         * Returns the lowest claimed Minecraft version for this declaration.
         *
         * @return the minimum claimed Minecraft version
         */
        public @NotNull MinecraftVersion minimumVersion() {
            return minimumVersion;
        }

        /**
         * Returns the highest claimed Minecraft version for this declaration.
         *
         * @return the maximum claimed Minecraft version
         */
        public @NotNull MinecraftVersion maximumVersion() {
            return maximumVersion;
        }

        /**
         * Returns the tracker-hook family that must be assigned for this claim.
         *
         * @return the required tracker-hook family
         */
        public @NotNull EntityTrackerHookFamily trackerHookFamily() {
            return trackerHookFamily;
        }

        /**
         * Returns the publication family that must be assigned for this claim.
         *
         * @return the required publication family
         */
        public @NotNull EntityPublicationFamily publicationFamily() {
            return publicationFamily;
        }

        /**
         * Returns the transport family that must be assigned for this claim.
         *
         * @return the required transport family
         */
        public @NotNull EntityTransportFamily transportFamily() {
            return transportFamily;
        }

        /**
         * Returns the metadata family that must be assigned for this claim.
         *
         * @return the required metadata family
         */
        public @NotNull EntityMetadataFamily metadataFamily() {
            return metadataFamily;
        }

        /**
         * Returns the unit suites that gate this support claim.
         *
         * @return the required unit suites
         */
        public @NotNull List<String> requiredUnitSuites() {
            return releaseCriteria.requiredUnitSuites();
        }

        /**
         * Returns whether matrix evidence is required for this support claim.
         *
         * @return {@code true} when matrix evidence is required
         */
        public boolean matrixEvidenceRequired() {
            return releaseCriteria.matrixEvidenceRequired();
        }

        private @NotNull StrategySupportKind strategySupportKind() {
            return strategySupportKind;
        }

        private boolean matches(
                @NotNull VersionRuntimeProfile runtimeProfile,
                @NotNull EntityNetworkRuntimeBundle networkRuntime
        ) {
            return runtimeProfile.minecraftVersion().compareTo(minimumVersion) >= 0
                    && runtimeProfile.minecraftVersion().compareTo(maximumVersion) <= 0
                    && flavorConstraint.matches(runtimeProfile.serverFlavor())
                    && trackerHookFamily == networkRuntime.trackerHookFamily()
                    && publicationFamily == networkRuntime.publicationFamily()
                    && transportFamily == networkRuntime.transportFamily()
                    && metadataFamily == networkRuntime.metadataFamily();
        }
    }
}

## Initialization

## 2026-04-13 task 1
- Kept runtime-profile awareness entirely inside `versions/runtime` by adding `EntityRuntimeProfile`, `RuntimeServerFlavor`, detector/probe utilities, and a bootstrap-local `RuntimeProfileSelectionSupport` contract instead of widening `versions/api`.
- Chose a stable ambiguity failure as an `IllegalStateException` with a deterministic message built from the runtime profile plus sorted adapter class names, which gives overlap tests a fixed assertion surface without introducing a new public exception type.
- Preserved the existing `SpigotEntityBootstrap -> EntityAdapterDiscovery -> EntityAdapterRegistry/ServiceLoader` discovery flow and added profile-aware boot/select overloads around it rather than changing discovery or `VersionedEntityPlatform` construction.
- Used tiny test-only marker classes plus a selective classloader to validate reflective flavor/probe detection without adding Paper dependencies to main runtime code.

## 2026-04-13 task 2
- Modeled subsystem selection as four separate enums plus four focused selectors (`tracker-hook`, `publication`, `transport`, `metadata`) instead of extending `EntityStrategyBundleSelector`, so later backend tasks can bind each axis independently without one giant combined family enum.
- Added a new `VersionedEntityPlatform(EntityRuntimeProfile, EntityVersionAdapter)` constructor and made `SpigotEntityBootstrap` use it, while the older `(MinecraftVersion, adapter)` constructor now delegates through a flavor-neutral Spigot profile for compatibility with existing tests and call sites.
- Kept `EntityStrategyBundleSelector` intact for spawn/replacement behavior and only narrowed its documentation, which preserves current lifecycle behavior while exposing the new `networkRuntime()` composition root for upcoming transport tasks.

## 2026-04-13 task 3
- Introduced one semantic runtime-only transport SPI (`EntityTransport`) plus an internal request/pipeline pair, instead of putting packet operations on `EntityNetworkController` or leaking raw packet concepts into `versions/api`.
- Made `VersionedEntityPlatform` resolve and inject the transport backend into both `RuntimeNativeEntityLifecycle` and `RuntimeAttachedEntityLifecycle`, so later family-specific backends can plug in behind the existing runtime constructors without changing plugin-facing APIs.
- Fixed the ordered runtime contract to `spawn -> initial metadata -> living initialization -> velocity -> passenger/vehicle -> head rotation` for late-viewer snapshots and `passenger/vehicle -> movement -> rotation -> velocity -> dirty metadata -> head rotation` for tick deltas, because those are the stable semantic phases later packet families need to preserve.
- Resolved every currently selected transport family to a semantic no-op backend for now, which keeps this task scoped to the contract/pipeline and leaves concrete packet family logic for later backend tasks.

## 2026-04-13 task 11
- Used the existing shared strategy split as the skeleton seam instead of inventing new family-specific spawn/replacement implementations: `1.13.2` exposes the legacy provider bridges, while `1.16.5`, `1.17.1`, and `1.19.2` expose the current paper-like provider bridges until later tasks add family-specific native behavior.
- Kept the new family factories intentionally shallow by publishing only capability/binding metadata plus stubbed native operations, so task 11 lands the version matrix and ServiceLoader registration without accidentally implementing packet, metadata, or tracker backends ahead of schedule.
- Strengthened runtime coverage with explicit single-family assertions at matrix boundaries (`1.12.2`, `1.13.0`, `1.14.0`, `1.18.2`, `1.20.6`, `1.21.0`) so range gaps/overlaps fail in tests before later backend work starts depending on the module skeletons.

## 2026-04-13 paper 1.19.2 fresh-spawn fix
- Implemented the `1.19.2` family fresh-spawn path by reusing the existing shared `PaperFreshSpawnStrategy_1_21_plus` seam instead of inventing a matrix-only branch: `EntityFactoryV1_19_2` now resolves metadata, probes the live native type from a temporary Bukkit spawn, reflectively constructs the raw native entity, and binds through the shared publication/tracking flow.
- Kept the family change scoped by leaving replacement/attach stubs alone and only making the fresh-spawn bridge non-throwing; the only shared-runtime change was in `AbstractEntityPublicationBackend`, where the live Paper `1.19.2` server proved that publication must also support the two-argument Bukkit `SpawnReason.CUSTOM` overload.
- Added module-local tests for the new `1.19.2` fresh-spawn helpers (constructor selection, spawn-location application, tracked-handle fallback, and broader metadata-registry support) plus a runtime publication regression test for the `addFreshEntity(..., SpawnReason)` path.

## 2026-04-13 task 4
- Introduced a new runtime SPI, `EntityVersionNetworkMetadataProvider`, instead of overloading the existing `EntityVersionMetadataProvider`, so selection metadata (`capabilities` / `bindings`) and watcher synchronization metadata stay on separate adapter contracts.
- Chose immutable runtime payload objects (`WatcherPayload`, `WatcherDelta`, `LivingEntityMetadata`, `HeadRotation`, `PassengerVehicleState`) with empty/absent sentinels, allowing the transport layer to request init and delta payload assembly now without implementing any family-specific encoders yet.
- Kept the new runtime contract independent from `EntityNetworkRuntimeBundle.metadataFamily()`: the bundle still answers *which* metadata family is selected, while `networkMetadataContract()` answers *what* watcher/living payload contract an adapter publishes.

## 2026-04-13 task 5
- Kept viewer lifecycle ownership inside `AbstractRuntimeControlledEntity` and the existing T03 transport pipeline instead of pushing more state back into `EntityNetworkController`, so fresh-spawn and attach/replacement continue sharing one runtime path.
- Fixed bind and network-controller rebind to refresh live network state before `onBind`, because bind-time viewer lifecycle callbacks need current position/velocity regardless of whether the entity was freshly spawned or attached.

## 2026-04-13 task 8
- Added a dedicated runtime `publication` package with explicit backend classes (`legacy-world-listener`, `entities-by-uuid`, `section-manager`, `paper-chunk-system`, `paper-moonrise-chunk-system`) and resolved the chosen backend from `EntityNetworkRuntimeBundle`, while leaving `EntityStrategyBundle` intact as the spawn/replacement metadata selector.
- Injected the resolved `EntityPublicationBackend` into `RuntimeNativeEntityLifecycle` and `RuntimeAttachedEntityLifecycle`, then made the shared legacy/paper fresh-spawn and replacement strategies delegate publication through that lifecycle-owned backend instead of calling `LegacyWorldAddStrategy` / `PaperWorldAddStrategy` directly.
- Used one generic replacement publication bridge (`EntityPublicationReplacementSupport`) with default adapters back to the existing legacy/paper support contracts, so the family-specific bridge changes stay minimal in `1.8.8`, `1.13.2`, `1.16.5`, `1.17.1`, `1.19.2`, and `1.21.11` while still allowing `1.21.11` to override `SECTION_MANAGER` versus `PAPER_MOONRISE_CHUNK_SYSTEM` world-reference rewriting explicitly.

## 2026-04-13 task 8 deterministic publication fix
- Fixed the ordered-method regression at the shared reflection seam instead of in individual publication backends: `findCompatibleMethod(...)` now resolves names in explicit candidate order, which keeps `PaperChunkSystemPublicationBackend` and the other modern backends deterministic without duplicating name-priority logic at each call site.

## 2026-04-13 task 7
- Kept the existing `PaperTrackingBindingStrategy_1_21_plus` type as the shared modern 1.14+ tracker seam instead of renaming the class mid-plan, but upgraded it from passive handle binding into an active hook creator that returns a runtime-owned `ModernTrackerHook` and preserves tracker-state bridge data.
- Added tracker-owned network tick dispatch as an opt-in flag on `AbstractRuntimeControlledEntity`: normal entity ticks still drive transport by default, and only version supports that explicitly report a live tracker-hook installation switch network transport ownership over to tracker-state ticks.
- Exposed a dedicated `PaperTrackingBindingStrategy_1_21_plus.Provider` / `Support` bridge on the 1.16.5, 1.17.1, 1.19.2, and 1.21.11 adapters so the whole modern family points at one tracker-hook model while keeping version-local installation details isolated.

## 2026-04-13 task 6
- Implemented the legacy tracker family as a shared runtime backend (`LegacyTrackerHookBackend`) plus a tiny version-local support bridge, instead of embedding legacy viewer/tick logic directly in `SpigotEntityAdapterV1_8_8` / `SpigotEntityAdapterV1_13_2`; this keeps the pre-1.14 entry-hook semantics centralized while still allowing version-local overlays.
- Chose a backend-owned visibility rule model (`LegacyTrackerViewabilitySnapshot`) rather than version adapters returning a final yes/no decision, so the backend remains the place where legacy respawn-blindness/self-view/passenger/range/chunk/player-visibility rules are composed.
- Treated `1.13.2` as the same legacy family with a distinct overlay id instead of a different callback model, which keeps the runtime boundary explicit for later native overlays without forcing modern tracker-state concepts into the transitional family.

## 2026-04-13 task 10
- Added a new internal runtime SPI (`EntityVersionTransportProvider` + `ModernTransportSupport`) rather than widening `versions/api`, so version modules can publish family-local packet bridges and probe-driven overlays without leaking raw packets or NMS handles to plugin code.
- Kept the modern family transport classes shallow (`1.14-1.16.5`, `1.17-1.18.2`, `1.19.2-1.20.6`, `1.21.x`) and pushed fork-specific divergence into adapter-selected support instances, which lets `1.19.2+` and `1.21.x` switch to a Paper overlay only when the runtime profile reports chunk-system features.
- Reused the task-4 metadata contract ids in the adapters (`modern-synched-entity-data-*`, `latest-synched-entity-data-*`) so transport and metadata remain aligned by family instead of inventing a second parallel id space.

## 2026-04-13 task 9
- Added a dedicated runtime SPI for the legacy packet family (`EntityVersionLegacyTransportProvider` + `LegacyTransportSupport`) instead of overloading the existing modern transport provider, so the pre-1.14 packet differences stay explicit without disturbing the already-landed modern transport work.
- Implemented the runtime side as `AbstractLegacyEntityTransport` + `LegacyEntityTransportV1_8_to_1_13_2`, mirroring the modern transport structure so metadata snapshots/deltas, head rotation fallback, and viewer-vs-broadcast dispatch all stay owned by shared runtime code rather than drifting into version modules.
- Kept version-local changes intentionally minimal by making `LegacyTransportSupportV1_8_8` and `LegacyTransportSupportV1_13_2` thin wrappers over one reflective bridge configured by three mode choices: relative-move encoding, equipment slot encoding, and passenger/vehicle packet strategy.

## 2026-04-13 task 12
- Kept task 12 scoped to tests by strengthening only the family adapter suites that were still underspecified (`1.16.5`, `1.17.1`, `1.19.2`, `1.21.11`) instead of rewriting the already-good runtime contract suites or legacy family adapter tests.
- Chose module-local assertions that pin the shared modern tracker bridge to the same adapter-published support object used by fresh-spawn and replacement paths, so the tests prove hook capability exposure without reaching into native implementation details.

## 2026-04-13 task 14 live autorun fix
- Kept the runner backward-compatible by passing both the newer `entity.matrix.*` / `ENTITY_MATRIX_*` names and the older `spigotboot.entityMatrix.*` / `SPIGOTBOOT_ENTITY_MATRIX_*` names, then taught `test-plugin` to accept both and to honor explicit output directory/file overrides before falling back to its default `target/entity-matrix/<server>/<scenario>/` layout.
- Added a dedicated `EntityMatrixAutorunService` instead of overloading commands or listeners, because startup autorun is a one-shot server lifecycle concern and should not depend on a human player joining or on manual console input.
- Kept manual `/entitydemo` usage intact and added only headless scenario variants for matrix autorun, so player-driven demos still use the existing command/service surface while automated runs can complete without a real viewer joining the server.

## 2026-04-13 task 14 boundary packaging follow-up
- Chose the honest packaging fix first: `test-plugin` now depends on every adapter module required by the representative boundary set (`1.8.8`, `1.13.2`, `1.16.5`, `1.17.1`, `1.19.2`, `1.21.11`) so ServiceLoader discovery inside the shaded plugin matches the plan’s support claims instead of relying on endpoint jars only.
- Kept the `1.17.1` attach workaround scoped to the matrix-only headless scenario instead of rewriting runtime selection or the public manual command path, because the live blocker after packaging was an unfinished family-local replacement stub rather than a discovery problem.

## 2026-04-13 task 15 blocker fix
- Moved fail-fast support-matrix enforcement off the universal `VersionedEntityPlatform` constructor path and onto `SpigotEntityBootstrap.boot(...)`, so only real runtime-profile support claims are gated while direct fake platform construction keeps working for lower-level tests.
- Kept `RuntimeSupportMatrix` itself explicit and reusable by adding an overload that resolves capabilities, bindings, network bundle, and metadata contract from the selected adapter/profile, which lets bootstrap enforce claims without duplicating runtime-selection logic.
- Added shared `RuntimeSupportFixtures` test helpers for fully wired claimed-profile adapters, so bootstrap/support-matrix tests can prove the task-15 gate while `VersionedEntityPlatformTest` can keep exercising direct fake adapters without being recast as support claims.

## 2026-04-13 task 15 javadoc package fix
- Kept the package follow-up strictly doc-only by correcting the overload Javadocs in `RuntimeSupportMatrix` instead of touching any support-selection or exception behavior, since Maven confirmed the blocker was just the invalid `@param` tags.

## 2026-04-13 task 13
- Kept the plan contract centralized in `test-plugin` with one descriptor map that owns all five scenario ids and their exact assertion keys, then had both the command/service layer and the new `EntityScenarioRegistrationTest` consume that same source of truth so task 14 can rely on stable ids.
- Chose lightweight in-module JSON writing instead of adding a serializer dependency to `test-plugin`, because task 13 only needs deterministic machine-readable `trace.json` / `assertions.json` payloads under `target/entity-matrix/<server>/<scenario>/` and the existing module had no JSON library configured.
## 2026-04-13 task 1.17.1 attach replacement fix
- Kept this fix scoped to the `1.17.1` family by implementing the real attach/replacement bridge inside `EntityFactoryV1_17_1` and removing the `test-plugin` matrix fallback, instead of widening shared runtime publication helpers for obfuscated 1.17 callback/collection names.
- Used same-class `Unsafe` allocation plus reflective field-copy/rebind steps for the `attach-existing-zombie` path rather than porting the full latest generated-hook stack into `1.17.1`; that was the smallest real bridge needed to make the live runtime replacement succeed honestly for the blocked matrix scenario.

## 2026-04-14 task 1.19.2 generated replacement bridge follow-up
- Kept the new attach fix scoped to `versions/1.19.2` by adding a family-local `EntityHookBinderV1_19_2` plus generated replacement metadata inside `EntityFactoryV1_19_2`, while deliberately leaving the already-working fresh-spawn/publication flow untouched.
- Added the direct `org.javassist:javassist` dependency only to `versions/1.19.2/pom.xml` instead of changing the runtime module's optional dependency model for every family, because this task is the first one in the current boundary set that eagerly instantiates the shared generated-subclass factory during its own module tests.

## 2026-04-14T01:18:06.9689887-03:00 task F3 manual qa rerun
- Ground final QA on fresh runner-produced artifacts in 	arget/entity-matrix/... rather than cached server-home logs under .tools/entity-matrix/..., and approve only when the representative traces show scenario-specific success events with no fallback markers alongside passing assertion payloads.

## 2026-04-14T05:00:21.3733069-03:00 task F3 manual qa contract-correction sanity
- Treat the 1.19.2 cow-contract correction as a transparency improvement, not release proof: explicit unsupported-type rejection is acceptable for the non-representative extra scenario, but approval still requires directly inspectable retained artifacts for the representative release matrix set.

## 2026-04-14T05:13:50.4257278-03:00 task F3 final representative recheck
- Require the representative artifact set to be directly inspectable under the promised 	arget/entity-matrix/<server>/<scenario>/ paths before approving release QA; prompt-level claims about rebuilt evidence are not enough when the retained files do not match the claimed layout.

## 2026-04-14T05:26:49.2949842-03:00 task F3 final approval recheck
- Approve release QA once the full representative matrix is retained on disk with direct pass:true assertion artifacts and no degraded-behavior markers in the corresponding representative traces; treat non-representative extras such as spigot-1.19.2/deathfx-cow as out-of-scope for release approval when they are explicitly unsupported and not part of the claimed matrix.

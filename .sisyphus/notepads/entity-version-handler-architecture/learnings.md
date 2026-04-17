## 2026-04-12T23:34:23.210Z Task: startup-analysis
Current orchestration already lives mostly in VersionedEntityPlatform plus runtime lifecycle classes; bootstrap/discovery/registry are thin and should stay unchanged in Wave 1.
The cleanest first extraction seam is capability/binding models feeding a future strategy bundle, not bootstrap redesign.
1.21.11 fresh spawn is already clustered in EntityFactoryV1_21_11 around spawnFreshEntity/resolveSpawnMetadata/createFreshNativeEntity/instantiateNativeEntity/addFreshNativeEntity/resolveTrackedEntityState.
1.8.8 still uses spawn-then-replace, so any shared API must return only the final live entity and avoid exposing pre-swap identity assumptions.
VersionedEntityPlatformTest and SpigotEntityBootstrapTest are the best first regression points for strategy/capability seam validation.

## 2026-04-12T20:58:09-03:00 Task: T01
Capability metadata landed cleanly as a narrow seam: platform exposes adapter metadata via EntityVersionMetadataProvider, while exact-version adapters delegate static capability/binding bundles from their factories.
The small model set was sufficient: fresh-spawn path, world-registration modes, constructor shapes, and tracker-entry/state availability.
Focused runtime tests passed without changing bootstrap/discovery behavior.

## 2026-04-12T23:59:00.000Z Task: T01-capability-binding-seam
An optional adapter-side metadata provider lets VersionedEntityPlatform expose runtime capabilities/bindings without changing bootstrap/discovery/registry selection behavior.
Static capability/binding descriptors on exact-version factories avoid instantiating version factories just to inspect runtime metadata, which keeps the seam lazy and safe for wrong-version class loading.
The smallest useful Wave 1 model covered four stable facts: fresh-spawn path, constructor priority, tracker-state availability, and world/chunk registration mode split between fresh spawn and replacement.

## 2026-04-12T21:14:12-03:00 Task: T02
The cleanest scaffolding is a central EntityStrategyBundleSelector with descriptive/delegating placeholder strategies; this creates a future extraction seam without moving real version logic out of factories yet.
VersionedEntityPlatform can safely cache capabilities, bindings, and the selected bundle at construction time because adapter metadata is now immutable and optional.
Keeping exact-version adapters untouched was the right choice: they already behave as thin metadata + lazy factory entrypoints.

## 2026-04-13T00:10:00.000Z Task: T02-strategy-selection-scaffolding
The T01 metadata seam was already expressive enough to classify the currently supported adapters into a legacy 1.8.8 bundle and a paper-like 1.21.11 bundle without touching bootstrap, discovery, or registry flow.
Routing `VersionedEntityPlatform` spawn and attach through runtime-selected placeholder strategies preserved behavior while making the composition path real instead of leaving selection as dead metadata.
World-add and tracking-binding can stay descriptive for now; the important Wave 1 win is that one immutable bundle now captures the chosen family for fresh spawn, replacement, registration, and tracking in shared runtime code.

## 2026-04-12T21:21:58-03:00 Task: T03
The best seam tests use metadata-only or lightweight delegating adapters plus existing runtime lifecycle test doubles, not heavier fake NMS layers.
Strategy verification is strongest when covered from three angles at once: bundle composition in VersionedEntityPlatform, bootstrap-selected bundle family in SpigotEntityBootstrapTest, and lifecycle class passthrough in RuntimeNativeEntityLifecycleTest.

## 2026-04-13T10:17:10-03:00 Task: T15
The full repository verification matrix now passes cleanly with `mvnw.cmd clean test`; the earlier `commands` module failure was transient/environmental and not a stable blocker.
The remaining gap for T15 is hands-on QA only: no runnable local 1.8.8 or 1.21.11 server environment is present in this workspace.

## 2026-04-13T00:20:00.000Z Task: T03-runtime-test-seams-and-fixtures
The safest regression seam stayed metadata-first: tiny test adapters that only expose capability/binding descriptors plus lightweight spawn/attach delegation are enough to verify strategy-family selection without any real NMS execution.
`VersionedEntityPlatformTest` is the best place to prove bundle stability and delegation together, while `SpigotEntityBootstrapTest` anchors adapter-selection-to-bundle-selection, and `RuntimeNativeEntityLifecycleTest` confirms the delegated seams still receive the expected runtime lifecycle objects.
Legacy-like, paper-like, and unspecified bundles can all be asserted strongly through strategy ids plus lifecycle/delegation behavior; the tests do not need to fall back to weak string-only smoke coverage.

## 2026-04-13T00:33:30.000Z Task: T04-thin-exact-version-entrypoints
Keeping lazy metadata on static factory descriptors while introducing a tiny shared `EntityVersionEntrypoint` contract lets the exact-version adapters stay wrong-version-safe and still name the version-owned spawn/attach seam explicitly.
Splitting each factory's public `spawn` and `attach` methods into dedicated fresh-spawn and replacement entrypoint delegates made the classes read as orchestration boundaries over shared runtime selection, without moving any 1.8.8 swap internals or 1.21.11 constructor-first helpers out yet.

## 2026-04-13T00:43:20.000Z Task: T04-non-clean-verification-fix
The failing non-clean verification was build-state-sensitive rather than a missing runtime dependency: `spigot-boot-entity-v1_21_11` already had the runtime module on its compile and test classpaths, but the runtime module was still leaving shade-generated `dependency-reduced-pom.xml` metadata in the module root after package runs.
Disabling dependency-reduced POM generation in `versions/runtime/pom.xml` and removing the stray generated file keeps package builds from mutating source-tree metadata, and the required non-clean reactor test command stayed green even after an explicit runtime package build.

## 2026-04-13T01:10:00.000Z Task: T05-paper-fresh-spawn-extraction
The clean extraction seam for the 1.21.11 constructor-first path is a shared runtime strategy plus a tiny exact-version support bridge, not a wider adapter contract or a replacement-path migration.
Keeping the 1.21.11 factory as the owner of prepared spawn metadata, native construction, lifecycle binding, Bukkit wrapper resolution, world add, and tracker-handle resolution preserves exact behavior while still moving the orchestration order into shared runtime code.
An adapter-side lazy support proxy is important for wrong-version safety and lightweight tests: exposing the shared paper bridge should not instantiate the 1.21.11 factory or pull in Javassist until the fresh-spawn path is actually executed.
Runtime seam tests can verify the fresh-spawn order strongly by mixing support-bridge event markers with `RuntimeNativeEntityLifecycle` bind/onSpawn side effects, which catches both strategy ordering and lifecycle timing regressions.

## 2026-04-12T22:39:00-03:00 Task: T06-legacy-fresh-spawn-extraction
The reusable part for legacy 1.8.8 was the same constructor-first orchestration shape already proven for paper: prepare metadata, create native entity, bind lifecycle to native, bind the Bukkit wrapper, add to world, write tracking, then let `onSpawn()` fire at the existing runtime boundary.
The legacy-specific work stayed version-local inside `EntityFactoryV1_8_8`: probe-spawn metadata discovery, generated-class constructor resolution, spawn-location application, direct world add, and tracker-entry lookup all differ enough from paper that only the orchestration should be shared.
Legacy runtime seam tests are strongest when they stop asserting plain `adapter.spawn(...)` delegation and instead verify the shared strategy order plus entry-only tracking semantics through a lazy adapter-side support bridge.

## 2026-04-13T02:12:00.000Z Task: T07-wire-spawn-strategy-selection-end-to-end
The remaining 1.8.8 fresh-spawn fallback seam could stay behind the shared legacy strategy without widening replacement/world-add abstractions: catching only pre-registration failures inside `LegacyFreshSpawnStrategy_1_8_to_1_12` and delegating recovery back to the support bridge preserved the narrow safe boundary from T06.
Once the runtime-selected strategy owned that fallback hook, both exact-version factories could flatten their stale fresh-spawn wrapper entry classes down to direct shared-strategy calls while leaving replacement/reference-rewrite internals untouched.

## 2026-04-12T23:59:59.000Z Task: T08B-document-sample-plugin-qa
The most durable maintainer QA location is `wiki/wiki/CustomEntities_AILossInvestigation.md`, because the two manual flows map directly to the failure modes already documented there: `/entitydemo orbit` is the quick registration/controller sanity check, while `/entitydemo deathfx cow` is the reactive-AI guard against falling back to swap-style fresh spawn.
Documenting the same two commands separately for `1.8.8` and `1.21.11` matters even when the player input is identical, because the expected failure logs differ by era: legacy mostly surfaces duplicate add or tracker-entry symptoms, while modern Paper-era regressions also show section-storage or tracker-state errors.

## 2026-04-12T23:22:00-03:00 Task: T08A-add-fresh-spawn-regression-coverage
The strongest regression anchor for T07 stayed test-side: runtime seam tests can prove the shared constructor-first route by asserting the full support-bridge-to-lifecycle ordering, while version adapter tests pin the exact constructor priority metadata and stable shared support bridge exposure without loading native classes.
A useful anti-regression assertion for both legacy and paper bundles is that the selected shared strategy runs with `adapter.spawn(...)` left unused; that catches accidental reintroduction of the old parallel fresh-spawn branches while still letting the legacy preparation fallback stay explicit and narrow behind the shared strategy.

## 2026-04-13T02:52:00.000Z Task: T09-replacement-orchestration-extraction
The replacement seam could mirror fresh spawn cleanly: shared strategy code now owns native-handle resolution, lifecycle-aware short-circuiting, instance-field copy, lifecycle binding, Bukkit bind, tracker-handle writes, and repair scheduling, while each version bridge still owns the actual reference rewrites.
The legacy spawn fallback was safe to re-point at the new shared legacy replacement strategy, which removed the last factory-local replacement shell without merging fresh spawn and replacement into one contract.

## 2026-04-13T03:22:00.000Z Task: T10-world-add-strategy-extraction
The narrow T10 seam was to make world-add executable by era without consuming tracker or repair concerns: fresh spawn now delegates chunk-preload/add sequencing to shared world-add strategies, while replacement delegates the publication order there and still resolves tracker handles afterward through the version support bridge.
Keeping the low-level rewrite primitives in the exact-version factories made the extraction testable and wrong-version-safe: runtime strategies own the era ordering, but factories still own the reflective field/method details that T11 and T12 will peel further later.

## 2026-04-13T00:41:30-03:00 Task: T11-tracking-strategy-extraction
The clean T11 seam was exactly the post-publication tracking snapshot/bind step: legacy only needs a tracker-entry handle rebound into runtime network state, while paper-like runtimes need the tracked-entry plus server-entity state pair rebound together.
Making tracking executable by era worked best when fresh-spawn and replacement kept owning lifecycle/world-add/repair order, and only delegated the handle capture plus `networkState` writeback to dedicated tracking strategies.
Replacement regression coverage is stronger when it asserts `publish -> resolve-bukkit -> tracking -> repair`, because that protects the T12 boundary against accidentally absorbing repair scheduling into tracking extraction.

## 2026-04-13T00:57:00-03:00 Task: T12-final-orchestration-consolidation
The last duplicated orchestration shell was not low-level NMS mechanics but the exact-version factory habit of rebuilding era strategies inline for `spawn(...)` and `attach(...)`; caching one selector-composed runtime bundle per factory was enough to remove that duplication without widening adapters again.
Legacy fallback still needs one version-local step to create the vanilla Bukkit entity, but once that exists the replacement path can reuse the already-selected shared replacement strategy and keep the observed `bind-bukkit -> initializer -> spawn` timing unchanged.

## 2026-04-13T01:05:00-03:00 Task: T13-replacement-world-tracking-regressions
The strongest T13 regressions stayed in the existing lightweight seams: `VersionedEntityPlatformTest` can prove that fresh spawn never touches replacement/attach while still asserting the full `add-world -> tracking -> initializer -> spawn` order, and `SpigotEntityBootstrapTest` can prove strategy ids remain metadata-selected rather than version-hardcoded.
For the modern helper layer, idempotence is the practical duplicate-registration guard: repeated `migrateModernSectionMembership(...)` and `replaceManagedCollectionEntry(...)` calls must leave exactly one replacement entry behind, which catches extraction drift without requiring a heavier fake world harness.

## 2026-04-13T12:00:00.000Z Task: T14-architecture-documentation-and-migration-notes
The most durable place for the T14 maintainer map was the existing `wiki/wiki/CustomEntities_AILossInvestigation.md` page, because it already explains why constructor-first fresh spawn matters and already hosts the sample-plugin QA flows that guard the same boundary.
After T12, the real architecture seam is now easy to state: `VersionedEntityPlatform` resolves immutable adapter metadata, `EntityStrategyBundleSelector` composes the shared era bundle, exact-version adapters stay the SPI boundary, and only repair plus native helper details remain version-local.

## 2026-04-13T11:58:00-03:00 Task: remove-ai-slops
The safe AI-slop cleanup surface in this branch was narrow: small legacy wrapper/delegation methods in `versions/api` and tiny anonymous callback/customizer sites in `versions/runtime` could be reduced to method references or lambdas without changing behavior.
Fresh version-bridge factories, adapter wiring, and large runtime strategy classes were better left untouched because their apparent verbosity is intentional compatibility scaffolding and not a safe slop-removal target.

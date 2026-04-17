## 2026-04-12T23:34:23.210Z Task: startup-analysis
Adopt a narrow first slice: introduce capability and binding models before any shared spawn/replacement strategy extraction.
Keep SpigotEntityBootstrap, EntityAdapterDiscovery, EntityAdapterRegistry, and exact-version adapter selection behavior unchanged in Wave 1.
Model only the entity-specific capability flags needed for constructor choice, tracker state, and world/chunk registration differences; do not copy BKCommonLib's broad capability registry.
Treat replacement as a separate concern from fresh spawn from the start.

## 2026-04-12T20:58:09-03:00 Task: T01
Keep metadata exposure optional via EntityVersionMetadataProvider so plain adapters still fall back to unspecified metadata and bootstrap contracts remain unchanged.
Use immutable value objects for strategy-facing metadata rather than introspecting exact-version factories later.

## 2026-04-12T23:59:00.000Z Task: T01-capability-binding-seam
Expose the new seam through VersionedEntityPlatform.capabilities() and VersionedEntityPlatform.bindings(), backed by an optional EntityVersionMetadataProvider contract instead of widening EntityVersionAdapter itself.
Keep capability objects immutable and small, and keep binding objects descriptive rather than executable so Wave 1 stays model-only and future strategy extraction can consume the seam later.
Represent 1.8.8 as Bukkit-spawn-then-attach with tracker-entry-only bindings, and 1.21.11 as constructor-first with ordered constructor shapes, tracker-state support, and chunk-preload-and-add fresh registration.

## 2026-04-12T21:14:12-03:00 Task: T02
Select strategy bundles only from runtime metadata (capabilities + bindings + resolved MinecraftVersion), not from exact-version class names.
Use adapter-delegating placeholder implementations for fresh spawn and replacement in Wave 1 so T02 changes architecture boundaries without altering semantics.
Expose world-add and tracking families as descriptive strategies now, even before they become executable implementations, so later tasks can swap internals without widening the public seam.

## 2026-04-13T00:10:00.000Z Task: T02-strategy-selection-scaffolding
Resolve capabilities and bindings once inside VersionedEntityPlatform and immediately compose an immutable runtime `EntityStrategyBundle` from shared selector code, so strategy-family choice is centralized and stable for the lifetime of the resolved platform.
Keep fresh-spawn and replacement strategies as adapter-delegating placeholders for this task, while world-add and tracking-binding stay metadata-driven descriptors until later extraction tasks move the actual version logic out of the factories.

## 2026-04-12T21:21:58-03:00 Task: T03
Keep T03 test-only: strengthen regression coverage around the new seam instead of widening production runtime APIs just to make tests easier.
Use targeted runtime test triples (`VersionedEntityPlatformTest`, `SpigotEntityBootstrapTest`, `RuntimeNativeEntityLifecycleTest`) as the standing safety net for later extraction tasks.

## 2026-04-13T00:20:00.000Z Task: T03-runtime-test-seams-and-fixtures
Keep T03 entirely test-side by introducing only nested test adapters/fixtures inside the existing runtime test classes, instead of widening production runtime APIs just to make strategy selection observable.
Verify the T02 seam through two assertions at once wherever practical: the selected bundle ids must match the metadata family, and the selected fresh-spawn/replacement strategies must still delegate into adapter-provided runtime lifecycles.

## 2026-04-13T00:33:30.000Z Task: T04-thin-exact-version-entrypoints
Introduce one minimal shared runtime contract, `EntityVersionEntrypoint`, to describe the version-owned spawn/attach boundary, but keep adapter metadata methods backed by static factory descriptors so shared runtime code still never instantiates the wrong factory just to inspect capabilities or bindings.
Keep the actual 1.8.8 replacement path and 1.21.11 constructor-first fresh-spawn path inside their exact-version factories for now; T04 only thins the public entrypoints and names the seam more clearly for later T05/T06 extraction.

## 2026-04-13T00:43:20.000Z Task: T04-non-clean-verification-fix
Treat shade-generated dependency-reduced POMs as build artifacts, not source-tree state: keep `versions/runtime` from writing them into the module root so later non-clean reactor runs are not influenced by stale package metadata.
Keep the Java seam unchanged for this fix; the smallest safe correction was build-configuration hardening in the runtime module plus removing the already-generated reduced POM artifact.

## 2026-04-13T01:10:00.000Z Task: T05-paper-fresh-spawn-extraction
Introduce `PaperFreshSpawnStrategy_1_21_plus` as the first real executable fresh-spawn strategy in shared runtime code, with nested `Provider`/`Support` bridge types and opaque prepared-spawn/tracking tokens so runtime stays version-agnostic.
Have only the paper-like selector bundle route through the new shared strategy; keep legacy/1.8.8 on the existing adapter-delegating path and leave replacement/reference-rewrite execution inside `EntityFactoryV1_21_11` for later tasks.
Keep `EntityFactoryV1_21_11` as the version-local collaborator that implements the support bridge over its existing helper cluster, and make `SpigotEntityAdapterV1_21_11` expose that bridge lazily so metadata lookup remains lightweight and wrong-version-safe.

## 2026-04-12T22:39:00-03:00 Task: T06-legacy-fresh-spawn-extraction
Introduce a dedicated shared runtime strategy, `LegacyFreshSpawnStrategy_1_8_to_1_12`, rather than reusing the paper class, so legacy keeps its own entry-only tracking contract and support bridge while still sharing the constructor-first orchestration shape.
Represent 1.8.8 fresh spawn as constructor-first metadata with ordered constructor hints and a legacy-specific world-add descriptor, while keeping replacement/reference-rewrite helpers local to `EntityFactoryV1_8_8` and out of the new happy path.
Keep fallback explicit and narrow: only pre-registration preparation/native-construction failures are allowed to fall back to the old spawn-then-replace flow, because the runtime hook-binder binding is one-shot once lifecycle/native binding has begun.

## 2026-04-13T02:12:00.000Z Task: T07-wire-spawn-strategy-selection-end-to-end
Keep the end-to-end fresh-spawn path singular in runtime code: `VersionedEntityPlatform` should continue to call only the selected fresh-spawn strategy, and the 1.8.8 narrow fallback should be exposed as a support-level recovery hook invoked by that shared legacy strategy rather than by a factory-local wrapper branch.
Flatten the exact-version factory fresh-spawn entry wiring instead of adding new runtime abstractions; direct shared-strategy calls are enough for adapter entrypoints, while replacement/reference-rewrite flow remains version-local until later tasks.

## 2026-04-13T02:52:00.000Z Task: T09-replacement-orchestration-extraction
Introduce two real shared replacement strategies, `LegacyReplacementStrategy_1_8_to_1_12` and `PaperReplacementStrategy_1_21_plus`, and select them only for the already-classified legacy and paper-like bundles; keep unspecified replacement on the adapter-delegating path.
Keep the exact-version factories as support-bridge owners for replacement preparation, allocation, publish-time storage rewrites, wrapper rebinding, tracker-handle resolution, and repair scheduling instead of extracting world-add or tracking strategy families ahead of T10-T12.

## 2026-04-13T03:22:00.000Z Task: T10-world-add-strategy-extraction
Introduce real selected world-add strategies, `LegacyWorldAddStrategy_1_8_to_1_12` and `PaperWorldAddStrategy_1_21_plus`, and inject them into the already-shared fresh-spawn and replacement strategies so bundle selection names the same executable era family that now performs world publication.
Narrow replacement support by splitting publication steps from tracker resolution: world-add strategies now run the add/rewrite order, while exact-version support continues to provide post-publication tracker lookups and repair scheduling so T11/T12 still have clean follow-up seams.

## 2026-04-13T00:41:30-03:00 Task: T11-tracking-strategy-extraction
Introduce two real selected tracking strategies, `LegacyTrackingBindingStrategy_1_8_to_1_12` and `PaperTrackingBindingStrategy_1_21_plus`, and inject them into the already-shared legacy/paper fresh-spawn and replacement strategies instead of leaving tracker binding inline there.
Keep the extraction boundary narrow by letting the new tracking strategies own only post-publication handle snapshot resolution plus runtime `networkState` binding, while world-add remains in world-add strategies and repair scheduling remains in replacement strategies for T12.
Keep exact-version entrypoints on static era-specific tracking strategy constants derived from their existing binding metadata instead of widening adapter/bootstrap selection contracts again.

## 2026-04-13T00:57:00-03:00 Task: T12-final-orchestration-consolidation
Make `EntityStrategyBundleSelector` the shared composition entry for both `VersionedEntityPlatform` and the exact-version factories by adding a metadata-based selector overload plus typed narrowing helpers, instead of letting each factory rebuild its own era strategy instances.
Drop factory-local world-add/tracking strategy assembly from `EntityFactoryV1_8_8` and `EntityFactoryV1_21_11`; each factory now caches the selector-composed fresh-spawn and replacement strategies and stays responsible only for support-bridge methods, version-local helpers, and repair behavior.

## 2026-04-13T01:05:00-03:00 Task: T13-replacement-world-tracking-regressions
Keep T13 test-only by extending the existing runtime/bootstrap/factory fixtures instead of adding new harnesses: the regression net now lives where strategy selection, attach caching, and modern membership rewrites are already observable.
Treat strategy-family ids as selector output derived from metadata, not from the parsed Minecraft version string alone; the added bootstrap regressions lock that assumption in both legacy-like and paper-like directions.

## 2026-04-13T12:00:00.000Z Task: T14-architecture-documentation-and-migration-notes
Extend `wiki/wiki/CustomEntities_AILossInvestigation.md` instead of creating a second architecture page, so the root-cause narrative, the new shared-runtime map, and the maintainer QA script all stay in one place.
Document the final boundary explicitly: fresh spawn, replacement, world add, and tracking are shared runtime strategy concerns selected by era, while exact-version factories keep native helper details and the post-replacement repair pass.

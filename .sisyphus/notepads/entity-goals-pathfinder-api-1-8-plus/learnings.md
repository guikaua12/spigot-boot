# Learnings

## 2026-04-15 Task: T1 research synthesis
- `versions/api` public contracts use MIT license headers, `@since 2.0.2`, `@NotNull`/`@Nullable`, and full Javadocs on public methods.
- `EntityTemplate` is the main immutable-pattern reference: final class, final fields, package-private constructor, static `builder(...)` entry points, inner `Builder<T>`, and `Objects.requireNonNull(...)` on public entry points.
- `SpawnBuilder` is the fluent override-pattern reference: mutable builder state that emits immutable snapshots via `template()` and `spawnOptions()`.
- `ControlledEntity` is the minimal runtime-interface reference: prefer narrow interface + default methods over exposing implementation detail.
- `VersionedPlatform.spawn(..., Consumer<SpawnBuilder<T>>)` and `get(entity)` show the downstream consumption style the new goals API must fit.
- Strong external public references exist for path results/selectors (`Paper Pathfinder`, Brigadier `ArgumentTypes`) but not for a canonical stable vanilla-goal-key abstraction; the library should own that abstraction.

## 2026-04-15 Task: T1 contract implementation
- `GoalProfile<T>` fits the existing API style well as an immutable snapshot with an inner builder and selector-specific immutable list accessors.
- Selector-local replacement semantics can be frozen without runtime code by using builder maps keyed by `VanillaGoalKey` / `CustomGoalKey` and exposing only immutable lists from the built profile.
- `GoalOperationResult` can represent supported-but-absent removals cleanly through `Operation.REMOVE` plus `removedEntries() == 0`, while unsupported combinations stay in explicit exception types.

## 2026-04-15 Task: T2 template/spawn goal integration
- EntityTemplate<T> now carries a non-null immutable GoalProfile<T> snapshot, defaulting to GoalProfile.builder(bukkitType).build() so both registered templates and one-off spawn builders always expose an empty profile instead of 
ull.
- EntityTemplate.Builder<T> and SpawnBuilder<T> can share the same public pattern for goals: full-profile replacement via goalProfile(...) plus typed append helpers that rebuild through GoalProfile.Builder, which preserves selector-local deduping and avoids mutating the source template during one-off spawn customization.
- VersionedPlatform.spawn(template, location, customizer) stays source-compatible because the customizer path still only consumes spawnBuilder.template() and spawnBuilder.spawnOptions(), with merged goals carried entirely inside the immutable template snapshot.

## 2026-04-15 Task: T3 controlled-entity runtime goal manager contract
- `ControlledEntity` can gain goal access non-breakingly through a default `goalManager()` method, which matches the existing narrow-interface pattern and immediately propagates the API surface to spawned and attached runtime handles.
- The v1 live manager contract fits best as `GoalManager<T>` with typed spec-based add methods, key-based remove methods, selector-wide `clear(...)`, and `managedGoals()` returning only a deterministic `GoalProfile<T>` snapshot of the recognized managed set.
- A placeholder unsupported manager is safe for this phase: it returns an empty managed profile, carries `MinecraftVersion` context when available, and throws explicit `UnsupportedGoalOperationException` mutations instead of exposing raw selector contents or silently no-oping.

## 2026-04-15 Task: T4 runtime seam mapping
- `VersionedPlatform.get(entity)` and `spawnUnchecked(...)` are the two top-level runtime entry points that must feed one shared goal-management orchestration path.
- `AttachedEntityRegistry` already owns attached-entity caching and removal cleanup, so attached goal ownership should stay aligned with that lifecycle rather than adding a parallel registry.
- `AbstractRuntimeControlledEntity` already provides shared state, removal callbacks, and `scheduleNextTickRepair(...)`, making it the natural home for a real queued runtime goal manager.
- `RuntimeAttachedEntityLifecycle` and `RuntimeNativeEntityLifecycle` both extend `AbstractRuntimeControlledEntity`, so Task 4 can reuse one internal manager/state implementation for attach and spawn without touching exact-version factories yet.

## 2026-04-15 Task: T4 shared runtime goal-management core
- A shared internal `RuntimeGoalManager` inside `versions/runtime/.../goal/` works cleanly when `AbstractRuntimeControlledEntity` owns it and both spawned and attached lifecycles only provide the initial managed snapshot plus a deferred executor seam.
- Deterministic runtime mutation semantics are easiest to preserve by updating the managed `GoalProfile` immediately, queueing immutable mutation descriptors, and flushing that queue through `scheduleNextTickRepair(...)` on the next safe hook/tick path.
- Attached-entity ownership can preserve unknown third-party goals in this phase by snapshotting and mutating only the recognized managed set; the shared runtime layer never needs to inspect or clear unknown selector contents to satisfy the contract.

## 2026-04-15 Task: T5 goal-support SPI and selection seam
- The cleanest Task 5 seam mirrors the existing runtime selectors: `VersionedPlatform` resolves one immutable `EntityGoalSupportBundle` at construction time, then both spawn and attach paths consume only that bundle instead of branching on provider presence inline.
- Keeping the new provider optional through `VersionGoalSupportProvider` + `VersionGoalSupportMetadata.unspecified()` lets all existing exact-version adapters continue compiling unchanged while still advertising future managed-goal keys, attached snapshot support, and executor factory availability.
- Goal-support family selection is best treated as coarse runtime classification (`LEGACY`, `TRANSITIONAL`, `MODERN`, `LATEST`, `UNSPECIFIED`) so later tasks can plug family backends into a stable bundle id without reshaping the public runtime entry point again.

## 2026-04-15 Task: T9 latest-family 1.21.11 goal support
- `1.21.11` can opt into the shared runtime goal seam by implementing `VersionGoalSupportProvider` on `SpigotVersionAdapterV1_21_11`; no public API changes are required when the exact-version adapter advertises metadata plus spawned/attached executor factories.
- A package-private helper inside `versions/1.21.11` is enough to keep the Paper/latest-family goal catalog internal while still snapshotting recognized attach-time goals from reflective `goalSelector` / `targetSelector` handles.
- The shared runtime queue contract still applies unchanged on `1.21.11`: attach-time recognized goals update the managed snapshot immediately, but mutation batches only flush on the next safe hook/tick boundary.

## 2026-04-15 Task: T7 transitional-family 1.16.5 goal support
- `1.16.5` can plug into the shared runtime contract entirely from the exact-version module by advertising `VersionGoalSupportProvider` on the adapter and keeping selector reflection plus mutation execution in one package-private helper.
- Fresh-spawn builder-time goals need the version-local executor to bind against the generated native handle during `bindLifecycleToNativeEntity(...)`; attached flows need the same binding during `bindLifecycleToReplacement(...)` so queued runtime mutations reach the replacement handle.
- Replacement/attach tests need valid Bukkit-entity fixtures (`isValid() == true`) or the shared runtime will correctly skip queued mutation execution because the controlled entity looks already removed.

## 2026-04-15 Task: T8 modern-family 1.17.1 + 1.19.2 learnings
- 1.17.1 and 1.19.2 can reuse the shared runtime goal contract entirely from the exact-version modules by advertising VersionGoalSupportProvider on the adapter and keeping selector snapshot/application local to the modern family factories.
- Fresh-spawn builder-time goal coverage is stable in unit tests when the modern-family tests use a real mocked World in SpawnOptions.at(...); production null-world validation stays intact instead of being weakened for the test path.
- For the modern exact-version unit fixtures, the safest attach-time verification boundary is the version-local managed-goal snapshot application helper itself: it exercises the same selector preservation/removal logic that the executor applies while avoiding false negatives from a minimal hook-binder stub.

## 2026-04-15 Task: T6 legacy-family 1.8.8 + 1.13.2 learnings
- Both legacy exact versions still expose the old selector contract the backend needs: `goalSelector` / `targetSelector` live on the native zombie handle, registered entries live in selector field `b`, and selector add/remove still route through `a(int, PathfinderGoal)` / `a(PathfinderGoal)`.
- Task 6 can be regression-tested without real NMS jars by placing minimal fake classes under `src/test/java/net/minecraft/server/v1_8_R3` and `.../v1_13_R2`; the version-local reflection code then exercises the same package/class names it will resolve at runtime.
- Using the runtime batch’s final managed snapshot as the exact-version sync target is enough to cover legacy queued mutations and unmanaged-goal preservation without duplicating any queue state outside `versions/runtime`.

## 2026-04-15 Task: T9 1.21.11 latest-family fix
- `1.21.11` needs the same native-handle bind step as older exact-version families for spawn-time goals: wiring the package-private helper into `EntityFactoryV1_21_11.bindRuntimeLifecycle(...)` is enough to apply the initial managed profile without changing any public API.
- For the latest family, a stable exact-version backend can preserve unknown selector entries by treating the runtime batch’s final `GoalProfile` as the sync target and only removing/rebuilding recognized managed entries on each flush.
- The exact-version tests are strongest when they re-snapshot the handle through `createAttachedGoalMutationExecutor(...).initialManagedGoals()` after a flush; that proves real selector mutation behavior without depending on one concrete internal wrapper class.

## 2026-04-15 Task: T10 shared goal contract regression coverage
- The shared API surface already covered immutable builder-time goal snapshots, so the missing contract gap was spawn override replacement: `SpawnBuilderGoalProfileTest` now locks same-key merge behavior without mutating the source template.
- The clearest shared negative path for unsupported goal operations is `GoalManager.unsupported(ArmorStand.class, version)`, which proves the explicit `UnsupportedGoalOperationException` contract carries selector, key, entity type, and Minecraft version context without any exact-version backend.
- The strongest false-green guard against noop executors is in `ControlledEntityGoalManagerRuntimeContractTest`: assert the managed snapshot changes immediately, the executor-applied snapshot stays old until the next safe tick, duplicate adds report replacement, and repeated removals return `1` then `0`.

## 2026-04-15 Task: T12 sample-plugin exercise path
- The smallest useful in-repo demo surface was one new player-facing spawn command plus a refocus of the existing `attach-existing-zombie` path, rather than expanding the sample plugin into a broader goals showcase.
- The new sample flows stay on the public entity-goals surface only: `SpawnBuilder.addVanillaGoal(...)` for builder-time configuration and `ControlledEntity.goalManager().addVanilla(...)` / `removeVanilla(...)` for attached-entity mutation.
- `jdtls` is unavailable in this environment, so Maven remained the authoritative Java verifier; `mvnw.cmd -pl test-plugin -am package` and `mvnw.cmd clean test` both completed successfully.

## 2026-04-15 Task: T12 verification unblock
- The `versions/runtime` package-phase blocker was real: the shade filter excluded core Javassist classes such as `ClassPath` before relocation, which broke `EntityFactoryV1_13_2` during the package reactor once the runtime jar was consumed by legacy exact-version tests.
- `spigot-boot-commands-config-spigot` needed an explicit direct dependency on `spigot-boot-config`; after declaring the dependency, both normal and clean test runs for that module resolved config-core classes consistently.
- One small sample-plugin compile typo also surfaced during the package verification wave (`orderedDetail(...)` did not exist in `EntityDemoService`); replacing it with a local `LinkedHashMap` trace payload restored `test-plugin` compilation without changing behavior.
- The final required Maven commands must be run sequentially in the same workspace; running `package` and `clean test` in parallel against one worktree can create false reactor failures by cleaning module outputs out from under the other build.

## 2026-04-15 Task: T8 modern-family spawn hook fix
- 1.19.2 fresh spawns need the same generated bridge metadata shape as its replacement path; applying the initial managed goal snapshot alone is not enough because post-spawn goal mutations flush only through the bound native hook/tick lifecycle.
- An exact-version regression can prove the spawned runtime path without a real server world by constructing private fresh-spawn metadata reflectively, mocking a world getHandle() bridge, and driving the generated native entity's 	ick() method directly after indLifecycleToNativeEntity(...).

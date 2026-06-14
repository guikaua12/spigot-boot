# Folia scheduler & region-correctness — design

- **Date:** 2026-06-14
- **Status:** Approved (design); pending implementation plan
- **Modules:** `core-spigot` (new abstraction), `inventory-api/api` (migration), `config-spigot` (Sound)
- **Related:** load-time Folia fixes already landed this session — `@Plugin(foliaSupported)` + `PluginAnnotationProcessor`, and `InventoryApiNMS.resolveTitleUpdater`. Those make a plugin *boot* on Folia; this spec makes the *runtime* threading correct.

## 1. Problem

Folia (PaperMC's regionized fork) has no single main thread: the world is split into independently-ticked regions, plus a global-region thread and an async pool. Code may only touch a chunk/block/entity/player/inventory from the thread that owns that region.

The framework targets the legacy single-main-thread model directly. A prior audit found two failure classes; this spec addresses the runtime one:

- **8 hard blockers** — legacy synchronous `BukkitScheduler` calls (`runTask`/`runTaskLater`/`runTaskTimer`) that throw `UnsupportedOperationException` on Folia at the call site, on any thread:
  - `core-spigot` `Utils.java`: `sync` (L129), `syncLater` (L133), `playSound` (L146).
  - `inventory-api/api`: `FirstRenderPhase` view-update timer (L194), `ViewEngine` click self-defer (L139) and deferred-op drain (L345), `FlushCoordinator` coalesced shared flush (L185), `BukkitSettleDispatcher` async-settle (L57).
- **Region-ownership hazards** — `Bukkit.isPrimaryThread()` / `ThreadUtils.assertMainThread` used as a "safe to mutate" guard. On Folia this is `true` on *any* tick thread, so it neither proves the viewer's region is owned nor protects the throwing calls behind it. It gates `openInventory`, `setTitle`, slot paint, `closeInventory` across ~11 `ViewEngine` entry points.

Separately, a modern-Paper ABI break: `org.bukkit.Sound` changed from an enum to an interface, so `SoundSerializer`'s `Enum`-upcast bytecode fails class verification (`VerifyError`). It is currently disabled.

## 2. Goals / non-goals

**Goals**
- One platform-conditional scheduling abstraction that works on Spigot 1.8.8 → modern Paper → Folia from a single code path.
- inventory-api views open/update/close **region-correctly** on Folia (full correctness, not just "stops throwing").
- `Utils` scheduling helpers Folia-correct (redesigned to require a target).
- `SoundSerializer` working cross-version (1.8.8 enum ↔ modern interface) via reflection.
- Keep working on legacy Spigot/Paper; do not regress existing tests.

**Non-goals**
- No async-scheduler abstraction (legacy async already works on Folia; the pagination timeout thread stays as-is).
- No change to event/command/plugin-message registration (already Folia-compatible).
- No remediation of `pmc`/`placeholder`/`commands-spigot` caller-dependent reads (separate, lower priority).

## 3. Approach

**Chosen: A — platform-detected scheduler with lazily-isolated implementations.** Mirrors the existing `InventoryApiNMS` selector pattern.

- `PlatformScheduler` interface (no version-specific references).
- `FoliaPlatformScheduler` — references the modern Paper scheduler API (`Bukkit.getRegionScheduler()`, `Entity#getScheduler()`, `Bukkit.getGlobalRegionScheduler()`, `Bukkit.isOwnedByCurrentRegion(...)`). Instantiated only when that API is present, so it works on **modern Paper and Folia** alike.
- `BukkitPlatformScheduler` — references the legacy `BukkitScheduler` + `Bukkit.isPrimaryThread()`. Used on legacy Spigot / old Paper.
- `PlatformSchedulers.create(plugin)` detects the modern API via `Class.forName` (e.g. `io.papermc.paper.threadedregions.scheduler.RegionScheduler`) and returns the right impl.

**Isolation constraint (critical):** a class that references the modern scheduler API fails to *load* on 1.8.8. So all such references live only inside `FoliaPlatformScheduler`, which is constructed only when detection passes — legacy servers never link those types (no `NoClassDefFoundError`). `core-spigot` compiles against modern `paper-api`, so the types resolve at compile time; animal-sniffer (1.8.8 signature) `<ignore>` entries cover each modern call (same mechanism as the existing `org.bukkit.UnsafeValues` ignore).

**Rejected:** B reflection-only (slow, untyped, verbose); C drop legacy support (contradicts the framework's 1.8.8 coverage).

## 4. Component design

### 4.1 `PlatformScheduler` (`core-spigot`, package `tech.guilhermekaua.spigotboot.core.spigot.scheduler`)

Only the operations callers need (YAGNI):

```java
PlatformTask runOnEntity(Entity entity, Runnable task, @Nullable Runnable retired);
PlatformTask runOnEntityLater(Entity entity, Runnable task, @Nullable Runnable retired, long delayTicks);
PlatformTask runOnEntityAtFixedRate(Entity entity, Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks);
PlatformTask runAtRegion(Location location, Runnable task);
PlatformTask runAtRegionLater(Location location, Runnable task, long delayTicks);
PlatformTask runGlobal(Runnable task);
PlatformTask runGlobalLater(Runnable task, long delayTicks);
boolean ownsRegion(Entity entity);
boolean ownsRegion(Location location);
```

- `retired` runs when the target entity is gone before the task fires (Folia Entity scheduler semantics); on legacy it is ignored (or invoked when scheduling is rejected during shutdown), preserving the existing `BukkitSettleDispatcher` drop-on-disable behavior.
- `delayTicks` / `periodTicks` are in ticks for parity with the legacy API; Folia schedulers also take ticks.

### 4.2 `PlatformTask`

A minimal cancellable handle: `void cancel(); boolean isCancelled();`. Two impls wrapping `org.bukkit.scheduler.BukkitTask` and `io.papermc.paper.threadedregions.scheduler.ScheduledTask` (the Folia type referenced only inside the Folia impl).

### 4.3 Implementations & detection

- `BukkitPlatformScheduler(Plugin)` — entity/region/global all map to `Bukkit.getScheduler().runTask*`; `ownsRegion(...)` → `Bukkit.isPrimaryThread()`.
- `FoliaPlatformScheduler(Plugin)` — entity → `entity.getScheduler().run/runDelayed/runAtFixedRate`; region → `Bukkit.getRegionScheduler().run/runDelayed`; global → `Bukkit.getGlobalRegionScheduler()`; `ownsRegion(...)` → `Bukkit.isOwnedByCurrentRegion(...)`.
- `PlatformSchedulers.create(Plugin)` — `Class.forName` detection; documented and unit-tested through a package-private pure selector (parallel to `InventoryApiNMS.resolveTitleUpdater`).

### 4.4 DI wiring

A `@Configuration` in `core-spigot`:

```java
@Bean
@ConditionalOnMissingBean(PlatformScheduler.class)
public PlatformScheduler platformScheduler(Plugin plugin) {
    return PlatformSchedulers.create(plugin);
}
```

`@ConditionalOnMissingBean` lets a downstream plugin substitute its own. (Confirm a `Plugin`/`JavaPlugin` bean is injectable — it is per existing `@Config`/`PersistenceConfig` injection.)

### 4.5 inventory-api migration (full region-correctness)

Inject `PlatformScheduler` into `ViewEngine`; thread the viewer `Player` through entry points.

| Site | Change |
|---|---|
| `FirstRenderPhase` L194 (timer) | `runOnEntityAtFixedRate(session.player(), …, interval, interval)`; `Session.updateTask` field type `BukkitTask` → `PlatformTask` |
| `ViewEngine` L139 (open self-defer) | `runOnEntity(player, () -> open(...), null)` |
| `ViewEngine` L345 (deferred drain) | `runOnEntity(session.player(), () -> drainDeferred(session), null)` |
| `FlushCoordinator` L185 (shared flush) | **fan out per session**: for each open session `runOnEntity(session.player(), () -> flushSharedForSession(session, ids), null)` |
| `BukkitSettleDispatcher` L57 | carry viewer `Player` on `PageRequest`; `runOnEntity(viewer, task, null)` |
| `ThreadUtils.assertMainThread` (universal guard) | region-aware `assertOwnsRegion(scheduler, player, op)` using `ownsRegion(player)`; viewer threaded into each entry point |
| `FlushCoordinator` L162, `BukkitSettleDispatcher` L52 (`isPrimaryThread` inline branch) | `ownsRegion(session.player())` / `ownsRegion(viewer)` |

Leaf mutators (`SlotPainter`, `OpenPhase.createInventory`, `ClosePhase`, `OpenFailureHandler`, title update) need no logic change — correctness follows from their callers running on the viewer's region thread via the above.

### 4.6 Utils redesign (breaking change, no in-repo callers)

- `playSound(PlatformScheduler scheduler, Player player, String name)` → `scheduler.runOnEntity(player, () -> player.playSound(player.getEyeLocation(), resolveSound(name), 1f, 1f), null)`, where `resolveSound` is a cross-version Sound lookup using the same reflection technique as §4.7. This also fixes the latent `Sound.valueOf` modern-API break in the current `playSound`. (The plan must place `resolveSound` where both `core-spigot` and `config-spigot` can reach it, or duplicate the small reflective helper per module if the dependency direction does not allow sharing — to be settled in the plan.)
- `sync`/`syncLater` take an explicit `Entity` (or `Location`) target + `PlatformScheduler` and delegate. The old no-target overloads are **removed**.

### 4.7 Cross-version `SoundSerializer` (`config-spigot`)

Reflection-based, branching at runtime on `Sound.class.isEnum()`:

- **serialize** — enum: `((Enum<?>) value).name()`; interface: reflectively call `getKey()` and `toString()` (no static `Keyed`/`NamespacedKey`/`Registry` references, so the class still loads on 1.8.8).
- **deserialize** — enum: reflective `Sound.valueOf(String)`; interface: reflective registry lookup by key.
- Re-register in `BukkitSerializers.registerAll` (uncomment L51).

## 5. Testing

- `PlatformSchedulers` selector: pure-logic unit test (parallel to `InventoryApiNMSTest`); legacy impl exercisable via MockBukkit; Folia impl branches asserted via the detection seam.
- inventory-api: extend existing engine tests (MockBukkit) to assert tasks route through an injected `PlatformScheduler` (mock/fake) and that entry points consult `ownsRegion`; reuse the `BukkitSettleDispatcherTest` style.
- `SoundSerializer`: test the modern (interface) path against the real `Sound` on the test classpath + unit-test the key-string parsing; document that the 1.8.8 enum path cannot be co-tested in one JVM.
- Whole reactor green with JDK 21.

## 6. Delivery (increments — build + tests green between each)

1. `PlatformScheduler` + `PlatformTask` + impls + factory + DI + tests.
2. inventory-api migration + region-correctness + tests.
3. `Utils` redesign.
4. cross-version `SoundSerializer`.
5. Validation: reactor build (JDK 21) + Folia 26.1.2 boot; if feasible, a runtime GUI smoke test (open a view, confirm no region thread-check error).

## 7. Risks

- Folia `ScheduledTask`/scheduler signatures referenced in the Folia impl must be guarded by detection so legacy servers never load that class — verified by the isolation pattern.
- Threading the viewer `Player` through ~11 entry points is the largest change; staged in increment 2 with tests at each step.
- Cross-version Sound reflection cannot be fully co-tested in one JVM; mitigated by testing the live path and isolating parsing logic.

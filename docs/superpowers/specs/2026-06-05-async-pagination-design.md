# Async Pagination for inventory-api — Design Spec

- **Date:** 2026-06-05
- **Module:** `modules/inventory-api/api`
- **Status:** Approved by user (brainstorming session); adversarially reviewed by 5-lens workflow (48 findings folded in)

## 1. Goal

Let pagination sources be loaded asynchronously (e.g. from a database) instead of from an in-memory list. On every page change, a user-supplied function returns a `CompletableFuture` with that page's items; while it is in flight the paginator exposes `isLoading()` and can auto-render a configurable loading item. All three pagination types (`NormalPagination`, `ScrollPagination`, `PatternPagination`) support it through one shared mechanism.

**Chosen architecture (Approach C):** extract a `PageSource<T>` strategy that answers "where do page items come from". The existing pagination classes delegate to it; `EagerPageSource` reproduces today's in-memory behavior as a degenerate synchronous case, `AsyncPageSource` implements the async machinery exactly once.

Non-goals: page caching beyond the opt-in TTL cache (§13); fixing unrelated pre-existing issues except those listed in §8 and §11.

## 2. New types — package `tech.guilhermekaua.spigotboot.inventoryapi.pagination.source`

All new files carry the MIT license header and complete Javadoc (`@param`/`@return`/`@throws`) per CLAUDE.md. Main sources compile at **Java 8** (`pom.xml` `-source/target 1.8`): no records, no `List.of()`, no `CompletableFuture.orTimeout`. Use `Collections.emptyList()` / `Collections.unmodifiableList(new ArrayList<>(...))`.

### 2.1 `AsyncPageSupplier<T>` (user-facing functional interface)

```java
@FunctionalInterface
public interface AsyncPageSupplier<T> {
    CompletableFuture<PageResult<T>> load(PageRequest request);
}
```

Javadoc must state: called on every page change (unless a fresh cache entry exists, §13); the future may complete on any thread; returning `null` is treated as a failed load; return at most `request.getPageSize()` items; a future that never completes leaves the paginator loading until a superseding navigation or the configured `requestTimeout` (§12) recovers it.

### 2.2 `PageRequest`

Final class, `@Getter` + `@RequiredArgsConstructor` (all fields final): `int page` (1-indexed), `int pageSize`, `int offset` (global element offset), `Viewer viewer`.

Javadoc: **`offset` and `pageSize` are the authoritative query bounds** (`LIMIT pageSize OFFSET offset`); `page` is informational. This matters for scroll pagination, whose stride is one element per page — `offset = page - 1`, not `(page - 1) * pageSize`.

### 2.3 `PageResult<T>`

Final class: `List<T> items` (stored as an unmodifiable defensive copy), `int totalElements`. Static factory `PageResult.of(List<T> items, int totalElements)`; validates `items != null`, `totalElements >= 0`. If a delivered result has more items than `request.getPageSize()`, `AsyncPageSource` truncates to `pageSize` and logs a warning (provider contract violation, §2.6).

### 2.4 `PaginationErrorCallback` (functional interface)

```java
void onError(PageRequest request, Throwable error);
```

Named `*Callback` per module convention (`ItemCallback`, `ItemUpdateCallback`).

### 2.5 `PageSource<T>` (strategy interface)

```java
public interface PageSource<T> {
    void request(PageRequest request, BiConsumer<PageResult<T>, Throwable> onSettle);
    int totalElements();
    boolean totalsKnown();
    boolean isLoading();
    Throwable lastError();
    List<T> elements();
    default void invalidate() {}
}
```

Normative `request` Javadoc (Liskov guard for third-party implementations):

- `onSettle` is invoked **at most once** per request, with exactly one of result/error non-null; it may never be invoked (superseded request).
- It MAY be invoked synchronously inside `request()` and MAY be invoked on any thread.
- Implementations MUST NOT settle a request that has been superseded by a newer one.
- `isLoading()` must be `false` once the latest request has settled.

`elements()`: eager → the full backing list; async → items of the most recently applied result. `totalsKnown()`: eager → always `true`; async → `false` until the first successful settle. `invalidate()`: clears any cache (no-op default; eager no-op).

### 2.6 `EagerPageSource<T>`

Wraps a `List<T>` (defensive copy). `request()` settles synchronously with `PageResult.of(slice, list.size())` where the slice clamps **both** sublist ends: `from = min(offset, size)`, `to = min(offset + pageSize, size)` (a leftover `currentPage` from a larger previous source must yield an empty page, not `IndexOutOfBoundsException`). `isLoading()` always `false`, `lastError()` always `null`, `totalsKnown()` `true`. Static `EagerPageSource.empty()`.

### 2.7 `AsyncPageSource<T>`

Owns ALL async machinery; pure Java (no Bukkit imports) for plain-JUnit testability. Constructed with: `AsyncPageSupplier<T>`, optional `PaginationErrorCallback`, optional timeout `Duration`, optional cache settings (TTL + max pages), and a **settle `Executor`** (see §3). Package-private constructor overload accepts an injectable `ScheduledExecutorService` for tests.

State machine — **every transition inside one `synchronized` block** (volatile-only was proven racy by review: check-then-act could render a stale page or stick `isLoading=true` forever):

- A single `AtomicLong` issues request ids; it is the only source of truth for "latest" (no separate `latestId` field that could regress under concurrent dispatch).
- `request()`: under the lock — new id, mark loading, clear `lastError`; check cache (§13): fresh hit settles synchronously on the calling thread (counts as that id's settle) and skips the supplier. Otherwise call `supplier.load(request)` outside the lock inside try/catch; a synchronous throw or a `null` future settles that id as failure.
- Completion (`whenComplete`) and timeout (§12) both route through the settle executor: `settleExecutor.execute(task)`. The task, under the lock, re-validates `id == latest` **and** "not already settled" before applying state (items, totalElements, loading=false / lastError) — then invokes `errorCallback` (failures only) and `onSettle` outside the lock. Superseded or already-settled ids are discarded silently with no state change except that a superseded id never clears `loading` (the newer request owns the flag).
- Successful results are truncated to `pageSize` if oversized (warning log) and cached when caching is enabled. Failures and timeouts are never cached.

## 3. Threading model

`tickAsync` is a per-inventory opt-in (`InventoryConfiguration.tickAsync()`, documented as the only exception to main-thread-only Bukkit access). Async pagination honors the configured mode:

- The settle executor runs the task **inline** when `Bukkit.isPrimaryThread()` is already true or the owning inventory configured `tickAsync(true)`; otherwise it hops via `Bukkit.getScheduler().runTask(viewer.getPlugin(), task)` — same precedent as `ViewerImpl.close()`.
- The executor is created at `build()` time as a lambda owned by the pagination instance; it resolves the viewer lazily (set during `init`). If the viewer is not yet initialized, run inline (no Bukkit interaction can have happened yet).
- Because the state application happens inside the executor task (§2.7), the main thread is the single writer for default-configured inventories — collapsing the render/state races found in review.
- `currentItems` in the pagination impls is `volatile` and always holds an unmodifiable list. `insertPageItems` reads it (and, for Pattern, `currentPattern` + `itemPageLimit`) **once into locals** and uses the same snapshot for both list-building and `fillPage` — fixing a latent `IndexOutOfBoundsException` tear in today's `tickAsync` path (list sized by old limit, filled against new pattern).
- The error callback runs on the settle executor thread (main thread unless `tickAsync`).

## 4. Render triggering — no synchronous-settle renders

A settle that completes **inline within the dispatching call** (eager source, cache hit, already-completed future on the main thread) must NOT call `updateInventory`; the dispatching caller renders, exactly as today. Only genuinely asynchronous settles trigger a render. Mechanism: the pagination sets a flag around the `pageSource.request(...)` call; the settle handler skips its render when it runs within that window.

This prevents: (a) `setSource(...)` called from `update()` recursing to `StackOverflowError` (settle → `updateInventory` → `update()` → `setSource` → …), and (b) `init`/`setSource` inside `firstOpen` re-entrantly firing `update()` before `configureInventory` has run (`defaultOpenInventory` order: register viewer → open → `firstOpen` → `configureInventory` → `update`). Eager-mode behavior stays bit-for-bit today's: `init` and `setSource` never render.

`changePage(int)` flow (all three impls):

1. Clamp the target (lower bound 1 always; upper bound only when `pageSource.totalsKnown()`, §5).
2. **Dedupe:** if the clamped target equals `currentPage` AND `pageSource.isLoading()` (every dispatch targets `currentPage`, so an in-flight request is always for it), return without dispatching (click spam must not hammer the supplier and starve the first render). `refresh()` bypasses this (§13).
3. Snapshot rollback state (§6): `currentPage`; Pattern also `currentPattern`/`lastPattern`/`itemPageLimit`.
4. Commit navigation state (Pattern: switch pattern, recompute `itemPageLimit`, `clearLastPattern()`), compute per-type `offset`/`pageSize`, dispatch `pageSource.request(...)`.
5. Render once: if `isLoading()` after dispatch returned → loading frame; else the inline-settled result. Guarded by `viewer.getPlayer() != null` — hoisted so the existing unguarded `viewer.getPlayer()` in all three `changePage` impls is fixed in the same motion.

Settle handler (runs on the settle executor): apply result (`currentItems = result.getItems()`), re-clamp downward if `currentPage > getTotalPages()` (re-dispatching through `changePage`; converges — totals only shrink the target monotonically toward 1), or roll back on failure (§6); then render with the same null-player guard. The whole body is wrapped in try/catch routed to the plugin logger so no exception vanishes into an unobserved future.

Per-type request parameters:

| Type | `offset` | `pageSize` |
|---|---|---|
| Normal | `(page - 1) * itemPageLimit` | `itemPageLimit` |
| Scroll | `page - 1` | `itemPageLimit` (window size) |
| Pattern | cumulative slot-count of pages `1..page-1` | new `currentPattern` slot count |

`insertPageItems()` (all three): if `pageSource.isLoading()` → fill every slot of the current layout/pattern with `loadingItem` (falling back to `fallbackItem`, then empty, when unset); else render the `currentItems` snapshot positionally (`0..n`), padded with `fallbackItem` to the page limit. `editor.fillPage(...)` unchanged.

## 5. Navigation semantics with unknown totals

- Before the first successful settle, `AsyncPageSource.totalsKnown()` is `false` → `changePage` skips the upper clamp. `init → changePage(savedPage)` therefore honors a saved page instead of collapsing to page 1 (totals start at 0 → `getTotalPages() == 1`). The first settle re-clamps downward if the request was out of range.
- Empty-source guards in `getTotalPages()` translate to `pageSource.totalElements() == 0` — NOT `elements().isEmpty()` (an empty delivered page with `totalElements > 0` must not collapse totals to 1).
- `NormalPagination.getTotalPages()` ceil division uses `long` math (async totals can approach `Integer.MAX_VALUE`; the current int expression overflows negative).
- New `Pagination` interface members (source-compatible for external implementors):
  - `default boolean isLoading() { return false; }`
  - `default Throwable lastError() { return null; }`
  - `default int getTotalElements() { return getSource().size(); }`
  - `default void refresh() { changePage(getCurrentPage()); }`
  - The three impls override all four (delegating to `pageSource`; `refresh()` per §13).
- `lastError()` returns the failure of the most recent settled request; cleared on the next dispatch.

## 6. Failure handling — transactional navigation

`changePage` snapshots pre-dispatch navigation state. On a failed settle (exception, `null` future, synchronous throw, timeout):

1. Restore `currentPage` (and for Pattern: clear the failed pattern's slots, restore `currentPattern`/`lastPattern`/`itemPageLimit`).
2. Keep the last good `currentItems`.
3. `lastError` is set (by the source); the error callback has been invoked by the source.
4. Render (guarded).

Without rollback, a failed `nextPage()` leaves `getCurrentPage()`/nav arrows describing page N+1 while slots show page N — and Pattern renders old items scrambled into the new pattern's shape after the old pattern was already cleared.

## 7. Contract updates (Javadoc)

- `Pagination.getSource()`: redefined — "the elements currently loaded locally: the full source for eager paginators, the items of the most recently delivered page for async paginators". Returns an **unmodifiable view**. ⚠ Deliberate eager-mode behavior change: mutating the returned list no longer affects rendering (today Lombok's getter leaks the live list); use `setSource`/`refresh()`.
- `Pagination.setSource(List)`: documented as switching the paginator to an eager in-memory source. On an async-built paginator this **discards the supplier** (loading item, error callback, cache, timeout become inert) and logs one warning — explicitly not `UnsupportedOperationException` (the LSP anti-pattern named in CLAUDE.md). Pre-`init` calls remain legal: the request dispatch is deferred to `init`.
- `init(Viewer)`: unchanged contract; after storing the viewer and computing limits it dispatches the initial request for `currentPage` (eager-empty settles inline and harmlessly; async fires the first load so `apply()` renders the loading frame). Never renders (§4).
- `changePage(int)`: documents the unknown-totals lower-bound-only clamp (§5) and the in-flight dedupe (§4).
- `getPageOfIndex(int)`: documents `-1` for out-of-range across all types (§11).

## 8. Prerequisite fix — close-event unregistration

`CustomInventoryListener.onInventoryClose` (`listener/CustomInventoryListener.java:61-69`) unregisters by player unconditionally. Opening menu B from menu A makes Bukkit fire A's `InventoryCloseEvent` synchronously inside `player.openInventory`, which — because `defaultOpenInventory` registers B's viewer *before* opening — unregisters the just-registered B viewer. Pre-existing bug (kills tick updates/click handling after menu→menu navigation); fatal for async (settle → `findViewer` empty → no-op → permanent loading frame).

Fix, shipped before/with the async work: only unregister when the closing inventory is the viewer's current inventory (identity-compare `event.getInventory()` with the viewer's created inventory). Note: MockBukkit's `PlayerMock.openInventory` does not fire the close event for the previous container, so this gets a targeted unit test of the listener with a hand-fired event rather than a faithful end-to-end repro.

## 9. Pagination class changes (shared across the three impls)

- Field swap: `List<T> source` → `PageSource<T> pageSource` (non-final; `@Getter(AccessLevel.NONE)`). New fields: `volatile List<T> currentItems = Collections.emptyList()` and `InventoryItemSupplier loadingItem` (both `@Getter(AccessLevel.NONE)`).
- Hand-written constructors (replacing `@RequiredArgsConstructor`, since `pageSource` is reassignable and `loadingItem` joins the parameter list). Exact signatures:
  - `NormalPagination(InventoryItemSupplier fallbackItem, GenericInventoryItemSupplier<T> itemSupplier, InventoryLayout layout, InventoryItemSupplier loadingItem, PageSource<T> pageSource)`
  - `ScrollPagination(...)` — same shape.
  - `PatternPagination(InventoryItemSupplier fallbackItem, GenericInventoryItemSupplier<T> itemSupplier, List<InventoryLayout> patterns, InventoryItemSupplier loadingItem, PageSource<T> pageSource)`
  - Builders are the supported construction path; the constructor change is acceptable pre-1.0.
- Hand-written `getSource()` → unmodifiable view of `pageSource.elements()`. Hand-written `isLoading()`/`lastError()`/`getTotalElements()` delegating to `pageSource`.
- All `source.size()` reads → `pageSource.totalElements()`; `source.isEmpty()` guards → `totalElements() == 0`.
- `PatternPagination` performance: `getTotalPages()`, `getPageIndex(int)`, `getPageOfIndex(int)` replace O(totalPages) walks with closed-form cycle math — precompute `cycleSize = Σ pattern slot counts` in the constructor; full cycles via division, remainder via a walk bounded by `patterns.size()`. Required because async unlocks totals where ~77k-iteration walks would run per tick per viewer. Existing tests pin equivalence.

## 10. Builders

All three builders gain exactly one async method plus the options type (package `pagination.builder`):

```java
public NormalPaginationBuilder<T> async(Consumer<AsyncPaginationOptions<T>> configurer)
```

`AsyncPaginationOptions<T>` — mutable options object (same pattern as `InventorySettings`), fluent setters returning `this`:

| Setter | Required | Default |
|---|---|---|
| `source(AsyncPageSupplier<T>)` | yes (when `async` used) | — |
| `loadingItem(InventoryItemSupplier)` | no | none → fallback/empty |
| `errorCallback(PaginationErrorCallback)` | no | none |
| `requestTimeout(Duration)` | no | disabled |
| `cacheTtl(Duration)` | no | caching disabled |
| `cacheMaxPages(int)` | no | 128 (only meaningful with `cacheTtl`) |

`build()` validation (house style — `Objects.requireNonNull(x, "x is required.")` / `IllegalArgumentException`): `source` required when `async(...)` was called; timeout/TTL positive; `cacheMaxPages >= 1`; `cacheMaxPages` without `cacheTtl` → `IllegalArgumentException`. Cross-field "X requires asyncSource" rules are structurally impossible now. `build()` wires either `EagerPageSource.empty()` or an `AsyncPageSource` configured from the options.

Example (the agreed API shape):

```java
NormalPaginationBuilder.<ShopItem>builder()
    .layout(layout)
    .itemFactory((viewer, item) -> InventoryItem.of(render(item)))
    .async(options -> options
        .source(request -> shopRepository.findPage(request.getOffset(), request.getPageSize()))
        .loadingItem(viewer -> InventoryItem.of(LOADING_STACK))
        .errorCallback((request, error) -> log.warn("page load failed", error))
        .requestTimeout(Duration.ofSeconds(10))
        .cacheTtl(Duration.ofSeconds(30))
        .cacheMaxPages(64))
    .build();
```

## 11. `getPageOfIndex` fix and alignment

- `ScrollPagination.getPageOfIndex(i)`: current chunked math is wrong for window semantics (window=7, size=10: claims index 9 is on page 2, which shows indices 1–8). New: **first page on which the index becomes visible** — `max(1, i - itemPageLimit + 2)`; always ≤ `getTotalPages()` for valid indices.
- All three types return **`-1` for `index < 0 || index >= getTotalElements()`** (Pattern already does; Normal/Scroll currently return unbounded values — small behavior change, regression tests updated).

## 12. Request timeout

- Opt-in via `requestTimeout(Duration)` (§10). Implemented in `AsyncPageSource`: a lazily-initialized shared static single-thread daemon `ScheduledExecutorService` (thread name `spigotboot-inventoryapi-pagination-timeout`) schedules one task per dispatch; any settle cancels it. Tests inject a deterministic scheduler (package-private constructor).
- Timeout settles its request id once as a failure (`TimeoutException`) through the standard machinery — at-most-once enforcement means a late real completion is discarded. Best-effort `future.cancel(true)`. Follows the §6 error path.
- Without a timeout configured, a never-completing future leaves the paginator loading until a superseding navigation; documented on `AsyncPageSupplier`.

## 13. Opt-in page cache

- Enabled by `cacheTtl(Duration)`; bounded LRU (`cacheMaxPages`, default 128) — relevant for scroll, where one page per element offset can mean thousands of keys.
- Lives inside `AsyncPageSource`, keyed by `offset + ":" + pageSize`, guarded by the existing lock (`LinkedHashMap` access-order). Fresh hit → synchronous settle with the cached `PageResult` (no supplier call, no loading frame; §4's no-inline-render rule applies). Totals update from cached results. Only successful settles populate it.
- A cache hit still consumes a request id, so an older in-flight request is superseded normally.
- Invalidation: `PageSource.invalidate()` clears it. The three impls override `refresh()` as: `pageSource.invalidate()`, then an unconditional re-dispatch of the current page (bypassing the §4 dedupe) — "re-query after a DB write" always hits the supplier. `setSource` discards the whole `AsyncPageSource`, cache included.

## 14. Testing

**Pure JUnit — `AsyncPageSource`** (controlled executors/latches; no Bukkit):
sync + async settles; out-of-order completion (stale must lose — both items and `isLoading`); two threads racing `request()` (later dispatch always wins); loading-flag integrity across superseded requests; error capture + `errorCallback`; `null` future and synchronous supplier throw; `totalsKnown()` transitions; oversized-result truncation; timeout fires → error settle, settle cancels timeout, late completion after timeout discarded; cache TTL hit/expiry, LRU eviction, invalidate, totals-from-cache, errors-not-cached.

**Pure JUnit — `EagerPageSource`:** offset beyond size (empty items, correct total), offset == size, partial last page, `empty()`.

**MockBukkit — per pagination type:** loading frame rendered while in flight; settle render asserted via `performOneTick()` (proves the scheduler hop — MockBukkit cannot detect off-main access, so the hop itself is the tested artifact); failure rollback (incl. Pattern pattern/limit restoration and slot clearing); nav totals/arrows driven by async `totalElements`; pre-first-load `changePage(savedPage)` honored, then re-clamped by first settle; settle after close/logoff no-ops (null-player guard); shrink re-clamp convergence (incl. `totalElements == 0` result); scroll supplier receives `offset = page - 1`; pattern supplier receives cumulative offsets and per-pattern sizes; eager regression: `setSource` inside `update()` does not recurse, `firstOpen` flow renders exactly as today; `getPageOfIndex` new semantics (all types, `-1` out-of-range); `refresh()` invalidates cache and re-fetches.

**Listener:** `onInventoryClose` identity guard (hand-fired events; MockBukkit cannot repro the menu→menu close, documented in the test).

**Sample:** `SampleAsyncPagedInventory` in `test-plugin` using delayed futures + loading item + error callback; page indicator rendered from `update()`/`configureInventory` (not from click callbacks, where the page number is stale by design under async).

## 15. Out of scope

- Stale-while-revalidate or cross-viewer shared caches.
- Folia-style scheduler abstraction (the settle executor is the single seam if needed later).
- Back-porting `refresh()`/async to the legacy `local-only/inventory-api-maven` sources.

## Appendix — review provenance

Adversarially reviewed by a 5-agent workflow (concurrency, contracts, page math, lifecycle, completeness) against the real code; 48 findings. Material design changes forced by review: main-thread settle hop honoring `tickAsync` (§3), no-inline-settle-render rule (§4), synchronized at-most-once settle machinery (§2.7), transactional navigation rollback (§6), unknown-totals clamp deferral (§5), close-listener prerequisite fix (§8), Java 8 + Lombok constructor constraints (§9), Pattern closed-form math (§9), `PageResult` truncation + null-future handling (§2.3, §2.7).

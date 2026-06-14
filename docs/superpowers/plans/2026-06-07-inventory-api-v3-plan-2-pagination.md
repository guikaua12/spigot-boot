# inventory-api 3.0.0 — Plan 2 of 3: Pagination Switchover

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 2.x pagination surface with the declarative v3 `Pagination` token API (spec §5.7) on top of the preserved page-source engine (spec §5.8), wired into the v3 view engine (`PaginationInitPhase`, reactive settles, click routing), with the module and full reactor green after every task.

**Architecture:** The 2.x geometry engine (`AbstractPageSourcePagination` + `Normal`/`Scroll`/`Pattern`) is **copied** to `internal.pagination.engine` behind the new `PaginationHost` seam (algorithms preserved verbatim, world-facing types swapped), then the old public pagination cluster is deleted in one switchover task — freeing the `pagination.Pagination` FQN for the new public token interface — and the new machinery (`PaginationSpec`/`PaginationBuilderImpl`/`PaginationImpl`/`PaginationBinding`) is built on the copy. Per-context bindings live in the pagination token's own `StateStore` slot; settles drive a scoped `PAGINATION_SETTLE` update pass. A `FlushCoordinator` is extracted from `ViewEngine` first (reviewer backlog #1) so settle plumbing lands on a clean seam.

**Tech Stack:** Java 8 (main sources; tests compile at 17), Maven, Spigot/Paper API 1.20.1, spigot-boot DI, JUnit 5, Mockito, MockBukkit-v1.20 3.20.2, Lombok (build with JDK 21).

**Spec:** `docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md` — referenced as §N. Plan-1 outcomes and the reviewer backlog are in `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-2-notes.md`.

**Build/test commands** (every mvnw invocation MUST set JDK 21 first — the shell default JDK 25 crashes Lombok):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'
.\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=<TestClass>"
# full module check:
.\mvnw.cmd -pl modules/inventory-api/api -am test -B
# tasks touching test-plugin additionally verify:
.\mvnw.cmd -pl test-plugin -am package -B -DskipTests
```

**License header:** every new file starts with the same MIT header block used by every existing file in the module (copy from `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/annotation/RegisterView.java` lines 1–22).

**Scope notes (deviations the plan consciously makes):**

1. **Early deletion of the 2.x pagination cluster.** The locked decisions defer 2.x deletion to Plan 3, but the FQN collision (old `pagination.Pagination` vs the new token) plus the host-seam refactor make the old public pagination types (`Pagination` interface, `impl/*`, `builder/*`), `InventoryEditor.fillPage`, and the four paged test-plugin samples unkeepable. Plan 2 deletes exactly that cluster (Task 5) and rewrites the four samples against the new API (Task 14). Plan 3 deletes the *rest* of 2.x. Nothing else 2.x is touched.
2. **`PaginationHost` carries `playerId()`/`plugin()`** in addition to the spec §10 sketch's three methods — the slim `PageRequest` (§5.8) must be populated from somewhere, and the host is the only world-facing seam the engine keeps. The host is implemented by `PaginationBinding` (per token), not by `ViewSession` as the §10 sketch says: a view may declare several pagination tokens, and `fillPage`/`requestRender` need per-token disambiguation. `ViewSession` remains the data home (bindings live in its `StateStore`).
3. **`update()` coalescing (reviewer backlog #2) is resolved as: keep the synchronous-pass-per-call implementation, amend the spec.** Settle-driven passes are scoped (token-singleton dirty set), so same-tick settle multiplication never triggers full passes. Task 15 amends spec §5.4's `update()` comment.
4. **Backlog items NOT taken:** #5 (`openInventory` result ignored — Plan 3 candidate, unrelated to pagination), #7 (`ViewRegistry` map hardening), #9 (OPEN_FAILED `closeInventory` tightening), #10 (onOpen-dirt spurious pass). Recorded for Plan 3 in Task 15's notes file.
5. **One deliberate reorder inside the preserved navigation algorithm:** the unbound-host record-only branch of `changePageInternal` moves AHEAD of the totals-known clamp (pinned rule 2). The 2.x order divides by zero when a pending target is replayed over a non-empty eager source (pre-bind `itemPageLimit` is 0) — a latent 2.x bug the new pending-target replay would have triggered. Observably equivalent (no in-flight request exists pre-bind; overshoot is corrected by the settle's downward re-clamp); regression-tested in Task 3; Task 15 amends the spec §10 behavior-map row accordingly.
6. **Small pinned behavior choices the spec leaves open (recorded here so they read as decisions, not drift):** the renderer-failure rate limit is per BINDING (one shared 1/min window across renderer/fallback/loading stages), a pragmatic coarsening of §9's "per component"; a pagination element whose renderer declares no item source gets the §9 failure semantics (keep previous content + rate-limited log) instead of §5.6's first-render `ViewConfigurationException` — a throwing fill mid-settle must not kill the session; a static component overlapping a pagination target slot aborts the open OPEN_FAILED with `ViewConfigurationException` (plan-defined rule — §5.3/§5.6 do not address the collision; silent double-ownership of a slot is the worst outcome); the post-freeze `IllegalStateException` of §5.2 fires at `PaginationBuilder.build()` (where the token registers), not at the `paginate*` factory call itself.

---

## File Structure

All paths relative to `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/` (main) and `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/` (test) unless prefixed with `test-plugin/` or `docs/`.

**New public API:**

| File | Responsibility |
|---|---|
| `pagination/Pagination.java` | the reactive pagination token (§5.7) — NEW interface at the FQN vacated by the deleted 2.x interface |
| `pagination/PaginationBuilder.java` | fluent declaration returned by `View.paginate*` (§5.7) |
| `pagination/PaginationItemRenderer.java` | per-element renderer callback (§5.7) |

**Modified public API:**

| File | Change |
|---|---|
| `View.java` | adds the four `paginate*` factories (§5.2) |
| `pagination/source/PageRequest.java` | REWRITTEN slim: `page`/`pageSize`/`offset` + `@Nullable UUID playerId()` + `@Nullable Plugin plugin()`; `Viewer` gone (§5.8) |
| `pagination/source/BukkitSettleDispatcher.java` | REWRITTEN: `tickAsync` consultation deleted; plugin read from the request; null plugin or primary thread → inline (§5.8) |
| `pagination/source/AsyncPageSource.java` | adds `@ApiStatus.Internal public static void shutdownSharedTimeoutScheduler()` — everything else untouched |
| `pagination/source/PaginationErrorCallback.java` | Javadoc only: the "unless the owning inventory opted into async ticking" clause is deleted with the `tickAsync` cut — callbacks now always run on the main thread for engine-dispatched settles |
| `InventoryApiModule.java` | adds `@OnDisable` hook shutting the shared timeout scheduler down |
| `editor/InventoryEditor.java`, `editor/impl/InventoryEditorImpl.java` | `fillPage(...)` REMOVED (only callers were the relocated engines) |

**Deleted (Task 5 switchover):** `pagination/Pagination.java` (2.x interface), `pagination/impl/{AbstractPageSourcePagination, NormalPagination, ScrollPagination, PatternPagination}.java`, `pagination/builder/{NormalPaginationBuilder, ScrollPaginationBuilder, PatternPaginationBuilder, AsyncPaginationOptions}.java`, test classes `pagination/impl/{NormalPaginationTest, ScrollPaginationTest, PatternPaginationTest, AsyncPaginationIntegrationTest}.java`, `pagination/builder/AsyncPaginationOptionsTest.java`, test-plugin `inventory/{SampleNormalPagedInventory, SamplePagedInventory, SamplePatternPagedInventory, SampleAsyncPagedInventory}.java` (re-created in Task 14).

**New internal (all `@ApiStatus.Internal`):**

| File | Responsibility |
|---|---|
| `internal/pagination/PaginationHost.java` | the adapter seam the relocated engine talks to (§10 + scope note 2) |
| `internal/pagination/RenderedItem.java` | one slot's worth of engine output: element builder, plain item, or failure sentinel |
| `internal/pagination/PaginationSpec.java` | immutable declaration (geometry, target, renderer, options) built by the builder |
| `internal/pagination/PaginationSourceSpec.java` | source declaration: eager-static / eager-lazy / async / custom + per-context source construction |
| `internal/pagination/PaginationBuilderImpl.java` | `PaginationBuilder` implementation + build-time validation |
| `internal/pagination/PaginationImpl.java` | the token implementation (`Pagination<T>` + `IdentifiableToken`) |
| `internal/pagination/PaginationBinding.java` | per-(session, token) runtime: pending nav, engine, slot map, element components; implements `PaginationHost` |
| `internal/pagination/PaginationBindings.java` | static lookup helpers over a session's bindings |
| `internal/pagination/engine/Paginator.java` | renamed ex-2.x `Pagination` interface, host seam applied |
| `internal/pagination/engine/PageItemFactory.java` | `(index, value) → RenderedItem` element factory the binding supplies |
| `internal/pagination/engine/AbstractPageSourcePagination.java` | relocated skeleton: navigation transaction, settle handling, rollback, paint frames — algorithms verbatim |
| `internal/pagination/engine/NormalPagination.java` | relocated; geometry math verbatim |
| `internal/pagination/engine/ScrollPagination.java` | relocated; geometry math verbatim |
| `internal/pagination/engine/PatternPagination.java` | relocated; geometry math + clearPattern verbatim |
| `internal/engine/FlushCoordinator.java` | extracted flush machinery (flushDirty cascade, shared-flush coalescing, hook wiring) |
| `internal/engine/phase/PaginationInitPhase.java` | §7 step 7: per-token source+engine construction between layout resolution and onFirstRender |
| `internal/engine/phase/OpenFailureHandler.java` | extracted OPEN_FAILED abort path shared by FirstRenderPhase and PaginationInitPhase |

**Modified internal:**

| File | Change |
|---|---|
| `internal/engine/ViewEngine.java` | delegates flush machinery to `FlushCoordinator`; adds `paginationSettle(session, tokenId)` + `painter()` accessor; `open()` calls `PaginationInitPhase` between open and first render |
| `internal/engine/phase/OpenPhase.java` | creates `PaginationBinding`s into the `StateStore` before `onOpen` (pending-nav recording target) |
| `internal/engine/phase/UpdatePhase.java` | gate admits PAGINATION_SETTLE for ACTIVE/TRANSITIONING; full pass repaints pagination areas; scoped pass repaints dirty-token areas + element watchers |
| `internal/engine/phase/ClickRoutingPhase.java` | component lookup falls back to pagination element components |
| `internal/engine/phase/FirstRenderPhase.java` | paints pagination areas after static components; validates static/pagination slot overlap; unbound-layout-char WARNING (§5.3); uses `OpenFailureHandler` |
| `internal/registry/ViewRegistry.java` | registration-time `layoutChar` validation (§5.3) |
| `internal/state/ContextStateAccess.java` | widened to public; gains consolidated `storeFor(context, owner)` guard (backlog #6) |
| `internal/state/{MutableStateImpl, LazyStateImpl, InitialStateImpl}.java` | use the consolidated guard |
| `internal/context/RenderContextImpl.java` | records which layout chars `layoutSlot(...)` bound (warning input) |

**test-plugin:** `listener/JoinListener.java` modified twice (Task 5 removes dead sample wiring; Task 14 rewires to `ViewService` + new views); `inventory/{SampleScrollView, SampleNormalView, SamplePatternView, SampleAsyncView}.java` created in Task 14.

**Docs:** spec §5.4 `update()` comment amended (Task 15); `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-3-notes.md` created (Task 15).

**Explicitly NOT in this plan:** deleting any non-pagination 2.x type (Plan 3), attribution README/NOTICE/package-info (Plan 3), version bump to 3.0.0 (Plan 3), migration table (Plan 3), `ViewerPropertyMap`/`CustomInventoryImpl`/`InventoryService` changes (Plan 3).

---

## Shared Type Contracts

Every task MUST use these exact signatures. A task that needs a method not listed here is wrong — go back to this section. (Bodies are defined in the tasks; this section pins names and shapes. `@NotNull`/`@Nullable` are `org.jetbrains.annotations`.)

### Public

```java
// pagination/Pagination.java — NEW token interface (replaces the deleted 2.x interface at this FQN)
@ApiStatus.NonExtendable
public interface Pagination<T> extends StateToken {
    int currentPage(@NotNull ViewContext context);          // 1-indexed; the pending target before init
    int totalPages(@NotNull ViewContext context);           // >= 1; 1 before init / before totals known
    int totalElements(@NotNull ViewContext context);        // 0 before init / before totals known
    boolean canAdvance(@NotNull ViewContext context);
    boolean canBack(@NotNull ViewContext context);
    void advance(@NotNull ViewContext context);             // main thread; pre-init records a pending target
    void back(@NotNull ViewContext context);                // main thread; pre-init records a pending target
    void switchTo(@NotNull ViewContext context, int page);  // main thread; clamped exactly as 2.x changePage
    boolean isLoading(@NotNull ViewContext context);        // always false for eager sources
    @Nullable Throwable lastError(@NotNull ViewContext context);
    void refresh(@NotNull ViewContext context);             // main thread; pre-init no-op; lazy: re-invokes the
                                                            // source function; async: invalidates the cache;
                                                            // then re-requests the current page (forced)
}

// pagination/PaginationBuilder.java
@ApiStatus.NonExtendable
public interface PaginationBuilder<T> {
    @NotNull PaginationBuilder<T> layoutChar(char character);            // default 'O' when never called
    @NotNull PaginationBuilder<T> layout(@NotNull Layout layout);        // explicit fill order; overrides layoutChar
    @NotNull PaginationBuilder<T> scroll();                              // sliding-window geometry
    @NotNull PaginationBuilder<T> patterns(@NotNull Layout... patterns); // per-page slot patterns, cycled
    @NotNull PaginationBuilder<T> itemRenderer(@NotNull PaginationItemRenderer<T> renderer);  // required
    @NotNull PaginationBuilder<T> fallbackItem(@NotNull Function<ViewContext, ItemStack> item);
    // async-only options — ViewConfigurationException at build() on a non-async builder
    @NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item);
    @NotNull PaginationBuilder<T> onError(@NotNull PaginationErrorCallback callback);
    @NotNull PaginationBuilder<T> requestTimeout(@NotNull Duration timeout);   // IllegalArgumentException unless positive
    @NotNull PaginationBuilder<T> cacheTtl(@NotNull Duration ttl);             // IllegalArgumentException unless positive
    @NotNull PaginationBuilder<T> cacheMaxPages(int maxPages);                 // IllegalArgumentException when < 1; requires cacheTtl at build()
    @NotNull Pagination<T> build();                                            // registers the token; IllegalStateException on second call
}

// pagination/PaginationItemRenderer.java
@FunctionalInterface
public interface PaginationItemRenderer<T> {
    // index = ZERO-BASED position of the element within the CURRENT page (not the global element index)
    void render(@NotNull ViewContext context, @NotNull ItemComponentBuilder item, int index, @NotNull T value);
}

// View.java — ADDED factories (all protected final, legal only in field initializers/constructor,
// IllegalStateException from TokenTable.register after freeze; builder construction itself never registers —
// only build() does)
protected final <T> PaginationBuilder<T> paginate(@NotNull List<T> source);                       // defensive copy → ONE shared EagerPageSource
protected final <T> PaginationBuilder<T> paginate(@NotNull Function<ViewContext, List<T>> source); // lazy eager: fn runs once per context at init
protected final <T> PaginationBuilder<T> paginateAsync(@NotNull AsyncPageSupplier<T> source);      // fresh AsyncPageSource per context at init
protected final <T> PaginationBuilder<T> paginateSource(@NotNull Function<ViewContext, PageSource<T>> factory); // escape hatch, once per context

// pagination/source/PageRequest.java — REWRITTEN (no Lombok; hand-written accessors)
public final class PageRequest {
    public PageRequest(int page, int pageSize, int offset, @Nullable UUID playerId, @Nullable Plugin plugin);
    public int getPage();                 // 1-indexed, informational only (scroll offsets are not (page-1)*pageSize)
    public int getPageSize();
    public int getOffset();               // authoritative query bound: LIMIT pageSize OFFSET offset
    public @Nullable UUID playerId();     // null only for engine-external test usage
    public @Nullable Plugin plugin();     // null only for engine-external test usage
}

// pagination/source/BukkitSettleDispatcher.java — REWRITTEN
public final class BukkitSettleDispatcher implements SettleDispatcher {
    @Override public void dispatch(PageRequest request, Runnable task);
    // request.plugin() == null || Bukkit.isPrimaryThread() → task.run() inline;
    // else Bukkit.getScheduler().runTask(request.plugin(), task), catching IllegalPluginAccessException →
    // WARNING "Dropped a page-load settle: the owning plugin is disabled." (settle dropped).
    // The tickAsync consultation is DELETED. FIFO + disable-drop Javadoc contract preserved.
}

// pagination/source/AsyncPageSource.java — ADDED (everything else byte-for-byte untouched)
@ApiStatus.Internal
public static void shutdownSharedTimeoutScheduler();
// synchronized (AsyncPageSource.class): if non-null → shutdownNow() + null the field; lazy init recreates on next use
```

### Internal — relocated engine (`internal/pagination/engine/`)

```java
// Paginator.java — ex pagination/Pagination.java, renamed (the public token now owns that name)
@ApiStatus.Internal
public interface Paginator<T> {
    void bind(@NotNull PaginationHost host);    // ex init(Viewer): host set → initNavigationState() → dispatch(snapshot, render=false)
    void insertPageItems();                     // paints the current frame via host.fillPage (ex apply()/insertPageItems())
    void changePage(int page);                  // clamp/dedupe/unbound-record/snapshot-commit-dispatch — 2.x verbatim
    void nextPage();
    boolean hasNextPage();                      // currentPage + 1 <= getTotalPages()
    void previousPage();
    boolean hasPreviousPage();                  // currentPage > 1
    int getTotalPages();
    int getCurrentPage();
    int getTotalElements();
    int getItemPageLimit();
    int getPageOfIndex(int index);              // internal-only survivor (public cut per §11; geometry regression value)
    boolean isLoading();
    @Nullable Throwable lastError();
    void refresh();                             // pageSource.invalidate() + changePageInternal(currentPage, force=true) — 2.x verbatim
    void replaceSource(@NotNull PageSource<T> source); // ex setSource(List): swap source + clear currentItems;
                                                       // NO dispatch, NO async warning (callers drive the re-request)
}

// PageItemFactory.java
@FunctionalInterface
public interface PageItemFactory<T> {
    @NotNull RenderedItem create(int index, @NotNull T value);  // index = position within the current page
}

// AbstractPageSourcePagination.java — relocated skeleton, PACKAGE-PRIVATE, algorithms verbatim with these substitutions:
//   Viewer viewer            → PaginationHost host
//   InventoryItemSupplier    → Supplier<RenderedItem> (fallbackItem, loadingItem; both @Nullable)
//   GenericInventoryItemSupplier<T> → PageItemFactory<T> itemFactory (itemFactory.create(i, items.get(i)))
//   InventoryLayout          → Layout (getSlots().size() → slots().size())
//   editor.fillPage(items, layout, this) → host.fillPage(items, layout)
//   renderIfOnline()         → { PaginationHost h = this.host; if (h == null || !h.isActive()) return; h.requestRender(); }
//   new PageRequest(page, limit, offset, viewer) → new PageRequest(page, limit, offset, host.playerId(), host.plugin())
//   emptyOrFallback(): fallbackItem == null ? RenderedItem.ofItem(null) : fallbackItem.get()
//   loadingOrFallback(): loadingItem != null ? loadingItem.get() : emptyOrFallback()
// PRESERVED VERBATIM: changePageInternal (incl. unbound-host record-only branch), dispatch (incl.
// volatile dispatchingThread inline-settle detection), onSettle (incl. rollback + downward re-clamp +
// WARNING catch), volatile currentItems, the navigation snapshot/commit/restore abstract hooks.
@Getter
abstract class AbstractPageSourcePagination<T, S> implements Paginator<T> {
    AbstractPageSourcePagination(@Nullable Supplier<RenderedItem> fallbackItem,
                                 @NotNull PageItemFactory<T> itemFactory,
                                 @Nullable Supplier<RenderedItem> loadingItem,
                                 @NotNull PageSource<T> pageSource);
    protected abstract void initNavigationState();
    protected abstract int requestOffset();
    protected abstract S navigationSnapshot();
    protected abstract void restoreNavigation(S snapshot);
    protected abstract void commitNavigation(int target);
    protected abstract Layout renderLayout();
    protected final @NotNull RenderedItem emptyOrFallback();
    protected final @NotNull RenderedItem loadingOrFallback();
}

// NormalPagination / ScrollPagination / PatternPagination — relocated PUBLIC classes, geometry verbatim:
public class NormalPagination<T> extends AbstractPageSourcePagination<T, Integer>
    public NormalPagination(@Nullable Supplier<RenderedItem> fallbackItem, @NotNull PageItemFactory<T> itemFactory,
                            @NotNull Layout layout)                                    // eager convenience: EagerPageSource.empty()
    public NormalPagination(@Nullable Supplier<RenderedItem> fallbackItem, @NotNull PageItemFactory<T> itemFactory,
                            @NotNull Layout layout, @Nullable Supplier<RenderedItem> loadingItem,
                            @NotNull PageSource<T> pageSource)
public class ScrollPagination<T> extends AbstractPageSourcePagination<T, Integer>      // same two ctor shapes
public class PatternPagination<T> extends AbstractPageSourcePagination<T, PatternPagination.PatternState>
    public PatternPagination(@Nullable Supplier<RenderedItem> fallbackItem, @NotNull PageItemFactory<T> itemFactory,
                             @NotNull List<Layout> patterns)
    public PatternPagination(@Nullable Supplier<RenderedItem> fallbackItem, @NotNull PageItemFactory<T> itemFactory,
                             @NotNull List<Layout> patterns, @Nullable Supplier<RenderedItem> loadingItem,
                             @NotNull PageSource<T> pageSource)
    // nested: static final class PatternState — page, currentPattern (Layout), lastPattern (Layout), itemPageLimit
```

### Internal — new machinery (`internal/pagination/`)

```java
// PaginationHost.java
@ApiStatus.Internal
public interface PaginationHost {
    boolean isActive();                          // paintable: session status ACTIVE or TRANSITIONING
    void fillPage(@NotNull List<RenderedItem> items, @NotNull Layout layout);  // item i → layout.slots().get(i)
    void requestRender();                        // → ViewEngine.paginationSettle(session, tokenId)
    @Nullable UUID playerId();                   // populates the slim PageRequest (§5.8)
    @NotNull Plugin plugin();
}

// RenderedItem.java — immutable
@ApiStatus.Internal
public final class RenderedItem {
    public static @NotNull RenderedItem ofElement(@NotNull ItemComponentBuilderImpl builder); // user-declared page element
    public static @NotNull RenderedItem ofItem(@Nullable ItemStack item);                     // frame item; null = clear slot
    public static @NotNull RenderedItem failure();                                            // keep previous slot content
    public @Nullable ItemComponentBuilderImpl elementBuilder();
    public @Nullable ItemStack plainItem();
    public boolean isFailure();
}

// PaginationSpec.java — immutable; built by PaginationBuilderImpl.build()
// PINNED constructor (package-private, all-args, in accessor declaration order — Tasks 6/7/8 must agree):
// PaginationSpec(Geometry geometry, Target target, char layoutChar, @Nullable Layout explicitLayout,
//                List<Layout> patterns, PaginationItemRenderer<T> renderer,
//                @Nullable Function<ViewContext, ItemStack> fallbackItem,
//                @Nullable Function<ViewContext, ItemStack> loadingItem,
//                PaginationSourceSpec<T> source, @Nullable PaginationErrorCallback errorCallback,
//                @Nullable Duration requestTimeout, @Nullable Duration cacheTtl, int cacheMaxPages)
@ApiStatus.Internal
public final class PaginationSpec<T> {
    public enum Geometry { NORMAL, SCROLL, PATTERN }
    public enum Target { LAYOUT_CHAR, EXPLICIT_LAYOUT, PATTERNS }
    public @NotNull Geometry geometry();
    public @NotNull Target target();
    public char layoutChar();                                  // meaningful only when target == LAYOUT_CHAR; default 'O'
    public @Nullable Layout explicitLayout();
    public @NotNull List<Layout> patterns();                   // unmodifiable; empty unless target == PATTERNS
    public @NotNull PaginationItemRenderer<T> renderer();
    public @Nullable Function<ViewContext, ItemStack> fallbackItem();
    public @Nullable Function<ViewContext, ItemStack> loadingItem();
    public @NotNull PaginationSourceSpec<T> source();
    // async plumbing (consumed by PaginationSourceSpec.createSource):
    public @Nullable PaginationErrorCallback errorCallback();
    public @Nullable Duration requestTimeout();
    public @Nullable Duration cacheTtl();
    public int cacheMaxPages();                                // default 128 (2.x DEFAULT_CACHE_MAX_PAGES)
}

// PaginationSourceSpec.java — immutable; one of four kinds
@ApiStatus.Internal
public final class PaginationSourceSpec<T> {
    public enum Kind { EAGER_STATIC, EAGER_LAZY, ASYNC, CUSTOM }
    public static <T> @NotNull PaginationSourceSpec<T> eager(@NotNull List<T> source);          // builds the ONE shared EagerPageSource now
    public static <T> @NotNull PaginationSourceSpec<T> lazy(@NotNull Function<ViewContext, List<T>> source);
    public static <T> @NotNull PaginationSourceSpec<T> async(@NotNull AsyncPageSupplier<T> supplier);
    public static <T> @NotNull PaginationSourceSpec<T> custom(@NotNull Function<ViewContext, PageSource<T>> factory);
    public @NotNull Kind kind();
    public boolean isAsync();                                  // kind == ASYNC
    public @Nullable Function<ViewContext, List<T>> lazyFunction();  // non-null only for EAGER_LAZY (refresh path)
    public @NotNull PageSource<T> createSource(@NotNull ViewContext context, @NotNull PaginationSpec<T> spec);
    // EAGER_STATIC → the shared instance; EAGER_LAZY → new EagerPageSource<>(requireNonNull(fn.apply(context)));
    // ASYNC → new AsyncPageSource<>(supplier, spec.errorCallback(), spec.requestTimeout(), spec.cacheTtl(),
    //                               spec.cacheMaxPages(), new BukkitSettleDispatcher());
    // CUSTOM → requireNonNull(factory.apply(context))
}

// PaginationBuilderImpl.java
@ApiStatus.Internal
public final class PaginationBuilderImpl<T> implements PaginationBuilder<T> {
    public PaginationBuilderImpl(@NotNull View owner, @NotNull TokenTable table, @NotNull PaginationSourceSpec<T> source);
    // build(): validations (pinned rule 12) → new PaginationImpl<>(owner, table, spec) → marks built;
    // second build() → IllegalStateException("build() may only be called once per paginate* call")
}

// PaginationImpl.java
@ApiStatus.Internal
public final class PaginationImpl<T> implements Pagination<T>, IdentifiableToken {
    public PaginationImpl(@NotNull View owner, @NotNull TokenTable table, @NotNull PaginationSpec<T> spec);
    public int id();                  // TokenTable-assigned
    @Override public int tokenId();   // same value
    public @NotNull PaginationSpec<T> spec();
    // every Pagination method: ContextStateAccess.storeFor(context, owner) guard (foreign/closed →
    // StaleContextException, exact existing messages) → binding = (PaginationBinding) store.get(id)
    // (null → IllegalStateException("pagination binding missing for " + owner.getClass().getName()))
    // → delegate per the pre/post-init table
    // (pinned rule 6). Mutators additionally ThreadUtils.assertMainThread("Pagination.<method>").
}

// PaginationBinding.java — per (session, token); stored in the token's StateStore slot.
// NOTE: deliberately does NOT reference PaginationImpl (dependency direction: PaginationImpl → binding);
// it works on the spec + token id. Internally uses documented unchecked casts to pair the spec's T with
// the paginator's T. Evaluates renderer/fallback/loading/lazy functions against a PlainViewContextImpl
// it creates at initialize time.
@ApiStatus.Internal
public final class PaginationBinding implements PaginationHost {
    public PaginationBinding(@NotNull PaginationSpec<?> spec, int tokenId,
                             @NotNull ViewSession session, @NotNull ViewEngine engine);
    // --- pre-init pending navigation (consumed once at initialize) ---
    public int pendingTarget();                                // starts 1
    public void recordSwitchTo(int target);                    // stores max(1, target); marks navigation recorded
    public boolean hasPendingNavigation();
    // --- init (PaginationInitPhase) ---
    public void initialize(@NotNull ResolvedLayout viewLayout, @NotNull ViewConfig effectiveConfig);
    // resolve target slots → build per-context source → construct geometry engine → replay pending nav
    // via paginator.changePage(pendingTarget) on the UNBOUND paginator (preserved 2.x record-only branch)
    // → paginator.bind(this). Throws ViewConfigurationException on bounds/empty-layout violations.
    public boolean isInitialized();
    public @Nullable Paginator<?> paginator();
    // --- runtime ---
    public @NotNull int[] targetSlots();                       // resolved fill slots: empty before initialize;
                                                               // NORMAL/SCROLL → the fill layout's slots;
                                                               // PATTERN → the union of all pattern slots
                                                               // (overlap validation input, FirstRenderPhase)
    public @Nullable ComponentInstance componentAt(int slot);  // current page element component, if any
    public void repaint();                                     // full area re-render: paginator.insertPageItems()
    public @NotNull List<ComponentInstance> elementWatchersOf(@NotNull Set<Integer> dirtyTokenIds);
    public int tokenId();
    public void refreshLazy(@NotNull ViewContext context);     // EAGER_LAZY refresh: replaceSource(new EagerPageSource<>(fn.apply(ctx))) + paginator.refresh()
    // --- PaginationHost ---
    // isActive() → session.status() == ACTIVE || TRANSITIONING
    // fillPage(items, layout) → per pinned rule 8 (slot mapping, element materialization, failure semantics)
    // requestRender() → engine.paginationSettle(session, tokenId)
    // playerId() → session.player().getUniqueId(); plugin() → engine.plugin()
}

// PaginationBindings.java — static helpers (no instances)
@ApiStatus.Internal
public final class PaginationBindings {
    public static @NotNull List<PaginationBinding> of(@NotNull ViewSession session);
    // iterate session.registered().instance().tokenTable().tokens(); for each instanceof PaginationImpl,
    // read (PaginationBinding) session.stateStore().get(tokenId); skip nulls
    public static @Nullable ComponentInstance componentAt(@NotNull ViewSession session, int slot);
}
```

### Internal — engine changes

```java
// internal/engine/FlushCoordinator.java — PACKAGE-PRIVATE, extracted from ViewEngine unchanged in behavior:
// flushDirty loop + CASCADE_CAP(8) + warn-and-drop, flushShared public+private overloads,
// wireSharedFlush (sharedFlushPending re-entrancy set + sharedFlushScheduled ConcurrentHashMap.compute
// coalescing), exact current bodies. ViewEngine keeps its public flushDirty/flushShared/wireSharedFlush
// signatures, delegating (main-thread asserts stay in ViewEngine).
final class FlushCoordinator {
    FlushCoordinator(@NotNull Plugin plugin, @NotNull SessionRegistry sessions, @NotNull UpdatePhase updatePhase);
    void flushDirty(@NotNull ViewSession session);
    void flushShared(@NotNull View owner);
    void wireSharedFlush(@NotNull RegisteredView registered);
}

// internal/engine/ViewEngine.java — constructor signature UNCHANGED; additions:
public void paginationSettle(@NotNull ViewSession session, int tokenId);
// ThreadUtils.assertMainThread("ViewEngine.paginationSettle");
// status CLOSED or OPENING → return;   (ACTIVE/TRANSITIONING delivered)
// updatePhase.update(session, UpdateTrigger.PAGINATION_SETTLE, Collections.singleton(tokenId));
// flushDirty(session);
public @NotNull SlotPainter painter();
// open(): ... session = openPhase.openSession(...); if (session == null) return;
//         if (!paginationInitPhase.init(session)) return;     // failed → already aborted OPEN_FAILED
//         firstRenderPhase.firstRender(session);

// internal/engine/phase/PaginationInitPhase.java — public @ApiStatus.Internal (ViewEngine lives in a
// sibling package and must reference it — same visibility as the existing OpenPhase/UpdatePhase classes)
public final class PaginationInitPhase {
    public PaginationInitPhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions);
    public boolean init(@NotNull ViewSession session);
    // for each PaginationBindings.of(session): binding.initialize(session.layout(), session.effectiveConfig());
    // RuntimeException → SEVERE log naming the view class + OpenFailureHandler.abort(...) → false; else true
}

// internal/engine/phase/OpenFailureHandler.java — package-private static helper, extracted from
// FirstRenderPhase's catch block (engine.close(session, OPEN_FAILED) + online-player orphan-container
// closeInventory guard); used by FirstRenderPhase and PaginationInitPhase
final class OpenFailureHandler {
    static void abort(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions, @NotNull ViewSession session);
}

// internal/engine/phase/OpenPhase.java — ADDED step after bindInitialState, before onOpen:
// for each PaginationImpl token of the view:
//   store.set(tokenId, new PaginationBinding(token.spec(), token.tokenId(), session, engine))

// internal/engine/phase/UpdatePhase.java — gate change + repaint extension:
// gate: CLOSED → skip all (unchanged); non-ACTIVE → deliver STATE_CHANGE (unchanged) AND deliver
//   PAGINATION_SETTLE when status == TRANSITIONING (OPENING still skipped);
// repaint full pass (dirtyOrNull == null): static components (unchanged), then every initialized
//   binding.repaint();
// repaint scoped pass: watchersOf(dirty) (unchanged), then bindings with tokenId ∈ dirty → repaint(),
//   then per binding elementWatchersOf(dirty) → renderForPaint repaint (RENDER_FAILURE skip, same loop shape)

// internal/engine/phase/ClickRoutingPhase.java — component resolution becomes:
// ComponentInstance component = session.components().componentAt(slot);
// if (component == null) component = PaginationBindings.componentAt(session, slot);
// (everything downstream unchanged — element components behave exactly like static ones)

// internal/engine/phase/FirstRenderPhase.java — three additions:
// 1. after materializeAll(): for each binding slot occupied by a static component →
//    ViewConfigurationException("slot N is bound to both a component and pagination") → existing abort path
// 2. unbound-layout-char WARNING (§5.3): layout chars bound by neither layoutSlot() declarations
//    (RenderContextImpl.boundLayoutChars()) nor LAYOUT_CHAR pagination targets → ONE warning per view class
//    (static ConcurrentHashMap.newKeySet of view classes)
// 3. paintAll(): after static components, for each initialized binding → binding.repaint()
//    (eager items ready pre-show; async paints the loading frame)

// internal/registry/ViewRegistry.java — register(): after builder.build(), for each PaginationImpl token:
// target == LAYOUT_CHAR → config.layout() empty → ViewConfigurationException("view X declares pagination on
// layout char 'c' but has no layout"); char absent (use ResolvedLayout.resolve(config).hasChar(c)) →
// ViewConfigurationException("pagination layout char 'c' is not present in the layout of view X")

// internal/state/ContextStateAccess.java — widened to PUBLIC @ApiStatus.Internal; ADDS:
public static @NotNull StateStore storeFor(@NotNull ViewContext context, @NotNull View owner);
// the exact storeFor guard currently triplicated in MutableStateImpl/LazyStateImpl/InitialStateImpl
// (foreign owner → StaleContextException("state token of <owner> used with a context of <other>"),
// closed → StaleContextException("context of <owner> is closed")) — message strings preserved verbatim;
// the three impls refactor onto it; PaginationImpl uses it

// internal/context/RenderContextImpl.java — ADDS (public: the consumer FirstRenderPhase lives in
// internal.engine.phase, a different package; the class is @ApiStatus.Internal anyway):
public @NotNull Set<Character> boundLayoutChars();   // chars passed to layoutSlot(...) during onFirstRender

// InventoryApiModule.java — ADDS:
@OnDisable
public void onDisable() { AsyncPageSource.shutdownSharedTimeoutScheduler(); }
```

### Pinned behavioral rules (from spec + 2.x sources, restated for drafters)

1. **Geometry math preserved EXACTLY** (regression tests must assert the numbers):
   normal `requestOffset = (currentPage - 1) * itemPageLimit`, `totalPages = total == 0 ? 1 : (int) (((long) total + limit - 1) / limit)` (long ceil-div), `pageOfIndex = index / limit + 1`;
   scroll `requestOffset = currentPage - 1` (window slides ONE element per page), `totalPages = max(1, (total - limit) + 1)`, `pageOfIndex = max(1, index - limit + 2)`;
   pattern cycle arithmetic (`cycleSize` = Σ pattern slot counts, `fromPage = patterns.get((page - 1) % patterns.size())`, `getPageIndex` cumulative walk) verbatim. `itemPageLimit` = `renderLayout().slots().size()`, set in `initNavigationState()` and (pattern only) `commitNavigation`/`restoreNavigation`.
2. **Navigation transaction (2.x verbatim, ONE deliberate reorder):** `changePageInternal(page, force)`: **the unbound-host record-only branch moves to the TOP** — `if (host == null) { currentPage = max(1, page); return; }` — BEFORE the totals-known clamp and the in-flight dedupe. Rationale: the 2.x order calls `getTotalPages()` before the unbound check; pre-bind `itemPageLimit` is 0, so a pending-target replay over a non-empty eager source would divide by zero in NORMAL geometry (latent 2.x bug never hit because pre-init sources were always empty). The reorder is observably behavior-preserving: pre-bind there is never an in-flight request (dedupe is a no-op) and an overshooting recorded target is corrected by the settle's downward re-clamp — exactly the documented "honored optimistically and re-clamped downward when totals arrive" contract. Bound path unchanged: `target = max(1, page)`; upper-clamp to `getTotalPages()` ONLY when `pageSource.totalsKnown()`; skip when `!force && target == currentPage && pageSource.isLoading()`; else `S rollback = navigationSnapshot(); commitNavigation(target); dispatch(rollback, true)`.
3. **Settle handling (2.x verbatim):** `dispatch` sets volatile `dispatchingThread` around `pageSource.request(...)` (inline-settle detection — single-dispatcher assumption documented); `onSettle`: error → `restoreNavigation(rollback)` (pattern clears the FAILED pattern's slots first); success → `currentItems = result.getItems()`, then downward re-clamp `if (totalsKnown() && currentPage > getTotalPages()) { changePageInternal(getTotalPages(), true); return; }`; render only when `!inline`; whole body wrapped in catch-all → `Level.WARNING "Failed to apply a settled page load."`. **Preserved quirk (NEVER "fix"):** rapid advance→advance→fail rolls back to the last *requested* page, not the last rendered one.
4. **bind:** `host` set → `initNavigationState()` → `dispatch(navigationSnapshot(), false)` — render=false; the open's initial paint covers it. Eager sources settle inline during init (items ready before first paint); async sources leave `isLoading() == true` so the first paint shows the loading frame.
5. **requestRender → reactive settle pass:** `renderIfOnline` equivalent = `host == null || !host.isActive()` → no-op; else `host.requestRender()` → `ViewEngine.paginationSettle(session, tokenId)`: gate (rule below) → `UpdatePhase` PAGINATION_SETTLE pass scoped to the token singleton (repaints the pagination area + every component watching the token, fires `onUpdate(PAGINATION_SETTLE)`) → `flushDirty(session)`. Gate: ACTIVE and TRANSITIONING delivered; CLOSED and OPENING dropped (close-during-async-load = no paint, no watcher marking, no onUpdate — §7). TRANSITIONING delivery caveat: the pass runs and `onUpdate(PAGINATION_SETTLE)` fires, but context-reading renderers (elements and watchers alike) hit the existing TRANSITIONING semantics — `contextActive()` is false, state reads throw `StaleContextException`, `renderForPaint` returns RENDER_FAILURE and the slot keeps its previous content until the next ACTIVE pass; tests assert delivery (onUpdate + no throw), not fresh content. Identical to how STATE_CHANGE flushes already behave on TRANSITIONING sessions (Plan 1 approved).
6. **PaginationImpl pre/post-init delegation table:**

   | Method | pre-init (`!binding.isInitialized()`) | post-init |
   |---|---|---|
   | `currentPage` | `binding.pendingTarget()` | `getCurrentPage()` |
   | `totalPages` | `1` | `getTotalPages()` |
   | `totalElements` | `0` | `getTotalElements()` |
   | `canAdvance` | `false` | `hasNextPage()` |
   | `canBack` | `pendingTarget() > 1` | `hasPreviousPage()` |
   | `advance` | `recordSwitchTo(pendingTarget() + 1)` | `nextPage()` |
   | `back` | `recordSwitchTo(pendingTarget() - 1)` | `previousPage()` |
   | `switchTo(p)` | `recordSwitchTo(p)` | `changePage(p)` |
   | `isLoading` | `false` | `isLoading()` |
   | `lastError` | `null` | `lastError()` |
   | `refresh` | no-op | lazy: `binding.refreshLazy(ctx)`; else `paginator().refresh()` |

   All methods run the `ContextStateAccess.storeFor(context, owner)` guard; mutators assert the main thread BEFORE the guard (matching `MutableStateImpl.set` — off-main misuse reports `IllegalStateException` even on a foreign context).
7. **Element semantics:** the user `PaginationItemRenderer` runs once per element per AREA repaint (fresh `ItemComponentBuilderImpl` each time, materialized into a single-slot `ComponentInstance` at fill time); renderer throw OR missing item source → rate-limited SEVERE (1/min per binding) + `RenderedItem.failure()` → the slot keeps its previous content ENTIRELY (item + click handlers); on first paint with no previous content the fallback item (or clear) is painted instead. `displayIf`/`updateOnStateChange`/`onClick`/`cancelOnClick`/`closeOnClick`/`openOnClick` on element builders behave exactly as on static components. Element-watcher repaints re-evaluate `renderForPaint` only (item function), NOT the full renderer.
8. **fillPage mapping:** item `i` → `layout.slots().get(i)`; the engine always builds exactly `renderLayout().slots().size()` items; loop bound = `min(items.size(), slots.size())` defensively. Frame items (`ofItem`) paint the plain stack (null clears) and REMOVE any element component on that slot; `ofElement` materializes `builder.materialize(new int[]{slot})`, paints via `renderForPaint` semantics (RENDER_FAILURE → keep previous), and replaces the slot's element component; `failure()` → keep previous mapping and content (fallback on first paint). Track `lastApplied` per slot for the first-paint decision.
9. **Threading:** `advance`/`back`/`switchTo`/`refresh` assert main thread (`ThreadUtils.assertMainThread`); reads are unasserted (coherent on main only); settles always land on main via the rewritten dispatcher; `PageRequest` is immutable and any-thread.
10. **Open-order errors:** `PaginationInitPhase` failure (lazy fn throw, custom factory throw/null, bounds violation, empty explicit layout) → SEVERE log naming the view class + `OpenFailureHandler.abort` (close OPEN_FAILED; orphan-container guard; session never registered) — mirrors `onFirstRender` throw handling (§9). Registration-time `layoutChar` violations → `ViewConfigurationException` at boot (§5.3).
11. **Per-context isolation (§14 test 8):** every context gets its own engine + source (except the SHARED immutable `EagerPageSource` of `paginate(List)`); two players paginating concurrently share NOTHING mutable (request ids, cache, currentItems, navigation are all per-instance).
12. **Builder validation at `build()`:** missing renderer → `ViewConfigurationException`; `patterns(...)` combined with explicit `layoutChar(...)` call, `layout(...)`, or `scroll()` → `ViewConfigurationException`; empty `patterns` array, any empty pattern layout, or empty explicit `layout(...)` → `ViewConfigurationException`; async-only options (`loadingItem`/`onError`/`requestTimeout`/`cacheTtl`/`cacheMaxPages`) on a non-async source → `ViewConfigurationException`; `cacheMaxPages` without `cacheTtl` → `ViewConfigurationException`; value errors at SETTER time → `IllegalArgumentException` (non-positive durations, `cacheMaxPages < 1`, nulls → NPE via requireNonNull). `layout(...)` overrides `layoutChar(...)` silently (spec'd). Defaults: layoutChar `'O'`, geometry NORMAL, cacheMaxPages 128.
13. **Style:** comments start lowercase; full Javadoc on public/protected API (`@param`/`@return`/`@throws`); no fully-qualified inline types; 4-space indent; license header on every new file; SOLID (each new class has the one responsibility named in File Structure).
14. **MockBukkit:** `MockBukkit.mock()`/`unmock()` per test; `unmock()` drains pending scheduler tasks — tests that queue deferred ops they don't want to run MUST `server.getScheduler().cancelTasks(plugin)` first (see `ContextPhaseValidityTest` tearDown); drive settles with `server.getScheduler().performOneTick()`.

---

## Task summaries (bodies follow; ordered so every commit compiles and stays green)

| # | Task | Theme |
|---|---|---|
| 1 | FlushCoordinator extraction | refactor before settle plumbing (backlog #1) |
| 2 | Slim PageRequest + BukkitSettleDispatcher rewrite | §5.8 modified files; old engine call-site patched |
| 3 | Host seam + relocated engine core (Paginator, PageItemFactory, RenderedItem, PaginationHost, Abstract, Normal, Scroll) | copy with seam; ported eager geometry tests |
| 4 | Relocated PatternPagination + async engine flows | ported pattern + settle/rollback/pre-init/re-clamp tests |
| 5 | Public-surface switchover | delete 2.x cluster; new public interfaces; JoinListener neutered |
| 6 | PaginationSpec + PaginationSourceSpec | declaration model |
| 7 | ViewEngine additions (painter, paginationSettle) + PaginationBinding | fill/element/failure/repaint runtime |
| 8 | PaginationBuilderImpl + PaginationImpl + ContextStateAccess consolidation | builder validation + the token |
| 9 | View.paginate* factories | §5.2 factories + freeze semantics |
| 10 | Open wiring: OpenPhase bindings + PaginationBindings + PaginationInitPhase + OpenFailureHandler + ViewRegistry validation | §7 step 7 |
| 11 | Reactive settles: UpdatePhase gate/areas/watchers + FirstRenderPhase pagination paint | §5.7 reactive settles |
| 12 | Click routing on elements + overlap validation + unbound-char warning | §6 routing extension |
| 13 | Shared timeout scheduler shutdown on disable | §5.8 new-in-3.0.0 |
| 14 | test-plugin samples rewrite + end-to-end sample tests | §12 ergonomics proof |
| 15 | Spec amendment + Plan-3 notes + full reactor green | docs + verification |

Mid-plan partial states (deliberate, each still green): after Task 5 the four paged samples are deleted (restored as v3 views in Task 14); between Tasks 7 and 11 settles repaint token watchers but not yet pagination areas (UpdatePhase extension lands in Task 11, with the tests that pin the full reactive behavior).

---

<!-- TASKS START -->

### Task 1: FlushCoordinator extraction

Behavior-preserving refactor (reviewer backlog #1): the flush machinery — the dirty-token cascade loop, the shared-state fan-out flush with its re-entrancy guard and per-tick coalescing, and the shared flush-hook wiring — moves out of `ViewEngine` into a package-private `FlushCoordinator`, so the pagination settle plumbing of later tasks lands on a clean seam. `ViewEngine` keeps its public `flushDirty`/`flushShared` and package-private `wireSharedFlush` signatures (and their `ThreadUtils` main-thread asserts) and delegates. No test is modified; the existing suite is the regression net. Method bodies move character-for-character — reviewers will diff them against the pre-refactor `ViewEngine`.

One deliberate choice: the cascade-cap WARNING keeps publishing on the **ViewEngine** JUL logger (`Logger.getLogger(ViewEngine.class.getName())`), not a FlushCoordinator-named one. `UpdateFlushTest.selfFeedingRenderer_stopsAtCascadeCapWithWarning` attaches its capturing handler to exactly that logger name, and JUL loggers named `...engine.ViewEngine` and `...engine.FlushCoordinator` are siblings, not parent/child — renaming the logger would silently break the warning's observable destination and the test. A comment in the new file documents this.

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/FlushCoordinator.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/UpdateFlushTest.java (existing, NOT modified — baseline + regression net)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngineOpenCloseTest.java (existing, NOT modified — exercises `open()` → `wireSharedFlush` wiring)

- [ ] **Step 1: Record the green baseline**

The flush machinery is covered by the existing engine tests in `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/`: `UpdateFlushTest` pins the cascade loop (`stateChange_duringOnUpdate_cascadesExactlyOneExtraPass`), the cap + WARNING on the ViewEngine logger (`selfFeedingRenderer_stopsAtCascadeCapWithWarning`), main-thread shared fan-out (`sharedStateSet_onMain_flushesEverySessionOfTheView`), and off-main scheduling/coalescing (`sharedStateSet_offMain_isScheduledAndFlushesNextTick`, `sharedStateSets_offMain_coalesceToOneFlushPerSessionPerTick`); `ViewEngineOpenCloseTest` exercises the `open()` path that calls `wireSharedFlush`. Run them before touching anything:

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=UpdateFlushTest,ViewEngineOpenCloseTest"`
Expected: PASS (BUILD SUCCESS, 0 failures, 0 errors) — record the test counts; the same command must produce the same counts in Step 4.

- [ ] **Step 2: Create FlushCoordinator**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/FlushCoordinator.java`. The `flushDirty` loop, both `flushShared` overloads and `wireSharedFlush` bodies are copied character-for-character from the current `ViewEngine` (lines 286–298, 308, 319–327 and 340–384 of the pre-refactor file); only the `ThreadUtils.assertMainThread` lines stay behind in `ViewEngine`.

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.UpdatePhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.SharedStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Flush machinery extracted from {@link ViewEngine}: the dirty-token cascade loop, the
 * shared-state fan-out flush with its re-entrancy guard and per-tick coalescing, and the
 * shared flush-hook wiring. Behavior-preserving extraction — {@link ViewEngine} keeps the
 * public entry points (and their main-thread asserts) and delegates here.
 */
final class FlushCoordinator {

    // the warnings keep publishing on the ViewEngine logger: this extraction must not change
    // observable behavior, and the cascade-cap warning destination is pinned by UpdateFlushTest
    private static final Logger LOGGER = Logger.getLogger(ViewEngine.class.getName());
    static final int CASCADE_CAP = 8;

    private final Plugin plugin;
    private final SessionRegistry sessions;
    private final UpdatePhase updatePhase;

    // re-entrancy guard for main-thread shared flushes: a renderer writing shared state
    // while its view is being flushed must not recurse; main thread only
    private final Set<View> sharedFlushPending = new HashSet<>();
    // per-tick coalescing of off-main shared writes: maps each owner to the accumulated set
    // of dirty token ids; touched from any thread; drained atomically when the scheduled task runs
    private final ConcurrentHashMap<View, Set<Integer>> sharedFlushScheduled = new ConcurrentHashMap<>();

    /**
     * Creates the coordinator.
     *
     * @param plugin      the plugin owning the inventory-api runtime
     * @param sessions    the per-player session registry
     * @param updatePhase the update phase running the repaint passes
     */
    FlushCoordinator(@NotNull Plugin plugin, @NotNull SessionRegistry sessions,
                     @NotNull UpdatePhase updatePhase) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.updatePhase = Objects.requireNonNull(updatePhase, "updatePhase");
    }

    /**
     * Flushes dirty state tokens of a session: each pass drains the dirty set and runs a
     * STATE_CHANGE update over the watchers; passes repeat while handlers re-dirty tokens,
     * capped at {@value #CASCADE_CAP} cascades per flush, after which the remaining dirty
     * tokens are dropped with a WARNING. Main thread only; the {@link ViewEngine} entry
     * point asserts it.
     *
     * @param session the session to flush
     */
    void flushDirty(@NotNull ViewSession session) {
        int cascades = 0;
        while (session.stateStore().hasDirty()) {
            if (++cascades > CASCADE_CAP) {
                LOGGER.log(Level.WARNING, "state feedback loop detected for view {0}; "
                                + "dropping remaining dirty tokens after {1} cascaded flush passes",
                        new Object[]{session.registered().type().getName(), CASCADE_CAP});
                session.stateStore().drainDirty();
                break;
            }
            Set<Integer> dirty = session.stateStore().drainDirty();
            updatePhase.update(session, UpdateTrigger.STATE_CHANGE, dirty);
        }
    }

    /**
     * Runs a full STATE_CHANGE repaint pass on every active session of the given view; full-pass
     * fallback — the wired hooks use the watcher-scoped overload. Main thread only; the
     * {@link ViewEngine} entry point asserts it.
     *
     * @param owner the view singleton whose sessions should flush
     */
    void flushShared(@NotNull View owner) {
        flushShared(owner, null);
    }

    /**
     * Runs a STATE_CHANGE repaint pass on every active session of the given view, restricting
     * the repaint to watchers of the supplied token id set. Passing {@code null} for
     * {@code tokenIds} triggers a full repaint (same as the no-arg overload).
     *
     * @param owner    the view singleton whose sessions should flush
     * @param tokenIds the dirty token ids to pass to the update phase, or {@code null} for a full pass
     */
    private void flushShared(@NotNull View owner, @Nullable Set<Integer> tokenIds) {
        // snapshot: an onUpdate handler may close a session and mutate the registry
        List<ViewSession> snapshot = new ArrayList<>(sessions.all());
        for (ViewSession session : snapshot) {
            if (session.registered().instance() == owner && session.isActive()) {
                updatePhase.update(session, UpdateTrigger.STATE_CHANGE, tokenIds);
            }
        }
    }

    /**
     * Wires the flush hook of every {@code SharedState} token of the view so writes fan out
     * to all of the view's open sessions: main-thread writes flush immediately (re-entrancy
     * guarded), off-main writes coalesce into one scheduled flush per view per tick. Already
     * wired tokens are skipped to avoid redundant re-wiring. The flush is watcher-scoped: each
     * hook captures its token id and passes it as a singleton dirty set so only components
     * watching that token are repainted.
     *
     * @param registered the registration whose view instance is being opened
     */
    void wireSharedFlush(@NotNull RegisteredView registered) {
        final View owner = registered.instance();
        for (StateToken token : owner.tokenTable().tokens()) {
            if (!(token instanceof SharedStateImpl)) {
                continue;
            }
            SharedStateImpl<?> shared = (SharedStateImpl<?>) token;
            if (shared.flushHookWired()) {
                continue;
            }
            final int tokenId = shared.id();
            shared.flushHook(() -> {
                if (Bukkit.isPrimaryThread()) {
                    if (sharedFlushPending.add(owner)) {
                        try {
                            flushShared(owner, Collections.singleton(tokenId));
                        } finally {
                            sharedFlushPending.remove(owner);
                        }
                    }
                } else {
                    // compute is atomic vs the drain's remove on the same key, so an id can
                    // never land in an already-drained set
                    boolean[] schedule = {false};
                    sharedFlushScheduled.compute(owner, (key, existing) -> {
                        if (existing == null) {
                            Set<Integer> created = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
                            created.add(tokenId);
                            schedule[0] = true;
                            return created;
                        }
                        existing.add(tokenId);
                        return existing;
                    });
                    if (schedule[0]) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Set<Integer> ids = sharedFlushScheduled.remove(owner);
                            if (ids != null) {
                                flushShared(owner, ids);
                            }
                        });
                    }
                }
            });
        }
    }
}
```

- [ ] **Step 3: Rewire ViewEngine onto the coordinator**

Modify `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java`. Five regions change; everything else (class Javadoc, `open`, `close`, `updateTitle`, `update`, `click`, `drag`, `bukkitClose`, `defer`, `drainDeferred`, `isInClickDispatch`, `clickDispatch`, `plugin()`, `titleUpdater()`) stays byte-for-byte.

**(3a) Imports** — replace the whole import block (current lines 25–65). Removed because their only users moved out: `org.jetbrains.annotations.Nullable`, `...internal.state.SharedStateImpl`, `...state.StateToken`, `java.util.ArrayList`, `java.util.Collections`, `java.util.HashSet`, `java.util.Set`, `java.util.concurrent.ConcurrentHashMap`, `java.util.logging.Level`, `java.util.logging.Logger`. `java.util.List` stays (`drainDeferred` uses it); `UpdateTrigger` stays (`update` parameter). The new block:

```java
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.ClickRoutingPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.ClosePhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.FirstRenderPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.OpenPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.UpdatePhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.util.ThreadUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

import java.util.List;
import java.util.Objects;
```

**(3b) Fields** — replace the field region (current lines 75–98: the `LOGGER` and `CASCADE_CAP` constants, the five collaborator fields, `inClickDispatch`, both shared-flush fields with their comments, and the phase block) with:

```java
    private final Plugin plugin;
    private final ViewRegistry views;
    private final SessionRegistry sessions;
    private final SlotPainter painter;
    private final TitleUpdater titleUpdater;

    private boolean inClickDispatch;

    // fixed-order phase handlers, engine-owned
    final OpenPhase openPhase;
    final FirstRenderPhase firstRenderPhase;
    final UpdatePhase updatePhase;
    final ClickRoutingPhase clickRoutingPhase;
    final ClosePhase closePhase;

    // flush machinery extracted behind a dedicated coordinator (single responsibility);
    // the flush entry points below delegate to it after asserting the main thread
    private final FlushCoordinator flushCoordinator;
```

`LOGGER`, `CASCADE_CAP`, `sharedFlushPending` and `sharedFlushScheduled` are DELETED from `ViewEngine` — they live in `FlushCoordinator` now.

**(3c) Constructor** — signature and Javadoc unchanged; the body gains the coordinator construction. `UpdatePhase` is built before `FlushCoordinator` because the coordinator depends on it:

```java
    /**
     * Creates the engine and its phase handlers.
     *
     * @param plugin       the plugin owning the inventory-api runtime
     * @param views        the view registry
     * @param sessions     the per-player session registry
     * @param painter      the slot painter used by the rendering phases
     * @param titleUpdater the in-place title update strategy
     */
    public ViewEngine(@NotNull Plugin plugin, @NotNull ViewRegistry views, @NotNull SessionRegistry sessions,
                      @NotNull SlotPainter painter, @NotNull TitleUpdater titleUpdater) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.views = Objects.requireNonNull(views, "views");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.painter = Objects.requireNonNull(painter, "painter");
        this.titleUpdater = Objects.requireNonNull(titleUpdater, "titleUpdater");
        this.closePhase = new ClosePhase(this, sessions);
        this.openPhase = new OpenPhase(this, sessions, painter);
        this.firstRenderPhase = new FirstRenderPhase(this, sessions, painter);
        this.updatePhase = new UpdatePhase(this, painter);
        this.clickRoutingPhase = new ClickRoutingPhase(this);
        this.flushCoordinator = new FlushCoordinator(plugin, sessions, updatePhase);
    }
```

**(3d) Flush methods** — replace the entire region from the `flushDirty` Javadoc through the closing brace of `wireSharedFlush` (current lines 276–384, i.e. `flushDirty`, the public `flushShared`, the private `flushShared(View, Set)` overload, and `wireSharedFlush`) with these three delegators. The private overload is gone from `ViewEngine` entirely (it is the coordinator's private method now); `ViewEngine.open` keeps calling `wireSharedFlush(registered)` unchanged at its line 156, which now delegates:

```java
    /**
     * Flushes dirty state tokens of a session: each pass drains the dirty set and runs a
     * STATE_CHANGE update over the watchers; passes repeat while handlers re-dirty tokens,
     * capped at {@value FlushCoordinator#CASCADE_CAP} cascades per flush, after which the
     * remaining dirty tokens are dropped with a WARNING.
     *
     * @param session the session to flush
     */
    public void flushDirty(@NotNull ViewSession session) {
        ThreadUtils.assertMainThread("ViewEngine.flushDirty");
        flushCoordinator.flushDirty(session);
    }

    /**
     * Runs a full STATE_CHANGE repaint pass on every active session of the given view; full-pass
     * fallback — the wired hooks use the watcher-scoped overload.
     *
     * @param owner the view singleton whose sessions should flush
     */
    public void flushShared(@NotNull View owner) {
        ThreadUtils.assertMainThread("ViewEngine.flushShared");
        flushCoordinator.flushShared(owner);
    }

    /**
     * Wires the flush hook of every {@code SharedState} token of the view so writes fan out
     * to all of the view's open sessions; see {@link FlushCoordinator#wireSharedFlush} for
     * the immediate-vs-coalesced dispatch rules.
     *
     * @param registered the registration whose view instance is being opened
     */
    void wireSharedFlush(@NotNull RegisteredView registered) {
        flushCoordinator.wireSharedFlush(registered);
    }
```

**(3e) Sanity check** — nothing else in `ViewEngine` referenced the moved members: `update(...)` and `click(...)` call the public `flushDirty(session)` (now delegating, behavior identical — the assert runs twice on those paths only in the sense that it ran once before and still runs once; the coordinator does not re-assert).

- [ ] **Step 4: Rerun the baseline tests, then the full module**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=UpdateFlushTest,ViewEngineOpenCloseTest"`
Expected: PASS — identical test counts to Step 1 (the cascade-cap WARNING assertion passes because the coordinator publishes on the ViewEngine logger).

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B`
Expected: PASS (BUILD SUCCESS, full module green).

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/FlushCoordinator.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java
git commit -m "refactor(inventory-api): extract flush machinery from ViewEngine into FlushCoordinator" -m "Behavior-preserving extraction (reviewer backlog #1): the dirty-token cascade loop, shared-flush re-entrancy guard, per-tick coalescing map and shared flush-hook wiring move to a package-private FlushCoordinator so pagination settle plumbing lands on a clean seam. ViewEngine keeps its public signatures and main-thread asserts and delegates; the cascade-cap warning keeps publishing on the ViewEngine logger. No test changes."
```

---

### Task 2: Slim PageRequest + BukkitSettleDispatcher rewrite

Spec §5.8: `PageRequest` drops its `Viewer` reach-through and becomes a slim, Lombok-free, any-thread immutable carrying `page`/`pageSize`/`offset` plus `@Nullable UUID playerId()` and `@Nullable Plugin plugin()`. `BukkitSettleDispatcher` is rewritten to read the plugin from the request and to drop the `tickAsync()` consultation entirely: settles always route to the main thread (inline when the load already completed there, or when the request carries no plugin — engine-external test usage only). The single production call site that builds a `PageRequest` (the OLD 2.x engine `pagination/impl/AbstractPageSourcePagination.dispatch`, which Task 3 later copies behind the host seam) is patched to populate the new fields from the bound viewer. This is a breaking public-signature change — the commit is marked `!`.

Call-site inventory (verified by grepping `new PageRequest(` and `getViewer()` across the repo — these are ALL of them):

| File | Line | Change |
|---|---|---|
| `pagination/impl/AbstractPageSourcePagination.java` (main) | 228–229 | 4-arg ctor → 5-arg from viewer |
| `pagination/source/BukkitSettleDispatcher.java` (main) | 50 | `getViewer()` reader — file rewritten |
| `pagination/source/BukkitSettleDispatcherTest.java` (test) | 67 | file rewritten |
| `pagination/source/AsyncPageSourceTest.java` (test) | 51 | gains fifth `null` arg |
| `pagination/source/EagerPageSourceTest.java` (test) | 40 | gains fifth `null` arg |
| `pagination/impl/AsyncPaginationIntegrationTest.java` (test) | 340 | `getViewer()` assertion → `playerId()`/`plugin()` |

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PageRequest.java (full rewrite)
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/BukkitSettleDispatcher.java (full rewrite)
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AbstractPageSourcePagination.java (dispatch call site)
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PaginationErrorCallback.java (type Javadoc only — stale tickAsync clause)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/BukkitSettleDispatcherTest.java (full rewrite)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java (one helper line)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/EagerPageSourceTest.java (one helper line)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AsyncPaginationIntegrationTest.java (one test method + one import)

- [ ] **Step 1: Rewrite/adapt the tests first (they will not compile yet)**

**(1a)** Replace the entire content of `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/BukkitSettleDispatcherTest.java` with the file below (keep the existing MIT header lines 1–22 unchanged). MockBukkit is dropped on purpose: every Bukkit static the dispatcher touches (`isPrimaryThread`, `getScheduler`) is mocked with `Mockito.mockStatic` — the pattern the old file already used — so no real scheduler exists, nothing can leak tasks, and no `unmock()` drain pitfall applies.

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitSettleDispatcherTest {

    private static PageRequest requestWith(Plugin plugin) {
        return new PageRequest(1, 3, 0, null, plugin);
    }

    @Test
    void dispatch_onPrimaryThread_runsInline() {
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        AtomicBoolean ran = new AtomicBoolean();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            dispatcher.dispatch(requestWith(mock(Plugin.class)), () -> ran.set(true));

            bukkit.verify(Bukkit::getScheduler, never());
        }

        assertTrue(ran.get(), "a settle completing on the main thread must run inline");
    }

    @Test
    void dispatch_nullPluginOffThread_runsInline() {
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        AtomicBoolean ran = new AtomicBoolean();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

            dispatcher.dispatch(requestWith(null), () -> ran.set(true));

            bukkit.verify(Bukkit::getScheduler, never());
        }

        assertTrue(ran.get(), "engine-external requests without a plugin must settle on the calling thread");
    }

    @Test
    void dispatch_offMainWithPlugin_schedulesOntoMainThread() {
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        AtomicBoolean ran = new AtomicBoolean();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            dispatcher.dispatch(requestWith(plugin), () -> ran.set(true));

            assertFalse(ran.get(), "the settle must not run inline on the completing thread");
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), task.capture());
            task.getValue().run();
        }

        assertTrue(ran.get(), "the scheduled task must carry the settle onto the main thread");
    }

    @Test
    void dispatch_whilePluginDisabling_dropsSettleInsteadOfThrowing() {
        // the real scheduler rejects tasks registered by a disabling plugin with
        // IllegalPluginAccessException; the dispatcher must swallow it (a throw inside
        // whenComplete or a timeout task would vanish into an unobserved future) and the
        // settle is dropped
        BukkitSettleDispatcher dispatcher = new BukkitSettleDispatcher();
        Plugin plugin = mock(Plugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        AtomicBoolean ran = new AtomicBoolean();

        when(scheduler.runTask(any(Plugin.class), any(Runnable.class)))
                .thenThrow(new IllegalPluginAccessException("Plugin attempted to register task while disabled"));

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            assertDoesNotThrow(() -> dispatcher.dispatch(requestWith(plugin), () -> ran.set(true)),
                    "a disabling plugin must not blow up the completing thread");
        }

        assertFalse(ran.get(), "the scheduler rejected the task; the settle is dropped");
    }
}
```

**(1b)** In `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java`, the request helper (lines 50–52) gains the fifth argument:

```java
    private static PageRequest request(int page) {
        return new PageRequest(page, 3, (page - 1) * 3, null, null);
    }
```

**(1c)** In `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/EagerPageSourceTest.java`, the request helper (lines 39–41) gains the fifth argument:

```java
    private static PageRequest request(int page, int pageSize, int offset) {
        return new PageRequest(page, pageSize, offset, null, null);
    }
```

**(1d)** In `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AsyncPaginationIntegrationTest.java`:

Add the import (after `import java.util.List;` at line 50, keeping the `java.util.*` group sorted):

```java
import java.util.UUID;
```

Replace the whole `changePageBeforeInit_isDeferredToInit` test (lines 323–341) — its final assertion (line 340) was the only `request.getViewer()` reader in the test suite; it becomes a `playerId()`/`plugin()` pair. The viewer mock gets a lenient `getUniqueId()` stub because this test asserts the id (every OTHER test in this class leaves `getUniqueId()` unstubbed: a Mockito mock returns `null`, which the slim `PageRequest` accepts, so no other method changes):

```java
    @Test
    void changePageBeforeInit_isDeferredToInit() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.changePage(3);

        assertTrue(supplier.requests.isEmpty(), "no viewer is bound yet; nothing must be dispatched");
        assertEquals(3, pagination.getCurrentPage());

        Viewer viewer = mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class));
        UUID playerId = UUID.randomUUID();
        lenient().when(viewer.getUniqueId()).thenReturn(playerId);
        pagination.init(viewer);

        assertEquals(1, supplier.requests.size());
        PageRequest request = supplier.requests.get(0);
        assertEquals(3, request.getPage());
        assertEquals(6, request.getOffset(), "page 3 of size 3 => offset 6");
        assertEquals(playerId, request.playerId(), "the dispatched request must carry the viewer's player id");
        assertEquals(plugin, request.plugin(), "the dispatched request must carry the owning plugin");
    }
```

(`plugin` is the test class's `MockPlugin` field, already stubbed into the viewer by `mockViewer` via `lenient().when(viewer.getPlugin()).thenReturn(plugin)`.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=BukkitSettleDispatcherTest,AsyncPageSourceTest,EagerPageSourceTest,AsyncPaginationIntegrationTest"`
Expected: FAIL — test COMPILATION ERROR (surefire never runs): `constructor PageRequest in class PageRequest cannot be applied to given types; required: int,int,int,Viewer; found: int,int,int,<null>,<null>` plus `cannot find symbol: method playerId()` / `method plugin()`.

- [ ] **Step 3: Rewrite the two main files and patch the call site**

**(3a)** Replace the entire content of `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PageRequest.java` (keep the existing MIT header lines 1–22 unchanged). No Lombok; hand-written constructor and accessors; the authoritative-bounds Javadoc wording is preserved verbatim:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable description of one page load issued by a paginator; safe to share across threads.
 *
 * <p>{@code offset} and {@code pageSize} are the <strong>authoritative query
 * bounds</strong> — a backing store should fetch with {@code LIMIT pageSize OFFSET offset}.
 * {@code page} is informational only: scroll paginators advance one element per page, so
 * deriving the offset as {@code (page - 1) * pageSize} is wrong for them.
 *
 * <p>{@code playerId} identifies who the page is loaded for and {@code plugin} owns the load
 * (the settle dispatcher schedules onto the main thread on its behalf). Both are {@code null}
 * only for engine-external test usage — requests dispatched by the built-in paginators always
 * populate them.
 */
public final class PageRequest {

    private final int page;
    private final int pageSize;
    private final int offset;
    private final UUID playerId;
    private final Plugin plugin;

    /**
     * Creates a page request.
     *
     * @param page     the 1-indexed page being requested; informational only
     * @param pageSize the maximum number of items the requested page can display
     * @param offset   the global element offset of the first item on the requested page
     * @param playerId the player the page is being loaded for, or {@code null} only for
     *                 engine-external test usage
     * @param plugin   the plugin owning the load, or {@code null} only for engine-external
     *                 test usage
     */
    public PageRequest(int page, int pageSize, int offset, @Nullable UUID playerId, @Nullable Plugin plugin) {
        this.page = page;
        this.pageSize = pageSize;
        this.offset = offset;
        this.playerId = playerId;
        this.plugin = plugin;
    }

    /**
     * Returns the 1-indexed page being requested. Informational only — scroll paginators
     * slide one element per page, so the offset must never be derived from it.
     *
     * @return the 1-indexed page
     */
    public int getPage() {
        return page;
    }

    /**
     * Returns the maximum number of items the requested page can display.
     *
     * @return the page size
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * Returns the global element offset of the first item on the requested page — together
     * with {@link #getPageSize()} the authoritative query bound: {@code LIMIT pageSize
     * OFFSET offset}.
     *
     * @return the element offset
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Returns the unique id of the player the page is being loaded for.
     *
     * @return the player id, or {@code null} only for engine-external test usage
     */
    public @Nullable UUID playerId() {
        return playerId;
    }

    /**
     * Returns the plugin owning the load; the settle dispatcher schedules onto the main
     * thread on its behalf.
     *
     * @return the owning plugin, or {@code null} only for engine-external test usage
     */
    public @Nullable Plugin plugin() {
        return plugin;
    }
}
```

**(3b)** Replace the entire content of `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/BukkitSettleDispatcher.java` (keep the existing MIT header lines 1–22 unchanged). The `tickAsync` consultation, its private helper and the `Viewer`/`CustomInventory`/`InventoryConfiguration` imports are deleted; the class Javadoc is rewritten with no `tickAsync` mention, documenting the FIFO assumption and the disable-drop:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link SettleDispatcher}: settles always route to the main server thread — inline
 * when the load already completed there, otherwise through the Bukkit scheduler on behalf of
 * the request's owning plugin. A request without a plugin (engine-external test usage only)
 * settles inline on the completing thread.
 *
 * <p>Dispatch order is FIFO: inline settles run immediately and the Bukkit scheduler runs
 * same-tick tasks in submission order. {@link AsyncPageSource} relies on this — a request's
 * timeout settle must reach the dispatcher before the cancellation settle it triggers, so the
 * at-most-once check discards the latter.
 *
 * <p>A settle completing while the owning plugin is disabling is dropped with a warning: the
 * scheduler rejects new tasks at that point and the inventory is about to be closed by the
 * shutdown anyway.
 */
public final class BukkitSettleDispatcher implements SettleDispatcher {

    private static final Logger LOGGER = Logger.getLogger(BukkitSettleDispatcher.class.getName());

    @Override
    public void dispatch(PageRequest request, Runnable task) {
        if (request.plugin() == null || Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(request.plugin(), task);
        } catch (IllegalPluginAccessException e) {
            // thrown inside whenComplete or a timeout task this would otherwise vanish into
            // an unobserved future
            LOGGER.log(Level.WARNING, "Dropped a page-load settle: the owning plugin is disabled.", e);
        }
    }
}
```

**(3c)** In `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AbstractPageSourcePagination.java`, replace the `dispatch` method (lines 227–239) with:

```java
    private void dispatch(S rollback, boolean render) {
        // the viewer is always bound here: init dispatches after binding it, and both
        // changePageInternal and setSource guard the unbound case before dispatching
        PageRequest request = new PageRequest(this.currentPage, this.itemPageLimit, requestOffset(),
                this.viewer.getUniqueId(), this.viewer.getPlugin());
        this.dispatchingThread = Thread.currentThread();
        try {
            this.pageSource.request(request, (result, error) -> onSettle(rollback, result, error));
        } finally {
            this.dispatchingThread = null;
        }
        if (render) {
            renderIfOnline();
        }
    }
```

No import changes are needed (`PageRequest` is already imported; `getUniqueId()`/`getPlugin()` are `Viewer` methods and `UUID`/`Plugin` are not named in this file). The file contains no other PageRequest-related comment or Javadoc to update — the only PageRequest reference was this construction, and the new comment above documents the viewer-bound guarantee the slim request relies on.

Finally, fix the now-stale threading note in `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PaginationErrorCallback.java` — its type Javadoc still references the deleted `tickAsync` opt-out. Replace the type Javadoc (lines 25–29) with:

```java
/**
 * Invoked when an async page load fails (exceptional completion, {@code null} future, synchronous
 * throw, or timeout). Runs on the same thread that applies the settle — always the main server
 * thread for settles routed through {@link BukkitSettleDispatcher}.
 */
```

Nothing else in the file changes.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=BukkitSettleDispatcherTest,AsyncPageSourceTest,EagerPageSourceTest,AsyncPaginationIntegrationTest"`
Expected: PASS (BUILD SUCCESS; all four classes green — `AsyncPaginationIntegrationTest`'s off-thread settle tests confirm the rewritten dispatcher still routes through the MockBukkit scheduler tick).

- [ ] **Step 5: Full module check**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B`
Expected: PASS (BUILD SUCCESS — no other main or test source references `getViewer()` or the 4-arg constructor; verified by grep in the call-site inventory above).

- [ ] **Step 6: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PageRequest.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/BukkitSettleDispatcher.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PaginationErrorCallback.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AbstractPageSourcePagination.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/BukkitSettleDispatcherTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/EagerPageSourceTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AsyncPaginationIntegrationTest.java
git commit -m "refactor(inventory-api)!: slim PageRequest and rewrite BukkitSettleDispatcher" -m "BREAKING: PageRequest loses getViewer(); the constructor is now PageRequest(int page, int pageSize, int offset, UUID playerId, Plugin plugin) with hand-written accessors (no Lombok), playerId/plugin null only for engine-external test usage. BukkitSettleDispatcher reads the plugin from the request and no longer consults tickAsync(): settles always route to the main thread (inline when already there); a disabling plugin drops the settle with a warning. The 2.x engine's dispatch site populates the slim request from the bound viewer."
```

---

### Task 3: Host seam + relocated engine core

Copies the 2.x page-source engine skeleton plus the normal and scroll geometries into `internal.pagination.engine`, behind the new `PaginationHost` seam. Algorithms are preserved verbatim; only the pinned substitutions from "Shared Type Contracts" are applied. The 2.x cluster (`pagination/Pagination.java`, `pagination/impl/*`, `pagination/builder/*`) stays untouched and green until Task 5. Prerequisite: Task 2 already slimmed `PageRequest` to the 5-arg `(page, pageSize, offset, playerId, plugin)` form — the relocated `dispatch` populates it from `host.playerId()`/`host.plugin()`.

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationHost.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/RenderedItem.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/Paginator.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/PageItemFactory.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/AbstractPageSourcePagination.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/NormalPagination.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/ScrollPagination.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/FakePaginationHost.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/NormalPaginationTest.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/ScrollPaginationTest.java

- [ ] **Step 1: Write the failing tests**

The tests are ports of the 2.x `pagination/impl/NormalPaginationTest` and `ScrollPaginationTest`: same method names and assertions (the exact geometry numbers of pinned rule 1), but the deleted builders are replaced by direct engine construction, the mocked `Viewer`/`InventoryEditor` pair by `FakePaginationHost`, and `InventoryItem` amount assertions by `RenderedItem.plainItem()` amount assertions. The 2.x `setSource_beforeInit_isHonoredByInit` becomes `replaceSource_beforeBind_isHonoredByBind` (`replaceSource` never dispatches; the bind-time dispatch picks the swapped source up). `FakePaginationHost` is the shared fixture for this task and Task 4.

`FakePaginationHost.java` (test fixture, package-private):

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationHost;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test double for {@link PaginationHost}: records every {@code fillPage} invocation as an
 * (items, layout) pair, counts {@code requestRender} calls and exposes a configurable
 * active flag. Shared by the relocated-engine test classes.
 */
final class FakePaginationHost implements PaginationHost {

    /**
     * One recorded {@code fillPage} invocation: the items and the layout they were mapped onto.
     */
    static final class FillPageCall {
        final List<RenderedItem> items;
        final Layout layout;

        FillPageCall(List<RenderedItem> items, Layout layout) {
            this.items = items;
            this.layout = layout;
        }
    }

    private final List<FillPageCall> fillPageCalls = new ArrayList<>();
    private final UUID playerId;
    private final Plugin plugin;
    private boolean active = true;
    private int requestRenderCount;

    FakePaginationHost(UUID playerId, Plugin plugin) {
        this.playerId = playerId;
        this.plugin = plugin;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void fillPage(@NotNull List<RenderedItem> items, @NotNull Layout layout) {
        fillPageCalls.add(new FillPageCall(new ArrayList<>(items), layout));
    }

    @Override
    public void requestRender() {
        requestRenderCount++;
    }

    @Override
    public @Nullable UUID playerId() {
        return playerId;
    }

    @Override
    public @NotNull Plugin plugin() {
        return plugin;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    List<FillPageCall> fillPageCalls() {
        return fillPageCalls;
    }

    FillPageCall lastFillPage() {
        if (fillPageCalls.isEmpty()) {
            throw new AssertionError("no fillPage call was recorded");
        }
        return fillPageCalls.get(fillPageCalls.size() - 1);
    }

    int requestRenderCount() {
        return requestRenderCount;
    }
}
```

`NormalPaginationTest.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NormalPaginationTest {

    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static Layout nineSlotLayout() {
        return Layout.ofSlots(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    private static NormalPagination<Integer> eagerPagination(List<Integer> source) {
        return new NormalPagination<>(
                null,
                (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value)),
                nineSlotLayout(),
                null,
                new EagerPageSource<>(source));
    }

    private FakePaginationHost newHost() {
        return new FakePaginationHost(UUID.randomUUID(), plugin);
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }

    @Test
    void eagerFlow_rendersFirstPageSlice() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        FakePaginationHost host = newHost();

        pagination.bind(host);
        pagination.insertPageItems();

        List<RenderedItem> items = host.lastFillPage().items;
        assertEquals(9, items.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(i + 1, items.get(i).plainItem().getAmount());
        }
    }

    @Test
    void eagerFlow_totalsAndNavigationMatchListSize() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        pagination.bind(newHost());

        assertEquals(3, pagination.getTotalPages());
        assertEquals(20, pagination.getTotalElements());

        pagination.changePage(3);
        assertEquals(3, pagination.getCurrentPage());
        assertFalse(pagination.hasNextPage());
    }

    @Test
    void changePage_beyondTotal_clampsToLastPage() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        pagination.bind(newHost());

        pagination.changePage(99);

        assertEquals(3, pagination.getCurrentPage());
    }

    @Test
    void getPageOfIndex_returnsMinusOneOutOfRange() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        pagination.bind(newHost());

        assertEquals(1, pagination.getPageOfIndex(0));
        assertEquals(1, pagination.getPageOfIndex(8));
        assertEquals(2, pagination.getPageOfIndex(9));
        assertEquals(3, pagination.getPageOfIndex(19));
        assertEquals(-1, pagination.getPageOfIndex(20));
        assertEquals(-1, pagination.getPageOfIndex(-1));
    }

    @Test
    void replaceSource_beforeBind_isHonoredByBind() {
        NormalPagination<Integer> pagination = new NormalPagination<>(
                null,
                (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value)),
                nineSlotLayout());
        FakePaginationHost host = newHost();

        pagination.replaceSource(new EagerPageSource<>(sourceOf(5)));
        pagination.bind(host);
        pagination.insertPageItems();

        assertEquals(1, host.lastFillPage().items.get(0).plainItem().getAmount());
    }

    @Test
    void changePage_beforeBind_onNonEmptyEagerSource_recordsAndReclamps() {
        // regression for the pinned rule-2 reorder: the 2.x body clamped via getTotalPages()
        // before the unbound check, dividing by the still-zero itemPageLimit for a non-empty
        // eager source; the record-only branch must run first and the settle's downward
        // re-clamp must correct the overshoot after bind
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));

        pagination.changePage(99);
        assertEquals(99, pagination.getCurrentPage());

        pagination.bind(newHost());

        assertEquals(3, pagination.getCurrentPage());
    }

    @Test
    void changePage_beforeBind_inRangeTarget_isDispatchedByBind() {
        NormalPagination<Integer> pagination = eagerPagination(sourceOf(20));
        FakePaginationHost host = newHost();

        pagination.changePage(2);
        pagination.bind(host);
        pagination.insertPageItems();

        assertEquals(2, pagination.getCurrentPage());
        // page 2 of a 20-element source over 9 slots renders amounts 10..18
        assertEquals(10, host.lastFillPage().items.get(0).plainItem().getAmount());
    }
}
```

`ScrollPaginationTest.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollPaginationTest {

    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ScrollPagination<Integer> sevenSlotScroll(List<Integer> source) {
        return new ScrollPagination<>(
                null,
                (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value)),
                Layout.ofSlots(0, 1, 2, 3, 4, 5, 6),
                null,
                new EagerPageSource<>(source));
    }

    private FakePaginationHost newHost() {
        return new FakePaginationHost(UUID.randomUUID(), plugin);
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }

    @Test
    void totals_windowOfSevenOverTen_isFourPages() {
        ScrollPagination<Integer> pagination = sevenSlotScroll(sourceOf(10));
        pagination.bind(newHost());

        assertEquals(4, pagination.getTotalPages());
    }

    @Test
    void pageTwo_slidesWindowByOneElement() {
        ScrollPagination<Integer> pagination = sevenSlotScroll(sourceOf(10));
        FakePaginationHost host = newHost();
        pagination.bind(host);

        pagination.changePage(2);
        pagination.insertPageItems();

        List<RenderedItem> items = host.lastFillPage().items;

        // window slid by ONE element: values 2..8
        for (int i = 0; i < 7; i++) {
            assertEquals(i + 2, items.get(i).plainItem().getAmount());
        }
    }

    @Test
    void getPageOfIndex_usesFirstVisiblePageSemantics() {
        ScrollPagination<Integer> pagination = sevenSlotScroll(sourceOf(10)); // window 7 => 4 pages
        pagination.bind(newHost());

        assertEquals(1, pagination.getPageOfIndex(0));
        assertEquals(1, pagination.getPageOfIndex(5));
        assertEquals(1, pagination.getPageOfIndex(6));
        assertEquals(2, pagination.getPageOfIndex(7));
        assertEquals(4, pagination.getPageOfIndex(9));
        assertEquals(-1, pagination.getPageOfIndex(10));
        assertEquals(-1, pagination.getPageOfIndex(-1));
    }

    @Test
    void sourceSmallerThanWindow_isSinglePage() {
        ScrollPagination<Integer> pagination = sevenSlotScroll(sourceOf(3));
        pagination.bind(newHost());

        assertEquals(1, pagination.getTotalPages());
        assertEquals(1, pagination.getPageOfIndex(2));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

The old 2.x test classes share the simple names `NormalPaginationTest`/`ScrollPaginationTest` until Task 5 deletes them, so use fully qualified `-Dtest` patterns:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.NormalPaginationTest,tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.ScrollPaginationTest"
```

Expected: FAIL (test compilation error: `package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination does not exist` — `PaginationHost`, `RenderedItem` and the engine classes are not implemented yet)

- [ ] **Step 3: Write the implementation**

`PaginationHost.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.List;
import java.util.UUID;

/**
 * The world-facing seam the relocated pagination engine talks to instead of the 2.x
 * {@code Viewer}/{@code InventoryEditor} pair. Implemented per (session, token) by the
 * pagination binding, so a view declaring several pagination tokens keeps {@code fillPage}
 * and {@code requestRender} disambiguated per token.
 */
@ApiStatus.Internal
public interface PaginationHost {

    /**
     * Returns whether the bound session is currently paintable.
     *
     * @return {@code true} while the session status is ACTIVE or TRANSITIONING
     */
    boolean isActive();

    /**
     * Applies one engine frame: item {@code i} is mapped onto {@code layout.slots().get(i)}.
     *
     * @param items  the rendered slot contents, one per layout slot
     * @param layout the fill order the items map onto
     */
    void fillPage(@NotNull List<RenderedItem> items, @NotNull Layout layout);

    /**
     * Requests a reactive settle pass for the owning token: the engine repaints the token's
     * pagination area and every component watching the token.
     */
    void requestRender();

    /**
     * Returns the player pages are being loaded for; populates the slim {@code PageRequest}.
     *
     * @return the player's unique id, or {@code null} only for engine-external test usage
     */
    @Nullable UUID playerId();

    /**
     * Returns the plugin that owns the view; asynchronously completed settles are scheduled
     * through it.
     *
     * @return the owning plugin
     */
    @NotNull Plugin plugin();
}
```

`RenderedItem.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ItemComponentBuilderImpl;

import java.util.Objects;

/**
 * One slot's worth of pagination engine output: a user-declared element builder, a plain
 * frame item (a {@code null} item clears the slot), or the failure sentinel that keeps the
 * slot's previous content entirely.
 */
@ApiStatus.Internal
public final class RenderedItem {

    private static final RenderedItem FAILURE = new RenderedItem(null, null, true);

    private final ItemComponentBuilderImpl elementBuilder;
    private final ItemStack plainItem;
    private final boolean failure;

    private RenderedItem(ItemComponentBuilderImpl elementBuilder, ItemStack plainItem, boolean failure) {
        this.elementBuilder = elementBuilder;
        this.plainItem = plainItem;
        this.failure = failure;
    }

    /**
     * Creates a slot output backed by a user-declared page element.
     *
     * @param builder the element declaration to materialize at fill time, not null
     * @return the rendered slot content
     * @throws NullPointerException if {@code builder} is null
     */
    public static @NotNull RenderedItem ofElement(@NotNull ItemComponentBuilderImpl builder) {
        return new RenderedItem(Objects.requireNonNull(builder, "builder"), null, false);
    }

    /**
     * Creates a plain frame item: a loading frame, a fallback filler or a slot clear.
     *
     * @param item the stack to paint, or {@code null} to clear the slot
     * @return the rendered slot content
     */
    public static @NotNull RenderedItem ofItem(@Nullable ItemStack item) {
        return new RenderedItem(null, item, false);
    }

    /**
     * Returns the failure sentinel: the slot keeps its previous content (item and click
     * handlers); on a first paint with no previous content the fallback item is painted instead.
     *
     * @return the shared failure sentinel
     */
    public static @NotNull RenderedItem failure() {
        return FAILURE;
    }

    /**
     * Returns the element declaration carried by this output.
     *
     * @return the element builder, or {@code null} unless created by {@link #ofElement}
     */
    public @Nullable ItemComponentBuilderImpl elementBuilder() {
        return elementBuilder;
    }

    /**
     * Returns the plain frame item carried by this output.
     *
     * @return the stack, or {@code null} for elements, failures and slot clears
     */
    public @Nullable ItemStack plainItem() {
        return plainItem;
    }

    /**
     * Returns whether this output is the failure sentinel.
     *
     * @return {@code true} for {@link #failure()} outputs
     */
    public boolean isFailure() {
        return failure;
    }
}
```

`Paginator.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationHost;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

/**
 * Internal page-source geometry engine: navigation, page-load dispatch and frame painting
 * over a {@link PaginationHost}. This is the relocated 2.x {@code Pagination} interface,
 * renamed because the public {@code pagination.Pagination} name now belongs to the reactive
 * token.
 *
 * @param <T> the source element type
 */
@ApiStatus.Internal
public interface Paginator<T> {

    /**
     * Binds the engine against a freshly opened host: sets the host, derives the navigation
     * state for the current (possibly pre-recorded) page and dispatches the initial load
     * without requesting a render — the open's initial paint covers it.
     *
     * <p>Must be called once before any painting method is invoked. Eager sources settle
     * inline during bind, so their items are ready before the first paint; async sources
     * leave {@link #isLoading()} {@code true} so the first paint shows the loading frame.
     *
     * @param host the world-facing seam the engine paints and renders through
     */
    void bind(@NotNull PaginationHost host);

    /**
     * Paints the items of the current page through {@link PaginationHost#fillPage}.
     */
    void insertPageItems();

    /**
     * Navigates directly to the given 1-indexed page.
     *
     * <p>The target is clamped to at least 1, and to {@link #getTotalPages()} once the backing
     * source's totals are known. For async sources, navigation issued before the first load
     * completes is honored optimistically and re-clamped downward when totals arrive. A call
     * that targets the current page while a request for it is already in flight is ignored;
     * use {@link #refresh()} to force a reload. Before {@link #bind(PaginationHost)} the call
     * only records the target page; {@code bind} dispatches the load for it.
     *
     * @param page the 1-indexed page to navigate to
     */
    void changePage(int page);

    /**
     * Advances to the next page if one exists.
     */
    void nextPage();

    /**
     * Returns whether forward navigation is possible.
     *
     * @return {@code true} if there is at least one page after the current one
     */
    boolean hasNextPage();

    /**
     * Returns to the previous page if one exists.
     */
    void previousPage();

    /**
     * Returns whether backward navigation is possible.
     *
     * @return {@code true} if there is at least one page before the current one
     */
    boolean hasPreviousPage();

    /**
     * Returns the page count of the current source.
     *
     * @return the total number of pages backing the current source, at least 1
     */
    int getTotalPages();

    /**
     * Returns the current page.
     *
     * @return the 1-indexed current page number
     */
    int getCurrentPage();

    /**
     * Returns the element count of the current source.
     *
     * @return the total number of elements in the backing source, independent of how many
     * are loaded locally
     */
    int getTotalElements();

    /**
     * Returns the page capacity.
     *
     * @return the maximum number of items rendered per page
     */
    int getItemPageLimit();

    /**
     * Maps a global source index onto the page it appears on.
     *
     * @param index the global element index
     * @return the 1-indexed page containing the given index (for scroll paginators: the first
     * page on which the index becomes visible), or {@code -1} if the index is outside
     * {@code [0, getTotalElements())}
     */
    int getPageOfIndex(int index);

    /**
     * Returns the in-flight state of the source.
     *
     * @return {@code true} while an async page load for this paginator is in flight; always
     * {@code false} for eager sources
     */
    boolean isLoading();

    /**
     * Returns the most recent load failure.
     *
     * @return the failure of the most recent async page load, or {@code null}; cleared when a
     * new load is dispatched. Always {@code null} for eager sources.
     */
    @Nullable Throwable lastError();

    /**
     * Re-requests the current page, invalidating any cached copy first. For async sources this
     * is the supported idiom to re-query after the backing store changed; for eager sources it
     * re-renders the current page.
     */
    void refresh();

    /**
     * Swaps the backing source and clears the locally held items. Dispatches nothing and
     * renders nothing — callers drive the re-request; a swap before
     * {@link #bind(PaginationHost)} is picked up by the bind-time dispatch.
     *
     * @param source the new page source, not null
     * @throws NullPointerException if {@code source} is null
     */
    void replaceSource(@NotNull PageSource<T> source);
}
```

`PageItemFactory.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;

/**
 * Creates the {@link RenderedItem} for one element of the current page; supplied by the
 * pagination binding so the engine stays free of renderer concerns.
 *
 * @param <T> the source element type
 */
@FunctionalInterface
@ApiStatus.Internal
public interface PageItemFactory<T> {

    /**
     * Creates the rendered slot content for one page element.
     *
     * @param index the zero-based position of the element within the current page
     * @param value the source element
     * @return the rendered slot content, never null
     */
    @NotNull RenderedItem create(int index, @NotNull T value);
}
```

`AbstractPageSourcePagination.java` — the 2.x `pagination/impl/AbstractPageSourcePagination.java` with ONLY the pinned substitutions applied (`Viewer viewer` → `PaginationHost host`, suppliers → `Supplier<RenderedItem>`/`PageItemFactory<T>`, `InventoryLayout` → `Layout`, `editor.fillPage(items, layout, this)` → `host.fillPage(items, layout)`, `renderIfOnline` body → host-active guard + `requestRender`, `PageRequest` built from `host.playerId()`/`host.plugin()`, `setSource(List)` → `replaceSource(PageSource)` without warning or dispatch) **plus the ONE pinned reorder from rule 2: the unbound-host record-only branch moves to the top of `changePageInternal`, ahead of the totals-known clamp** (pre-bind `itemPageLimit` is 0 and the clamp's `getTotalPages()` would divide by zero for a non-empty eager source — a latent 2.x bug the pending-target replay would otherwise trigger; observably behavior-preserving, see pinned rule 2). Everything else — `dispatch` with volatile inline-settle detection, `onSettle` with rollback, downward re-clamp and the WARNING catch, the volatile `currentItems`, all comments — is preserved line-for-line:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationHost;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Skeleton for {@link Paginator} implementations backed by a {@link PageSource}. Owns the
 * machinery shared by every pagination type: request dispatch with inline-settle detection,
 * transactional navigation with rollback on failed loads, downward re-clamping when totals
 * shrink, loading-frame rendering and the source swap behind {@link #replaceSource(PageSource)}.
 *
 * <p>Subclasses contribute the per-type geometry: how the current page maps to a request offset
 * ({@link #requestOffset()}), which layout the page renders into ({@link #renderLayout()}), and
 * which navigation state must be snapshotted for rollback ({@link #navigationSnapshot()} /
 * {@link #restoreNavigation(Object)} / {@link #commitNavigation(int)}).
 *
 * <p>Settle failures are logged through a per-class JUL logger (visible in the server console)
 * rather than a plugin logger, since a host may not be bound when they occur.
 *
 * @param <T> the source element type
 * @param <S> the navigation state snapshot used for failure rollback
 */
@Getter
abstract class AbstractPageSourcePagination<T, S> implements Paginator<T> {

    /**
     * Named after the concrete class so warnings point at the actual pagination type.
     */
    @Getter(AccessLevel.NONE)
    protected final Logger logger = Logger.getLogger(getClass().getName());

    protected final Supplier<RenderedItem> fallbackItem;
    protected final PageItemFactory<T> itemFactory;
    @Getter(AccessLevel.NONE)
    protected final Supplier<RenderedItem> loadingItem;
    @Getter(AccessLevel.NONE)
    protected PageSource<T> pageSource;
    @Getter(AccessLevel.NONE)
    private volatile List<T> currentItems = Collections.emptyList();
    // inline-settle detection assumes a single thread dispatches for this paginator at a time
    // (the main thread by default); concurrent dispatches may misclassify an inline settle as
    // asynchronous, costing only a redundant render
    @Getter(AccessLevel.NONE)
    private volatile Thread dispatchingThread;
    protected PaginationHost host;
    protected int currentPage = 1;
    protected int itemPageLimit;

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemFactory  renders one source element, not null
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    AbstractPageSourcePagination(@Nullable Supplier<RenderedItem> fallbackItem,
                                 @NotNull PageItemFactory<T> itemFactory,
                                 @Nullable Supplier<RenderedItem> loadingItem,
                                 @NotNull PageSource<T> pageSource) {
        this.fallbackItem = fallbackItem;
        this.itemFactory = itemFactory;
        this.loadingItem = loadingItem;
        this.pageSource = Objects.requireNonNull(pageSource, "pageSource is required.");
    }

    /**
     * Derives the layout state (page limit, pattern, ...) for the current page. Invoked by
     * {@link #bind(PaginationHost)} after the host is bound and before the initial request
     * dispatch.
     */
    protected abstract void initNavigationState();

    /**
     * @return the global element offset of the first item of the current page
     */
    protected abstract int requestOffset();

    /**
     * @return a snapshot of the navigation state to restore if the dispatched load fails
     */
    protected abstract S navigationSnapshot();

    /**
     * Restores the navigation state captured by {@link #navigationSnapshot()} after a failed
     * load, including clearing anything the failed navigation already painted.
     *
     * @param snapshot the pre-dispatch navigation state
     */
    protected abstract void restoreNavigation(S snapshot);

    /**
     * Commits navigation to the already-clamped target page: updates the current page and any
     * per-type layout state. Only invoked with a host bound.
     *
     * @param target the 1-indexed page to commit
     */
    protected abstract void commitNavigation(int target);

    /**
     * @return the layout the current page renders into
     */
    protected abstract Layout renderLayout();

    @Override
    public void bind(@NotNull PaginationHost host) {
        this.host = host;
        initNavigationState();
        dispatch(navigationSnapshot(), false);
    }

    @Override
    public void nextPage() {
        this.changePage(this.currentPage + 1);
    }

    @Override
    public boolean hasNextPage() {
        return this.currentPage + 1 <= this.getTotalPages();
    }

    @Override
    public void previousPage() {
        this.changePage(this.currentPage - 1);
    }

    @Override
    public boolean hasPreviousPage() {
        return this.currentPage > 1;
    }

    @Override
    public void insertPageItems() {
        Layout layout = renderLayout();
        int limit = layout.slots().size();
        List<T> items = this.currentItems;
        boolean loading = this.pageSource.isLoading();
        List<RenderedItem> renderedItems = new LinkedList<>();

        for (int i = 0; i < limit; i++) {
            if (loading) {
                renderedItems.add(loadingOrFallback());
            } else if (i < items.size()) {
                renderedItems.add(this.itemFactory.create(i, items.get(i)));
            } else {
                renderedItems.add(emptyOrFallback());
            }
        }

        this.host.fillPage(renderedItems, layout);
    }

    @Override
    public void changePage(int page) {
        changePageInternal(page, false);
    }

    private void changePageInternal(int page, boolean forceDispatch) {
        // no host bound yet: record the target only; bind derives the layout state for it and
        // dispatches the load. this branch sits BEFORE the totals-known clamp on purpose (a
        // deliberate reorder of the 2.x body): pre-bind itemPageLimit is 0, so the clamp's
        // getTotalPages() would divide by zero for a non-empty eager source; the clamp is
        // unnecessary here anyway — there is never an in-flight request pre-bind, and an
        // overshooting recorded target is corrected by the settle's downward re-clamp
        if (this.host == null) {
            this.currentPage = Math.max(1, page);
            return;
        }
        int target = Math.max(1, page);
        if (this.pageSource.totalsKnown()) {
            target = Math.min(target, this.getTotalPages());
        }
        if (!forceDispatch && target == this.currentPage && this.pageSource.isLoading()) {
            return;
        }
        S rollback = navigationSnapshot();
        commitNavigation(target);
        dispatch(rollback, true);
    }

    private void dispatch(S rollback, boolean render) {
        PageRequest request = new PageRequest(this.currentPage, this.itemPageLimit,
                requestOffset(), this.host.playerId(), this.host.plugin());
        this.dispatchingThread = Thread.currentThread();
        try {
            this.pageSource.request(request, (result, error) -> onSettle(rollback, result, error));
        } finally {
            this.dispatchingThread = null;
        }
        if (render) {
            renderIfOnline();
        }
    }

    private void onSettle(S rollback, PageResult<T> result, Throwable error) {
        boolean inline = Thread.currentThread() == this.dispatchingThread;
        try {
            if (error != null) {
                restoreNavigation(rollback);
            } else {
                this.currentItems = result.getItems();
                if (this.pageSource.totalsKnown() && this.currentPage > this.getTotalPages()) {
                    changePageInternal(this.getTotalPages(), true);
                    return;
                }
            }
            if (!inline) {
                renderIfOnline();
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to apply a settled page load.", t);
        }
    }

    private void renderIfOnline() {
        PaginationHost host = this.host;
        if (host == null || !host.isActive()) {
            return;
        }
        host.requestRender();
    }

    @Override
    public void replaceSource(@NotNull PageSource<T> source) {
        this.pageSource = Objects.requireNonNull(source, "source is required.");
        this.currentItems = Collections.emptyList();
    }

    @Override
    public boolean isLoading() {
        return this.pageSource.isLoading();
    }

    @Override
    public @Nullable Throwable lastError() {
        return this.pageSource.lastError();
    }

    @Override
    public int getTotalElements() {
        return this.pageSource.totalElements();
    }

    @Override
    public void refresh() {
        this.pageSource.invalidate();
        changePageInternal(this.currentPage, true);
    }

    /**
     * @return the configured fallback item, or a slot clear when none was set
     */
    protected final @NotNull RenderedItem emptyOrFallback() {
        return fallbackItem == null ? RenderedItem.ofItem(null) : fallbackItem.get();
    }

    /**
     * @return the configured loading item, falling back to {@link #emptyOrFallback()}
     */
    protected final @NotNull RenderedItem loadingOrFallback() {
        return loadingItem != null ? loadingItem.get() : emptyOrFallback();
    }
}
```

`NormalPagination.java` — geometry math verbatim from the 2.x file:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.function.Supplier;

/**
 * Page-by-page paginator. Each page renders a contiguous slice of the source, clamped to
 * {@code itemPageLimit} (the number of slots in the configured layout). Pages come from a
 * {@link PageSource}: an in-memory list by default, or an async supplier configured by the
 * declaring view.
 *
 * @param <T> the source element type
 */
@Getter
@ApiStatus.Internal
public class NormalPagination<T> extends AbstractPageSourcePagination<T, Integer> {

    private final Layout layout;

    /**
     * Creates an eager paginator over an initially empty source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemFactory  renders one source element, not null
     * @param layout       the slots the page renders into, not null
     */
    public NormalPagination(@Nullable Supplier<RenderedItem> fallbackItem,
                            @NotNull PageItemFactory<T> itemFactory,
                            @NotNull Layout layout) {
        this(fallbackItem, itemFactory, layout, null, EagerPageSource.empty());
    }

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemFactory  renders one source element, not null
     * @param layout       the slots the page renders into, not null
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    public NormalPagination(@Nullable Supplier<RenderedItem> fallbackItem,
                            @NotNull PageItemFactory<T> itemFactory,
                            @NotNull Layout layout,
                            @Nullable Supplier<RenderedItem> loadingItem,
                            @NotNull PageSource<T> pageSource) {
        super(fallbackItem, itemFactory, loadingItem, pageSource);
        this.layout = layout;
    }

    @Override
    protected void initNavigationState() {
        this.itemPageLimit = layout.slots().size();
    }

    @Override
    protected int requestOffset() {
        return (this.currentPage - 1) * this.itemPageLimit;
    }

    @Override
    protected Integer navigationSnapshot() {
        return this.currentPage;
    }

    @Override
    protected void restoreNavigation(Integer snapshot) {
        this.currentPage = snapshot;
    }

    @Override
    protected void commitNavigation(int target) {
        this.currentPage = target;
    }

    @Override
    protected Layout renderLayout() {
        return this.layout;
    }

    @Override
    public int getTotalPages() {
        int total = this.pageSource.totalElements();
        if (total == 0) {
            return 1;
        }
        return (int) (((long) total + this.itemPageLimit - 1) / this.itemPageLimit);
    }

    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.pageSource.totalElements()) {
            return -1;
        }
        return index / this.itemPageLimit + 1;
    }
}
```

`ScrollPagination.java` — geometry math verbatim from the 2.x file:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.function.Supplier;

/**
 * Sliding-window paginator. Each "page" advances the visible window by one source element
 * rather than chunking into discrete pages. Pages come from a {@link PageSource}: an
 * in-memory list by default, or an async supplier configured by the declaring view.
 *
 * @param <T> the source element type
 */
@Getter
@ApiStatus.Internal
public class ScrollPagination<T> extends AbstractPageSourcePagination<T, Integer> {

    private final Layout layout;

    /**
     * Creates an eager paginator over an initially empty source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemFactory  renders one source element, not null
     * @param layout       the slots of the sliding window, not null
     */
    public ScrollPagination(@Nullable Supplier<RenderedItem> fallbackItem,
                            @NotNull PageItemFactory<T> itemFactory,
                            @NotNull Layout layout) {
        this(fallbackItem, itemFactory, layout, null, EagerPageSource.empty());
    }

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemFactory  renders one source element, not null
     * @param layout       the slots of the sliding window, not null
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    public ScrollPagination(@Nullable Supplier<RenderedItem> fallbackItem,
                            @NotNull PageItemFactory<T> itemFactory,
                            @NotNull Layout layout,
                            @Nullable Supplier<RenderedItem> loadingItem,
                            @NotNull PageSource<T> pageSource) {
        super(fallbackItem, itemFactory, loadingItem, pageSource);
        this.layout = layout;
    }

    @Override
    protected void initNavigationState() {
        this.itemPageLimit = layout.slots().size();
    }

    @Override
    protected int requestOffset() {
        // the window slides one element per page
        return this.currentPage - 1;
    }

    @Override
    protected Integer navigationSnapshot() {
        return this.currentPage;
    }

    @Override
    protected void restoreNavigation(Integer snapshot) {
        this.currentPage = snapshot;
    }

    @Override
    protected void commitNavigation(int target) {
        this.currentPage = target;
    }

    @Override
    protected Layout renderLayout() {
        return this.layout;
    }

    @Override
    public int getTotalPages() {
        int total = this.pageSource.totalElements();
        return Math.max(1, (total - this.itemPageLimit) + 1);
    }

    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.pageSource.totalElements()) {
            return -1;
        }
        return Math.max(1, index - this.itemPageLimit + 2);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.NormalPaginationTest,tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.ScrollPaginationTest"
```

Expected: PASS (11 tests)

- [ ] **Step 5: Full module check**

The 2.x cluster is untouched, so the whole module must stay green:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationHost.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/RenderedItem.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/Paginator.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/PageItemFactory.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/AbstractPageSourcePagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/NormalPagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/ScrollPagination.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/FakePaginationHost.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/NormalPaginationTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/ScrollPaginationTest.java
git commit -m "feat(inventory-api): relocate pagination engine core behind the PaginationHost seam

Copies the 2.x AbstractPageSourcePagination, NormalPagination and
ScrollPagination into internal.pagination.engine with the navigation,
dispatch and settle algorithms preserved verbatim. The engine now talks
to a PaginationHost instead of Viewer/InventoryEditor, emits RenderedItem
frames through PageItemFactory, and setSource becomes the dispatch-free
replaceSource. The 2.x cluster stays untouched until Task 5."
```

---

### Task 4: Relocated PatternPagination + async engine flows

Completes the engine relocation started in Task 3: copies the 2.x `PatternPagination` into `internal.pagination.engine` (cycle arithmetic verbatim; `PatternState` now holds `Layout` references; `clearPattern` paints fallback fillers through `host.fillPage`) and ports the 2.x regression suites — all 9 `PatternPaginationTest` methods plus the `AsyncPaginationIntegrationTest` settle/rollback/re-clamp/pre-bind flows — onto the relocated engine, `FakePaginationHost` (Task 3) and the rewritten `BukkitSettleDispatcher` (Task 2).

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/PatternPagination.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/PatternPaginationTest.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/AsyncPaginationEngineTest.java

- [ ] **Step 1: Write the failing tests**

`PatternPaginationTest.java` — ports all 9 methods of the 2.x `pagination/impl/PatternPaginationTest` with the same names and assertions, host-seam-adapted: builders become direct construction, `apply()` becomes `insertPageItems()`, `setSource` becomes constructing over an `EagerPageSource`, and editor captors become `FakePaginationHost.fillPageCalls()`. One assertion is layout-API-adapted: `diamondLayout_leavesColumnZeroEmpty` asserted `getColumnSizes().containsKey(0)` on the 2.x `InventoryLayout`; v3 `Layout` has no column map, so the equivalent assertion is that no fill slot falls into column zero.

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternPaginationTest {

    private static final PageItemFactory<Integer> ITEM_FACTORY =
            (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value));

    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void applyAndChangePage_withCyclingPatterns_doNotThrow() {
        Paginator<Integer> pagination = samplePatternPagination(sourceOfFifty());

        pagination.bind(newHost());

        assertDoesNotThrow(pagination::insertPageItems);
        assertTrue(pagination.getTotalPages() >= 1);

        if (pagination.hasNextPage()) {
            assertDoesNotThrow(pagination::nextPage);
        }
        if (pagination.hasPreviousPage()) {
            assertDoesNotThrow(pagination::previousPage);
        }
        assertDoesNotThrow(() -> pagination.changePage(pagination.getTotalPages()));
    }

    @Test
    void cyclingPatterns_fiftyItems_renderEverySourceValueExactlyOnce() {
        Paginator<Integer> pagination = samplePatternPagination(sourceOfFifty());
        FakePaginationHost host = newHost();

        pagination.bind(host);

        for (int page = 1; page <= pagination.getTotalPages(); page++) {
            pagination.changePage(page);
            pagination.insertPageItems();
        }

        List<Integer> rendered = diamondAmounts(host.fillPageCalls());

        assertEquals(
                IntStream.rangeClosed(1, 50).boxed().toList(),
                rendered.stream().sorted().toList(),
                "every source value 1..50 must appear exactly once across all pages (no skips, no overlap)"
        );
    }

    @Test
    void diamondLayout_leavesColumnZeroEmpty() {
        // v3 Layout exposes no column map; the 2.x getColumnSizes().containsKey(0) assertion
        // becomes: no fill slot may sit in column zero
        for (int slot : centeredDiamondLayout().slots()) {
            assertNotEquals(0, slot % 9, "slot " + slot + " must not sit in column zero");
        }
    }

    @Test
    void diamondPattern_pageOne_fillsSequentiallyFromFirstItem() {
        Layout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond, sourceOfTwenty());
        FakePaginationHost host = newHost();

        pagination.bind(host);
        pagination.insertPageItems();

        List<Integer> expectedValues = IntStream.rangeClosed(1, 13).boxed().toList();
        assertMappedSourceValues(host, diamond, expectedValues, false);
    }

    @Test
    void getPageOfIndex_diamondLayout_mapsSequentialPages() {
        PatternPagination<Integer> pagination =
                diamondOnlyPagination(centeredDiamondLayout(), sourceOfTwentyOne());

        pagination.bind(newHost());

        assertEquals(1, pagination.getPageOfIndex(0));
        assertEquals(1, pagination.getPageOfIndex(12));
        assertEquals(2, pagination.getPageOfIndex(13));
        assertEquals(2, pagination.getPageOfIndex(20));
        assertEquals(-1, pagination.getPageOfIndex(21));
        assertEquals(-1, pagination.getPageOfIndex(-1));
    }

    @Test
    void diamondPattern_twentyOneItems_tailIndexReachableOnLastPage() {
        Layout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond, sourceOfTwentyOne());

        pagination.bind(newHost());

        assertEquals(2, pagination.getTotalPages());
        assertTrue(pagination.hasNextPage());

        pagination.changePage(2);
        pagination.insertPageItems();

        assertEquals(2, pagination.getCurrentPage());
        assertFalse(pagination.hasNextPage());
    }

    @Test
    void diamondPattern_allPages_unionCoversEverySourceValue() {
        Layout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond, sourceOfTwentyOne());
        FakePaginationHost host = newHost();

        pagination.bind(host);

        for (int page = 1; page <= pagination.getTotalPages(); page++) {
            pagination.changePage(page);
            pagination.insertPageItems();
        }

        Set<Integer> renderedValues = new HashSet<>(diamondAmounts(host.fillPageCalls()));

        IntStream.rangeClosed(1, 21).forEach(expected ->
                assertTrue(
                        renderedValues.contains(expected),
                        "source value " + expected + " must appear on some page"
                )
        );
    }

    @Test
    void diamondPattern_pageTwo_advancesSourceWindow() {
        Layout diamond = centeredDiamondLayout();
        PatternPagination<Integer> pagination = diamondOnlyPagination(diamond, sourceOf(26));
        FakePaginationHost host = newHost();

        pagination.bind(host);
        pagination.changePage(2);
        pagination.insertPageItems();

        List<Integer> expectedValues = IntStream.rangeClosed(14, 26).boxed().toList();
        assertMappedSourceValues(host, diamond, expectedValues, true);
    }

    @Test
    void mixedGridAndOrderedSlotsPatterns_pageTwo_fillsOrderedLayoutInGivenOrder() {
        Layout diamond = centeredDiamondLayout(); // 13 slots, grid-based
        Layout snake = Layout.ofSlots(36, 27, 18, 9, 0, 1, 10, 19); // 8 slots
        PatternPagination<Integer> pagination = new PatternPagination<>(
                PatternPaginationTest::glassFiller,
                ITEM_FACTORY,
                List.of(diamond, snake),
                null,
                new EagerPageSource<>(sourceOf(21))); // 13 on the diamond page + 8 on the snake page
        FakePaginationHost host = newHost();

        pagination.bind(host);

        assertEquals(2, pagination.getTotalPages());

        pagination.changePage(2);
        pagination.insertPageItems();

        List<Integer> expectedValues = IntStream.rangeClosed(14, 21).boxed().toList();
        assertMappedSourceValues(host, snake, expectedValues, true);
    }

    private static void assertMappedSourceValues(
            FakePaginationHost host,
            Layout layout,
            List<Integer> expectedValues,
            boolean lastFillPageOnly
    ) {
        List<FakePaginationHost.FillPageCall> calls = new ArrayList<>();
        for (FakePaginationHost.FillPageCall call : host.fillPageCalls()) {
            if (call.layout == layout) {
                calls.add(call);
            }
        }
        assertFalse(calls.isEmpty(), "expected at least one fillPage call for the layout");
        if (!lastFillPageOnly) {
            assertEquals(1, calls.size(), "expected exactly one fillPage call for the layout");
        }

        List<RenderedItem> items = calls.get(calls.size() - 1).items;
        assertEquals(expectedValues.size(), items.size());

        for (int i = 0; i < expectedValues.size(); i++) {
            assertEquals(
                    expectedValues.get(i).intValue(),
                    items.get(i).plainItem().getAmount(),
                    "slot fill order index " + i
            );
        }
    }

    private static List<Integer> diamondAmounts(List<FakePaginationHost.FillPageCall> calls) {
        List<Integer> rendered = new ArrayList<>();
        for (FakePaginationHost.FillPageCall call : calls) {
            for (RenderedItem item : call.items) {
                ItemStack stack = item.plainItem();
                if (stack != null && stack.getType() == Material.DIAMOND) {
                    rendered.add(stack.getAmount());
                }
            }
        }
        return rendered;
    }

    private static RenderedItem glassFiller() {
        return RenderedItem.ofItem(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
    }

    private static Layout centeredDiamondLayout() {
        return Layout.ofGrid(
                "    O    ",
                "   OOO   ",
                "  OOOOO  ",
                "   OOO   ",
                "    O    ",
                "         "
        );
    }

    private static PatternPagination<Integer> diamondOnlyPagination(Layout diamond, List<Integer> source) {
        return new PatternPagination<>(
                PatternPaginationTest::glassFiller,
                ITEM_FACTORY,
                List.of(diamond),
                null,
                new EagerPageSource<>(source));
    }

    private static Paginator<Integer> samplePatternPagination(List<Integer> source) {
        return new PatternPagination<>(
                PatternPaginationTest::glassFiller,
                ITEM_FACTORY,
                List.of(
                        Layout.ofGrid(
                                "    O    ",
                                "   OOO   ",
                                "  OOOOO  ",
                                "   OOO   ",
                                "    O    ",
                                "         "
                        ),
                        Layout.ofGrid(
                                "         ",
                                " OOOOOOO ",
                                " OOOOOOO ",
                                " OOOOOOO ",
                                " OOOOOOO ",
                                "         "
                        ),
                        Layout.ofGrid(
                                "         ",
                                "         ",
                                "  OOOOO  ",
                                "         ",
                                "         ",
                                "         "
                        )),
                null,
                new EagerPageSource<>(source));
    }

    private FakePaginationHost newHost() {
        return new FakePaginationHost(UUID.randomUUID(), plugin);
    }

    private static List<Integer> sourceOfTwenty() {
        return sourceOf(20);
    }

    private static List<Integer> sourceOfTwentyOne() {
        return sourceOf(21);
    }

    private static List<Integer> sourceOfFifty() {
        return sourceOf(50);
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }
}
```

`AsyncPaginationEngineTest.java` — ports the 2.x `AsyncPaginationIntegrationTest` flows onto the relocated engine. The real `BukkitSettleDispatcher` (rewritten in Task 2 to read `request.plugin()`) routes off-thread settles through the MockBukkit scheduler, so the host must expose the MockBukkit plugin. Renders are asserted via `FakePaginationHost.requestRenderCount()` (the 2.x `updateInventory` assertions); `setSource_afterInit_neverTriggersUpdateInventory` has no equivalent (`replaceSource` dispatches nothing by contract — covered by Task 3's `replaceSource_beforeBind_isHonoredByBind`), and `settleAfterLogoff_doesNotThrow`/`asyncSettle_repaintsInventoryForOnlineViewer` become the inactive-host and off-thread-completion tests:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.BukkitSettleDispatcher;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncPaginationEngineTest {

    private static final PageItemFactory<Integer> ITEM_FACTORY =
            (index, value) -> RenderedItem.ofItem(new ItemStack(Material.DIAMOND, value));

    private ServerMock server;
    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; cancel them first so a settle queued by a
        // test that deliberately never ticked is not run against a torn-down fixture
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    /**
     * Captures every request and exposes its futures for hand-completion.
     */
    private static final class CapturingSupplier implements AsyncPageSupplier<Integer> {
        private final List<PageRequest> requests = new ArrayList<>();
        private final List<CompletableFuture<PageResult<Integer>>> futures = new ArrayList<>();

        @Override
        public CompletableFuture<PageResult<Integer>> load(PageRequest request) {
            requests.add(request);
            CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        }
    }

    private FakePaginationHost newHost() {
        return new FakePaginationHost(UUID.randomUUID(), plugin);
    }

    private static Layout threeSlots() {
        return Layout.ofSlots(0, 1, 2);
    }

    private static NormalPagination<Integer> asyncNormal(CapturingSupplier supplier) {
        return new NormalPagination<>(
                null,
                ITEM_FACTORY,
                threeSlots(),
                () -> RenderedItem.ofItem(new ItemStack(Material.CLOCK)),
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher()));
    }

    @Test
    void whileLoading_rendersLoadingItemInEverySlot() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        FakePaginationHost host = newHost();

        pagination.bind(host);
        assertTrue(pagination.isLoading());
        pagination.insertPageItems();

        List<RenderedItem> items = host.lastFillPage().items;
        assertEquals(3, items.size());
        for (RenderedItem item : items) {
            assertEquals(Material.CLOCK, item.plainItem().getType());
        }
    }

    @Test
    void offThreadCompletion_appliesOnNextSchedulerTick() throws Exception {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        FakePaginationHost host = newHost();

        pagination.bind(host);

        Thread completer = new Thread(() ->
                supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 9)));
        completer.start();
        completer.join(5000);

        // the settle was dispatched to the main thread, not applied inline on the completer
        assertTrue(pagination.isLoading(), "state must not be applied before the scheduler tick");
        assertEquals(0, host.requestRenderCount(), "no render may be requested before the scheduler tick");
        server.getScheduler().performOneTick();

        assertFalse(pagination.isLoading());
        assertEquals(9, pagination.getTotalElements());
        assertEquals(1, host.requestRenderCount(), "the settled load must request exactly one render");
        pagination.insertPageItems();
        assertEquals(Material.DIAMOND, host.lastFillPage().items.get(0).plainItem().getType());
    }

    @Test
    void failedLoad_rollsBackToLastRequestedPageAndInvokesErrorCallback() {
        CapturingSupplier supplier = new CapturingSupplier();
        AtomicReference<Throwable> callbackError = new AtomicReference<>();
        NormalPagination<Integer> pagination = new NormalPagination<>(
                null,
                ITEM_FACTORY,
                threeSlots(),
                null,
                new AsyncPageSource<>(supplier, (request, error) -> callbackError.set(error),
                        null, null, 128, new BukkitSettleDispatcher()));

        pagination.bind(newHost());
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 9)); // page 1 ok

        pagination.nextPage(); // page 2 in flight
        pagination.nextPage(); // page 3 in flight, supersedes page 2
        assertEquals(3, pagination.getCurrentPage());
        supplier.futures.get(2).completeExceptionally(new RuntimeException("db down"));

        // preserved 2.x quirk (NEVER "fix"): the rollback restores the page the previous
        // dispatch REQUESTED (page 2, which never rendered), not the last rendered page 1
        assertEquals(2, pagination.getCurrentPage(),
                "failed navigation must roll back to the last requested page");
        assertNotNull(callbackError.get());
        assertNotNull(pagination.lastError());
        assertFalse(pagination.isLoading());
    }

    @Test
    void navigationBeforeFirstSettle_isHonoredThenReclampedByTotals() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.bind(newHost());
        pagination.changePage(5); // totals unknown: honored optimistically

        assertEquals(5, pagination.getCurrentPage());
        assertEquals(12, supplier.requests.get(1).getOffset(), "page 5 of size 3 => offset 12");

        // totals arrive: only 2 pages exist => re-clamp dispatches page 2
        supplier.futures.get(1).complete(PageResult.of(List.of(), 6));

        assertEquals(2, pagination.getCurrentPage());
        assertEquals(3, supplier.requests.get(2).getOffset());
    }

    @Test
    void changePage_targetingInFlightPage_doesNotRedispatch() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.bind(newHost());
        int dispatched = supplier.requests.size(); // bind's page-1 request

        pagination.changePage(1);
        pagination.changePage(1);

        assertEquals(dispatched, supplier.requests.size(),
                "click spam on the loading page must not re-dispatch");
    }

    @Test
    void refresh_invalidatesCacheAndForcesRedispatch() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = new NormalPagination<>(
                null,
                ITEM_FACTORY,
                threeSlots(),
                null,
                new AsyncPageSource<>(supplier, null, null, Duration.ofMinutes(5), 128,
                        new BukkitSettleDispatcher()));

        pagination.bind(newHost());
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 9)); // page 1 now cached
        int dispatched = supplier.requests.size();

        pagination.refresh();

        // without invalidation this would be a cache hit and the supplier would not be called
        assertEquals(dispatched + 1, supplier.requests.size());
        assertEquals(1, supplier.requests.get(supplier.requests.size() - 1).getPage());
    }

    @Test
    void patternFailedLoad_restoresPagePatternAndLimitAndClearsFailedPattern() {
        CapturingSupplier supplier = new CapturingSupplier();
        Layout five = Layout.ofSlots(0, 1, 2, 3, 4);
        Layout two = Layout.ofSlots(9, 10);
        PatternPagination<Integer> pagination = new PatternPagination<>(
                null,
                ITEM_FACTORY,
                List.of(five, two),
                null,
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher()));
        FakePaginationHost host = newHost();

        pagination.bind(host);
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3, 4, 5), 9));

        pagination.nextPage(); // switches to pattern `two`, limit 2
        assertEquals(2, pagination.getCurrentPage());
        supplier.futures.get(1).completeExceptionally(new RuntimeException("db down"));

        assertEquals(1, pagination.getCurrentPage(), "failed navigation must roll back the page");
        assertEquals(five, pagination.getCurrentPattern(), "failed navigation must roll back the pattern");
        assertEquals(5, pagination.getItemPageLimit(), "failed navigation must roll back the page limit");

        // the failed pattern's slots were cleared through the host before the state restore
        FakePaginationHost.FillPageCall clearCall = host.lastFillPage();
        assertEquals(two, clearCall.layout, "the failed pattern's slots must be cleared");
        assertEquals(2, clearCall.items.size());
        for (RenderedItem filler : clearCall.items) {
            assertNull(filler.plainItem(), "no fallback configured: the fillers must clear the slots");
            assertFalse(filler.isFailure());
        }
    }

    @Test
    void scrollSupplier_receivesSlidingWindowOffsets() {
        CapturingSupplier supplier = new CapturingSupplier();
        ScrollPagination<Integer> pagination = new ScrollPagination<>(
                null,
                ITEM_FACTORY,
                threeSlots(),
                null,
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher()));

        pagination.bind(newHost());
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 10));

        pagination.changePage(2);

        PageRequest second = supplier.requests.get(1);
        assertEquals(1, second.getOffset(), "scroll slides one element per page");
        assertEquals(3, second.getPageSize());
    }

    @Test
    void patternSupplier_receivesCumulativeOffsetsAndPerPatternSizes() {
        CapturingSupplier supplier = new CapturingSupplier();
        PatternPagination<Integer> pagination = new PatternPagination<>(
                null,
                ITEM_FACTORY,
                List.of(Layout.ofSlots(0, 1, 2, 3, 4),      // 5 slots
                        Layout.ofSlots(9, 10)),              // 2 slots
                null,
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher()));

        pagination.bind(newHost());
        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3, 4, 5), 9));

        pagination.changePage(2);

        PageRequest second = supplier.requests.get(1);
        assertEquals(5, second.getOffset(), "page 2 starts after pattern 1's 5 slots");
        assertEquals(2, second.getPageSize(), "page 2 uses pattern 2's slot count");
    }

    @Test
    void changePageBeforeBind_isDeferredToBind() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.changePage(3);

        assertTrue(supplier.requests.isEmpty(), "no host is bound yet; nothing must be dispatched");
        assertEquals(3, pagination.getCurrentPage());

        FakePaginationHost host = newHost();
        pagination.bind(host);

        assertEquals(1, supplier.requests.size());
        PageRequest request = supplier.requests.get(0);
        assertEquals(3, request.getPage());
        assertEquals(6, request.getOffset(), "page 3 of size 3 => offset 6");
        assertEquals(host.playerId(), request.playerId(),
                "the dispatched request must carry the host's player id");
        assertEquals(plugin, request.plugin(), "the dispatched request must carry the host's plugin");
    }

    @Test
    void changePageBeforeBind_onPattern_neverTouchesTheHost() {
        CapturingSupplier supplier = new CapturingSupplier();
        PatternPagination<Integer> pagination = new PatternPagination<>(
                null,
                ITEM_FACTORY,
                List.of(Layout.ofSlots(0, 1, 2, 3, 4),      // 5 slots
                        Layout.ofSlots(9, 10)),              // 2 slots
                null,
                new AsyncPageSource<>(supplier, null, null, null, 128, new BukkitSettleDispatcher()));

        pagination.changePage(2);
        pagination.changePage(3); // 2.x regression: NPE'd clearing the last pattern unbound

        assertTrue(supplier.requests.isEmpty(), "no host is bound yet; nothing must be dispatched");

        FakePaginationHost host = newHost();
        pagination.bind(host);

        assertEquals(1, supplier.requests.size());
        assertEquals(3, supplier.requests.get(0).getPage());
        assertEquals(7, supplier.requests.get(0).getOffset(), "page 3 starts after one full 7-slot cycle");
        assertEquals(5, supplier.requests.get(0).getPageSize(), "page 3 cycles back to the 5-slot pattern");
        assertTrue(host.fillPageCalls().isEmpty(),
                "pre-bind navigation is record-only: no pattern clear may reach the host");
    }

    @Test
    void settleWithInactiveHost_performsNoRequestRender() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        FakePaginationHost host = newHost();

        pagination.bind(host);
        host.setActive(false);

        supplier.futures.get(0).complete(PageResult.of(List.of(1, 2, 3), 9));

        // the data still applies; only the paint is skipped for the unpaintable session
        assertFalse(pagination.isLoading());
        assertEquals(9, pagination.getTotalElements());
        assertEquals(0, host.requestRenderCount(), "an inactive host must not be asked to render");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

The old 2.x `pagination/impl/PatternPaginationTest` shares its simple name until Task 5 deletes it, so use fully qualified `-Dtest` patterns:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.PatternPaginationTest,tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.AsyncPaginationEngineTest"
```

Expected: FAIL (test compilation error: `cannot find symbol: class PatternPagination` in package `tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine` — the relocated class does not exist yet)

- [ ] **Step 3: Write the implementation**

`PatternPagination.java` — the 2.x `pagination/impl/PatternPagination.java` with ONLY the pinned substitutions applied: `InventoryLayout` → `Layout` (`getSlots()` → `slots()`), the supplier seam swap, and `clearPattern` painting through `host.fillPage(fillers, pattern)` instead of `viewer.getEditor().fillPage(...)`. The cycle arithmetic (`cycleSize`, `fromPage`, `getPageIndex`, `getTotalPages`, `getPageOfIndex`), the snapshot/restore/commit transaction and all comments are preserved line-for-line. `PatternState` now holds `Layout` references:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine;

import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.RenderedItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Paginates a source list across one or more {@link Layout} patterns, cycling through the
 * patterns as the player pages forward. Each page renders a contiguous slice of the source whose
 * length equals the slot count of that page's pattern, so every source item appears exactly once
 * across the pages with no gaps or overlap.
 *
 * <p>The pattern controls only <em>where</em> and in <em>what order</em> items are placed: slots
 * are filled in the order of the layout's {@link Layout#slots()} list, which lets a pattern
 * lay items out horizontally, vertically, or in any custom order. Grid-based layouts derive that
 * order from their letters; ordered-slots layouts state it explicitly.
 *
 * <p>{@link #changePage(int)} records the previous layout in {@code lastPattern} and clears its
 * slots before rendering the new page, so cycling between patterns of different sizes leaves no
 * residual items behind. Pages come from a {@link PageSource}: an in-memory list by default, or
 * an async supplier configured by the declaring view.
 *
 * @param <T> the source element type
 */
@Getter
@ApiStatus.Internal
public class PatternPagination<T> extends AbstractPageSourcePagination<T, PatternPagination.PatternState> {

    private final List<Layout> patterns;
    @Getter(AccessLevel.NONE)
    private final int cycleSize;
    private Layout currentPattern;
    private Layout lastPattern;

    /**
     * Creates an eager paginator over an initially empty source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemFactory  renders one source element, not null
     * @param patterns     the layout patterns cycled across pages, not null or empty
     */
    public PatternPagination(@Nullable Supplier<RenderedItem> fallbackItem,
                             @NotNull PageItemFactory<T> itemFactory,
                             @NotNull List<Layout> patterns) {
        this(fallbackItem, itemFactory, patterns, null, EagerPageSource.empty());
    }

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemFactory  renders one source element, not null
     * @param patterns     the layout patterns cycled across pages, not null or empty
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    public PatternPagination(@Nullable Supplier<RenderedItem> fallbackItem,
                             @NotNull PageItemFactory<T> itemFactory,
                             @NotNull List<Layout> patterns,
                             @Nullable Supplier<RenderedItem> loadingItem,
                             @NotNull PageSource<T> pageSource) {
        super(fallbackItem, itemFactory, loadingItem, pageSource);
        this.patterns = patterns;
        int slots = 0;
        for (Layout pattern : patterns) {
            slots += pattern.slots().size();
        }
        this.cycleSize = slots;
    }

    @Override
    protected void initNavigationState() {
        this.currentPattern = fromPage(this.currentPage);
        this.itemPageLimit = this.currentPattern.slots().size();
    }

    @Override
    protected int requestOffset() {
        return getPageIndex(this.currentPage);
    }

    @Override
    protected PatternState navigationSnapshot() {
        return new PatternState(this.currentPage, this.currentPattern, this.lastPattern, this.itemPageLimit);
    }

    @Override
    protected void restoreNavigation(PatternState snapshot) {
        // clear the failed pattern's slots before restoring, so the last good items are not
        // left scrambled into the failed pattern's shape
        clearPattern(this.currentPattern);
        this.currentPage = snapshot.page;
        this.currentPattern = snapshot.currentPattern;
        this.lastPattern = snapshot.lastPattern;
        this.itemPageLimit = snapshot.itemPageLimit;
    }

    @Override
    protected void commitNavigation(int target) {
        this.currentPage = target;
        this.lastPattern = this.currentPattern;
        this.currentPattern = fromPage(target);
        this.itemPageLimit = this.currentPattern.slots().size();
        clearPattern(this.lastPattern);
    }

    @Override
    protected Layout renderLayout() {
        return this.currentPattern;
    }

    private void clearPattern(Layout pattern) {
        if (pattern == null) return;

        List<RenderedItem> fillers = new LinkedList<>();

        for (int i = 0; i < pattern.slots().size(); i++) {
            fillers.add(emptyOrFallback());
        }

        this.host.fillPage(fillers, pattern);
    }

    private Layout fromPage(int page) {
        return patterns.get((page - 1) % patterns.size());
    }

    @Override
    public int getTotalPages() {
        int total = this.pageSource.totalElements();
        if (total == 0) {
            return 1;
        }
        int fullCycles = total / this.cycleSize;
        int remainder = total % this.cycleSize;
        int pages = fullCycles * this.patterns.size();
        int consumed = 0;
        int patternIndex = 0;
        while (consumed < remainder) {
            consumed += this.patterns.get(patternIndex).slots().size();
            patternIndex++;
            pages++;
        }
        return Math.max(1, pages);
    }

    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.pageSource.totalElements()) {
            return -1;
        }
        int fullCycles = index / this.cycleSize;
        int remainder = index % this.cycleSize;
        int page = fullCycles * this.patterns.size() + 1;
        int consumed = 0;
        int patternIndex = 0;
        while (remainder >= consumed + this.patterns.get(patternIndex).slots().size()) {
            consumed += this.patterns.get(patternIndex).slots().size();
            patternIndex++;
            page++;
        }
        return page;
    }

    private int getPageIndex(int page) {
        int completedPages = page - 1;
        int fullCycles = completedPages / this.patterns.size();
        int partial = completedPages % this.patterns.size();
        int offset = fullCycles * this.cycleSize;
        for (int i = 0; i < partial; i++) {
            offset += this.patterns.get(i).slots().size();
        }
        return offset;
    }

    /**
     * Pre-dispatch navigation state restored when a page load fails.
     */
    static final class PatternState {
        private final int page;
        private final Layout currentPattern;
        private final Layout lastPattern;
        private final int itemPageLimit;

        private PatternState(int page, Layout currentPattern,
                             Layout lastPattern, int itemPageLimit) {
            this.page = page;
            this.currentPattern = currentPattern;
            this.lastPattern = lastPattern;
            this.itemPageLimit = itemPageLimit;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.PatternPaginationTest,tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.AsyncPaginationEngineTest"
```

Expected: PASS (21 tests: 9 pattern + 12 async)

- [ ] **Step 5: Full module check**

The 2.x cluster is still untouched, so the whole module must stay green:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/PatternPagination.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/PatternPaginationTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/AsyncPaginationEngineTest.java
git commit -m "feat(inventory-api): relocate PatternPagination and port async engine flows

Copies the 2.x PatternPagination into internal.pagination.engine with the
pattern cycle arithmetic preserved verbatim; PatternState holds Layout
references and clearPattern paints fallback fillers through host.fillPage.
Ports the nine pattern geometry tests and the async settle, rollback,
re-clamp, pre-bind and inactive-host flows onto the relocated engine,
FakePaginationHost and the rewritten BukkitSettleDispatcher."
```

---

### Task 5: Public-surface switchover

This is the one-shot deletion of the 2.x public pagination cluster (plan scope note 1). It requires Tasks 1–4: the geometry engines were already copied to `internal/pagination/engine` behind the `PaginationHost` seam, and `PageRequest`/`BukkitSettleDispatcher` were already rewritten with the old call sites patched, so deleting the old cluster here leaves no dangling references. Deleting the 2.x `pagination.Pagination` interface vacates that FQN for the NEW v3 token interface created in this same task.

The three new public interfaces (`Pagination`, `PaginationBuilder`, `PaginationItemRenderer`) are **compile-only in this task** — no implementation exists yet. `PaginationBuilderImpl`/`PaginationImpl` land in Task 8 and the `View.paginate*` factories in Task 9; until then nothing constructs or implements these types. That is deliberate: the module must compile and stay green at every commit, and interfaces with no implementors compile fine.

Mid-plan partial state (deliberate): after this task the four paged test-plugin samples are gone; they return as v3 views in Task 14. `JoinListener` keeps its (now sample-free) block-place handler so Task 14 only has to re-add wiring.

Note on `pagination/Pagination.java`: the path is deleted in Step 1 and re-created with entirely new content in Step 6. Git will show it as a single modification in the commit — that is expected; the FQN reuse is the point.

**Files:**
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/Pagination.java (2.x interface; path re-created in Step 6)
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AbstractPageSourcePagination.java
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/NormalPagination.java
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/ScrollPagination.java
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPagination.java
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/NormalPaginationBuilder.java
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/ScrollPaginationBuilder.java
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/PatternPaginationBuilder.java
- Delete: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/AsyncPaginationOptions.java
- Delete: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/NormalPaginationTest.java
- Delete: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/ScrollPaginationTest.java
- Delete: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPaginationTest.java
- Delete: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AsyncPaginationIntegrationTest.java
- Delete: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/AsyncPaginationOptionsTest.java
- Delete: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java
- Delete: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalPagedInventory.java
- Delete: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternPagedInventory.java
- Delete: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleAsyncPagedInventory.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/InventoryEditor.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/impl/InventoryEditorImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayout.java
- Modify: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/Pagination.java (NEW v3 token interface)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/PaginationBuilder.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/PaginationItemRenderer.java

- [ ] **Step 1: Delete the 2.x pagination cluster**

Run the three `git rm` commands from the repo root (forward slashes work in Git on Windows; the deletions are staged immediately):

```bash
git rm modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/Pagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AbstractPageSourcePagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/NormalPagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/ScrollPagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/NormalPaginationBuilder.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/ScrollPaginationBuilder.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/PatternPaginationBuilder.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/AsyncPaginationOptions.java
git rm modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/NormalPaginationTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/ScrollPaginationTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPaginationTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AsyncPaginationIntegrationTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/AsyncPaginationOptionsTest.java
git rm test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalPagedInventory.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternPagedInventory.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleAsyncPagedInventory.java
```

This leaves `pagination/source/` (the preserved engine room: `PageSource`, `EagerPageSource`, `AsyncPageSource`, `AsyncPageSupplier`, `PageRequest`, `PageResult`, `PaginationErrorCallback`, `SettleDispatcher`, `BukkitSettleDispatcher`) and its tests untouched. The module does NOT compile between this step and the end of Step 5 — that is fine; nothing is run until Step 7.

- [ ] **Step 2: Remove `fillPage` from `InventoryEditor`**

The deleted engines were the only callers of `fillPage` (the relocated copies call `PaginationHost.fillPage` instead). Removing the method makes the `InventoryLayout`, `Pagination` and `java.util.List` imports unused — remove those three; `Inventory`, `ItemStack`, `InventoryItem` and `ItemCallback` are still used by the remaining members and stay. Complete resulting file `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/InventoryEditor.java`:

```java
/* MIT license header — keep the existing lines 1-22 unchanged */
package tech.guilhermekaua.spigotboot.inventoryapi.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.ItemCallback;

/**
 * Owns the per-viewer Bukkit {@link Inventory} and the slot-to-callback mapping. Implementations
 * apply placeholders to display name and lore at item-set time.
 */
public interface InventoryEditor {

    Inventory getInventory();

    /**
     * Places an item in the given slot, or clears the slot when {@code inventoryItem} is null.
     *
     * @param slot           the slot index
     * @param inventoryItem  the item to place, or {@code null} to clear the slot
     * @throws IllegalArgumentException if {@code slot} is outside {@code [0, inventory.getSize())}
     */
    void setItem(int slot, InventoryItem inventoryItem);

    /**
     * Places an item in the given slot, using a fallback when {@code inventoryItem} is null.
     *
     * @param slot           the slot index
     * @param inventoryItem  the primary item, or {@code null} to use the fallback
     * @param fallbackItem   the item used when {@code inventoryItem} is null
     * @throws IllegalArgumentException if {@code slot} is outside {@code [0, inventory.getSize())}
     */
    void setItem(int slot, InventoryItem inventoryItem, InventoryItem fallbackItem);

    /**
     * Clears the given slot.
     *
     * @param slot the slot index
     * @throws IllegalArgumentException if {@code slot} is outside {@code [0, inventory.getSize())}
     */
    void setEmptyItem(int slot);

    void updateItemStack(int slot);

    void updateAllItemStacks();

    /**
     * Returns the item in the given slot with placeholders applied.
     *
     * @param slot the slot index
     * @return the item stack in the slot, or {@code null} if empty
     * @throws IllegalArgumentException if {@code slot} is outside {@code [0, inventory.getSize())}
     */
    ItemStack getItemStack(int slot);

    ItemCallback getItemCallback(int slot);

}
```

- [ ] **Step 3: Remove `fillPage` from `InventoryEditorImpl`**

Same care with imports: removing the method makes `InventorySlot`, `InventoryLayout`, `Pagination` and `java.util.List` unused — remove those four; `Map` and `ConcurrentHashMap` are still used by the callback map and `updateAllItemStacks`, everything else stays. Complete resulting file `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/impl/InventoryEditorImpl.java`:

```java
/* MIT license header — keep the existing lines 1-22 unchanged */
package tech.guilhermekaua.spigotboot.inventoryapi.editor.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.ItemCallback;
import tech.guilhermekaua.spigotboot.inventoryapi.item.callback.update.ItemUpdateCallback;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@RequiredArgsConstructor
public final class InventoryEditorImpl implements InventoryEditor {

    private final Inventory inventory;
    private final PlaceholderApplier placeholderApplier;
    private final Player viewerPlayer;
    // ConcurrentHashMap so a tickAsync update can iterate the callbacks while a main-thread click
    // mutates them without a ConcurrentModificationException; item callbacks are never null
    private final Map<Integer, ItemCallback> inventoryCallbackMap = new ConcurrentHashMap<>();

    @Override
    public void setItem(int slot, InventoryItem inventoryItem) {
        validateSlot(slot);
        if (inventoryItem == null) {
            setEmptyItem(slot);
            return;
        }

        final ItemStack itemStack = applyPlaceholders(inventoryItem.getItemStack());

        this.inventory.setItem(slot, itemStack);
        this.inventoryCallbackMap.put(slot, inventoryItem.getItemCallback());
    }

    @Override
    public void setItem(int slot, InventoryItem inventoryItem, InventoryItem fallbackItem) {
        validateSlot(slot);
        if (inventoryItem == null) {
            if (fallbackItem == null) {
                setEmptyItem(slot);
                return;
            }

            final ItemStack itemStack = applyPlaceholders(fallbackItem.getItemStack());
            this.inventory.setItem(slot, itemStack);
            this.inventoryCallbackMap.put(slot, fallbackItem.getItemCallback());
            return;
        }

        final ItemStack itemStack = applyPlaceholders(inventoryItem.getItemStack());
        this.inventory.setItem(slot, itemStack);
        this.inventoryCallbackMap.put(slot, inventoryItem.getItemCallback());
    }

    @Override
    public void setEmptyItem(int slot) {
        validateSlot(slot);
        this.inventory.setItem(slot, null);
        this.inventoryCallbackMap.remove(slot);
    }

    @Override
    public void updateItemStack(int slot) {
        ItemCallback itemCallback = getItemCallback(slot);
        if (itemCallback == null) return;

        updateItemStack(slot, itemCallback);
    }

    @Override
    public void updateAllItemStacks() {
        for (Map.Entry<Integer, ItemCallback> entry : inventoryCallbackMap.entrySet()) {
            updateItemStack(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public ItemStack getItemStack(int slot) {
        validateSlot(slot);
        return applyPlaceholders(this.inventory.getItem(slot));
    }

    @Override
    public ItemCallback getItemCallback(int slot) {
        return this.inventoryCallbackMap.get(slot);
    }

    private void updateItemStack(int slot, ItemCallback itemCallback) {
        validateSlot(slot);
        ItemUpdateCallback updateCallback = itemCallback.getUpdateCallback();
        if (updateCallback == null) return;

        ItemStack itemStack = getItemStack(slot);
        updateCallback.accept(itemStack);

        this.inventory.setItem(slot, itemStack);
    }

    private void validateSlot(int slot) {
        int size = inventory.getSize();
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException(
                    "slot " + slot + " is out of bounds for inventory size " + size
            );
        }
    }

    private ItemStack applyPlaceholders(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        itemStack = itemStack.clone();

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        itemMeta.setDisplayName(placeholderApplier.apply(viewerPlayer, itemMeta.getDisplayName()));
        itemMeta.setLore(placeholderApplier.applyAll(viewerPlayer, itemMeta.getLore()));

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

}
```

- [ ] **Step 4: Reword the `InventoryLayout` Javadoc that names the removed method**

In `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayout.java` (interface Javadoc, lines 37–38) replace exactly this:

```java
 * <p>The core contract is {@link #getSlots()}: an ordered list of fill positions. Consumers such
 * as {@code InventoryEditor#fillPage} place the i-th page item into the i-th slot of that list.
```

with exactly this:

```java
 * <p>The core contract is {@link #getSlots()}: an ordered list of fill positions. Consumers
 * place the i-th page item into the i-th slot of that list.
```

No other change to the file.

- [ ] **Step 5: Neuter `JoinListener`**

Remove the four sample imports, the four block-type if-blocks, the now-unused `blockType`/`player` locals and their `Material`/`Player` imports, and — because nothing else in test-plugin uses it (verified by grep) — the `InventoryService` field and import. The `userService` field and the pre-existing commented-out experiments stay as they are. Complete resulting file `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java`:

```java
/* MIT license header — keep the existing lines 1-22 unchanged */
package tech.guilhermekaua.spigotboot.testPlugin.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.testPlugin.services.UserService;

@Component
@RequiredArgsConstructor
public class JoinListener implements Listener {
    private final UserService userService;

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // the 2.x paged samples were deleted with the old pagination API; the v3 pagination
        // samples return in Task 14, opened through ViewService

//        try {
//            bungeeChannel.sendMessage(player, new GetPlayerServerAction(player.getName())).thenAccept(serverName -> {
//                player.sendMessage("You are on server: " + serverName);
//            }).exceptionally(throwable -> {
//                player.sendMessage("An error occurred while trying to fetch your server.");
//                throwable.printStackTrace();
//                return null;
//            });
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

//        final Optional<People> people = userService.getPeople(player.getUniqueId().toString());
//
//        if (!people.isPresent()) {
//            player.sendMessage("People not in database!");
//            return;
//        }

//        try {
//            bungeeChannel.sendMessage(player, new ForwardAction<People, String>(ForwardAction.SERVER_ALL, "test", people.get()))
//                    .thenAccept(response -> {
//                        System.out.println("[SendMessage] received response: " + response.getBody());
//                    });
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }
}
```

- [ ] **Step 6: Create the new public token interface `Pagination`**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/Pagination.java` (the path freed in Step 1) with exactly:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.pagination;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

/**
 * Reactive pagination token, declared once per view through the {@code View.paginate*} factories
 * and built by {@link PaginationBuilder#build()}. The token itself holds no paging state: every
 * method reads or mutates the state of the session behind the given {@link ViewContext}, so a
 * single declaration serves every viewer with fully isolated per-context paging.
 *
 * <p>As a {@link StateToken} the token can be watched via
 * {@code ItemComponentBuilder.updateOnStateChange(StateToken...)}: watching components re-render
 * whenever a page load settles or a navigation repaints the pagination area.
 *
 * <p><strong>Pre-init window.</strong> Between {@code onOpen} and the engine's pagination
 * initialization (which runs before {@code onFirstRender}) the token is not yet backed by a
 * paging engine. In that window reads return defaults — {@link #totalPages} is {@code 1},
 * {@link #totalElements} is {@code 0}, {@link #isLoading} is {@code false} and
 * {@link #lastError} is {@code null} — and {@link #advance}, {@link #back} and
 * {@link #switchTo} record a pending target page (never below 1) that is replayed once
 * initialization completes; {@link #currentPage} reports that pending target.
 *
 * <p><strong>Threading.</strong> {@link #advance}, {@link #back}, {@link #switchTo} and
 * {@link #refresh} are main-thread only and throw {@link IllegalStateException} when invoked
 * off the main server thread. Reads are unsynchronized and only coherent on the main thread.
 *
 * <p>Every method first validates the context: a context belonging to a different view class or
 * to an already closed session fails with {@link StaleContextException}.
 *
 * @param <T> the element type served by the backing page source
 */
@ApiStatus.NonExtendable
public interface Pagination<T> extends StateToken {

    /**
     * Returns the current page, 1-indexed. Before initialization this is the pending
     * navigation target.
     *
     * @param context the context of the session to read
     * @return the current 1-indexed page
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    int currentPage(@NotNull ViewContext context);

    /**
     * Returns the total page count, always at least 1. Before initialization — and, for async
     * sources, before the first successful load reveals the totals — this is {@code 1}.
     *
     * @param context the context of the session to read
     * @return the total page count, {@code >= 1}
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    int totalPages(@NotNull ViewContext context);

    /**
     * Returns the total element count of the backing source. Before initialization — and, for
     * async sources, before totals are known — this is {@code 0}.
     *
     * @param context the context of the session to read
     * @return the total element count
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    int totalElements(@NotNull ViewContext context);

    /**
     * Returns whether a next page exists, i.e. {@code currentPage + 1 <= totalPages}. Always
     * {@code false} before initialization.
     *
     * @param context the context of the session to read
     * @return {@code true} when {@link #advance} would move forward
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    boolean canAdvance(@NotNull ViewContext context);

    /**
     * Returns whether a previous page exists, i.e. {@code currentPage > 1}. Before
     * initialization this reports whether the pending target is above page 1.
     *
     * @param context the context of the session to read
     * @return {@code true} when {@link #back} would move backward
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    boolean canBack(@NotNull ViewContext context);

    /**
     * Navigates one page forward, clamped exactly like {@link #switchTo}. Before
     * initialization the pending target is incremented instead. Main thread only.
     *
     * @param context the context of the session to navigate
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     * @throws IllegalStateException when invoked off the main server thread
     */
    void advance(@NotNull ViewContext context);

    /**
     * Navigates one page backward, clamped exactly like {@link #switchTo}. Before
     * initialization the pending target is decremented instead (never below 1). Main thread
     * only.
     *
     * @param context the context of the session to navigate
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     * @throws IllegalStateException when invoked off the main server thread
     */
    void back(@NotNull ViewContext context);

    /**
     * Switches to the given page. The target is clamped exactly as the 2.x {@code changePage}:
     * lower-clamped to page 1 always, upper-clamped to {@link #totalPages} only once the
     * source's totals are known (always for eager sources; after the first successful load for
     * async sources — an overshooting target is re-clamped downward when that load settles).
     * Re-requesting the page already shown is a no-op while that page is still loading. Before
     * initialization the lower-clamped target is recorded and replayed at initialization. Main
     * thread only.
     *
     * @param context the context of the session to navigate
     * @param page    the 1-indexed target page; out-of-range values are clamped, not rejected
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     * @throws IllegalStateException when invoked off the main server thread
     */
    void switchTo(@NotNull ViewContext context, int page);

    /**
     * Returns whether the latest page request has not settled yet. Always {@code false} for
     * eager sources and before initialization.
     *
     * @param context the context of the session to read
     * @return {@code true} while a page load is in flight
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    boolean isLoading(@NotNull ViewContext context);

    /**
     * Returns the failure of the most recently settled page load, or {@code null}; cleared
     * when a new request is dispatched. Always {@code null} for eager sources and before
     * initialization.
     *
     * @param context the context of the session to read
     * @return the last page-load failure, or {@code null}
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     */
    @Nullable Throwable lastError(@NotNull ViewContext context);

    /**
     * Forces a reload of the current page. Before initialization this is a no-op. After
     * initialization the behavior depends on the source kind: lazy sources
     * ({@code View.paginate(Function)}) re-invoke the source function against this context and
     * swap the fresh result in; async sources invalidate their page cache; every kind then
     * re-requests the current page, forced — the same-page dedupe is bypassed. Main thread
     * only.
     *
     * @param context the context of the session to refresh
     * @throws StaleContextException if {@code context} belongs to another view or is closed
     * @throws IllegalStateException when invoked off the main server thread
     */
    void refresh(@NotNull ViewContext context);
}
```

- [ ] **Step 7: Create `PaginationBuilder`**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/PaginationBuilder.java` with exactly:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.pagination;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PaginationErrorCallback;

import java.time.Duration;
import java.util.function.Function;

/**
 * Fluent declaration of one pagination token, returned by the {@code View.paginate*}
 * factories. Builder methods only record the declaration; nothing is registered or validated
 * as a whole until {@link #build()}, which validates the combination, registers the token with
 * the owning view and returns it.
 *
 * <p><strong>Defaults.</strong> When neither {@link #layoutChar(char)}, {@link #layout(Layout)}
 * nor {@link #patterns(Layout...)} is called, the pagination targets layout char {@code 'O'}.
 * The default geometry is normal (page-by-page); {@link #scroll()} switches to a sliding
 * window, {@link #patterns(Layout...)} to cycled per-page patterns.
 *
 * <p><strong>Async-only options.</strong> {@link #loadingItem(Function)},
 * {@link #onError(PaginationErrorCallback)}, {@link #requestTimeout(Duration)},
 * {@link #cacheTtl(Duration)} and {@link #cacheMaxPages(int)} are only legal on a builder
 * created by {@code View.paginateAsync}; calling any of them on another builder makes
 * {@link #build()} throw {@link ViewConfigurationException}.
 *
 * <p>All methods return this builder for chaining and reject {@code null} arguments with
 * {@link NullPointerException}. Value errors fail at setter time with
 * {@link IllegalArgumentException}; combination errors fail at {@link #build()} with
 * {@link ViewConfigurationException}.
 *
 * @param <T> the element type served by the source
 */
@ApiStatus.NonExtendable
public interface PaginationBuilder<T> {

    /**
     * Targets every slot of the given character in the view's layout, in row-major order.
     * Defaults to {@code 'O'} when never called. The character must exist in the view's
     * layout; both "no layout at all" and "char absent from the layout" are validated at view
     * registration time. An explicit {@link #layout(Layout)} silently overrides this value;
     * combining an explicit call with {@link #patterns(Layout...)} fails at {@link #build()}.
     *
     * @param character the layout character whose slots receive the page items
     * @return this builder
     */
    @NotNull PaginationBuilder<T> layoutChar(char character);

    /**
     * Sets an explicit fill order, silently overriding {@link #layoutChar(char)}. An empty
     * layout, or combining with {@link #patterns(Layout...)}, fails at {@link #build()} with
     * {@link ViewConfigurationException}.
     *
     * @param layout the explicit fill order for the page items
     * @return this builder
     */
    @NotNull PaginationBuilder<T> layout(@NotNull Layout layout);

    /**
     * Switches to sliding-window geometry: each page slides the visible window by exactly one
     * element instead of jumping a full page. Cannot be combined with
     * {@link #patterns(Layout...)} — that fails at {@link #build()}.
     *
     * @return this builder
     */
    @NotNull PaginationBuilder<T> scroll();

    /**
     * Switches to pattern geometry: page {@code p} paints into the slots of pattern
     * {@code (p - 1) % patterns.length}, cycling through the given patterns. Cannot be
     * combined with an explicit {@link #layoutChar(char)} call, {@link #layout(Layout)} or
     * {@link #scroll()}; an empty array or any empty pattern also fails — all at
     * {@link #build()} with {@link ViewConfigurationException}.
     *
     * @param patterns the per-page slot patterns, cycled in order
     * @return this builder
     */
    @NotNull PaginationBuilder<T> patterns(@NotNull Layout... patterns);

    /**
     * Sets the per-element renderer. Required — a declaration without a renderer fails at
     * {@link #build()} with {@link ViewConfigurationException}.
     *
     * @param renderer renders one element of the current page into its component builder
     * @return this builder
     */
    @NotNull PaginationBuilder<T> itemRenderer(@NotNull PaginationItemRenderer<T> renderer);

    /**
     * Sets the item painted into page slots not covered by an element (for example the tail
     * of a short last page) and into slots whose element failed on its very first paint.
     * Evaluated against the session's context at paint time. When absent, uncovered slots are
     * cleared instead.
     *
     * @param item the fallback item factory
     * @return this builder
     */
    @NotNull PaginationBuilder<T> fallbackItem(@NotNull Function<ViewContext, ItemStack> item);

    /**
     * Sets the item painted into every page slot while an async load is in flight. Async-only:
     * on a non-async builder {@link #build()} throws {@link ViewConfigurationException}.
     *
     * @param item the loading placeholder factory
     * @return this builder
     */
    @NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item);

    /**
     * Sets the callback invoked when an async page load fails. Async-only: on a non-async
     * builder {@link #build()} throws {@link ViewConfigurationException}.
     *
     * @param callback the failure callback
     * @return this builder
     */
    @NotNull PaginationBuilder<T> onError(@NotNull PaginationErrorCallback callback);

    /**
     * Enables a per-request timeout: a load exceeding it fails with a
     * {@code TimeoutException} and follows the normal error path. Async-only: on a non-async
     * builder {@link #build()} throws {@link ViewConfigurationException}.
     *
     * @param timeout the timeout, must be positive
     * @return this builder
     * @throws IllegalArgumentException if {@code timeout} is zero or negative
     */
    @NotNull PaginationBuilder<T> requestTimeout(@NotNull Duration timeout);

    /**
     * Enables page caching: revisiting a page within the TTL renders from cache without
     * calling the supplier; {@link Pagination#refresh(ViewContext)} invalidates the cache.
     * Async-only: on a non-async builder {@link #build()} throws
     * {@link ViewConfigurationException}.
     *
     * @param ttl the cache entry freshness window, must be positive
     * @return this builder
     * @throws IllegalArgumentException if {@code ttl} is zero or negative
     */
    @NotNull PaginationBuilder<T> cacheTtl(@NotNull Duration ttl);

    /**
     * Bounds the page cache (least-recently-used eviction). Defaults to 128. Requires
     * {@link #cacheTtl(Duration)} — setting it without a TTL fails at {@link #build()} with
     * {@link ViewConfigurationException}. Async-only: on a non-async builder {@link #build()}
     * throws {@link ViewConfigurationException}.
     *
     * @param maxPages the maximum number of cached pages, at least 1
     * @return this builder
     * @throws IllegalArgumentException if {@code maxPages} is below 1
     */
    @NotNull PaginationBuilder<T> cacheMaxPages(int maxPages);

    /**
     * Validates the declaration, constructs the token and registers it with the owning view.
     * Like every token registration this is legal only while the view's tokens are still
     * open for registration — i.e. from field initializers or the view constructor; the
     * builder construction itself (the {@code paginate*} call) never registers anything, only
     * this method does.
     *
     * @return the registered pagination token
     * @throws ViewConfigurationException when the renderer is missing; when
     *         {@link #patterns(Layout...)} is combined with an explicit
     *         {@link #layoutChar(char)} call, {@link #layout(Layout)} or {@link #scroll()};
     *         when the patterns array or any single pattern, or an explicit layout, is empty;
     *         when an async-only option was used on a non-async source; or when
     *         {@link #cacheMaxPages(int)} was set without {@link #cacheTtl(Duration)}
     * @throws IllegalStateException when called a second time on the same builder
     *         ("build() may only be called once per paginate* call") or after the view's
     *         token table froze
     */
    @NotNull Pagination<T> build();
}
```

- [ ] **Step 8: Create `PaginationItemRenderer`**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/PaginationItemRenderer.java` with exactly:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.pagination;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;

/**
 * Renders one element of the current page into a component builder. The engine invokes the
 * renderer once per element on every repaint of the pagination area, always on the main
 * thread, handing it a fresh {@link ItemComponentBuilder} that is materialized into a
 * single-slot component afterwards — {@code displayIf}, {@code updateOnStateChange},
 * {@code onClick}, {@code cancelOnClick}, {@code closeOnClick} and {@code openOnClick} behave
 * exactly as on statically declared components.
 *
 * <p>The renderer must declare an item source via {@code item(...)}. A renderer that throws,
 * or declares no item source, is logged rate-limited and the slot keeps its previous content
 * entirely (item and click handlers); on a first paint with no previous content the
 * pagination's fallback item is painted instead.
 *
 * @param <T> the element type served by the pagination's page source
 */
@FunctionalInterface
public interface PaginationItemRenderer<T> {

    /**
     * Renders one page element.
     *
     * @param context the context of the session being painted
     * @param item    the fresh component builder to declare the element on
     * @param index   the ZERO-BASED position of the element within the CURRENT page — not the
     *                global element index
     * @param value   the element value served by the page source
     */
    void render(@NotNull ViewContext context, @NotNull ItemComponentBuilder item, int index, @NotNull T value);
}
```

- [ ] **Step 9: Run the full module suite**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B`
Expected: PASS (BUILD SUCCESS — the deleted 2.x pagination tests no longer exist; the relocated-engine tests from Tasks 3–4, the `pagination/source` tests and all v3 core tests stay green; no compile references to the deleted types remain).

- [ ] **Step 10: Package the test-plugin**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl test-plugin -am package -B -DskipTests`
Expected: PASS (BUILD SUCCESS — the four paged samples are gone and `JoinListener` no longer references them).

- [ ] **Step 11: Commit**

The Step 1 deletions are already staged by `git rm`; stage the modified and created files and commit (breaking change, pre-3.0, hence the `!`):

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/Pagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/PaginationBuilder.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/PaginationItemRenderer.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/InventoryEditor.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/editor/impl/InventoryEditorImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayout.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java
git commit -m "refactor(inventory-api)!: swap the 2.x pagination surface for the v3 token interfaces" -m "deleted public surface (breaking, pre-3.0): the 2.x pagination.Pagination interface, pagination.impl.AbstractPageSourcePagination/NormalPagination/ScrollPagination/PatternPagination, pagination.builder.NormalPaginationBuilder/ScrollPaginationBuilder/PatternPaginationBuilder/AsyncPaginationOptions, InventoryEditor.fillPage (and its impl), and the four paged test-plugin samples (rebuilt on the new API in Task 14). the vacated pagination.Pagination FQN now carries the v3 reactive token interface; PaginationBuilder and PaginationItemRenderer land alongside it, compile-only until Tasks 6-9 wire the implementations."
```

---

### Task 6: PaginationSpec + PaginationSourceSpec

The declaration model: two immutable internal value types that carry what a `View.paginate*` call declared. `PaginationSpec` is the full declaration (geometry, target, renderer, frame items, async options); `PaginationSourceSpec` is the source declaration — which of the four source kinds was used and how to construct the per-context `PageSource`. Depends on Task 5 (the public `PaginationItemRenderer` type) and Task 2 (the rewritten `BukkitSettleDispatcher`, whose constructor is Bukkit-free — only `dispatch(...)` touches Bukkit — so the async path is constructible in plain unit tests without MockBukkit).

Both constructors are package-private: `PaginationBuilderImpl` (Task 8) lives in the same package and is the only production caller; the same-package test below is the only other one. **The `PaginationSpec` constructor parameter order pinned here is load-bearing for Task 8** — (geometry, target, layoutChar, explicitLayout, patterns, renderer, fallbackItem, loadingItem, source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages).

The `cacheMaxPages` default of 128 (the 2.x `AsyncPaginationOptions.DEFAULT_CACHE_MAX_PAGES`) is enforced by the builder in Task 8; the spec only carries the value. `eager(...)` builds its `EagerPageSource` immediately — the defensive copy of the caller's list is the `EagerPageSource` constructor's own documented behavior, so the factory does not copy again.

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSpec.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSourceSpec.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSourceSpecTest.java

- [ ] **Step 1: Write the failing test**

Create `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSourceSpecTest.java` with exactly:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PaginationSourceSpecTest {

    private final ViewContext context = mock(ViewContext.class);

    // pins the package-private PaginationSpec constructor order Task 8's builder must use:
    // (geometry, target, layoutChar, explicitLayout, patterns, renderer, fallbackItem,
    //  loadingItem, source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages)
    private PaginationSpec<Integer> specOf(PaginationSourceSpec<Integer> source) {
        // the renderer is a no-op lambda: createSource never invokes it
        return new PaginationSpec<Integer>(PaginationSpec.Geometry.NORMAL,
                PaginationSpec.Target.LAYOUT_CHAR, 'O', null, Collections.emptyList(),
                (ctx, item, index, value) -> { }, null, null, source, null, null, null, 128);
    }

    @Test
    void eager_returnsTheSameSharedSourceForEveryCreateCall() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.eager(Arrays.asList(1, 2, 3));
        PaginationSpec<Integer> spec = specOf(sourceSpec);

        PageSource<Integer> first = sourceSpec.createSource(context, spec);
        PageSource<Integer> second = sourceSpec.createSource(mock(ViewContext.class), spec);

        assertTrue(first instanceof EagerPageSource);
        assertSame(first, second);
    }

    @Test
    void eager_originalListMutationDoesNotLeakIntoTheSource() {
        List<Integer> backing = new ArrayList<>(Arrays.asList(1, 2, 3));
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.eager(backing);

        backing.add(4);

        PageSource<Integer> source = sourceSpec.createSource(context, specOf(sourceSpec));
        assertEquals(Arrays.asList(1, 2, 3), source.elements());
    }

    @Test
    void eager_reportsItsKind() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.eager(Collections.singletonList(1));

        assertEquals(PaginationSourceSpec.Kind.EAGER_STATIC, sourceSpec.kind());
        assertFalse(sourceSpec.isAsync());
        assertNull(sourceSpec.lazyFunction());
    }

    @Test
    void lazy_buildsAFreshEagerSourcePerCallWrappingTheFunctionResult() {
        AtomicReference<ViewContext> seen = new AtomicReference<>();
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.lazy(ctx -> {
            seen.set(ctx);
            return Arrays.asList(7, 8);
        });
        PaginationSpec<Integer> spec = specOf(sourceSpec);

        PageSource<Integer> first = sourceSpec.createSource(context, spec);
        PageSource<Integer> second = sourceSpec.createSource(context, spec);

        assertTrue(first instanceof EagerPageSource);
        assertNotSame(first, second);
        assertEquals(Arrays.asList(7, 8), first.elements());
        assertSame(context, seen.get());
    }

    @Test
    void lazy_nullFunctionResultFailsWithThePinnedMessage() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.lazy(ctx -> null);
        PaginationSpec<Integer> spec = specOf(sourceSpec);

        NullPointerException error = assertThrows(NullPointerException.class,
                () -> sourceSpec.createSource(context, spec));

        assertEquals("lazy pagination source function returned null", error.getMessage());
    }

    @Test
    void lazy_exposesTheFunctionForTheRefreshPath() {
        Function<ViewContext, List<Integer>> fn = ctx -> Collections.singletonList(1);
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.lazy(fn);

        assertEquals(PaginationSourceSpec.Kind.EAGER_LAZY, sourceSpec.kind());
        assertFalse(sourceSpec.isAsync());
        assertSame(fn, sourceSpec.lazyFunction());
    }

    @Test
    void async_buildsAFreshAsyncPageSourcePerCall() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.async(
                request -> CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1, 2), 2)));
        // requestTimeout/cacheTtl/cacheMaxPages/errorCallback are set to prove the option
        // pass-through compiles and constructs; the behavioral pass-through (timeouts firing,
        // cache hits, error-callback invocation) is covered by the engine tests of later tasks
        PaginationSpec<Integer> spec = new PaginationSpec<Integer>(PaginationSpec.Geometry.NORMAL,
                PaginationSpec.Target.LAYOUT_CHAR, 'O', null, Collections.emptyList(),
                (ctx, item, index, value) -> { }, null, null, sourceSpec,
                (request, error) -> { }, Duration.ofSeconds(5), Duration.ofSeconds(30), 64);

        PageSource<Integer> first = sourceSpec.createSource(context, spec);
        PageSource<Integer> second = sourceSpec.createSource(context, spec);

        assertTrue(first instanceof AsyncPageSource);
        assertNotSame(first, second);
        assertEquals(PaginationSourceSpec.Kind.ASYNC, sourceSpec.kind());
        assertTrue(sourceSpec.isAsync());
    }

    @Test
    void custom_returnsTheFactoryResultAsIs() {
        PageSource<Integer> made = new EagerPageSource<>(Collections.singletonList(5));
        AtomicReference<ViewContext> seen = new AtomicReference<>();
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.custom(ctx -> {
            seen.set(ctx);
            return made;
        });

        PageSource<Integer> created = sourceSpec.createSource(context, specOf(sourceSpec));

        assertSame(made, created);
        assertSame(context, seen.get());
        assertEquals(PaginationSourceSpec.Kind.CUSTOM, sourceSpec.kind());
        assertFalse(sourceSpec.isAsync());
    }

    @Test
    void custom_nullFactoryResultFailsWithThePinnedMessage() {
        PaginationSourceSpec<Integer> sourceSpec = PaginationSourceSpec.custom(ctx -> null);

        NullPointerException error = assertThrows(NullPointerException.class,
                () -> sourceSpec.createSource(context, specOf(sourceSpec)));

        assertEquals("paginateSource factory returned null", error.getMessage());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationSourceSpecTest"`
Expected: FAIL (test compilation error: cannot find symbol: class PaginationSourceSpec / class PaginationSpec in package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination)

- [ ] **Step 3: Write `PaginationSpec`**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSpec.java` with exactly:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PaginationErrorCallback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable pagination declaration built by {@code PaginationBuilderImpl.build()}: geometry,
 * paint target, renderer and frame items, the source declaration, and the async plumbing
 * consumed by {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec)}. All
 * combination validation happens in the builder before a spec is constructed; the spec only
 * carries the validated values.
 *
 * @param <T> the element type served by the source
 */
@ApiStatus.Internal
public final class PaginationSpec<T> {

    /**
     * How elements are distributed across pages.
     */
    public enum Geometry {
        /**
         * Page-by-page: page {@code p} serves elements {@code (p-1)*limit .. p*limit-1}.
         */
        NORMAL,
        /**
         * Sliding window: each page slides the visible window by exactly one element.
         */
        SCROLL,
        /**
         * Cycled per-page slot patterns.
         */
        PATTERN
    }

    /**
     * Where the page items are painted.
     */
    public enum Target {
        /**
         * All slots of one character of the view's layout, row-major.
         */
        LAYOUT_CHAR,
        /**
         * An explicit {@link Layout} fill order.
         */
        EXPLICIT_LAYOUT,
        /**
         * The cycled per-page patterns.
         */
        PATTERNS
    }

    private final Geometry geometry;
    private final Target target;
    private final char layoutChar;
    private final Layout explicitLayout;
    private final List<Layout> patterns;
    private final PaginationItemRenderer<T> renderer;
    private final Function<ViewContext, ItemStack> fallbackItem;
    private final Function<ViewContext, ItemStack> loadingItem;
    private final PaginationSourceSpec<T> source;
    private final PaginationErrorCallback errorCallback;
    private final Duration requestTimeout;
    private final Duration cacheTtl;
    private final int cacheMaxPages;

    /**
     * Creates a spec. Package-private: only {@code PaginationBuilderImpl} and same-package
     * tests construct specs, after the builder validated the combination.
     *
     * @param geometry       the page geometry
     * @param target         the paint target
     * @param layoutChar     the target layout character; meaningful only when {@code target}
     *                       is {@link Target#LAYOUT_CHAR}; {@code 'O'} by default
     * @param explicitLayout the explicit fill order; non-null only when {@code target} is
     *                       {@link Target#EXPLICIT_LAYOUT}
     * @param patterns       the per-page patterns, defensively copied; empty unless
     *                       {@code target} is {@link Target#PATTERNS}
     * @param renderer       the per-element renderer
     * @param fallbackItem   the frame item for uncovered page slots, or null to clear them
     * @param loadingItem    the async loading frame item, or null
     * @param source         the source declaration
     * @param errorCallback  the async error callback, or null
     * @param requestTimeout the async per-request timeout, or null to disable
     * @param cacheTtl       the async cache TTL, or null to disable caching
     * @param cacheMaxPages  the async cache LRU bound; 128 unless overridden (the 2.x default)
     * @throws NullPointerException if {@code geometry}, {@code target}, {@code patterns},
     *                              {@code renderer} or {@code source} is null
     */
    PaginationSpec(@NotNull Geometry geometry,
                   @NotNull Target target,
                   char layoutChar,
                   @Nullable Layout explicitLayout,
                   @NotNull List<Layout> patterns,
                   @NotNull PaginationItemRenderer<T> renderer,
                   @Nullable Function<ViewContext, ItemStack> fallbackItem,
                   @Nullable Function<ViewContext, ItemStack> loadingItem,
                   @NotNull PaginationSourceSpec<T> source,
                   @Nullable PaginationErrorCallback errorCallback,
                   @Nullable Duration requestTimeout,
                   @Nullable Duration cacheTtl,
                   int cacheMaxPages) {
        this.geometry = Objects.requireNonNull(geometry, "geometry is required.");
        this.target = Objects.requireNonNull(target, "target is required.");
        this.layoutChar = layoutChar;
        this.explicitLayout = explicitLayout;
        this.patterns = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(patterns, "patterns is required.")));
        this.renderer = Objects.requireNonNull(renderer, "renderer is required.");
        this.fallbackItem = fallbackItem;
        this.loadingItem = loadingItem;
        this.source = Objects.requireNonNull(source, "source is required.");
        this.errorCallback = errorCallback;
        this.requestTimeout = requestTimeout;
        this.cacheTtl = cacheTtl;
        this.cacheMaxPages = cacheMaxPages;
    }

    /**
     * Returns the page geometry.
     *
     * @return the geometry
     */
    public @NotNull Geometry geometry() {
        return geometry;
    }

    /**
     * Returns the paint target.
     *
     * @return the target
     */
    public @NotNull Target target() {
        return target;
    }

    /**
     * Returns the target layout character, meaningful only when {@link #target()} is
     * {@link Target#LAYOUT_CHAR}.
     *
     * @return the layout character, {@code 'O'} by default
     */
    public char layoutChar() {
        return layoutChar;
    }

    /**
     * Returns the explicit fill order.
     *
     * @return the explicit layout, or null unless {@link #target()} is
     *         {@link Target#EXPLICIT_LAYOUT}
     */
    public @Nullable Layout explicitLayout() {
        return explicitLayout;
    }

    /**
     * Returns the per-page patterns.
     *
     * @return an unmodifiable list; empty unless {@link #target()} is {@link Target#PATTERNS}
     */
    public @NotNull List<Layout> patterns() {
        return patterns;
    }

    /**
     * Returns the per-element renderer.
     *
     * @return the renderer
     */
    public @NotNull PaginationItemRenderer<T> renderer() {
        return renderer;
    }

    /**
     * Returns the frame item painted into page slots not covered by an element.
     *
     * @return the fallback item factory, or null when uncovered slots are cleared
     */
    public @Nullable Function<ViewContext, ItemStack> fallbackItem() {
        return fallbackItem;
    }

    /**
     * Returns the frame item painted into every page slot while an async load is in flight.
     *
     * @return the loading item factory, or null
     */
    public @Nullable Function<ViewContext, ItemStack> loadingItem() {
        return loadingItem;
    }

    /**
     * Returns the source declaration.
     *
     * @return the source spec
     */
    public @NotNull PaginationSourceSpec<T> source() {
        return source;
    }

    /**
     * Returns the async error callback, consumed by
     * {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec)}.
     *
     * @return the callback, or null
     */
    public @Nullable PaginationErrorCallback errorCallback() {
        return errorCallback;
    }

    /**
     * Returns the async per-request timeout, consumed by
     * {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec)}.
     *
     * @return the timeout, or null when disabled
     */
    public @Nullable Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * Returns the async cache TTL, consumed by
     * {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec)}.
     *
     * @return the TTL, or null when caching is disabled
     */
    public @Nullable Duration cacheTtl() {
        return cacheTtl;
    }

    /**
     * Returns the async cache LRU bound, consumed by
     * {@link PaginationSourceSpec#createSource(ViewContext, PaginationSpec)}.
     *
     * @return the bound; 128 unless overridden (the 2.x {@code DEFAULT_CACHE_MAX_PAGES})
     */
    public int cacheMaxPages() {
        return cacheMaxPages;
    }
}
```

- [ ] **Step 4: Write `PaginationSourceSpec`**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSourceSpec.java` with exactly:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.BukkitSettleDispatcher;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable source declaration of one pagination token: which of the four source kinds the
 * view declared, and how to construct the {@link PageSource} for one context.
 * {@link Kind#EAGER_STATIC} carries the ONE {@link EagerPageSource} shared by every context —
 * safe because that source is immutable; every other kind constructs a fresh source per
 * context, preserving per-context isolation.
 *
 * @param <T> the element type served by the source
 */
@ApiStatus.Internal
public final class PaginationSourceSpec<T> {

    /**
     * The four ways a view can declare where page elements come from.
     */
    public enum Kind {
        /**
         * {@code View.paginate(List)}: one immutable in-memory source shared by every context.
         */
        EAGER_STATIC,
        /**
         * {@code View.paginate(Function)}: the function runs once per context at init (and
         * again on refresh), each result wrapped in a fresh eager source.
         */
        EAGER_LAZY,
        /**
         * {@code View.paginateAsync(AsyncPageSupplier)}: a fresh async source per context.
         */
        ASYNC,
        /**
         * {@code View.paginateSource(Function)}: the escape hatch, the factory runs once per
         * context.
         */
        CUSTOM
    }

    private final Kind kind;
    private final PageSource<T> sharedEagerSource;
    private final Function<ViewContext, List<T>> lazyFunction;
    private final AsyncPageSupplier<T> asyncSupplier;
    private final Function<ViewContext, PageSource<T>> customFactory;

    /**
     * Creates a source spec. Package-private: the static factories are the only entry points;
     * exactly the field matching {@code kind} is non-null.
     *
     * @param kind              the source kind
     * @param sharedEagerSource the shared source; non-null only for {@link Kind#EAGER_STATIC}
     * @param lazyFunction      the per-context list function; non-null only for
     *                          {@link Kind#EAGER_LAZY}
     * @param asyncSupplier     the async supplier; non-null only for {@link Kind#ASYNC}
     * @param customFactory     the source factory; non-null only for {@link Kind#CUSTOM}
     */
    PaginationSourceSpec(@NotNull Kind kind,
                         @Nullable PageSource<T> sharedEagerSource,
                         @Nullable Function<ViewContext, List<T>> lazyFunction,
                         @Nullable AsyncPageSupplier<T> asyncSupplier,
                         @Nullable Function<ViewContext, PageSource<T>> customFactory) {
        this.kind = Objects.requireNonNull(kind, "kind is required.");
        this.sharedEagerSource = sharedEagerSource;
        this.lazyFunction = lazyFunction;
        this.asyncSupplier = asyncSupplier;
        this.customFactory = customFactory;
    }

    /**
     * Declares a static eager source. The ONE shared {@link EagerPageSource} every context
     * will use is built immediately; the defensive copy of {@code source} is taken by the
     * {@link EagerPageSource} constructor itself, so later mutation of the caller's list
     * never leaks into pages.
     *
     * @param <T>    the element type
     * @param source the backing elements
     * @return the source spec
     * @throws NullPointerException if {@code source} is null
     */
    public static <T> @NotNull PaginationSourceSpec<T> eager(@NotNull List<T> source) {
        Objects.requireNonNull(source, "source is required.");
        return new PaginationSourceSpec<>(Kind.EAGER_STATIC, new EagerPageSource<>(source), null, null, null);
    }

    /**
     * Declares a lazy eager source: {@code source} runs once per context at initialization
     * (and again on {@code Pagination.refresh}), each result wrapped in a fresh
     * {@link EagerPageSource}.
     *
     * @param <T>    the element type
     * @param source produces the backing elements for one context
     * @return the source spec
     * @throws NullPointerException if {@code source} is null
     */
    public static <T> @NotNull PaginationSourceSpec<T> lazy(@NotNull Function<ViewContext, List<T>> source) {
        Objects.requireNonNull(source, "source is required.");
        return new PaginationSourceSpec<>(Kind.EAGER_LAZY, null, source, null, null);
    }

    /**
     * Declares an async source: a fresh {@link AsyncPageSource} is constructed per context
     * from the supplier and the owning spec's async options.
     *
     * @param <T>      the element type
     * @param supplier loads pages on demand
     * @return the source spec
     * @throws NullPointerException if {@code supplier} is null
     */
    public static <T> @NotNull PaginationSourceSpec<T> async(@NotNull AsyncPageSupplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier is required.");
        return new PaginationSourceSpec<>(Kind.ASYNC, null, null, supplier, null);
    }

    /**
     * Declares a custom source: the factory runs once per context and its result is used
     * as-is.
     *
     * @param <T>     the element type
     * @param factory produces the page source for one context
     * @return the source spec
     * @throws NullPointerException if {@code factory} is null
     */
    public static <T> @NotNull PaginationSourceSpec<T> custom(@NotNull Function<ViewContext, PageSource<T>> factory) {
        Objects.requireNonNull(factory, "factory is required.");
        return new PaginationSourceSpec<>(Kind.CUSTOM, null, null, null, factory);
    }

    /**
     * Returns the source kind.
     *
     * @return the kind
     */
    public @NotNull Kind kind() {
        return kind;
    }

    /**
     * Returns whether this is an async declaration — the gate for the async-only builder
     * options.
     *
     * @return {@code true} when {@link #kind()} is {@link Kind#ASYNC}
     */
    public boolean isAsync() {
        return kind == Kind.ASYNC;
    }

    /**
     * Returns the lazy list function, used by the {@code Pagination.refresh} path to
     * re-evaluate the source.
     *
     * @return the function; non-null only for {@link Kind#EAGER_LAZY}
     */
    public @Nullable Function<ViewContext, List<T>> lazyFunction() {
        return lazyFunction;
    }

    /**
     * Creates (or returns) the {@link PageSource} for one context.
     *
     * <ul>
     *   <li>{@link Kind#EAGER_STATIC}: returns the single shared {@link EagerPageSource}
     *       built by {@link #eager(List)}.</li>
     *   <li>{@link Kind#EAGER_LAZY}: invokes the source function with {@code context} and
     *       wraps the result in a fresh {@link EagerPageSource} (whose constructor takes the
     *       defensive copy).</li>
     *   <li>{@link Kind#ASYNC}: constructs a fresh {@link AsyncPageSource} from the supplier
     *       and the spec's async options, dispatching settles through a
     *       {@link BukkitSettleDispatcher}.</li>
     *   <li>{@link Kind#CUSTOM}: invokes the factory with {@code context} and returns its
     *       result as-is.</li>
     * </ul>
     *
     * @param context the context the source will serve
     * @param spec    the owning spec, read for the async options
     * @return the page source for this context
     * @throws NullPointerException if the lazy function returns null
     *         ("lazy pagination source function returned null"), or the custom factory
     *         returns null ("paginateSource factory returned null"), or an argument is null
     */
    public @NotNull PageSource<T> createSource(@NotNull ViewContext context, @NotNull PaginationSpec<T> spec) {
        Objects.requireNonNull(context, "context is required.");
        Objects.requireNonNull(spec, "spec is required.");
        switch (kind) {
            case EAGER_STATIC:
                return sharedEagerSource;
            case EAGER_LAZY:
                return new EagerPageSource<>(Objects.requireNonNull(lazyFunction.apply(context),
                        "lazy pagination source function returned null"));
            case ASYNC:
                return new AsyncPageSource<>(asyncSupplier, spec.errorCallback(), spec.requestTimeout(),
                        spec.cacheTtl(), spec.cacheMaxPages(), new BukkitSettleDispatcher());
            case CUSTOM:
                return Objects.requireNonNull(customFactory.apply(context),
                        "paginateSource factory returned null");
            default:
                // unreachable: the enum is exhaustive, but javac requires the branch
                throw new IllegalStateException("unknown source kind: " + kind);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationSourceSpecTest"`
Expected: PASS (9 tests, 0 failures)

- [ ] **Step 6: Run the full module suite**

Run: `$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B`
Expected: PASS (BUILD SUCCESS — the two new classes are leaves; nothing existing changes)

- [ ] **Step 7: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSpec.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSourceSpec.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSourceSpecTest.java
git commit -m "feat(inventory-api): add the PaginationSpec and PaginationSourceSpec declaration model" -m "immutable spec carrying geometry, target, renderer and async options, plus the four-kind source declaration with per-context source construction: the one shared eager source, lazy and custom factories with pinned null-result messages, and async option pass-through into AsyncPageSource."
```

---

### Task 7: ViewEngine additions + PaginationBinding

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBinding.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java

> **Mid-plan state (deliberate):** `ViewEngine.paginationSettle` delivers PAGINATION_SETTLE for ACTIVE and TRANSITIONING sessions, but `UpdatePhase`'s gate still drops every non-STATE_CHANGE trigger on non-ACTIVE sessions, and the pass repaints token *watchers* only — pagination-area repaint and the TRANSITIONING gate extension land with Task 11's `UpdatePhase` change. Tests here therefore drive area repaints by calling `binding.repaint()` explicitly and assert settle delivery only on ACTIVE sessions.
>
> **Seam handshake (Task 6):** the test constructs `PaginationSpec` directly (the builder arrives only in Task 8). It assumes Task 6 gave `PaginationSpec` a package-private all-args constructor in contract accessor order: `(Geometry geometry, Target target, char layoutChar, Layout explicitLayout, List<Layout> patterns, PaginationItemRenderer<T> renderer, Function<ViewContext, ItemStack> fallbackItem, Function<ViewContext, ItemStack> loadingItem, PaginationSourceSpec<T> source, PaginationErrorCallback errorCallback, Duration requestTimeout, Duration cacheTtl, int cacheMaxPages)`. The test funnels every construction through the single `specOf` helper, so a differing Task 6 constructor shape is a one-line reconciliation in that helper — nothing else in this task touches the constructor.
>
> **Resolved contract note:** replaying a pending target onto the *unbound* paginator is safe for every source kind — pinned rule 2 moves the unbound-host record-only branch to the TOP of `changePageInternal` (ahead of the totals-known clamp), and Task 3 ships that reorder with the `changePage_beforeBind_onNonEmptyEagerSource_recordsAndReclamps` regression test. Without the reorder, a totals-known (eager) source with NORMAL geometry would have divided by the still-zero `itemPageLimit` pre-bind. Do NOT re-add the 2.x clamp-first ordering here.

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationBindingTest {

    private static final Logger BINDING_LOGGER = Logger.getLogger(PaginationBinding.class.getName());

    private ServerMock server;
    private Plugin plugin;
    private ViewEngine engine;
    private PlayerMock player;

    static final class PagedView extends View {
    }

    static final class RecordingView extends View {
        final List<UpdateTrigger> triggers = new ArrayList<>();

        @Override
        protected void onUpdate(@NotNull UpdateContext context) {
            triggers.add(context.trigger());
        }
    }

    static final class WatchingView extends View {
        final MutableState<Integer> counter = mutableState(0);
    }

    /** synchronous source that records every request; totals stay unknown until the first settle (async-shaped) */
    static final class RecordingPageSource implements PageSource<Integer> {
        private final List<Integer> elements;
        final List<PageRequest> requests = new ArrayList<>();
        private boolean settled;

        RecordingPageSource(List<Integer> elements) {
            this.elements = elements;
        }

        @Override
        public void request(PageRequest request, BiConsumer<PageResult<Integer>, Throwable> onSettle) {
            requests.add(request);
            settled = true;
            int from = Math.min(Math.max(0, request.getOffset()), elements.size());
            int to = Math.min(from + request.getPageSize(), elements.size());
            onSettle.accept(PageResult.of(elements.subList(from, to), elements.size()), null);
        }

        @Override
        public int totalElements() {
            return settled ? elements.size() : 0;
        }

        @Override
        public boolean totalsKnown() {
            return settled;
        }

        @Override
        public boolean isLoading() {
            return false;
        }

        @Override
        public Throwable lastError() {
            return null;
        }

        @Override
        public List<Integer> elements() {
            return elements;
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        engine = new ViewEngine(plugin, new ViewRegistry(), new SessionRegistry(),
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
    }

    @AfterEach
    void tearDown() {
        // nothing here schedules tasks today, but unmock drains the scheduler queue;
        // cancel defensively so a future deferred op never runs against a dead registry
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    private static ViewConfig layoutConfig() {
        // 'O' occupies slots 2, 3 and 4 of the single row
        return new ViewConfigBuilder().title("Paged").layout("  OOO    ").build();
    }

    private static ViewConfig rowsConfig() {
        return new ViewConfigBuilder().title("Paged").rows(1).build();
    }

    // builds a session exactly like ContextPhaseValidityTest: direct RegisteredView + StateStore wiring
    private ViewSession sessionFor(View view, ViewConfig config) {
        RegisteredView registered = new RegisteredView(view.getClass(), view, config);
        ViewSession session = new ViewSession(player, registered, ViewArguments.empty(),
                new StateStore(view.tokenTable().size()));
        session.effectiveConfig(config);
        session.layout(ResolvedLayout.resolve(config));
        session.inventory(Bukkit.createInventory(null, config.rows() * Layout.ROW_WIDTH));
        session.status(ViewSession.Status.ACTIVE);
        return session;
    }

    // single construction point for Task 6's package-private all-args constructor
    // (contract accessor order); async-only options stay at their defaults here
    private static <T> PaginationSpec<T> specOf(PaginationSpec.Geometry geometry,
                                                PaginationSpec.Target target,
                                                char layoutChar,
                                                Layout explicitLayout,
                                                List<Layout> patterns,
                                                PaginationItemRenderer<T> renderer,
                                                Function<ViewContext, ItemStack> fallbackItem,
                                                PaginationSourceSpec<T> source) {
        return new PaginationSpec<>(geometry, target, layoutChar, explicitLayout, patterns,
                renderer, fallbackItem, null, source, null, null, null, 128);
    }

    private static <T> PaginationSpec<T> layoutCharSpec(PaginationItemRenderer<T> renderer,
                                                        PaginationSourceSpec<T> source) {
        return specOf(PaginationSpec.Geometry.NORMAL, PaginationSpec.Target.LAYOUT_CHAR, 'O',
                null, Collections.emptyList(), renderer, null, source);
    }

    private static PaginationItemRenderer<Integer> amountRenderer() {
        return (context, item, index, value) -> item.item(new ItemStack(Material.PAPER, value));
    }

    @Test
    void pendingNavigation_startsAtOneRecordsAndClampsAtOne() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);

        assertEquals(1, binding.pendingTarget());
        assertFalse(binding.hasPendingNavigation());
        assertFalse(binding.isInitialized());
        assertNull(binding.paginator());

        binding.recordSwitchTo(-3);
        assertTrue(binding.hasPendingNavigation());
        assertEquals(1, binding.pendingTarget(), "targets below 1 clamp to 1");

        binding.recordSwitchTo(4);
        assertEquals(4, binding.pendingTarget(), "the latest recorded target wins");
    }

    @Test
    void repaint_beforeInitialize_isANoOp() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);

        assertDoesNotThrow(binding::repaint);
        assertNull(session.inventory().getItem(2));
    }

    @Test
    void initialize_layoutChar_paintsEagerItemsIntoTheCharSlots() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3, 4, 5))),
                0, session, engine);

        binding.initialize(session.layout(), session.effectiveConfig());
        assertTrue(binding.isInitialized());
        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(1, inventory.getItem(2).getAmount());
        assertEquals(2, inventory.getItem(3).getAmount());
        assertEquals(3, inventory.getItem(4).getAmount());
        assertNull(inventory.getItem(1), "slots outside the area stay untouched");
        assertNull(inventory.getItem(5));
    }

    @Test
    void targetSlots_emptyBeforeInitialize_resolvedAfter() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);

        assertEquals(0, binding.targetSlots().length);

        binding.initialize(session.layout(), session.effectiveConfig());

        // layoutConfig's 'O' chars sit at slots 2, 3 and 4 (row-major)
        assertArrayEquals(new int[]{2, 3, 4}, binding.targetSlots());
        // defensive copy: mutating the returned array does not corrupt the binding
        binding.targetSlots()[0] = 99;
        assertArrayEquals(new int[]{2, 3, 4}, binding.targetSlots());
    }

    @Test
    void initialize_layoutCharWithoutSlots_throwsViewConfigurationException() {
        // a rows-only config has no layout, so 'O' resolves to zero slots
        ViewSession session = sessionFor(new PagedView(), rowsConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> binding.initialize(session.layout(), session.effectiveConfig()));

        assertTrue(error.getMessage().contains("'O'"));
        assertFalse(binding.isInitialized());
    }

    @Test
    void initialize_explicitLayoutSlotOutOfBounds_throwsNamingSlotAndRows() {
        ViewSession session = sessionFor(new PagedView(), rowsConfig());
        PaginationSpec<Integer> spec = specOf(PaginationSpec.Geometry.NORMAL,
                PaginationSpec.Target.EXPLICIT_LAYOUT, 'O', Layout.ofSlots(0, 9),
                Collections.emptyList(), amountRenderer(), null,
                PaginationSourceSpec.eager(Arrays.asList(1, 2, 3)));
        PaginationBinding binding = new PaginationBinding(spec, 0, session, engine);

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> binding.initialize(session.layout(), session.effectiveConfig()));

        assertTrue(error.getMessage().contains("slot 9"));
        assertTrue(error.getMessage().contains("1-row"));
    }

    @Test
    void initialize_patternSlotOutOfBounds_throwsViewConfigurationException() {
        ViewSession session = sessionFor(new PagedView(), rowsConfig());
        PaginationSpec<Integer> spec = specOf(PaginationSpec.Geometry.PATTERN,
                PaginationSpec.Target.PATTERNS, 'O', null,
                Arrays.asList(Layout.ofSlots(0, 1), Layout.ofSlots(10)),
                amountRenderer(), null, PaginationSourceSpec.eager(Arrays.asList(1, 2, 3)));
        PaginationBinding binding = new PaginationBinding(spec, 0, session, engine);

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> binding.initialize(session.layout(), session.effectiveConfig()));

        assertTrue(error.getMessage().contains("slot 10"));
    }

    @Test
    void initialize_consumesThePendingTargetThroughTheUnboundPaginator() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        RecordingPageSource source = new RecordingPageSource(Arrays.asList(1, 2, 3, 4, 5));
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.custom(context -> source)),
                0, session, engine);

        binding.recordSwitchTo(2);
        binding.initialize(session.layout(), session.effectiveConfig());

        assertEquals(2, binding.paginator().getCurrentPage());
        // bind dispatched exactly one load, already for the recorded page: 3 'O' slots
        // per page, so page 2 starts at offset 3
        assertEquals(1, source.requests.size());
        PageRequest request = source.requests.get(0);
        assertEquals(2, request.getPage());
        assertEquals(3, request.getPageSize());
        assertEquals(3, request.getOffset());
        // the slim request is populated from the host seam
        assertEquals(player.getUniqueId(), request.playerId());
        assertSame(plugin, request.plugin());

        binding.repaint();
        Inventory inventory = session.inventory();
        assertEquals(4, inventory.getItem(2).getAmount());
        assertEquals(5, inventory.getItem(3).getAmount());
        assertNull(inventory.getItem(4), "the empty tail of the last page clears (no fallback declared)");
    }

    @Test
    void repaint_registersElementComponentsAndReplacesThemPerPass() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) ->
                item.item(new ItemStack(Material.PAPER, value)).onClick(click -> {
                });
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(renderer, PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();
        ComponentInstance first = binding.componentAt(2);
        assertNotNull(first);
        assertNotNull(first.handlerFor(ClickType.LEFT), "the element's click handler is materialized");
        assertNull(binding.componentAt(1), "non-area slots hold no element component");

        binding.repaint();
        ComponentInstance second = binding.componentAt(2);
        assertNotNull(second);
        assertNotSame(first, second, "every area repaint materializes fresh element components");
    }

    @Test
    void refreshLazy_swapsTheSourceAndFrameItemsClearStaleElements() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        AtomicReference<List<Integer>> backing = new AtomicReference<>(Arrays.asList(1, 2, 3));
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.lazy(context -> backing.get())),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());
        binding.repaint();
        assertEquals(3, session.inventory().getItem(4).getAmount());
        assertNotNull(binding.componentAt(4));

        backing.set(Collections.singletonList(9));
        binding.refreshLazy(new PlainViewContextImpl(session, engine));
        // mid-plan: the settle pass repaints token watchers only; Task 11 makes it repaint
        // the area, so this test drives the area repaint explicitly
        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(9, inventory.getItem(2).getAmount(), "the re-invoked lazy source renders");
        assertNull(inventory.getItem(3), "the shrunk source clears the now-empty area slots");
        assertNull(inventory.getItem(4));
        assertNull(binding.componentAt(3), "a frame item removes the slot's element component");
        assertNull(binding.componentAt(4));
        assertNotNull(binding.componentAt(2));
    }

    @Test
    void rendererThrow_keepsPreviousContentAndComponentAndLogsOncePerMinute() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        AtomicBoolean fail = new AtomicBoolean(false);
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) -> {
            if (fail.get() && index == 0) {
                throw new IllegalStateException("boom");
            }
            item.item(new ItemStack(Material.PAPER, value)).onClick(click -> {
            });
        };
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(renderer, PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());
        binding.repaint();
        ComponentInstance healthy = binding.componentAt(2);
        assertEquals(1, session.inventory().getItem(2).getAmount());

        CapturingHandler handler = new CapturingHandler();
        BINDING_LOGGER.addHandler(handler);
        try {
            fail.set(true);
            binding.repaint();
            binding.repaint();
        } finally {
            BINDING_LOGGER.removeHandler(handler);
        }

        // the failed element keeps its previous item AND its previous click component
        assertEquals(1, session.inventory().getItem(2).getAmount());
        assertSame(healthy, binding.componentAt(2));
        // the healthy elements of the same passes re-rendered as usual
        assertEquals(2, session.inventory().getItem(3).getAmount());
        // two failing passes inside the same minute log exactly one SEVERE record
        assertEquals(1, handler.records.size());
        assertEquals(Level.SEVERE, handler.records.get(0).getLevel());
    }

    @Test
    void firstPaintFailure_paintsTheFallbackItem() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) -> {
            if (index == 0) {
                throw new IllegalStateException("boom");
            }
            item.item(new ItemStack(Material.PAPER, value));
        };
        PaginationSpec<Integer> spec = specOf(PaginationSpec.Geometry.NORMAL,
                PaginationSpec.Target.LAYOUT_CHAR, 'O', null, Collections.emptyList(),
                renderer, context -> new ItemStack(Material.BARRIER),
                PaginationSourceSpec.eager(Arrays.asList(1, 2, 3)));
        PaginationBinding binding = new PaginationBinding(spec, 0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(Material.BARRIER, inventory.getItem(2).getType());
        assertNull(binding.componentAt(2), "a first-paint failure paints the fallback, not a component");
        assertEquals(2, inventory.getItem(3).getAmount());
    }

    @Test
    void rendererWithoutItemSource_isTreatedAsAFailure() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) -> {
            if (index == 0) {
                // click handler but no item source: pinned rule 7 demands failure semantics
                item.onClick(click -> {
                });
                return;
            }
            item.item(new ItemStack(Material.PAPER, value));
        };
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(renderer, PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        CapturingHandler handler = new CapturingHandler();
        BINDING_LOGGER.addHandler(handler);
        try {
            binding.repaint();
        } finally {
            BINDING_LOGGER.removeHandler(handler);
        }

        // no fallback declared: the first paint of a sourceless element clears the slot
        // and registers no component
        assertNull(session.inventory().getItem(2));
        assertNull(binding.componentAt(2));
        assertEquals(1, handler.records.size());
        assertEquals(Level.SEVERE, handler.records.get(0).getLevel());
    }

    @Test
    void elementWatchersOf_matchesWatchedTokenIds() {
        WatchingView view = new WatchingView();
        ViewSession session = sessionFor(view, layoutConfig());
        PaginationItemRenderer<Integer> renderer = (context, item, index, value) ->
                item.item(new ItemStack(Material.PAPER, value)).updateOnStateChange(view.counter);
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(renderer, PaginationSourceSpec.eager(Arrays.asList(1, 2))),
                7, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());
        binding.repaint();

        assertEquals(7, binding.tokenId());
        // the counter token has id 0; both elements watch it, the empty third slot is a frame
        assertEquals(2, binding.elementWatchersOf(Collections.singleton(0)).size());
        assertTrue(binding.elementWatchersOf(Collections.singleton(99)).isEmpty());
    }

    @Test
    void hostIsActive_trueForActiveAndTransitioningOnly() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1))),
                0, session, engine);

        session.status(ViewSession.Status.OPENING);
        assertFalse(binding.isActive());
        session.status(ViewSession.Status.ACTIVE);
        assertTrue(binding.isActive());
        session.status(ViewSession.Status.TRANSITIONING);
        assertTrue(binding.isActive());
        session.status(ViewSession.Status.CLOSED);
        assertFalse(binding.isActive());
    }

    @Test
    void requestRender_activeSession_firesOnUpdateWithPaginationSettle() {
        RecordingView view = new RecordingView();
        ViewSession session = sessionFor(view, layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());
        assertTrue(view.triggers.isEmpty(), "binding the eager engine must not fire an update");

        binding.requestRender();

        assertEquals(Collections.singletonList(UpdateTrigger.PAGINATION_SETTLE), view.triggers);
    }

    @Test
    void requestRender_closedOrOpeningSession_isDropped() {
        RecordingView view = new RecordingView();
        ViewSession session = sessionFor(view, layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2, 3))),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        session.status(ViewSession.Status.OPENING);
        binding.requestRender();
        session.status(ViewSession.Status.CLOSED);
        binding.requestRender();

        assertTrue(view.triggers.isEmpty(), "OPENING and CLOSED sessions drop settles entirely");
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationBindingTest"
```

Expected: FAIL — test compilation error: `cannot find symbol: class PaginationBinding` in package `tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination` (and `ViewEngine` has no `painter()`/`paginationSettle` yet, which the binding will need).

- [ ] **Step 3: Add the ViewEngine entry points**

In `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java`, insert `paginationSettle` immediately **after** the existing `update(...)` method (the one ending with `flushDirty(session);` before `click`). Add `import java.util.Collections;` to the `java.util` import block — Task 1's FlushCoordinator extraction removed it from this file together with the moved flush bodies (`UpdateTrigger`, `ThreadUtils` and `SlotPainter` are still imported).

```java
    /**
     * Runs a pagination-settle update pass scoped to the settled token, then flushes any
     * state the handlers dirtied. ACTIVE and TRANSITIONING sessions receive the settle;
     * CLOSED and OPENING sessions drop it entirely — no paint, no watcher marking, no
     * {@code onUpdate} (§7).
     *
     * @param session the session whose pagination token settled
     * @param tokenId the token id of the settled pagination declaration
     * @throws IllegalStateException when called off the main thread
     */
    public void paginationSettle(@NotNull ViewSession session, int tokenId) {
        ThreadUtils.assertMainThread("ViewEngine.paginationSettle");
        ViewSession.Status status = session.status();
        if (status == ViewSession.Status.CLOSED || status == ViewSession.Status.OPENING) {
            return;
        }
        updatePhase.update(session, UpdateTrigger.PAGINATION_SETTLE, Collections.singleton(tokenId));
        flushDirty(session);
    }
```

Then insert the `painter()` accessor immediately **before** the existing `plugin()` accessor at the bottom of the class:

```java
    /**
     * Returns the slot painter used by the rendering phases; pagination bindings paint
     * their page frames through it.
     *
     * @return the slot painter
     */
    public @NotNull SlotPainter painter() {
        return painter;
    }
```

Mid-plan note (deliberate, do not "fix" here): the engine gate admits TRANSITIONING, but `UpdatePhase`'s gate still drops PAGINATION_SETTLE for non-ACTIVE sessions, and the pass repaints only the components watching the settled token — pagination-area repaint and the TRANSITIONING delivery land with Task 11's `UpdatePhase` extension.

- [ ] **Step 4: Write PaginationBinding**

Create `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBinding.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ItemComponentBuilderImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.NormalPagination;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.PageItemFactory;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.Paginator;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.PatternPagination;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.ScrollPagination;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-(session, token) pagination runtime: records pre-init navigation, builds the
 * per-context page source and geometry engine at open time, owns the page-element
 * components of the current frame and implements the {@link PaginationHost} seam the
 * relocated engine paints and settles through.
 *
 * <p>The binding deliberately does not reference {@code PaginationImpl} — the dependency
 * points the other way (the token resolves its binding from the session's state store);
 * the binding works on the immutable {@link PaginationSpec} and its token id alone. The
 * spec's {@code T} and the paginator's {@code T} originate from the same {@code paginate*}
 * declaration, so the binding pairs them internally on {@code Object} through a documented
 * unchecked cast. Renderer, fallback, loading and lazy-source functions are evaluated
 * against one {@link PlainViewContextImpl} the binding creates at initialize time.
 *
 * <p>Renderer, fallback and loading failures are swallowed and logged at most once per
 * minute per binding (§9 error table); the affected slot keeps its previous content, or
 * shows the fallback item (or clears) when it never held content.
 */
@ApiStatus.Internal
public final class PaginationBinding implements PaginationHost {

    private static final Logger LOGGER = Logger.getLogger(PaginationBinding.class.getName());
    private static final long LOG_INTERVAL_MILLIS = 60_000L;

    private final PaginationSpec<Object> spec;
    private final int tokenId;
    private final ViewSession session;
    private final ViewEngine engine;

    // pre-init pending navigation; consumed once by initialize
    private int pendingTarget = 1;
    private boolean navigationRecorded;

    // initialize-time collaborators; null until initialize ran (or when it aborted mid-way)
    private PlainViewContextImpl plainContext;
    private Supplier<RenderedItem> fallbackSupplier;
    private Supplier<RenderedItem> loadingSupplier;
    private Paginator<Object> paginator;

    // resolved fill slots; empty until initialize ran — FirstRenderPhase validates these
    // against the static component table (overlap check, Task 12)
    private int[] targetSlots = new int[0];

    // current frame: the page-element component per slot, in fill order, plus what was
    // last applied per slot — lastApplied keys drive the first-paint-fallback decision
    private final Map<Integer, ComponentInstance> elementComponents = new LinkedHashMap<>();
    private final Map<Integer, RenderedItem> lastApplied = new HashMap<>();

    // rate limit for renderer/fallback/loading failure logs; a racy double log is acceptable
    private volatile long lastLogMillis;

    /**
     * Creates the binding for one pagination token of one session.
     *
     * @param spec    the immutable pagination declaration of the token
     * @param tokenId the token id assigned by the owning view's token table
     * @param session the session this binding belongs to
     * @param engine  the engine providing the painter, the settle entry point and the plugin
     */
    @SuppressWarnings("unchecked")
    public PaginationBinding(@NotNull PaginationSpec<?> spec, int tokenId,
                             @NotNull ViewSession session, @NotNull ViewEngine engine) {
        // documented unchecked cast: the spec's T and the paginator's T originate from the
        // same paginate* declaration; the binding pairs them internally on Object
        this.spec = (PaginationSpec<Object>) Objects.requireNonNull(spec, "spec");
        this.tokenId = tokenId;
        this.session = Objects.requireNonNull(session, "session");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Returns the page navigation will land on once the binding initializes.
     *
     * @return the pending 1-indexed target page; {@code 1} until a navigation was recorded
     */
    public int pendingTarget() {
        return pendingTarget;
    }

    /**
     * Records a pre-init navigation target; consumed once by {@link #initialize}.
     *
     * @param target the requested 1-indexed page; clamped to at least {@code 1}
     */
    public void recordSwitchTo(int target) {
        this.pendingTarget = Math.max(1, target);
        this.navigationRecorded = true;
    }

    /**
     * Returns whether a pre-init navigation was recorded.
     *
     * @return {@code true} once {@link #recordSwitchTo} ran before {@link #initialize}
     */
    public boolean hasPendingNavigation() {
        return navigationRecorded;
    }

    /**
     * Builds the per-context runtime: resolves and validates the fill target, creates the
     * per-context page source and the geometry engine, replays any pending navigation on
     * the still-unbound paginator (the preserved 2.x record-only branch) and binds the
     * engine to this host — which dispatches the initial page load. Eager sources settle
     * inline here so their items are ready before the open's first paint; async sources
     * stay loading so the first paint shows the loading frame.
     *
     * @param viewLayout      the session's resolved config layout
     * @param effectiveConfig the session's effective config
     * @throws ViewConfigurationException when the layout char resolves to no slots, the
     *                                    explicit layout is empty, or an explicit layout
     *                                    or pattern slot lies outside the container
     */
    public void initialize(@NotNull ResolvedLayout viewLayout, @NotNull ViewConfig effectiveConfig) {
        Objects.requireNonNull(viewLayout, "viewLayout");
        Objects.requireNonNull(effectiveConfig, "effectiveConfig");

        Layout fillLayout = null;
        List<Layout> patterns = null;
        if (spec.geometry() == PaginationSpec.Geometry.PATTERN) {
            patterns = spec.patterns();
            for (Layout pattern : patterns) {
                checkBounds(pattern, effectiveConfig);
            }
        } else {
            fillLayout = resolveFillLayout(viewLayout, effectiveConfig);
        }
        this.targetSlots = resolveTargetSlots(fillLayout, patterns);

        this.plainContext = new PlainViewContextImpl(session, engine);
        PageSource<Object> source = spec.source().createSource(plainContext, spec);
        this.fallbackSupplier = frameSupplier(spec.fallbackItem(), "fallback item");
        this.loadingSupplier = frameSupplier(spec.loadingItem(), "loading item");
        PageItemFactory<Object> factory = elementFactory();

        Paginator<Object> built;
        switch (spec.geometry()) {
            case NORMAL:
                built = new NormalPagination<>(fallbackSupplier, factory, fillLayout, loadingSupplier, source);
                break;
            case SCROLL:
                built = new ScrollPagination<>(fallbackSupplier, factory, fillLayout, loadingSupplier, source);
                break;
            case PATTERN:
                built = new PatternPagination<>(fallbackSupplier, factory, patterns, loadingSupplier, source);
                break;
            default:
                throw new IllegalStateException("unhandled pagination geometry " + spec.geometry());
        }

        if (navigationRecorded) {
            // replay on the still-unbound paginator: the preserved 2.x record-only branch
            // stores the target; bind then derives the layout state for that page and
            // dispatches the initial load for it
            built.changePage(pendingTarget);
        }
        this.paginator = built;
        built.bind(this);
    }

    /**
     * Returns whether {@link #initialize} completed and the geometry engine is bound.
     *
     * @return {@code true} once the paginator exists
     */
    public boolean isInitialized() {
        return paginator != null;
    }

    /**
     * Returns the bound geometry engine.
     *
     * @return the paginator, or {@code null} before {@link #initialize} completed
     */
    public @Nullable Paginator<?> paginator() {
        return paginator;
    }

    /**
     * Returns the slots this binding paints into: the fill layout's slots for normal and
     * scroll geometry, the union of all pattern slots for pattern geometry.
     *
     * @return a defensive copy of the resolved fill slots; empty before {@link #initialize}
     */
    public @NotNull int[] targetSlots() {
        return targetSlots.clone();
    }

    /**
     * Returns the page-element component currently occupying a slot.
     *
     * @param slot the raw container slot
     * @return the element component, or {@code null} when the slot holds none
     */
    public @Nullable ComponentInstance componentAt(int slot) {
        return elementComponents.get(slot);
    }

    private static int[] resolveTargetSlots(Layout fillLayout, List<Layout> patterns) {
        if (fillLayout != null) {
            List<Integer> slots = fillLayout.slots();
            int[] resolved = new int[slots.size()];
            for (int i = 0; i < resolved.length; i++) {
                resolved[i] = slots.get(i);
            }
            return resolved;
        }
        // pattern geometry: the union of every pattern's slots, first-encounter order
        Set<Integer> union = new LinkedHashSet<>();
        for (Layout pattern : patterns) {
            union.addAll(pattern.slots());
        }
        int[] resolved = new int[union.size()];
        int i = 0;
        for (Integer slot : union) {
            resolved[i++] = slot;
        }
        return resolved;
    }

    /**
     * Repaints the whole pagination area from the engine's current frame.
     */
    public void repaint() {
        // full passes may reach a binding whose initialize aborted mid-open (the abort
        // tears the session down right after); painting nothing is the safe behavior
        if (paginator == null) {
            return;
        }
        paginator.insertPageItems();
    }

    /**
     * Returns the current page-element components watching any of the given token ids.
     *
     * @param dirtyTokenIds the dirty token ids of a state flush
     * @return the matching element components, each at most once, in fill order
     */
    public @NotNull List<ComponentInstance> elementWatchersOf(@NotNull Set<Integer> dirtyTokenIds) {
        Objects.requireNonNull(dirtyTokenIds, "dirtyTokenIds");
        List<ComponentInstance> watchers = new ArrayList<>();
        for (ComponentInstance component : elementComponents.values()) {
            // watchedTokenIdsInternal() is package-private to internal.component; the
            // public clone accessor is what is visible here, and element watch lists are tiny
            for (int watched : component.watchedTokenIds()) {
                if (dirtyTokenIds.contains(watched)) {
                    watchers.add(component);
                    break;
                }
            }
        }
        return watchers;
    }

    /**
     * Returns the id of the pagination token this binding belongs to.
     *
     * @return the token id
     */
    public int tokenId() {
        return tokenId;
    }

    /**
     * Re-invokes a lazy eager source's function against the given context, replaces the
     * paginator's source with a fresh eager source over the result and re-requests the
     * current page (forced).
     *
     * @param context the context the lazy source function is evaluated against
     * @throws IllegalStateException when the binding's source is not a lazy eager source
     */
    public void refreshLazy(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        Paginator<Object> current = this.paginator;
        if (current == null) {
            // pre-init refresh is a no-op (PaginationImpl gates this; defend anyway)
            return;
        }
        Function<ViewContext, List<Object>> fn = spec.source().lazyFunction();
        if (fn == null) {
            throw new IllegalStateException("refreshLazy is only legal for a lazy eager source");
        }
        current.replaceSource(new EagerPageSource<>(fn.apply(context)));
        current.refresh();
    }

    @Override
    public boolean isActive() {
        ViewSession.Status status = session.status();
        return status == ViewSession.Status.ACTIVE || status == ViewSession.Status.TRANSITIONING;
    }

    @Override
    public void fillPage(@NotNull List<RenderedItem> items, @NotNull Layout layout) {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            // nothing to paint into; component bookkeeping would desync from the container
            return;
        }
        List<Integer> slots = layout.slots();
        // the engine builds exactly slots-many items; bound defensively anyway
        int bound = Math.min(items.size(), slots.size());
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();
        for (int i = 0; i < bound; i++) {
            applyToSlot(inventory, slots.get(i), items.get(i), applyPlaceholders);
        }
    }

    @Override
    public void requestRender() {
        engine.paginationSettle(session, tokenId);
    }

    @Override
    public @Nullable UUID playerId() {
        return session.player().getUniqueId();
    }

    @Override
    public @NotNull Plugin plugin() {
        return engine.plugin();
    }

    private void applyToSlot(Inventory inventory, int slot, RenderedItem item, boolean applyPlaceholders) {
        if (item.isFailure()) {
            if (lastApplied.containsKey(slot)) {
                // keep the previous slot content and element component entirely
                return;
            }
            // first paint of a failed slot: the fallback item, or a cleared slot
            RenderedItem fallback = firstPaintFallback();
            engine.painter().paint(session.player(), inventory, slot, fallback.plainItem(), applyPlaceholders);
            lastApplied.put(slot, fallback);
            return;
        }
        ItemComponentBuilderImpl elementBuilder = item.elementBuilder();
        if (elementBuilder == null) {
            // frame item: paint the plain stack (null clears) and drop any element component
            elementComponents.remove(slot);
            engine.painter().paint(session.player(), inventory, slot, item.plainItem(), applyPlaceholders);
            lastApplied.put(slot, item);
            return;
        }
        ComponentInstance component = elementBuilder.materialize(new int[]{slot});
        ItemStack rendered = component.renderForPaint(plainContext);
        if (rendered != ComponentInstance.RENDER_FAILURE) {
            engine.painter().paint(session.player(), inventory, slot, rendered, applyPlaceholders);
        }
        // identity check above: a failed item function keeps the previous slot content,
        // but the freshly materialized component still takes over the slot's handlers
        elementComponents.put(slot, component);
        lastApplied.put(slot, item);
    }

    private Layout resolveFillLayout(ResolvedLayout viewLayout, ViewConfig effectiveConfig) {
        if (spec.target() == PaginationSpec.Target.EXPLICIT_LAYOUT) {
            Layout explicit = spec.explicitLayout();
            if (explicit == null || explicit.slots().isEmpty()) {
                // the builder validates this at build(); defend against a hand-built spec
                throw new ViewConfigurationException("pagination of view "
                        + session.registered().type().getName()
                        + " declares an empty explicit layout");
            }
            checkBounds(explicit, effectiveConfig);
            return explicit;
        }
        // LAYOUT_CHAR: registration already guarantees presence; defend against an empty
        // resolution anyway so a mis-built spec fails loudly instead of painting nothing
        char character = spec.layoutChar();
        int[] slots = viewLayout.slotsOf(character);
        if (slots.length == 0) {
            throw new ViewConfigurationException("pagination layout char '" + character
                    + "' is not present in the layout of view "
                    + session.registered().type().getName());
        }
        return Layout.ofSlots(slots);
    }

    private void checkBounds(Layout layout, ViewConfig effectiveConfig) {
        int size = effectiveConfig.rows() * Layout.ROW_WIDTH;
        for (Integer slot : layout.slots()) {
            if (slot >= size) {
                throw new ViewConfigurationException("pagination layout slot " + slot
                        + " is out of bounds for a " + effectiveConfig.rows() + "-row view of "
                        + session.registered().type().getName());
            }
        }
    }

    private PageItemFactory<Object> elementFactory() {
        final PaginationItemRenderer<Object> renderer = spec.renderer();
        return (index, value) -> {
            // fresh builder per element per area repaint (pinned rule 7)
            ItemComponentBuilderImpl collected = new ItemComponentBuilderImpl();
            SourceTrackingBuilder builder = new SourceTrackingBuilder(collected);
            try {
                renderer.render(plainContext, builder, index, value);
            } catch (RuntimeException error) {
                logFailure("element renderer", error);
                return RenderedItem.failure();
            }
            if (!builder.itemSourceDeclared()) {
                logFailure("element renderer", new ViewConfigurationException(
                        "pagination element renderer declared no item source; "
                                + "call item(ItemStack) or item(Function)"));
                return RenderedItem.failure();
            }
            return RenderedItem.ofElement(collected);
        };
    }

    private @Nullable Supplier<RenderedItem> frameSupplier(@Nullable final Function<ViewContext, ItemStack> fn,
                                                           final String stage) {
        if (fn == null) {
            // a null supplier lets the engine's emptyOrFallback/loadingOrFallback defaults apply
            return null;
        }
        return () -> {
            try {
                return RenderedItem.ofItem(fn.apply(plainContext));
            } catch (RuntimeException error) {
                logFailure(stage, error);
                return RenderedItem.failure();
            }
        };
    }

    private RenderedItem firstPaintFallback() {
        Supplier<RenderedItem> fallback = this.fallbackSupplier;
        if (fallback == null) {
            return RenderedItem.ofItem(null);
        }
        RenderedItem item = fallback.get();
        // a fallback that itself failed degrades to clearing the slot
        return item.isFailure() ? RenderedItem.ofItem(null) : item;
    }

    private void logFailure(String stage, RuntimeException error) {
        long now = System.currentTimeMillis();
        if (now - lastLogMillis < LOG_INTERVAL_MILLIS) {
            return;
        }
        lastLogMillis = now;
        LOGGER.log(Level.SEVERE, "pagination " + stage + " failed for view "
                + session.registered().type().getName(), error);
    }

    /**
     * Delegating view of the per-element builder handed to the user renderer: records
     * whether an item source was declared — the one builder fact the failure semantics
     * need that neither the builder nor the materialized component exposes publicly —
     * while collecting everything into the wrapped implementation.
     */
    private static final class SourceTrackingBuilder implements ItemComponentBuilder {

        private final ItemComponentBuilderImpl delegate;
        private boolean itemSourceDeclared;

        SourceTrackingBuilder(ItemComponentBuilderImpl delegate) {
            this.delegate = delegate;
        }

        boolean itemSourceDeclared() {
            return itemSourceDeclared;
        }

        @Override
        public @NotNull ItemComponentBuilder item(@NotNull ItemStack item) {
            delegate.item(item);
            itemSourceDeclared = true;
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder item(@NotNull Function<ViewContext, ItemStack> renderer) {
            delegate.item(renderer);
            itemSourceDeclared = true;
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder displayIf(@NotNull Predicate<ViewContext> condition) {
            delegate.displayIf(condition);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder updateOnStateChange(@NotNull StateToken... tokens) {
            delegate.updateOnStateChange(tokens);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder onClick(@NotNull Consumer<SlotClickContext> handler) {
            delegate.onClick(handler);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder onClick(@NotNull ClickType type,
                                                     @NotNull Consumer<SlotClickContext> handler) {
            delegate.onClick(type, handler);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder cancelOnClick(boolean cancel) {
            delegate.cancelOnClick(cancel);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder closeOnClick() {
            delegate.closeOnClick();
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target) {
            delegate.openOnClick(target);
            return this;
        }

        @Override
        public @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target,
                                                         @NotNull ViewArguments arguments) {
            delegate.openOnClick(target, arguments);
            return this;
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationBindingTest"
```

Expected: PASS (17 tests).

- [ ] **Step 6: Full module check**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS — no existing test touches `painter()`/`paginationSettle`, and `PaginationBinding` is new code with no callers yet.

- [ ] **Step 7: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBinding.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java
git commit -m "feat(inventory-api): add ViewEngine settle entry points and PaginationBinding" -m "ViewEngine gains painter() and the token-scoped paginationSettle pass (ACTIVE/TRANSITIONING delivered, CLOSED/OPENING dropped, dirty flush after). PaginationBinding carries the per-(session, token) runtime: pending navigation, per-context source and geometry-engine construction, element-component fill semantics with first-paint-fallback failure handling, and the PaginationHost seam. Until Task 11 extends UpdatePhase, settle passes repaint token watchers only."
```

---

### Task 8: PaginationBuilderImpl + PaginationImpl + ContextStateAccess consolidation

Three parts, one combined `feat(inventory-api)` commit (the guard consolidation is a refactor and is noted in the commit body):

- **Part A** widens `ContextStateAccess` to public and consolidates the `storeFor` guard that is currently triplicated verbatim across `MutableStateImpl`, `LazyStateImpl` and `InitialStateImpl` (reviewer backlog #6). Both `StaleContextException` message strings are preserved verbatim.
- **Part B** adds `internal/pagination/PaginationBuilderImpl` — the `PaginationBuilder` implementation with the full build-time validation matrix (pinned rule 12).
- **Part C** adds `internal/pagination/PaginationImpl` — the `Pagination` token, registered at build time, delegating every call to the per-context `PaginationBinding` per the pre/post-init delegation table (pinned rule 6).

Depends on Task 5 (public `Pagination`/`PaginationBuilder`/`PaginationItemRenderer` interfaces), Task 6 (`PaginationSpec`/`PaginationSourceSpec`) and Task 7 (`PaginationBinding`, `ViewEngine.paginationSettle`). The `PaginationSpec` constructor is the all-fields constructor Task 6 defines, parameters in accessor declaration order: `(geometry, target, layoutChar, explicitLayout, patterns, renderer, fallbackItem, loadingItem, source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages)` — if Task 6 landed a different shape, adapt the single `new PaginationSpec<>(...)` call in Step 5 and nothing else.

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImpl.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/ContextStateAccess.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/MutableStateImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/LazyStateImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/InitialStateImpl.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImplTest.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationImplTest.java

- [ ] **Step 1: Write the failing builder test**

Full file `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImplTest.java`. Pure unit test — no MockBukkit (no server present, so `ThreadUtils.assertMainThread` is skipped; `build()` never asserts threads anyway).

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationBuilderImplTest {

    private static final class TestView extends View {
    }

    private static <T> PaginationItemRenderer<T> renderer() {
        return (context, item, index, value) -> {
        };
    }

    private static PaginationBuilderImpl<String> eagerBuilder(View owner) {
        return new PaginationBuilderImpl<>(owner, owner.tokenTable(),
                PaginationSourceSpec.eager(Arrays.asList("a", "b", "c")));
    }

    private static PaginationBuilderImpl<String> asyncBuilder(View owner) {
        PaginationSourceSpec<String> source = PaginationSourceSpec.async(request ->
                CompletableFuture.completedFuture(PageResult.of(Collections.<String>emptyList(), 0)));
        return new PaginationBuilderImpl<>(owner, owner.tokenTable(), source);
    }

    private static Layout nonEmptyLayout() {
        return Layout.ofSlots(0, 1, 2);
    }

    private static Layout emptyLayout() {
        return Layout.ofGrid("         ");
    }

    // --- rule 12 clause 1: renderer required, validated first ---

    @Test
    void build_withoutRenderer_throwsViewConfigurationException() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("itemRenderer"),
                "message must name the missing itemRenderer, got: " + thrown.getMessage());
    }

    @Test
    void build_missingRenderer_isReportedBeforePatternConflicts() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.scroll().patterns(nonEmptyLayout());

        // validation order is pinned: the missing renderer wins over the patterns conflict
        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("itemRenderer"));
    }

    // --- rule 12 clause 2: patterns conflicts ---

    @Test
    void build_patternsCombinedWithExplicitLayoutChar_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).layoutChar('P').patterns(nonEmptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("patterns"));
    }

    @Test
    void build_patternsCombinedWithLayout_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).layout(nonEmptyLayout()).patterns(nonEmptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("patterns"));
    }

    @Test
    void build_patternsCombinedWithScroll_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).scroll().patterns(nonEmptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("patterns"));
    }

    // --- rule 12 clause 3: empty geometry inputs ---

    @Test
    void build_emptyPatternsArray_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).patterns();

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("at least one pattern"));
    }

    @Test
    void build_patternWithEmptyLayout_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).patterns(nonEmptyLayout(), emptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("at least one slot"));
    }

    @Test
    void build_emptyExplicitLayout_throws() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).layout(emptyLayout());

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("at least one slot"));
    }

    // --- rule 12 clause 4: async-only options on a non-async source ---

    @Test
    void build_asyncOnlyOptions_onEagerSource_throwNamingTheOption() {
        // the option functions are never evaluated at build time, so returning null is safe
        assertAsyncOnlyRejected(builder -> builder.loadingItem(context -> null), "loadingItem");
        assertAsyncOnlyRejected(builder -> builder.onError((request, error) -> {
        }), "onError");
        assertAsyncOnlyRejected(builder -> builder.requestTimeout(Duration.ofSeconds(1)), "requestTimeout");
        assertAsyncOnlyRejected(builder -> builder.cacheTtl(Duration.ofSeconds(1)), "cacheTtl");
        assertAsyncOnlyRejected(builder -> builder.cacheMaxPages(4), "cacheMaxPages");
    }

    private static void assertAsyncOnlyRejected(Consumer<PaginationBuilder<String>> option, String optionName) {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer());
        option.accept(builder);

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains(optionName),
                "expected a message naming " + optionName + ", got: " + thrown.getMessage());
    }

    // --- rule 12 clause 5: cacheMaxPages requires cacheTtl ---

    @Test
    void build_cacheMaxPagesWithoutCacheTtl_onAsyncSource_throws() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());
        builder.itemRenderer(renderer()).cacheMaxPages(16);

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("cacheTtl"));
    }

    @Test
    void build_cacheMaxPagesWithCacheTtl_onAsyncSource_succeeds() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());
        Pagination<String> token = builder.itemRenderer(renderer())
                .cacheTtl(Duration.ofSeconds(30)).cacheMaxPages(16).build();

        assertEquals(16, ((PaginationImpl<String>) token).spec().cacheMaxPages());
    }

    // --- rule 12 setter-time value errors ---

    @Test
    void requestTimeout_nonPositive_throwsAtSetterTime() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());

        assertThrows(IllegalArgumentException.class, () -> builder.requestTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.requestTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void cacheTtl_nonPositive_throwsAtSetterTime() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());

        assertThrows(IllegalArgumentException.class, () -> builder.cacheTtl(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> builder.cacheTtl(Duration.ofMillis(-5)));
    }

    @Test
    void cacheMaxPages_belowOne_throwsAtSetterTime() {
        PaginationBuilderImpl<String> builder = asyncBuilder(new TestView());

        assertThrows(IllegalArgumentException.class, () -> builder.cacheMaxPages(0));
        assertThrows(IllegalArgumentException.class, () -> builder.cacheMaxPages(-7));
    }

    @Test
    void nullArguments_throwNullPointerExceptionAtSetterTime() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());

        assertThrows(NullPointerException.class, () -> builder.layout(null));
        assertThrows(NullPointerException.class, () -> builder.patterns((Layout[]) null));
        assertThrows(NullPointerException.class, () -> builder.patterns(nonEmptyLayout(), null));
        assertThrows(NullPointerException.class, () -> builder.itemRenderer(null));
        assertThrows(NullPointerException.class, () -> builder.fallbackItem(null));
        assertThrows(NullPointerException.class, () -> builder.loadingItem(null));
        assertThrows(NullPointerException.class, () -> builder.onError(null));
        assertThrows(NullPointerException.class, () -> builder.requestTimeout(null));
        assertThrows(NullPointerException.class, () -> builder.cacheTtl(null));
    }

    // --- defaults and geometry/target resolution ---

    @Test
    void defaults_normalGeometryLayoutCharOAndCacheMaxPages128_reachTheSpec() {
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .itemRenderer(renderer()).build();
        PaginationSpec<String> spec = token.spec();

        assertEquals(PaginationSpec.Geometry.NORMAL, spec.geometry());
        assertEquals(PaginationSpec.Target.LAYOUT_CHAR, spec.target());
        assertEquals('O', spec.layoutChar());
        assertNull(spec.explicitLayout());
        assertTrue(spec.patterns().isEmpty());
        assertEquals(128, spec.cacheMaxPages());
    }

    @Test
    void layout_silentlyOverridesLayoutChar() {
        Layout explicit = nonEmptyLayout();
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .layoutChar('X').layout(explicit).itemRenderer(renderer()).build();

        assertEquals(PaginationSpec.Target.EXPLICIT_LAYOUT, token.spec().target());
        assertSame(explicit, token.spec().explicitLayout());
    }

    @Test
    void scroll_resolvesScrollGeometryOnLayoutCharTarget() {
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .scroll().itemRenderer(renderer()).build();

        assertEquals(PaginationSpec.Geometry.SCROLL, token.spec().geometry());
        assertEquals(PaginationSpec.Target.LAYOUT_CHAR, token.spec().target());
    }

    @Test
    void patterns_resolvePatternGeometryAndPatternsTarget() {
        Layout first = nonEmptyLayout();
        Layout second = Layout.ofSlots(9, 10);
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .patterns(first, second).itemRenderer(renderer()).build();

        assertEquals(PaginationSpec.Geometry.PATTERN, token.spec().geometry());
        assertEquals(PaginationSpec.Target.PATTERNS, token.spec().target());
        assertEquals(Arrays.asList(first, second), token.spec().patterns());
    }

    // --- registration timing ---

    @Test
    void build_registersTheTokenInTheTable_constructionDoesNot() {
        TestView view = new TestView();
        PaginationBuilderImpl<String> builder = eagerBuilder(view);
        builder.itemRenderer(renderer());
        assertEquals(0, view.tokenTable().size());

        Pagination<String> token = builder.build();

        assertEquals(1, view.tokenTable().size());
        assertSame(token, view.tokenTable().tokens().get(0));
        assertEquals(0, ((IdentifiableToken) token).tokenId());
    }

    @Test
    void secondBuild_throwsIllegalStateException() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).build();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, builder::build);
        assertEquals("build() may only be called once per paginate* call", thrown.getMessage());
    }

    @Test
    void buildAfterFreeze_throwsIllegalStateExceptionFromTokenTable() {
        TestView view = new TestView();
        PaginationBuilderImpl<String> builder = eagerBuilder(view);
        builder.itemRenderer(renderer());

        view.tokenTable().freeze();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(thrown.getMessage().contains("frozen"));
        assertEquals(0, view.tokenTable().size());
    }
}
```

- [ ] **Step 2: Write the failing token test**

Full file `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationImplTest.java`. Uses the `sessionFor` fixture pattern from `ContextPhaseValidityTest` (direct `RegisteredView` + `StateStore` wiring) with real `PlainViewContextImpl` contexts, and the background-thread pattern from `StateImplTest.MainThreadGuard` for the off-main mutator checks. MockBukkit is mocked per test so `ThreadUtils.assertMainThread` is live; nothing here schedules tasks, but `cancelTasks(plugin)` runs in tearDown anyway because `MockBukkit.unmock()` drains pending scheduler tasks.

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PaginationImplTest {

    private static final List<String> ELEMENTS = Arrays.asList("a", "b", "c", "d", "e");
    private static final AtomicReference<List<String>> LAZY_ELEMENTS =
            new AtomicReference<>(Collections.emptyList());

    /** declares one eager pagination token; the renderer is never evaluated by these tests. */
    static final class PagedView extends View {
        final PaginationImpl<String> pagination = (PaginationImpl<String>) new PaginationBuilderImpl<>(
                this, tokenTable(), PaginationSourceSpec.eager(ELEMENTS))
                .itemRenderer((context, item, index, value) -> {
                })
                .build();
    }

    /** declares one eager-lazy pagination token reading {@link #LAZY_ELEMENTS} per context. */
    static final class LazyPagedView extends View {
        final PaginationImpl<String> pagination = (PaginationImpl<String>) new PaginationBuilderImpl<>(
                this, tokenTable(), PaginationSourceSpec.lazy(context -> LAZY_ELEMENTS.get()))
                .itemRenderer((context, item, index, value) -> {
                })
                .build();
    }

    static final class ForeignView extends View {
    }

    private ServerMock server;
    private Plugin plugin;
    private ViewEngine engine;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Plugin.class);
        engine = new ViewEngine(plugin, new ViewRegistry(), new SessionRegistry(),
                new SlotPainter(new NoopPlaceholderApplier()), mock(TitleUpdater.class));
        player = server.addPlayer("paginator");
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; cancel anything a test queued first
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    // three 'O' slots (0, 1, 2): five eager elements paginate into two pages of limit 3
    private static ViewConfig pagedConfig() {
        return new ViewConfigBuilder().title("Paged").layout("OOO      ", "         ").build();
    }

    // the sessionFor fixture pattern from ContextPhaseValidityTest: direct RegisteredView + StateStore wiring
    private ViewSession sessionFor(View view, ViewConfig config) {
        RegisteredView registered = new RegisteredView(view.getClass(), view, config);
        ViewSession session = new ViewSession(player, registered, ViewArguments.empty(),
                new StateStore(view.tokenTable().size()));
        session.effectiveConfig(config);
        session.status(ViewSession.Status.OPENING);
        return session;
    }

    // hand-places an UNINITIALIZED binding into the token's own state store slot (what OpenPhase does)
    private PaginationBinding placeBinding(PaginationImpl<?> token, ViewSession session) {
        PaginationBinding binding = new PaginationBinding(token.spec(), token.tokenId(), session, engine);
        session.stateStore().set(token.id(), binding);
        return binding;
    }

    // places the binding and runs PaginationInitPhase's per-binding step by hand
    private PaginationBinding initializeBinding(PaginationImpl<?> token, ViewSession session) {
        PaginationBinding binding = placeBinding(token, session);
        ResolvedLayout layout = ResolvedLayout.resolve(session.effectiveConfig());
        session.layout(layout);
        binding.initialize(layout, session.effectiveConfig());
        session.status(ViewSession.Status.ACTIVE);
        return binding;
    }

    // --- guard: foreign / closed / missing binding ---

    @Test
    void foreignViewsContext_throwsStaleContextException_withExactMessage() {
        PagedView view = new PagedView();
        ForeignView foreign = new ForeignView();
        ViewSession foreignSession = sessionFor(foreign, pagedConfig());
        PlainViewContextImpl foreignContext = new PlainViewContextImpl(foreignSession, engine);

        StaleContextException thrown = assertThrows(StaleContextException.class,
                () -> view.pagination.currentPage(foreignContext));

        assertEquals("state token of " + PagedView.class.getName()
                + " used with a context of " + ForeignView.class.getName(), thrown.getMessage());
    }

    @Test
    void closedContext_throwsStaleContextException_withExactMessage() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        session.status(ViewSession.Status.CLOSED);

        StaleContextException thrown = assertThrows(StaleContextException.class,
                () -> view.pagination.totalPages(context));

        assertEquals("context of " + PagedView.class.getName() + " is closed", thrown.getMessage());
    }

    @Test
    void missingBinding_throwsIllegalStateException() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> view.pagination.currentPage(context));

        assertEquals("pagination binding missing for " + PagedView.class.getName(), thrown.getMessage());
    }

    // --- threading: mutators assert the main thread ---

    @Test
    void mutators_offMainThread_throwIllegalStateException() throws Exception {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        placeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        // the test thread created the mock server, so it is the primary thread
        assertDoesNotThrow(() -> view.pagination.switchTo(context, 2));

        assertThrowsOffMain(() -> view.pagination.advance(context));
        assertThrowsOffMain(() -> view.pagination.back(context));
        assertThrowsOffMain(() -> view.pagination.switchTo(context, 3));
        assertThrowsOffMain(() -> view.pagination.refresh(context));
    }

    // background-thread pattern mirrored from StateImplTest.MainThreadGuard
    private static void assertThrowsOffMain(Runnable call) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            executor.submit(() -> {
                try {
                    call.run();
                } catch (Throwable t) {
                    thrown.set(t);
                }
            }).get(5, TimeUnit.SECONDS);
            assertTrue(thrown.get() instanceof IllegalStateException,
                    "off-main mutator must throw IllegalStateException, got " + thrown.get());
        } finally {
            executor.shutdownNow();
        }
    }

    // --- pre-init delegation table (pinned rule 6, left column) ---

    @Test
    void preInit_readsAndMutationsFollowTheDelegationTable() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        PaginationBinding binding = placeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertFalse(binding.isInitialized());
        assertFalse(binding.hasPendingNavigation());
        assertEquals(1, view.pagination.currentPage(context));     // pendingTarget starts at 1
        assertEquals(1, view.pagination.totalPages(context));
        assertEquals(0, view.pagination.totalElements(context));
        assertFalse(view.pagination.canAdvance(context));
        assertFalse(view.pagination.canBack(context));             // pendingTarget() > 1 is false
        assertFalse(view.pagination.isLoading(context));
        assertNull(view.pagination.lastError(context));

        view.pagination.advance(context);                          // records pendingTarget + 1
        assertEquals(2, view.pagination.currentPage(context));
        assertTrue(view.pagination.canBack(context));
        assertTrue(binding.hasPendingNavigation());

        view.pagination.back(context);                             // records pendingTarget - 1
        assertEquals(1, view.pagination.currentPage(context));

        view.pagination.back(context);                             // recordSwitchTo stores max(1, target)
        assertEquals(1, view.pagination.currentPage(context));

        view.pagination.switchTo(context, 7);
        assertEquals(7, view.pagination.currentPage(context));

        view.pagination.switchTo(context, -3);                     // clamped up to 1
        assertEquals(1, view.pagination.currentPage(context));

        view.pagination.refresh(context);                          // pre-init refresh is a no-op
        assertFalse(binding.isInitialized());
        assertEquals(1, view.pagination.currentPage(context));
    }

    // --- post-init delegation (pinned rule 6, right column) ---

    @Test
    void postInit_delegatesToTheBoundPaginator() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        PaginationBinding binding = initializeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertTrue(binding.isInitialized());
        // three 'O' slots, five elements: pages are [a b c] and [d e]
        assertEquals(1, view.pagination.currentPage(context));
        assertEquals(2, view.pagination.totalPages(context));
        assertEquals(5, view.pagination.totalElements(context));
        assertTrue(view.pagination.canAdvance(context));
        assertFalse(view.pagination.canBack(context));
        assertFalse(view.pagination.isLoading(context));           // always false for eager sources
        assertNull(view.pagination.lastError(context));

        view.pagination.advance(context);
        assertEquals(2, view.pagination.currentPage(context));
        assertFalse(view.pagination.canAdvance(context));
        assertTrue(view.pagination.canBack(context));

        view.pagination.switchTo(context, 99);                     // upper-clamped exactly as 2.x changePage
        assertEquals(2, view.pagination.currentPage(context));

        view.pagination.switchTo(context, -4);                     // lower-clamped to 1
        assertEquals(1, view.pagination.currentPage(context));

        view.pagination.back(context);                             // already at the first page
        assertEquals(1, view.pagination.currentPage(context));
    }

    @Test
    void refresh_postInit_eagerStaticSource_routesToPaginatorRefresh() {
        PagedView view = new PagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        initializeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        view.pagination.advance(context);
        view.pagination.refresh(context);                          // re-requests the current page (forced)

        assertEquals(2, view.pagination.currentPage(context));
        assertEquals(5, view.pagination.totalElements(context));
        assertNull(view.pagination.lastError(context));
    }

    @Test
    void refresh_postInit_eagerLazySource_reinvokesTheSourceFunction() {
        LAZY_ELEMENTS.set(Arrays.asList("a", "b"));
        LazyPagedView view = new LazyPagedView();
        ViewSession session = sessionFor(view, pagedConfig());
        initializeBinding(view.pagination, session);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertEquals(2, view.pagination.totalElements(context));
        assertEquals(1, view.pagination.totalPages(context));

        LAZY_ELEMENTS.set(Arrays.asList("a", "b", "c", "d"));
        view.pagination.refresh(context);                          // refreshLazy replaces the source

        assertEquals(4, view.pagination.totalElements(context));
        assertEquals(2, view.pagination.totalPages(context));
    }
}
```

- [ ] **Step 3: Run both tests to verify they fail**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationBuilderImplTest,PaginationImplTest"
```

Expected: FAIL — test compilation error: `cannot find symbol: class PaginationBuilderImpl` and `cannot find symbol: class PaginationImpl` in package `tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination` (the spec/binding types from Tasks 6–7 exist; the two classes under test do not).

- [ ] **Step 4: Part A — widen ContextStateAccess and consolidate the guard**

Existing pins on this guard (grep `src/test` for the fragments `used with a context of` and `is closed`): **no test pins the literal message strings today** — the `StaleContextException` behavior is pinned at type level by `StateImplTest` (`foreignOwnersContext_throwsStaleContextException`, `closedContext_throwsStaleContextException`) and `ContextPhaseValidityTest` (`stateAccess_afterClosed_throwsStaleContextException`). All must stay green after this refactor; the exact strings become pinned by `PaginationImplTest` from this task onward.

Replace `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/ContextStateAccess.java` with (license header lines 1–22 unchanged; class doc, visibility and the new method change; `storeOf`/`ownerOf`/`isActive`/`backed` stay package-private — only the consolidated guard needs to be visible outside this package):

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;

/**
 * Resolver from a public {@link ViewContext} to its state backing; token impls never cast
 * to concrete context classes, only to {@link StateBackedContext}. Public so token
 * implementations outside this package (the pagination token) can run the shared
 * owner/liveness guard via {@link #storeFor(ViewContext, View)}.
 */
@ApiStatus.Internal
public final class ContextStateAccess {

    private ContextStateAccess() {
    }

    /**
     * Resolves the state store of a context after validating that the context belongs to
     * the given owning view and is still open — the single guard shared by every
     * per-context token implementation.
     *
     * @param context the context a token was invoked with
     * @param owner   the view declaring the token
     * @return the backing state store
     * @throws StaleContextException when the context belongs to another view, is already
     *                               closed, or carries no state backing
     */
    public static @NotNull StateStore storeFor(@NotNull ViewContext context, @NotNull View owner) {
        View contextOwner = ownerOf(context);
        if (contextOwner != owner) {
            throw new StaleContextException("state token of " + owner.getClass().getName()
                    + " used with a context of " + contextOwner.getClass().getName());
        }
        if (!isActive(context)) {
            throw new StaleContextException("context of " + owner.getClass().getName() + " is closed");
        }
        return storeOf(context);
    }

    /**
     * Resolves the state store of a context.
     *
     * @param context the context to resolve
     * @return the backing state store
     * @throws StaleContextException when the context carries no state backing
     */
    static @NotNull StateStore storeOf(@NotNull ViewContext context) {
        return backed(context).stateStore();
    }

    /**
     * Resolves the view owning a context.
     *
     * @param context the context to resolve
     * @return the owning view
     * @throws StaleContextException when the context carries no state backing
     */
    static @NotNull View ownerOf(@NotNull ViewContext context) {
        return backed(context).owner();
    }

    /**
     * Resolves whether a context may still access state.
     *
     * @param context the context to resolve
     * @return {@code true} while state access is legal
     * @throws StaleContextException when the context carries no state backing
     */
    static boolean isActive(@NotNull ViewContext context) {
        return backed(context).contextActive();
    }

    private static StateBackedContext backed(ViewContext context) {
        if (!(context instanceof StateBackedContext)) {
            throw new StaleContextException("context " + context.getClass().getName()
                    + " does not expose state storage");
        }
        return (StateBackedContext) context;
    }
}
```

Then refactor the three token impls onto the consolidated guard. The private one-line delegate is kept (instead of inlining) because `get`/`set`/`update` call `storeFor(context)` at four call sites across the three files — delegating keeps those untouched, the smaller diff. In **each** of `MutableStateImpl.java`, `LazyStateImpl.java` and `InitialStateImpl.java`, replace the identical block:

```java
    private StateStore storeFor(ViewContext context) {
        View contextOwner = ContextStateAccess.ownerOf(context);
        if (contextOwner != owner) {
            throw new StaleContextException("state token of " + owner.getClass().getName()
                    + " used with a context of " + contextOwner.getClass().getName());
        }
        if (!ContextStateAccess.isActive(context)) {
            throw new StaleContextException("context of " + owner.getClass().getName() + " is closed");
        }
        return ContextStateAccess.storeOf(context);
    }
```

with:

```java
    private StateStore storeFor(ViewContext context) {
        return ContextStateAccess.storeFor(context, owner);
    }
```

In each of the three files the import `tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException` is now unused — remove it (it was referenced only by the deleted guard bodies; the class Javadoc of none of the three names it in a `@link` — verify with a quick search before removing, and keep it if a Javadoc reference exists).

- [ ] **Step 5: Part B — write PaginationBuilderImpl**

Full file `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImpl.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PaginationErrorCallback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@link PaginationBuilder} implementation behind {@code View.paginate*}: collects the
 * declaration fluently (value errors rejected at setter time), validates the combination
 * at {@link #build()}, resolves geometry and target, and constructs the registered
 * {@link PaginationImpl} token. One builder produces at most one token.
 *
 * @param <T> the element type
 */
@ApiStatus.Internal
public final class PaginationBuilderImpl<T> implements PaginationBuilder<T> {

    private static final char DEFAULT_LAYOUT_CHAR = 'O';
    private static final int DEFAULT_CACHE_MAX_PAGES = 128;

    private final View owner;
    private final TokenTable table;
    private final PaginationSourceSpec<T> source;

    private char layoutChar = DEFAULT_LAYOUT_CHAR;
    private boolean layoutCharCalled;
    private Layout explicitLayout;
    private boolean scroll;
    // null until patterns(...) was called; a non-null empty list records the explicit empty call
    private List<Layout> patterns;
    private PaginationItemRenderer<T> renderer;
    private Function<ViewContext, ItemStack> fallbackItem;
    private Function<ViewContext, ItemStack> loadingItem;
    private PaginationErrorCallback errorCallback;
    private Duration requestTimeout;
    private Duration cacheTtl;
    private int cacheMaxPages = DEFAULT_CACHE_MAX_PAGES;
    private boolean cacheMaxPagesCalled;
    private boolean built;

    /**
     * Creates a builder for one pagination declaration of a view.
     *
     * @param owner  the view declaring the pagination
     * @param table  the owner's token table; {@link #build()} registers into it
     * @param source the source declaration produced by the {@code View.paginate*} factory
     */
    public PaginationBuilderImpl(@NotNull View owner, @NotNull TokenTable table,
                                 @NotNull PaginationSourceSpec<T> source) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.table = Objects.requireNonNull(table, "table");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public @NotNull PaginationBuilder<T> layoutChar(char character) {
        this.layoutChar = character;
        this.layoutCharCalled = true;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> layout(@NotNull Layout layout) {
        this.explicitLayout = Objects.requireNonNull(layout, "layout");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> scroll() {
        this.scroll = true;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> patterns(@NotNull Layout... patterns) {
        Objects.requireNonNull(patterns, "patterns");
        List<Layout> copied = new ArrayList<>(patterns.length);
        for (Layout pattern : patterns) {
            copied.add(Objects.requireNonNull(pattern, "patterns must not contain null"));
        }
        this.patterns = copied;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> itemRenderer(@NotNull PaginationItemRenderer<T> renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> fallbackItem(@NotNull Function<ViewContext, ItemStack> item) {
        this.fallbackItem = Objects.requireNonNull(item, "item");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item) {
        this.loadingItem = Objects.requireNonNull(item, "item");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> onError(@NotNull PaginationErrorCallback callback) {
        this.errorCallback = Objects.requireNonNull(callback, "callback");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> requestTimeout(@NotNull Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        this.requestTimeout = timeout;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> cacheTtl(@NotNull Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("cacheTtl must be positive");
        }
        this.cacheTtl = ttl;
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> cacheMaxPages(int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("cacheMaxPages must be at least 1");
        }
        this.cacheMaxPages = maxPages;
        this.cacheMaxPagesCalled = true;
        return this;
    }

    @Override
    public @NotNull Pagination<T> build() {
        if (built) {
            throw new IllegalStateException("build() may only be called once per paginate* call");
        }
        validate();

        PaginationSpec.Geometry geometry;
        PaginationSpec.Target target;
        if (patterns != null) {
            geometry = PaginationSpec.Geometry.PATTERN;
            target = PaginationSpec.Target.PATTERNS;
        } else {
            geometry = scroll ? PaginationSpec.Geometry.SCROLL : PaginationSpec.Geometry.NORMAL;
            target = explicitLayout != null
                    ? PaginationSpec.Target.EXPLICIT_LAYOUT
                    : PaginationSpec.Target.LAYOUT_CHAR;
        }

        PaginationSpec<T> spec = new PaginationSpec<>(geometry, target, layoutChar, explicitLayout,
                patterns == null ? Collections.<Layout>emptyList() : patterns, renderer, fallbackItem,
                loadingItem, source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages);
        // registration happens here, AT BUILD TIME: a frozen table throws before built is set
        PaginationImpl<T> token = new PaginationImpl<>(owner, table, spec);
        built = true;
        return token;
    }

    // build-time validation in the pinned order; value errors were already rejected at setter time
    private void validate() {
        if (renderer == null) {
            throw new ViewConfigurationException("pagination of view " + owner.getClass().getName()
                    + " declares no itemRenderer(...)");
        }
        if (patterns != null && (layoutCharCalled || explicitLayout != null || scroll)) {
            throw new ViewConfigurationException(
                    "patterns(...) cannot be combined with layoutChar(...), layout(...) or scroll()");
        }
        if (patterns != null && patterns.isEmpty()) {
            throw new ViewConfigurationException("patterns(...) requires at least one pattern layout");
        }
        if (patterns != null) {
            for (Layout pattern : patterns) {
                if (pattern.slots().isEmpty()) {
                    throw new ViewConfigurationException(
                            "every pattern layout must contain at least one slot");
                }
            }
        }
        if (explicitLayout != null && explicitLayout.slots().isEmpty()) {
            throw new ViewConfigurationException("layout(...) must contain at least one slot");
        }
        if (!source.isAsync()) {
            String asyncOnly = firstAsyncOnlyOption();
            if (asyncOnly != null) {
                throw new ViewConfigurationException(asyncOnly
                        + " is only legal on an async pagination source");
            }
        }
        if (cacheMaxPagesCalled && cacheTtl == null) {
            throw new ViewConfigurationException("cacheMaxPages(...) requires cacheTtl(...)");
        }
    }

    // the first async-only option used on this builder, or null when none was called
    private @Nullable String firstAsyncOnlyOption() {
        if (loadingItem != null) {
            return "loadingItem(...)";
        }
        if (errorCallback != null) {
            return "onError(...)";
        }
        if (requestTimeout != null) {
            return "requestTimeout(...)";
        }
        if (cacheTtl != null) {
            return "cacheTtl(...)";
        }
        if (cacheMaxPagesCalled) {
            return "cacheMaxPages(...)";
        }
        return null;
    }
}
```

- [ ] **Step 6: Part C — write PaginationImpl**

Full file `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationImpl.java`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.engine.Paginator;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.ContextStateAccess;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.util.ThreadUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;

import java.util.Objects;

/**
 * The {@link Pagination} token implementation: a per-view singleton declared at view
 * construction time whose per-context runtime lives in a {@link PaginationBinding} stored
 * in this token's own {@code StateStore} slot. Every method resolves the binding through
 * the shared {@link ContextStateAccess#storeFor(ViewContext, View)} guard, then delegates
 * to the pending-navigation recorder before initialization and to the bound
 * {@link Paginator} afterwards; mutators additionally assert the main thread.
 *
 * @param <T> the element type
 */
@ApiStatus.Internal
public final class PaginationImpl<T> implements Pagination<T>, IdentifiableToken {

    private final View owner;
    private final PaginationSpec<T> spec;
    private final int id;

    /**
     * Creates and registers the token; called by {@link PaginationBuilderImpl#build()}, so
     * registration happens at build time.
     *
     * @param owner the view declaring the token
     * @param table the owner's token table
     * @param spec  the immutable pagination declaration
     * @throws IllegalStateException when the table is already frozen
     */
    public PaginationImpl(@NotNull View owner, @NotNull TokenTable table, @NotNull PaginationSpec<T> spec) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.id = Objects.requireNonNull(table, "table").register(this);
    }

    /**
     * Returns the table-assigned token id, the index into the per-session state store.
     *
     * @return the token id
     */
    public int id() {
        return id;
    }

    @Override
    public int tokenId() {
        return id;
    }

    /**
     * Returns the immutable declaration this token was built from.
     *
     * @return the pagination spec
     */
    public @NotNull PaginationSpec<T> spec() {
        return spec;
    }

    @Override
    public int currentPage(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return binding.pendingTarget();
        }
        return paginatorOf(binding).getCurrentPage();
    }

    @Override
    public int totalPages(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return 1;
        }
        return paginatorOf(binding).getTotalPages();
    }

    @Override
    public int totalElements(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return 0;
        }
        return paginatorOf(binding).getTotalElements();
    }

    @Override
    public boolean canAdvance(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return false;
        }
        return paginatorOf(binding).hasNextPage();
    }

    @Override
    public boolean canBack(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return binding.pendingTarget() > 1;
        }
        return paginatorOf(binding).hasPreviousPage();
    }

    @Override
    public void advance(@NotNull ViewContext context) {
        ThreadUtils.assertMainThread("Pagination.advance");
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            binding.recordSwitchTo(binding.pendingTarget() + 1);
            return;
        }
        paginatorOf(binding).nextPage();
    }

    @Override
    public void back(@NotNull ViewContext context) {
        ThreadUtils.assertMainThread("Pagination.back");
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            binding.recordSwitchTo(binding.pendingTarget() - 1);
            return;
        }
        paginatorOf(binding).previousPage();
    }

    @Override
    public void switchTo(@NotNull ViewContext context, int page) {
        ThreadUtils.assertMainThread("Pagination.switchTo");
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            binding.recordSwitchTo(page);
            return;
        }
        paginatorOf(binding).changePage(page);
    }

    @Override
    public boolean isLoading(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return false;
        }
        return paginatorOf(binding).isLoading();
    }

    @Override
    public @Nullable Throwable lastError(@NotNull ViewContext context) {
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            return null;
        }
        return paginatorOf(binding).lastError();
    }

    @Override
    public void refresh(@NotNull ViewContext context) {
        ThreadUtils.assertMainThread("Pagination.refresh");
        PaginationBinding binding = bindingFor(context);
        if (!binding.isInitialized()) {
            // pre-init refresh is a no-op: there is no source to re-request yet
            return;
        }
        if (spec.source().kind() == PaginationSourceSpec.Kind.EAGER_LAZY) {
            binding.refreshLazy(context);
            return;
        }
        paginatorOf(binding).refresh();
    }

    // resolves the per-context binding through the shared owner/liveness guard
    private @NotNull PaginationBinding bindingFor(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        StateStore store = ContextStateAccess.storeFor(context, owner);
        Object raw = store.get(id);
        if (raw == null) {
            throw new IllegalStateException("pagination binding missing for " + owner.getClass().getName());
        }
        return (PaginationBinding) raw;
    }

    // post-initialization accessor; PaginationBinding.initialize guarantees the paginator exists
    private @NotNull Paginator<?> paginatorOf(@NotNull PaginationBinding binding) {
        return Objects.requireNonNull(binding.paginator(), "paginator");
    }
}
```

- [ ] **Step 7: Run the new tests to verify they pass**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationBuilderImplTest,PaginationImplTest"
```

Expected: PASS.

- [ ] **Step 8: Run the state regression tests (refactor safety net)**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=StateImplTest,ContextPhaseValidityTest,ViewTest"
```

Expected: PASS — the consolidated guard must not change any observable behavior of the three state token impls.

- [ ] **Step 9: Full module check**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS, zero failures.

- [ ] **Step 10: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/ContextStateAccess.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/MutableStateImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/LazyStateImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/InitialStateImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationImpl.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImplTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationImplTest.java
git commit -m "feat(inventory-api): add pagination builder validation and the Pagination token" -m "PaginationBuilderImpl enforces the build-time validation matrix (renderer required, patterns conflicts, empty layouts, async-only options, cacheMaxPages-requires-cacheTtl) with setter-time value checks, resolves geometry/target and registers the token at build time. PaginationImpl delegates every call to the per-context PaginationBinding per the pre/post-init table; mutators assert the main thread.

refactor: consolidate the storeFor guard triplicated across MutableStateImpl/LazyStateImpl/InitialStateImpl into public ContextStateAccess.storeFor (reviewer backlog #6); both StaleContextException messages preserved verbatim and now pinned by PaginationImplTest."
```

---

### Task 9: View.paginate* factories

Adds the four `protected final` pagination declaration factories to `View` (spec §5.2). Each factory only wraps the matching `PaginationSourceSpec` kind into a fresh `PaginationBuilderImpl` — **factory calls never register anything**; the builder's `build()` (Task 8) registers the token, so the existing freeze semantics apply automatically: factories are legal anywhere, but a `build()` after `ViewRegistry.register` froze the token table throws `IllegalStateException` from `TokenTable.register`. Depends on Tasks 6 (`PaginationSourceSpec`) and 8 (`PaginationBuilderImpl`/`PaginationImpl`).

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/View.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/ViewTest.java

- [ ] **Step 1: Write the failing tests**

Append to `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/ViewTest.java`. `ViewTest` lives in the same package as `View`, so the `protected final` factories are callable directly on the existing `BlankView` fixture — same approach as the existing `stateFactories_*` tests.

Add these imports to the existing import block (keep alphabetical grouping: `tech.*` imports with the other `tech.*` ones, `java.*` after them, statics last):

```java
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationSourceSpec;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationItemRenderer;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
```

Append these members inside `class ViewTest`, after the existing test methods:

```java
    private static <T> PaginationItemRenderer<T> noopRenderer() {
        return (context, item, index, value) -> {
        };
    }

    @Test
    void paginateFactories_buildersRegisterExactlyOneTokenEach() {
        BlankView view = new BlankView();

        PaginationBuilder<String> eager = view.paginate(Arrays.asList("a", "b"));
        PaginationBuilder<String> lazy = view.paginate(context -> Arrays.asList("a"));
        PaginationBuilder<String> async = view.paginateAsync(request ->
                CompletableFuture.completedFuture(PageResult.of(Collections.<String>emptyList(), 0)));
        PaginationBuilder<String> custom = view.paginateSource(context ->
                new EagerPageSource<>(Arrays.asList("x")));

        // factory calls only return builders; build() is what registers
        assertEquals(0, view.tokenTable().size());

        Pagination<String> eagerToken = eager.itemRenderer(noopRenderer()).build();
        assertEquals(1, view.tokenTable().size());
        Pagination<String> lazyToken = lazy.itemRenderer(noopRenderer()).build();
        assertEquals(2, view.tokenTable().size());
        Pagination<String> asyncToken = async.itemRenderer(noopRenderer()).build();
        assertEquals(3, view.tokenTable().size());
        Pagination<String> customToken = custom.itemRenderer(noopRenderer()).build();
        assertEquals(4, view.tokenTable().size());

        List<StateToken> tokens = view.tokenTable().tokens();
        assertSame(eagerToken, tokens.get(0));
        assertSame(lazyToken, tokens.get(1));
        assertSame(asyncToken, tokens.get(2));
        assertSame(customToken, tokens.get(3));
        for (int i = 0; i < tokens.size(); i++) {
            assertTrue(tokens.get(i) instanceof Pagination,
                    "token " + i + " must implement Pagination");
            assertTrue(tokens.get(i) instanceof IdentifiableToken,
                    "token " + i + " must implement IdentifiableToken");
            assertEquals(i, ((IdentifiableToken) tokens.get(i)).tokenId());
        }
    }

    @Test
    void paginateFactories_mapToTheMatchingSourceKind() {
        BlankView view = new BlankView();

        assertEquals(PaginationSourceSpec.Kind.EAGER_STATIC,
                kindOf(view.paginate(Arrays.asList("a"))));
        assertEquals(PaginationSourceSpec.Kind.EAGER_LAZY,
                kindOf(view.paginate(context -> Arrays.asList("a"))));
        assertEquals(PaginationSourceSpec.Kind.ASYNC,
                kindOf(view.paginateAsync(request ->
                        CompletableFuture.completedFuture(PageResult.of(Collections.<String>emptyList(), 0)))));
        assertEquals(PaginationSourceSpec.Kind.CUSTOM,
                kindOf(view.paginateSource(context -> new EagerPageSource<>(Arrays.asList("x")))));
    }

    private static <T> PaginationSourceSpec.Kind kindOf(PaginationBuilder<T> builder) {
        PaginationImpl<T> token = (PaginationImpl<T>) builder.itemRenderer(noopRenderer()).build();
        return token.spec().source().kind();
    }

    @Test
    void paginateList_takesDefensiveCopy_andSharesOneEagerSourceAcrossContexts() {
        BlankView view = new BlankView();
        List<String> original = new ArrayList<>(Arrays.asList("a", "b"));

        PaginationBuilder<String> builder = view.paginate(original);
        original.add("mutated-after-the-factory-call");

        PaginationImpl<String> token = (PaginationImpl<String>) builder.itemRenderer(noopRenderer()).build();
        ViewContext context = mock(ViewContext.class);
        PageSource<String> first = token.spec().source().createSource(context, token.spec());
        PageSource<String> second = token.spec().source().createSource(context, token.spec());

        assertEquals(Arrays.asList("a", "b"), first.elements());
        // EAGER_STATIC serves the one shared immutable source to every context
        assertSame(first, second);
    }

    @Test
    void paginateBuilders_buildAfterFreeze_throwIllegalStateException() {
        BlankView view = new BlankView();
        PaginationBuilder<String> builder = view.paginate(Arrays.asList("a")).itemRenderer(noopRenderer());

        view.tokenTable().freeze();

        assertThrows(IllegalStateException.class, builder::build);
        assertEquals(0, view.tokenTable().size());
    }

    @Test
    void twoPaginateCalls_buildDistinctTokensWithSequentialIds() {
        BlankView view = new BlankView();

        Pagination<String> first = view.paginate(Arrays.asList("a")).itemRenderer(noopRenderer()).build();
        Pagination<Integer> second = view.paginate(Arrays.asList(1, 2)).itemRenderer(noopRenderer()).build();

        assertNotSame(first, second);
        assertEquals(0, ((IdentifiableToken) first).tokenId());
        assertEquals(1, ((IdentifiableToken) second).tokenId());
        assertEquals(2, view.tokenTable().size());
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=ViewTest"
```

Expected: FAIL — test compilation error: `cannot find symbol: method paginate(java.util.List<java.lang.String>)` (and the other three factory methods) on `View`/`BlankView`.

- [ ] **Step 3: Write the implementation**

Modify `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/View.java`.

Add to the import block (with the other `tech.*` imports; `java.util.List` goes next to the existing `java.util.function.Function`):

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBuilderImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationSourceSpec;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.PaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.List;
```

Insert the four factories after `sharedState(...)` and before the `tokenTable()` accessor:

```java
    /**
     * Declares paginated rendering over a fixed element list. The list is copied
     * defensively when this factory runs and becomes one immutable page source shared by
     * every context; later mutations of the original list are never observed. Use
     * {@link #paginate(Function)} when elements differ per viewer or must be refreshable.
     *
     * <p>Like the state factories, pagination declarations are legal only in field
     * initializers or the constructor: the returned builder's
     * {@link PaginationBuilder#build()} registers the token and throws
     * {@link IllegalStateException} once registration froze the token table.
     *
     * @param source the elements to paginate, copied defensively
     * @param <T>    the element type
     * @return the pagination declaration builder
     */
    protected final <T> PaginationBuilder<T> paginate(@NotNull List<T> source) {
        return new PaginationBuilderImpl<>(this, tokenTable, PaginationSourceSpec.eager(source));
    }

    /**
     * Declares paginated rendering over a per-context element list: the function runs
     * once per context at pagination initialization, on the main thread, and runs again
     * for that context only when {@code Pagination.refresh(context)} is called.
     *
     * <p>Like the state factories, pagination declarations are legal only in field
     * initializers or the constructor: the returned builder's
     * {@link PaginationBuilder#build()} registers the token and throws
     * {@link IllegalStateException} once registration froze the token table.
     *
     * @param source the per-context element list factory; must not return {@code null}
     * @param <T>    the element type
     * @return the pagination declaration builder
     */
    protected final <T> PaginationBuilder<T> paginate(@NotNull Function<ViewContext, List<T>> source) {
        return new PaginationBuilderImpl<>(this, tokenTable, PaginationSourceSpec.lazy(source));
    }

    /**
     * Declares paginated rendering over an asynchronously loaded source: every context
     * gets its own fresh {@code AsyncPageSource} at pagination initialization, so request
     * ids, page caches, loading state and errors are never shared between viewers. The
     * async-only builder options ({@code loadingItem}, {@code onError},
     * {@code requestTimeout}, {@code cacheTtl}, {@code cacheMaxPages}) are legal only on
     * the builder returned here.
     *
     * <p>Like the state factories, pagination declarations are legal only in field
     * initializers or the constructor: the returned builder's
     * {@link PaginationBuilder#build()} registers the token and throws
     * {@link IllegalStateException} once registration froze the token table.
     *
     * @param source the page loader invoked per page request
     * @param <T>    the element type
     * @return the pagination declaration builder
     */
    protected final <T> PaginationBuilder<T> paginateAsync(@NotNull AsyncPageSupplier<T> source) {
        return new PaginationBuilderImpl<>(this, tokenTable, PaginationSourceSpec.async(source));
    }

    /**
     * Escape hatch declaring paginated rendering over a custom {@link PageSource}: the
     * factory runs once per context at pagination initialization and must return the
     * source instance serving exactly that context.
     *
     * <p>Like the state factories, pagination declarations are legal only in field
     * initializers or the constructor: the returned builder's
     * {@link PaginationBuilder#build()} registers the token and throws
     * {@link IllegalStateException} once registration froze the token table.
     *
     * @param factory the per-context page source factory; must not return {@code null}
     * @param <T>     the element type
     * @return the pagination declaration builder
     */
    protected final <T> PaginationBuilder<T> paginateSource(@NotNull Function<ViewContext, PageSource<T>> factory) {
        return new PaginationBuilderImpl<>(this, tokenTable, PaginationSourceSpec.custom(factory));
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=ViewTest"
```

Expected: PASS (both the five new tests and the four pre-existing `ViewTest` tests).

- [ ] **Step 5: Full module check**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS, zero failures.

- [ ] **Step 6: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/View.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/ViewTest.java
git commit -m "feat(inventory-api): add View.paginate* declaration factories" -m "paginate(List)/paginate(Function)/paginateAsync/paginateSource return a PaginationBuilderImpl over the matching PaginationSourceSpec kind (eager/lazy/async/custom). Factory calls never register; build() registers the token, so the token-table freeze gives the same field-initializer/constructor-only semantics as the state factories. Tests pin the one-token-per-build delta, kind mapping, paginate(List) defensive copy with the shared eager source, build-after-freeze failure and sequential token ids."
```

---

### Task 10: Open wiring — OpenPhase bindings, PaginationBindings, PaginationInitPhase, OpenFailureHandler, ViewRegistry validation

The open pipeline gains spec §7 step 7: `OpenPhase` seeds one `PaginationBinding` per pagination token into the session's `StateStore` before `onOpen` (so pre-init navigation has a recording target), the new `PaginationInitPhase` initializes every binding between layout resolution and `onFirstRender`, a failing init aborts through the new `OpenFailureHandler` (the OPEN_FAILED abort path extracted verbatim from `FirstRenderPhase`), and `ViewRegistry` rejects LAYOUT_CHAR pagination targets that have no matching layout at registration time (§5.3, pinned rule 10).

Note: at this point in the plan, settles repaint token *watchers* only — pagination areas are not yet painted by `FirstRenderPhase`/`UpdatePhase` (that lands in Task 11). The tests below therefore observe init effects through token reads and watcher components, never through area slot contents.

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindings.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/OpenFailureHandler.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/PaginationInitPhase.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/OpenPhase.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistry.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationOpenWiringTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.PaginationInitPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationOpenWiringTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;
    private PlayerMock second;

    private SimpleView simpleView;
    private EagerOrderingView eagerOrderingView;
    private OnOpenNavigationView onOpenNavigationView;
    private IsolationView isolationView;
    private CacheIsolationView cacheIsolationView;
    private LazyInitFailView lazyInitFailView;
    private OutOfBoundsLayoutView outOfBoundsLayoutView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("first");
        second = server.addPlayer("second");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        simpleView = new SimpleView();
        eagerOrderingView = new EagerOrderingView();
        onOpenNavigationView = new OnOpenNavigationView();
        isolationView = new IsolationView();
        cacheIsolationView = new CacheIsolationView();
        lazyInitFailView = new LazyInitFailView();
        outOfBoundsLayoutView = new OutOfBoundsLayoutView();
        views.register(simpleView);
        views.register(eagerOrderingView);
        views.register(onOpenNavigationView);
        views.register(isolationView);
        views.register(cacheIsolationView);
        views.register(lazyInitFailView);
        views.register(outOfBoundsLayoutView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; cancel first so settle tasks queued by
        // tests that never ticked do not run against torn-down state
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    // ---------------------------------------------------------------- fixture views

    static final class SimpleView extends View {
        CloseReason lastCloseReason;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Simple").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(0, new ItemStack(Material.STONE));
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class EagerOrderingView extends View {
        final Pagination<String> pagination = this.<String>paginate(
                        Arrays.asList("a", "b", "c", "d", "e"))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, index + 1)))
                .build();

        Integer pageInFirstRender;
        Integer totalPagesInFirstRender;
        Integer totalElementsInFirstRender;
        Boolean loadingInFirstRender;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("EagerOrdering").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            // settled values prove init ran after layout resolution and before this
            // handler: pre-init reads would be 1 / 1 / 0 / false
            pageInFirstRender = pagination.currentPage(context);
            totalPagesInFirstRender = pagination.totalPages(context);
            totalElementsInFirstRender = pagination.totalElements(context);
            loadingInFirstRender = pagination.isLoading(context);
        }
    }

    static final class OnOpenNavigationView extends View {
        final List<PageRequest> requests = new CopyOnWriteArrayList<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            requests.add(request);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < request.getPageSize(); i++) {
                items.add("item-" + (request.getOffset() + i));
            }
            return CompletableFuture.completedFuture(PageResult.of(items, 100));
        })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("OnOpenNavigation").layout("OO       ");
        }

        @Override
        protected void onOpen(@NotNull OpenContext context) {
            // pre-init: records a pending target the init phase replays through the
            // preserved unbound-record branch of changePage
            pagination.switchTo(context, 3);
        }
    }

    static final class IsolationView extends View {
        final Map<UUID, CompletableFuture<PageResult<String>>> futures = new ConcurrentHashMap<>();
        final List<PageRequest> requests = new CopyOnWriteArrayList<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            requests.add(request);
            CompletableFuture<PageResult<String>> future = new CompletableFuture<>();
            futures.put(request.playerId(), future);
            return future;
        })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Isolation").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            // loading indicator outside the pagination area: repaints on the token's
            // settle pass because it watches the pagination token
            context.slot(8)
                    .item(ctx -> new ItemStack(
                            pagination.isLoading(ctx) ? Material.BARRIER : Material.EMERALD))
                    .updateOnStateChange(pagination);
        }
    }

    static final class CacheIsolationView extends View {
        final Map<UUID, List<PageRequest>> requestsByPlayer = new ConcurrentHashMap<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            requestsByPlayer
                    .computeIfAbsent(request.playerId(), id -> new CopyOnWriteArrayList<>())
                    .add(request);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < request.getPageSize(); i++) {
                items.add("item-" + (request.getOffset() + i));
            }
            return CompletableFuture.completedFuture(PageResult.of(items, 6));
        })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .cacheTtl(Duration.ofMinutes(5))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CacheIsolation").layout("OO       ");
        }
    }

    static final class LazyInitFailView extends View {
        CloseReason lastCloseReason;
        boolean firstRendered;
        final Pagination<String> pagination = this.<String>paginate(ctx -> {
            throw new IllegalStateException("lazy boom");
        })
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("LazyInitFail").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            firstRendered = true;
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class OutOfBoundsLayoutView extends View {
        CloseReason lastCloseReason;
        final Pagination<String> pagination = this.<String>paginate(
                        Collections.singletonList("a"))
                .layout(Layout.ofSlots(50))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            // a 1-row view: slot 50 of the explicit layout is out of bounds at init time
            config.title("OutOfBounds").rows(1);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class LayoutlessCharView extends View {
        @SuppressWarnings("unused")
        private final Pagination<String> pagination = this.<String>paginate(
                        Collections.singletonList("a"))
                .layoutChar('P')
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("LayoutlessChar").rows(1);
        }
    }

    static final class MissingCharView extends View {
        @SuppressWarnings("unused")
        private final Pagination<String> pagination = this.<String>paginate(
                        Collections.singletonList("a"))
                .layoutChar('P')
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("MissingChar").layout("OOO      ");
        }
    }

    // ---------------------------------------------------------------- tests

    @Test
    void eagerInit_runsAfterLayoutResolution_beforeOnFirstRender() {
        engine.open(player, EagerOrderingView.class, ViewArguments.empty());

        assertTrue(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(1, eagerOrderingView.pageInFirstRender);
        assertEquals(3, eagerOrderingView.totalPagesInFirstRender,
                "ceil(5 elements / 2 slots) pages; a pre-init read would see 1");
        assertEquals(5, eagerOrderingView.totalElementsInFirstRender,
                "a pre-init read would see 0");
        assertEquals(Boolean.FALSE, eagerOrderingView.loadingInFirstRender,
                "an eager source settles inline during init");
    }

    @Test
    void onOpenNavigation_recordsPendingTarget_initDispatchesThatPage() {
        engine.open(player, OnOpenNavigationView.class, ViewArguments.empty());

        // exactly one request: the pending target was replayed on the unbound paginator
        // (record-only branch), then bind dispatched once for the recorded page
        assertEquals(1, onOpenNavigationView.requests.size());
        PageRequest request = onOpenNavigationView.requests.get(0);
        assertEquals(3, request.getPage());
        assertEquals(4, request.getOffset(), "(3 - 1) * 2 layout slots");
        assertEquals(2, request.getPageSize());
        assertEquals(player.getUniqueId(), request.playerId());

        ViewSession session = session();
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        assertEquals(3, onOpenNavigationView.pagination.currentPage(context));
    }

    @Test
    void perContextIsolation_independentRequests_settlePaintsOnlyTheSettledSession() {
        engine.open(player, IsolationView.class, ViewArguments.empty());
        engine.open(second, IsolationView.class, ViewArguments.empty());

        // each context built its own source: two independent requests, one per player
        assertEquals(2, isolationView.requests.size());
        Set<UUID> requestedIds = new HashSet<>();
        requestedIds.add(isolationView.requests.get(0).playerId());
        requestedIds.add(isolationView.requests.get(1).playerId());
        assertEquals(new HashSet<>(Arrays.asList(player.getUniqueId(), second.getUniqueId())),
                requestedIds);

        ViewSession sessionA = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        ViewSession sessionB = sessions.find(second.getUniqueId()).orElseThrow(IllegalStateException::new);
        assertEquals(Material.BARRIER, sessionA.inventory().getItem(8).getType());
        assertEquals(Material.BARRIER, sessionB.inventory().getItem(8).getType());

        // completing A's future on the main thread settles inline and repaints only A
        isolationView.futures.get(player.getUniqueId())
                .complete(PageResult.of(Arrays.asList("a", "b"), 2));

        assertEquals(Material.EMERALD, sessionA.inventory().getItem(8).getType(),
                "player A's watcher must repaint on A's settle");
        assertEquals(Material.BARRIER, sessionB.inventory().getItem(8).getType(),
                "player B must still be loading");
        PlainViewContextImpl contextA = new PlainViewContextImpl(sessionA, engine);
        PlainViewContextImpl contextB = new PlainViewContextImpl(sessionB, engine);
        assertFalse(isolationView.pagination.isLoading(contextA));
        assertTrue(isolationView.pagination.isLoading(contextB));
        assertEquals(2, isolationView.pagination.totalElements(contextA));
        assertEquals(0, isolationView.pagination.totalElements(contextB),
                "totals are per-context; B's source has not settled");
    }

    @Test
    void perContextIsolation_pageCacheIsNotSharedAcrossContexts() {
        // §14 item 8 also pins cache isolation: the TTL cache lives on the per-context
        // AsyncPageSource instance, so one player's cached page must never serve another
        engine.open(player, CacheIsolationView.class, ViewArguments.empty());
        List<PageRequest> requestsA = cacheIsolationView.requestsByPlayer.get(player.getUniqueId());
        assertEquals(1, requestsA.size(), "A's initial page-1 load");

        ViewSession sessionA = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        PlainViewContextImpl contextA = new PlainViewContextImpl(sessionA, engine);
        cacheIsolationView.pagination.switchTo(contextA, 2);
        assertEquals(2, requestsA.size(), "A's page-2 load");
        cacheIsolationView.pagination.switchTo(contextA, 1);
        assertEquals(2, requestsA.size(),
                "page 1 must come from A's warm cache, not a third supplier call");
        assertEquals(1, cacheIsolationView.pagination.currentPage(contextA));

        engine.open(second, CacheIsolationView.class, ViewArguments.empty());
        List<PageRequest> requestsB = cacheIsolationView.requestsByPlayer.get(second.getUniqueId());
        assertEquals(1, requestsB.size(),
                "B's source must load page 1 itself even though A has it cached");
        assertEquals(0, requestsB.get(0).getOffset());
    }

    @Test
    void initFailure_abortsOpenFailed_registersNothing_closesTheDeadReplacedContainer() {
        Logger logger = Logger.getLogger(PaginationInitPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, SimpleView.class, ViewArguments.empty());
            ViewSession previous = session();
            Inventory previousContainer = previous.inventory();
            assertNotNull(previousContainer);

            engine.open(player, LazyInitFailView.class, ViewArguments.empty());

            assertFalse(sessions.find(player.getUniqueId()).isPresent(),
                    "an aborted open must register nothing");
            assertEquals(CloseReason.OPEN_FAILED, lazyInitFailView.lastCloseReason);
            assertFalse(lazyInitFailView.firstRendered,
                    "a failed init must abort before onFirstRender");
            assertTrue(handler.hasSevereContaining("pagination init failed for view"));
            // the commit point already replaced the previous session before init ran
            // (mirrors onFirstRender failures); the dead container must be closed
            assertEquals(CloseReason.REPLACED, simpleView.lastCloseReason);
            assertNotSame(previousContainer, player.getOpenInventory().getTopInventory(),
                    "the replaced view's dead container must be closed, not left clickable");
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void explicitLayoutOutOfBounds_abortsOpenFailed() {
        Logger logger = Logger.getLogger(PaginationInitPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            // the bounds violation is per-open (rows can vary per open when no config
            // layout is declared), so it surfaces at init, not at registration
            engine.open(player, OutOfBoundsLayoutView.class, ViewArguments.empty());

            assertFalse(sessions.find(player.getUniqueId()).isPresent());
            assertEquals(CloseReason.OPEN_FAILED, outOfBoundsLayoutView.lastCloseReason);
            assertTrue(handler.hasSevereContaining("pagination init failed for view"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void register_paginationLayoutChar_withoutLayout_throwsAtRegistration() {
        ViewRegistry registry = new ViewRegistry();

        ViewConfigurationException ex = assertThrows(ViewConfigurationException.class,
                () -> registry.register(new LayoutlessCharView()));

        assertEquals("view " + LayoutlessCharView.class.getName()
                + " declares pagination on layout char 'P' but has no layout", ex.getMessage());
    }

    @Test
    void register_paginationLayoutChar_missingFromLayout_throwsAtRegistration() {
        ViewRegistry registry = new ViewRegistry();

        ViewConfigurationException ex = assertThrows(ViewConfigurationException.class,
                () -> registry.register(new MissingCharView()));

        assertEquals("pagination layout char 'P' is not present in the layout of view "
                + MissingCharView.class.getName(), ex.getMessage());
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        boolean hasSevereContaining(String fragment) {
            for (LogRecord record : records) {
                if (record.getLevel() == Level.SEVERE && record.getMessage() != null
                        && record.getMessage().contains(fragment)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell, from repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationOpenWiringTest"
```

Expected: FAIL — compilation error: `package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase does not contain PaginationInitPhase` (the test's import does not resolve; `PaginationBindings` and `OpenFailureHandler` do not exist yet either).

- [ ] **Step 3: Create `PaginationBindings`**

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.List;

/**
 * Static lookup helpers over a session's pagination bindings. Bindings live in the owning
 * token's {@code StateStore} slot (created by the open phase), so lookups walk the view's
 * token table and read each pagination token's slot.
 */
@ApiStatus.Internal
public final class PaginationBindings {

    private PaginationBindings() {
    }

    /**
     * Returns the pagination bindings of a session in token-declaration order.
     *
     * @param session the session to inspect
     * @return the bindings created for the session; empty when the view declares no
     *         pagination tokens or the open phase has not seeded them yet
     */
    public static @NotNull List<PaginationBinding> of(@NotNull ViewSession session) {
        List<PaginationBinding> bindings = new ArrayList<>();
        for (StateToken token : session.registered().instance().tokenTable().tokens()) {
            if (!(token instanceof PaginationImpl)) {
                continue;
            }
            Object stored = session.stateStore().get(((PaginationImpl<?>) token).tokenId());
            if (stored != null) {
                bindings.add((PaginationBinding) stored);
            }
        }
        return bindings;
    }

    /**
     * Looks up the pagination element component currently occupying a slot.
     *
     * @param session the session to inspect
     * @param slot    the container slot
     * @return the element component at the slot, or {@code null} when no binding owns it
     */
    public static @Nullable ComponentInstance componentAt(@NotNull ViewSession session, int slot) {
        for (PaginationBinding binding : of(session)) {
            ComponentInstance component = binding.componentAt(slot);
            if (component != null) {
                return component;
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Create `OpenFailureHandler` and refactor `FirstRenderPhase` onto it**

The abort body moves VERBATIM out of the `FirstRenderPhase` catch block (current lines 90-97), comments included. `OpenFailureHandler` stays package-private: both of its callers live in `internal.engine.phase`.

New file:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

/**
 * Shared OPEN_FAILED abort path of the open pipeline: tears the failed session down through
 * the close phase and closes the player's screen when the commit point already replaced a
 * previous session, leaving no session protecting the visible container (spec §9). Used by
 * {@link FirstRenderPhase} and {@link PaginationInitPhase}.
 */
final class OpenFailureHandler {

    private OpenFailureHandler() {
    }

    /**
     * Aborts a failed open: closes the session with {@link CloseReason#OPEN_FAILED} and
     * closes the player's screen unless another session took over in the meantime.
     *
     * @param engine   the engine performing the close
     * @param sessions the per-player session registry
     * @param session  the session whose open failed; never registered by an aborted open
     */
    static void abort(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions,
                      @NotNull ViewSession session) {
        // teardown through the close phase; the session was never registered
        engine.close(session, CloseReason.OPEN_FAILED);
        // REPLACED -> OPEN_FAILED dead container: the commit point already closed the
        // previous session, so the player may still be staring at its container with no
        // session protecting it (free item theft); close the screen unless another
        // session took over in the meantime
        if (session.player().isOnline() && !sessions.find(session.player().getUniqueId()).isPresent()) {
            session.player().closeInventory();
        }
    }
}
```

In `FirstRenderPhase`, replace the whole `firstRender` method with (only the catch block changed — the abort lines moved into the handler):

```java
    /**
     * Renders and shows a freshly opened session produced by {@link OpenPhase#openSession}.
     *
     * @param session the session to render and activate
     */
    public void firstRender(@NotNull ViewSession session) {
        View view = session.registered().instance();
        RenderContextImpl renderContext = new RenderContextImpl(session, engine);
        try {
            HandlerInvoker.invoke(HandlerInvoker.ON_FIRST_RENDER, view, renderContext);
            renderContext.materializeAll();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onFirstRender failed for view " + view.getClass().getName()
                    + "; aborting the open", ex);
            OpenFailureHandler.abort(engine, sessions, session);
            return;
        }

        paintAll(session, renderContext);

        sessions.register(session);
        session.player().openInventory(session.inventory());
        session.status(ViewSession.Status.ACTIVE);
        startScheduledUpdates(session);
    }
```

And delete the now-unused import line from `FirstRenderPhase`:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
```

Everything else in `FirstRenderPhase` (class javadoc, `paintAll`, `startScheduledUpdates`) stays untouched in this task.

- [ ] **Step 5: Create `PaginationInitPhase`**

Visibility note: the phase is PUBLIC `@ApiStatus.Internal` (not package-private) because `ViewEngine` lives in `internal.engine` while the phases live in `internal.engine.phase` — the same pattern as `OpenPhase`/`FirstRenderPhase`/`UpdatePhase`.

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pagination init step of the open pipeline (spec §7 step 7): initializes every pagination
 * binding of a freshly opened session between layout resolution and {@code onFirstRender} —
 * target slots resolve against the session layout, the per-context page source is built,
 * the geometry engine is constructed and pending navigation recorded during {@code onOpen}
 * is replayed. A failing initialization aborts the open through the shared OPEN_FAILED
 * path. Constructed and invoked only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class PaginationInitPhase {

    private static final Logger LOGGER = Logger.getLogger(PaginationInitPhase.class.getName());

    private final ViewEngine engine;
    private final SessionRegistry sessions;

    /**
     * Creates the phase.
     *
     * @param engine   the owning engine, used by the abort path
     * @param sessions the per-player session registry, used by the abort path
     */
    public PaginationInitPhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Initializes every pagination binding of the session.
     *
     * @param session the freshly opened session; layout resolved and container created
     * @return {@code true} when every binding initialized; {@code false} when a binding
     *         failed and the open was aborted with an {@code OPEN_FAILED} close
     */
    public boolean init(@NotNull ViewSession session) {
        // openSession resolved the layout before returning; init never runs without it
        ResolvedLayout layout = Objects.requireNonNull(session.layout(), "layout");
        for (PaginationBinding binding : PaginationBindings.of(session)) {
            try {
                binding.initialize(layout, session.effectiveConfig());
            } catch (RuntimeException ex) {
                LOGGER.log(Level.SEVERE, "pagination init failed for view "
                        + session.registered().type().getName() + "; aborting the open", ex);
                OpenFailureHandler.abort(engine, sessions, session);
                return false;
            }
        }
        return true;
    }
}
```

- [ ] **Step 6: Modify `OpenPhase` — seed bindings before `onOpen`**

Add these imports to `OpenPhase`:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationImpl;
```

Call site — in `openSession`, the two lines after the `bindInitialState` call become three (the new call sits after `bindInitialState`, before the `OpenContextImpl` construction):

```java
        // a type mismatch propagates here, before any observable side effect
        bindInitialState(view, arguments, store);
        // pagination bindings exist before onOpen so pre-init navigation calls have a
        // recording target; the init phase replays the recorded target after layout resolution
        bindPaginationTokens(view, session, store);

        OpenContextImpl openContext = new OpenContextImpl(session, engine);
```

Full added method (placed directly below the existing `bindInitialState` method; not static — it needs the engine reference):

```java
    // creates one binding per pagination token; the binding records pre-init navigation
    // and is initialized by the pagination init phase after layout resolution
    private void bindPaginationTokens(View view, ViewSession session, StateStore store) {
        for (StateToken token : view.tokenTable().tokens()) {
            if (token instanceof PaginationImpl) {
                PaginationImpl<?> pagination = (PaginationImpl<?>) token;
                store.set(pagination.tokenId(),
                        new PaginationBinding(pagination.spec(), pagination.tokenId(), session, engine));
            }
        }
    }
```

- [ ] **Step 7: Modify `ViewEngine` — construct the phase and call it from `open()`**

Add the import:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.PaginationInitPhase;
```

Extend the phase-handler field block (anchor: the existing `final ClosePhase closePhase;` line — Task 1's FlushCoordinator extraction does not touch this block):

```java
    // fixed-order phase handlers, engine-owned
    final OpenPhase openPhase;
    final FirstRenderPhase firstRenderPhase;
    final UpdatePhase updatePhase;
    final ClickRoutingPhase clickRoutingPhase;
    final ClosePhase closePhase;
    final PaginationInitPhase paginationInitPhase;
```

In the constructor, add one line directly after `this.clickRoutingPhase = new ClickRoutingPhase(this);`:

```java
        this.paginationInitPhase = new PaginationInitPhase(this, sessions);
```

Replace `open()` with (javadoc unchanged; the only change is the init call between the null-check and `firstRender`):

```java
    /**
     * Opens a registered view for a player, replacing any previous session at the commit
     * point (spec §7). Self-defers to end of tick during click dispatch, so service-path
     * opens made from a click handler never tear down the clicked session mid-dispatch;
     * the registration check then runs when the deferred open executes.
     *
     * @param player    the viewer
     * @param viewType  the registered view class
     * @param arguments the open arguments
     * @throws UnknownViewException     when the view class is not registered
     * @throws IllegalArgumentException when an {@code initialState} argument has a
     *                                  mismatching type
     * @throws IllegalStateException    when called off the main thread
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> viewType,
                     @NotNull ViewArguments arguments) {
        ThreadUtils.assertMainThread("ViewEngine.open");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(arguments, "arguments");
        if (isInClickDispatch()) {
            // self-defer: an inline open would replace the clicked session while its click
            // is still being routed; the deferred op runs at end of tick, when click
            // dispatch is over, so it cannot re-defer
            ViewSession current = sessions.find(player.getUniqueId()).orElse(null);
            if (current != null) {
                defer(current, () -> open(player, viewType, arguments));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> open(player, viewType, arguments));
            }
            return;
        }
        RegisteredView registered = views.find(viewType).orElseThrow(() ->
                new UnknownViewException("view " + viewType.getName() + " is not registered"));
        wireSharedFlush(registered);
        ViewSession session = openPhase.openSession(player, registered, arguments);
        if (session == null) {
            // cancelled with zero side effects; the previous session stays untouched
            return;
        }
        if (!paginationInitPhase.init(session)) {
            // a failed init already aborted the open with an OPEN_FAILED close
            return;
        }
        firstRenderPhase.firstRender(session);
    }
```

- [ ] **Step 8: Modify `ViewRegistry` — registration-time layoutChar validation**

Add these imports to `ViewRegistry`:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationSpec;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;
```

Call site — in `register(View instance)`, insert the validation between `builder.build()` and the map put:

```java
        instance.tokenTable().freeze();
        ViewConfigBuilder builder = new ViewConfigBuilder();
        HandlerInvoker.invoke(HandlerInvoker.ON_INIT, instance, builder);
        ViewConfig config = builder.build();
        validatePaginationTargets(instance, config);
        views.put(type, new RegisteredView(type, instance, config));
```

Full added validation method (placed below `register`):

```java
    // registration-time guard (spec §5.3): a LAYOUT_CHAR pagination target must name a
    // char that exists in the declared layout; explicit layouts and patterns carry their
    // own slots and are bounds-checked per open instead, because rows may vary per open
    private static void validatePaginationTargets(View instance, ViewConfig config) {
        for (StateToken token : instance.tokenTable().tokens()) {
            if (!(token instanceof PaginationImpl)) {
                continue;
            }
            PaginationSpec<?> spec = ((PaginationImpl<?>) token).spec();
            if (spec.target() != PaginationSpec.Target.LAYOUT_CHAR) {
                continue;
            }
            char character = spec.layoutChar();
            if (config.layout().isEmpty()) {
                throw new ViewConfigurationException("view " + instance.getClass().getName()
                        + " declares pagination on layout char '" + character
                        + "' but has no layout");
            }
            if (!ResolvedLayout.resolve(config).hasChar(character)) {
                throw new ViewConfigurationException("pagination layout char '" + character
                        + "' is not present in the layout of view "
                        + instance.getClass().getName());
            }
        }
    }
```

Also extend the `register` javadoc `@throws ViewConfigurationException` line so the new failure mode is documented:

```java
     * @throws ViewConfigurationException when the built config violates the validation rules
     *                                    or a pagination layout-char target does not match
     *                                    the declared layout
```

- [ ] **Step 9: Run the new test and the pre-existing OPEN_FAILED regressions**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationOpenWiringTest,ViewEngineOpenCloseTest,ViewEngineOpenOrderingTest"
```

Expected: PASS — all 8 `PaginationOpenWiringTest` tests green, and the two pre-existing OPEN_FAILED tests still green after the `OpenFailureHandler` extraction:
- `ViewEngineOpenCloseTest.onFirstRenderThrows_abortsWithOpenFailedCloseHookAndRegistersNothing`
- `ViewEngineOpenOrderingTest.onFirstRenderThrows_afterReplacement_closesTheDeadPreviousContainer`

- [ ] **Step 10: Full module check**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS (BUILD SUCCESS, every inventory-api test green).

- [ ] **Step 11: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindings.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/OpenFailureHandler.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/PaginationInitPhase.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/OpenPhase.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistry.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationOpenWiringTest.java
git commit -m "feat(inventory-api): wire pagination bindings and init into the open pipeline" -m "OpenPhase seeds one PaginationBinding per pagination token before onOpen,
PaginationInitPhase initializes them between layout resolution and
onFirstRender (aborting OPEN_FAILED through the extracted shared
OpenFailureHandler), and ViewRegistry rejects LAYOUT_CHAR targets that
do not match the declared layout at registration time."
```

---

### Task 11: Reactive settles — UpdatePhase gate/areas/watchers + FirstRenderPhase pagination paint

`UpdatePhase` learns the §5.7 reactive-settle semantics: the gate additionally delivers `PAGINATION_SETTLE` to TRANSITIONING sessions (CLOSED skip and the always-delivered STATE_CHANGE semantics are unchanged; OPENING still receives neither), full passes repaint every initialized pagination area after the static components, and scoped passes repaint dirty-token areas plus the pagination element components watching dirty tokens. `FirstRenderPhase.paintAll` paints every initialized binding after the static components, so eager items are visible before `player.openInventory` and async sources show their loading frame.

Mid-plan note: this task closes the deliberate interim state left after Task 7 — until now settles repainted token *watchers* only, not the pagination areas themselves. After this task the reactive behavior is fully spec-compliant, and the 2.x tick-poll caveat (needing `scheduleUpdate` for async page paints) is gone.

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/UpdatePhase.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationSettleTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaginationSettleTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;

    private EagerPaintView eagerPaintView;
    private AsyncSettleView asyncSettleView;
    private CachedCounterView cachedCounterView;
    private EagerNavView eagerNavView;
    private ElementWatcherView elementWatcherView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        eagerPaintView = new EagerPaintView();
        asyncSettleView = new AsyncSettleView();
        cachedCounterView = new CachedCounterView();
        eagerNavView = new EagerNavView();
        elementWatcherView = new ElementWatcherView();
        views.register(eagerPaintView);
        views.register(asyncSettleView);
        views.register(cachedCounterView);
        views.register(eagerNavView);
        views.register(elementWatcherView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; cancel first so settle tasks queued by
        // tests that never ticked do not run against torn-down state
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private InventoryClickEvent click(PlayerMock who, ViewSession session, int rawSlot) {
        Inventory top = session.inventory();
        Inventory bottom = who.getInventory();
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(bottom);
        when(invView.getPlayer()).thenReturn(who);
        when(invView.convertSlot(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(invView.getInventory(anyInt())).thenAnswer(inv -> {
            int raw = inv.getArgument(0);
            if (raw < 0) {
                return null;
            }
            return raw < top.getSize() ? top : bottom;
        });
        return new InventoryClickEvent(invView, InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    // ---------------------------------------------------------------- fixture views

    static final class EagerPaintView extends View {
        final Pagination<String> pagination = this.<String>paginate(Arrays.asList("1", "2", "3"))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, Integer.parseInt(value))))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("EagerPaint").layout("OOO      ");
        }
    }

    static final class AsyncSettleView extends View {
        volatile CompletableFuture<PageResult<String>> pendingFuture;
        final List<UpdateTrigger> triggers = new CopyOnWriteArrayList<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            CompletableFuture<PageResult<String>> future = new CompletableFuture<>();
            pendingFuture = future;
            return future;
        })
                .loadingItem(ctx -> new ItemStack(Material.CLOCK))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, index + 1)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            // deliberately NO scheduleUpdate: repaints must be settle-driven
            config.title("AsyncSettle").layout("OOO      ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            // arrow: visible only once a next page exists; watches the pagination token
            context.slot(8)
                    .item(new ItemStack(Material.ARROW))
                    .displayIf(ctx -> pagination.canAdvance(ctx))
                    .updateOnStateChange(pagination);
        }

        @Override
        protected void onUpdate(@NotNull UpdateContext context) {
            triggers.add(context.trigger());
        }
    }

    static final class CachedCounterView extends View {
        final AtomicInteger supplierCalls = new AtomicInteger();
        final AtomicInteger rendererCalls = new AtomicInteger();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            supplierCalls.incrementAndGet();
            return CompletableFuture.completedFuture(
                    PageResult.of(Arrays.asList("a", "b"), 2));
        })
                .cacheTtl(Duration.ofMinutes(1))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, rendererCalls.incrementAndGet())))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CachedCounter").layout("OO       ");
        }
    }

    static final class EagerNavView extends View {
        final Pagination<String> pagination = this.<String>paginate(
                        Arrays.asList("1", "2", "3", "4"))
                .itemRenderer((ctx, item, index, value) ->
                        item.item(new ItemStack(Material.PAPER, Integer.parseInt(value))))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("EagerNav").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(8, new ItemStack(Material.ARROW))
                    .onClick(ctx -> pagination.advance(ctx));
        }
    }

    static final class ElementWatcherView extends View {
        final MutableState<Integer> badge = mutableState(0);
        final AtomicInteger rendererCalls = new AtomicInteger();
        final AtomicInteger itemFunctionCalls = new AtomicInteger();
        final Pagination<String> pagination = this.<String>paginate(Arrays.asList("a", "b"))
                .itemRenderer((ctx, item, index, value) -> {
                    rendererCalls.incrementAndGet();
                    item.item(c -> {
                        itemFunctionCalls.incrementAndGet();
                        Integer current = badge.get(c);
                        return new ItemStack(Material.PAPER, (current == null ? 0 : current) + 1);
                    }).updateOnStateChange(badge);
                })
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ElementWatcher").layout("OO       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(8, new ItemStack(Material.STONE))
                    .onClick(ctx -> badge.set(ctx, 5));
        }
    }

    // ---------------------------------------------------------------- tests

    @Test
    void eagerOpen_paintsItemsIntoCharSlots_beforeTheContainerIsShown() {
        engine.open(player, EagerPaintView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();

        // firstRender paints static components, then pagination areas, BEFORE
        // player.openInventory and activation — so these contents were visible at show time
        assertEquals(Material.PAPER, inventory.getItem(0).getType());
        assertEquals(1, inventory.getItem(0).getAmount());
        assertEquals(2, inventory.getItem(1).getAmount());
        assertEquals(3, inventory.getItem(2).getAmount());
        assertNull(inventory.getItem(3), "slots outside the area stay untouched");
        assertSame(inventory, player.getOpenInventory().getTopInventory());
    }

    @Test
    void asyncOpen_loadingFrame_settleDeliversOnTick_repaintsAreaAndWatcher()
            throws InterruptedException {
        engine.open(player, AsyncSettleView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();

        // the loading frame is painted at first render; the arrow is hidden (no next page yet)
        assertEquals(Material.CLOCK, inventory.getItem(0).getType());
        assertEquals(Material.CLOCK, inventory.getItem(1).getType());
        assertEquals(Material.CLOCK, inventory.getItem(2).getType());
        assertNull(inventory.getItem(8));

        // off-thread completion: the settle must land on the main thread via the scheduler
        Thread completer = new Thread(() -> asyncSettleView.pendingFuture
                .complete(PageResult.of(Arrays.asList("a", "b", "c"), 10)));
        completer.start();
        completer.join();

        assertEquals(Material.CLOCK, inventory.getItem(0).getType(),
                "nothing repaints before the scheduled settle runs");
        assertFalse(asyncSettleView.triggers.contains(UpdateTrigger.PAGINATION_SETTLE));

        server.getScheduler().performOneTick();

        assertEquals(Material.PAPER, inventory.getItem(0).getType());
        assertEquals(1, inventory.getItem(0).getAmount());
        assertEquals(3, inventory.getItem(2).getAmount());
        assertTrue(asyncSettleView.triggers.contains(UpdateTrigger.PAGINATION_SETTLE));
        assertEquals(Material.ARROW, inventory.getItem(8).getType(),
                "the arrow watches the token and repaints when canAdvance flips");
    }

    @Test
    void settleDrivenRepaint_requiresNoScheduledUpdateTask() throws InterruptedException {
        engine.open(player, AsyncSettleView.class, ViewArguments.empty());
        ViewSession session = session();
        // no scheduleUpdate was configured: there is no tick poll to lean on (2.x caveat gone)
        assertNull(session.updateTask());

        Thread completer = new Thread(() -> asyncSettleView.pendingFuture
                .complete(PageResult.of(Arrays.asList("a", "b", "c"), 10)));
        completer.start();
        completer.join();
        server.getScheduler().performOneTick();

        assertEquals(Material.PAPER, session.inventory().getItem(0).getType(),
                "the repaint is settle-driven, not poll-driven");
    }

    @Test
    void settleForClosedSession_isDropped_noPaintNoOnUpdateNoThrow() throws InterruptedException {
        engine.open(player, AsyncSettleView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();

        Thread completer = new Thread(() -> asyncSettleView.pendingFuture
                .complete(PageResult.of(Arrays.asList("a", "b", "c"), 10)));
        completer.start();
        completer.join();
        engine.close(session, CloseReason.API);
        asyncSettleView.triggers.clear();

        // the scheduled settle now runs against a CLOSED session and must be dropped
        server.getScheduler().performOneTick();

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertEquals(Material.CLOCK, inventory.getItem(0).getType(),
                "the dead container must not repaint");
        assertTrue(asyncSettleView.triggers.isEmpty(), "no onUpdate fires for a closed session");
    }

    @Test
    void settleWhileTransitioning_isDelivered_andRepaints() throws InterruptedException {
        engine.open(player, AsyncSettleView.class, ViewArguments.empty());
        ViewSession session = session();

        Thread completer = new Thread(() -> asyncSettleView.pendingFuture
                .complete(PageResult.of(Arrays.asList("a", "b", "c"), 10)));
        completer.start();
        completer.join();
        // a deferred navigation is pending: the session left ACTIVE but is not torn down
        session.status(ViewSession.Status.TRANSITIONING);

        server.getScheduler().performOneTick();

        assertEquals(Material.PAPER, session.inventory().getItem(0).getType(),
                "PAGINATION_SETTLE must be delivered to a TRANSITIONING session");
        assertTrue(asyncSettleView.triggers.contains(UpdateTrigger.PAGINATION_SETTLE));
    }

    @Test
    void explicitUpdate_fullPass_reRendersThePaginationArea() {
        engine.open(player, CachedCounterView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();
        assertEquals(2, cachedCounterView.rendererCalls.get(),
                "first paint renders each of the two elements once");
        assertEquals(1, cachedCounterView.supplierCalls.get());
        assertEquals(1, inventory.getItem(0).getAmount());

        new PlainViewContextImpl(session, engine).update();

        assertEquals(4, cachedCounterView.rendererCalls.get(),
                "a full pass re-renders every element of the pagination area");
        assertEquals(3, inventory.getItem(0).getAmount(), "the re-rendered item is repainted");
        assertEquals(1, cachedCounterView.supplierCalls.get(),
                "the settled cached page is reused; no new load is dispatched");
    }

    @Test
    void advanceFromClick_eagerSource_repaintsInlineWithinTheClick() {
        engine.open(player, EagerNavView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();
        assertEquals(1, inventory.getItem(0).getAmount());
        assertEquals(2, inventory.getItem(1).getAmount());

        engine.click(session, click(player, session, 8));

        // the eager advance settles inline; its settle pass repaints within the click
        assertEquals(3, inventory.getItem(0).getAmount(),
                "page 2 must be visible right after the click");
        assertEquals(4, inventory.getItem(1).getAmount());
    }

    @Test
    void stateChangeFlush_repaintsElementWatchers_withoutReRunningTheRenderer() {
        engine.open(player, ElementWatcherView.class, ViewArguments.empty());
        ViewSession session = session();
        Inventory inventory = session.inventory();
        assertEquals(2, elementWatcherView.rendererCalls.get());
        assertEquals(2, elementWatcherView.itemFunctionCalls.get());
        assertEquals(1, inventory.getItem(0).getAmount());

        // the click handler writes the watched MutableState; the click's coalesced flush
        // runs a STATE_CHANGE pass scoped to the badge token
        engine.click(session, click(player, session, 8));

        assertEquals(6, inventory.getItem(0).getAmount());
        assertEquals(6, inventory.getItem(1).getAmount());
        assertEquals(4, elementWatcherView.itemFunctionCalls.get(),
                "both element item functions re-evaluate on the watched flush");
        assertEquals(2, elementWatcherView.rendererCalls.get(),
                "the pagination renderer must not re-run for an element-watcher repaint");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell, from repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationSettleTest"
```

Expected: FAIL — the file compiles (every referenced type exists after Task 10), but the eager/async first-paint assertions throw `NullPointerException` calling `getType()`/`getAmount()` on empty slots (`FirstRenderPhase` does not paint pagination areas yet), `settleWhileTransitioning_isDelivered_andRepaints` fails because the old gate drops PAGINATION_SETTLE for non-ACTIVE sessions, and the explicit-update / advance-click / element-watcher tests fail their repaint assertions (`UpdatePhase` does not repaint areas or element watchers yet).

- [ ] **Step 3: Modify `UpdatePhase` — gate + area/element repaints**

Add these imports to `UpdatePhase`:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
```

Replace the class javadoc with:

```java
/**
 * Update pass: invokes {@code View.onUpdate} with the trigger, then repaints components —
 * all of them plus every pagination area on a full pass, or only the watchers of a dirty
 * token set (plus dirty pagination areas and their element watchers) during a scoped
 * flush. An {@code onUpdate} failure is logged and the repaint still runs (§9 error table).
 */
```

Replace the whole `update` method with (the gate gains the PAGINATION_SETTLE branch; everything after the gate is unchanged):

```java
    /**
     * Runs one update pass on a session. CLOSED sessions are skipped entirely — neither
     * {@code onUpdate} nor a repaint runs on a torn-down session. Other non-active sessions
     * skip every trigger except {@link UpdateTrigger#STATE_CHANGE}, which flushes are
     * allowed to deliver to TRANSITIONING and OPENING sessions, and
     * {@link UpdateTrigger#PAGINATION_SETTLE}, which is delivered to TRANSITIONING
     * sessions only — a page settling while a deferred navigation is pending still
     * paints, while OPENING sessions never observe a settle pass (spec §7).
     *
     * @param session     the session to update
     * @param trigger     the cause of this pass
     * @param dirtyOrNull the dirty token ids restricting the repaint to their watchers,
     *                    or {@code null} to repaint every component
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger,
                       @Nullable Set<Integer> dirtyOrNull) {
        // a CLOSED session never receives onUpdate, not even from a STATE_CHANGE flush
        if (session.status() == ViewSession.Status.CLOSED) {
            return;
        }
        if (!session.isActive()
                && trigger != UpdateTrigger.STATE_CHANGE
                && !(trigger == UpdateTrigger.PAGINATION_SETTLE
                && session.status() == ViewSession.Status.TRANSITIONING)) {
            return;
        }

        UpdateContextImpl context = new UpdateContextImpl(session, engine, trigger);
        try {
            HandlerInvoker.invoke(HandlerInvoker.ON_UPDATE,
                    session.registered().instance(), context);
        } catch (RuntimeException error) {
            LOGGER.log(Level.SEVERE, "onUpdate failed for view "
                    + session.registered().type().getName(), error);
        }

        // onUpdate may have closed the session; never paint a torn-down container
        if (session.status() == ViewSession.Status.CLOSED) {
            return;
        }
        repaint(session, context, dirtyOrNull);
    }
```

Replace the whole `repaint` method with the following (the existing renderForPaint loop moves into the new `paintComponents` helper so the scoped element-watcher pass reuses the exact same loop shape):

```java
    private void repaint(ViewSession session, UpdateContextImpl context,
                         @Nullable Set<Integer> dirtyOrNull) {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }

        Player player = session.player();
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();

        List<ComponentInstance> targets = dirtyOrNull == null
                ? session.components().all()
                : session.components().watchersOf(dirtyOrNull);
        paintComponents(targets, context, player, inventory, applyPlaceholders);

        if (dirtyOrNull == null) {
            // full pass: every pagination area re-renders after the static components
            for (PaginationBinding binding : PaginationBindings.of(session)) {
                if (binding.isInitialized()) {
                    binding.repaint();
                }
            }
            return;
        }

        for (PaginationBinding binding : PaginationBindings.of(session)) {
            if (!binding.isInitialized()) {
                continue;
            }
            if (dirtyOrNull.contains(binding.tokenId())) {
                // the binding's own token settled or was dirtied: re-render the whole area;
                // the fresh element components are painted by the fill itself, so running
                // the watcher pass too would evaluate them a second time in one flush
                // (§5.5: at most one re-render per component per flush) — skip it
                binding.repaint();
                continue;
            }
            // element components watching a dirty token repaint exactly like static
            // watchers: the item function re-evaluates, the user renderer does not re-run
            paintComponents(binding.elementWatchersOf(dirtyOrNull), context, player,
                    inventory, applyPlaceholders);
        }
    }

    // the shared renderForPaint loop of both pass kinds: the RENDER_FAILURE identity
    // sentinel skips the paint so the slots keep their previous content (§9)
    private void paintComponents(List<ComponentInstance> targets, UpdateContextImpl context,
                                 Player player, Inventory inventory, boolean applyPlaceholders) {
        for (ComponentInstance component : targets) {
            ItemStack item = component.renderForPaint(context);
            if (item == ComponentInstance.RENDER_FAILURE) {
                continue;
            }
            for (int slot : component.slots()) {
                painter.paint(player, inventory, slot, item, applyPlaceholders);
            }
        }
    }
```

- [ ] **Step 4: Modify `FirstRenderPhase.paintAll` — paint pagination areas before show**

Add these imports to `FirstRenderPhase`:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
```

Replace the whole `paintAll` method with (the static-component loop is unchanged; the binding loop is appended):

```java
    private void paintAll(ViewSession session, RenderContextImpl renderContext) {
        Inventory inventory = session.inventory();
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();
        for (ComponentInstance component : session.components().all()) {
            ItemStack item = component.renderForPaint(renderContext);
            if (item == ComponentInstance.RENDER_FAILURE) {
                // identity sentinel: skip the paint so the slots keep their previous content (§9)
                continue;
            }
            for (int slot : component.slots()) {
                painter.paint(session.player(), inventory, slot, item, applyPlaceholders);
            }
        }
        // pagination areas paint after the static components: eager sources show their
        // items before the container is shown to the player; async sources paint the
        // loading frame
        for (PaginationBinding binding : PaginationBindings.of(session)) {
            if (binding.isInitialized()) {
                binding.repaint();
            }
        }
    }
```

- [ ] **Step 5: Run test to verify it passes**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationSettleTest"
```

Expected: PASS — all 8 tests green.

- [ ] **Step 6: Full module check**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS (BUILD SUCCESS, every inventory-api test green — in particular the Task 7/10 pagination tests and the pre-existing `UpdateFlushTest` gate/flush behaviors are unaffected).

- [ ] **Step 7: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/UpdatePhase.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationSettleTest.java
git commit -m "feat(inventory-api): drive pagination repaints from reactive settles" -m "UpdatePhase delivers PAGINATION_SETTLE to TRANSITIONING sessions and
repaints pagination areas on full passes plus dirty-token areas and
element watchers on scoped passes; FirstRenderPhase paints every
initialized area before the container is shown. Closes the Task 7
watchers-only interim state."
```

---

### Task 12: Click routing on elements, overlap validation and unbound-layout-char warning

> **Seam confirmation:** this task reads `PaginationBinding.targetSlots()` — pinned in the Shared Type Contracts and shipped (with its regression test) by Task 7: `public @NotNull int[] targetSlots()`, empty before `initialize(...)`, the resolved fill layout's slots for NORMAL/SCROLL geometry, the union of all pattern slots for PATTERN geometry.
>
> **Seam confirmation:** the contract pins `RenderContextImpl.boundLayoutChars()` as `public` (its consumer `FirstRenderPhase` lives in a different package; the class is `@ApiStatus.Internal` — same precedent as `materializeAll()`). This task adds it exactly so.

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/ClickRoutingPhase.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/RenderContextImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationClickRoutingTest.java

- [ ] **Step 1: Write the failing test**

The fixture and click-event fabrication style mirrors `ClickRoutingTest` (same package, same Mockito `InventoryView` mock). Every fixture paginates an eager list, so elements are materialized inline during the open — no scheduler ticks are needed before clicking.

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.FirstRenderPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaginationClickRoutingTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private PlayerMock player;
    private ElementHandlersView elementHandlersView;
    private CancelOverrideView cancelOverrideView;
    private ThrowingElementView throwingElementView;
    private HiddenElementView hiddenElementView;
    private OverlapView overlapView;

    // two items over three 'O' slots: slots 0-1 hold elements, slot 2 holds the frame fallback
    static final class ElementHandlersView extends View {
        int typedRightClicks;
        int untypedClicks;
        int viewClicks;

        final Pagination<String> pagination = paginate(Arrays.asList("first", "second"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.STONE))
                        .onClick(ClickType.RIGHT, ctx -> typedRightClicks++)
                        .onClick(ctx -> untypedClicks++))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ElementHandlers").layout("OOO      ");
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
        }
    }

    static final class CancelOverrideView extends View {
        int elementClicks;

        final Pagination<String> pagination = paginate(Arrays.asList("only"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.PAPER))
                        .cancelOnClick(false)
                        .onClick(ctx -> elementClicks++))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CancelOverride").layout("O        ");
        }
    }

    static final class ThrowingElementView extends View {
        int handlerCalls;
        int viewClicks;

        final Pagination<String> pagination = paginate(Arrays.asList("boom"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.STONE))
                        .cancelOnClick(false)
                        .closeOnClick()
                        .onClick(ctx -> {
                            handlerCalls++;
                            throw new IllegalStateException("boom");
                        }))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ThrowingElement").layout("O        ");
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
        }
    }

    static final class CloseOnClickElementView extends View {
        final Pagination<String> pagination = paginate(Arrays.asList("close"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.ARROW))
                        .closeOnClick())
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CloseOnClickElement").layout("O        ");
        }
    }

    static final class HiddenElementView extends View {
        int hiddenClicks;
        int viewClicks;

        final Pagination<String> pagination = paginate(Arrays.asList("ghost"))
                .itemRenderer((context, item, index, value) -> item
                        .item(new ItemStack(Material.STONE))
                        .displayIf(ctx -> false)
                        .cancelOnClick(false)
                        .onClick(ctx -> hiddenClicks++))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("HiddenElement").layout("O        ");
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
        }
    }

    static final class OverlapView extends View {
        CloseReason lastCloseReason;

        final Pagination<String> pagination = paginate(Arrays.asList("a", "b", "c"))
                .itemRenderer((context, item, index, value) -> item.item(new ItemStack(Material.STONE)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Overlap").layout("OOO      ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            // static component on the same 'O' slots the pagination targets
            context.layoutSlot('O', new ItemStack(Material.PAPER));
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    // 'O' is pagination-bound, 'A' is component-bound, 'X' is bound to nothing
    static final class UnboundCharView extends View {
        final Pagination<String> pagination = paginate(Arrays.asList("page"))
                .itemRenderer((context, item, index, value) -> item.item(new ItemStack(Material.STONE)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("UnboundChar").layout("OAX      ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.layoutSlot('A', new ItemStack(Material.PAPER));
        }
    }

    static final class BoundCharsView extends View {
        final Pagination<String> pagination = paginate(Arrays.asList("page"))
                .itemRenderer((context, item, index, value) -> item.item(new ItemStack(Material.STONE)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("BoundChars").layout("OA       ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.layoutSlot('A', new ItemStack(Material.PAPER));
        }
    }

    static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        elementHandlersView = new ElementHandlersView();
        cancelOverrideView = new CancelOverrideView();
        throwingElementView = new ThrowingElementView();
        hiddenElementView = new HiddenElementView();
        overlapView = new OverlapView();
        views.register(elementHandlersView);
        views.register(cancelOverrideView);
        views.register(throwingElementView);
        views.register(new CloseOnClickElementView());
        views.register(hiddenElementView);
        views.register(overlapView);
        views.register(new UnboundCharView());
        views.register(new BoundCharsView());
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        });
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks; deferred closes left behind by a failed
        // assertion must not run against a torn-down registry
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    private ViewSession open(Class<? extends View> type) {
        engine.open(player, type, ViewArguments.empty());
        return session();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private InventoryView mockView(Inventory top) {
        Inventory bottom = player.getInventory();
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(bottom);
        when(invView.getPlayer()).thenReturn(player);
        when(invView.convertSlot(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(invView.getInventory(anyInt())).thenAnswer(inv -> {
            int raw = inv.getArgument(0);
            if (raw < 0) {
                return null;
            }
            return raw < top.getSize() ? top : bottom;
        });
        return invView;
    }

    private InventoryClickEvent click(int rawSlot, ClickType clickType, InventoryAction action) {
        return new InventoryClickEvent(mockView(session().inventory()),
                InventoryType.SlotType.CONTAINER, rawSlot, clickType, action);
    }

    private InventoryClickEvent click(int rawSlot) {
        return click(rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private static List<LogRecord> warningsOf(CapturingHandler handler) {
        List<LogRecord> warnings = new ArrayList<>();
        for (LogRecord record : handler.records) {
            if (record.getLevel() == Level.WARNING) {
                warnings.add(record);
            }
        }
        return warnings;
    }

    @Test
    void elementTypedHandler_runsOnMatchingClickType() {
        ViewSession session = open(ElementHandlersView.class);

        engine.click(session, click(0, ClickType.RIGHT, InventoryAction.PICKUP_HALF));

        assertEquals(1, elementHandlersView.typedRightClicks);
        assertEquals(0, elementHandlersView.untypedClicks, "the typed handler wins for its click type");
    }

    @Test
    void elementUntypedHandler_runsOnlyWithoutTypedMatch() {
        ViewSession session = open(ElementHandlersView.class);

        engine.click(session, click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL));

        assertEquals(0, elementHandlersView.typedRightClicks);
        assertEquals(1, elementHandlersView.untypedClicks);
    }

    @Test
    void elementCancelFalse_overridesConfigDefaultTrue() {
        ViewSession session = open(CancelOverrideView.class);
        InventoryClickEvent event = click(0);

        engine.click(session, event);

        assertEquals(1, cancelOverrideView.elementClicks);
        assertFalse(event.isCancelled(), "the element override beats the config default");
    }

    @Test
    void throwingElementHandler_forceCancelsAndSkipsRest() {
        ViewSession session = open(ThrowingElementView.class);
        InventoryClickEvent event = click(0);

        engine.click(session, event);

        assertEquals(1, throwingElementView.handlerCalls);
        assertTrue(event.isCancelled(), "a throwing element handler force-cancels despite cancelOnClick(false)");
        assertEquals(0, throwingElementView.viewClicks, "view-level onClick is skipped after the throw");
        assertTrue(session.deferredOps().isEmpty(), "the closeOnClick post-action must not be deferred");
        assertEquals(ViewSession.Status.ACTIVE, session.status());
    }

    @Test
    void elementCloseOnClick_isDeferredToEndOfTick() {
        ViewSession session = open(CloseOnClickElementView.class);

        engine.click(session, click(0));

        assertEquals(ViewSession.Status.TRANSITIONING, session.status(),
                "the session leaves ACTIVE immediately so further clicks are swallowed");
        assertFalse(session.deferredOps().isEmpty(), "the close runs at end of tick, not inline");

        server.getScheduler().performOneTick();

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void hiddenElement_clickIsComponentLess() {
        ViewSession session = open(HiddenElementView.class);
        InventoryClickEvent event = click(0);

        engine.click(session, event);

        assertEquals(0, hiddenElementView.hiddenClicks, "hidden elements get no clicks");
        assertTrue(event.isCancelled(), "config cancel wins; the hidden element's override is ignored");
        assertEquals(1, hiddenElementView.viewClicks, "the slot degrades to a component-less click");
    }

    @Test
    void frameSlotClick_isComponentLess() {
        // slot 2 of the three-slot area holds the frame fallback, not a page element
        ViewSession session = open(ElementHandlersView.class);
        InventoryClickEvent event = click(2);

        engine.click(session, event);

        assertEquals(0, elementHandlersView.typedRightClicks);
        assertEquals(0, elementHandlersView.untypedClicks, "frame slots have no element handlers");
        assertEquals(1, elementHandlersView.viewClicks);
        assertTrue(event.isCancelled());
    }

    @Test
    void staticComponentOnPaginationSlot_abortsOpenAsOpenFailed() {
        // static-vs-element precedence is deliberately untestable: the overlap validation
        // rejects the configuration before any element could shadow a static component
        engine.open(player, OverlapView.class, ViewArguments.empty());

        assertFalse(sessions.find(player.getUniqueId()).isPresent(), "the session must never register");
        assertEquals(CloseReason.OPEN_FAILED, overlapView.lastCloseReason);
    }

    @Test
    void unboundLayoutChar_warnsExactlyOncePerViewClass() {
        Logger logger = Logger.getLogger(FirstRenderPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, UnboundCharView.class, ViewArguments.empty());
            PlayerMock second = server.addPlayer("second");
            engine.open(second, UnboundCharView.class, ViewArguments.empty());
        } finally {
            logger.removeHandler(handler);
        }

        List<LogRecord> warnings = warningsOf(handler);
        assertEquals(1, warnings.size(), "one warning per view class, not one per open");
        assertTrue(warnings.get(0).getMessage().contains("[X]"),
                "the warning must list exactly the unbound chars: " + warnings.get(0).getMessage());
    }

    @Test
    void boundAndPaginationChars_doNotWarn() {
        Logger logger = Logger.getLogger(FirstRenderPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, BoundCharsView.class, ViewArguments.empty());
        } finally {
            logger.removeHandler(handler);
        }

        assertEquals(0, warningsOf(handler).size(),
                "component-bound and pagination-bound chars must not warn");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationClickRoutingTest"
```

Expected: FAIL — the suite compiles (every referenced type exists by Task 11) but 7 of 10 tests fail:
- `elementTypedHandler_runsOnMatchingClickType`, `elementUntypedHandler_runsOnlyWithoutTypedMatch`, `throwingElementHandler_forceCancelsAndSkipsRest` — expected click count 1 but was 0 (no fallback lookup, element handlers never found);
- `elementCancelFalse_overridesConfigDefaultTrue` — event cancelled (config default applies, override unseen);
- `elementCloseOnClick_isDeferredToEndOfTick` — expected TRANSITIONING but was ACTIVE;
- `staticComponentOnPaginationSlot_abortsOpenAsOpenFailed` — session still registered / `lastCloseReason` null;
- `unboundLayoutChar_warnsExactlyOncePerViewClass` — expected 1 warning but was 0.

`hiddenElement_clickIsComponentLess`, `frameSlotClick_isComponentLess` and `boundAndPaginationChars_doNotWarn` already pass — they are guards pinning behavior that must not regress once the fallback lands.

- [ ] **Step 3: Write the implementation**

**(a) `ClickRoutingPhase.java`** — single change inside `route(...)`: the component-resolution site gains the pagination fallback. Add one import:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
```

Replace the resolution statement (currently `ComponentInstance component = bottom ? null : session.components().componentAt(event.getRawSlot());`) with:

```java
        ComponentInstance component = bottom ? null
                : session.components().componentAt(event.getRawSlot());
        if (!bottom && component == null) {
            // pagination page elements live in the per-token bindings, not the static table
            component = PaginationBindings.componentAt(session, event.getRawSlot());
        }
```

Everything downstream is untouched: the `SlotClickContextImpl` pre-cancel construction, the hidden-component degrade (`isVisible` check), `dispatch(...)` (per-type handler → untyped default → view-level `onClick`, throw force-cancel), the safety floor, and `queuePostActions` all receive element components exactly like static ones.

**(b) `RenderContextImpl.java`** — track the chars bound through `layoutSlot(char)`. Add imports (the file already imports `java.util.ArrayList` and `java.util.List`):

```java
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
```

Add a field next to `pending`:

```java
    private final Set<Character> boundLayoutChars = new LinkedHashSet<>();
```

Replace `layoutSlot(char)` with (only the `boundLayoutChars.add` line and its comment are new — both existing validation throws keep their exact message strings):

```java
    @Override
    public @NotNull ItemComponentBuilder layoutSlot(char character) {
        ResolvedLayout layout = session().layout();
        if (layout == null) {
            throw new ViewConfigurationException("no layout is defined for " + view().getClass().getName()
                    + "; layoutSlot('" + character + "') requires a config layout");
        }
        if (!layout.hasChar(character)) {
            throw new ViewConfigurationException("layout character '" + character
                    + "' is not present in the layout of " + view().getClass().getName());
        }
        // successful bindings feed the first-render unbound-layout-char warning
        boundLayoutChars.add(character);
        return register(layout.slotsOf(character));
    }
```

Add the accessor after `materializeAll()` (public, not package-private — see the visibility deviation note at the top of this task; `FirstRenderPhase` lives in another package):

```java
    /**
     * Returns the layout characters successfully bound through {@link #layoutSlot(char)}
     * during this render, in declaration order; the first-render phase subtracts them when
     * warning about layout characters bound to neither a component nor pagination.
     *
     * @return an unmodifiable view of the bound layout characters
     */
    public @NotNull Set<Character> boundLayoutChars() {
        return Collections.unmodifiableSet(boundLayoutChars);
    }
```

**(c) `FirstRenderPhase.java`** — overlap validation inside the existing abort path plus the once-per-view-class warning. Ensure these imports are present (Task 11 already added `PaginationBinding`/`PaginationBindings` for the paintAll extension — add only the missing ones):

```java
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBinding;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationBindings;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.pagination.PaginationSpec;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
```

Add a static field below `LOGGER`:

```java
    // warn once per view class per classloader: a full server reload re-creates the plugin
    // classloader and warns again; re-registering views inside the same JVM does not
    private static final Set<Class<?>> UNBOUND_CHAR_WARNED = ConcurrentHashMap.newKeySet();
```

`firstRender(...)` gains exactly two lines (marked NEW). The catch body shows the post-Task-10 `OpenFailureHandler` form — if the file's catch body differs textually, keep it as Task 10 left it; this task only adds the marked lines:

```java
    public void firstRender(@NotNull ViewSession session) {
        View view = session.registered().instance();
        RenderContextImpl renderContext = new RenderContextImpl(session, engine);
        try {
            HandlerInvoker.invoke(HandlerInvoker.ON_FIRST_RENDER, view, renderContext);
            renderContext.materializeAll();
            validatePaginationOverlap(session);                                   // NEW
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onFirstRender failed for view " + view.getClass().getName()
                    + "; aborting the open", ex);
            OpenFailureHandler.abort(engine, sessions, session);
            return;
        }

        warnUnboundLayoutChars(session, renderContext);                           // NEW

        paintAll(session, renderContext);

        sessions.register(session);
        session.player().openInventory(session.inventory());
        session.status(ViewSession.Status.ACTIVE);
        startScheduledUpdates(session);
    }
```

Add the two private methods below `paintAll(...)` (note: `paintAll` itself keeps the binding-repaint loop Task 11 added — untouched here):

```java
    // overlap validation (§5.3/§6): a slot cannot be both statically bound and a pagination
    // target; runs inside the try so the failure flows into the OPEN_FAILED abort path.
    // bindings have no element components yet at this point (the first fill happens in
    // paintAll), so the check uses the binding's resolved target slots
    private void validatePaginationOverlap(ViewSession session) {
        for (PaginationBinding binding : PaginationBindings.of(session)) {
            for (int slot : binding.targetSlots()) {
                if (session.components().componentAt(slot) != null) {
                    throw new ViewConfigurationException(
                            "slot " + slot + " is bound to both a component and pagination");
                }
            }
        }
    }

    // unbound-layout-char warning (§5.3): chars present in the effective layout but bound by
    // neither a layoutSlot(...) declaration nor a LAYOUT_CHAR pagination target
    private void warnUnboundLayoutChars(ViewSession session, RenderContextImpl renderContext) {
        Set<Character> unbound = new LinkedHashSet<>();
        for (String row : session.effectiveConfig().layout()) {
            for (int column = 0; column < row.length(); column++) {
                char character = row.charAt(column);
                if (character != ' ') {
                    unbound.add(character);
                }
            }
        }
        if (unbound.isEmpty()) {
            return;
        }
        unbound.removeAll(renderContext.boundLayoutChars());
        for (StateToken token : session.registered().instance().tokenTable().tokens()) {
            if (!(token instanceof PaginationImpl)) {
                continue;
            }
            PaginationSpec<?> spec = ((PaginationImpl<?>) token).spec();
            if (spec.target() == PaginationSpec.Target.LAYOUT_CHAR) {
                unbound.remove(spec.layoutChar());
            }
        }
        if (unbound.isEmpty()) {
            return;
        }
        Class<?> viewClass = session.registered().type();
        if (UNBOUND_CHAR_WARNED.add(viewClass)) {
            LOGGER.warning("view " + viewClass.getName() + " declares layout chars " + unbound
                    + " that are bound to neither a component nor pagination");
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationClickRoutingTest"
```

Expected: PASS (all 10 tests).

- [ ] **Step 5: Full module check**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS — in particular `ClickRoutingTest` (static components and the bottom/hidden/floor policies are untouched by the fallback) and the existing first-render suites (the warning only fires for views whose layouts contain unbound chars; no existing fixture has any).

- [ ] **Step 6: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/ClickRoutingPhase.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/RenderContextImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationClickRoutingTest.java
git commit -m "feat(inventory-api): route clicks to pagination elements and validate layout bindings" -m "Component resolution falls back to pagination element components so element handlers, cancellation overrides and post-actions behave exactly like static ones. First render now rejects slots bound to both a component and pagination (OPEN_FAILED abort) and warns once per view class about layout chars bound to neither."
```

---

### Task 13: Shared timeout scheduler shutdown on disable

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSource.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/InventoryApiModule.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java (modified — tests appended)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/InventoryApiModuleDisableTest.java (created)

- [ ] **Step 1: Write the failing scheduler tests**

Append to `AsyncPageSourceTest` (same package as `AsyncPageSource`, so the existing fixtures apply; the new tests deliberately use the public constructor — no test-scheduler seam — so they exercise the real shared scheduler). Add two imports to the file's import block:

```java
import java.time.Duration;
```

and to the static imports:

```java
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
```

Append these two test methods after `expiredEntry_missesAndRefetches()` (before the `failedFuture` helper). They reuse the file's existing `request(int)` helper (Task 2 already migrated it to the slim `PageRequest`):

```java
    @Test
    void shutdownSharedTimeoutScheduler_lazilyRecreatesForNewTimeouts() throws Exception {
        // force the shared scheduler into existence (no test-scheduler seam), then kill it
        AsyncPageSource<Integer> first = new AsyncPageSource<>(req -> new CompletableFuture<>(),
                null, Duration.ofMillis(30), null, 128, SettleDispatcher.inline());
        CountDownLatch firstSettled = new CountDownLatch(1);
        first.request(request(1), (result, error) -> firstSettled.countDown());
        assertTrue(firstSettled.await(2, TimeUnit.SECONDS),
                "the shared scheduler must fire the priming timeout");

        AsyncPageSource.shutdownSharedTimeoutScheduler();

        CountDownLatch settled = new CountDownLatch(1);
        AtomicReference<Throwable> settledError = new AtomicReference<>();
        AsyncPageSource<Integer> source = new AsyncPageSource<>(req -> new CompletableFuture<>(),
                null, Duration.ofMillis(30), null, 128, SettleDispatcher.inline());
        source.request(request(1), (result, error) -> {
            settledError.set(error);
            settled.countDown();
        });

        assertTrue(settled.await(2, TimeUnit.SECONDS),
                "lazy init must recreate the shared scheduler after shutdown");
        assertInstanceOf(TimeoutException.class, settledError.get());
    }

    @Test
    void shutdownSharedTimeoutScheduler_isIdempotent() {
        assertDoesNotThrow(() -> {
            AsyncPageSource.shutdownSharedTimeoutScheduler();
            AsyncPageSource.shutdownSharedTimeoutScheduler();
        });
    }
```

(`CountDownLatch`, `TimeUnit`, `TimeoutException`, `AtomicReference`, `CompletableFuture`, `assertTrue` and `assertInstanceOf` are already imported by the existing tests.)

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell, repo root):

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=AsyncPageSourceTest"
```

Expected: FAIL — compilation error: `cannot find symbol: method shutdownSharedTimeoutScheduler()` in `AsyncPageSource`.

- [ ] **Step 3: Write the AsyncPageSource implementation**

Two changes to `AsyncPageSource.java` and ZERO others — the rest of the file stays byte-for-byte untouched (the contract pins this).

Add one import at the top of the import block (the file currently has only `java.*` imports; `org.*` goes first per module convention):

```java
import org.jetbrains.annotations.ApiStatus;
```

Insert the method directly after the private `timeoutScheduler()` method:

```java
    /**
     * Shuts the shared timeout scheduler down and clears it so the next timeout-bearing
     * request lazily recreates a fresh one. Called from the inventory-api module disable
     * hook; before 3.0.0 the shared daemon thread outlived the plugin and pinned its
     * classloader across reloads. Safe to call repeatedly and when no scheduler was ever
     * created.
     */
    @ApiStatus.Internal
    public static void shutdownSharedTimeoutScheduler() {
        synchronized (AsyncPageSource.class) {
            ScheduledExecutorService shared = sharedTimeoutScheduler;
            if (shared != null) {
                shared.shutdownNow();
                sharedTimeoutScheduler = null;
            }
        }
    }
```

(The `synchronized (AsyncPageSource.class)` block pairs with the lazy-init double-checked lock in `timeoutScheduler()`, so a shutdown can never race a concurrent lazy creation into a leaked executor.)

- [ ] **Step 4: Run test to verify it passes**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=AsyncPageSourceTest"
```

Expected: PASS (the whole class, existing tests included — the `ManualScheduler`-seamed timeout tests never touch the shared scheduler).

- [ ] **Step 5: Write the failing module-hook test**

Create `InventoryApiModuleDisableTest.java`. The hook is invoked reflectively by the core context (`BeanLifecycleInvoker.invokeOnDisable` scans declared methods for `@OnDisable`), so the test asserts the reflective contract, then invokes the method directly — `onDisable` must not need Bukkit, DI-injected fields, or a prior `onInitialize`:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryApiModuleDisableTest {

    @Test
    void onDisable_isAPublicVoidOnDisableHook() throws Exception {
        Method method = InventoryApiModule.class.getDeclaredMethod("onDisable");

        assertTrue(Modifier.isPublic(method.getModifiers()),
                "the context invokes disable hooks reflectively; the method must be public");
        assertEquals(void.class, method.getReturnType());
        assertNotNull(method.getAnnotation(OnDisable.class), "the hook must carry @OnDisable");
    }

    @Test
    void onDisable_runsWithoutBootstrapAndIsRepeatable() {
        InventoryApiModule module = new InventoryApiModule();

        assertDoesNotThrow(module::onDisable);
        assertDoesNotThrow(module::onDisable);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=InventoryApiModuleDisableTest"
```

Expected: FAIL — compilation error: `cannot find symbol: method onDisable()` on `InventoryApiModule` (the method reference in the second test does not compile until the hook exists).

- [ ] **Step 7: Write the module-hook implementation**

Add two imports to `InventoryApiModule.java`:

```java
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
```

Add the method after `onInitialize(Context)`:

```java
    /**
     * Shuts the shared pagination timeout scheduler down when the host plugin disables.
     * Open sessions are already closed when this runs: {@code ViewListener.onPluginDisable}
     * reacts to Bukkit's {@code PluginDisableEvent}, which fires before the context destroys
     * its beans and invokes this hook, so no session can still be waiting on a timeout. The
     * scheduler is recreated lazily on the next timeout-bearing request.
     */
    @OnDisable
    public void onDisable() {
        AsyncPageSource.shutdownSharedTimeoutScheduler();
    }
```

- [ ] **Step 8: Run test to verify it passes**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=InventoryApiModuleDisableTest"
```

Expected: PASS (both tests).

- [ ] **Step 9: Full module check**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS — the shutdown only nulls a lazily recreated singleton, so async pagination suites running after the shutdown tests are unaffected.

- [ ] **Step 10: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSource.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/InventoryApiModule.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/InventoryApiModuleDisableTest.java
git commit -m "feat(inventory-api): shut the shared pagination timeout scheduler down on disable" -m "AsyncPageSource gains an internal shutdownSharedTimeoutScheduler() wired to a module @OnDisable hook, fixing the pre-3.0 classloader leak on reload; lazy init recreates the scheduler on next use."
```

---

### Task 14: test-plugin samples rewrite + end-to-end sample flows

**Files:**
- Create: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleScrollView.java
- Create: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalView.java
- Create: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternView.java
- Create: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleAsyncView.java
- Modify: test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationSampleFlowsTest.java

Context for this task (read before writing code):

- Task 5 deleted the four 2.x paged samples (`SamplePagedInventory`, `SampleNormalPagedInventory`, `SamplePatternPagedInventory`, `SampleAsyncPagedInventory`) and neutered `JoinListener` (its four block-place open branches, the `InventoryService` injection, and the four sample imports are gone; the class still compiles with its `UserService` field). This task recreates the four samples as v3 `@RegisterView` views (spec §12 ergonomics proof) and rewires `JoinListener` through the injected `ViewService`. The old samples demonstrated: scroll geometry over a `'O'` grid row with gold ingots and a glass-pane fallback; normal geometry over an explicit serpentine `ofSlots` fill order (right-to-left, bottom-to-top across three rows) with title updates on navigation; three letter-ordered patterns cycled per page with diamonds; and async loading with a simulated 1.5s database query, CLOCK loading items, an error callback, 10s request timeout, 15s cache TTL, and a loading/page indicator slot.
- The module end-to-end test (spec §14 item 10) drives four views of the SAME shapes through the real `ViewService` + `ViewListener` + engine. The test-plugin classes are NOT on the inventory-api module test classpath (test-plugin depends on inventory-api, not the reverse), so the test defines its own sample-shaped nested views — keep their geometry identical to the test-plugin samples so the e2e suite stays a faithful proof of the shipped sample shapes.
- This suite is a regression gate over Tasks 1–13, not TDD of new module code: no main-source file changes in this task, so the test is expected to PASS on first run. If it fails, an earlier task is broken — debug and fix THERE (`superpowers:systematic-debugging`); never bend this test to a broken engine.
- MockBukkit pins (pinned rule 14): `MockBukkit.unmock()` drains pending scheduler tasks, so tearDown cancels tasks first; settles marshalled through the Bukkit scheduler are driven with `server.getScheduler().performOneTick()`. The async test completes the supplier future on a background thread on purpose: `BukkitSettleDispatcher` then routes the settle through `runTask`, which is exactly the production path. tearDown also calls `AsyncPageSource.shutdownSharedTimeoutScheduler()` (added in Task 13) so the 10s timeout watchdog never outlives the mocked server.

- [ ] **Step 1: Write the module end-to-end sample-flows test**

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.listener.ViewListener;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end flows for the four sample shapes shipped in test-plugin (spec §12, §14 item 10):
 * scroll over a layout-char row, normal over an explicit serpentine fill order, cycled
 * patterns, and async with a loading indicator. The test-plugin classes are not on this
 * module's test classpath, so the same shapes are restated as nested views here — keep them
 * in sync with the test-plugin samples.
 */
class PaginationSampleFlowsTest {

    // 'O' slots of the async layout, row-major: rows 1-3, columns 1-7
    private static final int[] ASYNC_O_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    // explicit fill order of the normal sample: right-to-left, bottom-to-top across rows 3..1
    private static final int[] SERPENTINE_SLOTS = {
            34, 33, 32, 31, 30, 29, 28,
            25, 24, 23, 22, 21, 20, 19,
            16, 15, 14, 13, 12, 11, 10
    };

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private ViewService service;
    private ViewListener listener;
    private ScrollFlowView scrollView;
    private NormalFlowView normalView;
    private PatternFlowView patternView;
    private AsyncFlowView asyncView;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        ViewRegistry registry = new ViewRegistry();
        sessions = new SessionRegistry();
        engine = new ViewEngine(plugin, registry, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
                });
        service = new ViewService(engine, sessions);
        listener = new ViewListener(sessions, engine);
        scrollView = new ScrollFlowView();
        normalView = new NormalFlowView();
        patternView = new PatternFlowView();
        asyncView = new AsyncFlowView();
        registry.register(scrollView);
        registry.register(normalView);
        registry.register(patternView);
        registry.register(asyncView);
        AsyncFlowView.PENDING.set(null);
        AsyncFlowView.LAST_REQUEST.set(null);
        AsyncFlowView.ERRORS.clear();
        player = server.addPlayer("tester");
    }

    @AfterEach
    void tearDown() {
        // unmock() drains pending scheduler tasks — drop anything still queued first
        server.getScheduler().cancelTasks(plugin);
        // never let the shared 10s timeout watchdog outlive the mocked server
        AsyncPageSource.shutdownSharedTimeoutScheduler();
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private ViewContext context() {
        return service.contextOf(player).orElseThrow(IllegalStateException::new);
    }

    private InventoryClickEvent click(int rawSlot) {
        Inventory top = session().inventory();
        Inventory bottom = player.getInventory();
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(bottom);
        when(invView.getPlayer()).thenReturn(player);
        when(invView.convertSlot(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(invView.getInventory(anyInt())).thenAnswer(inv -> {
            int raw = inv.getArgument(0);
            if (raw < 0) {
                return null;
            }
            return raw < top.getSize() ? top : bottom;
        });
        return new InventoryClickEvent(invView, InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private static Material typeAt(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        return item == null ? Material.AIR : item.getType();
    }

    private static void assertItem(Inventory inventory, int slot, Material type, int amount) {
        ItemStack item = inventory.getItem(slot);
        assertNotNull(item, "slot " + slot + " should hold an item");
        assertEquals(type, item.getType(), "slot " + slot + " material");
        assertEquals(amount, item.getAmount(), "slot " + slot + " amount");
    }

    private static void assertSlotEmpty(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        assertTrue(item == null || item.getType() == Material.AIR, "slot " + slot + " should be empty");
    }

    private static List<Integer> numbersUpTo(int max) {
        List<Integer> numbers = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            numbers.add(i);
        }
        return numbers;
    }

    @Test
    void scrollFlow_openNavigateClampAndClose() {
        service.open(player, ScrollFlowView.class);
        assertEquals(ViewSession.Status.ACTIVE, session().status());
        Inventory inventory = session().inventory();

        // first frame: items 1..5 in the five 'O' slots; back hidden, next shown (46 pages)
        for (int i = 0; i < 5; i++) {
            assertItem(inventory, 29 + i, Material.GOLD_INGOT, i + 1);
        }
        assertSlotEmpty(inventory, 27);
        assertEquals(Material.ARROW, typeAt(inventory, 35));

        // next arrow: deny-by-default cancellation + the window slides ONE element
        InventoryClickEvent next = click(35);
        listener.onClick(next);
        assertTrue(next.isCancelled(), "nav arrow clicks are deny-by-default cancelled");
        for (int i = 0; i < 5; i++) {
            assertItem(inventory, 29 + i, Material.GOLD_INGOT, i + 2);
        }
        assertEquals(Material.ARROW, typeAt(inventory, 27), "back arrow appears on page 2");

        // switchTo clamps exactly like 2.x changePage: upper to totalPages, lower to 1
        ViewContext context = context();
        scrollView.numbers.switchTo(context, 999);
        assertEquals(46, scrollView.numbers.currentPage(context));
        for (int i = 0; i < 5; i++) {
            assertItem(inventory, 29 + i, Material.GOLD_INGOT, 46 + i);
        }
        assertSlotEmpty(inventory, 35);
        assertEquals(Material.ARROW, typeAt(inventory, 27));

        scrollView.numbers.switchTo(context, -3);
        assertEquals(1, scrollView.numbers.currentPage(context));
        assertItem(inventory, 29, Material.GOLD_INGOT, 1);
        assertSlotEmpty(inventory, 27);

        service.close(player);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void normalFlow_serpentineFillOrderAndFallbackTail() {
        service.open(player, NormalFlowView.class);
        Inventory inventory = session().inventory();

        // page 1 fills the explicit serpentine order exactly
        for (int i = 0; i < SERPENTINE_SLOTS.length; i++) {
            assertItem(inventory, SERPENTINE_SLOTS[i], Material.EMERALD, i + 1);
        }
        assertSlotEmpty(inventory, 27);
        assertEquals(Material.ARROW, typeAt(inventory, 35));

        // page 2 of 3: items 22..42
        listener.onClick(click(35));
        assertItem(inventory, 34, Material.EMERALD, 22);
        assertItem(inventory, 10, Material.EMERALD, 42);
        assertEquals(Material.ARROW, typeAt(inventory, 27));

        // page 3 of 3: items 43..50, then the fallback pane fills the tail
        listener.onClick(click(35));
        assertItem(inventory, 34, Material.EMERALD, 43);
        assertItem(inventory, 25, Material.EMERALD, 50);
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, typeAt(inventory, 24));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, typeAt(inventory, 10));
        assertSlotEmpty(inventory, 35);

        service.close(player);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void patternFlow_cyclesPatternsAndClearsStaleSlots() {
        service.open(player, PatternFlowView.class);
        Inventory inventory = session().inventory();

        // page 1 — clockwise ring, letters A..L: A=2..E=6, F=15, G=24..K=20, L=11
        assertItem(inventory, 2, Material.DIAMOND, 1);
        assertItem(inventory, 6, Material.DIAMOND, 5);
        assertItem(inventory, 15, Material.DIAMOND, 6);
        assertItem(inventory, 24, Material.DIAMOND, 7);
        assertItem(inventory, 20, Material.DIAMOND, 11);
        assertItem(inventory, 11, Material.DIAMOND, 12);
        assertSlotEmpty(inventory, 27);
        assertEquals(Material.ARROW, typeAt(inventory, 35));

        // page 2 — X pattern (A=2, B=6, C=13, D=20, E=24) with items 13..17; ring-only slots clear
        InventoryClickEvent next = click(35);
        listener.onClick(next);
        assertTrue(next.isCancelled(), "nav arrow clicks are deny-by-default cancelled");
        assertItem(inventory, 2, Material.DIAMOND, 13);
        assertItem(inventory, 6, Material.DIAMOND, 14);
        assertItem(inventory, 13, Material.DIAMOND, 15);
        assertItem(inventory, 20, Material.DIAMOND, 16);
        assertItem(inventory, 24, Material.DIAMOND, 17);
        assertSlotEmpty(inventory, 3);
        assertSlotEmpty(inventory, 15);
        assertSlotEmpty(inventory, 11);

        // page 3 — serpentine block (A=3,B=4,C=5,D=14,E=13,F=12,G=21,H=22,I=23) with items 18..26
        listener.onClick(click(35));
        assertItem(inventory, 3, Material.DIAMOND, 18);
        assertItem(inventory, 5, Material.DIAMOND, 20);
        assertItem(inventory, 14, Material.DIAMOND, 21);
        assertItem(inventory, 13, Material.DIAMOND, 22);
        assertItem(inventory, 12, Material.DIAMOND, 23);
        assertItem(inventory, 22, Material.DIAMOND, 25);
        assertItem(inventory, 23, Material.DIAMOND, 26);
        assertSlotEmpty(inventory, 2);
        assertSlotEmpty(inventory, 6);

        service.close(player);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void asyncFlow_loadingFrameThenSettleRepaintsAreaAndFlipsIndicator() throws InterruptedException {
        service.open(player, AsyncFlowView.class);
        Inventory inventory = session().inventory();
        ViewContext context = context();

        // loading frame: every page slot shows the loading item; indicator reads loading/page 1;
        // both arrows hidden (totals unknown → 1 page)
        for (int slot : ASYNC_O_SLOTS) {
            assertEquals(Material.CLOCK, typeAt(inventory, slot), "slot " + slot + " shows the loading item");
        }
        assertItem(inventory, 49, Material.CLOCK, 1);
        assertSlotEmpty(inventory, 45);
        assertSlotEmpty(inventory, 53);
        assertTrue(asyncView.numbers.isLoading(context));

        CompletableFuture<PageResult<Integer>> pending = AsyncFlowView.PENDING.get();
        assertNotNull(pending, "open dispatched the initial page load");
        PageRequest request = AsyncFlowView.LAST_REQUEST.get();
        assertEquals(21, request.getPageSize());
        assertEquals(0, request.getOffset());
        assertEquals(player.getUniqueId(), request.playerId());

        // complete off-main on purpose: the settle must marshal back through the Bukkit scheduler
        List<Integer> items = numbersUpTo(21);
        Thread completer = new Thread(() -> pending.complete(PageResult.of(items, 100)));
        completer.start();
        completer.join();
        server.getScheduler().performOneTick();

        // settled frame: emeralds 1..21, indicator flips to PAPER page 1, next arrow appears (5 pages)
        for (int i = 0; i < ASYNC_O_SLOTS.length; i++) {
            assertItem(inventory, ASYNC_O_SLOTS[i], Material.EMERALD, i + 1);
        }
        assertItem(inventory, 49, Material.PAPER, 1);
        assertEquals(Material.ARROW, typeAt(inventory, 53), "next arrow appears once totals are known");
        assertSlotEmpty(inventory, 45);
        assertFalse(asyncView.numbers.isLoading(context));
        assertEquals(5, asyncView.numbers.totalPages(context));
        assertEquals(100, asyncView.numbers.totalElements(context));
        assertTrue(AsyncFlowView.ERRORS.isEmpty(), "no error callback fired");

        // click navigation: advance repaints the loading frame inline (the click's dispatch
        // requests page 2 and the settle pass paints CLOCKs), the off-main settle then
        // repaints the area with the second page
        InventoryClickEvent next = click(53);
        listener.onClick(next);
        assertTrue(next.isCancelled(), "nav arrow clicks are deny-by-default cancelled");
        assertTrue(asyncView.numbers.isLoading(context));
        for (int slot : ASYNC_O_SLOTS) {
            assertEquals(Material.CLOCK, typeAt(inventory, slot),
                    "slot " + slot + " shows the loading frame while page 2 loads");
        }
        assertItem(inventory, 49, Material.CLOCK, 2);

        CompletableFuture<PageResult<Integer>> pageTwo = AsyncFlowView.PENDING.get();
        assertNotSame(pending, pageTwo, "advance dispatched a fresh page-2 load");
        assertEquals(21, AsyncFlowView.LAST_REQUEST.get().getOffset(), "(2 - 1) * 21 layout slots");
        List<Integer> secondItems = new ArrayList<>();
        for (int value = 22; value <= 42; value++) {
            secondItems.add(value);
        }
        Thread secondCompleter = new Thread(() -> pageTwo.complete(PageResult.of(secondItems, 100)));
        secondCompleter.start();
        secondCompleter.join();
        server.getScheduler().performOneTick();

        for (int i = 0; i < ASYNC_O_SLOTS.length; i++) {
            assertItem(inventory, ASYNC_O_SLOTS[i], Material.EMERALD, 22 + i);
        }
        assertItem(inventory, 49, Material.PAPER, 2);
        assertEquals(Material.ARROW, typeAt(inventory, 45), "back arrow appears on page 2");
        assertEquals(Material.ARROW, typeAt(inventory, 53), "next arrow stays: 3 <= 5 pages");
        assertTrue(AsyncFlowView.ERRORS.isEmpty(), "no error callback fired across navigation");

        service.close(player);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    public static final class ScrollFlowView extends View {
        final Pagination<Integer> numbers = paginate(numbersUpTo(50))
                .scroll()
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.GOLD_INGOT, value)))
                .fallbackItem(ctx -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aScroll Flow")
                    .layout("         ",
                            "         ",
                            "         ",
                            "< OOOOO >",
                            "         ",
                            "         ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.layoutSlot('<', new ItemStack(Material.ARROW))
                    .displayIf(numbers::canBack)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::back);
            render.layoutSlot('>', new ItemStack(Material.ARROW))
                    .displayIf(numbers::canAdvance)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::advance);
        }
    }

    public static final class NormalFlowView extends View {
        final Pagination<Integer> numbers = paginate(numbersUpTo(50))
                .layout(Layout.ofSlots(
                        34, 33, 32, 31, 30, 29, 28,
                        25, 24, 23, 22, 21, 20, 19,
                        16, 15, 14, 13, 12, 11, 10))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.EMERALD, value)))
                .fallbackItem(ctx -> new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aNormal Flow")
                    .rows(6);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(27, new ItemStack(Material.ARROW))
                    .displayIf(numbers::canBack)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::back);
            render.slot(35, new ItemStack(Material.ARROW))
                    .displayIf(numbers::canAdvance)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::advance);
        }
    }

    public static final class PatternFlowView extends View {
        final Pagination<Integer> gems = paginate(numbersUpTo(50))
                .patterns(
                        Layout.ofGrid(
                                "  ABCDE  ",
                                "  L   F  ",
                                "  KJIHG  "),
                        Layout.ofGrid(
                                "  A   B  ",
                                "    C    ",
                                "  D   E  "),
                        Layout.ofGrid(
                                "   ABC   ",
                                "   FED   ",
                                "   GHI   "))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.DIAMOND, value)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aPattern Flow")
                    .rows(4);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(27, new ItemStack(Material.ARROW))
                    .displayIf(gems::canBack)
                    .updateOnStateChange(gems)
                    .onClick(gems::back);
            render.slot(35, new ItemStack(Material.ARROW))
                    .displayIf(gems::canAdvance)
                    .updateOnStateChange(gems)
                    .onClick(gems::advance);
        }
    }

    public static final class AsyncFlowView extends View {
        static final AtomicReference<CompletableFuture<PageResult<Integer>>> PENDING = new AtomicReference<>();
        static final AtomicReference<PageRequest> LAST_REQUEST = new AtomicReference<>();
        static final List<Throwable> ERRORS = new CopyOnWriteArrayList<>();

        final Pagination<Integer> numbers = paginateAsync(AsyncFlowView::loadPage)
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.EMERALD, value)))
                .loadingItem(ctx -> new ItemStack(Material.CLOCK))
                .onError((request, error) -> ERRORS.add(error))
                .requestTimeout(Duration.ofSeconds(10))
                .cacheTtl(Duration.ofSeconds(15))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&bAsync Flow")
                    .layout("         ",
                            " OOOOOOO ",
                            " OOOOOOO ",
                            " OOOOOOO ",
                            "         ",
                            "<   I   >");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.layoutSlot('<', new ItemStack(Material.ARROW))
                    .displayIf(numbers::canBack)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::back);
            render.layoutSlot('>', new ItemStack(Material.ARROW))
                    .displayIf(numbers::canAdvance)
                    .updateOnStateChange(numbers)
                    .onClick(numbers::advance);
            render.layoutSlot('I')
                    .item(ctx -> new ItemStack(numbers.isLoading(ctx) ? Material.CLOCK : Material.PAPER,
                            Math.max(1, numbers.currentPage(ctx))))
                    .updateOnStateChange(numbers);
        }

        private static CompletableFuture<PageResult<Integer>> loadPage(PageRequest request) {
            LAST_REQUEST.set(request);
            CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
            PENDING.set(future);
            return future;
        }
    }
}
```

- [ ] **Step 2: Run the end-to-end suite**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PaginationSampleFlowsTest"
```

Expected: PASS (4 tests). This task adds no module main-source code — the suite exercises machinery shipped by Tasks 1–13, so it must pass on first run. A failure here means an earlier task regressed: debug and fix the offending task (do NOT adapt this test to broken behavior).

- [ ] **Step 3: Create the scroll sample view**

Create `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleScrollView.java` — the spec §12 scroll sample, verbatim-adapted (only the `numbersUpTo` helper is added; the spec elides it):

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;

import java.util.ArrayList;
import java.util.List;

/**
 * Scroll-geometry pagination sample: 50 gold ingots slide one element per page through the
 * five {@code 'O'} layout slots. The navigation arrows are ordinary components bound to the
 * {@code '<'} and {@code '>'} layout characters; they repaint reactively because they watch
 * the pagination token — no scheduled update is needed.
 */
@RegisterView
public final class SampleScrollView extends View {

    private final Pagination<Integer> numbers = paginate(numbersUpTo(50))
            .scroll()
            .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.GOLD_INGOT, value)))
            .fallbackItem(ctx -> new ItemStack(Material.BLACK_STAINED_GLASS_PANE))
            .build();

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&aSample Scroll View")
                .layout("         ",
                        "         ",
                        "         ",
                        "< OOOOO >",
                        "         ",
                        "         ");
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.layoutSlot('<', new ItemStack(Material.ARROW))
                .displayIf(numbers::canBack)
                .updateOnStateChange(numbers)
                .onClick(numbers::back);
        render.layoutSlot('>', new ItemStack(Material.ARROW))
                .displayIf(numbers::canAdvance)
                .updateOnStateChange(numbers)
                .onClick(numbers::advance);
    }

    private static List<Integer> numbersUpTo(int max) {
        List<Integer> numbers = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}
```

- [ ] **Step 4: Create the normal sample view**

Create `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalView.java` — normal geometry over the same explicit serpentine fill order the deleted 2.x sample used, plus its fallback pane; the arrow handlers also demonstrate `ViewContext.updateTitle` like the old sample did:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;

import java.util.ArrayList;
import java.util.List;

/**
 * Normal-geometry pagination sample over an explicit {@link Layout#ofSlots} fill order:
 * pages of 21 emeralds fill right-to-left, bottom-to-top across rows 3..1, and the gray
 * fallback pane fills the tail of the last page. The arrows additionally update the title
 * with the settled page number, demonstrating {@code ViewContext.updateTitle}.
 */
@RegisterView
public final class SampleNormalView extends View {

    private final Pagination<Integer> numbers = paginate(numbersUpTo(50))
            .layout(Layout.ofSlots(
                    34, 33, 32, 31, 30, 29, 28,
                    25, 24, 23, 22, 21, 20, 19,
                    16, 15, 14, 13, 12, 11, 10))
            .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.EMERALD, value)))
            .fallbackItem(ctx -> new ItemStack(Material.GRAY_STAINED_GLASS_PANE))
            .build();

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&aSample Normal View")
                .rows(6);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.slot(27, new ItemStack(Material.ARROW))
                .displayIf(numbers::canBack)
                .updateOnStateChange(numbers)
                .onClick(ctx -> {
                    numbers.back(ctx);
                    ctx.updateTitle("&ePage " + numbers.currentPage(ctx));
                });
        render.slot(35, new ItemStack(Material.ARROW))
                .displayIf(numbers::canAdvance)
                .updateOnStateChange(numbers)
                .onClick(ctx -> {
                    numbers.advance(ctx);
                    ctx.updateTitle("&ePage " + numbers.currentPage(ctx));
                });
    }

    private static List<Integer> numbersUpTo(int max) {
        List<Integer> numbers = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}
```

- [ ] **Step 5: Create the pattern sample view**

Create `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternView.java` — three cycled per-page patterns, adapting the deleted sample's letter-ordered spiral idea into three small 3-row figures (ring → X → serpentine block):

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;

import java.util.ArrayList;
import java.util.List;

/**
 * Pattern-geometry pagination sample: three per-page slot patterns are cycled — a clockwise
 * ring, an X and a serpentine block — with the letter order of each grid defining the fill
 * order within the page. Slots of the previous pattern that the next one does not reuse are
 * cleared automatically on every page change.
 */
@RegisterView
public final class SamplePatternView extends View {

    private final Pagination<Integer> gems = paginate(numbersUpTo(50))
            .patterns(
                    Layout.ofGrid(
                            "  ABCDE  ",
                            "  L   F  ",
                            "  KJIHG  "),
                    Layout.ofGrid(
                            "  A   B  ",
                            "    C    ",
                            "  D   E  "),
                    Layout.ofGrid(
                            "   ABC   ",
                            "   FED   ",
                            "   GHI   "))
            .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.DIAMOND, value)))
            .build();

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&aSample Pattern View")
                .rows(4);
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.slot(27, new ItemStack(Material.ARROW))
                .displayIf(gems::canBack)
                .updateOnStateChange(gems)
                .onClick(gems::back);
        render.slot(35, new ItemStack(Material.ARROW))
                .displayIf(gems::canAdvance)
                .updateOnStateChange(gems)
                .onClick(gems::advance);
    }

    private static List<Integer> numbersUpTo(int max) {
        List<Integer> numbers = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}
```

- [ ] **Step 6: Create the async sample view**

Create `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleAsyncView.java` — the spec §12 async sample, verbatim-adapted; `loadPage` keeps the deleted 2.x sample's simulated 1.5s database query over 50 elements:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Async pagination sample: every page is "fetched" with a simulated 1.5s database delay,
 * rendering clocks into the page area while loading. The {@code 'I'} indicator component
 * watches the pagination token, so it flips between a clock (loading) and the settled page
 * number reactively — the 2.x tick-poll workaround is gone.
 */
@RegisterView
public final class SampleAsyncView extends View {

    private static final Logger LOGGER = Logger.getLogger(SampleAsyncView.class.getName());
    private static final int TOTAL_ELEMENTS = 50;

    private final Pagination<Integer> numbers = paginateAsync(SampleAsyncView::loadPage)
            .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.EMERALD, value)))
            .loadingItem(ctx -> new ItemStack(Material.CLOCK))
            .onError((request, error) -> LOGGER.warning("page " + request.getPage() + " failed: " + error))
            .requestTimeout(Duration.ofSeconds(10))
            .cacheTtl(Duration.ofSeconds(15))
            .build();

    @Override
    protected void onInit(@NotNull ViewConfigBuilder config) {
        config.title("&bSample Async View")
                .layout("         ",
                        " OOOOOOO ",
                        " OOOOOOO ",
                        " OOOOOOO ",
                        "         ",
                        "<   I   >");
    }

    @Override
    protected void onFirstRender(@NotNull RenderContext render) {
        render.layoutSlot('<', new ItemStack(Material.ARROW))
                .displayIf(numbers::canBack)
                .updateOnStateChange(numbers)
                .onClick(numbers::back);
        render.layoutSlot('>', new ItemStack(Material.ARROW))
                .displayIf(numbers::canAdvance)
                .updateOnStateChange(numbers)
                .onClick(numbers::advance);
        render.layoutSlot('I')
                .item(ctx -> new ItemStack(numbers.isLoading(ctx) ? Material.CLOCK : Material.PAPER,
                        Math.max(1, numbers.currentPage(ctx))))
                .updateOnStateChange(numbers);
    }

    // simulated database query: pages are sliced from 1..TOTAL_ELEMENTS via offset/pageSize
    private static CompletableFuture<PageResult<Integer>> loadPage(PageRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<Integer> items = new ArrayList<>();
            int end = Math.min(request.getOffset() + request.getPageSize(), TOTAL_ELEMENTS);
            for (int i = request.getOffset(); i < end; i++) {
                items.add(i + 1);
            }
            return PageResult.of(items, TOTAL_ELEMENTS);
        });
    }
}
```

- [ ] **Step 7: Rewire JoinListener through ViewService**

Replace the ENTIRE content of `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java` with the file below (regardless of the exact post-Task-5 intermediate state). The four block-place branches return; `InventoryService` is replaced by the injected v3 `ViewService`; the `UserService` injection is kept (it predates the sample wiring); the long-dead commented-out bungee/people experiment blocks from 2.x are deliberately dropped:

```java
/* MIT license header — copy from annotation/RegisterView.java lines 1-22 */
package tech.guilhermekaua.spigotboot.testPlugin.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.SampleAsyncView;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.SampleNormalView;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.SamplePatternView;
import tech.guilhermekaua.spigotboot.testPlugin.inventory.SampleScrollView;
import tech.guilhermekaua.spigotboot.testPlugin.services.UserService;

@Component
@RequiredArgsConstructor
public class JoinListener implements Listener {
    private final UserService userService;
    private final ViewService viewService;

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        final Material blockType = event.getBlock().getType();
        final Player player = event.getPlayer();

        if (blockType == Material.DIAMOND_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening scroll pagination sample");
            viewService.open(player, SampleScrollView.class);
            return;
        }

        if (blockType == Material.EMERALD_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening normal pagination sample");
            viewService.open(player, SampleNormalView.class);
            return;
        }

        if (blockType == Material.GOLD_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening pattern pagination sample");
            viewService.open(player, SamplePatternView.class);
            return;
        }

        if (blockType == Material.NETHERITE_BLOCK) {
            player.sendMessage("[ApxPlugin] - opening async pagination sample");
            viewService.open(player, SampleAsyncView.class);
        }
    }
}
```

- [ ] **Step 8: Verify test-plugin compiles and packages**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl test-plugin -am package -B -DskipTests
```

Expected: BUILD SUCCESS (compiles the four new views and the rewired JoinListener against the new public pagination API).

- [ ] **Step 9: Full module check**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test -B
```

Expected: PASS (all inventory-api module tests green, including PaginationSampleFlowsTest).

- [ ] **Step 10: Commit**

```bash
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationSampleFlowsTest.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleScrollView.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalView.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternView.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleAsyncView.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/listener/JoinListener.java
git commit -m "feat(inventory-api): rewrite test-plugin pagination samples against the v3 view API" -m "Recreates the four paged samples deleted in the Task 5 switchover as @RegisterView views (scroll over a layout-char row, normal over an explicit serpentine fill order, three cycled patterns, async with a reactive loading indicator), rewires JoinListener's block-place opens through the injected ViewService, and adds PaginationSampleFlowsTest driving the same four shapes end to end through ViewService, ViewListener and the engine (spec section 12 ergonomics proof, section 14 item 10)."
```

---

### Task 15: Spec amendment + Plan-3 notes + full reactor green

**Files:**
- Modify: docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md
- Create: docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-3-notes.md

No code in this task. It closes reviewer backlog #2 as decided in this plan's scope note 3 (keep the synchronous-pass-per-call `update()` implementation, amend the spec), seeds the authoring notes for Plan 3, and proves the whole reactor green after the pagination switchover. Markdown files carry no license header (matching every existing doc).

- [ ] **Step 1: Amend the spec §5.4 `update()` comment**

In `docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md`, inside the §5.4 `ViewContext` code block, replace this exact line (currently line 226):

```java
    void update();                             // schedules a full update pass (coalesced, same tick)
```

with:

```java
    void update();                             // runs a full update pass immediately (synchronous; each call is its own pass)
```

- [ ] **Step 2: Append the decision record to §5.4's deferral paragraph**

Still in §5.4, the deferral paragraph (the prose paragraph after the "Phase validity" paragraph) currently ends with this exact sentence:

```
immediately — never deferred (prevents close→onClose→openView→REPLACED-close loops).
```

Append ONE sentence to the end of that paragraph (same paragraph, continuing the prose, wrapped at the file's ~90-column width), so the paragraph now ends:

```
immediately — never deferred (prevents close→onClose→openView→REPLACED-close loops).
Decision record (Plan 2, reviewer backlog #2): `update()` is deliberately synchronous —
each call runs one full pass immediately and nothing coalesces same-tick calls, which is
safe because settle-driven passes are token-scoped and never multiply full passes — the
implementation truth was kept and the `update()` comment above amended to match.
```

- [ ] **Step 2b: Amend the §10 behavior-map row for the pre-init navigation reorder**

In §10's behavior preservation map, the "Deferred pre-init navigation" row currently reads:

```
| Deferred pre-init navigation | `changePageInternal` viewer-null branch | same branch, keyed on unbound host |
```

Replace its third cell so the row reads:

```
| Deferred pre-init navigation | `changePageInternal` viewer-null branch | same branch, keyed on unbound host; the record-only check moved AHEAD of the totals-known clamp (pre-bind `itemPageLimit` is 0 — the 2.x order divides by zero for a non-empty eager source; observably equivalent: no in-flight request exists pre-bind and overshoot is corrected by the settle's downward re-clamp) |
```

This records the one deliberate reorder Plan 2's pinned rule 2 made inside the preserved algorithm (scope note 5 of the plan).

- [ ] **Step 3: Create the Plan-3 authoring notes**

Create `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-3-notes.md` with exactly this content:

```markdown
# inventory-api 3.0.0 — Plan 3 Authoring Notes

Input for whoever authors Plan 3 (final 2.x deletion, migration table, attribution,
3.0.0 release). Plan 2 (pagination switchover) is complete on `feat/inventory-api`
(Tasks 1–15, module suite and full reactor green). The spec stays authoritative for
Plan 3's content; this file carries the Plan-2 outcomes and the deferred backlog that
the spec and the Plan 2 doc do not.

## Authoritative sources

- Spec: `docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md` —
  Plan 3 implements §13 (migration & attribution, version 3.0.0) and deletes the
  remaining 2.x public surface listed in §4 ("Deleted from the public surface").
  §5.4's `update()` comment was amended in Plan 2 Task 15 (reviewer backlog #2).
- Plan 2 doc: `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-2-pagination.md`
  — Shared Type Contracts for the entire pagination surface; Plan 3 must not break
  them. Follow the same authoring process (contracts pinned centrally, task bodies
  drafted against them, subagent execution with two-stage review per task).
- Plan 2 notes: `docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-2-notes.md` —
  the original Plan-1 outcomes and reviewer backlog; resolution status is recorded
  below.

## Plan-2 outcomes Plan 3 builds on

- The new public pagination surface shipped: `pagination/Pagination` (the reactive
  token; implements `StateToken`, so `updateOnStateChange(pagination)` and settle
  repaints work), `pagination/PaginationBuilder`, `pagination/PaginationItemRenderer`,
  and the four `View.paginate*` factories (spec §5.2/§5.7).
- The 2.x public pagination cluster is ALREADY DELETED (Plan 2 Task 5, a conscious
  deviation from the locked decisions forced by the `pagination.Pagination` FQN
  collision): the old `Pagination` interface, `pagination/impl/{
  AbstractPageSourcePagination, NormalPagination, ScrollPagination, PatternPagination}`,
  `pagination/builder/{NormalPaginationBuilder, ScrollPaginationBuilder,
  PatternPaginationBuilder, AsyncPaginationOptions}`, their test classes, and
  `InventoryEditor.fillPage`/`InventoryEditorImpl.fillPage`. Plan 3 deletes the REST of
  2.x — do not double-count the pagination cluster.
- test-plugin samples are ALREADY on the v3 API (Plan 2 Task 14):
  `inventory/{SampleScrollView, SampleNormalView, SamplePatternView, SampleAsyncView}`
  plus a `JoinListener` that opens them through the injected `ViewService`. Plan 3's
  sample work is JoinListener-adjacent cleanup only — verifying nothing in test-plugin
  still references a 2.x type once the final deletions land. Sample work REMAINING for
  Plan 3 (spec §12, last paragraph): the navigation pair (`ShopView` ↔ confirm view via
  `openOnClick`/`initialState`) and the `SharedState` leaderboard sample — the four PAGED
  samples are done; these two were never part of the pagination switchover.
- Internal pagination architecture (all `@ApiStatus.Internal`): the relocated 2.x
  engine lives in `internal/pagination/engine/` — `Paginator` (the renamed ex-2.x
  `Pagination` interface), `AbstractPageSourcePagination` and `Normal/Scroll/Pattern`
  subclasses with geometry math and the navigation/settle/rollback algorithms verbatim
  — talking to the world only through the `internal/pagination/PaginationHost` seam.
  `PaginationBinding` (one per session+token, stored in the token's `StateStore` slot)
  implements the host and owns slot mapping, element components, pre-init pending
  navigation and the per-context source; `PaginationSpec`/`PaginationSourceSpec` carry
  the frozen declaration; `PaginationInitPhase` builds engines between open and first
  render (§7 step 7); settles flow `host.requestRender()` →
  `ViewEngine.paginationSettle(session, tokenId)` → scoped PAGINATION_SETTLE update
  pass → `flushDirty`. `FlushCoordinator` (extracted from `ViewEngine`, backlog #1)
  owns the flush cascade and shared-flush coalescing.
- `PageRequest` was REWRITTEN slim (`page`/`pageSize`/`offset` plus
  `@Nullable UUID playerId()` and `@Nullable Plugin plugin()`; `Viewer` gone) and
  `BukkitSettleDispatcher` was REWRITTEN (the `tickAsync` consultation is deleted; the
  plugin is read from the request; null plugin or primary thread → inline). Spec §5.8
  documents both.
- `AsyncPageSource` gained `@ApiStatus.Internal shutdownSharedTimeoutScheduler()`,
  invoked from `InventoryApiModule`'s `@OnDisable` hook (fixes the preexisting
  classloader leak on reload).
- Reviewer backlog resolved in Plan 2: #1 (`FlushCoordinator` extraction), #2
  (`update()` coalescing — implementation truth kept, spec §5.4 amended), #6
  (`ContextStateAccess.storeFor` consolidation across the state impls).

## Deferred backlog carried into Plan 3 (one-liners from the Plan-2 notes)

- #5: `FirstRenderPhase` ignores the `player.openInventory` result: another plugin
  cancelling `InventoryOpenEvent` yields an ACTIVE ghost session until quit/replace
  (2.x had the same flaw). Candidate fix in Plan 3.
- #7: `ViewRegistry` uses `LinkedHashMap` (boot-time writes only; 2.x used
  `ConcurrentHashMap`) — optional hardening.
- #9: OPEN_FAILED with no previous session calls `player.closeInventory()` and may
  close an unrelated vanilla screen — acceptable on the exceptional path; could be
  tightened by checking what is actually on screen.
- #10: State written in `onOpen` stays dirty until the first entry point (one spurious
  STATE_CHANGE pass on first click) — cosmetic.

Items #3, #4 and #8 of the Plan-2 notes were note-only observations and remain
informational.

## Plan-3 scope reminders (spec §13)

- Delete the remaining 2.x public surface (spec §4 list): packages `editor/`, `event/`,
  `inventory/`, `item/`, `viewer/`, `registry/`, `schedule/`, `listener/`;
  `CustomInventory(Impl)`, `InventoryService`, `InventoryItem`, `InventoryEditor`,
  `Viewer`, `ViewerPropertyMap`, `@Inventory`, `InventoryLayout` (with `GridLayout`,
  the 2.x `OrderedSlotsLayout` and `InventorySlot`; the v3 replacement `Layout` already
  exists), `InventorySettings`, `InventoryConfiguration` — plus their tests. The
  pagination cluster and `InventoryEditor.fillPage` are already gone (Plan 2).
- Migration table (2.x → 3.0) ships with the module docs:
  `@Inventory`→`@RegisterView`; `CustomInventoryImpl.configure`→`onInit`;
  `configureViewer`/`firstOpen`→`onOpen`/`onFirstRender`;
  `configureInventory`+`update`→`onFirstRender`+reactive state;
  `InventoryService.open`→`ViewService.open` (throws);
  `InventoryItem.of(...).callback`→`ItemComponentBuilder.onClick`;
  `ViewerPropertyMap`→state tokens;
  `*PaginationBuilder`+`init`/`apply`→`paginate*(...)` factories;
  `InventoryLayout`→`Layout` (back/next slots removed); and the documented removals
  `Pagination.getPageOfIndex` (no public replacement; survives internally on
  `Paginator` for geometry regression value) and `Pagination.setSource` (use a lazy
  source + `refresh(ctx)`, which re-invokes the source function).
- Attribution: README section and NOTICE entry — "API design inspired by
  devnatan/inventory-framework (MIT)" — plus a note in the root `package-info.java`.
  Implementation is clean-room; the engine is original 2.x code.
- Version bump to 3.0.0 across the reactor; PR targets `dev`.
- test-plugin/JoinListener final state: already on the v3 API after Plan 2; Plan 3 only
  re-verifies `mvnw.cmd -pl test-plugin -am package` after each deletion sweep.
- Build rule (memory): JDK 21 for every mvnw invocation
  (`$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'`); the shell-default JDK 25
  crashes Lombok.
```

- [ ] **Step 4: Full reactor verification**

Run from the repo root:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd clean test -B
```

Expected: `BUILD SUCCESS` with every module green (this is the plan's exit gate: core, commands, config, data, utils, platform-spigot, all inventory-api modules, the other adapters and test-plugin all SUCCESS; zero failures, zero errors).

REQUIRED: paste the `[INFO] Reactor Summary` block (every module line plus the final `BUILD SUCCESS` line) verbatim into the task report. Do not summarize it — the reviewer needs the raw lines.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md docs/superpowers/plans/2026-06-07-inventory-api-v3-plan-3-notes.md
git commit -m "docs(inventory-api): resolve the update() coalescing question and seed Plan-3 notes" -m "Amends spec section 5.4 to document ViewContext.update() as a synchronous per-call full pass with an inline decision record (reviewer backlog #2: implementation truth kept, spec amended), and creates the Plan-3 authoring notes recording the Plan-2 outcomes, the deferred backlog items #5/#7/#9/#10 and the spec section 13 scope reminders."
```

---

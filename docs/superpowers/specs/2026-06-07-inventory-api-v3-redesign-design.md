# inventory-api 3.0.0 — API Redesign Design

Status: approved design, pending implementation plan.
Module: `modules/inventory-api` (artifact `spigot-boot-inventory-api`), version 3.0.0.
Date: 2026-06-07.

The public API of the inventory-api module is redesigned around views, typed per-phase
contexts, reactive state, and declarative pagination. The design is openly inspired by
[devnatan/inventory-framework](https://github.com/devnatan/inventory-framework) (MIT) —
concepts and ergonomics are adopted; no code or documentation text is copied (clean-room).
The existing 2.x page-source engine (async settle machinery, pagination geometries, layout
parsing, placeholder/title integrations) is preserved, not rewritten.

## 1. Goals

- Replace the template-method 2.x surface (`CustomInventoryImpl` hooks receiving
  `(Viewer, InventoryEditor)`) with typed per-phase contexts that expose only what is valid
  in each phase.
- Replace the stringly-typed `ViewerPropertyMap` with reactive, token-based state.
- Make pagination declarative (declared as a view field, rendered into layout-character
  slots, navigation via ordinary components) while preserving the 2.x engine semantics
  byte-for-byte where not explicitly changed.
- Keep spigot-boot's DI identity: views are discovered singletons, opened via an injected
  service.
- Keep 2.x's deny-by-default click protection; reject inventory-framework's opt-in model.

## 2. Locked decisions

| Decision | Choice |
|---|---|
| Compatibility | Clean break; 3.0.0; old public API deleted, no deprecation layer |
| State model | Full reactive state (tokens, per-context storage, watchers) |
| Entry point | DI-first: `@RegisterView` discovery + injected `ViewService` |
| Pagination | Declarative surface over the preserved `PageSource` engine; all three geometries |
| Naming | Shared vocabulary with inventory-framework (`View`, `ViewConfig`, `RenderContext`, `SlotClickContext`, `State`) |
| Internals | Approach C: internal phase-handler chain; no public pipeline/interceptor API in 3.0.0 |
| Click cancellation | Deny-by-default, three-layer override model, non-negotiable safety floor |
| Per-ClickType cancellation | Handler-level only (`setCancelled` in `onClick`); no declarative per-type API in 3.0.0 |

## 3. Constraints

- Main sources compile at Java 8 (root pom `<source>1.8</source>`); tests at 17. No
  records/sealed types in the API; non-extensibility via `final`, package-private
  constructors, and `@ApiStatus.NonExtendable` / `@ApiStatus.Internal`.
- Module structure unchanged: `api`, `nms-api`, `nms`, `nms-1_8_R3` … `nms-1_19_R3`.
- Build with JDK 21 (Lombok 1.18.36 crashes under newer JDKs).
- Repo style: 4-space indent, `@NotNull`/`@Nullable` null-safety, full Javadoc on
  public/protected API, lowercase normal comments, no fully-qualified inline types, SOLID.

## 4. Package layout

All under `tech.guilhermekaua.spigotboot.inventoryapi`:

| Package | Public contents |
|---|---|
| *(root)* | `View` |
| `annotation` | `@RegisterView` |
| `service` | `ViewService`, `ViewArguments` |
| `config` | `ViewConfig`, `ViewConfigBuilder` |
| `context` | `ViewContext`, `OpenContext`, `RenderContext`, `UpdateContext`, `SlotClickContext`, `CloseContext`, `UpdateTrigger`, `CloseReason` |
| `state` | `StateToken`, `State`, `MutableState`, `SharedState` |
| `component` | `ItemComponentBuilder` |
| `pagination` | `Pagination`, `PaginationBuilder`, `PaginationItemRenderer` |
| `pagination.source` | `PageSource`, `PageRequest`, `PageResult`, `EagerPageSource`, `AsyncPageSource`, `AsyncPageSupplier`, `SettleDispatcher`, `BukkitSettleDispatcher`, `PaginationErrorCallback` (preserved engine) |
| `layout` | `Layout` |
| `placeholder` | `PlaceholderApplier`, `PapiPlaceholderApplier`, `NoopPlaceholderApplier` (preserved SPI) |
| `title` | `TitleUpdater` (preserved SPI) |
| `exception` | `UnknownViewException`, `ViewConfigurationException`, `StaleContextException` |
| `internal.*` | engine, phase handlers, session, state store, component table, painter, registries, listener, scheduler — `@ApiStatus.Internal`, excluded from the compatibility contract |

Deleted from the public surface: `editor/`, `event/`, `inventory/`, `item/`, `viewer/`,
`registry/`, `schedule/`, `listener/` packages; `CustomInventory(Impl)`,
`InventoryService`, `InventoryItem`, `InventoryEditor`, `Viewer`, `ViewerPropertyMap`,
`@Inventory`, `InventoryLayout` (renamed `Layout`), `InventorySettings`,
`InventoryConfiguration`.

**Annotation naming.** `@View` collides with the `View` base class import;
`@InventoryView` collides with `org.bukkit.inventory.InventoryView`. Chosen:
`@RegisterView`. The base context is named `ViewContext` (not `Context`) because
spigot-boot core already exposes a `Context` type.

## 5. Public API catalog

### 5.1 `@RegisterView` + `ViewService` + `ViewArguments`

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpigotBootDiscoveryCategory(value = DiscoveryCategories.INVENTORY,
        kind = SpigotBootDiscoveryCategory.Kind.ANNOTATION)
public @interface RegisterView {}
```

Discovery reuses the existing `DiscoveryCategories.INVENTORY` wiring. Boot-time guard: a
`@RegisterView`-annotated class that does not extend `View` logs SEVERE at boot (never a
silent drop).

```java
@Service
public final class ViewService {
    /**
     * @throws UnknownViewException if the view class is not registered
     * @throws IllegalStateException if called off the main thread
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> view);
    public void open(@NotNull Player player, @NotNull Class<? extends View> view,
                     @NotNull ViewArguments arguments);
    public void close(@NotNull Player player);                       // no-op without active context
    public @NotNull Optional<ViewContext> contextOf(@NotNull Player player);
}
```

`open` throws for unregistered views (2.x returned `null`). There is no
`ViewService.update(Player)` — use `contextOf(player).ifPresent(ViewContext::update)`.

```java
public final class ViewArguments {                                   // immutable, typed reads
    public static @NotNull ViewArguments empty();
    public static @NotNull ViewArguments of(@NotNull String key, @NotNull Object value);
    public static @NotNull ViewArguments of(@NotNull String k1, @NotNull Object v1,
                                            @NotNull String k2, @NotNull Object v2);
    public static @NotNull Builder builder();
    public <T> @Nullable T get(@NotNull String key, @NotNull Class<T> type);  // ClassCastException-free: throws IllegalArgumentException on type mismatch
    public <T> @NotNull T require(@NotNull String key, @NotNull Class<T> type); // throws IllegalArgumentException when absent
    public boolean has(@NotNull String key);
}
```

Use `builder()` for more than two key-value pairs; the `of(...)` overloads stop at two.

### 5.2 `View`

```java
public abstract class View {
    // lifecycle handlers — all optional overrides; threading per §8
    protected void onInit(@NotNull ViewConfigBuilder config) {}      // once per class at registration; config frozen after
    protected void onOpen(@NotNull OpenContext context) {}           // per open, before any container exists
    protected void onFirstRender(@NotNull RenderContext context) {}  // declare components; once per context
    protected void onUpdate(@NotNull UpdateContext context) {}
    protected void onClick(@NotNull SlotClickContext context) {}     // view-level fallback + bottom-inventory clicks (§6)
    protected void onClose(@NotNull CloseContext context) {}

    // state factories — legal ONLY in instance field initializers / the constructor
    // (they are instance methods, so static contexts cannot reach them by construction);
    // throw IllegalStateException after registration freezes the token table
    protected final <T> MutableState<T> mutableState(@Nullable T initialValue);
    protected final <T> MutableState<T> mutableState(@NotNull Function<ViewContext, T> initialValue);
    protected final <T> State<T> lazyState(@NotNull Function<ViewContext, T> computation);
    protected final <T> MutableState<T> initialState(@NotNull String key, @NotNull Class<T> type);
    protected final <T> SharedState<T> sharedState(@Nullable T initialValue);

    // pagination factories — source-first so T infers at the call site
    protected final <T> PaginationBuilder<T> paginate(@NotNull List<T> source);
    protected final <T> PaginationBuilder<T> paginate(@NotNull Function<ViewContext, List<T>> source);
    protected final <T> PaginationBuilder<T> paginateAsync(@NotNull AsyncPageSupplier<T> source);
    protected final <T> PaginationBuilder<T> paginateSource(@NotNull Function<ViewContext, PageSource<T>> factory);
}
```

Views are DI singletons constructed by the container (constructor injection works as in
2.x — `InventoryRegistry` already instantiates discovered classes through the dependency
manager). Contract: view fields hold only state/pagination tokens, injected collaborators,
and immutable configuration. All per-player values live in per-context storage and are
dropped when the context closes.

Factory semantics:

- `mutableState(T initialValue)` — the initial value object is shared by every context;
  it MUST be immutable (documented; the `Function` overload exists for mutable initials
  such as collections: `mutableState(ctx -> new ArrayList<>())`).
- `lazyState(fn)` — computed on first `get` per context on the main thread, stored
  thereafter.
- `initialState(key, type)` — bound from `ViewArguments` at open; the type is validated at
  the open site (step 2 of §7), so a wrong-typed argument fails at `open(...)`, not in a
  later click handler. The type check applies only when the key is present: an absent key
  means `get` returns null until `set` is called (use `ViewArguments.require` at the open
  site when an argument is mandatory).
- `sharedState(initial)` — one value per view singleton, shared by all viewers; see §5.5.
- `paginate(List)` — static eager source; the list is defensively copied into one shared
  immutable `EagerPageSource` (safe to share across contexts).
- `paginate(Function)` — lazy eager: the function runs once per context at pagination
  init; `Pagination.refresh(ctx)` re-invokes it (see §5.7).
- `paginateAsync(supplier)` — a fresh `AsyncPageSource` is constructed **per context** at
  pagination init. Sources are never shared across contexts: request-id supersede and the
  page cache are per-instance and have no player dimension.
- `paginateSource(factory)` — escape hatch; the factory runs once per context.

### 5.3 `ViewConfig` / `ViewConfigBuilder`

```java
public final class ViewConfigBuilder {
    public @NotNull ViewConfigBuilder title(@NotNull String title);        // legacy color codes; placeholders applied per player
    public @NotNull ViewConfigBuilder rows(int rows);                      // 1..6; optional when layout() present (inferred)
    public @NotNull ViewConfigBuilder layout(@NotNull String... rows);     // each row exactly 9 chars; ' ' = unnamed
    public @NotNull ViewConfigBuilder cancelOnClick(boolean cancel);       // default TRUE
    public @NotNull ViewConfigBuilder cancelOnDrag(boolean cancel);        // default TRUE
    public @NotNull ViewConfigBuilder scheduleUpdate(long intervalTicks);  // <= 0 disables (default)
    public @NotNull ViewConfigBuilder applyPlaceholders(boolean apply);    // default true when PAPI present
}

public final class ViewConfig { /* immutable getters; layout() returns an unmodifiable List<String> */ }
```

Validation at registration (fail fast at plugin enable, `ViewConfigurationException`):
missing title, rows outside 1–6, malformed layout rows (width ≠ 9), rows/layout mismatch
when both set, pagination `layoutChar` absent from the layout. A layout character that no
component or pagination binds by the end of `onFirstRender` is not an error: it logs one
WARNING per view class (reserved space may be intentional, but it is usually a typo).

There is no `scheduleUpdateAsync`/`tickAsync` in 3.0.0 (§11).

### 5.4 Context hierarchy

All contexts are `@ApiStatus.NonExtendable` interfaces implemented internally.

```java
public interface ViewContext {
    @NotNull Player player();
    @NotNull UUID playerId();
    @NotNull View view();
    @NotNull ViewConfig config();              // effective config (per-open overrides applied)
    @NotNull Plugin plugin();
    @NotNull ViewArguments arguments();
    @NotNull Inventory inventory();            // throws IllegalStateException before container creation / after close
    boolean isActive();
    void update();                             // runs a full update pass immediately (synchronous; each call is its own pass)
    void close();                              // deferred to end of tick when called during click dispatch
    void updateTitle(@NotNull String title);   // NMS TitleUpdater, no reopen; placeholders applied
    void openView(@NotNull Class<? extends View> target);                     // closes this context with REPLACED
    void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments);
}

public interface OpenContext extends ViewContext {
    void overrideTitle(@NotNull String title);  // per-open override (narrow on purpose; not the full builder)
    void overrideRows(int rows);
    void cancelOpen();                          // aborts with zero side effects; the player's current view is untouched
    boolean isOpenCancelled();
}

public interface RenderContext extends ViewContext {
    @NotNull ItemComponentBuilder slot(int slot);                       // 0-based, < rows*9
    @NotNull ItemComponentBuilder slot(int row, int column);            // both 1-based (row 1..6, column 1..9)
    @NotNull ItemComponentBuilder slot(int slot, @NotNull ItemStack item);
    @NotNull ItemComponentBuilder layoutSlot(char character);           // one component applied to every slot of the char
    @NotNull ItemComponentBuilder layoutSlot(char character, @NotNull ItemStack item);
}

public interface UpdateContext extends ViewContext {
    @NotNull UpdateTrigger trigger();           // SCHEDULED | STATE_CHANGE | EXPLICIT | PAGINATION_SETTLE
}

public interface SlotClickContext extends ViewContext {
    int slot();                                 // raw slot id of the clicked container
    @NotNull ClickType clickType();
    @Nullable ItemStack item();                 // the clicked item, if any
    boolean isPlayerInventory();                // true when the click landed in the player's own (bottom) inventory
    void setCancelled(boolean cancelled);       // last-writer-wins override of config/component policy
    boolean isCancelled();
    @NotNull InventoryClickEvent rawEvent();    // escape hatch
}

public interface CloseContext extends ViewContext {
    @NotNull CloseReason reason();              // PLAYER | API | REPLACED | DISCONNECT | PLUGIN_DISABLE | OPEN_FAILED
}
```

Phase validity: methods invalid in a phase throw `IllegalStateException` with a message
naming the phase (e.g. `inventory()` inside `onOpen`). There is no close cancellation in
3.0.0.

`close()`/`openView()`/`ViewService.open()` invoked from inside click dispatch are
**deferred to the end of the current tick** (Bukkit forbids open/close inside
`InventoryClickEvent`); the session is marked TRANSITIONING immediately so further clicks
are swallowed. `openView` from inside `onClose` is illegal: logged SEVERE and dropped
immediately — never deferred (prevents close→onClose→openView→REPLACED-close loops).
Decision record (Plan 2, reviewer backlog #2): `update()` is deliberately synchronous —
each call runs one full pass immediately and nothing coalesces same-tick calls, which is
safe because settle-driven passes are token-scoped and never multiply full passes — the
implementation truth was kept and the `update()` comment above amended to match.

### 5.5 State

```java
public interface StateToken {}                  // watchable marker; implemented by State and Pagination

public interface State<T> extends StateToken {
    @Nullable T get(@NotNull ViewContext context);   // throws StaleContextException on foreign/closed context
}

public interface MutableState<T> extends State<T> {
    void set(@NotNull ViewContext context, @Nullable T value);          // main thread only; marks watchers dirty
    void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn);
}

public interface SharedState<T> extends StateToken { // one value per view singleton; NOT context-keyed
    @Nullable T get();
    void set(@Nullable T value);                 // atomic (AtomicReference), callable from any thread
    void update(@NotNull UnaryOperator<T> fn);   // atomic compare-and-set loop
}
```

Semantics:

- Token identity is reference identity; each token gets a per-view-instance int id at
  construction; per-context storage is an array indexed by token id, owned by the context
  and GC'd with it.
- A token used with a context of a different view class throws `StaleContextException`;
  same for a closed context.
- `MutableState.set` off the main thread throws `IllegalStateException`. The two
  legitimate cross-thread paths are `SharedState` and the pagination settle pipeline.
- `SharedState.set` from any thread: the value write is atomic; the watcher flush for all
  open contexts of the view is executed inline when on the main thread, otherwise
  scheduled onto the next main-thread tick. Off-main flushes are coalesced per view per
  tick — multiple `set` calls in one tick produce at most one re-render per component.
- Watcher notification: dirty token ids accumulate per context; one coalesced flush runs
  at the end of the current engine entry point (click dispatch, settle application, update
  tick, explicit `update()`). Each watching component re-renders at most once per flush;
  cascading set-during-flush is allowed up to depth 8 per tick, then the engine logs a
  feedback-loop warning and stops flushing for that tick.

### 5.6 `ItemComponentBuilder`

Returned by `RenderContext.slot(...)`/`layoutSlot(...)` and handed to pagination item
renderers — one builder type everywhere. Declaration order = paint order.

```java
public interface ItemComponentBuilder {
    @NotNull ItemComponentBuilder item(@NotNull ItemStack item);                          // static
    @NotNull ItemComponentBuilder item(@NotNull Function<ViewContext, ItemStack> renderer); // re-evaluated each re-render
    @NotNull ItemComponentBuilder displayIf(@NotNull Predicate<ViewContext> condition);   // false => slot cleared; hidden components receive no clicks
    @NotNull ItemComponentBuilder updateOnStateChange(@NotNull StateToken... tokens);
    @NotNull ItemComponentBuilder onClick(@NotNull Consumer<SlotClickContext> handler);
    @NotNull ItemComponentBuilder onClick(@NotNull ClickType type, @NotNull Consumer<SlotClickContext> handler);
    @NotNull ItemComponentBuilder cancelOnClick(boolean cancel);                          // per-slot override of config policy
    @NotNull ItemComponentBuilder closeOnClick();                                         // deferred to end of tick
    @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target);     // navigation sugar, deferred
    @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments);
}
```

Handler dispatch order: the per-`ClickType` handler matching the click runs; the untyped
`onClick(Consumer)` handler runs only when no per-type handler matched (2.x
`defaultCallback` semantics). A component must declare an item source via `item(...)`
(static or dynamic); a component with neither fails at first render with
`ViewConfigurationException`.

Re-render triggers for a component: a watched token changes, `ctx.update()` /
`ViewService`-driven update, scheduled tick update, pagination settle repainting its
area. There is no automatic read-tracking in 3.0.0 — dependencies are explicit via
`updateOnStateChange`.

### 5.7 Declarative pagination

`Pagination<T>` is a token (implements `StateToken`): methods take the context, enabling
`displayIf(pagination::canBack)` method references. Per-context machinery is the preserved
2.x engine.

```java
public interface Pagination<T> extends StateToken {
    int currentPage(@NotNull ViewContext context);        // 1-indexed
    int totalPages(@NotNull ViewContext context);
    int totalElements(@NotNull ViewContext context);
    boolean canAdvance(@NotNull ViewContext context);
    boolean canBack(@NotNull ViewContext context);
    void advance(@NotNull ViewContext context);
    void back(@NotNull ViewContext context);
    void switchTo(@NotNull ViewContext context, int page); // clamped exactly as 2.x changePage
    boolean isLoading(@NotNull ViewContext context);       // always false for eager sources
    @Nullable Throwable lastError(@NotNull ViewContext context);
    void refresh(@NotNull ViewContext context);            // invalidates source (lazy: re-invokes the function;
                                                           // async: invalidates cache) and re-requests the current page
}

public interface PaginationBuilder<T> {
    @NotNull PaginationBuilder<T> layoutChar(char character);            // default 'O'; fills the view layout's char slots
    @NotNull PaginationBuilder<T> layout(@NotNull Layout layout);        // explicit fill order; overrides layoutChar
    @NotNull PaginationBuilder<T> scroll();                              // sliding-window geometry
    @NotNull PaginationBuilder<T> patterns(@NotNull Layout... patterns); // per-page slot patterns, cycled
    @NotNull PaginationBuilder<T> itemRenderer(@NotNull PaginationItemRenderer<T> renderer);  // required
    @NotNull PaginationBuilder<T> fallbackItem(@NotNull Function<ViewContext, ItemStack> item);
    // async-only options — throw ViewConfigurationException at build() on a non-async builder
    @NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item);
    @NotNull PaginationBuilder<T> onError(@NotNull PaginationErrorCallback callback);
    @NotNull PaginationBuilder<T> requestTimeout(@NotNull Duration timeout);
    @NotNull PaginationBuilder<T> cacheTtl(@NotNull Duration ttl);
    @NotNull PaginationBuilder<T> cacheMaxPages(int maxPages);           // requires cacheTtl
    @NotNull Pagination<T> build();
}

@FunctionalInterface
public interface PaginationItemRenderer<T> {
    void render(@NotNull ViewContext context, @NotNull ItemComponentBuilder item, int index, @NotNull T value);
}
```

```java
@FunctionalInterface
public interface PaginationErrorCallback {     // preserved 2.x contract
    void onError(@NotNull PageRequest request, @NotNull Throwable error);
}
```

Geometry/target rules at `build()`: default geometry is normal (page slices); `scroll()`
combines freely with `layoutChar` or `layout`; an explicit `layout(...)` overrides
`layoutChar`; `patterns(...)` carries its own per-page geometry and conflicts with both
`layoutChar` and `layout` (and with `scroll()`) — conflicts throw
`ViewConfigurationException`. Navigation methods (`switchTo`/`advance`/`back`) are
callable from `onOpen`, before pagination init: they record a pending target consumed at
init (§7 step 3).

Reactive settles: every settle (success, failure, timeout) marks the pagination token
dirty, so the pagination area and every component watching the token repaint on the settle
tick — the 2.x "needs a tick poll to display settled pages" caveat is gone, and
`onUpdate(PAGINATION_SETTLE)` fires.

Known preserved quirk (deliberate non-fix): rapid advance→advance→fail rolls navigation
back to the last *requested* page, not the last *rendered* one (2.x
`navigationSnapshot`/`restoreNavigation` behavior). Geometry math is preserved exactly,
including scroll's per-element window slide — its request offset is `currentPage - 1`,
unlike normal's `(page - 1) * pageSize` — and the regression tests must assert both.

### 5.8 Page-source engine (preserved)

`PageSource`, `PageResult`, `EagerPageSource`, `AsyncPageSource`, `AsyncPageSupplier`,
`SettleDispatcher`, `BukkitSettleDispatcher`, `PaginationErrorCallback` keep their 2.x
contracts: monotonic request ids claimed under the lock, at-most-once settle, stale-settle
discard, request timeout via the shared scheduler, TTL+LRU page cache, inline settle for
cache hits/immediate failures, oversized-result truncation, plugin-disable settle drop.

Two deliberate changes (these two files are *modified*, not preserved verbatim):

1. **`PageRequest` slims down.** The full public surface is: `getPage()`,
   `getPageSize()`, `getOffset()` (unchanged; offset remains the authoritative query
   bound), plus `@Nullable UUID playerId()` and `@Nullable Plugin plugin()` replacing
   `Viewer viewer` (null only for engine-external test usage). All fields are immutable
   and thread-safe; off-main `AsyncPageSupplier` code can no longer reach
   main-thread-only API through the request.
2. **`BukkitSettleDispatcher` is rewritten accordingly.** Today it reaches through
   `viewer.getPlugin()` and `viewer.getCustomInventory().getConfiguration().tickAsync()`
   to decide routing. With `tickAsync` cut (§11), the `tickAsync` consultation is deleted
   and the plugin is read from `PageRequest.plugin()`: settles always route to the main
   thread (inline when already there). The `IllegalPluginAccessException` disable-drop
   catch and the FIFO dispatch assumption documented on `SettleDispatcher` are unchanged.

New in 3.0.0: the shared timeout scheduler is shut down from the module's disable hook
(fixes a preexisting classloader leak on reload).

### 5.9 Layout

```java
public interface Layout {
    int ROW_WIDTH = 9;
    @NotNull List<Integer> slots();                          // ordered fill positions
    static @NotNull Layout ofGrid(@NotNull String... rows);  // ' ' empty; chars fill alphabetically, then occurrence
    static @NotNull Layout ofSlots(int... slots);            // explicit order; 0..53, unique
}
```

`getBackSlot()`/`getNextSlot()` and the reserved `<`/`>` characters are removed —
navigation buttons are ordinary components bound to any layout char with
`displayIf(pagination::canBack)`.

### 5.10 Preserved SPIs

`PlaceholderApplier` (+ `PapiPlaceholderApplier`/`NoopPlaceholderApplier`,
`@ConditionalOnMissingBean` selection) and `TitleUpdater` (NMS dispatch per version) are
preserved. Placeholders are applied at paint time (title + display name + lore), as 2.x's
`InventoryEditorImpl` did.

## 6. Click protection and routing

While a session is open, every `InventoryClickEvent` and `InventoryDragEvent` involving
the player is intercepted by the single internal listener.

**Routing.**

- Top-container slot with a component: pre-cancel per policy → component per-`ClickType`
  handler, then component default handler, then view-level `onClick` → deferred
  post-actions (`closeOnClick`, `openOnClick`).
- Top-container slot without a component: pre-cancel per config → view-level `onClick`.
- Bottom (player) inventory click: pre-cancelled per the safety floor → view-level
  `onClick` with `isPlayerInventory() == true`. This is what makes deposit/sell/trade
  GUIs expressible — the handler implements item movement programmatically.
- Components hidden by `displayIf` receive no clicks; the click is treated as
  component-less.

**Cancellation: three layers, last writer wins.**

1. Config default: `cancelOnClick(true)` / `cancelOnDrag(true)` unless overridden in
   `onInit`.
2. Per-component: `ItemComponentBuilder.cancelOnClick(boolean)`.
3. Per-click: `SlotClickContext.setCancelled(boolean)` inside any handler. The engine
   applies layers 1–2 before handlers run; handlers observe that decision via
   `isCancelled()` and may overturn it. The final cancellation state is read once, after
   click dispatch completes.

**Safety floor (non-negotiable in 3.0.0).** Regardless of all three layers, the engine
always cancels actions that move items into or out of the top container from
bottom-originating clicks: `MOVE_TO_OTHER_INVENTORY` (shift-click into the GUI),
`COLLECT_TO_CURSOR` (double-click vacuum of GUI display items), and hotbar swaps targeting
top slots (`HOTBAR_SWAP`/`HOTBAR_MOVE_AND_READD`). These cannot be authorized per slot
because vanilla, not the developer, picks the affected slots. Handlers still observe the
(cancelled) events and may implement the gesture programmatically.

A click whose handler throws is force-cancelled regardless of policy (anti-duplication).

## 7. Lifecycle semantics

**Registration (plugin enable, once per view class):** DI constructs the singleton →
state/pagination factories run in field initializers/constructor, token table frozen →
`onInit(ViewConfigBuilder)` → `ViewConfig` validated and frozen
(`ViewConfigurationException` on violation).

**Open (main thread, single tick):**

1. `ViewService.open` → main-thread assert → registry lookup (`UnknownViewException`).
2. New session + state store created (status OPENING); `ViewArguments` bound;
   `initialState` keys type-checked now (fail at the open site).
3. `onOpen(OpenContext)` — may `cancelOpen()` (abort with **zero side effects**: the
   player's current view, if any, is untouched and still protected), may
   `overrideTitle`/`overrideRows`. Pagination navigation calls
   (`switchTo`/`advance`/`back`) are legal here and record a pending target (deferred
   pre-init navigation, preserved 2.x machinery); the target is consumed in step 7.
4. Open committed → the player's previous session (if any) runs the full close path with
   `CloseReason.REPLACED`.
5. Layout resolution from the effective config.
6. Container created (placeholder-applied title).
7. Pagination init per token: per-context geometry engine + per-context `PageSource`
   built; pending navigation target consumed; initial `PageRequest` dispatched. Eager
   sources settle inline (items ready before first paint); async sources paint
   `loadingItem`. Ordering rationale: init needs the resolved layout from step 5 (the
   geometry engines derive `itemPageLimit` from it) and the bound session from step 2,
   and must precede `onFirstRender` so components declared there can read pagination
   state.
8. `onFirstRender(RenderContext)` — components declared and materialized.
9. Initial paint → `player.openInventory(...)`. The synchronous `InventoryCloseEvent`
   Bukkit fires for the previous container is ignored via the container-identity guard
   (preserved 2.x invariant; regression test required).
10. Status ACTIVE; scheduled update task starts (if `scheduleUpdate > 0`).

**Update pass** (scheduled tick / `ctx.update()` / settle / state flush): `onUpdate`
with the matching `UpdateTrigger`, then re-render of affected components (full pass for
SCHEDULED/EXPLICIT; dirty-only for STATE_CHANGE; pagination area + watchers for
PAGINATION_SETTLE).

**Reopen** (same player, same or different view): indistinguishable from open; the old
context fully closes first. Nothing survives except `SharedState`.

**Close** (player Esc, API, replaced, disconnect, plugin disable): context deactivated →
`onClose(CloseContext)` with reason → update task cancelled → session unregistered →
state store dropped. Close during click dispatch is deferred to end of tick.

**Close during async load:** the late settle lands on the main thread, applies
source-internal state (at-most-once contract intact), then sees `isActive() == false` and
stops: no painting, no watcher marking (the context's state store is already dropped),
and no `onUpdate(PAGINATION_SETTLE)`.

**Plugin disable:** sessions close with `PLUGIN_DISABLE`; pending settles are dropped by
`BukkitSettleDispatcher` with a warning (preserved); the shared timeout scheduler shuts
down.

## 8. Threading contract

| Operation | Thread rule |
|---|---|
| All lifecycle handlers, click handlers, component renderers, `displayIf` | Main thread (engine-invoked) |
| `ViewService.open/close`, `ViewContext.update/close/openView/updateTitle` | Main thread; off-main throws `IllegalStateException` |
| `State.get` | Any thread (volatile read), but values are only coherent on main |
| `MutableState.set/update` | Main thread only; off-main throws |
| `SharedState.get/set/update` | Any thread; atomic; flush marshalled to main |
| `AsyncPageSupplier.load` | Any thread (user's executor) |
| Settle application + repaint | Always main thread (dispatcher inline when already on main) |
| `PageRequest` accessors | Any thread (immutable) |

## 9. Error handling

| Failure | Behavior |
|---|---|
| Config/layout/builder violations | `ViewConfigurationException` at boot/registration |
| `@RegisterView` on a non-`View` class | SEVERE log at boot |
| Unknown view in `open`/`openView` | `UnknownViewException` |
| Foreign/closed-context token access | `StaleContextException` |
| State factory after registration; phase-invalid context method; off-main mutation | `IllegalStateException` |
| `onOpen` throws | Open aborted as if cancelled; previous view untouched; SEVERE log |
| `onFirstRender` throws | Open aborted; container never shown; `onClose(OPEN_FAILED)`; SEVERE log |
| Click handler throws | Click force-cancelled; SEVERE log (view class + slot); session stays usable; everything remaining for that click is skipped — later component handlers, the view-level `onClick`, and deferred post-actions (`closeOnClick`/`openOnClick`) |
| `item(fn)` / `displayIf` / `PaginationItemRenderer` throws | Slot keeps previous content (fallback on first paint); log rate-limited per component (1/min) |
| `onUpdate` / `onClose` throws | Caught and logged; close teardown always completes |
| Async load failure / timeout / null future | Preserved engine path: `onError` callback, `lastError()`, navigation rollback (pattern geometry clears failed-pattern slots first) |

User handler failures never propagate into Bukkit's event bus or the scheduler.

## 10. Internal architecture (Approach C)

```
internal/
├── engine/ViewEngine                  — fixed phase order; sole session mutator
│   └── phase/OpenPhase, LayoutResolutionPhase, PaginationInitPhase,
│             FirstRenderPhase, UpdatePhase, ClickRoutingPhase, ClosePhase
├── session/ViewSession                — status OPENING|ACTIVE|TRANSITIONING|CLOSED;
│                                        owns Inventory, StateStore, ComponentTable, pagination bindings
├── session/SessionRegistry            — player UUID -> session (ex ViewerRegistry semantics)
├── state/StateStore                   — per-context array indexed by token id
├── state/WatcherTable                 — token id -> watching components (per context)
├── component/ComponentTable           — slot -> component instance
├── render/SlotPainter                 — ItemStack writes + PlaceholderApplier (ex InventoryEditorImpl)
├── pagination/PaginationBinding       — token spec -> per-context engine + source wiring
├── pagination/PaginationHost          — the adapter seam (below)
├── registry/ViewRegistry              — view class -> singleton + frozen ViewConfig + token table
├── discovery/ViewDiscoveryService     — @RegisterView scan + boot guard
├── listener/ViewListener              — single Bukkit listener (click/drag/close/quit/disable)
├── schedule/ViewUpdateTask            — per-session repeating task
├── title/TitleService                 — NMS TitleUpdater chain (preserved)
└── placeholder/PlaceholderService     — applier resolution (preserved)
```

No public pipeline/interceptor API; the phase handlers are small single-responsibility
classes composed by `ViewEngine` in hard-coded order. A public extension contract can be
introduced additively in 3.1+ if demand appears.

**The adapter seam.** The preserved pagination engine
(`AbstractPageSourcePagination` + `NormalPagination`/`ScrollPagination`/`PatternPagination`)
touched the old world through exactly three points (`Viewer`, `InventoryEditor.fillPage`,
`CustomInventory.updateInventory`). They collapse into one internal interface implemented
by `ViewSession`:

```java
interface PaginationHost {
    boolean isActive();                                                  // ex renderIfOnline null-chain
    void fillPage(@NotNull List<RenderedItem> items, @NotNull Layout layout); // ex editor.fillPage
    void requestRender();                                                // ex updateInventory; marks token dirty
}
```

Everything else in those classes — and in `AsyncPageSource`/`BukkitSettleDispatcher` — is
untouched.

**Behavior preservation map.**

| Preserved behavior | 2.x home | 3.0 home |
|---|---|---|
| Monotonic request ids, at-most-once settle, stale discard | `AsyncPageSource` | unchanged |
| Request timeout (timeout-before-cancel FIFO note) | `AsyncPageSource.scheduleTimeout` | unchanged |
| TTL + LRU page cache | `AsyncPageSource` | unchanged (per-context instance) |
| Settle thread routing | `BukkitSettleDispatcher` | **modified**: `tickAsync` consultation deleted (feature cut), plugin read from slim `PageRequest`; disable-drop + FIFO contract unchanged |
| Plugin-disable settle drop | `BukkitSettleDispatcher` catch | unchanged |
| Navigation rollback on failed load | `navigationSnapshot`/`restoreNavigation` | same algorithm, same class |
| Deferred pre-init navigation | `changePageInternal` viewer-null branch | same branch, keyed on unbound host; the record-only check moved AHEAD of the totals-known clamp (pre-bind `itemPageLimit` is 0 — the 2.x order divides by zero for a non-empty eager source; observably equivalent: no in-flight request exists pre-bind and overshoot is corrected by the settle's downward re-clamp) |
| Downward re-clamp when totals shrink | `onSettle` | same |
| Inline-settle detection | `AbstractPageSourcePagination.dispatch` | same |
| Loading frame rendering | `insertPageItems` | same, via `PaginationHost.fillPage` |
| Pattern stale-slot clearing | `PatternPagination.clearPattern` | same |
| Scroll window math (`max(1, total - limit + 1)`) | `ScrollPagination` | same |
| Tick updates | `InventoryUpdateRunnable` | `ViewUpdateTask` |
| Title update without reopen | `TitleUpdater` + `ViewerImpl.updateTitle` | `TitleUpdater` + `ViewContext.updateTitle` |
| Placeholders at paint time | `InventoryEditorImpl` | `SlotPainter` |
| One-session-per-player + close-event container guard | `ViewerRegistry` + listener guard | `SessionRegistry` + `ClosePhase` |

## 11. Deliberate cuts (3.1 candidates, all additive later)

| Cut | Rationale |
|---|---|
| `tickAsync` / async scheduled updates | Breaks the single-writer state model; async pagination + reactive settle repaint serves the DB-menu use case better; simplifies dispatcher and threading contract |
| `computedState` | Unwatchable (watchers never fire); `lazyState` + `item(fn)` cover it |
| Automatic read-tracking | Largest unproven subsystem; explicit `updateOnStateChange` only |
| `cancelClose()` | Reopen races, re-entrancy loops, no 2.x precedent or demand |
| `Pagination.setSource()` | Lazy sources + `refresh()` (which re-invokes the source function) cover it |
| `pageOf(index)` (ex `getPageOfIndex`) | No consumer; documented removal |
| `MutableIntState` etc. | Liskov-problematic primitive specializations |
| `firstSlot()`/`lastSlot()`, `hideIf`, `updateOnClick()`, `resetTitle()`, `updateCount()` | YAGNI sweep; no sample/test/2.x usage |
| Declarative per-`ClickType` cancellation | Handler-level `setCancelled` covers it in one line |
| Multi-viewer shared contexts | One-player-per-session stays; `SharedState` covers view-global data; scoped shared state (e.g. trade pairs) is the designated 3.1 driver |
| Non-chest container types | Rows-only (1–6); `ViewConfig` shape does not preclude `type(...)` later |
| Public pipeline/interceptor API | Approach C decision; internals stay free to evolve |

## 12. Samples (ergonomics proof)

### Scroll with layout-char navigation

```java
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
}
```

(2.x needed `firstOpen` + `configureInventory` + `update` + a `ViewerPropertyMap` key +
manual `init`/`apply` + conditional `setItem(null)` for the same result. Note: no
`scheduleUpdate` — arrows repaint reactively via the watched token.)

### Async with live loading indicator

```java
@RegisterView
public final class SampleAsyncView extends View {

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
                .displayIf(numbers::canBack).updateOnStateChange(numbers).onClick(numbers::back);
        render.layoutSlot('>', new ItemStack(Material.ARROW))
                .displayIf(numbers::canAdvance).updateOnStateChange(numbers).onClick(numbers::advance);
        render.layoutSlot('I')
                .item(ctx -> new ItemStack(numbers.isLoading(ctx) ? Material.CLOCK : Material.PAPER,
                        Math.max(1, numbers.currentPage(ctx))))
                .updateOnStateChange(numbers);
    }

    private static CompletableFuture<PageResult<Integer>> loadPage(PageRequest request) {
        // simulated database query using request.getOffset()/getPageSize()
    }
}
```

The normal-geometry sample (explicit `Layout.ofSlots` fill order) and pattern sample
(`patterns(Layout.ofGrid(...), ...)`) follow the same shape; the test-plugin rewrite
covers all four, plus a navigation pair (`ShopView` ↔ confirm view via
`openOnClick`/`initialState`) and a `SharedState` leaderboard.

### Cross-session pattern (trade GUI; validates §6 routing)

State lives in a DI service (`TradeSession` keyed per trade), passed to both players'
views via `ViewArguments`; the GUI renders state and never holds real items. Deposits:
bottom-inventory clicks arrive (pre-cancelled) at view-level `onClick` with
`isPlayerInventory()`, and the handler moves items programmatically. Sync is manual:
`views.contextOf(other).ifPresent(ViewContext::update)`. Scoped shared state for this is
deferred to 3.1.

## 13. Migration & attribution

- Version bump to 3.0.0 across the reactor; PR targets `dev`.
- Migration table (2.x → 3.0) ships with the module docs:
  `@Inventory`→`@RegisterView`, `CustomInventoryImpl.configure`→`onInit`,
  `configureViewer`/`firstOpen`→`onOpen`/`onFirstRender`,
  `configureInventory`+`update`→`onFirstRender`+reactive state,
  `InventoryService.open`→`ViewService.open` (throws), `InventoryItem.of(...).callback`→
  `ItemComponentBuilder.onClick`, `ViewerPropertyMap`→state tokens,
  `*PaginationBuilder`+`init`/`apply`→`paginate*(...)` factories,
  `InventoryLayout`→`Layout` (back/next slots removed).
- test-plugin: 4 sample inventories + `JoinListener` rewritten.
- Attribution: README section and NOTICE entry — "API design inspired by
  devnatan/inventory-framework (MIT)" — plus a note in the root `package-info.java`.
  Implementation is clean-room; the engine is original 2.x code.

## 14. Test plan

Ported (behavior preserved): `AsyncPageSourceTest`, `BukkitSettleDispatcherTest` (minus
`tickAsync` cases, plus always-main routing cases), `NormalPaginationTest`,
`ScrollPaginationTest`, `PatternPaginationTest`, `GridLayoutTest`,
`OrderedSlotsLayoutTest`; `InventoryEditorImplTest` becomes `SlotPainterTest`.

New regression suites (one per identified hazard):

1. Click-protection matrix: top click, shift-click from bottom, `COLLECT_TO_CURSOR`,
   hotbar swap, and drag events touching the top container × config/component/handler
   policy layers; safety-floor invariants.
2. Click routing: component per-type vs default vs view-level ordering; hidden-component
   clicks; bottom-click delivery with `isPlayerInventory()`.
3. Open ordering: `cancelOpen()` leaves the previous view intact and protected; REPLACED
   sequencing; stale `InventoryCloseEvent` container-identity guard.
4. Deferred operations: `close`/`openView`/`closeOnClick` from click handlers run end of
   tick; TRANSITIONING swallows clicks; `openView`-in-`onClose` rejected.
5. Settle lifecycle: settle-after-close dropped before paint; plugin-disable drop; timeout
   scheduler shutdown on disable.
6. Pre-init navigation recording → consumed at init; rollback on failed load incl.
   pattern slot clearing; reactive settle repaint (no tick poll); exact geometry math
   asserted against 2.x (scroll offset `currentPage - 1`, normal `(page - 1) * pageSize`,
   scroll page count `max(1, total - limit + 1)`).
7. State: token freeze after registration; foreign-token and closed-context
   `StaleContextException`; off-main `set` throws; flush coalescing (≤1 re-render per
   component per flush); cascade cap; `SharedState` atomicity and multi-context flush;
   `mutableState(Function)` per-context initials.
8. Per-context `PageSource` isolation: two players paginating concurrently, zero
   cross-talk (request ids, cache).
9. `ViewArguments`/`initialState` type validation at the open site.
10. MockBukkit integration: full open → click → navigate → close flows for all four
    sample views; boot guard for `@RegisterView` on non-`View`.

CI bar: `mvnw.cmd clean test` green across all modules (build with JDK 21).

## 15. Resolved and deferred questions

Resolved during design review: open-ordering (cancel before previous-session close);
deny-by-default click policy with safety floor; bottom-click routing +
`isPlayerInventory()`; per-context page sources; slim `PageRequest`; lazy-source
`refresh()` re-invocation; `StateToken` marker typing for `updateOnStateChange`;
`OpenContext` narrow overrides; `ViewArguments` typed reads; rows inferred from layout;
1-based `slot(row, column)`.

Deferred to 3.1+ (in likely priority order): scoped shared state / multi-viewer contexts
(trade use case), `refreshOnStateChange` for pagination sources, automatic read-tracking,
declarative per-`ClickType` cancellation, non-chest container types, public extension
(interceptor) API, per-component `placeholders(false)` opt-out (profile first).

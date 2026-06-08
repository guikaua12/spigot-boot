# inventory-api 3.0.0 — Plan 1 of 3: Core View Engine

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the new view-based API core (state, config, contexts, components, layout, click protection, lifecycle engine, ViewService, discovery) alongside the untouched 2.x API, fully tested, with the module build staying green.

**Architecture:** New public surface under `tech.guilhermekaua.spigotboot.inventoryapi.{annotation,service,config,context,state,component,layout,exception}` with all machinery in `internal.*` (Approach C: fixed-order phase handlers composed by `ViewEngine`, no public pipeline). The 2.x API is not touched in this plan — old and new coexist; pagination switchover is Plan 2; deletion of 2.x is Plan 3.

**Tech Stack:** Java 8 (main sources; tests compile at 17), Maven, Spigot/Paper API 1.20.1, spigot-boot DI (`@Component`/`@Service`/`@Inject`/`@Configuration`/`@Bean`), JUnit 5, Mockito, MockBukkit-v1.20 3.20.2, Lombok (build with JDK 21).

**Spec:** `docs/superpowers/specs/2026-06-07-inventory-api-v3-redesign-design.md` — sections referenced as §N throughout.

**Test command pattern:** `mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=<TestClass>"` from the repo root. Full module check: `mvnw.cmd -pl modules/inventory-api/api -am test`.

**License header:** every new file starts with the same MIT header block used by every existing file in the module (copy from `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/annotation/Inventory.java` lines 1–22).

---

## File Structure

All paths relative to `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/` (main) and `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/` (test).

**Public API (new files):**

| File | Responsibility |
|---|---|
| `View.java` | abstract base: lifecycle handlers + state factories + internal token table |
| `annotation/RegisterView.java` | discovery marker |
| `exception/UnknownViewException.java` | unregistered view opened |
| `exception/ViewConfigurationException.java` | registration-time config/builder violations |
| `exception/StaleContextException.java` | foreign/closed-context state access |
| `service/ViewArguments.java` | immutable typed argument map |
| `service/ViewService.java` | DI facade: open/close/contextOf |
| `config/ViewConfig.java` | immutable frozen config |
| `config/ViewConfigBuilder.java` | fluent builder used in `onInit` |
| `context/ViewContext.java`, `context/OpenContext.java`, `context/RenderContext.java`, `context/UpdateContext.java`, `context/SlotClickContext.java`, `context/CloseContext.java` | per-phase contexts (§5.4) |
| `context/UpdateTrigger.java`, `context/CloseReason.java` | enums |
| `state/StateToken.java`, `state/State.java`, `state/MutableState.java`, `state/SharedState.java` | reactive state surface (§5.5) |
| `component/ItemComponentBuilder.java` | fluent component declaration (§5.6) |
| `layout/Layout.java` | ofGrid/ofSlots, ordered fill positions (§5.9) |

**Internal (new files, all `@ApiStatus.Internal`):**

| File | Responsibility |
|---|---|
| `internal/layout/GridSlotsLayout.java`, `internal/layout/OrderedSlotsLayout.java` | `Layout` implementations |
| `internal/layout/ResolvedLayout.java` | parsed `ViewConfig.layout()` (char → slots) |
| `internal/state/TokenTable.java` | per-view token registry + freeze |
| `internal/state/IdentifiableToken.java` | int-id seam implemented by every token impl |
| `internal/state/StateBackedContext.java`, `internal/state/ContextStateAccess.java` | context→store resolution seam (implemented by `AbstractViewContext`) |
| `internal/state/MutableStateImpl.java`, `internal/state/LazyStateImpl.java`, `internal/state/SharedStateImpl.java`, `internal/state/InitialStateImpl.java` | token implementations |
| `internal/state/StateStore.java` | per-context values + dirty set |
| `internal/component/ItemComponentBuilderImpl.java` | builder implementation |
| `internal/component/ComponentInstance.java` | materialized component (slots, handlers, watch list) |
| `internal/component/ComponentTable.java` | slot → component, token id → watchers |
| `internal/render/SlotPainter.java` | ItemStack writes + placeholder application |
| `internal/session/ViewSession.java` | per-(player, open) runtime state + status |
| `internal/session/SessionRegistry.java` | player UUID → session |
| `internal/context/AbstractViewContext.java` + `OpenContextImpl/RenderContextImpl/UpdateContextImpl/SlotClickContextImpl/CloseContextImpl/PlainViewContextImpl` | context implementations |
| `internal/engine/ViewEngine.java` | orchestrator, fixed phase order, deferred ops |
| `internal/engine/phase/OpenPhase.java`, `FirstRenderPhase.java`, `UpdatePhase.java`, `ClickRoutingPhase.java`, `ClosePhase.java` | single-responsibility phase handlers |
| `internal/registry/ViewRegistry.java`, `internal/registry/RegisteredView.java` | view class → singleton + frozen config |
| `internal/discovery/ViewDiscoveryService.java` | `@RegisterView` scan + boot guard |
| `internal/listener/ViewListener.java` | single Bukkit listener bridge |
| `internal/schedule/ViewUpdateTask.java` | per-session scheduled update runnable |

**Modified files:**

| File | Change |
|---|---|
| `InventoryApiModule.java` | add `viewRegistry.initialize(context)` bootstrap call |

**Explicitly NOT in this plan:** anything under `pagination/` (Plan 2), deletion of any 2.x type (Plan 3), `paginate*` factories on `View` (Plan 2), `PaginationInitPhase` (Plan 2).

---

## Shared Type Contracts

Every task MUST use these exact signatures. A task that needs a method not listed here is wrong — go back to this section. (Bodies are defined in the tasks; this section pins names and shapes. `@NotNull`/`@Nullable` are `org.jetbrains.annotations`.)

### Public

```java
// View.java (root package)
public abstract class View {
    protected void onInit(@NotNull ViewConfigBuilder config) {}
    protected void onOpen(@NotNull OpenContext context) {}
    protected void onFirstRender(@NotNull RenderContext context) {}
    protected void onUpdate(@NotNull UpdateContext context) {}
    protected void onClick(@NotNull SlotClickContext context) {}
    protected void onClose(@NotNull CloseContext context) {}
    protected final <T> MutableState<T> mutableState(@Nullable T initialValue);
    protected final <T> MutableState<T> mutableState(@NotNull Function<ViewContext, T> initialValue);
    protected final <T> State<T> lazyState(@NotNull Function<ViewContext, T> computation);
    protected final <T> MutableState<T> initialState(@NotNull String key, @NotNull Class<T> type);
    protected final <T> SharedState<T> sharedState(@Nullable T initialValue);
    @ApiStatus.Internal public final @NotNull TokenTable tokenTable();
}

// service/ViewService.java
@Service
public final class ViewService {
    public ViewService(ViewEngine engine, SessionRegistry sessions);   // constructor injection
    public void open(@NotNull Player player, @NotNull Class<? extends View> view);
    public void open(@NotNull Player player, @NotNull Class<? extends View> view, @NotNull ViewArguments arguments);
    public void close(@NotNull Player player);
    public @NotNull Optional<ViewContext> contextOf(@NotNull Player player);
}

// service/ViewArguments.java — final, immutable
public final class ViewArguments {
    public static @NotNull ViewArguments empty();
    public static @NotNull ViewArguments of(@NotNull String key, @NotNull Object value);
    public static @NotNull ViewArguments of(@NotNull String k1, @NotNull Object v1, @NotNull String k2, @NotNull Object v2);
    public static @NotNull Builder builder();
    public <T> @Nullable T get(@NotNull String key, @NotNull Class<T> type);     // IllegalArgumentException on type mismatch
    public <T> @NotNull T require(@NotNull String key, @NotNull Class<T> type);  // IllegalArgumentException when absent/mismatch
    public boolean has(@NotNull String key);
    public static final class Builder {
        public @NotNull Builder put(@NotNull String key, @NotNull Object value);
        public @NotNull ViewArguments build();
    }
}

// config/ViewConfigBuilder.java — final, mutable, used in onInit/per-open overrides
public final class ViewConfigBuilder {
    public @NotNull ViewConfigBuilder title(@NotNull String title);
    public @NotNull ViewConfigBuilder rows(int rows);
    public @NotNull ViewConfigBuilder layout(@NotNull String... rows);
    public @NotNull ViewConfigBuilder cancelOnClick(boolean cancel);
    public @NotNull ViewConfigBuilder cancelOnDrag(boolean cancel);
    public @NotNull ViewConfigBuilder scheduleUpdate(long intervalTicks);
    public @NotNull ViewConfigBuilder applyPlaceholders(boolean apply);
    public @NotNull ViewConfig build();          // validates; throws ViewConfigurationException
}

// config/ViewConfig.java — final, deeply immutable
public final class ViewConfig {
    public @NotNull String title();
    public int rows();                            // always resolved (explicit or inferred from layout)
    public @NotNull List<String> layout();        // empty list when absent; unmodifiable
    public boolean cancelOnClick();               // default true
    public boolean cancelOnDrag();                // default true
    public long updateIntervalTicks();            // 0 = disabled
    public boolean applyPlaceholders();           // default true
    public @NotNull ViewConfig withOverrides(@Nullable String title, @Nullable Integer rows); // per-open merge (§5.4 OpenContext)
}

// context/* — all @ApiStatus.NonExtendable interfaces, exact methods per spec §5.4:
public interface ViewContext {
    @NotNull Player player();  @NotNull UUID playerId();  @NotNull View view();
    @NotNull ViewConfig config();  @NotNull Plugin plugin();  @NotNull ViewArguments arguments();
    @NotNull Inventory inventory();  boolean isActive();
    void update();  void close();  void updateTitle(@NotNull String title);
    void openView(@NotNull Class<? extends View> target);
    void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments);
}
public interface OpenContext extends ViewContext {
    void overrideTitle(@NotNull String title);  void overrideRows(int rows);
    void cancelOpen();  boolean isOpenCancelled();
}
public interface RenderContext extends ViewContext {
    @NotNull ItemComponentBuilder slot(int slot);
    @NotNull ItemComponentBuilder slot(int row, int column);            // both 1-based
    @NotNull ItemComponentBuilder slot(int slot, @NotNull ItemStack item);
    @NotNull ItemComponentBuilder layoutSlot(char character);
    @NotNull ItemComponentBuilder layoutSlot(char character, @NotNull ItemStack item);
}
public interface UpdateContext extends ViewContext { @NotNull UpdateTrigger trigger(); }
public interface SlotClickContext extends ViewContext {
    int slot();  @NotNull ClickType clickType();  @Nullable ItemStack item();
    boolean isPlayerInventory();
    void setCancelled(boolean cancelled);  boolean isCancelled();
    @NotNull InventoryClickEvent rawEvent();
}
public interface CloseContext extends ViewContext { @NotNull CloseReason reason(); }
public enum UpdateTrigger { SCHEDULED, STATE_CHANGE, EXPLICIT, PAGINATION_SETTLE }
public enum CloseReason { PLAYER, API, REPLACED, DISCONNECT, PLUGIN_DISABLE, OPEN_FAILED }

// state/*
public interface StateToken {}
public interface State<T> extends StateToken { @Nullable T get(@NotNull ViewContext context); }
public interface MutableState<T> extends State<T> {
    void set(@NotNull ViewContext context, @Nullable T value);
    void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn);
}
public interface SharedState<T> extends StateToken {
    @Nullable T get();  void set(@Nullable T value);  void update(@NotNull UnaryOperator<T> fn);
}

// component/ItemComponentBuilder.java — @NonExtendable interface
public interface ItemComponentBuilder {
    @NotNull ItemComponentBuilder item(@NotNull ItemStack item);
    @NotNull ItemComponentBuilder item(@NotNull Function<ViewContext, ItemStack> renderer);
    @NotNull ItemComponentBuilder displayIf(@NotNull Predicate<ViewContext> condition);
    @NotNull ItemComponentBuilder updateOnStateChange(@NotNull StateToken... tokens);
    @NotNull ItemComponentBuilder onClick(@NotNull Consumer<SlotClickContext> handler);
    @NotNull ItemComponentBuilder onClick(@NotNull ClickType type, @NotNull Consumer<SlotClickContext> handler);
    @NotNull ItemComponentBuilder cancelOnClick(boolean cancel);
    @NotNull ItemComponentBuilder closeOnClick();
    @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target);
    @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments);
}

// layout/Layout.java
public interface Layout {
    int ROW_WIDTH = 9;
    @NotNull List<Integer> slots();
    static @NotNull Layout ofGrid(@NotNull String... rows);   // returns internal GridSlotsLayout
    static @NotNull Layout ofSlots(int... slots);             // returns internal OrderedSlotsLayout
}

// exception/* — all extend RuntimeException, (String message) and (String, Throwable) constructors
public final class UnknownViewException extends RuntimeException
public final class ViewConfigurationException extends RuntimeException
public final class StaleContextException extends RuntimeException

// annotation/RegisterView.java — mirrors 2.x @Inventory wiring exactly
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@SpigotBootDiscoveryCategory(value = DiscoveryCategories.INVENTORY, kind = SpigotBootDiscoveryCategory.Kind.ANNOTATION)
public @interface RegisterView {}
```

### Internal

```java
// internal/state/TokenTable.java
public final class TokenTable {
    public int register(@NotNull StateToken token);            // returns assigned id; throws IllegalStateException after freeze()
    public void freeze();
    public boolean isFrozen();
    public int size();
    public @NotNull List<StateToken> tokens();                 // unmodifiable
}

// internal/state/StateStore.java — per session
public final class StateStore {
    public StateStore(int size);
    public @Nullable Object get(int id);
    public void set(int id, @Nullable Object value);
    public void markDirty(int id);
    public @NotNull Set<Integer> drainDirty();                 // returns and clears
    public boolean hasDirty();
}

// internal/state/IdentifiableToken.java — created in Task 6; implemented by ALL four token impls
public interface IdentifiableToken { int tokenId(); }          // tokenId() == the TokenTable-assigned id

// internal/state/StateBackedContext.java — created in Task 6; AbstractViewContext (Task 11)
// MUST implement it; token impls resolve state ONLY through it (never cast to concrete contexts)
public interface StateBackedContext {
    @NotNull StateStore stateStore();
    @NotNull View owner();
    boolean contextActive();   // true during OPENING and ACTIVE (state writable in onOpen/onFirstRender)
}
// internal/state/ContextStateAccess.java — static helpers storeOf/ownerOf/isActive over StateBackedContext

// internal/state token impls — each holds `final int id` assigned via TokenTable.register(this)
// and a reference to the owning View for foreign-context checks; value resolution goes through
// ContextStateAccess (StateBackedContext seam), NOT AbstractViewContext casts.
public final class MutableStateImpl<T> implements MutableState<T>, IdentifiableToken
public final class LazyStateImpl<T> implements State<T>, IdentifiableToken       // computed once per session
public final class InitialStateImpl<T> implements MutableState<T>, IdentifiableToken // String key + Class<T>, bound at open
public final class SharedStateImpl<T> implements SharedState<T>, IdentifiableToken {
    // AtomicReference<T>; flush via ViewEngine
    public void flushHook(@Nullable Runnable hook);            // @ApiStatus.Internal setter, wired by ViewEngine (Task 16)
}

// internal/layout/ResolvedLayout.java
public final class ResolvedLayout {
    public static @NotNull ResolvedLayout resolve(@NotNull ViewConfig config); // empty when config.layout() empty
    public int rows();
    public boolean hasChar(char c);
    public @NotNull int[] slotsOf(char c);                     // empty array when absent
}

// internal/component/ComponentInstance.java
public final class ComponentInstance {
    // built by ItemComponentBuilderImpl.materialize(int[] slots)
    public @NotNull int[] slots();
    public static final ItemStack RENDER_FAILURE;              // identity sentinel: "skip paint, keep previous content"
    public boolean isVisible(@NotNull ViewContext ctx);        // displayIf, true by default; exceptions → false + rate-limited log
    public @Nullable ItemStack renderItem(@NotNull ViewContext ctx); // static item or renderer.apply; exceptions → null + log
    public @Nullable ItemStack renderForPaint(@NotNull ViewContext ctx); // paint entry: null = clear (hidden),
                                                               // == RENDER_FAILURE = skip paint (displayIf/renderer threw, §9), else paint
    public @Nullable Consumer<SlotClickContext> handlerFor(@NotNull ClickType type); // typed first, else default, else null
    public @Nullable Boolean cancelOnClick();                  // null = inherit config
    public boolean closeOnClick();
    public @Nullable Class<? extends View> openOnClickTarget();
    public @NotNull ViewArguments openOnClickArguments();
    public @NotNull int[] watchedTokenIds();
}

// internal/component/ComponentTable.java — per session
public final class ComponentTable {
    public void add(@NotNull ComponentInstance component);     // ViewConfigurationException on slot overlap or missing item source
    public @Nullable ComponentInstance componentAt(int slot);
    public @NotNull List<ComponentInstance> all();
    public @NotNull List<ComponentInstance> watchersOf(@NotNull Set<Integer> dirtyTokenIds);
}

// internal/render/SlotPainter.java — @Component bean
public final class SlotPainter {
    public SlotPainter(PlaceholderApplier placeholderApplier);
    public void paint(@NotNull Player player, @NotNull Inventory inventory, int slot,
                      @Nullable ItemStack item, boolean applyPlaceholders);  // null clears the slot
}

// internal/session/ViewSession.java
public final class ViewSession {
    public enum Status { OPENING, ACTIVE, TRANSITIONING, CLOSED }
    public ViewSession(@NotNull Player player, @NotNull RegisteredView registered,
                       @NotNull ViewArguments arguments, @NotNull StateStore stateStore);
    public @NotNull Player player();
    public @NotNull RegisteredView registered();
    public @NotNull ViewArguments arguments();
    public @NotNull StateStore stateStore();
    public @NotNull ComponentTable components();
    public @NotNull Status status();          public void status(@NotNull Status s);
    public @Nullable Inventory inventory();   public void inventory(@NotNull Inventory inv);
    public @NotNull ViewConfig effectiveConfig();  public void effectiveConfig(@NotNull ViewConfig c);
    public @Nullable ResolvedLayout layout(); public void layout(@NotNull ResolvedLayout l);
    public @Nullable BukkitTask updateTask(); public void updateTask(@Nullable BukkitTask t);
    public boolean isActive();                // status == ACTIVE
    public @NotNull List<Runnable> deferredOps();
}

// internal/session/SessionRegistry.java — @Component bean
public final class SessionRegistry {
    public void register(@NotNull ViewSession session);
    public void unregister(@NotNull ViewSession session);
    public @NotNull Optional<ViewSession> find(@NotNull UUID playerId);
    public @NotNull Collection<ViewSession> all();             // unmodifiable
}

// internal/context/AbstractViewContext.java — implements ViewContext AND StateBackedContext
public abstract class AbstractViewContext implements ViewContext, StateBackedContext {
    protected AbstractViewContext(@NotNull ViewSession session, @NotNull ViewEngine engine);
    public @NotNull ViewSession session();
    // StateBackedContext: stateStore() = session.stateStore(); owner() = session.registered().instance();
    // contextActive() = session.status() == OPENING || session.status() == ACTIVE
    // inventory() throws IllegalStateException until session.inventory() set; after CLOSED throws too
}
// internal/context/PlainViewContextImpl.java — trivial concrete AbstractViewContext used by
// ViewService.contextOf (adds nothing)
// internal/context impls:
public final class OpenContextImpl extends AbstractViewContext implements OpenContext
    // records overrides; getters: @Nullable String overriddenTitle(); @Nullable Integer overriddenRows();
    // inventory()/update()/updateTitle() throw IllegalStateException naming the phase
public final class RenderContextImpl extends AbstractViewContext implements RenderContext
public final class UpdateContextImpl extends AbstractViewContext implements UpdateContext
public final class SlotClickContextImpl extends AbstractViewContext implements SlotClickContext
public final class CloseContextImpl extends AbstractViewContext implements CloseContext
    // update()/openView() throw IllegalStateException; close() is a no-op (already closing)

// internal/registry/RegisteredView.java
public final class RegisteredView {
    public RegisteredView(@NotNull Class<? extends View> type, @NotNull View instance, @NotNull ViewConfig config);
    public @NotNull Class<? extends View> type();
    public @NotNull View instance();
    public @NotNull ViewConfig config();
}

// internal/registry/ViewRegistry.java — @Component bean
// created with the no-arg constructor in Task 12; Task 17 ADDS the DI constructor + initialize()
public final class ViewRegistry {
    public ViewRegistry();                                                // test/bootstrap constructor (Task 12)
    public ViewRegistry(ViewDiscoveryService discoveryService);           // DI constructor (Task 17)
    public void initialize(@NotNull Context context) throws Exception;    // discovery + instantiate via DI + register each (Task 17)
    public void register(@NotNull View instance);   // order pinned: tokenTable().freeze() FIRST, then onInit(builder),
                                                    // then build/validate config; second register of same type → IllegalStateException
    public @NotNull Optional<RegisteredView> find(@NotNull Class<? extends View> type);
    public @NotNull Collection<RegisteredView> all();
}

// internal/discovery/ViewDiscoveryService.java — @Component bean, mirrors InventoryDiscoveryService
public final class ViewDiscoveryService {
    public @NotNull Set<Class<? extends View>> discoverFromPackage(@NotNull String basePackage);
    // logs SEVERE for @RegisterView classes not extending View (boot guard, §5.1)
}

// internal/engine/ViewEngine.java — @Component bean, sole session mutator
public final class ViewEngine {
    public ViewEngine(Plugin plugin, ViewRegistry views, SessionRegistry sessions,
                      SlotPainter painter, TitleUpdater titleUpdater);
    public void open(@NotNull Player player, @NotNull Class<? extends View> viewType, @NotNull ViewArguments arguments);
    public void close(@NotNull ViewSession session, @NotNull CloseReason reason);
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger);
    public void click(@NotNull ViewSession session, @NotNull InventoryClickEvent event);
    public void drag(@NotNull ViewSession session, @NotNull InventoryDragEvent event);
    public void bukkitClose(@NotNull ViewSession session, @NotNull InventoryCloseEvent event);
    public void flushDirty(@NotNull ViewSession session);      // STATE_CHANGE flush, cascade cap 8
    public void flushShared(@NotNull View owner);              // SharedState: flush every open session of this view
    public void defer(@NotNull ViewSession session, @NotNull Runnable op);  // end-of-tick via scheduler
    public boolean isInClickDispatch();                        // true while click() runs (drives deferral)
    public @NotNull Plugin plugin();
    public @NotNull TitleUpdater titleUpdater();
    public static void assertMainThread(@NotNull String operation); // IllegalStateException off-main
}

// internal/engine/phase/* — package-private collaborators constructed by ViewEngine, one public
// method each:
final class OpenPhase      { /* ViewSession openSession(Player, RegisteredView, ViewArguments) — steps §7.2–7.6 up to container */ }
final class FirstRenderPhase { /* void firstRender(ViewSession) — onFirstRender + initial paint + openInventory */ }
final class UpdatePhase    { /* void update(ViewSession, UpdateTrigger, Set<Integer> dirtyOrNull) */ }
final class ClickRoutingPhase { /* void route(ViewSession, InventoryClickEvent) — §6 policy + routing */ }
final class ClosePhase     { /* void close(ViewSession, CloseReason) — onClose + teardown + unregister */ }

// internal/listener/ViewListener.java — @Component implements Listener (auto-registered like
// CustomInventoryListener); bridges InventoryClickEvent/InventoryDragEvent/InventoryCloseEvent/
// PlayerQuitEvent/PluginDisableEvent into ViewEngine, looking sessions up in SessionRegistry.
public final class ViewListener implements Listener

// internal/schedule/ViewUpdateTask.java — Runnable; per-session, started by FirstRenderPhase when
// updateIntervalTicks > 0, cancelled by ClosePhase.
public final class ViewUpdateTask implements Runnable {
    public ViewUpdateTask(@NotNull ViewEngine engine, @NotNull ViewSession session);
}
```

### Pinned behavioral rules (from spec, restated for drafters)

1. **Open order** (§7): lookup → session+store create (OPENING) + bind/validate `initialState` keys → `onOpen` (cancel = zero side effects, previous view untouched) → commit: close previous session (REPLACED) → layout resolution → container creation → *(pagination init in Plan 2)* → `onFirstRender` → initial paint → `player.openInventory` → stale-close guard → ACTIVE → start update task.
2. **Click policy** (§6): config default cancel; per-component `cancelOnClick` override; handler `setCancelled` last-writer-wins; safety floor (`MOVE_TO_OTHER_INVENTORY` from bottom, `COLLECT_TO_CURSOR`, `HOTBAR_SWAP`/`HOTBAR_MOVE_AND_READD` into top) force-cancelled AFTER handlers regardless of their decision. Bottom clicks always pre-cancelled, delivered to view-level `onClick` only with `isPlayerInventory() == true`. Hidden components get no clicks (treated as component-less slot). Typed handler runs; untyped handler only when no typed handler matched. Handler throws → force-cancel + skip everything remaining (later handlers, view-level `onClick`, post-actions).
3. **Deferral** (§5.4): `close()`/`openView()`/`ViewService.open()` during click dispatch → deferred to end of tick (`scheduler.runTask`), session → TRANSITIONING immediately, further clicks swallowed (cancelled, not routed). `openView` inside `onClose` → SEVERE log, dropped.
4. **State** (§5.5): `MutableState.set` off-main throws `IllegalStateException`; foreign/closed context → `StaleContextException`; flush coalesced at end of engine entry point, ≤1 re-render per component per flush, cascade cap 8/tick then warn-and-stop. `SharedState` atomic; off-main flush scheduled to main, coalesced per view per tick.
5. **Errors** (§9): exact table — `onOpen` throws = treated as cancelled; `onFirstRender` throws = abort + `onClose(OPEN_FAILED)`; renderer/`displayIf` throw = keep previous content + rate-limited log; `onUpdate`/`onClose` throw = log, teardown completes.
6. **Threading** (§8): all engine entry points assert main thread.
7. **Close-event guard**: ignore `InventoryCloseEvent` whose `event.getInventory() != session.inventory()` (mirrors `CustomInventoryListener.onInventoryClose` lines 68–72).
8. Comments start lowercase; full Javadoc on public/protected API; no fully-qualified inline types; 4-space indent; license header on every new file.

---

<!-- TASKS START -->
### Task 1: Exceptions and ViewArguments

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/exception/UnknownViewException.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/exception/ViewConfigurationException.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/exception/StaleContextException.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/service/ViewArguments.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/service/ViewArgumentsTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewArgumentsTest {

    @Test
    void empty_hasNoKeys() {
        ViewArguments arguments = ViewArguments.empty();

        assertFalse(arguments.has("anything"));
        assertNull(arguments.get("anything", String.class));
    }

    @Test
    void of_singlePair_storesValue() {
        ViewArguments arguments = ViewArguments.of("name", "Steve");

        assertTrue(arguments.has("name"));
        assertEquals("Steve", arguments.get("name", String.class));
    }

    @Test
    void of_twoPairs_storesBothValues() {
        ViewArguments arguments = ViewArguments.of("name", "Steve", "count", 3);

        assertEquals("Steve", arguments.get("name", String.class));
        assertEquals(3, arguments.get("count", Integer.class));
    }

    @Test
    void builder_storesAllPairs() {
        ViewArguments arguments = ViewArguments.builder()
                .put("a", 1)
                .put("b", 2L)
                .put("c", "three")
                .build();

        assertEquals(1, arguments.get("a", Integer.class));
        assertEquals(2L, arguments.get("b", Long.class));
        assertEquals("three", arguments.get("c", String.class));
    }

    @Test
    void get_absentKey_returnsNull() {
        ViewArguments arguments = ViewArguments.of("name", "Steve");

        assertNull(arguments.get("missing", String.class));
    }

    @Test
    void get_wrongType_throwsNamingKeyAndTypes() {
        ViewArguments arguments = ViewArguments.of("count", 3);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> arguments.get("count", String.class));

        assertTrue(error.getMessage().contains("count"));
        assertTrue(error.getMessage().contains(String.class.getName()));
        assertTrue(error.getMessage().contains(Integer.class.getName()));
    }

    @Test
    void require_presentKey_returnsValue() {
        ViewArguments arguments = ViewArguments.of("count", 3);

        assertEquals(3, arguments.require("count", Integer.class));
    }

    @Test
    void require_absentKey_throwsNamingKey() {
        ViewArguments arguments = ViewArguments.empty();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> arguments.require("missing", Integer.class));

        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void require_wrongType_throws() {
        ViewArguments arguments = ViewArguments.of("count", 3);

        assertThrows(IllegalArgumentException.class,
                () -> arguments.require("count", String.class));
    }

    @Test
    void has_reportsPresence() {
        ViewArguments arguments = ViewArguments.of("name", "Steve");

        assertTrue(arguments.has("name"));
        assertFalse(arguments.has("other"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewArgumentsTest"
Expected: FAIL (compilation error: class ViewArguments does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.exception;

import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewService;

/**
 * Thrown when {@link ViewService#open} or a context navigation targets a view class
 * that is not registered.
 */
public final class UnknownViewException extends RuntimeException {

    /**
     * Creates the exception with a detail message.
     *
     * @param message the detail message
     */
    public UnknownViewException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public UnknownViewException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.exception;

/**
 * Thrown at registration or build time when a view configuration, layout or component
 * declaration violates a validation rule.
 */
public final class ViewConfigurationException extends RuntimeException {

    /**
     * Creates the exception with a detail message.
     *
     * @param message the detail message
     */
    public ViewConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ViewConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.exception;

/**
 * Thrown when a state token is accessed through a context belonging to a different view
 * class or to an already closed session.
 */
public final class StaleContextException extends RuntimeException {

    /**
     * Creates the exception with a detail message.
     *
     * @param message the detail message
     */
    public StaleContextException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public StaleContextException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, typed key-value arguments passed to a view when it is opened.
 *
 * <p>Use {@link #builder()} for more than two key-value pairs; the {@code of(...)}
 * overloads stop at two.
 */
public final class ViewArguments {

    private static final ViewArguments EMPTY = new ViewArguments(Collections.emptyMap());

    private final Map<String, Object> values;

    private ViewArguments(@NotNull Map<String, Object> values) {
        this.values = values;
    }

    /**
     * Returns the shared empty arguments instance.
     *
     * @return arguments containing no entries
     */
    public static @NotNull ViewArguments empty() {
        return EMPTY;
    }

    /**
     * Creates arguments containing a single entry.
     *
     * @param key   the entry key
     * @param value the entry value
     * @return immutable arguments with one entry
     */
    public static @NotNull ViewArguments of(@NotNull String key, @NotNull Object value) {
        return builder().put(key, value).build();
    }

    /**
     * Creates arguments containing two entries.
     *
     * @param k1 the first entry key
     * @param v1 the first entry value
     * @param k2 the second entry key
     * @param v2 the second entry value
     * @return immutable arguments with two entries
     */
    public static @NotNull ViewArguments of(@NotNull String k1, @NotNull Object v1,
                                            @NotNull String k2, @NotNull Object v2) {
        return builder().put(k1, v1).put(k2, v2).build();
    }

    /**
     * Creates a new builder for arguments with any number of entries.
     *
     * @return a fresh builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Returns the value bound to the given key, or {@code null} when absent.
     *
     * @param key  the entry key
     * @param type the expected value type
     * @param <T>  the value type
     * @return the typed value, or {@code null} when the key is absent
     * @throws IllegalArgumentException if a value is present but not assignable to {@code type}
     */
    public <T> @Nullable T get(@NotNull String key, @NotNull Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        return cast(key, type, value);
    }

    /**
     * Returns the value bound to the given key, failing when absent.
     *
     * @param key  the entry key
     * @param type the expected value type
     * @param <T>  the value type
     * @return the typed value, never {@code null}
     * @throws IllegalArgumentException if the key is absent or the value is not assignable to {@code type}
     */
    public <T> @NotNull T require(@NotNull String key, @NotNull Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required argument '" + key + "'");
        }
        return cast(key, type, value);
    }

    /**
     * Returns whether a value is bound to the given key.
     *
     * @param key the entry key
     * @return {@code true} when the key is present
     */
    public boolean has(@NotNull String key) {
        return values.containsKey(Objects.requireNonNull(key, "key"));
    }

    private static <T> T cast(String key, Class<T> type, Object value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("argument '" + key + "' is of type "
                    + value.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(value);
    }

    /**
     * Mutable accumulator for {@link ViewArguments}.
     */
    public static final class Builder {

        private final Map<String, Object> values = new HashMap<>();

        private Builder() {
        }

        /**
         * Adds an entry, replacing any previous value for the same key.
         *
         * @param key   the entry key
         * @param value the entry value
         * @return this builder
         */
        public @NotNull Builder put(@NotNull String key, @NotNull Object value) {
            values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Builds an immutable snapshot of the accumulated entries.
         *
         * @return the immutable arguments
         */
        public @NotNull ViewArguments build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new ViewArguments(Collections.unmodifiableMap(new HashMap<>(values)));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewArgumentsTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/exception/UnknownViewException.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/exception/ViewConfigurationException.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/exception/StaleContextException.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/service/ViewArguments.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/service/ViewArgumentsTest.java
git commit -m "feat(inventory-api): add v3 exceptions and ViewArguments"
```

### Task 2: Layout

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/Layout.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/layout/GridSlotsLayout.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/layout/OrderedSlotsLayout.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/LayoutTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.layout;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.GridSlotsLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.OrderedSlotsLayout;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutTest {

    @Test
    void ofGrid_returnsGridSlotsLayout() {
        assertTrue(Layout.ofGrid("         ") instanceof GridSlotsLayout);
    }

    @Test
    void ofGrid_blankGrid_hasNoSlots() {
        Layout layout = Layout.ofGrid("         ");

        assertEquals(Collections.emptyList(), layout.slots());
    }

    @Test
    void ofGrid_distinctChars_fillAlphabetically() {
        Layout layout = Layout.ofGrid("   CAB   ");

        // A sits at column 4, B at 5, C at 3; fill order is A, B, C
        assertEquals(Arrays.asList(4, 5, 3), layout.slots());
    }

    @Test
    void ofGrid_repeatedChars_keepOccurrenceOrder() {
        Layout layout = Layout.ofGrid(
                "  OO     ",
                " O       ");

        assertEquals(Arrays.asList(2, 3, 10), layout.slots());
    }

    @Test
    void ofGrid_mixedChars_groupByCharThenOccurrence() {
        Layout layout = Layout.ofGrid(
                " B A     ",
                " A B     ");

        // all A occurrences (slots 3, 10) come before all B occurrences (slots 1, 12)
        assertEquals(Arrays.asList(3, 10, 1, 12), layout.slots());
    }

    @Test
    void ofGrid_rejectsRowNotExactlyNineWide() {
        assertThrows(IllegalArgumentException.class, () -> Layout.ofGrid("        ")); // 8 chars
        assertThrows(IllegalArgumentException.class, () -> Layout.ofGrid("          ")); // 10 chars
        assertThrows(IllegalArgumentException.class, () -> Layout.ofGrid(
                "         ",
                "    O   ")); // second row 8 chars
    }

    @Test
    void ofSlots_returnsOrderedSlotsLayout() {
        assertTrue(Layout.ofSlots(0) instanceof OrderedSlotsLayout);
    }

    @Test
    void ofSlots_keepsExplicitOrder() {
        Layout layout = Layout.ofSlots(14, 10, 12);

        assertEquals(Arrays.asList(14, 10, 12), layout.slots());
    }

    @Test
    void ofSlots_acceptsBoundarySlots() {
        Layout layout = Layout.ofSlots(0, 53);

        assertEquals(Arrays.asList(0, 53), layout.slots());
    }

    @Test
    void ofSlots_rejectsOutOfBoundsSlots() {
        assertThrows(IllegalArgumentException.class, () -> Layout.ofSlots(-1));
        assertThrows(IllegalArgumentException.class, () -> Layout.ofSlots(54));
    }

    @Test
    void ofSlots_rejectsDuplicateSlots() {
        assertThrows(IllegalArgumentException.class, () -> Layout.ofSlots(3, 3));
    }

    @Test
    void slots_areUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> Layout.ofGrid("A        ").slots().add(9));
        assertThrows(UnsupportedOperationException.class,
                () -> Layout.ofSlots(0).slots().add(9));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=LayoutTest"
Expected: FAIL (compilation error: class Layout does not exist in package tech.guilhermekaua.spigotboot.inventoryapi.layout)

- [ ] **Step 3: Write minimal implementation**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.layout;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.GridSlotsLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.OrderedSlotsLayout;

import java.util.List;

/**
 * An ordered sequence of inventory slot positions used as a fill order, created from an
 * ASCII grid ({@link #ofGrid}) or from explicit slot indices ({@link #ofSlots}).
 */
public interface Layout {

    /**
     * The width of a chest inventory row.
     */
    int ROW_WIDTH = 9;

    /**
     * Returns the slot positions in fill order.
     *
     * @return an unmodifiable ordered list of slot indices
     */
    @NotNull List<Integer> slots();

    /**
     * Parses a row-by-row ASCII grid: {@code ' '} marks an empty slot, every other character
     * is a fill slot; fill order is alphabetical by character, then occurrence order.
     *
     * @param rows the grid rows, each exactly {@link #ROW_WIDTH} characters wide
     * @return the parsed layout
     * @throws IllegalArgumentException if a row is not exactly {@link #ROW_WIDTH} characters wide
     */
    static @NotNull Layout ofGrid(@NotNull String... rows) {
        return new GridSlotsLayout(rows);
    }

    /**
     * Creates a layout that fills slots in exactly the given order.
     *
     * @param slots the slot indices in fill order; each must be within 0-53 and unique
     * @return the layout
     * @throws IllegalArgumentException if an index is out of bounds or duplicated
     */
    static @NotNull Layout ofSlots(int... slots) {
        return new OrderedSlotsLayout(slots);
    }
}
```

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.layout;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Grid-parsed {@link Layout} backing {@link Layout#ofGrid}: {@code ' '} is empty, every
 * other character is a fill slot ordered alphabetically by character, then occurrence.
 */
@ApiStatus.Internal
public final class GridSlotsLayout implements Layout {

    private final List<Integer> slots;

    /**
     * Parses the given grid rows into an ordered fill sequence.
     *
     * @param rows the grid rows, each exactly {@link Layout#ROW_WIDTH} characters wide
     * @throws IllegalArgumentException if a row is not exactly {@link Layout#ROW_WIDTH} characters wide
     */
    public GridSlotsLayout(@NotNull String... rows) {
        List<int[]> named = new ArrayList<>();

        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            if (line.length() != ROW_WIDTH) {
                throw new IllegalArgumentException(
                        "layout row " + row + " must be exactly " + ROW_WIDTH
                                + " characters wide, but was " + line.length()
                );
            }

            for (int column = 0; column < ROW_WIDTH; column++) {
                char character = line.charAt(column);
                if (character != ' ') {
                    named.add(new int[]{character, row * ROW_WIDTH + column});
                }
            }
        }

        // stable sort groups occurrences of the same char while keeping row-major order within a group
        named.sort(Comparator.comparingInt((int[] entry) -> entry[0]));

        List<Integer> ordered = new ArrayList<>(named.size());
        for (int[] entry : named) {
            ordered.add(entry[1]);
        }
        this.slots = Collections.unmodifiableList(ordered);
    }

    @Override
    public @NotNull List<Integer> slots() {
        return slots;
    }
}
```

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.layout;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Explicit-order {@link Layout} backing {@link Layout#ofSlots}: the i-th given index is
 * the i-th fill position.
 */
@ApiStatus.Internal
public final class OrderedSlotsLayout implements Layout {

    private static final int MAX_SLOT_EXCLUSIVE = 6 * ROW_WIDTH;

    private final List<Integer> slots;

    /**
     * Creates a layout that fills slots in exactly the given order.
     *
     * @param slots the slot indices in fill order; each must be within 0-53 and unique
     * @throws IllegalArgumentException if an index is negative, {@code >= 54} or duplicated
     */
    public OrderedSlotsLayout(int... slots) {
        List<Integer> ordered = new ArrayList<>(slots.length);
        Set<Integer> seen = new HashSet<>();

        for (int slot : slots) {
            if (slot < 0 || slot >= MAX_SLOT_EXCLUSIVE) {
                throw new IllegalArgumentException(
                        "slot index " + slot + " is out of bounds (0-" + (MAX_SLOT_EXCLUSIVE - 1) + ")"
                );
            }
            if (!seen.add(slot)) {
                throw new IllegalArgumentException("duplicate slot index " + slot);
            }
            ordered.add(slot);
        }

        this.slots = Collections.unmodifiableList(ordered);
    }

    @Override
    public @NotNull List<Integer> slots() {
        return slots;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=LayoutTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/Layout.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/layout/GridSlotsLayout.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/layout/OrderedSlotsLayout.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/LayoutTest.java
git commit -m "feat(inventory-api): add v3 Layout with grid and ordered-slots implementations"
```

### Task 3: ViewConfig and ViewConfigBuilder

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/config/ViewConfig.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/config/ViewConfigBuilder.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/config/ViewConfigBuilderTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.config;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewConfigBuilderTest {

    @Test
    void build_appliesDefaults() {
        ViewConfig config = new ViewConfigBuilder().title("&aShop").rows(3).build();

        assertEquals("&aShop", config.title());
        assertEquals(3, config.rows());
        assertEquals(Collections.emptyList(), config.layout());
        assertTrue(config.cancelOnClick());
        assertTrue(config.cancelOnDrag());
        assertEquals(0L, config.updateIntervalTicks());
        assertTrue(config.applyPlaceholders());
    }

    @Test
    void setters_areFluentAndStored() {
        ViewConfig config = new ViewConfigBuilder()
                .title("Shop")
                .rows(2)
                .cancelOnClick(false)
                .cancelOnDrag(false)
                .scheduleUpdate(20L)
                .applyPlaceholders(false)
                .build();

        assertFalse(config.cancelOnClick());
        assertFalse(config.cancelOnDrag());
        assertEquals(20L, config.updateIntervalTicks());
        assertFalse(config.applyPlaceholders());
    }

    @Test
    void scheduleUpdate_nonPositiveDisables() {
        ViewConfig config = new ViewConfigBuilder().title("Shop").rows(1).scheduleUpdate(-5L).build();

        assertEquals(0L, config.updateIntervalTicks());
    }

    @Test
    void build_missingTitle_throws() {
        ViewConfigBuilder builder = new ViewConfigBuilder().rows(3);

        assertThrows(ViewConfigurationException.class, builder::build);
    }

    @Test
    void build_rowsOutsideChestRange_throws() {
        assertThrows(ViewConfigurationException.class,
                () -> new ViewConfigBuilder().title("Shop").rows(0).build());
        assertThrows(ViewConfigurationException.class,
                () -> new ViewConfigBuilder().title("Shop").rows(7).build());
    }

    @Test
    void build_layoutRowNotNineChars_throws() {
        ViewConfigBuilder builder = new ViewConfigBuilder().title("Shop").layout("        "); // 8 chars

        assertThrows(ViewConfigurationException.class, builder::build);
    }

    @Test
    void build_rowsAndLayoutInconsistent_throws() {
        ViewConfigBuilder builder = new ViewConfigBuilder()
                .title("Shop")
                .rows(3)
                .layout("         ");

        assertThrows(ViewConfigurationException.class, builder::build);
    }

    @Test
    void build_rowsAndLayoutConsistent_passes() {
        ViewConfig config = new ViewConfigBuilder()
                .title("Shop")
                .rows(2)
                .layout("         ", "   AAA   ")
                .build();

        assertEquals(2, config.rows());
        assertEquals(Arrays.asList("         ", "   AAA   "), config.layout());
    }

    @Test
    void build_neitherRowsNorLayout_throws() {
        ViewConfigBuilder builder = new ViewConfigBuilder().title("Shop");

        assertThrows(ViewConfigurationException.class, builder::build);
    }

    @Test
    void build_infersRowsFromLayout() {
        ViewConfig config = new ViewConfigBuilder()
                .title("Shop")
                .layout("         ", "         ", "         ")
                .build();

        assertEquals(3, config.rows());
    }

    @Test
    void layout_isUnmodifiable() {
        ViewConfig config = new ViewConfigBuilder().title("Shop").layout("         ").build();

        assertThrows(UnsupportedOperationException.class, () -> config.layout().add("         "));
    }

    @Test
    void withOverrides_appliesNonNullValuesOnNewInstance() {
        ViewConfig original = new ViewConfigBuilder().title("Shop").rows(3).cancelOnClick(false).build();

        ViewConfig overridden = original.withOverrides("Bank", 6);

        assertNotSame(original, overridden);
        assertEquals("Bank", overridden.title());
        assertEquals(6, overridden.rows());
        // non-overridable settings carry over unchanged
        assertFalse(overridden.cancelOnClick());
        // original is untouched
        assertEquals("Shop", original.title());
        assertEquals(3, original.rows());
    }

    @Test
    void withOverrides_nullValuesKeepOriginal() {
        ViewConfig original = new ViewConfigBuilder().title("Shop").rows(3).build();

        ViewConfig overridden = original.withOverrides(null, null);

        assertEquals("Shop", overridden.title());
        assertEquals(3, overridden.rows());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewConfigBuilderTest"
Expected: FAIL (compilation error: classes ViewConfig and ViewConfigBuilder do not exist)

- [ ] **Step 3: Write minimal implementation**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable, validated view configuration produced by {@link ViewConfigBuilder#build()};
 * frozen at registration and merged with per-open overrides via {@link #withOverrides}.
 */
public final class ViewConfig {

    private final String title;
    private final int rows;
    private final List<String> layout;
    private final boolean cancelOnClick;
    private final boolean cancelOnDrag;
    private final long updateIntervalTicks;
    private final boolean applyPlaceholders;

    ViewConfig(@NotNull String title, int rows, @NotNull List<String> layout,
               boolean cancelOnClick, boolean cancelOnDrag,
               long updateIntervalTicks, boolean applyPlaceholders) {
        this.title = title;
        this.rows = rows;
        this.layout = layout;
        this.cancelOnClick = cancelOnClick;
        this.cancelOnDrag = cancelOnDrag;
        this.updateIntervalTicks = updateIntervalTicks;
        this.applyPlaceholders = applyPlaceholders;
    }

    /**
     * Returns the inventory title (legacy color codes allowed).
     *
     * @return the title, never {@code null}
     */
    public @NotNull String title() {
        return title;
    }

    /**
     * Returns the resolved row count, explicit or inferred from the layout.
     *
     * @return the row count within 1-6
     */
    public int rows() {
        return rows;
    }

    /**
     * Returns the layout rows declared in {@code onInit}.
     *
     * @return an unmodifiable list of layout rows; empty when no layout was set
     */
    public @NotNull List<String> layout() {
        return layout;
    }

    /**
     * Returns whether clicks are cancelled by default (default {@code true}).
     *
     * @return the cancel-on-click default
     */
    public boolean cancelOnClick() {
        return cancelOnClick;
    }

    /**
     * Returns whether drags are cancelled by default (default {@code true}).
     *
     * @return the cancel-on-drag default
     */
    public boolean cancelOnDrag() {
        return cancelOnDrag;
    }

    /**
     * Returns the scheduled update interval in ticks; {@code 0} means disabled.
     *
     * @return the update interval in ticks
     */
    public long updateIntervalTicks() {
        return updateIntervalTicks;
    }

    /**
     * Returns whether placeholders are applied at paint time (default {@code true}).
     *
     * @return the apply-placeholders flag
     */
    public boolean applyPlaceholders() {
        return applyPlaceholders;
    }

    /**
     * Returns a copy of this config with the given non-null per-open overrides applied;
     * this instance is unchanged.
     *
     * @param title the title override, or {@code null} to keep the current title
     * @param rows  the rows override, or {@code null} to keep the current rows
     * @return a new config with the overrides applied
     */
    public @NotNull ViewConfig withOverrides(@Nullable String title, @Nullable Integer rows) {
        return new ViewConfig(
                title != null ? title : this.title,
                rows != null ? rows : this.rows,
                this.layout,
                this.cancelOnClick,
                this.cancelOnDrag,
                this.updateIntervalTicks,
                this.applyPlaceholders
        );
    }
}
```

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.config;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Fluent, mutable builder for {@link ViewConfig}, used in {@code View.onInit};
 * {@link #build()} validates and freezes the configuration.
 */
public final class ViewConfigBuilder {

    private String title;
    private Integer rows;
    private List<String> layout = Collections.emptyList();
    private boolean cancelOnClick = true;
    private boolean cancelOnDrag = true;
    private long updateIntervalTicks = 0L;
    private boolean applyPlaceholders = true;

    /**
     * Sets the inventory title (legacy color codes allowed).
     *
     * @param title the title
     * @return this builder
     */
    public @NotNull ViewConfigBuilder title(@NotNull String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    /**
     * Sets the explicit row count; optional when a layout is present (inferred).
     *
     * @param rows the row count, validated to 1-6 at {@link #build()}
     * @return this builder
     */
    public @NotNull ViewConfigBuilder rows(int rows) {
        this.rows = rows;
        return this;
    }

    /**
     * Sets the layout rows; each row must be exactly {@link Layout#ROW_WIDTH} characters,
     * validated at {@link #build()}.
     *
     * @param rows the layout rows
     * @return this builder
     */
    public @NotNull ViewConfigBuilder layout(@NotNull String... rows) {
        Objects.requireNonNull(rows, "rows");
        this.layout = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(rows)));
        return this;
    }

    /**
     * Sets whether clicks are cancelled by default (default {@code true}).
     *
     * @param cancel the cancel-on-click default
     * @return this builder
     */
    public @NotNull ViewConfigBuilder cancelOnClick(boolean cancel) {
        this.cancelOnClick = cancel;
        return this;
    }

    /**
     * Sets whether drags are cancelled by default (default {@code true}).
     *
     * @param cancel the cancel-on-drag default
     * @return this builder
     */
    public @NotNull ViewConfigBuilder cancelOnDrag(boolean cancel) {
        this.cancelOnDrag = cancel;
        return this;
    }

    /**
     * Schedules periodic updates; values {@code <= 0} disable scheduling (the default).
     *
     * @param intervalTicks the update interval in ticks
     * @return this builder
     */
    public @NotNull ViewConfigBuilder scheduleUpdate(long intervalTicks) {
        this.updateIntervalTicks = intervalTicks <= 0 ? 0L : intervalTicks;
        return this;
    }

    /**
     * Sets whether placeholders are applied at paint time (default {@code true}).
     *
     * @param apply the apply-placeholders flag
     * @return this builder
     */
    public @NotNull ViewConfigBuilder applyPlaceholders(boolean apply) {
        this.applyPlaceholders = apply;
        return this;
    }

    /**
     * Validates and freezes the configuration.
     *
     * @return the immutable config
     * @throws ViewConfigurationException if the title is missing, rows are outside 1-6,
     *                                    a layout row is not exactly {@link Layout#ROW_WIDTH} characters wide,
     *                                    rows and layout are inconsistent, or neither rows nor layout is set
     */
    public @NotNull ViewConfig build() {
        if (title == null) {
            throw new ViewConfigurationException("view title is required");
        }
        if (rows != null && (rows < 1 || rows > 6)) {
            throw new ViewConfigurationException("rows must be between 1 and 6, got " + rows);
        }
        for (int i = 0; i < layout.size(); i++) {
            String row = layout.get(i);
            if (row.length() != Layout.ROW_WIDTH) {
                throw new ViewConfigurationException(
                        "layout row " + i + " must be exactly " + Layout.ROW_WIDTH
                                + " characters wide, but was " + row.length()
                );
            }
        }
        if (rows == null && layout.isEmpty()) {
            throw new ViewConfigurationException("either rows or layout must be set");
        }
        if (rows != null && !layout.isEmpty() && rows != layout.size()) {
            throw new ViewConfigurationException(
                    "rows (" + rows + ") does not match layout height (" + layout.size() + ")"
            );
        }

        int resolvedRows = rows != null ? rows : layout.size();
        return new ViewConfig(title, resolvedRows, layout, cancelOnClick, cancelOnDrag,
                updateIntervalTicks, applyPlaceholders);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewConfigBuilderTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/config/ViewConfig.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/config/ViewConfigBuilder.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/config/ViewConfigBuilderTest.java
git commit -m "feat(inventory-api): add v3 ViewConfig and ViewConfigBuilder"
```

---

### Task 4: Public contracts (compile-only)

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/View.java (lifecycle-handler skeleton — required so the context interfaces compile; state factories are added in Task 5)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context/ViewContext.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context/OpenContext.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context/RenderContext.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context/UpdateContext.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context/SlotClickContext.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context/CloseContext.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context/UpdateTrigger.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context/CloseReason.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/state/StateToken.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/state/State.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/state/MutableState.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/state/SharedState.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/component/ItemComponentBuilder.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/annotation/RegisterView.java

- [ ] **Step 1: Write the contract files**

No test class (interfaces, enums, one annotation and the abstract handler skeleton only). Depends on `exception/*`, `service/ViewArguments` and `config/ViewConfig(Builder)` from Tasks 1–3.

**View.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;

/**
 * Base class for inventory views. Views are DI singletons discovered via
 * {@code @RegisterView} and opened through {@code ViewService}; subclasses override only the
 * lifecycle handlers they need. All handlers run on the main thread, invoked by the engine.
 *
 * <p>Contract: view fields hold only state tokens, injected collaborators and immutable
 * configuration — every per-player value lives in per-context state and is dropped when the
 * context closes.
 */
public abstract class View {

    /**
     * Configures this view; called once per class at registration. The resulting config is
     * validated and frozen afterwards.
     *
     * @param config the mutable config builder
     */
    protected void onInit(@NotNull ViewConfigBuilder config) {
    }

    /**
     * Called once per open, before any container exists. May cancel the open (zero side
     * effects) or override title/rows for this open only.
     *
     * @param context the open context
     */
    protected void onOpen(@NotNull OpenContext context) {
    }

    /**
     * Declares this session's components; called once per open, after the container is
     * created and before the first paint.
     *
     * @param context the render context
     */
    protected void onFirstRender(@NotNull RenderContext context) {
    }

    /**
     * Called on every update pass; inspect {@link UpdateContext#trigger()} for the cause.
     *
     * @param context the update context
     */
    protected void onUpdate(@NotNull UpdateContext context) {
    }

    /**
     * View-level click fallback: runs after component handlers for top-container clicks
     * and receives every bottom-inventory click with {@code isPlayerInventory() == true}.
     *
     * @param context the click context
     */
    protected void onClick(@NotNull SlotClickContext context) {
    }

    /**
     * Called when the session tears down, with the matching {@code CloseReason}. Throwing
     * here is caught and logged; teardown always completes.
     *
     * @param context the close context
     */
    protected void onClose(@NotNull CloseContext context) {
    }
}
```

**context/ViewContext.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.UUID;

/**
 * Base contract shared by every per-phase context handed to {@link View} lifecycle handlers.
 *
 * <p>A context is bound to one (player, open) session. The mutating methods
 * ({@link #update()}, {@link #close()}, {@link #updateTitle(String)},
 * {@link #openView(Class)}) are main-thread only and throw {@link IllegalStateException}
 * when invoked off-main.
 */
@ApiStatus.NonExtendable
public interface ViewContext {

    /**
     * Returns the player viewing this session.
     *
     * @return the viewer
     */
    @NotNull Player player();

    /**
     * Returns the unique id of {@link #player()}.
     *
     * @return the viewer's UUID
     */
    @NotNull UUID playerId();

    /**
     * Returns the view singleton this context belongs to.
     *
     * @return the owning view instance
     */
    @NotNull View view();

    /**
     * Returns the effective configuration of this session, with per-open overrides
     * recorded in {@code onOpen} already applied.
     *
     * @return the effective, immutable view config
     */
    @NotNull ViewConfig config();

    /**
     * Returns the plugin that owns the inventory-api runtime.
     *
     * @return the owning plugin
     */
    @NotNull Plugin plugin();

    /**
     * Returns the arguments this session was opened with; empty when none were passed.
     *
     * @return the open arguments
     */
    @NotNull ViewArguments arguments();

    /**
     * Returns the top container of this session.
     *
     * @return the Bukkit inventory backing this view
     * @throws IllegalStateException before the container is created (e.g. inside
     *         {@code onOpen}) and after the session closed
     */
    @NotNull Inventory inventory();

    /**
     * Returns whether this session is currently active (open, not transitioning or closed).
     *
     * @return {@code true} while the session is active
     */
    boolean isActive();

    /**
     * Schedules a full update pass ({@link UpdateTrigger#EXPLICIT}); multiple calls in the
     * same tick are coalesced. Main thread only.
     */
    void update();

    /**
     * Closes this session. When called during click dispatch the close is deferred to the
     * end of the current tick and further clicks are swallowed. Main thread only.
     */
    void close();

    /**
     * Updates the container title in place (no reopen) via the NMS title updater;
     * placeholders are applied for {@link #player()}. Main thread only.
     *
     * @param title the new title, legacy color codes supported
     */
    void updateTitle(@NotNull String title);

    /**
     * Navigates to another registered view: this session closes with
     * {@link CloseReason#REPLACED}, then the target opens. Deferred to the end of the tick
     * during click dispatch. Main thread only.
     *
     * @param target the registered view class to open
     * @throws UnknownViewException when the target class is not registered
     */
    void openView(@NotNull Class<? extends View> target);

    /**
     * Same as {@link #openView(Class)}, passing arguments to the target view.
     *
     * @param target    the registered view class to open
     * @param arguments the arguments handed to the target's contexts
     * @throws UnknownViewException when the target class is not registered
     */
    void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments);
}
```

**context/OpenContext.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Context for {@code View.onOpen}: runs before any container exists.
 *
 * <p>Phase validity: {@code inventory()}, {@code update()} and {@code updateTitle(String)}
 * throw {@link IllegalStateException} naming this phase. Cancelling aborts the open with
 * zero side effects — the player's current view, if any, stays untouched and protected.
 */
@ApiStatus.NonExtendable
public interface OpenContext extends ViewContext {

    /**
     * Overrides the configured title for this open only.
     *
     * @param title the per-open title, legacy color codes supported
     */
    void overrideTitle(@NotNull String title);

    /**
     * Overrides the configured row count for this open only.
     *
     * @param rows the per-open row count, 1 to 6
     */
    void overrideRows(int rows);

    /**
     * Aborts this open with zero side effects; the player's current view is untouched.
     */
    void cancelOpen();

    /**
     * Returns whether {@link #cancelOpen()} was called for this open.
     *
     * @return {@code true} when this open is cancelled
     */
    boolean isOpenCancelled();
}
```

**context/RenderContext.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;

/**
 * Context for {@code View.onFirstRender}: declares this session's components. Declaration
 * order is paint order. Main thread, engine-invoked, once per open.
 */
@ApiStatus.NonExtendable
public interface RenderContext extends ViewContext {

    /**
     * Starts a component bound to a single slot.
     *
     * @param slot the 0-based raw slot, less than {@code rows * 9}
     * @return the component builder for fluent configuration
     */
    @NotNull ItemComponentBuilder slot(int slot);

    /**
     * Starts a component bound to a single slot addressed by grid position.
     *
     * @param row    the 1-based row, 1 to 6
     * @param column the 1-based column, 1 to 9
     * @return the component builder
     */
    @NotNull ItemComponentBuilder slot(int row, int column);

    /**
     * Starts a single-slot component with a static item already set.
     *
     * @param slot the 0-based raw slot
     * @param item the static item to display
     * @return the component builder
     */
    @NotNull ItemComponentBuilder slot(int slot, @NotNull ItemStack item);

    /**
     * Starts one component applied to every slot bound to the given layout character.
     *
     * @param character a character present in the configured layout
     * @return the component builder
     */
    @NotNull ItemComponentBuilder layoutSlot(char character);

    /**
     * Starts a layout-character component with a static item already set.
     *
     * @param character a character present in the configured layout
     * @param item      the static item displayed on every bound slot
     * @return the component builder
     */
    @NotNull ItemComponentBuilder layoutSlot(char character, @NotNull ItemStack item);
}
```

**context/UpdateContext.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Context for {@code View.onUpdate}: fired by scheduled ticks, state flushes, explicit
 * updates and pagination settles. Main thread, engine-invoked.
 */
@ApiStatus.NonExtendable
public interface UpdateContext extends ViewContext {

    /**
     * Returns what triggered this update pass.
     *
     * @return the update trigger
     */
    @NotNull UpdateTrigger trigger();
}
```

**context/SlotClickContext.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context for component click handlers and {@code View.onClick}: one instance per
 * intercepted {@link InventoryClickEvent}. Main thread, engine-invoked.
 *
 * <p>Cancellation is three-layered (config default, per-component override, then
 * {@link #setCancelled(boolean)} — last writer wins); the safety floor for cross-inventory
 * moves is enforced after handlers regardless of their decision.
 */
@ApiStatus.NonExtendable
public interface SlotClickContext extends ViewContext {

    /**
     * Returns the raw slot id of the clicked container.
     *
     * @return the clicked raw slot
     */
    int slot();

    /**
     * Returns the Bukkit click type of this click.
     *
     * @return the click type
     */
    @NotNull ClickType clickType();

    /**
     * Returns the item on the clicked slot, if any.
     *
     * @return the clicked item, or {@code null} when the slot is empty
     */
    @Nullable ItemStack item();

    /**
     * Returns whether the click landed in the player's own (bottom) inventory. Bottom
     * clicks are always pre-cancelled and delivered only to the view-level {@code onClick}.
     *
     * @return {@code true} for bottom-inventory clicks
     */
    boolean isPlayerInventory();

    /**
     * Overrides the cancellation decision for this click; last writer wins. The safety
     * floor still force-cancels cross-inventory moves after handlers run.
     *
     * @param cancelled whether the underlying event should be cancelled
     */
    void setCancelled(boolean cancelled);

    /**
     * Returns the current cancellation decision: config/component policy applied before
     * handlers run, possibly overturned by {@link #setCancelled(boolean)}.
     *
     * @return {@code true} when the click is currently cancelled
     */
    boolean isCancelled();

    /**
     * Returns the underlying Bukkit event as an escape hatch.
     *
     * @return the raw click event
     */
    @NotNull InventoryClickEvent rawEvent();
}
```

**context/CloseContext.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Context for {@code View.onClose}: the session is tearing down.
 *
 * <p>Phase validity: {@code update()} and {@code openView(...)} throw
 * {@link IllegalStateException}; {@code close()} is a no-op because the session is already
 * closing.
 */
@ApiStatus.NonExtendable
public interface CloseContext extends ViewContext {

    /**
     * Returns why this session is closing.
     *
     * @return the close reason
     */
    @NotNull CloseReason reason();
}
```

**context/UpdateTrigger.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.context;

/**
 * Why an update pass runs; exposed through {@link UpdateContext#trigger()}.
 */
public enum UpdateTrigger {

    /** Scheduled tick update configured via {@code ViewConfigBuilder.scheduleUpdate(long)}. */
    SCHEDULED,

    /** Coalesced flush after one or more state tokens changed. */
    STATE_CHANGE,

    /** Explicit {@link ViewContext#update()} or service-driven update. */
    EXPLICIT,

    /** A pagination page settled (success, failure or timeout). */
    PAGINATION_SETTLE
}
```

**context/CloseReason.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.context;

/**
 * Why a session closed; exposed through {@link CloseContext#reason()}.
 */
public enum CloseReason {

    /** The player closed the container themselves (e.g. pressed Esc). */
    PLAYER,

    /** {@code ViewService.close} or {@link ViewContext#close()} was called. */
    API,

    /** Another view was opened for the same player, replacing this session. */
    REPLACED,

    /** The player disconnected. */
    DISCONNECT,

    /** The owning plugin is being disabled. */
    PLUGIN_DISABLE,

    /** {@code onFirstRender} threw and the open was aborted before the container was shown. */
    OPEN_FAILED
}
```

**state/StateToken.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.state;

/**
 * Marker for watchable per-view tokens; implemented by {@link State} and pagination
 * tokens. Pass tokens to {@code ItemComponentBuilder.updateOnStateChange(StateToken...)}
 * to re-render a component when the token changes.
 */
public interface StateToken {
}
```

**state/State.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;

/**
 * Read access to a per-context value identified by this token. Values live in the
 * context's session and are dropped when the context closes.
 *
 * @param <T> the value type
 */
public interface State<T> extends StateToken {

    /**
     * Reads this token's value for the given context. Callable from any thread, but values
     * are only coherent on the main thread.
     *
     * @param context an active context of the owning view
     * @return the current value, possibly {@code null}
     * @throws StaleContextException when the context belongs to another view or is closed
     */
    @Nullable T get(@NotNull ViewContext context);
}
```

**state/MutableState.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;

import java.util.function.UnaryOperator;

/**
 * Mutable per-context state. Writes are main-thread only and mark watching components
 * dirty; dirty tokens flush coalesced at the end of the current engine entry point.
 *
 * @param <T> the value type
 */
public interface MutableState<T> extends State<T> {

    /**
     * Writes this token's value for the given context and marks watchers dirty.
     *
     * @param context an active context of the owning view
     * @param value   the new value, possibly {@code null}
     * @throws StaleContextException when the context belongs to another view or is closed
     * @throws IllegalStateException when called off the main thread
     */
    void set(@NotNull ViewContext context, @Nullable T value);

    /**
     * Read-modify-write convenience: applies {@code fn} to the current value and stores
     * the result. Main thread only.
     *
     * @param context an active context of the owning view
     * @param fn      the function producing the new value from the current one
     * @throws StaleContextException when the context belongs to another view or is closed
     * @throws IllegalStateException when called off the main thread
     */
    void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn);
}
```

**state/SharedState.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * One value per view singleton, shared by all viewers; not context-keyed. All methods are
 * atomic and callable from any thread; the watcher flush for open sessions is marshalled
 * to the main thread and coalesced per view per tick.
 *
 * @param <T> the value type
 */
public interface SharedState<T> extends StateToken {

    /**
     * Returns the current shared value.
     *
     * @return the current value, possibly {@code null}
     */
    @Nullable T get();

    /**
     * Atomically replaces the shared value and triggers a watcher flush for every open
     * session of the owning view.
     *
     * @param value the new value, possibly {@code null}
     */
    void set(@Nullable T value);

    /**
     * Atomically updates the shared value with a compare-and-set loop; {@code fn} may run
     * more than once under contention and must be side-effect free.
     *
     * @param fn the function producing the new value from the current one
     */
    void update(@NotNull UnaryOperator<T> fn);
}
```

**component/ItemComponentBuilder.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.component;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent declaration of one item component, returned by
 * {@code RenderContext.slot(...)}/{@code layoutSlot(...)}. Declaration order is paint
 * order; all callbacks run on the main thread, engine-invoked. A component must declare an
 * item source via {@code item(...)} — one without fails at first render with
 * {@code ViewConfigurationException}.
 */
@ApiStatus.NonExtendable
public interface ItemComponentBuilder {

    /**
     * Sets a static item for this component.
     *
     * @param item the item to display
     * @return this builder
     */
    @NotNull ItemComponentBuilder item(@NotNull ItemStack item);

    /**
     * Sets a dynamic item renderer, re-evaluated on every re-render of this component.
     * Renderer failures keep the previous slot content and are logged rate-limited.
     *
     * @param renderer the per-render item factory
     * @return this builder
     */
    @NotNull ItemComponentBuilder item(@NotNull Function<ViewContext, ItemStack> renderer);

    /**
     * Shows this component only while the condition holds; a hidden component's slots are
     * cleared and it receives no clicks.
     *
     * @param condition evaluated on every re-render of this component
     * @return this builder
     */
    @NotNull ItemComponentBuilder displayIf(@NotNull Predicate<ViewContext> condition);

    /**
     * Re-renders this component whenever one of the given tokens changes, at most once per
     * flush. Dependencies are explicit — there is no automatic read tracking.
     *
     * @param tokens the state tokens to watch
     * @return this builder
     */
    @NotNull ItemComponentBuilder updateOnStateChange(@NotNull StateToken... tokens);

    /**
     * Sets the untyped click handler; it runs only when no per-{@link ClickType} handler
     * matched the click.
     *
     * @param handler the fallback click handler
     * @return this builder
     */
    @NotNull ItemComponentBuilder onClick(@NotNull Consumer<SlotClickContext> handler);

    /**
     * Sets the handler for one specific click type; it takes precedence over the untyped
     * handler.
     *
     * @param type    the click type to match
     * @param handler the click handler
     * @return this builder
     */
    @NotNull ItemComponentBuilder onClick(@NotNull ClickType type, @NotNull Consumer<SlotClickContext> handler);

    /**
     * Overrides the config-level click cancellation for this component's slots; handlers
     * may still overturn the decision via {@code SlotClickContext.setCancelled(boolean)}.
     *
     * @param cancel whether clicks on this component are pre-cancelled
     * @return this builder
     */
    @NotNull ItemComponentBuilder cancelOnClick(boolean cancel);

    /**
     * Closes the view after a click on this component; deferred to the end of the tick.
     *
     * @return this builder
     */
    @NotNull ItemComponentBuilder closeOnClick();

    /**
     * Navigates to another registered view after a click on this component; deferred to
     * the end of the tick.
     *
     * @param target the registered view class to open
     * @return this builder
     */
    @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target);

    /**
     * Same as {@link #openOnClick(Class)}, passing arguments to the target view.
     *
     * @param target    the registered view class to open
     * @param arguments the arguments handed to the target's contexts
     * @return this builder
     */
    @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments);
}
```

**annotation/RegisterView.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.annotation;

import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.SpigotBootDiscoveryCategory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link tech.guilhermekaua.spigotboot.inventoryapi.View} subclass for boot-time
 * discovery and registration, after which it can be opened through
 * {@code ViewService#open(Player, Class)}.
 *
 * <p>Annotated classes are instantiated once via the dependency manager. A class carrying
 * this annotation that does not extend {@code View} is logged SEVERE at boot and skipped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpigotBootDiscoveryCategory(value = DiscoveryCategories.INVENTORY, kind = SpigotBootDiscoveryCategory.Kind.ANNOTATION)
public @interface RegisterView {
}
```

- [ ] **Step 2: Run compile check**
Run: mvnw.cmd -pl modules/inventory-api/api -am test-compile
Expected: BUILD SUCCESS
- [ ] **Step 3: Commit**
```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/View.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/context modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/state modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/component/ItemComponentBuilder.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/annotation/RegisterView.java
git commit -m "feat(inventory-api): add v3 public view, context, state and component contracts"
```

### Task 5: View base class + TokenTable

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/View.java (skeleton from Task 4 gains state factories + token table)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/TokenTable.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/MutableStateImpl.java (skeleton; bodies in Task 6)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/LazyStateImpl.java (skeleton; bodies in Task 6)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/InitialStateImpl.java (skeleton; bodies in Task 6)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/SharedStateImpl.java (skeleton; bodies in Task 6)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/ViewTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.State;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewTest {

    private static final class BlankView extends View {
    }

    @Test
    void stateFactories_registerDistinctTokensWithSequentialIds() {
        BlankView view = new BlankView();
        assertEquals(0, view.tokenTable().size());
        assertFalse(view.tokenTable().isFrozen());

        MutableState<String> first = view.mutableState("a");
        assertEquals(1, view.tokenTable().size());
        State<Integer> second = view.lazyState(ctx -> 1);
        assertEquals(2, view.tokenTable().size());
        MutableState<Integer> third = view.initialState("count", Integer.class);
        assertEquals(3, view.tokenTable().size());
        SharedState<String> fourth = view.sharedState("shared");
        assertEquals(4, view.tokenTable().size());
        MutableState<List<String>> fifth = view.mutableState(ctx -> new ArrayList<>());
        assertEquals(5, view.tokenTable().size());

        List<StateToken> tokens = view.tokenTable().tokens();
        assertSame(first, tokens.get(0));
        assertSame(second, tokens.get(1));
        assertSame(third, tokens.get(2));
        assertSame(fourth, tokens.get(3));
        assertSame(fifth, tokens.get(4));
    }

    @Test
    void stateFactories_afterFreeze_throwIllegalStateException() {
        BlankView view = new BlankView();
        view.tokenTable().freeze();
        assertTrue(view.tokenTable().isFrozen());

        assertThrows(IllegalStateException.class, () -> view.mutableState("late"));
        assertThrows(IllegalStateException.class, () -> view.mutableState(ctx -> "late"));
        assertThrows(IllegalStateException.class, () -> view.lazyState(ctx -> "late"));
        assertThrows(IllegalStateException.class, () -> view.initialState("key", String.class));
        assertThrows(IllegalStateException.class, () -> view.sharedState("late"));
    }

    @Test
    void tokenTable_registerAfterFreeze_throwsIllegalStateException() {
        TokenTable table = new BlankView().tokenTable();
        table.freeze();

        assertThrows(IllegalStateException.class, () -> table.register(new StateToken() {
        }));
    }

    @Test
    void tokenTable_tokensListIsUnmodifiable() {
        BlankView view = new BlankView();
        view.mutableState("a");

        assertThrows(UnsupportedOperationException.class, () -> view.tokenTable().tokens().clear());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewTest"
Expected: FAIL (compilation error: class TokenTable does not exist; method mutableState not found on View)
- [ ] **Step 3: Write minimal implementation**

**internal/state/TokenTable.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Per-view registry of state tokens. Tokens register during view construction and receive
 * sequential ids; registration is frozen once the view is registered.
 */
@ApiStatus.Internal
public final class TokenTable {

    private final List<StateToken> tokens = new ArrayList<>();
    private boolean frozen;

    /**
     * Registers a token and assigns its id.
     *
     * @param token the token to register
     * @return the assigned sequential id, starting at 0
     * @throws IllegalStateException when the table is already frozen
     */
    public int register(@NotNull StateToken token) {
        Objects.requireNonNull(token, "token");
        if (frozen) {
            throw new IllegalStateException("state factories are only legal in field initializers or the constructor; "
                    + "the token table is frozen once the view is registered");
        }
        tokens.add(token);
        return tokens.size() - 1;
    }

    /**
     * Freezes this table; further {@link #register(StateToken)} calls throw.
     */
    public void freeze() {
        frozen = true;
    }

    /**
     * Returns whether {@link #freeze()} was called.
     *
     * @return {@code true} once frozen
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Returns the number of registered tokens.
     *
     * @return the token count
     */
    public int size() {
        return tokens.size();
    }

    /**
     * Returns the registered tokens in id order.
     *
     * @return an unmodifiable view of the token list
     */
    public @NotNull List<StateToken> tokens() {
        return Collections.unmodifiableList(tokens);
    }
}
```

**internal/state/MutableStateImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Mutable per-context state token; value storage lives in the session's state store.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class MutableStateImpl<T> implements MutableState<T> {

    private final View owner;
    private final Function<ViewContext, T> initialValue;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner        the view declaring the token
     * @param table        the owner's token table
     * @param initialValue the per-context initial value factory
     * @throws IllegalStateException when the table is already frozen
     */
    public MutableStateImpl(@NotNull View owner, @NotNull TokenTable table,
                            @NotNull Function<ViewContext, T> initialValue) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.initialValue = Objects.requireNonNull(initialValue, "initialValue");
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
    public @Nullable T get(@NotNull ViewContext context) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }

    @Override
    public void set(@NotNull ViewContext context, @Nullable T value) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }

    @Override
    public void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }
}
```

**internal/state/LazyStateImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.state.State;

import java.util.Objects;
import java.util.function.Function;

/**
 * Read-only state token computed once per context on first read.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class LazyStateImpl<T> implements State<T> {

    private final View owner;
    private final Function<ViewContext, T> computation;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner       the view declaring the token
     * @param table       the owner's token table
     * @param computation the once-per-context computation
     * @throws IllegalStateException when the table is already frozen
     */
    public LazyStateImpl(@NotNull View owner, @NotNull TokenTable table,
                         @NotNull Function<ViewContext, T> computation) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.computation = Objects.requireNonNull(computation, "computation");
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
    public @Nullable T get(@NotNull ViewContext context) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }
}
```

**internal/state/InitialStateImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Mutable state token bound from {@code ViewArguments} at open; the argument type is
 * validated at the open site. An absent key reads as {@code null} until set.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class InitialStateImpl<T> implements MutableState<T> {

    private final View owner;
    private final String key;
    private final Class<T> type;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner the view declaring the token
     * @param table the owner's token table
     * @param key   the {@code ViewArguments} key bound at open
     * @param type  the expected argument type, validated at the open site
     * @throws IllegalStateException when the table is already frozen
     */
    public InitialStateImpl(@NotNull View owner, @NotNull TokenTable table,
                            @NotNull String key, @NotNull Class<T> type) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
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

    /**
     * Returns the {@code ViewArguments} key this token binds at open.
     *
     * @return the argument key
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * Returns the expected argument type, validated at the open site.
     *
     * @return the value type
     */
    public @NotNull Class<T> type() {
        return type;
    }

    @Override
    public @Nullable T get(@NotNull ViewContext context) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }

    @Override
    public void set(@NotNull ViewContext context, @Nullable T value) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }

    @Override
    public void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }
}
```

**internal/state/SharedStateImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Shared state token: one atomic value per view singleton, visible to all viewers.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class SharedStateImpl<T> implements SharedState<T> {

    private final View owner;
    private final AtomicReference<T> value;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner        the view declaring the token
     * @param table        the owner's token table
     * @param initialValue the initial shared value, possibly {@code null}
     * @throws IllegalStateException when the table is already frozen
     */
    public SharedStateImpl(@NotNull View owner, @NotNull TokenTable table, @Nullable T initialValue) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.value = new AtomicReference<>(initialValue);
        this.id = Objects.requireNonNull(table, "table").register(this);
    }

    /**
     * Returns the table-assigned token id.
     *
     * @return the token id
     */
    public int id() {
        return id;
    }

    @Override
    public @Nullable T get() {
        throw new UnsupportedOperationException("implemented in Task 6");
    }

    @Override
    public void set(@Nullable T value) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }

    @Override
    public void update(@NotNull UnaryOperator<T> fn) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }
}
```

**View.java** (complete replacement of the Task 4 skeleton)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.InitialStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.LazyStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.MutableStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.SharedStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.TokenTable;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.State;

import java.util.function.Function;

/**
 * Base class for inventory views. Views are DI singletons discovered via
 * {@code @RegisterView} and opened through {@code ViewService}; subclasses override only the
 * lifecycle handlers they need. All handlers run on the main thread, invoked by the engine.
 *
 * <p>Contract: view fields hold only state tokens, injected collaborators and immutable
 * configuration — every per-player value lives in per-context state and is dropped when the
 * context closes. State factories are legal only in field initializers or the constructor;
 * after registration freezes the token table they throw {@link IllegalStateException}.
 */
public abstract class View {

    private final TokenTable tokenTable = new TokenTable();

    /**
     * Configures this view; called once per class at registration. The resulting config is
     * validated and frozen afterwards.
     *
     * @param config the mutable config builder
     */
    protected void onInit(@NotNull ViewConfigBuilder config) {
    }

    /**
     * Called once per open, before any container exists. May cancel the open (zero side
     * effects) or override title/rows for this open only.
     *
     * @param context the open context
     */
    protected void onOpen(@NotNull OpenContext context) {
    }

    /**
     * Declares this session's components; called once per open, after the container is
     * created and before the first paint.
     *
     * @param context the render context
     */
    protected void onFirstRender(@NotNull RenderContext context) {
    }

    /**
     * Called on every update pass; inspect {@link UpdateContext#trigger()} for the cause.
     *
     * @param context the update context
     */
    protected void onUpdate(@NotNull UpdateContext context) {
    }

    /**
     * View-level click fallback: runs after component handlers for top-container clicks
     * and receives every bottom-inventory click with {@code isPlayerInventory() == true}.
     *
     * @param context the click context
     */
    protected void onClick(@NotNull SlotClickContext context) {
    }

    /**
     * Called when the session tears down, with the matching {@code CloseReason}. Throwing
     * here is caught and logged; teardown always completes.
     *
     * @param context the close context
     */
    protected void onClose(@NotNull CloseContext context) {
    }

    /**
     * Declares a per-context mutable value seeded with a shared initial value. The initial
     * object is shared by every context and must be immutable; use
     * {@link #mutableState(Function)} for mutable initials such as collections.
     *
     * @param initialValue the shared initial value, possibly {@code null}
     * @param <T>          the value type
     * @return the state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> MutableState<T> mutableState(@Nullable T initialValue) {
        return new MutableStateImpl<>(this, tokenTable, context -> initialValue);
    }

    /**
     * Declares a per-context mutable value whose initial is computed per context on first
     * read, e.g. {@code mutableState(ctx -> new ArrayList<>())}.
     *
     * @param initialValue the per-context initial value factory
     * @param <T>          the value type
     * @return the state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> MutableState<T> mutableState(@NotNull Function<ViewContext, T> initialValue) {
        return new MutableStateImpl<>(this, tokenTable, initialValue);
    }

    /**
     * Declares a read-only value computed once per context on the first read, on the main
     * thread, and stored thereafter.
     *
     * @param computation the once-per-context computation
     * @param <T>         the value type
     * @return the state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> State<T> lazyState(@NotNull Function<ViewContext, T> computation) {
        return new LazyStateImpl<>(this, tokenTable, computation);
    }

    /**
     * Declares a mutable value bound from {@code ViewArguments} at open; the type is
     * validated at the open site. An absent key reads as {@code null} until set.
     *
     * @param key  the argument key bound at open
     * @param type the expected argument type
     * @param <T>  the value type
     * @return the state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> MutableState<T> initialState(@NotNull String key, @NotNull Class<T> type) {
        return new InitialStateImpl<>(this, tokenTable, key, type);
    }

    /**
     * Declares one atomic value per view singleton, shared by all viewers and writable
     * from any thread.
     *
     * @param initialValue the initial shared value, possibly {@code null}
     * @param <T>          the value type
     * @return the shared state token
     * @throws IllegalStateException when called after registration froze the token table
     */
    protected final <T> SharedState<T> sharedState(@Nullable T initialValue) {
        return new SharedStateImpl<>(this, tokenTable, initialValue);
    }

    /**
     * Returns this view's token table; used by the engine to size per-session state
     * storage and to freeze token registration.
     *
     * @return the token registry of this view instance
     */
    @ApiStatus.Internal
    public final @NotNull TokenTable tokenTable() {
        return tokenTable;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewTest"
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/View.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/ViewTest.java
git commit -m "feat(inventory-api): add View state factories backed by an internal token table"
```

### Task 6: State impls + StateStore

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/StateStore.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/StateBackedContext.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/ContextStateAccess.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/MutableStateImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/LazyStateImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/InitialStateImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/SharedStateImpl.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/StateStoreTest.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/StateImplTest.java

- [ ] **Step 1: Write the failing test (StateStore)**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {

    @Test
    void get_withoutSet_returnsNull() {
        StateStore store = new StateStore(3);

        assertNull(store.get(0));
        assertNull(store.get(2));
    }

    @Test
    void setAndGet_roundTripById() {
        StateStore store = new StateStore(3);

        store.set(1, "value");
        store.set(2, 42);

        assertEquals("value", store.get(1));
        assertEquals(42, store.get(2));
        assertNull(store.get(0));
    }

    @Test
    void set_overwritesPreviousValue() {
        StateStore store = new StateStore(1);

        store.set(0, "first");
        store.set(0, "second");

        assertEquals("second", store.get(0));
    }

    @Test
    void markDirty_setsHasDirty() {
        StateStore store = new StateStore(2);
        assertFalse(store.hasDirty());

        store.markDirty(1);

        assertTrue(store.hasDirty());
    }

    @Test
    void drainDirty_returnsMarkedIdsAndClears() {
        StateStore store = new StateStore(4);
        store.markDirty(0);
        store.markDirty(3);
        // duplicate marks coalesce
        store.markDirty(0);

        Set<Integer> drained = store.drainDirty();

        assertEquals(new HashSet<>(Arrays.asList(0, 3)), drained);
        assertFalse(store.hasDirty());
        assertTrue(store.drainDirty().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=StateStoreTest"
Expected: FAIL (compilation error: class StateStore does not exist)
- [ ] **Step 3: Write minimal implementation**

**internal/state/StateStore.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-session state storage: an array indexed by token id plus the set of dirty token ids
 * awaiting the next coalesced flush. Owned by the session and dropped with it.
 */
@ApiStatus.Internal
public final class StateStore {

    private final Object[] values;
    private final Set<Integer> dirty = new LinkedHashSet<>();

    /**
     * Creates storage sized for a view's token table.
     *
     * @param size the token count of the owning view
     */
    public StateStore(int size) {
        this.values = new Object[size];
    }

    /**
     * Returns the stored value for a token id.
     *
     * @param id the token id
     * @return the stored value, or {@code null} when nothing was stored
     */
    public @Nullable Object get(int id) {
        return values[id];
    }

    /**
     * Stores a value for a token id, replacing any previous value.
     *
     * @param id    the token id
     * @param value the value to store, possibly {@code null}
     */
    public void set(int id, @Nullable Object value) {
        values[id] = value;
    }

    /**
     * Marks a token id dirty for the next flush; duplicate marks coalesce.
     *
     * @param id the token id
     */
    public void markDirty(int id) {
        dirty.add(id);
    }

    /**
     * Returns the dirty token ids and clears the dirty set.
     *
     * @return the ids marked dirty since the last drain
     */
    public @NotNull Set<Integer> drainDirty() {
        Set<Integer> drained = new LinkedHashSet<>(dirty);
        dirty.clear();
        return drained;
    }

    /**
     * Returns whether any token id is currently marked dirty.
     *
     * @return {@code true} when a flush is pending
     */
    public boolean hasDirty() {
        return !dirty.isEmpty();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=StateStoreTest"
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/StateStore.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/StateStoreTest.java
git commit -m "feat(inventory-api): add per-session state store with dirty tracking"
```

- [ ] **Step 6: Write the failing test (state token impls)**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateImplTest {

    private static final class OwnerView extends View {
    }

    /**
     * Minimal context backed by a real {@link StateStore}; pins the seam that
     * {@code AbstractViewContext} implements in the internal context layer.
     */
    private static final class FakeViewContext implements ViewContext, StateBackedContext {

        private final View owner;
        private final StateStore store;
        private boolean active = true;

        FakeViewContext(View owner, StateStore store) {
            this.owner = owner;
            this.store = store;
        }

        void deactivate() {
            active = false;
        }

        @Override
        public @NotNull StateStore stateStore() {
            return store;
        }

        @Override
        public @NotNull View owner() {
            return owner;
        }

        @Override
        public boolean contextActive() {
            return active;
        }

        @Override
        public @NotNull View view() {
            return owner;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        // remaining ViewContext methods are not exercised by state tokens
        @Override
        public @NotNull Player player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull UUID playerId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull ViewConfig config() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull Plugin plugin() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull ViewArguments arguments() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull Inventory inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateTitle(@NotNull String title) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void openView(@NotNull Class<? extends View> target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void mutableState_setGetRoundTripPerContext() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        assertEquals("initial", token.get(context));
        token.set(context, "changed");
        assertEquals("changed", token.get(context));
    }

    @Test
    void mutableState_explicitNullIsNotReinitialized() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        token.set(context, null);

        assertNull(token.get(context));
    }

    @Test
    void mutableState_isolatesValuesPerContext() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        FakeViewContext first = new FakeViewContext(view, new StateStore(view.tokenTable().size()));
        FakeViewContext second = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        token.set(first, "first-value");

        assertEquals("first-value", token.get(first));
        assertEquals("initial", token.get(second));
    }

    @Test
    void mutableState_setMarksTokenDirty() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        StateStore store = new StateStore(view.tokenTable().size());
        FakeViewContext context = new FakeViewContext(view, store);

        assertFalse(store.hasDirty());
        token.set(context, "changed");

        assertTrue(store.hasDirty());
        assertEquals(Collections.singleton(token.id()), store.drainDirty());
    }

    @Test
    void mutableState_updateAppliesFunctionToCurrentValue() {
        OwnerView view = new OwnerView();
        MutableStateImpl<Integer> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> 0);
        FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        token.update(context, value -> value + 1);
        token.update(context, value -> value + 1);

        assertEquals(2, token.get(context));
    }

    @Test
    void foreignOwnersContext_throwsStaleContextException() {
        OwnerView owner = new OwnerView();
        OwnerView foreign = new OwnerView();
        MutableStateImpl<String> token = new MutableStateImpl<>(owner, owner.tokenTable(), ctx -> "initial");
        FakeViewContext foreignContext = new FakeViewContext(foreign, new StateStore(1));

        assertThrows(StaleContextException.class, () -> token.get(foreignContext));
        assertThrows(StaleContextException.class, () -> token.set(foreignContext, "x"));
    }

    @Test
    void closedContext_throwsStaleContextException() {
        OwnerView view = new OwnerView();
        MutableStateImpl<String> mutable = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
        LazyStateImpl<String> lazy = new LazyStateImpl<>(view, view.tokenTable(), ctx -> "lazy");
        FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        context.deactivate();

        assertThrows(StaleContextException.class, () -> mutable.get(context));
        assertThrows(StaleContextException.class, () -> mutable.set(context, "x"));
        assertThrows(StaleContextException.class, () -> lazy.get(context));
    }

    @Test
    void lazyState_computesOncePerContext() {
        OwnerView view = new OwnerView();
        AtomicInteger calls = new AtomicInteger();
        LazyStateImpl<String> lazy = new LazyStateImpl<>(view, view.tokenTable(),
                ctx -> "v" + calls.incrementAndGet());
        FakeViewContext first = new FakeViewContext(view, new StateStore(view.tokenTable().size()));
        FakeViewContext second = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

        assertEquals("v1", lazy.get(first));
        assertEquals("v1", lazy.get(first));
        assertEquals(1, calls.get());

        assertEquals("v2", lazy.get(second));
        assertEquals(2, calls.get());
    }

    @Test
    void initialState_returnsNullUntilSet_thenRoundTrips() {
        OwnerView view = new OwnerView();
        InitialStateImpl<Integer> token = new InitialStateImpl<>(view, view.tokenTable(), "count", Integer.class);
        StateStore store = new StateStore(view.tokenTable().size());
        FakeViewContext context = new FakeViewContext(view, store);

        assertNull(token.get(context));
        assertEquals("count", token.key());
        assertSame(Integer.class, token.type());

        token.set(context, 5);

        assertEquals(5, token.get(context));
        assertEquals(Collections.singleton(token.id()), store.drainDirty());
    }

    @Test
    void sharedState_getSetUpdateRoundTrip() {
        OwnerView view = new OwnerView();
        SharedStateImpl<Integer> shared = new SharedStateImpl<>(view, view.tokenTable(), 0);

        assertEquals(0, shared.get());
        shared.set(10);
        assertEquals(10, shared.get());
        shared.update(value -> value + 5);
        assertEquals(15, shared.get());
    }

    @Test
    void sharedState_setAndUpdateInvokeFlushHook() {
        OwnerView view = new OwnerView();
        SharedStateImpl<String> shared = new SharedStateImpl<>(view, view.tokenTable(), "a");
        AtomicInteger flushes = new AtomicInteger();
        shared.flushHook = flushes::incrementAndGet;

        shared.set("b");
        assertEquals(1, flushes.get());

        shared.update(value -> value + "c");
        assertEquals(2, flushes.get());
        assertEquals("bc", shared.get());
    }

    @Test
    void sharedState_concurrentUpdatesAreAtomic() throws Exception {
        OwnerView view = new OwnerView();
        SharedStateImpl<Integer> counter = new SharedStateImpl<>(view, view.tokenTable(), 0);
        int threads = 4;
        int increments = 250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch done = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    for (int j = 0; j < increments; j++) {
                        counter.update(value -> value + 1);
                    }
                    done.countDown();
                });
            }
            assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent updates did not finish in time");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(threads * increments, counter.get());
    }

    @Nested
    class MainThreadGuard {

        @BeforeEach
        void setUp() {
            MockBukkit.mock();
        }

        @AfterEach
        void tearDown() {
            MockBukkit.unmock();
        }

        @Test
        void set_offMainThread_throwsIllegalStateException() throws Exception {
            OwnerView view = new OwnerView();
            MutableStateImpl<String> token = new MutableStateImpl<>(view, view.tokenTable(), ctx -> "initial");
            FakeViewContext context = new FakeViewContext(view, new StateStore(view.tokenTable().size()));

            // the test thread created the mock server, so it is the primary thread
            token.set(context, "on-main");
            assertEquals("on-main", token.get(context));

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                AtomicReference<Throwable> thrown = new AtomicReference<>();
                executor.submit(() -> {
                    try {
                        token.set(context, "off-main");
                    } catch (Throwable t) {
                        thrown.set(t);
                    }
                }).get(5, TimeUnit.SECONDS);
                assertTrue(thrown.get() instanceof IllegalStateException,
                        "off-main set must throw IllegalStateException, got " + thrown.get());
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
```

- [ ] **Step 7: Run test to verify it fails**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=StateImplTest"
Expected: FAIL (compilation error: class StateBackedContext does not exist)
- [ ] **Step 8: Write minimal implementation**

**internal/state/StateBackedContext.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;

/**
 * Seam between state token impls and context implementations: a context exposing the
 * session's state store, owning view and liveness. Implemented by
 * {@code AbstractViewContext} in the internal context layer (plan task 11); tests may
 * implement it directly against a real {@link StateStore}.
 */
@ApiStatus.Internal
public interface StateBackedContext {

    /**
     * Returns the per-session state storage backing this context.
     *
     * @return the state store
     */
    @NotNull StateStore stateStore();

    /**
     * Returns the view singleton owning this context, used for foreign-token checks.
     *
     * @return the owning view
     */
    @NotNull View owner();

    /**
     * Returns whether this context may still access state; {@code false} once closed.
     *
     * @return {@code true} while state access is legal
     */
    boolean contextActive();
}
```

**internal/state/ContextStateAccess.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;

/**
 * Package-visible resolver from a public {@link ViewContext} to its state backing; state
 * token impls never cast to concrete context classes, only to {@link StateBackedContext}.
 */
final class ContextStateAccess {

    private ContextStateAccess() {
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

**internal/state/MutableStateImpl.java** (complete replacement)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Mutable per-context state token; values live in the session's {@link StateStore}. The
 * initial value is computed per context on the first read; an explicitly stored
 * {@code null} is kept (not re-initialized) via a private sentinel.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class MutableStateImpl<T> implements MutableState<T> {

    private static final Object NULL_VALUE = new Object();

    private final View owner;
    private final Function<ViewContext, T> initialValue;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner        the view declaring the token
     * @param table        the owner's token table
     * @param initialValue the per-context initial value factory
     * @throws IllegalStateException when the table is already frozen
     */
    public MutableStateImpl(@NotNull View owner, @NotNull TokenTable table,
                            @NotNull Function<ViewContext, T> initialValue) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.initialValue = Objects.requireNonNull(initialValue, "initialValue");
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
    @SuppressWarnings("unchecked")
    public @Nullable T get(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        StateStore store = storeFor(context);
        Object raw = store.get(id);
        if (raw == null) {
            T computed = initialValue.apply(context);
            raw = computed == null ? NULL_VALUE : computed;
            store.set(id, raw);
        }
        return raw == NULL_VALUE ? null : (T) raw;
    }

    @Override
    public void set(@NotNull ViewContext context, @Nullable T value) {
        Objects.requireNonNull(context, "context");
        StateStore store = storeFor(context);
        assertMainThread();
        store.set(id, value == null ? NULL_VALUE : value);
        store.markDirty(id);
    }

    @Override
    public void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn) {
        Objects.requireNonNull(fn, "fn");
        set(context, fn.apply(get(context)));
    }

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

    private static void assertMainThread() {
        // the server null-check keeps pure unit tests (no Bukkit) working on any thread
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MutableState.set/update must run on the main thread");
        }
    }
}
```

**internal/state/LazyStateImpl.java** (complete replacement)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.state.State;

import java.util.Objects;
import java.util.function.Function;

/**
 * Read-only state token computed once per context on the first read and stored in the
 * session's {@link StateStore}; a {@code null} result is cached via a private sentinel so
 * the computation never re-runs for that context.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class LazyStateImpl<T> implements State<T> {

    private static final Object NULL_VALUE = new Object();

    private final View owner;
    private final Function<ViewContext, T> computation;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner       the view declaring the token
     * @param table       the owner's token table
     * @param computation the once-per-context computation
     * @throws IllegalStateException when the table is already frozen
     */
    public LazyStateImpl(@NotNull View owner, @NotNull TokenTable table,
                         @NotNull Function<ViewContext, T> computation) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.computation = Objects.requireNonNull(computation, "computation");
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
    @SuppressWarnings("unchecked")
    public @Nullable T get(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        StateStore store = storeFor(context);
        Object raw = store.get(id);
        if (raw == null) {
            T computed = computation.apply(context);
            raw = computed == null ? NULL_VALUE : computed;
            store.set(id, raw);
        }
        return raw == NULL_VALUE ? null : (T) raw;
    }

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
}
```

**internal/state/InitialStateImpl.java** (complete replacement)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Mutable state token bound from {@code ViewArguments} at open: the open phase validates
 * the argument type and writes the value straight into the session's {@link StateStore}.
 * An absent key reads as {@code null} until set.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class InitialStateImpl<T> implements MutableState<T> {

    private final View owner;
    private final String key;
    private final Class<T> type;
    private final int id;

    /**
     * Creates and registers the token.
     *
     * @param owner the view declaring the token
     * @param table the owner's token table
     * @param key   the {@code ViewArguments} key bound at open
     * @param type  the expected argument type, validated at the open site
     * @throws IllegalStateException when the table is already frozen
     */
    public InitialStateImpl(@NotNull View owner, @NotNull TokenTable table,
                            @NotNull String key, @NotNull Class<T> type) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
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

    /**
     * Returns the {@code ViewArguments} key this token binds at open.
     *
     * @return the argument key
     */
    public @NotNull String key() {
        return key;
    }

    /**
     * Returns the expected argument type, validated at the open site.
     *
     * @return the value type
     */
    public @NotNull Class<T> type() {
        return type;
    }

    @Override
    public @Nullable T get(@NotNull ViewContext context) {
        Objects.requireNonNull(context, "context");
        StateStore store = storeFor(context);
        return type.cast(store.get(id));
    }

    @Override
    public void set(@NotNull ViewContext context, @Nullable T value) {
        Objects.requireNonNull(context, "context");
        StateStore store = storeFor(context);
        assertMainThread();
        store.set(id, value);
        store.markDirty(id);
    }

    @Override
    public void update(@NotNull ViewContext context, @NotNull UnaryOperator<T> fn) {
        Objects.requireNonNull(fn, "fn");
        set(context, fn.apply(get(context)));
    }

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

    private static void assertMainThread() {
        // the server null-check keeps pure unit tests (no Bukkit) working on any thread
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("MutableState.set/update must run on the main thread");
        }
    }
}
```

**internal/state/SharedStateImpl.java** (complete replacement)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Shared state token: one atomic value per view singleton, readable and writable from any
 * thread. Every successful write invokes the flush hook so open sessions of the owning
 * view repaint their watchers.
 *
 * @param <T> the value type
 */
@ApiStatus.Internal
public final class SharedStateImpl<T> implements SharedState<T> {

    private final View owner;
    private final AtomicReference<T> value;
    private final int id;

    // wired by ViewEngine at registration (plan task 16) to flush every open session of
    // the owning view; null until the engine wires it
    @Nullable Runnable flushHook;

    /**
     * Creates and registers the token.
     *
     * @param owner        the view declaring the token
     * @param table        the owner's token table
     * @param initialValue the initial shared value, possibly {@code null}
     * @throws IllegalStateException when the table is already frozen
     */
    public SharedStateImpl(@NotNull View owner, @NotNull TokenTable table, @Nullable T initialValue) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.value = new AtomicReference<>(initialValue);
        this.id = Objects.requireNonNull(table, "table").register(this);
    }

    /**
     * Returns the table-assigned token id.
     *
     * @return the token id
     */
    public int id() {
        return id;
    }

    @Override
    public @Nullable T get() {
        return value.get();
    }

    @Override
    public void set(@Nullable T newValue) {
        value.set(newValue);
        runFlushHook();
    }

    @Override
    public void update(@NotNull UnaryOperator<T> fn) {
        Objects.requireNonNull(fn, "fn");
        T current;
        T next;
        do {
            current = value.get();
            next = fn.apply(current);
        } while (!value.compareAndSet(current, next));
        runFlushHook();
    }

    private void runFlushHook() {
        Runnable hook = flushHook;
        if (hook != null) {
            hook.run();
        }
    }
}
```

- [ ] **Step 9: Run test to verify it passes**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=StateImplTest,StateStoreTest,ViewTest"
Expected: PASS
- [ ] **Step 10: Commit**
```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/StateImplTest.java
git commit -m "feat(inventory-api): implement state tokens with stale-context and main-thread guards"
```

- [ ] **Step 11: Write the failing test for the IdentifiableToken seam**

Add to `StateImplTest.java`:

```java
    @Test
    void allTokenImpls_implementIdentifiableToken_withTableAssignedId() {
        View view = new TokenSeamView();
        java.util.List<tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken> tokens =
                view.tokenTable().tokens();

        for (int i = 0; i < tokens.size(); i++) {
            assertTrue(tokens.get(i) instanceof IdentifiableToken,
                    tokens.get(i).getClass().getSimpleName() + " must implement IdentifiableToken");
            assertEquals(i, ((IdentifiableToken) tokens.get(i)).tokenId());
        }
    }

    @Test
    void sharedState_flushHookSetter_isInvokedOnSet() {
        TokenSeamView view = new TokenSeamView();
        java.util.concurrent.atomic.AtomicInteger flushes = new java.util.concurrent.atomic.AtomicInteger();

        ((SharedStateImpl<String>) view.shared).flushHook(flushes::incrementAndGet);
        view.shared.set("value");

        assertEquals(1, flushes.get());
    }

    /** declares one token of each kind so the seam test covers all four impls. */
    private static final class TokenSeamView extends View {
        final MutableState<Integer> mutable = mutableState(0);
        final State<String> lazy = lazyState(ctx -> "x");
        final MutableState<String> initial = initialState("k", String.class);
        final SharedState<String> shared = sharedState(null);
    }
```

- [ ] **Step 12: Run test to verify it fails**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=StateImplTest"
Expected: FAIL (compilation error: class IdentifiableToken does not exist / flushHook(Runnable) not found)

- [ ] **Step 13: Implement the seam**

Create `internal/state/IdentifiableToken.java`:

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.state;

import org.jetbrains.annotations.ApiStatus;

/**
 * Internal seam exposing the table-assigned int id of a state token. Implemented by every
 * token implementation so the component builder can resolve watch ids without casting to
 * concrete classes.
 */
@ApiStatus.Internal
public interface IdentifiableToken {

    /**
     * @return the token id assigned by the owning view's token table
     */
    int tokenId();
}
```

Modify the four impl classes from Steps 7–8 (final versions):

1. `MutableStateImpl`: declaration becomes
   `public final class MutableStateImpl<T> implements MutableState<T>, IdentifiableToken {`
   and add directly below the existing `id()` method:
```java
    @Override
    public int tokenId() {
        return id;
    }
```
2. `LazyStateImpl`: declaration becomes
   `public final class LazyStateImpl<T> implements State<T>, IdentifiableToken {`
   plus the same `tokenId()` override.
3. `InitialStateImpl`: declaration becomes
   `public final class InitialStateImpl<T> implements MutableState<T>, IdentifiableToken {`
   plus the same `tokenId()` override.
4. `SharedStateImpl`: declaration becomes
   `public final class SharedStateImpl<T> implements SharedState<T>, IdentifiableToken {`
   plus the same `tokenId()` override, and add the public setter (the package-private field
   stays for same-package test convenience):
```java
    /**
     * Wires the engine flush callback invoked after every {@link #set}; assigned once by the
     * engine on the view's first open.
     *
     * @param hook the flush callback, or null to clear
     */
    @ApiStatus.Internal
    public void flushHook(@Nullable Runnable hook) {
        this.flushHook = hook;
    }
```

- [ ] **Step 14: Run test to verify it passes**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=StateImplTest"
Expected: PASS

- [ ] **Step 15: Commit**
```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/StateImplTest.java
git commit -m "feat(inventory-api): add IdentifiableToken seam and SharedState flush hook setter"
```

---

### Task 7: ResolvedLayout

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/layout/ResolvedLayout.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/layout/ResolvedLayoutTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.layout;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedLayoutTest {

    private static ViewConfig configWithLayout(String... rows) {
        return new ViewConfigBuilder()
                .title("test")
                .layout(rows)
                .build();
    }

    @Test
    void resolve_multiCharLayout_mapsCharsToRowMajorSlots() {
        ViewConfig config = configWithLayout(
                "A A      ",
                " BBB     ",
                "A       B");

        ResolvedLayout resolved = ResolvedLayout.resolve(config);

        assertTrue(resolved.hasChar('A'));
        assertTrue(resolved.hasChar('B'));
        assertArrayEquals(new int[]{0, 2, 18}, resolved.slotsOf('A'));
        assertArrayEquals(new int[]{10, 11, 12, 26}, resolved.slotsOf('B'));
    }

    @Test
    void resolve_layoutPresent_rowsEqualsLayoutRowCount() {
        ViewConfig config = configWithLayout(
                "A A      ",
                " BBB     ",
                "A       B");

        ResolvedLayout resolved = ResolvedLayout.resolve(config);

        assertEquals(3, resolved.rows());
    }

    @Test
    void resolve_spacesAreSkipped() {
        ViewConfig config = configWithLayout(
                "A A      ",
                " BBB     ",
                "A       B");

        ResolvedLayout resolved = ResolvedLayout.resolve(config);

        assertFalse(resolved.hasChar(' '));
        assertArrayEquals(new int[0], resolved.slotsOf(' '));
    }

    @Test
    void slotsOf_absentChar_returnsEmptyArray() {
        ViewConfig config = configWithLayout(
                "A A      ",
                " BBB     ",
                "A       B");

        ResolvedLayout resolved = ResolvedLayout.resolve(config);

        assertFalse(resolved.hasChar('X'));
        assertArrayEquals(new int[0], resolved.slotsOf('X'));
    }

    @Test
    void resolve_emptyLayout_usesConfigRowsAndHasNoChars() {
        ViewConfig config = new ViewConfigBuilder()
                .title("test")
                .rows(4)
                .build();

        ResolvedLayout resolved = ResolvedLayout.resolve(config);

        assertEquals(4, resolved.rows());
        assertFalse(resolved.hasChar('A'));
        assertArrayEquals(new int[0], resolved.slotsOf('A'));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ResolvedLayoutTest"
Expected: FAIL (compilation error: class ResolvedLayout does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.layout;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed form of {@link ViewConfig#layout()}: maps each named layout character to its
 * container slots in row-major order; {@code ' '} marks an unnamed position and is skipped.
 */
@ApiStatus.Internal
public final class ResolvedLayout {

    private static final int[] NO_SLOTS = new int[0];

    private final int rows;
    private final Map<Character, int[]> slotsByChar;

    private ResolvedLayout(int rows, @NotNull Map<Character, int[]> slotsByChar) {
        this.rows = rows;
        this.slotsByChar = slotsByChar;
    }

    /**
     * Parses the layout rows of the given config; layout shape was already validated at build time.
     *
     * @param config the effective view config
     * @return the resolved layout; an empty mapping when {@code config.layout()} is empty
     */
    public static @NotNull ResolvedLayout resolve(@NotNull ViewConfig config) {
        List<String> layout = config.layout();
        if (layout.isEmpty()) {
            return new ResolvedLayout(config.rows(), Collections.<Character, int[]>emptyMap());
        }

        Map<Character, List<Integer>> collected = new LinkedHashMap<>();
        for (int row = 0; row < layout.size(); row++) {
            String line = layout.get(row);
            for (int column = 0; column < line.length(); column++) {
                char character = line.charAt(column);
                if (character == ' ') {
                    continue;
                }

                List<Integer> positions = collected.get(character);
                if (positions == null) {
                    positions = new ArrayList<>();
                    collected.put(character, positions);
                }
                positions.add(row * Layout.ROW_WIDTH + column);
            }
        }

        Map<Character, int[]> slotsByChar = new HashMap<>();
        for (Map.Entry<Character, List<Integer>> entry : collected.entrySet()) {
            List<Integer> positions = entry.getValue();
            int[] slots = new int[positions.size()];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = positions.get(i);
            }
            slotsByChar.put(entry.getKey(), slots);
        }
        return new ResolvedLayout(layout.size(), slotsByChar);
    }

    /**
     * @return the layout row count, or the config's resolved rows when no layout was declared
     */
    public int rows() {
        return rows;
    }

    /**
     * @param c the layout character to test
     * @return true when the character names at least one slot
     */
    public boolean hasChar(char c) {
        return slotsByChar.containsKey(c);
    }

    /**
     * @param c the layout character to look up
     * @return the character's slots in row-major order; an empty array when absent
     */
    public @NotNull int[] slotsOf(char c) {
        int[] slots = slotsByChar.get(c);
        return slots != null ? slots.clone() : NO_SLOTS;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ResolvedLayoutTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/layout/ResolvedLayout.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/layout/ResolvedLayoutTest.java
git commit -m "feat(inventory-api): add ResolvedLayout for layout-char slot resolution"
```

### Task 8: Component machinery

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ItemComponentBuilderImpl.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentInstance.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentTable.java
- Depends on: `internal/state/IdentifiableToken.java` (created in Task 6 Step 13 — do NOT recreate)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentInstanceTest.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentTableTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.component;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ComponentInstanceTest {

    private static final Logger COMPONENT_LOGGER = Logger.getLogger(ComponentInstance.class.getName());

    private final ViewContext context = mock(ViewContext.class);
    private CapturingHandler logHandler;

    @BeforeEach
    void setUp() {
        logHandler = new CapturingHandler();
        COMPONENT_LOGGER.addHandler(logHandler);
    }

    @AfterEach
    void tearDown() {
        COMPONENT_LOGGER.removeHandler(logHandler);
    }

    @Test
    void slots_returnsMaterializedSlots() {
        ComponentInstance component = staticItemBuilder().materialize(new int[]{1, 4, 7});

        assertArrayEquals(new int[]{1, 4, 7}, component.slots());
    }

    @Test
    void isVisible_withoutDisplayIf_returnsTrue() {
        ComponentInstance component = staticItemBuilder().materialize(new int[]{0});

        assertTrue(component.isVisible(context));
    }

    @Test
    void isVisible_predicateReceivesContextAndDecides() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        List<ViewContext> seen = new ArrayList<>();
        builder.displayIf(ctx -> {
            seen.add(ctx);
            return false;
        });
        ComponentInstance component = builder.materialize(new int[]{0});

        assertFalse(component.isVisible(context));
        assertEquals(Collections.singletonList(context), seen);
    }

    @Test
    void isVisible_predicateThrows_returnsFalseAndLogsAtLeastOnce() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        builder.displayIf(ctx -> {
            throw new IllegalStateException("boom");
        });
        ComponentInstance component = builder.materialize(new int[]{0});

        assertFalse(component.isVisible(context));
        assertFalse(component.isVisible(context));
        assertTrue(logHandler.records.size() >= 1, "expected at least one rate-limited log");
    }

    @Test
    void renderItem_staticItem_returnsConfiguredItem() {
        ItemStack item = new ItemStack(Material.STONE);
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(item);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertSame(item, component.renderItem(context));
    }

    @Test
    void renderItem_renderer_appliesWithContext() {
        ItemStack item = new ItemStack(Material.STONE);
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(ctx -> ctx == context ? item : null);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertSame(item, component.renderItem(context));
    }

    @Test
    void renderItem_rendererThrows_returnsNullAndLogsAtLeastOnce() {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(ctx -> {
            throw new IllegalStateException("boom");
        });
        ComponentInstance component = builder.materialize(new int[]{0});

        assertNull(component.renderItem(context));
        assertTrue(logHandler.records.size() >= 1, "expected at least one rate-limited log");
    }

    @Test
    void handlerFor_typedMatch_returnsTypedHandler() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        Consumer<SlotClickContext> typed = click -> {
        };
        Consumer<SlotClickContext> untyped = click -> {
        };
        builder.onClick(ClickType.LEFT, typed);
        builder.onClick(untyped);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertSame(typed, component.handlerFor(ClickType.LEFT));
    }

    @Test
    void handlerFor_typedMiss_fallsBackToUntypedHandler() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        Consumer<SlotClickContext> typed = click -> {
        };
        Consumer<SlotClickContext> untyped = click -> {
        };
        builder.onClick(ClickType.LEFT, typed);
        builder.onClick(untyped);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertSame(untyped, component.handlerFor(ClickType.RIGHT));
    }

    @Test
    void handlerFor_noHandlers_returnsNull() {
        ComponentInstance component = staticItemBuilder().materialize(new int[]{0});

        assertNull(component.handlerFor(ClickType.LEFT));
    }

    @Test
    void defaults_inheritCancelAndDeclareNoPostActions() {
        ComponentInstance component = staticItemBuilder().materialize(new int[]{0});

        assertNull(component.cancelOnClick());
        assertFalse(component.closeOnClick());
        assertNull(component.openOnClickTarget());
        assertFalse(component.openOnClickArguments().has("anything"));
        assertArrayEquals(new int[0], component.watchedTokenIds());
    }

    @Test
    void builderOptions_carryIntoInstance() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        ViewArguments arguments = ViewArguments.of("key", "value");
        builder.cancelOnClick(false);
        builder.closeOnClick();
        builder.openOnClick(TargetView.class, arguments);
        ComponentInstance component = builder.materialize(new int[]{0});

        assertEquals(Boolean.FALSE, component.cancelOnClick());
        assertTrue(component.closeOnClick());
        assertEquals(TargetView.class, component.openOnClickTarget());
        assertSame(arguments, component.openOnClickArguments());
    }

    @Test
    void updateOnStateChange_identifiableTokens_resolveIdsInOrder() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        builder.updateOnStateChange(new StubToken(3), new StubToken(7));
        ComponentInstance component = builder.materialize(new int[]{0});

        assertArrayEquals(new int[]{3, 7}, component.watchedTokenIds());
    }

    @Test
    void updateOnStateChange_foreignToken_throwsIllegalArgumentException() {
        ItemComponentBuilderImpl builder = staticItemBuilder();
        StateToken foreign = new StateToken() {
        };

        assertThrows(IllegalArgumentException.class, () -> builder.updateOnStateChange(foreign));
    }

    private static ItemComponentBuilderImpl staticItemBuilder() {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(new ItemStack(Material.STONE));
        return builder;
    }

    private static final class TargetView extends View {
    }

    private static final class StubToken implements StateToken, IdentifiableToken {
        private final int id;

        private StubToken(int id) {
            this.id = id;
        }

        @Override
        public int tokenId() {
            return id;
        }
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

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ComponentInstanceTest"
Expected: FAIL (compilation error: classes ItemComponentBuilderImpl and ComponentInstance do not exist)

- [ ] **Step 3: Write minimal implementation**

`IdentifiableToken` already exists (Task 6 Step 13) — do not recreate it.

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.component;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Mutable collector behind {@link ItemComponentBuilder}; gathers a component declaration
 * and materializes it into an immutable {@link ComponentInstance} bound to fixed slots.
 */
@ApiStatus.Internal
public final class ItemComponentBuilderImpl implements ItemComponentBuilder {

    private ItemStack staticItem;
    private Function<ViewContext, ItemStack> renderer;
    private Predicate<ViewContext> displayIf;
    private final List<Integer> watchedTokenIds = new ArrayList<>();
    private final Map<ClickType, Consumer<SlotClickContext>> typedHandlers = new EnumMap<>(ClickType.class);
    private Consumer<SlotClickContext> defaultHandler;
    private Boolean cancelOnClick;
    private boolean closeOnClick;
    private Class<? extends View> openOnClickTarget;
    private ViewArguments openOnClickArguments = ViewArguments.empty();

    @Override
    public @NotNull ItemComponentBuilder item(@NotNull ItemStack item) {
        this.staticItem = Objects.requireNonNull(item, "item");
        this.renderer = null;
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder item(@NotNull Function<ViewContext, ItemStack> renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.staticItem = null;
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder displayIf(@NotNull Predicate<ViewContext> condition) {
        this.displayIf = Objects.requireNonNull(condition, "condition");
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder updateOnStateChange(@NotNull StateToken... tokens) {
        for (StateToken token : tokens) {
            Objects.requireNonNull(token, "token");
            if (!(token instanceof IdentifiableToken)) {
                throw new IllegalArgumentException("unsupported StateToken implementation: "
                        + token.getClass().getName()
                        + "; only tokens created by View state factories are watchable");
            }
            watchedTokenIds.add(((IdentifiableToken) token).tokenId());
        }
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder onClick(@NotNull Consumer<SlotClickContext> handler) {
        this.defaultHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder onClick(@NotNull ClickType type, @NotNull Consumer<SlotClickContext> handler) {
        Objects.requireNonNull(type, "type");
        typedHandlers.put(type, Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder cancelOnClick(boolean cancel) {
        this.cancelOnClick = cancel;
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder closeOnClick() {
        this.closeOnClick = true;
        return this;
    }

    @Override
    public @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target) {
        return openOnClick(target, ViewArguments.empty());
    }

    @Override
    public @NotNull ItemComponentBuilder openOnClick(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments) {
        this.openOnClickTarget = Objects.requireNonNull(target, "target");
        this.openOnClickArguments = Objects.requireNonNull(arguments, "arguments");
        return this;
    }

    /**
     * Freezes the collected declaration into a component bound to the given slots.
     *
     * @param slots the container slots this component paints, in paint order
     * @return the immutable materialized component
     */
    public @NotNull ComponentInstance materialize(@NotNull int[] slots) {
        return new ComponentInstance(slots, staticItem, renderer, displayIf, typedHandlers,
                defaultHandler, cancelOnClick, closeOnClick, openOnClickTarget,
                openOnClickArguments, watchedTokenIds);
    }
}
```

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.component;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Immutable materialized item component: fixed slots, item source, visibility predicate,
 * click handlers, post-actions and watched token ids. Renderer and predicate failures are
 * swallowed and logged at most once per minute per instance (§9 error table).
 */
@ApiStatus.Internal
public final class ComponentInstance {

    private static final Logger LOGGER = Logger.getLogger(ComponentInstance.class.getName());
    private static final long LOG_INTERVAL_MILLIS = 60_000L;

    private final int[] slots;
    private final ItemStack staticItem;
    private final Function<ViewContext, ItemStack> renderer;
    private final Predicate<ViewContext> displayIf;
    private final Map<ClickType, Consumer<SlotClickContext>> typedHandlers;
    private final Consumer<SlotClickContext> defaultHandler;
    private final Boolean cancelOnClick;
    private final boolean closeOnClick;
    private final Class<? extends View> openOnClickTarget;
    private final ViewArguments openOnClickArguments;
    private final int[] watchedTokenIds;

    // rate limit for renderer/displayIf failure logs; a racy double log is acceptable
    private volatile long lastLogMillis;

    ComponentInstance(@NotNull int[] slots,
                      @Nullable ItemStack staticItem,
                      @Nullable Function<ViewContext, ItemStack> renderer,
                      @Nullable Predicate<ViewContext> displayIf,
                      @NotNull Map<ClickType, Consumer<SlotClickContext>> typedHandlers,
                      @Nullable Consumer<SlotClickContext> defaultHandler,
                      @Nullable Boolean cancelOnClick,
                      boolean closeOnClick,
                      @Nullable Class<? extends View> openOnClickTarget,
                      @NotNull ViewArguments openOnClickArguments,
                      @NotNull List<Integer> watchedTokenIds) {
        this.slots = slots.clone();
        this.staticItem = staticItem;
        this.renderer = renderer;
        this.displayIf = displayIf;
        EnumMap<ClickType, Consumer<SlotClickContext>> handlers = new EnumMap<>(ClickType.class);
        handlers.putAll(typedHandlers);
        this.typedHandlers = handlers;
        this.defaultHandler = defaultHandler;
        this.cancelOnClick = cancelOnClick;
        this.closeOnClick = closeOnClick;
        this.openOnClickTarget = openOnClickTarget;
        this.openOnClickArguments = Objects.requireNonNull(openOnClickArguments, "openOnClickArguments");
        this.watchedTokenIds = toIntArray(watchedTokenIds);
    }

    /**
     * @return the container slots this component paints, in paint order
     */
    public @NotNull int[] slots() {
        return slots.clone();
    }

    /**
     * Evaluates the {@code displayIf} predicate; visible by default.
     *
     * @return false when the predicate returns false or throws (throw is rate-limit logged)
     */
    public boolean isVisible(@NotNull ViewContext ctx) {
        if (displayIf == null) {
            return true;
        }
        try {
            return displayIf.test(ctx);
        } catch (RuntimeException error) {
            logFailure("displayIf predicate", error);
            return false;
        }
    }

    /**
     * Produces this component's item: the static item, or the renderer applied to the context.
     *
     * @return the item, or null when the renderer throws (throw is rate-limit logged)
     */
    public @Nullable ItemStack renderItem(@NotNull ViewContext ctx) {
        if (staticItem != null) {
            return staticItem;
        }
        if (renderer == null) {
            return null;
        }
        try {
            return renderer.apply(ctx);
        } catch (RuntimeException error) {
            logFailure("item renderer", error);
            return null;
        }
    }

    /**
     * Resolves the handler for a click: the matching per-type handler, else the untyped
     * default handler, else null.
     */
    public @Nullable Consumer<SlotClickContext> handlerFor(@NotNull ClickType type) {
        Consumer<SlotClickContext> typed = typedHandlers.get(type);
        return typed != null ? typed : defaultHandler;
    }

    /**
     * @return the per-component cancellation override, or null to inherit the config default
     */
    public @Nullable Boolean cancelOnClick() {
        return cancelOnClick;
    }

    /**
     * @return true when a click on this component closes the view at end of tick
     */
    public boolean closeOnClick() {
        return closeOnClick;
    }

    /**
     * @return the view opened on click, or null when no navigation was declared
     */
    public @Nullable Class<? extends View> openOnClickTarget() {
        return openOnClickTarget;
    }

    /**
     * @return the arguments passed to the open-on-click target; empty when none were given
     */
    public @NotNull ViewArguments openOnClickArguments() {
        return openOnClickArguments;
    }

    /**
     * @return the watched token ids in declaration order
     */
    public @NotNull int[] watchedTokenIds() {
        return watchedTokenIds.clone();
    }

    /**
     * @return true when a static item or a renderer was declared; checked by {@link ComponentTable#add}
     */
    boolean hasItemSource() {
        return staticItem != null || renderer != null;
    }

    private void logFailure(@NotNull String stage, @NotNull RuntimeException error) {
        long now = System.currentTimeMillis();
        if (now - lastLogMillis < LOG_INTERVAL_MILLIS) {
            return;
        }
        lastLogMillis = now;
        LOGGER.log(Level.SEVERE,
                "component " + stage + " failed for slots " + Arrays.toString(slots), error);
    }

    private static int[] toIntArray(@NotNull List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ComponentInstanceTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/state/IdentifiableToken.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ItemComponentBuilderImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentInstance.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentInstanceTest.java
git commit -m "feat(inventory-api): add component builder and materialized instance"
```

- [ ] **Step 6: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.component;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.IdentifiableToken;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentTableTest {

    private ComponentTable table;

    @BeforeEach
    void setUp() {
        table = new ComponentTable();
    }

    @Test
    void add_thenComponentAt_returnsComponentForEverySlot() {
        ComponentInstance component = component(0, 5, 8);

        table.add(component);

        assertSame(component, table.componentAt(0));
        assertSame(component, table.componentAt(5));
        assertSame(component, table.componentAt(8));
        assertEquals(Collections.singletonList(component), table.all());
    }

    @Test
    void componentAt_unboundSlot_returnsNull() {
        table.add(component(0));

        assertNull(table.componentAt(1));
    }

    @Test
    void add_overlappingSlot_throwsAndLeavesTableUnchanged() {
        ComponentInstance first = component(0, 1);
        table.add(first);

        ViewConfigurationException exception = assertThrows(ViewConfigurationException.class,
                () -> table.add(component(1, 2)));

        assertTrue(exception.getMessage().contains("slot 1"));
        assertSame(first, table.componentAt(1));
        assertNull(table.componentAt(2));
        assertEquals(1, table.all().size());
    }

    @Test
    void add_missingItemSource_throwsViewConfigurationException() {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.onClick(click -> {
        });
        ComponentInstance component = builder.materialize(new int[]{3});

        assertThrows(ViewConfigurationException.class, () -> table.add(component));
    }

    @Test
    void watchersOf_intersectingDirtyIds_returnsOnlyWatchers() {
        ComponentInstance watcherOfThree = watching(3, 0);
        ComponentInstance watcherOfNine = watching(9, 1);
        ComponentInstance unwatched = component(2);
        table.add(watcherOfThree);
        table.add(watcherOfNine);
        table.add(unwatched);

        List<ComponentInstance> watchers = table.watchersOf(new HashSet<>(Arrays.asList(3, 99)));

        assertEquals(Collections.singletonList(watcherOfThree), watchers);
    }

    @Test
    void watchersOf_emptyDirtySet_returnsEmpty() {
        table.add(watching(3, 0));

        assertTrue(table.watchersOf(Collections.<Integer>emptySet()).isEmpty());
    }

    @Test
    void all_isUnmodifiable() {
        table.add(component(0));
        List<ComponentInstance> all = table.all();

        assertThrows(UnsupportedOperationException.class, all::clear);
    }

    private static ComponentInstance component(int... slots) {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(new ItemStack(Material.STONE));
        return builder.materialize(slots);
    }

    private static ComponentInstance watching(int tokenId, int... slots) {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        builder.item(new ItemStack(Material.STONE));
        builder.updateOnStateChange(new StubToken(tokenId));
        return builder.materialize(slots);
    }

    private static final class StubToken implements StateToken, IdentifiableToken {
        private final int id;

        private StubToken(int id) {
            this.id = id;
        }

        @Override
        public int tokenId() {
            return id;
        }
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ComponentTableTest"
Expected: FAIL (compilation error: class ComponentTable does not exist)

- [ ] **Step 8: Write minimal implementation**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.component;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-session component registry: maps slots to their owning component and resolves the
 * components watching a set of dirty token ids. Rejects slot overlaps and components
 * without an item source at registration time.
 */
@ApiStatus.Internal
public final class ComponentTable {

    private final Map<Integer, ComponentInstance> bySlot = new HashMap<>();
    private final List<ComponentInstance> components = new ArrayList<>();

    /**
     * Registers a component; validated before any slot is bound so a failed add leaves
     * the table unchanged.
     *
     * @throws ViewConfigurationException on slot overlap or when the component declares no item source
     */
    public void add(@NotNull ComponentInstance component) {
        if (!component.hasItemSource()) {
            throw new ViewConfigurationException("component for slots "
                    + Arrays.toString(component.slots())
                    + " declares no item source; call item(ItemStack) or item(Function)");
        }

        int[] slots = component.slots();
        for (int slot : slots) {
            if (bySlot.containsKey(slot)) {
                throw new ViewConfigurationException(
                        "slot " + slot + " is already bound to another component");
            }
        }
        for (int slot : slots) {
            bySlot.put(slot, component);
        }
        components.add(component);
    }

    /**
     * @return the component bound to the slot, or null when the slot is component-less
     */
    public @Nullable ComponentInstance componentAt(int slot) {
        return bySlot.get(slot);
    }

    /**
     * @return all registered components in declaration order; unmodifiable
     */
    public @NotNull List<ComponentInstance> all() {
        return Collections.unmodifiableList(components);
    }

    /**
     * Resolves the components whose watched token ids intersect the dirty set,
     * in declaration order.
     */
    public @NotNull List<ComponentInstance> watchersOf(@NotNull Set<Integer> dirtyTokenIds) {
        List<ComponentInstance> watchers = new ArrayList<>();
        for (ComponentInstance component : components) {
            for (int tokenId : component.watchedTokenIds()) {
                if (dirtyTokenIds.contains(tokenId)) {
                    watchers.add(component);
                    break;
                }
            }
        }
        return watchers;
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ComponentTableTest"
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentTable.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentTableTest.java
git commit -m "feat(inventory-api): add ComponentTable slot registry and watcher lookup"
```

- [ ] **Step 11: Write the failing test for renderForPaint (keep-previous-content, spec §9)**

Add to `ComponentInstanceTest.java`:

```java
    @Test
    void renderForPaint_visibleComponent_returnsItem() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ComponentInstance component = builderWith(b -> b.item(item)).materialize(new int[]{0});

        assertSame(item, component.renderForPaint(context));
    }

    @Test
    void renderForPaint_hiddenComponent_returnsNullToClear() {
        ComponentInstance component = builderWith(b -> b
                .item(new ItemStack(Material.DIAMOND))
                .displayIf(ctx -> false)).materialize(new int[]{0});

        assertNull(component.renderForPaint(context));
    }

    @Test
    void renderForPaint_displayIfThrows_returnsFailureSentinel() {
        ComponentInstance component = builderWith(b -> b
                .item(new ItemStack(Material.DIAMOND))
                .displayIf(ctx -> { throw new IllegalStateException("boom"); })).materialize(new int[]{0});

        assertSame(ComponentInstance.RENDER_FAILURE, component.renderForPaint(context));
    }

    @Test
    void renderForPaint_rendererThrows_returnsFailureSentinel() {
        ComponentInstance component = builderWith(b -> b
                .item(ctx -> { throw new IllegalStateException("boom"); })).materialize(new int[]{0});

        assertSame(ComponentInstance.RENDER_FAILURE, component.renderForPaint(context));
    }
```

(`builderWith` is the existing test helper from Step 1 that configures a fresh
`ItemComponentBuilderImpl`; if Step 1 named it differently, reuse that helper.)

- [ ] **Step 12: Run test to verify it fails**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ComponentInstanceTest"
Expected: FAIL (compilation error: renderForPaint / RENDER_FAILURE do not exist)

- [ ] **Step 13: Implement renderForPaint**

Add to `ComponentInstance.java`:

```java
    /**
     * Identity sentinel returned by {@link #renderForPaint} when evaluation failed: the caller
     * must skip painting so the slot keeps its previous content.
     */
    public static final ItemStack RENDER_FAILURE = new ItemStack(Material.BARRIER);

    /**
     * Single paint entry point: returns null to clear the slot (hidden component), the
     * {@link #RENDER_FAILURE} sentinel (compare by identity) when displayIf or the renderer
     * threw, or the item to paint.
     *
     * @param ctx the rendering context
     * @return the item, null, or the failure sentinel
     */
    public @Nullable ItemStack renderForPaint(@NotNull ViewContext ctx) {
        boolean visible;
        try {
            visible = displayIf == null || displayIf.test(ctx);
        } catch (RuntimeException e) {
            logFailure("displayIf", e);
            return RENDER_FAILURE;
        }
        if (!visible) {
            return null;
        }
        if (renderer == null) {
            return staticItem;
        }
        try {
            return renderer.apply(ctx);
        } catch (RuntimeException e) {
            logFailure("item renderer", e);
            return RENDER_FAILURE;
        }
    }
```

(`displayIf`, `renderer`, `staticItem`, and `logFailure` are the existing private members
from Step 3; add `import org.bukkit.Material;` if absent. Every painting caller — Tasks 12,
14, 16 — must check `item == ComponentInstance.RENDER_FAILURE` and skip the
`SlotPainter.paint` call for that slot.)

- [ ] **Step 14: Run test to verify it passes**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ComponentInstanceTest"
Expected: PASS

- [ ] **Step 15: Commit**
```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentInstance.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/component/ComponentInstanceTest.java
git commit -m "feat(inventory-api): add renderForPaint with keep-previous-content failure sentinel"
```

### Task 9: SlotPainter

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/render/SlotPainter.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/render/SlotPainterTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.render;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SlotPainterTest {

    private ServerMock server;
    private PlayerMock player;
    private Inventory inventory;
    private PlaceholderApplier placeholderApplier;
    private SlotPainter painter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("tester");
        inventory = Bukkit.createInventory(null, 27);
        placeholderApplier = mock(PlaceholderApplier.class);
        painter = new SlotPainter(placeholderApplier);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void paint_nullItem_clearsSlot() {
        inventory.setItem(4, new ItemStack(Material.STONE));

        painter.paint(player, inventory, 4, null, true);

        assertNull(inventory.getItem(4));
        verifyNoInteractions(placeholderApplier);
    }

    @Test
    void paint_applyPlaceholders_appliesNameAndLoreToCloneOnly() {
        ItemStack original = new ItemStack(Material.DIAMOND);
        ItemMeta meta = original.getItemMeta();
        meta.setDisplayName("name %placeholder%");
        meta.setLore(Collections.singletonList("lore %placeholder%"));
        original.setItemMeta(meta);

        when(placeholderApplier.apply(player, "name %placeholder%")).thenReturn("applied name");
        when(placeholderApplier.applyAll(player, Collections.singletonList("lore %placeholder%")))
                .thenReturn(Collections.singletonList("applied lore"));

        painter.paint(player, inventory, 0, original, true);

        ItemStack painted = inventory.getItem(0);
        assertNotNull(painted);
        assertEquals(Material.DIAMOND, painted.getType());
        assertEquals("applied name", painted.getItemMeta().getDisplayName());
        assertEquals(Collections.singletonList("applied lore"), painted.getItemMeta().getLore());

        // the caller's item must stay untouched
        assertEquals("name %placeholder%", original.getItemMeta().getDisplayName());
        assertEquals(Collections.singletonList("lore %placeholder%"), original.getItemMeta().getLore());
    }

    @Test
    void paint_applyPlaceholders_itemWithoutMeta_paintsWithoutApplier() {
        ItemStack original = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(original.clone()).thenReturn(clone);
        when(clone.clone()).thenReturn(clone);
        when(clone.getItemMeta()).thenReturn(null);

        painter.paint(player, inventory, 3, original, true);

        verify(original).clone();
        verify(clone, never()).setItemMeta(any());
        verifyNoInteractions(placeholderApplier);
    }

    @Test
    void paint_withoutPlaceholders_setsValueEqualItemAndSkipsApplier() {
        ItemStack original = new ItemStack(Material.GOLD_INGOT, 5);

        painter.paint(player, inventory, 2, original, false);

        ItemStack painted = inventory.getItem(2);
        assertNotNull(painted);
        assertEquals(original, painted);
        assertNotSame(original, painted);
        verifyNoInteractions(placeholderApplier);
    }

    @Test
    void paint_withoutPlaceholders_clonesTheCallerItem() {
        ItemStack original = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(original.clone()).thenReturn(clone);
        when(clone.clone()).thenReturn(clone);

        painter.paint(player, inventory, 5, original, false);

        verify(original).clone();
        verifyNoInteractions(placeholderApplier);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=SlotPainterTest"
Expected: FAIL (compilation error: class SlotPainter does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.render;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;

import java.util.Objects;

/**
 * Writes item stacks into container slots, applying placeholders to display name and lore
 * at paint time on a clone of the given item (mirrors the 2.x {@code InventoryEditorImpl}
 * placeholder behavior); the caller's item is never mutated.
 */
@Component
@ApiStatus.Internal
public final class SlotPainter {

    private final PlaceholderApplier placeholderApplier;

    /**
     * @param placeholderApplier the applier resolving placeholder tokens per player
     */
    public SlotPainter(@NotNull PlaceholderApplier placeholderApplier) {
        this.placeholderApplier = Objects.requireNonNull(placeholderApplier, "placeholderApplier");
    }

    /**
     * Paints the slot: a null item clears it; otherwise a clone of the item is written,
     * with placeholders applied to its display name and lore when requested.
     *
     * @param player            the viewer whose context resolves player-scoped placeholders
     * @param inventory         the container to write into
     * @param slot              the raw slot to paint
     * @param item              the item to paint, or null to clear the slot
     * @param applyPlaceholders whether placeholders should be applied to the item's meta
     */
    public void paint(@NotNull Player player, @NotNull Inventory inventory, int slot,
                      @Nullable ItemStack item, boolean applyPlaceholders) {
        if (item == null) {
            inventory.setItem(slot, null);
            return;
        }

        // clone so placeholder application never mutates the caller's item
        ItemStack copy = item.clone();
        if (applyPlaceholders) {
            ItemMeta meta = copy.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(placeholderApplier.apply(player, meta.getDisplayName()));
                meta.setLore(placeholderApplier.applyAll(player, meta.getLore()));
                copy.setItemMeta(meta);
            }
        }

        inventory.setItem(slot, copy);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=SlotPainterTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/render/SlotPainter.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/render/SlotPainterTest.java
git commit -m "feat(inventory-api): add SlotPainter with placeholder-aware slot painting"
```

---

closed sessions.
     *
     * @param session the session to close
     * @param reason  the close reason
     */
    public void close(@NotNull ViewSession session, @NotNull CloseReason reason) {
        assertMainThread("ViewEngine.close");
        closePhase.close(session, reason);
    }

    /**
     * Runs an update pass on a session.
     *
     * @param session the session to update
     * @param trigger the cause of the update
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger) {
        assertMainThread("ViewEngine.update");
        updatePhase.update(session, trigger, null);
    }

    /**
     * Routes a Bukkit click event into the session per the click policy; clicks on sessions
     * that are not ACTIVE are swallowed (cancelled, not routed).
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void click(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        assertMainThread("ViewEngine.click");
        if (session.status() != ViewSession.Status.ACTIVE) {
            event.setCancelled(true);
            return;
        }
        inClickDispatch = true;
        try {
            clickRoutingPhase.route(session, event);
        } finally {
            inClickDispatch = false;
        }
    }

    /**
     * Applies the drag policy of a session to a Bukkit drag event.
     *
     * @param session the affected session
     * @param event   the Bukkit event
     */
    public void drag(@NotNull ViewSession session, @NotNull InventoryDragEvent event) {
        throw new UnsupportedOperationException("implemented in Task 14");
    }

    /**
     * Handles a Bukkit close event for a session, guarded by container identity.
     *
     * @param session the player's session
     * @param event   the Bukkit event
     */
    public void bukkitClose(@NotNull ViewSession session, @NotNull InventoryCloseEvent event) {
        throw new UnsupportedOperationException("implemented in Task 13");
    }

    /**
     * Flushes dirty state tokens of a session (STATE_CHANGE re-render, cascade cap 8).
     *
     * @param session the session to flush
     */
    public void flushDirty(@NotNull ViewSession session) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }

    /**
     * Flushes shared-state watchers of every open session of a view.
     *
     * @param owner the view singleton whose sessions should flush
     */
    public void flushShared(@NotNull View owner) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }

    /**
     * Defers an operation to the end of the current tick. The session leaves ACTIVE
     * immediately (TRANSITIONING) so further clicks are swallowed until the operation runs;
     * sessions still TRANSITIONING after the drain return to ACTIVE.
     *
     * @param session the session the operation belongs to
     * @param op      the operation to run at end of tick
     */
    public void defer(@NotNull ViewSession session, @NotNull Runnable op) {
        assertMainThread("ViewEngine.defer");
        if (session.status() == ViewSession.Status.ACTIVE) {
            session.status(ViewSession.Status.TRANSITIONING);
        }
        session.deferredOps().add(op);
        Bukkit.getScheduler().runTask(plugin, () -> drainDeferred(session));
    }

    private void drainDeferred(ViewSession session) {
        List<Runnable> ops = session.deferredOps();
        while (!ops.isEmpty()) {
            ops.remove(0).run();
        }
        // deferred ops that neither closed nor replaced the session leave it usable again
        if (session.status() == ViewSession.Status.TRANSITIONING) {
            session.status(ViewSession.Status.ACTIVE);
        }
    }

    /**
     * @return true while a click event is being dispatched (drives operation deferral)
     */
    public boolean isInClickDispatch() {
        return inClickDispatch;
    }

    /**
     * @return the owning plugin
     */
    public @NotNull Plugin plugin() {
        return plugin;
    }

    /**
     * @return the title update strategy
     */
    public @NotNull TitleUpdater titleUpdater() {
        return titleUpdater;
    }

    /**
     * Throws when not on the main server thread.
     *
     * @param operation the operation name used in the error message
     */
    public static void assertMainThread(@NotNull String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(operation + " must be called on the main server thread");
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewEngineOpenCloseTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/schedule/ modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngineOpenCloseTest.java
git commit -m "feat(inventory-api): implement open, first-render, and close lifecycle phases"
```

### Task 10: ViewSession + SessionRegistry

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/RegisteredView.java (contract-pinned prerequisite — `ViewSession`'s constructor requires it and this is the first task that compiles against it; Task 12's `ViewRegistry` consumes this exact class, do NOT recreate it there)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/session/ViewSession.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/session/SessionRegistry.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/session/ViewSessionTest.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/session/SessionRegistryTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.session;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ViewSessionTest {

    private ServerMock server;
    private PlayerMock player;
    private ProbeView view;
    private ViewConfig config;
    private RegisteredView registered;

    static final class ProbeView extends View {
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("tester");
        view = new ProbeView();
        config = new ViewConfigBuilder().title("t").rows(1).build();
        registered = new RegisteredView(ProbeView.class, view, config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession newSession() {
        return new ViewSession(player, registered, ViewArguments.empty(), new StateStore(0));
    }

    @Test
    void registeredView_exposesTypeInstanceAndConfig() {
        assertEquals(ProbeView.class, registered.type());
        assertSame(view, registered.instance());
        assertSame(config, registered.config());
    }

    @Test
    void constructor_exposesCollaborators_andStartsOpening() {
        ViewArguments arguments = ViewArguments.of("key", "value");
        StateStore store = new StateStore(0);

        ViewSession session = new ViewSession(player, registered, arguments, store);

        assertSame(player, session.player());
        assertSame(registered, session.registered());
        assertSame(arguments, session.arguments());
        assertSame(store, session.stateStore());
        assertEquals(ViewSession.Status.OPENING, session.status());
        assertFalse(session.isActive());
    }

    @Test
    void status_followsEngineTransitions() {
        ViewSession session = newSession();

        assertEquals(ViewSession.Status.OPENING, session.status());
        session.status(ViewSession.Status.ACTIVE);
        assertEquals(ViewSession.Status.ACTIVE, session.status());
        session.status(ViewSession.Status.TRANSITIONING);
        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        // a drained deferral that neither closed nor replaced the session reactivates it
        session.status(ViewSession.Status.ACTIVE);
        assertEquals(ViewSession.Status.ACTIVE, session.status());
        session.status(ViewSession.Status.CLOSED);
        assertEquals(ViewSession.Status.CLOSED, session.status());
    }

    @Test
    void isActive_trueOnlyForActiveStatus() {
        ViewSession session = newSession();

        for (ViewSession.Status status : ViewSession.Status.values()) {
            session.status(status);
            assertEquals(status == ViewSession.Status.ACTIVE, session.isActive(),
                    "isActive() for status " + status);
        }
    }

    @Test
    void components_returnsOneStableComponentTable() {
        ViewSession session = newSession();

        assertSame(session.components(), session.components());
        assertTrue(session.components().all().isEmpty());
    }

    @Test
    void deferredOps_isOneStableMutableList() {
        ViewSession session = newSession();
        Runnable op = () -> {
        };

        session.deferredOps().add(op);

        assertSame(session.deferredOps(), session.deferredOps());
        assertEquals(1, session.deferredOps().size());
        assertSame(op, session.deferredOps().remove(0));
        assertTrue(session.deferredOps().isEmpty());
    }

    @Test
    void inventory_nullUntilContainerCreated_thenRoundTrips() {
        ViewSession session = newSession();
        assertNull(session.inventory());

        Inventory inventory = Bukkit.createInventory(null, 9);
        session.inventory(inventory);

        assertSame(inventory, session.inventory());
    }

    @Test
    void effectiveConfig_defaultsToRegisteredConfig_untilOverridden() {
        ViewSession session = newSession();
        assertSame(config, session.effectiveConfig());

        ViewConfig overridden = config.withOverrides("per-open", 2);
        session.effectiveConfig(overridden);

        assertSame(overridden, session.effectiveConfig());
    }

    @Test
    void layout_nullUntilResolved_thenRoundTrips() {
        ViewSession session = newSession();
        assertNull(session.layout());

        ResolvedLayout resolved = ResolvedLayout.resolve(config);
        session.layout(resolved);

        assertSame(resolved, session.layout());
    }

    @Test
    void updateTask_nullByDefault_settableAndClearable() {
        ViewSession session = newSession();
        assertNull(session.updateTask());

        BukkitTask task = mock(BukkitTask.class);
        session.updateTask(task);
        assertSame(task, session.updateTask());

        session.updateTask(null);
        assertNull(session.updateTask());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewSessionTest"
Expected: FAIL (compilation error: classes RegisteredView and ViewSession do not exist)

- [ ] **Step 3: Write minimal implementation**

**internal/registry/RegisteredView.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.registry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;

import java.util.Objects;

/**
 * Immutable registration record of one view class: the singleton instance and the frozen
 * config its {@code onInit} produced at registration time.
 */
@ApiStatus.Internal
public final class RegisteredView {

    private final Class<? extends View> type;
    private final View instance;
    private final ViewConfig config;

    /**
     * Creates the registration record.
     *
     * @param type     the registered view class
     * @param instance the view singleton
     * @param config   the frozen registration-time config
     */
    public RegisteredView(@NotNull Class<? extends View> type, @NotNull View instance,
                          @NotNull ViewConfig config) {
        this.type = Objects.requireNonNull(type, "type");
        this.instance = Objects.requireNonNull(instance, "instance");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Returns the registered view class.
     *
     * @return the view class
     */
    public @NotNull Class<? extends View> type() {
        return type;
    }

    /**
     * Returns the view singleton.
     *
     * @return the view instance
     */
    public @NotNull View instance() {
        return instance;
    }

    /**
     * Returns the frozen registration-time config; per-open overrides are merged onto a
     * copy by the open phase, never onto this instance.
     *
     * @return the immutable view config
     */
    public @NotNull ViewConfig config() {
        return config;
    }
}
```

**internal/session/ViewSession.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.session;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentTable;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime state of one (player, open) pair: lifecycle status, container, effective config,
 * resolved layout, component table, state storage and deferred end-of-tick operations.
 *
 * <p>Created in {@link Status#OPENING} by the open phase and mutated only by the engine on
 * the main thread; everything here is dropped when the session closes.
 */
@ApiStatus.Internal
public final class ViewSession {

    /**
     * Lifecycle status of a session.
     */
    public enum Status {

        /** The open phase is running; no container is shown yet. */
        OPENING,

        /** The container is shown; clicks and updates are routed. */
        ACTIVE,

        /** A deferred close or navigation is pending; further clicks are swallowed. */
        TRANSITIONING,

        /** The session tore down; terminal. */
        CLOSED
    }

    private final Player player;
    private final RegisteredView registered;
    private final ViewArguments arguments;
    private final StateStore stateStore;
    private final ComponentTable components = new ComponentTable();
    private final List<Runnable> deferredOps = new ArrayList<>();

    private Status status = Status.OPENING;
    private Inventory inventory;
    private ViewConfig effectiveConfig;
    private ResolvedLayout layout;
    private BukkitTask updateTask;

    /**
     * Creates a session in {@link Status#OPENING}.
     *
     * @param player     the viewer
     * @param registered the registration of the opened view
     * @param arguments  the arguments this open was requested with
     * @param stateStore the per-session state storage, sized for the view's token table
     */
    public ViewSession(@NotNull Player player, @NotNull RegisteredView registered,
                       @NotNull ViewArguments arguments, @NotNull StateStore stateStore) {
        this.player = Objects.requireNonNull(player, "player");
        this.registered = Objects.requireNonNull(registered, "registered");
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        // the registered config applies until the open phase merges per-open overrides
        this.effectiveConfig = registered.config();
    }

    /**
     * Returns the viewer of this session.
     *
     * @return the player
     */
    public @NotNull Player player() {
        return player;
    }

    /**
     * Returns the registration of the opened view.
     *
     * @return the registered view
     */
    public @NotNull RegisteredView registered() {
        return registered;
    }

    /**
     * Returns the arguments this open was requested with.
     *
     * @return the open arguments; empty when none were passed
     */
    public @NotNull ViewArguments arguments() {
        return arguments;
    }

    /**
     * Returns the per-session state storage.
     *
     * @return the state store
     */
    public @NotNull StateStore stateStore() {
        return stateStore;
    }

    /**
     * Returns this session's component table; one stable instance for the session's lifetime.
     *
     * @return the component table
     */
    public @NotNull ComponentTable components() {
        return components;
    }

    /**
     * Returns the current lifecycle status.
     *
     * @return the status
     */
    public @NotNull Status status() {
        return status;
    }

    /**
     * Sets the lifecycle status; called only by the engine.
     *
     * @param s the new status
     */
    public void status(@NotNull Status s) {
        this.status = Objects.requireNonNull(s, "status");
    }

    /**
     * Returns the top container of this session.
     *
     * @return the container, or {@code null} until the open phase creates it
     */
    public @Nullable Inventory inventory() {
        return inventory;
    }

    /**
     * Sets the top container once it is created.
     *
     * @param inv the created container
     */
    public void inventory(@NotNull Inventory inv) {
        this.inventory = Objects.requireNonNull(inv, "inventory");
    }

    /**
     * Returns the effective configuration of this session: the registered config until the
     * open phase applies per-open overrides.
     *
     * @return the effective config, never {@code null}
     */
    public @NotNull ViewConfig effectiveConfig() {
        return effectiveConfig;
    }

    /**
     * Sets the effective configuration after per-open overrides were merged.
     *
     * @param c the merged config
     */
    public void effectiveConfig(@NotNull ViewConfig c) {
        this.effectiveConfig = Objects.requireNonNull(c, "effectiveConfig");
    }

    /**
     * Returns the resolved layout of this session.
     *
     * @return the resolved layout, or {@code null} until layout resolution ran
     */
    public @Nullable ResolvedLayout layout() {
        return layout;
    }

    /**
     * Sets the resolved layout.
     *
     * @param l the resolved layout
     */
    public void layout(@NotNull ResolvedLayout l) {
        this.layout = Objects.requireNonNull(l, "layout");
    }

    /**
     * Returns the scheduled update task of this session.
     *
     * @return the task, or {@code null} when scheduling is disabled or not started
     */
    public @Nullable BukkitTask updateTask() {
        return updateTask;
    }

    /**
     * Sets or clears the scheduled update task.
     *
     * @param t the task, or {@code null} to clear it
     */
    public void updateTask(@Nullable BukkitTask t) {
        this.updateTask = t;
    }

    /**
     * Returns whether this session is currently active.
     *
     * @return {@code true} only while the status is {@link Status#ACTIVE}
     */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /**
     * Returns the operations deferred to the end of the current tick; the engine appends
     * during click dispatch and drains in FIFO order.
     *
     * @return the mutable deferred-operations list, one stable instance per session
     */
    public @NotNull List<Runnable> deferredOps() {
        return deferredOps;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewSessionTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/RegisteredView.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/session/ViewSession.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/session/ViewSessionTest.java
git commit -m "feat(inventory-api): add per-open ViewSession runtime state and RegisteredView record"
```

- [ ] **Step 6: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.session;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRegistryTest {

    private ServerMock server;
    private SessionRegistry registry;
    private RegisteredView registered;

    static final class ProbeView extends View {
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        registry = new SessionRegistry();
        registered = new RegisteredView(ProbeView.class, new ProbeView(),
                new ViewConfigBuilder().title("t").rows(1).build());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession sessionFor(PlayerMock player) {
        return new ViewSession(player, registered, ViewArguments.empty(), new StateStore(0));
    }

    @Test
    void registerThenFind_returnsSessionByPlayerId() {
        PlayerMock player = server.addPlayer("first");
        ViewSession session = sessionFor(player);

        registry.register(session);

        assertSame(session, registry.find(player.getUniqueId()).orElseThrow());
    }

    @Test
    void find_unknownPlayer_returnsEmpty() {
        assertFalse(registry.find(UUID.randomUUID()).isPresent());
    }

    @Test
    void unregister_removesTheMapping() {
        PlayerMock player = server.addPlayer("first");
        ViewSession session = sessionFor(player);
        registry.register(session);

        registry.unregister(session);

        assertFalse(registry.find(player.getUniqueId()).isPresent());
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void register_samePlayerAgain_replacesTheMapping() {
        PlayerMock player = server.addPlayer("first");
        ViewSession previous = sessionFor(player);
        ViewSession replacement = sessionFor(player);
        registry.register(previous);

        registry.register(replacement);

        assertSame(replacement, registry.find(player.getUniqueId()).orElseThrow());
        assertEquals(1, registry.all().size());
    }

    @Test
    void unregister_staleReplacedSession_keepsTheCurrentMapping() {
        PlayerMock player = server.addPlayer("first");
        ViewSession previous = sessionFor(player);
        ViewSession replacement = sessionFor(player);
        registry.register(previous);
        registry.register(replacement);

        registry.unregister(previous);

        assertSame(replacement, registry.find(player.getUniqueId()).orElseThrow());
    }

    @Test
    void all_containsEveryRegisteredSession_andIsUnmodifiable() {
        ViewSession first = sessionFor(server.addPlayer("first"));
        ViewSession second = sessionFor(server.addPlayer("second"));
        registry.register(first);
        registry.register(second);

        Collection<ViewSession> all = registry.all();

        assertEquals(2, all.size());
        assertTrue(all.contains(first));
        assertTrue(all.contains(second));
        assertThrows(UnsupportedOperationException.class, all::clear);
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=SessionRegistryTest"
Expected: FAIL (compilation error: class SessionRegistry does not exist)

- [ ] **Step 8: Write minimal implementation**

**internal/session/SessionRegistry.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.session;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the live view session of every player, keyed by player UUID (one open view per
 * player); the v3 counterpart of the 2.x {@code ViewerRegistry} as a DI-managed bean.
 */
@Component
@ApiStatus.Internal
public final class SessionRegistry {

    private final Map<UUID, ViewSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a session under its player's UUID, replacing any previous mapping.
     *
     * @param session the session to register
     */
    public void register(@NotNull ViewSession session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session.player().getUniqueId(), session);
    }

    /**
     * Removes a session's mapping.
     *
     * @param session the session to unregister
     */
    public void unregister(@NotNull ViewSession session) {
        Objects.requireNonNull(session, "session");
        // identity-guarded so unregistering an already replaced session never drops its successor
        sessions.remove(session.player().getUniqueId(), session);
    }

    /**
     * Looks up the session of a player.
     *
     * @param playerId the player's UUID
     * @return the player's session, or empty when none is registered
     */
    public @NotNull Optional<ViewSession> find(@NotNull UUID playerId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(playerId, "playerId")));
    }

    /**
     * Returns every registered session.
     *
     * @return an unmodifiable live view of all sessions
     */
    public @NotNull Collection<ViewSession> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=SessionRegistryTest,ViewSessionTest"
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/session/SessionRegistry.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/session/SessionRegistryTest.java
git commit -m "feat(inventory-api): add SessionRegistry keyed by player UUID"
```

---

### Task 11: Context implementations + ViewEngine skeleton

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/AbstractViewContext.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/OpenContextImpl.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/RenderContextImpl.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/UpdateContextImpl.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/SlotClickContextImpl.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/CloseContextImpl.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/PlainViewContextImpl.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/ContextPhaseValidityTest.java

Note: depends on Task 10's session machinery (`ViewSession`, `SessionRegistry`, `RegisteredView`) and the `ViewRegistry` type per the Shared Type Contracts. `ViewEngine` is created here as a skeleton with the exact contracted constructor and method signatures; Task 12 fills `open`/`close`/`update`/`click` (and Tasks 13/14/16 fill `bukkitClose`/`drag`/`flushDirty`/`flushShared`) by modifying this file — the bodies left throwing here name their task. `PlainViewContextImpl` is also listed as a Create in Task 18; the content below is byte-identical, so executing both is safe.

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.StaleContextException;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
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
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextPhaseValidityTest {

    private static final Logger CLOSE_LOGGER = Logger.getLogger(CloseContextImpl.class.getName());

    private ServerMock server;
    private Plugin plugin;
    private TitleUpdater titleUpdater;
    private ViewEngine engine;
    private PlayerMock player;

    static final class ProbeView extends View {
        final MutableState<Integer> counter = mutableState(0);
    }

    static final class TargetView extends View {
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Plugin.class);
        titleUpdater = mock(TitleUpdater.class);
        engine = new ViewEngine(plugin, new ViewRegistry(), new SessionRegistry(),
                new SlotPainter(new NoopPlaceholderApplier()), titleUpdater);
        player = server.addPlayer("tester");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ViewConfig config() {
        return new ViewConfigBuilder().title("Probe").rows(2).build();
    }

    private static ViewConfig layoutConfig() {
        return new ViewConfigBuilder().title("Probe").layout("  AAA    ", "         ").build();
    }

    // builds a session exactly like the Task 10 tests: direct RegisteredView + StateStore wiring
    private ViewSession sessionFor(View view, ViewConfig config) {
        RegisteredView registered = new RegisteredView(view.getClass(), view, config);
        ViewSession session = new ViewSession(player, registered, ViewArguments.empty(),
                new StateStore(view.tokenTable().size()));
        session.effectiveConfig(config);
        session.status(ViewSession.Status.OPENING);
        return session;
    }

    private RenderContextImpl renderContext(ViewConfig config) {
        ViewSession session = sessionFor(new ProbeView(), config);
        session.layout(ResolvedLayout.resolve(config));
        return new RenderContextImpl(session, engine);
    }

    private InventoryClickEvent clickEvent(Inventory top, int rawSlot, ItemStack current) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getPlayer()).thenReturn(player);
        when(view.convertSlot(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(view.getItem(rawSlot)).thenReturn(current);
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    @Test
    void baseContext_exposesSessionAndEngineCollaborators() {
        ProbeView view = new ProbeView();
        ViewConfig config = config();
        ViewSession session = sessionFor(view, config);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertSame(player, context.player());
        assertEquals(player.getUniqueId(), context.playerId());
        assertSame(view, context.view());
        assertSame(config, context.config());
        assertSame(plugin, context.plugin());
        assertSame(session.arguments(), context.arguments());
        assertSame(session, context.session());
    }

    @Test
    void inventory_beforeContainerAndAfterClose_throwsIllegalStateException() {
        ViewSession session = sessionFor(new ProbeView(), config());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        assertThrows(IllegalStateException.class, context::inventory);

        Inventory inventory = Bukkit.createInventory(null, 18);
        session.inventory(inventory);
        session.status(ViewSession.Status.ACTIVE);
        assertSame(inventory, context.inventory());

        session.status(ViewSession.Status.CLOSED);
        assertThrows(IllegalStateException.class, context::inventory);
    }

    @Test
    void contextActive_trueOnlyDuringOpeningAndActive() {
        ViewSession session = sessionFor(new ProbeView(), config());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        session.status(ViewSession.Status.OPENING);
        assertTrue(context.contextActive());
        assertFalse(context.isActive());

        session.status(ViewSession.Status.ACTIVE);
        assertTrue(context.contextActive());
        assertTrue(context.isActive());

        session.status(ViewSession.Status.TRANSITIONING);
        assertFalse(context.contextActive());

        session.status(ViewSession.Status.CLOSED);
        assertFalse(context.contextActive());
    }

    @Test
    void stateWrite_isAllowedDuringOpening() {
        ProbeView view = new ProbeView();
        ViewSession session = sessionFor(view, config());
        OpenContextImpl context = new OpenContextImpl(session, engine);

        // the session is OPENING; a real MutableState token must accept the write
        view.counter.set(context, 5);

        assertEquals(5, view.counter.get(context));
    }

    @Test
    void stateAccess_afterClosed_throwsStaleContextException() {
        ProbeView view = new ProbeView();
        ViewSession session = sessionFor(view, config());
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        session.status(ViewSession.Status.ACTIVE);
        view.counter.set(context, 1);

        session.status(ViewSession.Status.CLOSED);

        assertThrows(StaleContextException.class, () -> view.counter.get(context));
        assertThrows(StaleContextException.class, () -> view.counter.set(context, 2));
    }

    @Test
    void updateTitle_delegatesToTheEngineTitleUpdater() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);

        context.updateTitle("&aNew Title");

        verify(titleUpdater).update(player, "&aNew Title");
    }

    @Test
    void close_duringClickDispatch_isDeferredAndLeavesActive() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        engine.clickDispatch(true);

        // the skeleton engine close throws UnsupportedOperationException; deferral must not reach it
        assertDoesNotThrow(context::close);

        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        assertEquals(1, session.deferredOps().size());
    }

    @Test
    void openView_duringClickDispatch_isDeferred() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        engine.clickDispatch(true);

        // the skeleton engine open throws UnsupportedOperationException; deferral must not reach it
        assertDoesNotThrow(() -> context.openView(TargetView.class));

        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        assertEquals(1, session.deferredOps().size());
    }

    @Test
    void update_duringClickDispatch_isNotDeferred() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        session.inventory(Bukkit.createInventory(null, 18));
        PlainViewContextImpl context = new PlainViewContextImpl(session, engine);
        engine.clickDispatch(true);

        try {
            context.update();
        } catch (UnsupportedOperationException ignored) {
            // reaching the engine's not-yet-implemented update pass proves the call was not deferred
        }

        assertTrue(session.deferredOps().isEmpty());
        assertEquals(ViewSession.Status.ACTIVE, session.status());
    }

    @Test
    void engineClickDispatchFlag_defaultsFalseAndIsToggleable() {
        assertFalse(engine.isInClickDispatch());
        engine.clickDispatch(true);
        assertTrue(engine.isInClickDispatch());
        engine.clickDispatch(false);
        assertFalse(engine.isInClickDispatch());
    }

    @Test
    void assertMainThread_onMainPasses_offMainThrowsNamingTheOperation() throws Exception {
        assertDoesNotThrow(() -> ViewEngine.assertMainThread("test-op"));

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                ViewEngine.assertMainThread("test-op");
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        thread.start();
        thread.join();

        assertTrue(thrown.get() instanceof IllegalStateException,
                "off-main assertMainThread must throw IllegalStateException, got " + thrown.get());
        assertTrue(thrown.get().getMessage().contains("test-op"));
    }

    @Test
    void openContext_inventoryUpdateAndUpdateTitle_throwNamingTheOpenPhase() {
        ViewSession session = sessionFor(new ProbeView(), config());
        OpenContextImpl context = new OpenContextImpl(session, engine);

        IllegalStateException inventoryError = assertThrows(IllegalStateException.class, context::inventory);
        IllegalStateException updateError = assertThrows(IllegalStateException.class, context::update);
        IllegalStateException titleError = assertThrows(IllegalStateException.class,
                () -> context.updateTitle("title"));

        assertTrue(inventoryError.getMessage().contains("onOpen"));
        assertTrue(updateError.getMessage().contains("onOpen"));
        assertTrue(titleError.getMessage().contains("onOpen"));
        verifyNoInteractions(titleUpdater);
    }

    @Test
    void openContext_recordsOverridesAndCancellation() {
        ViewSession session = sessionFor(new ProbeView(), config());
        OpenContextImpl context = new OpenContextImpl(session, engine);

        assertNull(context.overriddenTitle());
        assertNull(context.overriddenRows());
        assertFalse(context.isOpenCancelled());

        context.overrideTitle("Bank");
        context.overrideRows(3);
        context.cancelOpen();

        assertEquals("Bank", context.overriddenTitle());
        assertEquals(Integer.valueOf(3), context.overriddenRows());
        assertTrue(context.isOpenCancelled());
    }

    @Test
    void updateContext_exposesTrigger() {
        ViewSession session = sessionFor(new ProbeView(), config());
        UpdateContextImpl context = new UpdateContextImpl(session, engine, UpdateTrigger.SCHEDULED);

        assertEquals(UpdateTrigger.SCHEDULED, context.trigger());
    }

    @Test
    void slotClickContext_exposesEventDataAndOwnCancellationFlag() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        Inventory top = Bukkit.createInventory(null, 18);
        session.inventory(top);
        ItemStack current = new ItemStack(Material.STONE);
        InventoryClickEvent event = clickEvent(top, 12, current);

        SlotClickContextImpl context = new SlotClickContextImpl(session, engine, event, false, true);

        assertEquals(12, context.slot());
        assertEquals(ClickType.LEFT, context.clickType());
        assertSame(current, context.item());
        assertSame(event, context.rawEvent());
        assertFalse(context.isPlayerInventory());
        assertTrue(context.isCancelled(), "the flag starts at the pre-cancel decision");

        context.setCancelled(false);
        assertFalse(context.isCancelled());
        // the decision lives on the context until the routing phase applies it to the event
        assertFalse(event.isCancelled());
    }

    @Test
    void slotClickContext_bottomInventoryClick_reportsPlayerInventory() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        Inventory top = Bukkit.createInventory(null, 18);
        session.inventory(top);
        InventoryClickEvent event = clickEvent(top, 20, null);

        SlotClickContextImpl context = new SlotClickContextImpl(session, engine, event, true, false);

        assertTrue(context.isPlayerInventory());
        assertFalse(context.isCancelled());
        context.setCancelled(true);
        assertTrue(context.isCancelled());
    }

    @Test
    void closeContext_updateThrowsAndCloseIsNoOp() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        CloseContextImpl context = new CloseContextImpl(session, engine, CloseReason.API);

        assertEquals(CloseReason.API, context.reason());

        IllegalStateException updateError = assertThrows(IllegalStateException.class, context::update);
        assertTrue(updateError.getMessage().contains("onClose"));

        // the session is already closing; close() must not call the engine (which would throw)
        assertDoesNotThrow(context::close);
    }

    @Test
    void closeContext_openViewIsDroppedWithSevereLogAndNoEngineInteraction() {
        ViewSession session = sessionFor(new ProbeView(), config());
        session.status(ViewSession.Status.ACTIVE);
        CloseContextImpl context = new CloseContextImpl(session, engine, CloseReason.PLAYER);

        CapturingHandler handler = new CapturingHandler();
        CLOSE_LOGGER.addHandler(handler);
        try {
            // the skeleton engine open throws UnsupportedOperationException; a dropped
            // navigation must never reach it
            assertDoesNotThrow(() -> context.openView(TargetView.class));
        } finally {
            CLOSE_LOGGER.removeHandler(handler);
        }

        assertEquals(ViewSession.Status.ACTIVE, session.status(), "a dropped navigation must not defer");
        assertTrue(session.deferredOps().isEmpty());
        assertEquals(1, handler.records.size());
        assertEquals(Level.SEVERE, handler.records.get(0).getLevel());
    }

    @Test
    void renderContext_slotOutOfBounds_throwsViewConfigurationException() {
        RenderContextImpl render = renderContext(config()); // 2 rows -> slots 0-17

        assertThrows(ViewConfigurationException.class, () -> render.slot(-1));
        assertThrows(ViewConfigurationException.class, () -> render.slot(18));
    }

    @Test
    void renderContext_slotDeclarations_materializeIntoSessionComponents() {
        RenderContextImpl render = renderContext(config());
        render.slot(0, new ItemStack(Material.STONE));
        render.slot(2, 3).item(new ItemStack(Material.PAPER)); // 1-based row/column -> raw slot 11

        render.materializeAll();

        ViewSession session = render.session();
        assertNotNull(session.components().componentAt(0));
        assertNotNull(session.components().componentAt(11));
        assertEquals(2, session.components().all().size());
    }

    @Test
    void renderContext_layoutSlot_bindsEverySlotOfTheCharacter() {
        RenderContextImpl render = renderContext(layoutConfig()); // 'A' occupies slots 2, 3, 4
        render.layoutSlot('A', new ItemStack(Material.STONE));

        render.materializeAll();

        ViewSession session = render.session();
        assertNotNull(session.components().componentAt(2));
        assertNotNull(session.components().componentAt(3));
        assertNotNull(session.components().componentAt(4));
        assertNull(session.components().componentAt(1));
    }

    @Test
    void renderContext_layoutSlotUnknownChar_throwsViewConfigurationException() {
        RenderContextImpl render = renderContext(layoutConfig());

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> render.layoutSlot('Z'));

        assertTrue(error.getMessage().contains("'Z'"));
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

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ContextPhaseValidityTest"
Expected: FAIL (compilation error: classes ViewEngine, AbstractViewContext and the context impls do not exist)

- [ ] **Step 3: Write minimal implementation**

**internal/engine/ViewEngine.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

/**
 * Orchestrator of the v3 view lifecycle and sole session mutator. This skeleton pins the
 * contracted surface; the phase handlers filling {@link #open}, {@link #close},
 * {@link #update}, {@link #click}, {@link #drag}, {@link #bukkitClose}, {@link #flushDirty}
 * and {@link #flushShared} are added by plan tasks 12-16.
 */
@Component
@ApiStatus.Internal
public final class ViewEngine {

    private final Plugin plugin;
    // collaborators consumed by the phase handlers added in tasks 12-16
    private final ViewRegistry views;
    private final SessionRegistry sessions;
    private final SlotPainter painter;
    private final TitleUpdater titleUpdater;

    private boolean inClickDispatch;

    /**
     * Creates the engine.
     *
     * @param plugin       the plugin owning the inventory-api runtime
     * @param views        the view class registry
     * @param sessions     the per-player session registry
     * @param painter      the slot painting strategy
     * @param titleUpdater the in-place title update strategy
     */
    public ViewEngine(Plugin plugin, ViewRegistry views, SessionRegistry sessions,
                      SlotPainter painter, TitleUpdater titleUpdater) {
        this.plugin = plugin;
        this.views = views;
        this.sessions = sessions;
        this.painter = painter;
        this.titleUpdater = titleUpdater;
    }

    /**
     * Opens a registered view for a player, replacing any current session.
     *
     * @param player    the viewer
     * @param viewType  the registered view class
     * @param arguments the open arguments
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> viewType,
                     @NotNull ViewArguments arguments) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Closes a session with the given reason.
     *
     * @param session the session to close
     * @param reason  the close reason
     */
    public void close(@NotNull ViewSession session, @NotNull CloseReason reason) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Runs an update pass on a session.
     *
     * @param session the session to update
     * @param trigger the cause of the update
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Routes a Bukkit click event into the session per the click policy.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void click(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        throw new UnsupportedOperationException("implemented in Task 12");
    }

    /**
     * Applies the drag policy of a session to a Bukkit drag event.
     *
     * @param session the affected session
     * @param event   the Bukkit event
     */
    public void drag(@NotNull ViewSession session, @NotNull InventoryDragEvent event) {
        throw new UnsupportedOperationException("implemented in Task 14");
    }

    /**
     * Handles a Bukkit close event for a session, guarded by container identity.
     *
     * @param session the player's session
     * @param event   the Bukkit event
     */
    public void bukkitClose(@NotNull ViewSession session, @NotNull InventoryCloseEvent event) {
        throw new UnsupportedOperationException("implemented in Task 13");
    }

    /**
     * Flushes dirty state tokens of a session (STATE_CHANGE re-render, cascade cap 8).
     *
     * @param session the session to flush
     */
    public void flushDirty(@NotNull ViewSession session) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }

    /**
     * Flushes shared-state watchers of every open session of a view.
     *
     * @param owner the view singleton whose sessions should flush
     */
    public void flushShared(@NotNull View owner) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }

    /**
     * Defers an operation to the end of the current tick. The session leaves ACTIVE
     * immediately (TRANSITIONING) so further clicks are swallowed until the operation runs.
     *
     * @param session the session the operation belongs to
     * @param op      the operation to run at end of tick
     */
    public void defer(@NotNull ViewSession session, @NotNull Runnable op) {
        session.status(ViewSession.Status.TRANSITIONING);
        session.deferredOps().add(op);
        Bukkit.getScheduler().runTask(plugin, op);
    }

    /**
     * Returns whether a click event is currently being dispatched; drives operation deferral.
     *
     * @return {@code true} while a click is being dispatched
     */
    public boolean isInClickDispatch() {
        return inClickDispatch;
    }

    /**
     * Marks the engine as inside or outside click dispatch; toggled by the click routing
     * phase (plan task 14) around handler execution.
     *
     * @param active {@code true} while a click is being dispatched
     */
    @ApiStatus.Internal
    public void clickDispatch(boolean active) {
        this.inClickDispatch = active;
    }

    /**
     * Returns the plugin owning the inventory-api runtime.
     *
     * @return the owning plugin
     */
    public @NotNull Plugin plugin() {
        return plugin;
    }

    /**
     * Returns the in-place title update strategy.
     *
     * @return the title updater
     */
    public @NotNull TitleUpdater titleUpdater() {
        return titleUpdater;
    }

    /**
     * Throws when called off the main server thread; a missing server (pure unit tests)
     * passes on any thread.
     *
     * @param operation the operation name used in the error message
     * @throws IllegalStateException when called off the main thread
     */
    public static void assertMainThread(@NotNull String operation) {
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(operation + " must run on the main thread");
        }
    }
}
```

**internal/context/AbstractViewContext.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateBackedContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Objects;
import java.util.UUID;

/**
 * Base implementation shared by every per-phase context: resolves all {@link ViewContext}
 * accessors from the bound session and routes the mutating operations through the engine.
 * Implements the {@link StateBackedContext} seam so state tokens resolve their storage
 * without casting to concrete context classes.
 *
 * <p>Deferral policy (§5.4): {@link #close()} and {@link #openView} are deferred to the end
 * of the tick during click dispatch; {@link #update()} is never deferred because an update
 * pass is safe mid-click.
 */
@ApiStatus.Internal
public abstract class AbstractViewContext implements ViewContext, StateBackedContext {

    private final ViewSession session;
    private final ViewEngine engine;

    /**
     * Binds the context to one session and the engine.
     *
     * @param session the session this context belongs to
     * @param engine  the engine executing lifecycle operations
     */
    protected AbstractViewContext(@NotNull ViewSession session, @NotNull ViewEngine engine) {
        this.session = Objects.requireNonNull(session, "session");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Returns the session this context is bound to.
     *
     * @return the backing session
     */
    public @NotNull ViewSession session() {
        return session;
    }

    /**
     * Returns the engine this context routes operations through.
     *
     * @return the view engine
     */
    protected @NotNull ViewEngine engine() {
        return engine;
    }

    @Override
    public @NotNull Player player() {
        return session.player();
    }

    @Override
    public @NotNull UUID playerId() {
        return session.player().getUniqueId();
    }

    @Override
    public @NotNull View view() {
        return session.registered().instance();
    }

    @Override
    public @NotNull ViewConfig config() {
        return session.effectiveConfig();
    }

    @Override
    public @NotNull Plugin plugin() {
        return engine.plugin();
    }

    @Override
    public @NotNull ViewArguments arguments() {
        return session.arguments();
    }

    @Override
    public @NotNull Inventory inventory() {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            throw new IllegalStateException("the container of this session has not been created yet");
        }
        if (session.status() == ViewSession.Status.CLOSED) {
            throw new IllegalStateException("the session is closed; its container is no longer available");
        }
        return inventory;
    }

    @Override
    public boolean isActive() {
        return session.isActive();
    }

    @Override
    public void update() {
        // explicit updates are safe mid-click; only close/openView defer to end of tick
        engine.update(session, UpdateTrigger.EXPLICIT);
    }

    @Override
    public void close() {
        if (engine.isInClickDispatch()) {
            engine.defer(session, () -> engine.close(session, CloseReason.API));
            return;
        }
        engine.close(session, CloseReason.API);
    }

    @Override
    public void updateTitle(@NotNull String title) {
        engine.titleUpdater().update(player(), title);
    }

    @Override
    public void openView(@NotNull Class<? extends View> target) {
        openView(target, ViewArguments.empty());
    }

    @Override
    public void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments) {
        if (engine.isInClickDispatch()) {
            engine.defer(session, () -> engine.open(player(), target, arguments));
            return;
        }
        engine.open(player(), target, arguments);
    }

    @Override
    public @NotNull StateStore stateStore() {
        return session.stateStore();
    }

    @Override
    public @NotNull View owner() {
        return session.registered().instance();
    }

    @Override
    public boolean contextActive() {
        ViewSession.Status status = session.status();
        return status == ViewSession.Status.OPENING || status == ViewSession.Status.ACTIVE;
    }
}
```

**internal/context/OpenContextImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Context for {@code View.onOpen}: records per-open overrides and the cancel decision.
 * No container exists yet, so {@link #inventory()}, {@link #update()} and
 * {@link #updateTitle(String)} throw {@link IllegalStateException} naming the phase.
 */
@ApiStatus.Internal
public final class OpenContextImpl extends AbstractViewContext implements OpenContext {

    private String overriddenTitle;
    private Integer overriddenRows;
    private boolean openCancelled;

    /**
     * Creates the context for one open attempt.
     *
     * @param session the session being opened
     * @param engine  the engine executing the open
     */
    public OpenContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine) {
        super(session, engine);
    }

    @Override
    public void overrideTitle(@NotNull String title) {
        this.overriddenTitle = Objects.requireNonNull(title, "title");
    }

    @Override
    public void overrideRows(int rows) {
        this.overriddenRows = rows;
    }

    @Override
    public void cancelOpen() {
        this.openCancelled = true;
    }

    @Override
    public boolean isOpenCancelled() {
        return openCancelled;
    }

    /**
     * Returns the per-open title override recorded in {@code onOpen}.
     *
     * @return the overridden title, or {@code null} when none was recorded
     */
    public @Nullable String overriddenTitle() {
        return overriddenTitle;
    }

    /**
     * Returns the per-open rows override recorded in {@code onOpen}.
     *
     * @return the overridden row count, or {@code null} when none was recorded
     */
    public @Nullable Integer overriddenRows() {
        return overriddenRows;
    }

    @Override
    public @NotNull Inventory inventory() {
        throw new IllegalStateException(
                "inventory() is not available during onOpen; the container is created after onOpen completes");
    }

    @Override
    public void update() {
        throw new IllegalStateException(
                "update() is not available during onOpen; the first paint happens after onOpen completes");
    }

    @Override
    public void updateTitle(@NotNull String title) {
        throw new IllegalStateException(
                "updateTitle(String) is not available during onOpen; use overrideTitle(String) instead");
    }
}
```

**internal/context/RenderContextImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.component.ItemComponentBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ItemComponentBuilderImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Context for {@code View.onFirstRender}: collects component declarations in order and
 * freezes them into the session's component table via {@link #materializeAll()}, called by
 * the first-render phase after the handler returns.
 */
@ApiStatus.Internal
public final class RenderContextImpl extends AbstractViewContext implements RenderContext {

    private final List<PendingComponent> pending = new ArrayList<>();

    /**
     * Creates the context for one first render.
     *
     * @param session the session being rendered
     * @param engine  the engine executing the render
     */
    public RenderContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine) {
        super(session, engine);
    }

    @Override
    public @NotNull ItemComponentBuilder slot(int slot) {
        int size = session().effectiveConfig().rows() * Layout.ROW_WIDTH;
        if (slot < 0 || slot >= size) {
            throw new ViewConfigurationException("slot " + slot + " is out of bounds for a "
                    + session().effectiveConfig().rows() + "-row view (0-" + (size - 1) + ")");
        }
        return register(new int[]{slot});
    }

    @Override
    public @NotNull ItemComponentBuilder slot(int row, int column) {
        return slot((row - 1) * Layout.ROW_WIDTH + (column - 1));
    }

    @Override
    public @NotNull ItemComponentBuilder slot(int slot, @NotNull ItemStack item) {
        return slot(slot).item(item);
    }

    @Override
    public @NotNull ItemComponentBuilder layoutSlot(char character) {
        ResolvedLayout layout = session().layout();
        if (layout == null || !layout.hasChar(character)) {
            throw new ViewConfigurationException("layout character '" + character
                    + "' is not present in the layout of " + view().getClass().getName());
        }
        return register(layout.slotsOf(character));
    }

    @Override
    public @NotNull ItemComponentBuilder layoutSlot(char character, @NotNull ItemStack item) {
        return layoutSlot(character).item(item);
    }

    /**
     * Materializes every collected declaration into the session's component table, in
     * declaration order; the table validates slot overlaps and missing item sources.
     *
     * @throws ViewConfigurationException when a declaration overlaps slots or has no item source
     */
    public void materializeAll() {
        for (PendingComponent declaration : pending) {
            session().components().add(declaration.builder.materialize(declaration.slots));
        }
    }

    private ItemComponentBuilder register(int[] slots) {
        ItemComponentBuilderImpl builder = new ItemComponentBuilderImpl();
        pending.add(new PendingComponent(builder, slots));
        return builder;
    }

    /** one collected declaration: the mutable builder plus its resolved slots. */
    private static final class PendingComponent {

        private final ItemComponentBuilderImpl builder;
        private final int[] slots;

        private PendingComponent(ItemComponentBuilderImpl builder, int[] slots) {
            this.builder = builder;
            this.slots = slots;
        }
    }
}
```

**internal/context/UpdateContextImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Context for {@code View.onUpdate}: a plain context carrying the trigger of the pass.
 */
@ApiStatus.Internal
public final class UpdateContextImpl extends AbstractViewContext implements UpdateContext {

    private final UpdateTrigger trigger;

    /**
     * Creates the context for one update pass.
     *
     * @param session the session being updated
     * @param engine  the engine executing the update
     * @param trigger the cause of this pass
     */
    public UpdateContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine,
                             @NotNull UpdateTrigger trigger) {
        super(session, engine);
        this.trigger = Objects.requireNonNull(trigger, "trigger");
    }

    @Override
    public @NotNull UpdateTrigger trigger() {
        return trigger;
    }
}
```

**internal/context/SlotClickContextImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Context for component click handlers and {@code View.onClick}: wraps one Bukkit click
 * event. The cancellation decision lives on an internal flag seeded with the pre-cancel
 * policy; the routing phase applies the final decision to the raw event after handlers run.
 */
@ApiStatus.Internal
public final class SlotClickContextImpl extends AbstractViewContext implements SlotClickContext {

    private final InventoryClickEvent event;
    private final boolean playerInventory;
    private boolean cancelled;

    /**
     * Creates the context for one intercepted click.
     *
     * @param session         the clicked session
     * @param engine          the engine dispatching the click
     * @param event           the underlying Bukkit event
     * @param playerInventory whether the click landed in the player's own (bottom) inventory
     * @param preCancelled    the cancellation decision computed from config and component policy
     */
    public SlotClickContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine,
                                @NotNull InventoryClickEvent event, boolean playerInventory,
                                boolean preCancelled) {
        super(session, engine);
        this.event = Objects.requireNonNull(event, "event");
        this.playerInventory = playerInventory;
        this.cancelled = preCancelled;
    }

    @Override
    public int slot() {
        return event.getRawSlot();
    }

    @Override
    public @NotNull ClickType clickType() {
        return event.getClick();
    }

    @Override
    public @Nullable ItemStack item() {
        return event.getCurrentItem();
    }

    @Override
    public boolean isPlayerInventory() {
        return playerInventory;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public @NotNull InventoryClickEvent rawEvent() {
        return event;
    }
}
```

**internal/context/CloseContextImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Context for {@code View.onClose}: the session is tearing down. {@link #update()} throws,
 * {@link #close()} is a no-op, and {@link #openView} is dropped with a SEVERE log (§5.4).
 */
@ApiStatus.Internal
public final class CloseContextImpl extends AbstractViewContext implements CloseContext {

    private static final Logger LOGGER = Logger.getLogger(CloseContextImpl.class.getName());

    private final CloseReason reason;

    /**
     * Creates the context for one teardown.
     *
     * @param session the closing session
     * @param engine  the engine executing the close
     * @param reason  why the session is closing
     */
    public CloseContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine,
                            @NotNull CloseReason reason) {
        super(session, engine);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public @NotNull CloseReason reason() {
        return reason;
    }

    @Override
    public void update() {
        throw new IllegalStateException("update() is not allowed during onClose; the session is tearing down");
    }

    @Override
    public void close() {
        // no-op: the session is already closing
    }

    @Override
    public void openView(@NotNull Class<? extends View> target) {
        openView(target, ViewArguments.empty());
    }

    @Override
    public void openView(@NotNull Class<? extends View> target, @NotNull ViewArguments arguments) {
        // navigation inside onClose is dropped, never deferred or executed (§5.4)
        LOGGER.severe("openView(" + target.getName() + ") called inside onClose of "
                + view().getClass().getName() + "; navigation during close is dropped");
    }
}
```

**internal/context/PlainViewContextImpl.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

/**
 * Plain phase-less context handed out by {@code ViewService.contextOf}; adds nothing on top of
 * the base context behavior.
 */
@ApiStatus.Internal
public final class PlainViewContextImpl extends AbstractViewContext {

    /**
     * Creates the context for a live session.
     */
    public PlainViewContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine) {
        super(session, engine);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ContextPhaseValidityTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/ContextPhaseValidityTest.java
git commit -m "feat(inventory-api): add v3 context implementations and ViewEngine skeleton"
```

---

### Task 12: ViewEngine open/close happy path

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java (complete replacement of the Task 11 skeleton)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/OpenPhase.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/ClosePhase.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/UpdatePhase.java (skeleton — body lands in Task 16)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/ClickRoutingPhase.java (skeleton — body lands in Task 14)
- Depends on: `internal/registry/RegisteredView.java` (created in Task 10 — do NOT recreate)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistry.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/schedule/ViewUpdateTask.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngineOpenCloseTest.java

Scope boundaries, stated explicitly:
- This task fills `open`, `close`, `bukkitClose` and `update` (minimal dispatch) on `ViewEngine`. `click`, `drag`, `defer`, `flushDirty` and `flushShared` REMAIN `UnsupportedOperationException` skeletons: `click`/`drag`/`defer` are implemented in Task 14, `flushDirty`/`flushShared` in Task 16. `isInClickDispatch()` returns `false` until Task 14 implements click dispatch. `update` dispatches to the `UpdatePhase` skeleton, which throws — that is fine, no test in this task exercises it, and the open flow never calls `update`.
- `bukkitClose` is implemented HERE with the container-identity guard (mirrors `CustomInventoryListener.onInventoryClose` lines 68–72), and `OpenPhase`/`ClosePhase` implement cancelOpen zero-side-effects ordering, REPLACED-close-after-commit and initialState type validation at the open site NOW — Task 13 (`ViewEngineOpenOrderingTest`) only ADDS regression tests that pin these behaviors; its tests must already pass against this implementation.
- `ViewRegistry` is created with the no-arg constructor only (per the amended contract). Task 17 ADDS the `ViewRegistry(ViewDiscoveryService)` DI constructor and `initialize(Context)`; do not add them here.
- Lifecycle handlers (`onOpen`/`onFirstRender`/`onClose`/`onInit`) are `protected` on the public `View` type, so phases and the registry dispatch them reflectively — the same pattern Task 17 pins for `invokeOnInit`.

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
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
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewEngineOpenCloseTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;
    private SimpleView simpleView;
    private ThrowOnOpenView throwOnOpenView;
    private ThrowOnRenderView throwOnRenderView;

    static final class SimpleView extends View {
        CloseReason lastCloseReason;
        int closeCount;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aSimple").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(0, new ItemStack(Material.STONE));
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
            closeCount++;
        }
    }

    static final class CounterView extends View {
        final MutableState<Integer> counter = mutableState(0);

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Counter").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            counter.update(context, value -> value + 1);
            context.slot(0).item(ctx -> new ItemStack(Material.PAPER, counter.get(ctx)));
        }
    }

    static final class ThrowOnOpenView extends View {
        boolean firstRendered;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ThrowOnOpen").rows(1);
        }

        @Override
        protected void onOpen(@NotNull OpenContext context) {
            throw new IllegalStateException("boom");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            firstRendered = true;
        }
    }

    static final class ThrowOnRenderView extends View {
        CloseReason lastCloseReason;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("ThrowOnRender").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            throw new IllegalStateException("boom");
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class ScheduledView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Scheduled").rows(1).scheduleUpdate(5L);
        }
    }

    static final class UnregisteredView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Unregistered").rows(1);
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        simpleView = new SimpleView();
        throwOnOpenView = new ThrowOnOpenView();
        throwOnRenderView = new ThrowOnRenderView();
        views.register(simpleView);
        views.register(new CounterView());
        views.register(throwOnOpenView);
        views.register(throwOnRenderView);
        views.register(new ScheduledView());
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow();
    }

    @Test
    void open_registersPaintsAndActivates() {
        engine.open(player, SimpleView.class, ViewArguments.empty());

        ViewSession session = session();
        assertEquals(ViewSession.Status.ACTIVE, session.status());
        assertSame(simpleView, session.registered().instance());
        Inventory inventory = session.inventory();
        assertNotNull(inventory);
        assertNotNull(inventory.getItem(0));
        assertEquals(Material.STONE, inventory.getItem(0).getType());
        assertSame(inventory, player.getOpenInventory().getTopInventory());
    }

    @Test
    void close_runsOnCloseWithReasonUnregistersAndIsIdempotent() {
        engine.open(player, SimpleView.class, ViewArguments.empty());
        ViewSession session = session();

        engine.close(session, CloseReason.API);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertEquals(CloseReason.API, simpleView.lastCloseReason);
        assertFalse(sessions.find(player.getUniqueId()).isPresent());

        // closing an already closed session is a no-op
        engine.close(session, CloseReason.API);
        assertEquals(1, simpleView.closeCount);
    }

    @Test
    void reopen_startsFromFreshPerSessionState() {
        engine.open(player, CounterView.class, ViewArguments.empty());
        ViewSession first = session();
        assertEquals(1, first.inventory().getItem(0).getAmount());

        engine.close(first, CloseReason.API);
        engine.open(player, CounterView.class, ViewArguments.empty());

        ViewSession second = session();
        assertNotSame(first, second);
        // a leaked store would paint amount 2 here
        assertEquals(1, second.inventory().getItem(0).getAmount());
    }

    @Test
    void open_unregisteredView_throwsUnknownViewException() {
        assertThrows(UnknownViewException.class,
                () -> engine.open(player, UnregisteredView.class, ViewArguments.empty()));

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void onOpenThrows_opensNothing() {
        engine.open(player, ThrowOnOpenView.class, ViewArguments.empty());

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertFalse(throwOnOpenView.firstRendered, "a throwing onOpen must cancel before first render");
    }

    @Test
    void onFirstRenderThrows_abortsWithOpenFailedCloseHookAndRegistersNothing() {
        engine.open(player, ThrowOnRenderView.class, ViewArguments.empty());

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(CloseReason.OPEN_FAILED, throwOnRenderView.lastCloseReason);
    }

    @Test
    void scheduledUpdates_taskStartsOnOpenAndIsCancelledOnClose() {
        engine.open(player, ScheduledView.class, ViewArguments.empty());
        ViewSession session = session();
        BukkitTask task = session.updateTask();
        assertNotNull(task);
        assertFalse(task.isCancelled());

        engine.close(session, CloseReason.API);

        assertTrue(task.isCancelled());
        assertNull(session.updateTask());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewEngineOpenCloseTest"
Expected: FAIL (compilation error: class ViewRegistry and the engine phase classes do not exist; the Task 11 `ViewEngine` skeleton still throws `UnsupportedOperationException` from `open`/`close`)

- [ ] **Step 3: Write minimal implementation**

`RegisteredView` already exists (Task 10 Step 3) — do not recreate it.

**internal/registry/ViewRegistry.java** (no-arg constructor version — Task 17 ADDS the `ViewRegistry(ViewDiscoveryService)` DI constructor and `initialize(Context)`)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.registry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of view singletons and their frozen configs, keyed by view class.
 *
 * <p>Discovery-driven registration ({@code initialize(Context)} and the DI constructor)
 * is added in plan task 17; until then views are registered directly via
 * {@link #register(View)}.
 */
@Component
@ApiStatus.Internal
public final class ViewRegistry {

    private final Map<Class<? extends View>, RegisteredView> views = new LinkedHashMap<>();

    /**
     * Creates a registry without discovery support; views are registered directly through
     * {@link #register(View)}.
     */
    public ViewRegistry() {
    }

    /**
     * Registers a view instance directly: freezes its token table, runs {@code onInit} once and
     * validates the resulting config.
     *
     * @param instance the view singleton to register
     * @throws ViewConfigurationException when the built config violates the validation rules
     * @throws IllegalStateException      when the view class is already registered
     */
    public void register(@NotNull View instance) {
        Objects.requireNonNull(instance, "instance");
        Class<? extends View> type = instance.getClass();
        if (views.containsKey(type)) {
            throw new IllegalStateException("view " + type.getName() + " is already registered");
        }
        instance.tokenTable().freeze();
        ViewConfigBuilder builder = new ViewConfigBuilder();
        invokeOnInit(instance, builder);
        ViewConfig config = builder.build();
        views.put(type, new RegisteredView(type, instance, config));
    }

    /**
     * Looks up the registration of a view class.
     *
     * @param type the view class to resolve
     * @return the registration, or empty when the class is not registered
     */
    public @NotNull Optional<RegisteredView> find(@NotNull Class<? extends View> type) {
        return Optional.ofNullable(views.get(type));
    }

    /**
     * Returns every registration in registration order.
     *
     * @return all registrations, unmodifiable
     */
    public @NotNull Collection<RegisteredView> all() {
        return Collections.unmodifiableCollection(views.values());
    }

    // onInit is protected on the public View type; the registry dispatches reflectively
    private static void invokeOnInit(View instance, ViewConfigBuilder builder) {
        try {
            Method method = View.class.getDeclaredMethod("onInit", ViewConfigBuilder.class);
            method.setAccessible(true);
            method.invoke(instance, builder);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onInit failed for view " + instance.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onInit for view " + instance.getClass().getName(), ex);
        }
    }
}
```

**internal/schedule/ViewUpdateTask.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.schedule;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Per-session scheduled update runnable, started by the first-render phase when
 * {@code updateIntervalTicks > 0} and cancelled by the close phase.
 */
@ApiStatus.Internal
public final class ViewUpdateTask implements Runnable {

    private final ViewEngine engine;
    private final ViewSession session;

    /**
     * Creates the task for one session.
     *
     * @param engine  the engine running the update pass
     * @param session the session to update on every tick of the timer
     */
    public ViewUpdateTask(@NotNull ViewEngine engine, @NotNull ViewSession session) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public void run() {
        // a timer tick racing the close (or a transitioning session) must not update
        if (!session.isActive()) {
            return;
        }
        engine.update(session, UpdateTrigger.SCHEDULED);
    }
}
```

**internal/engine/phase/OpenPhase.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.OpenContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.layout.ResolvedLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.InitialStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.StateStore;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * First half of the open pipeline (spec §7.2-7.6): session and store creation,
 * initial-state binding, {@code onOpen} with cancellation, previous-session replacement,
 * per-open config merge, layout resolution and container creation. Constructed and
 * invoked only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class OpenPhase {

    private static final Logger LOGGER = Logger.getLogger(OpenPhase.class.getName());

    private final ViewEngine engine;
    private final SessionRegistry sessions;

    /**
     * Creates the phase.
     *
     * @param engine   the owning engine, used to close a replaced previous session
     * @param sessions the per-player session registry
     */
    public OpenPhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Opens a session up to (and including) container creation; first render and
     * registration happen in {@link FirstRenderPhase}.
     *
     * @param player     the viewer
     * @param registered the registration of the view to open
     * @param arguments  the open arguments
     * @return the new session ready for first render, or {@code null} when the open was
     *         cancelled (by {@code cancelOpen()} or a throwing {@code onOpen}) with zero
     *         side effects
     * @throws IllegalArgumentException when an {@code initialState} argument is present
     *                                  with a mismatching type (propagates before any side effect)
     */
    public @Nullable ViewSession openSession(@NotNull Player player, @NotNull RegisteredView registered,
                                             @NotNull ViewArguments arguments) {
        View view = registered.instance();
        StateStore store = new StateStore(view.tokenTable().size());
        ViewSession session = new ViewSession(player, registered, arguments, store);
        session.status(ViewSession.Status.OPENING);
        // onOpen reads the registered config until the per-open overrides are committed
        session.effectiveConfig(registered.config());

        // a type mismatch propagates here, before any observable side effect
        bindInitialState(view, arguments, store);

        OpenContextImpl openContext = new OpenContextImpl(session, engine);
        boolean failed = false;
        try {
            invokeOnOpen(view, openContext);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onOpen failed for view " + view.getClass().getName()
                    + "; treating the open as cancelled", ex);
            failed = true;
        }
        if (failed || openContext.isOpenCancelled()) {
            // zero side effects: the player's previous session, if any, stays untouched
            return null;
        }

        // commit point: replace the previous session before the new container exists
        Optional<ViewSession> previous = sessions.find(player.getUniqueId());
        if (previous.isPresent()) {
            engine.close(previous.get(), CloseReason.REPLACED);
        }

        ViewConfig effective = registered.config()
                .withOverrides(openContext.overriddenTitle(), openContext.overriddenRows());
        session.effectiveConfig(effective);
        session.layout(ResolvedLayout.resolve(effective));

        Inventory inventory = Bukkit.createInventory(null, effective.rows() * Layout.ROW_WIDTH,
                ChatColor.translateAlternateColorCodes('&', effective.title()));
        session.inventory(inventory);
        return session;
    }

    // seeds initialState tokens from the open arguments; absent keys stay unset (null reads)
    private static void bindInitialState(View view, ViewArguments arguments, StateStore store) {
        for (StateToken token : view.tokenTable().tokens()) {
            if (token instanceof InitialStateImpl) {
                InitialStateImpl<?> initial = (InitialStateImpl<?>) token;
                Object value = arguments.get(initial.key(), initial.type());
                if (value != null) {
                    store.set(initial.tokenId(), value);
                }
            }
        }
    }

    // onOpen is protected on the public View type; the phase dispatches reflectively
    private static void invokeOnOpen(View view, OpenContext context) {
        try {
            Method method = View.class.getDeclaredMethod("onOpen", OpenContext.class);
            method.setAccessible(true);
            method.invoke(view, context);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onOpen failed for view " + view.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onOpen for view " + view.getClass().getName(), ex);
        }
    }
}
```

**internal/engine/phase/FirstRenderPhase.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.RenderContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.schedule.ViewUpdateTask;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Second half of the open pipeline: {@code onFirstRender}, component materialization,
 * initial paint, session registration, container show, activation and update-task start.
 * A throwing {@code onFirstRender} aborts the open with an {@code OPEN_FAILED} close hook
 * and registers nothing (spec §9). Constructed and invoked only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class FirstRenderPhase {

    private static final Logger LOGGER = Logger.getLogger(FirstRenderPhase.class.getName());

    private final ViewEngine engine;
    private final SessionRegistry sessions;
    private final SlotPainter painter;

    /**
     * Creates the phase.
     *
     * @param engine   the owning engine
     * @param sessions the per-player session registry
     * @param painter  the slot painter used for the initial paint
     */
    public FirstRenderPhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions,
                            @NotNull SlotPainter painter) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.painter = Objects.requireNonNull(painter, "painter");
    }

    /**
     * Renders and shows a freshly opened session produced by {@link OpenPhase#openSession}.
     *
     * @param session the session to render and activate
     */
    public void firstRender(@NotNull ViewSession session) {
        View view = session.registered().instance();
        RenderContextImpl renderContext = new RenderContextImpl(session, engine);
        try {
            invokeOnFirstRender(view, renderContext);
            renderContext.materializeAll();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onFirstRender failed for view " + view.getClass().getName()
                    + "; aborting the open", ex);
            // teardown through the close phase; the session was never registered
            engine.close(session, CloseReason.OPEN_FAILED);
            return;
        }

        paintAll(session, renderContext);

        sessions.register(session);
        session.player().openInventory(session.inventory());
        session.status(ViewSession.Status.ACTIVE);
        startScheduledUpdates(session);
    }

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
    }

    private void startScheduledUpdates(ViewSession session) {
        long interval = session.effectiveConfig().updateIntervalTicks();
        if (interval <= 0) {
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(engine.plugin(),
                new ViewUpdateTask(engine, session), interval, interval);
        session.updateTask(task);
    }

    // onFirstRender is protected on the public View type; the phase dispatches reflectively
    private static void invokeOnFirstRender(View view, RenderContext context) {
        try {
            Method method = View.class.getDeclaredMethod("onFirstRender", RenderContext.class);
            method.setAccessible(true);
            method.invoke(view, context);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onFirstRender failed for view " + view.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onFirstRender for view " + view.getClass().getName(), ex);
        }
    }
}
```

**internal/engine/phase/ClosePhase.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.CloseContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Session teardown: idempotent on closed sessions, cancels the update task, marks the
 * session closed, runs {@code onClose} (a throw is logged, teardown always completes),
 * unregisters the session and drops pending deferred operations. Constructed and invoked
 * only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class ClosePhase {

    private static final Logger LOGGER = Logger.getLogger(ClosePhase.class.getName());

    private final ViewEngine engine;
    private final SessionRegistry sessions;

    /**
     * Creates the phase.
     *
     * @param engine   the owning engine
     * @param sessions the per-player session registry
     */
    public ClosePhase(@NotNull ViewEngine engine, @NotNull SessionRegistry sessions) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Closes a session with the given reason; closing an already closed session is a no-op.
     *
     * @param session the session to close
     * @param reason  the close reason exposed to {@code onClose}
     */
    public void close(@NotNull ViewSession session, @NotNull CloseReason reason) {
        if (session.status() == ViewSession.Status.CLOSED) {
            return;
        }
        BukkitTask updateTask = session.updateTask();
        if (updateTask != null) {
            updateTask.cancel();
            session.updateTask(null);
        }
        session.status(ViewSession.Status.CLOSED);

        View view = session.registered().instance();
        CloseContextImpl closeContext = new CloseContextImpl(session, engine, reason);
        try {
            invokeOnClose(view, closeContext);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "onClose failed for view " + view.getClass().getName()
                    + "; teardown continues", ex);
        }

        sessions.unregister(session);
        session.deferredOps().clear();
    }

    // onClose is protected on the public View type; the phase dispatches reflectively
    private static void invokeOnClose(View view, CloseContext context) {
        try {
            Method method = View.class.getDeclaredMethod("onClose", CloseContext.class);
            method.setAccessible(true);
            method.invoke(view, context);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onClose failed for view " + view.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onClose for view " + view.getClass().getName(), ex);
        }
    }
}
```

**internal/engine/phase/UpdatePhase.java** (skeleton — Task 16 replaces the `update` body)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;
import java.util.Set;

/**
 * Update pass: {@code onUpdate} dispatch plus full or dirty-scoped component repaint.
 * Skeleton in this task; the body lands in plan task 16. Constructed and invoked only by
 * {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class UpdatePhase {

    // stored for the task 16 implementation
    private final ViewEngine engine;

    /**
     * Creates the phase.
     *
     * @param engine the owning engine
     */
    public UpdatePhase(@NotNull ViewEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Runs one update pass over a session.
     *
     * @param session     the session to update
     * @param trigger     the cause of the update
     * @param dirtyOrNull the dirty token ids driving a state-change pass, or {@code null}
     *                    for a full pass
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger,
                       @Nullable Set<Integer> dirtyOrNull) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }
}
```

**internal/engine/phase/ClickRoutingPhase.java** (skeleton — Task 14 replaces the `route` body)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.Objects;

/**
 * Click policy and routing (spec §6). Skeleton in this task; the body lands in plan
 * task 14. Constructed and invoked only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class ClickRoutingPhase {

    // stored for the task 14 implementation
    private final ViewEngine engine;

    /**
     * Creates the phase.
     *
     * @param engine the owning engine
     */
    public ClickRoutingPhase(@NotNull ViewEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Routes a Bukkit click event into the session per the click policy.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void route(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        throw new UnsupportedOperationException("implemented in Task 14");
    }
}
```

**internal/engine/ViewEngine.java** (complete replacement of the Task 11 skeleton; `click`/`drag`/`defer` remain skeletons for Task 14, `flushDirty`/`flushShared` for Task 16; Task 13 replaces the `bukkitClose` body with the identical code — everything else stays exactly as written here)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

import java.util.Objects;

/**
 * Orchestrator of the view lifecycle: composes the fixed-order phase handlers and is the
 * sole mutator of sessions. All entry points assert the main thread.
 *
 * <p>Click and drag routing plus end-of-tick deferral are completed in plan task 14;
 * dirty-state and shared-state flushing in plan task 16.
 */
@Component
@ApiStatus.Internal
public final class ViewEngine {

    private final Plugin plugin;
    private final ViewRegistry views;
    // used by the flush implementations added in plan task 16
    private final SessionRegistry sessions;
    private final TitleUpdater titleUpdater;

    // fixed-order phase handlers, engine-owned
    final OpenPhase openPhase;
    final FirstRenderPhase firstRenderPhase;
    final UpdatePhase updatePhase;
    final ClickRoutingPhase clickRoutingPhase;
    final ClosePhase closePhase;

    /**
     * Creates the engine and its phase handlers.
     *
     * @param plugin       the plugin owning the inventory-api runtime
     * @param views        the view registry
     * @param sessions     the per-player session registry
     * @param painter      the slot painter used by the rendering phases
     * @param titleUpdater the in-place title update strategy
     */
    public ViewEngine(Plugin plugin, ViewRegistry views, SessionRegistry sessions,
                      SlotPainter painter, TitleUpdater titleUpdater) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.views = Objects.requireNonNull(views, "views");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.titleUpdater = Objects.requireNonNull(titleUpdater, "titleUpdater");
        Objects.requireNonNull(painter, "painter");
        this.closePhase = new ClosePhase(this, sessions);
        this.openPhase = new OpenPhase(this, sessions);
        this.firstRenderPhase = new FirstRenderPhase(this, sessions, painter);
        this.updatePhase = new UpdatePhase(this);
        this.clickRoutingPhase = new ClickRoutingPhase(this);
    }

    /**
     * Opens a registered view for a player, replacing any previous session at the commit
     * point (spec §7).
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
        assertMainThread("ViewEngine.open");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(arguments, "arguments");
        RegisteredView registered = views.find(viewType).orElseThrow(() ->
                new UnknownViewException("view " + viewType.getName() + " is not registered"));
        ViewSession session = openPhase.openSession(player, registered, arguments);
        if (session == null) {
            // cancelled with zero side effects; the previous session stays untouched
            return;
        }
        firstRenderPhase.firstRender(session);
    }

    /**
     * Closes a session with the given reason; idempotent on already closed sessions.
     *
     * @param session the session to close
     * @param reason  the close reason
     */
    public void close(@NotNull ViewSession session, @NotNull CloseReason reason) {
        assertMainThread("ViewEngine.close");
        closePhase.close(session, reason);
    }

    /**
     * Runs an update pass on a session.
     *
     * @param session the session to update
     * @param trigger the cause of the update
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger) {
        assertMainThread("ViewEngine.update");
        updatePhase.update(session, trigger, null);
    }

    /**
     * Routes a Bukkit click event into the session per the click policy.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void click(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        throw new UnsupportedOperationException("implemented in Task 14");
    }

    /**
     * Applies the drag policy of a session to a Bukkit drag event.
     *
     * @param session the affected session
     * @param event   the Bukkit event
     */
    public void drag(@NotNull ViewSession session, @NotNull InventoryDragEvent event) {
        throw new UnsupportedOperationException("implemented in Task 14");
    }

    /**
     * Handles a Bukkit close event for a session, guarded by container identity: a close event
     * for a previous container (fired synchronously while opening a new view) must not tear
     * down the session of the view that is being opened.
     *
     * @param session the player's session
     * @param event   the Bukkit event
     */
    public void bukkitClose(@NotNull ViewSession session, @NotNull InventoryCloseEvent event) {
        assertMainThread("ViewEngine.bukkitClose");
        if (event.getInventory() != session.inventory()) {
            return;
        }
        close(session, CloseReason.PLAYER);
    }

    /**
     * Flushes dirty state tokens of a session (STATE_CHANGE re-render, cascade cap 8).
     *
     * @param session the session to flush
     */
    public void flushDirty(@NotNull ViewSession session) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }

    /**
     * Flushes shared-state watchers of every open session of a view.
     *
     * @param owner the view singleton whose sessions should flush
     */
    public void flushShared(@NotNull View owner) {
        throw new UnsupportedOperationException("implemented in Task 16");
    }

    /**
     * Defers an operation to the end of the current tick.
     *
     * @param session the session the operation belongs to
     * @param op      the operation to run at end of tick
     */
    public void defer(@NotNull ViewSession session, @NotNull Runnable op) {
        throw new UnsupportedOperationException("implemented in Task 14");
    }

    /**
     * Returns whether a click event is currently being dispatched (drives operation
     * deferral).
     *
     * @return always {@code false} until click dispatch lands in plan task 14
     */
    public boolean isInClickDispatch() {
        return false;
    }

    /**
     * Returns the plugin owning the inventory-api runtime.
     *
     * @return the owning plugin
     */
    public @NotNull Plugin plugin() {
        return plugin;
    }

    /**
     * Returns the in-place title update strategy.
     *
     * @return the title updater
     */
    public @NotNull TitleUpdater titleUpdater() {
        return titleUpdater;
    }

    /**
     * Throws when not on the main server thread.
     *
     * @param operation the operation name used in the error message
     * @throws IllegalStateException when called off the main server thread
     */
    public static void assertMainThread(@NotNull String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(operation + " must be called on the main server thread");
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewEngineOpenCloseTest"
Expected: PASS

Also run the full module suite to confirm the new `@Component` beans and engine wiring do not regress any existing 2.x or earlier v3 test:

Run: mvnw.cmd -pl modules/inventory-api/api -am test
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/schedule/ modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngineOpenCloseTest.java
git commit -m "feat(inventory-api): implement open, first-render, and close lifecycle phases"
```

---

### Task 13: Open-ordering edge cases

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngineOpenOrderingTest.java

Note: cases (a)-(d) pin behavior already implemented by Task 12's `OpenPhase` (no `OpenPhase` change expected — if any of (a)-(d) fail, fix `OpenPhase` rather than the tests); case (e) drives the new `ViewEngine.bukkitClose` container-identity guard, which mirrors `CustomInventoryListener.onInventoryClose` lines 68-72.

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.OpenContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewEngineOpenOrderingTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;
    private List<String> log;
    private ViewB viewB;

    static final class ViewA extends View {
        private final List<String> log;

        ViewA(List<String> log) {
            this.log = log;
        }

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("A").rows(1);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            log.add("A.onClose(" + context.reason() + ")");
        }
    }

    static final class ViewB extends View {
        private final List<String> log;
        boolean cancelNext;
        boolean throwNext;

        ViewB(List<String> log) {
            this.log = log;
        }

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("B").rows(1);
        }

        @Override
        protected void onOpen(@NotNull OpenContext context) {
            log.add("B.onOpen");
            if (throwNext) {
                throw new IllegalStateException("boom");
            }
            if (cancelNext) {
                context.cancelOpen();
            }
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            log.add("B.onFirstRender");
        }
    }

    static final class InitialStateView extends View {
        @SuppressWarnings("unused")
        private final MutableState<Integer> count = initialState("count", Integer.class);

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("C").rows(1);
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        log = new ArrayList<>();
        viewB = new ViewB(log);
        views.register(new ViewA(log));
        views.register(viewB);
        views.register(new InitialStateView());
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession openViewA() {
        engine.open(player, ViewA.class, ViewArguments.empty());
        return sessions.find(player.getUniqueId()).orElseThrow();
    }

    private InventoryCloseEvent closeEventFor(Inventory inventory) {
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inventory);
        return new InventoryCloseEvent(view);
    }

    @Test
    void cancelOpen_leavesPreviousSessionActive() {
        ViewSession previous = openViewA();
        viewB.cancelNext = true;

        engine.open(player, ViewB.class, ViewArguments.empty());

        assertEquals(ViewSession.Status.ACTIVE, previous.status());
        assertSame(previous, sessions.find(player.getUniqueId()).orElseThrow());
        assertSame(previous.inventory(), player.getOpenInventory().getTopInventory());
        assertEquals(Collections.singletonList("B.onOpen"), log,
                "cancelled open must not close the previous view nor reach onFirstRender");
    }

    @Test
    void onOpenThrows_leavesPreviousSessionActive() {
        ViewSession previous = openViewA();
        viewB.throwNext = true;

        engine.open(player, ViewB.class, ViewArguments.empty());

        assertEquals(ViewSession.Status.ACTIVE, previous.status());
        assertSame(previous, sessions.find(player.getUniqueId()).orElseThrow());
        assertSame(previous.inventory(), player.getOpenInventory().getTopInventory());
        assertEquals(Collections.singletonList("B.onOpen"), log);
    }

    @Test
    void committedOpen_closesPreviousWithReplaced_beforeNewContainerShown() {
        openViewA();

        engine.open(player, ViewB.class, ViewArguments.empty());

        assertEquals(Arrays.asList("B.onOpen", "A.onClose(REPLACED)", "B.onFirstRender"), log);
        ViewSession current = sessions.find(player.getUniqueId()).orElseThrow();
        assertSame(viewB, current.registered().instance());
        assertEquals(ViewSession.Status.ACTIVE, current.status());
        assertSame(current.inventory(), player.getOpenInventory().getTopInventory());
    }

    @Test
    void initialState_wrongTypedArgument_throwsAtOpenSite_previousIntact() {
        ViewSession previous = openViewA();

        assertThrows(IllegalArgumentException.class, () -> engine.open(player,
                InitialStateView.class, ViewArguments.of("count", "not-an-int")));

        assertEquals(ViewSession.Status.ACTIVE, previous.status());
        assertSame(previous, sessions.find(player.getUniqueId()).orElseThrow());
        assertTrue(log.isEmpty(), "the previous view must not observe a failed open");
    }

    @Test
    void staleCloseEvent_forDifferentContainer_isIgnored() {
        ViewSession session = openViewA();
        Inventory unrelated = Bukkit.createInventory(null, 9);

        engine.bukkitClose(session, closeEventFor(unrelated));

        assertEquals(ViewSession.Status.ACTIVE, session.status());
        assertSame(session, sessions.find(player.getUniqueId()).orElseThrow());
        assertTrue(log.isEmpty());
    }

    @Test
    void closeEvent_forOwnContainer_closesWithPlayerReason() {
        ViewSession session = openViewA();

        engine.bukkitClose(session, closeEventFor(session.inventory()));

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(Collections.singletonList("A.onClose(PLAYER)"), log);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewEngineOpenOrderingTest"
Expected: FAIL (`staleCloseEvent_forDifferentContainer_isIgnored` and `closeEvent_forOwnContainer_closesWithPlayerReason` throw `UnsupportedOperationException: implemented in Task 13`; the four ordering cases pass as regression pins of Task 12's `OpenPhase`)

- [ ] **Step 3: Write minimal implementation**

In `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java`, replace the `bukkitClose` body (everything else stays exactly as Task 12 left it):

```java
    /**
     * Handles a Bukkit close event for a session, guarded by container identity: a close event
     * for a previous container (fired synchronously while opening a new view) must not tear
     * down the session of the view that is being opened.
     *
     * @param session the player's session
     * @param event   the Bukkit event
     */
    public void bukkitClose(@NotNull ViewSession session, @NotNull InventoryCloseEvent event) {
        assertMainThread("ViewEngine.bukkitClose");
        if (event.getInventory() != session.inventory()) {
            return;
        }
        close(session, CloseReason.PLAYER);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewEngineOpenOrderingTest"
Expected: PASS

Also re-run the previous engine suite to confirm no regression:

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewEngineOpenCloseTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngineOpenOrderingTest.java
git commit -m "feat(inventory-api): guard bukkit close events by container identity"
```

---

ap<>();

    private final ViewDiscoveryService discoveryService;

    /**
     * Creates a registry without discovery support; used by tests that register views directly.
     */
    public ViewRegistry() {
        this((ViewDiscoveryService) null);
    }

    /**
     * Creates the registry with discovery support.
     *
     * @param discoveryService the discovery service used by {@link #initialize(Context)}
     */
    @Inject
    public ViewRegistry(@Nullable ViewDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * Discovers {@code @RegisterView} classes under the host plugin's base package, instantiates
     * each through the dependency manager and registers it.
     *
     * @throws Exception when context access fails
     */
    public void initialize(@NotNull Context context) throws Exception {
        ViewDiscoveryService discovery = this.discoveryService != null
                ? this.discoveryService
                : context.getBean(ViewDiscoveryService.class);
        if (discovery == null) {
            throw new IllegalStateException("ViewDiscoveryService is not available.");
        }

        String basePackage = context.getPlugin().getMainClass().getPackage().getName();
        Set<Class<? extends View>> classes = discovery.discoverFromPackage(basePackage);
        DependencyManager dependencyManager = context.getDependencyManager();

        int registered = 0;
        for (Class<? extends View> viewClass : classes) {
            try {
                registerDiscoveredView(viewClass, dependencyManager);
                registered++;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to register view " + viewClass.getName(), ex);
            }
        }

        LOGGER.log(Level.INFO, "Registered {0} views.", registered);
    }

    private void registerDiscoveredView(
            Class<? extends View> viewClass,
            DependencyManager dependencyManager
    ) throws Exception {
        Constructor<?> constructor = dependencyManager.findInjectConstructor(viewClass);
        if (constructor == null) {
            throw new IllegalStateException("No injectable constructor found for view: " + viewClass.getName());
        }

        Object[] constructorArguments = dependencyManager.resolveArguments(constructor);
        constructor.setAccessible(true);
        Object rawInstance = constructor.newInstance(constructorArguments);

        BeanDefinition definition = new BeanDefinition(
                viewClass,
                viewClass,
                viewClass.getName() + "#view",
                false,
                null,
                null
        );
        View view = (View) dependencyManager.initializeBean(definition, rawInstance);
        injectSuperclassDependencies(dependencyManager, viewClass, view);

        dependencyManager.registerDependency(
                view,
                BeanUtils.getQualifier(viewClass),
                BeanUtils.getIsPrimary(viewClass)
        );
        register(view);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectSuperclassDependencies(
            DependencyManager dependencyManager,
            Class<? extends View> viewClass,
            View instance
    ) {
        for (Class type = viewClass.getSuperclass();
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            dependencyManager.injectDependencies(type, instance);
        }
    }

    /**
     * Registers a view instance directly: freezes its token table, runs {@code onInit} once and
     * validates the resulting config.
     *
     * @throws ViewConfigurationException when the built config violates the validation rules
     * @throws IllegalStateException      when the view class is already registered
     */
    public void register(@NotNull View instance) {
        Class<? extends View> type = instance.getClass();
        if (views.containsKey(type)) {
            throw new IllegalStateException("view " + type.getName() + " is already registered");
        }
        instance.tokenTable().freeze();
        ViewConfigBuilder builder = new ViewConfigBuilder();
        invokeOnInit(instance, builder);
        ViewConfig config = builder.build();
        views.put(type, new RegisteredView(type, instance, config));
    }

    /**
     * Looks up the registration of a view class.
     */
    public @NotNull Optional<RegisteredView> find(@NotNull Class<? extends View> type) {
        return Optional.ofNullable(views.get(type));
    }

    /**
     * @return all registrations, unmodifiable
     */
    public @NotNull Collection<RegisteredView> all() {
        return Collections.unmodifiableCollection(views.values());
    }

    // onInit is protected on the public View type; the registry dispatches reflectively
    private static void invokeOnInit(View instance, ViewConfigBuilder builder) {
        try {
            Method method = View.class.getDeclaredMethod("onInit", ViewConfigBuilder.class);
            method.setAccessible(true);
            method.invoke(instance, builder);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onInit failed for view " + instance.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onInit for view " + instance.getClass().getName(), ex);
        }
    }
}
```

- [ ] **Step 9: Run test to verify it passes**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewRegistryTest"
Expected: PASS (also re-run "-Dtest=ClickRoutingTest,DeferredOpsTest,UpdateFlushTest" to confirm the kept no-arg constructor still satisfies earlier tests)

- [ ] **Step 10: Commit**
```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistry.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistryTest.java
git commit -m "feat(inventory-api): register discovered views through the dependency manager"
```

### Task 14: ClickRoutingPhase + ViewListener

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/ClickRoutingPhase.java (complete replacement of the Task 12 skeleton)
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java (fill `click`/`drag`/`defer`, restore the real click-dispatch flag)
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/listener/ViewListener.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ClickRoutingTest.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/listener/ViewListenerTest.java

Scope boundaries, stated explicitly:
- `flushDirty` REMAINS the Task 16 `UnsupportedOperationException` skeleton, so no test in this task may mutate state inside a click handler; `click` only calls it behind `session.stateStore().hasDirty()`.
- `defer` is filled HERE in its simple form (TRANSITIONING + `deferredOps().add` + scheduler) — Task 15 replaces it with the closed-session guard and drain helper; do not add that guard here, `DeferredOpsTest` case (6) must still fail before Task 15.
- `ViewListener` handler names `onClick`/`onQuit` are pinned by Task 18's `ViewServiceEndToEndTest` (`listener.onClick(increment)`, `listener.onQuit(...)`, `new ViewListener(sessions, engine)`); the remaining handlers follow the same short naming (`onDrag`/`onClose`/`onPluginDisable`).
- `View.onClick` is `protected`; the phase dispatches it reflectively, same pattern as `OpenPhase.invokeOnOpen`.

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClickRoutingTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private PlayerMock player;
    private PolicyView policyView;
    private ThrowingView throwingView;

    static final class PolicyView extends View {
        int componentClicks;
        int typedRightClicks;
        int untypedClicks;
        int hiddenClicks;
        int viewClicks;
        boolean uncancelNext;
        Boolean lastPlayerInventory;
        Boolean lastPreCancelled;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Policy").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .cancelOnClick(false)
                    .onClick(ctx -> componentClicks++);
            render.slot(1, new ItemStack(Material.PAPER))
                    .onClick(ClickType.RIGHT, ctx -> typedRightClicks++)
                    .onClick(ctx -> untypedClicks++);
            render.slot(2, new ItemStack(Material.ARROW))
                    .displayIf(ctx -> false)
                    .cancelOnClick(false)
                    .onClick(ctx -> hiddenClicks++);
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
            lastPlayerInventory = context.isPlayerInventory();
            lastPreCancelled = context.isCancelled();
            if (uncancelNext) {
                context.setCancelled(false);
            }
        }
    }

    static final class PermissiveView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Permissive").rows(1).cancelOnClick(false);
        }
    }

    static final class NoDragView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("NoDrag").rows(1).cancelOnDrag(false);
        }
    }

    static final class ThrowingView extends View {
        int handlerCalls;
        int viewClicks;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Throwing").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .cancelOnClick(false)
                    .closeOnClick()
                    .onClick(ctx -> {
                        handlerCalls++;
                        throw new IllegalStateException("boom");
                    });
        }

        @Override
        protected void onClick(@NotNull SlotClickContext context) {
            viewClicks++;
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        policyView = new PolicyView();
        throwingView = new ThrowingView();
        views.register(policyView);
        views.register(throwingView);
        views.register(new PermissiveView());
        views.register(new NoDragView());
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        });
    }

    @AfterEach
    void tearDown() {
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

    private InventoryDragEvent drag(int... rawSlots) {
        Map<Integer, ItemStack> slots = new HashMap<>();
        for (int rawSlot : rawSlots) {
            slots.put(rawSlot, new ItemStack(Material.STONE));
        }
        return new InventoryDragEvent(mockView(session().inventory()), null,
                new ItemStack(Material.STONE), false, slots);
    }

    @Test
    void configCancelFalse_plainTopClickIsNotCancelled() {
        open(PermissiveView.class);
        InventoryClickEvent event = click(4);

        engine.click(session(), event);

        assertFalse(event.isCancelled());
    }

    @Test
    void componentCancelFalse_overridesConfigTrue() {
        ViewSession session = open(PolicyView.class);
        InventoryClickEvent onComponent = click(0);
        engine.click(session, onComponent);
        assertFalse(onComponent.isCancelled());
        assertEquals(1, policyView.componentClicks);

        // a component-less slot of the same view stays config-cancelled
        InventoryClickEvent plain = click(5);
        engine.click(session, plain);
        assertTrue(plain.isCancelled());
    }

    @Test
    void handlerUncancel_overturnsConfigPreCancelOnPlainTopClick() {
        ViewSession session = open(PolicyView.class);
        policyView.uncancelNext = true;
        InventoryClickEvent event = click(5);

        engine.click(session, event);

        assertEquals(Boolean.TRUE, policyView.lastPreCancelled, "the context starts at the config pre-cancel");
        assertFalse(event.isCancelled(), "the handler decision is last-writer-wins for non-floor actions");
    }

    @Test
    void floorActions_stayCancelledDespiteHandlerUncancel() {
        ViewSession session = open(PolicyView.class);
        policyView.uncancelNext = true;

        InventoryClickEvent shiftFromBottom = click(12, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);
        engine.click(session, shiftFromBottom);
        assertTrue(shiftFromBottom.isCancelled());

        InventoryClickEvent collect = click(5, ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR);
        engine.click(session, collect);
        assertTrue(collect.isCancelled());

        InventoryClickEvent hotbarSwap = click(5, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP);
        engine.click(session, hotbarSwap);
        assertTrue(hotbarSwap.isCancelled());

        InventoryClickEvent hotbarReadd = click(5, ClickType.NUMBER_KEY,
                InventoryAction.HOTBAR_MOVE_AND_READD);
        engine.click(session, hotbarReadd);
        assertTrue(hotbarReadd.isCancelled());
    }

    @Test
    void typedHandlerWinsForItsClickType_untypedHandlesOthers() {
        ViewSession session = open(PolicyView.class);

        engine.click(session, click(1, ClickType.RIGHT, InventoryAction.PICKUP_HALF));
        assertEquals(1, policyView.typedRightClicks);
        assertEquals(0, policyView.untypedClicks);

        engine.click(session, click(1, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        assertEquals(1, policyView.typedRightClicks);
        assertEquals(1, policyView.untypedClicks);
    }

    @Test
    void hiddenComponent_receivesNoClicks_cancelledPerConfig() {
        ViewSession session = open(PolicyView.class);
        InventoryClickEvent event = click(2);

        engine.click(session, event);

        assertEquals(0, policyView.hiddenClicks, "hidden components get no clicks");
        assertTrue(event.isCancelled(), "config cancel wins; the hidden component's override is ignored");
        assertEquals(1, policyView.viewClicks, "the slot degrades to a component-less click");
    }

    @Test
    void bottomClick_reachesViewOnClickPreCancelledAsPlayerInventory() {
        ViewSession session = open(PolicyView.class);
        InventoryClickEvent event = click(20);

        engine.click(session, event);

        assertEquals(1, policyView.viewClicks);
        assertEquals(0, policyView.componentClicks, "bottom clicks never reach component handlers");
        assertEquals(Boolean.TRUE, policyView.lastPlayerInventory);
        assertEquals(Boolean.TRUE, policyView.lastPreCancelled);
        assertTrue(event.isCancelled());
    }

    @Test
    void throwingHandler_forceCancelsSkipsRestAndKeepsSessionUsable() {
        ViewSession session = open(ThrowingView.class);
        InventoryClickEvent first = click(0);

        engine.click(session, first);

        assertEquals(1, throwingView.handlerCalls);
        assertTrue(first.isCancelled(), "a throwing handler force-cancels despite cancelOnClick(false)");
        assertEquals(0, throwingView.viewClicks, "view-level onClick is skipped after a handler throw");
        assertTrue(session.deferredOps().isEmpty(), "the closeOnClick post-action must not be deferred");
        assertEquals(ViewSession.Status.ACTIVE, session.status());

        InventoryClickEvent second = click(0);
        engine.click(session, second);
        assertEquals(2, throwingView.handlerCalls, "the session stays clickable after a handler throw");
    }

    @Test
    void clickWhileTransitioning_isCancelledWithoutDispatch() {
        ViewSession session = open(PolicyView.class);
        session.status(ViewSession.Status.TRANSITIONING);
        InventoryClickEvent event = click(0);

        engine.click(session, event);

        assertTrue(event.isCancelled());
        assertEquals(0, policyView.componentClicks);
        assertEquals(0, policyView.viewClicks);
    }

    @Test
    void drag_touchingTop_isCancelledByDefault() {
        ViewSession session = open(PolicyView.class);
        InventoryDragEvent event = drag(2, 20);

        engine.drag(session, event);

        assertTrue(event.isCancelled());
    }

    @Test
    void drag_bottomOnly_isUntouched() {
        ViewSession session = open(PolicyView.class);
        InventoryDragEvent event = drag(9, 20);

        engine.drag(session, event);

        assertFalse(event.isCancelled());
    }

    @Test
    void drag_topUntouchedWhenCancelOnDragFalse() {
        ViewSession session = open(NoDragView.class);
        InventoryDragEvent event = drag(2);

        engine.drag(session, event);

        assertFalse(event.isCancelled());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ClickRoutingTest"
Expected: FAIL (every click test throws `UnsupportedOperationException: implemented in Task 14` from `ViewEngine.click`, every drag test from `ViewEngine.drag`)

- [ ] **Step 3: Write minimal implementation**

**internal/engine/phase/ClickRoutingPhase.java** (complete replacement of the Task 12 skeleton)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.SlotClickContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.SlotClickContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Click policy and routing (spec §6): pre-cancel from config and per-component override,
 * handler dispatch with last-writer-wins cancellation, the safety-floor actions
 * force-cancelled after handlers, and end-of-tick post-actions. Constructed and invoked
 * only by {@link ViewEngine}.
 */
@ApiStatus.Internal
public final class ClickRoutingPhase {

    private static final Logger LOGGER = Logger.getLogger(ClickRoutingPhase.class.getName());

    private final ViewEngine engine;

    /**
     * Creates the phase.
     *
     * @param engine the owning engine
     */
    public ClickRoutingPhase(@NotNull ViewEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Routes a Bukkit click event into the session per the click policy.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void route(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        // clicks on a non-ACTIVE session (OPENING/TRANSITIONING/CLOSED) are swallowed
        if (session.status() != ViewSession.Status.ACTIVE) {
            event.setCancelled(true);
            return;
        }
        Inventory inventory = session.inventory();
        if (inventory == null) {
            // defensive: an ACTIVE session always has its container; swallow if not
            event.setCancelled(true);
            return;
        }

        int topSize = inventory.getSize();
        boolean bottom = event.getRawSlot() >= topSize;
        InventoryAction action = event.getAction();
        // safety floor (§6): these item movements are force-cancelled after handlers,
        // regardless of any setCancelled(false) decision
        boolean forced = (bottom && action == InventoryAction.MOVE_TO_OTHER_INVENTORY)
                || action == InventoryAction.COLLECT_TO_CURSOR
                || ((action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD)
                && event.getRawSlot() < topSize);

        ComponentInstance component = bottom ? null
                : session.components().componentAt(event.getRawSlot());
        SlotClickContextImpl ctx = new SlotClickContextImpl(session, engine, event, bottom,
                preCancel(session, component, bottom) || forced);
        if (component != null && !component.isVisible(ctx)) {
            // hidden components get no clicks; the slot degrades to component-less and
            // the pre-cancel decision falls back to the config default
            component = null;
            ctx = new SlotClickContextImpl(session, engine, event, bottom,
                    preCancel(session, null, bottom) || forced);
        }

        dispatch(session, component, ctx, event);

        event.setCancelled(forced || ctx.isCancelled());
    }

    // pre-cancel policy: bottom always pre-cancelled; component override beats config
    private static boolean preCancel(ViewSession session, @Nullable ComponentInstance component,
                                     boolean bottom) {
        if (bottom) {
            return true;
        }
        if (component != null && component.cancelOnClick() != null) {
            return component.cancelOnClick();
        }
        return session.effectiveConfig().cancelOnClick();
    }

    // component handler, then view-level onClick, then deferred post-actions; a throw
    // anywhere force-cancels and skips everything remaining (§6, §9)
    private void dispatch(ViewSession session, @Nullable ComponentInstance component,
                          SlotClickContextImpl ctx, InventoryClickEvent event) {
        View view = session.registered().instance();
        try {
            if (component != null) {
                Consumer<SlotClickContext> handler = component.handlerFor(event.getClick());
                if (handler != null) {
                    handler.accept(ctx);
                }
            }
            invokeOnClick(view, ctx);
            if (component != null) {
                queuePostActions(session, component);
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "click handler failed for view " + view.getClass().getName()
                    + " at raw slot " + event.getRawSlot(), ex);
            ctx.setCancelled(true);
        }
    }

    private void queuePostActions(ViewSession session, ComponentInstance component) {
        if (component.closeOnClick()) {
            engine.defer(session, () -> engine.close(session, CloseReason.API));
        }
        Class<? extends View> target = component.openOnClickTarget();
        if (target != null) {
            engine.defer(session, () -> engine.open(session.player(), target,
                    component.openOnClickArguments()));
        }
    }

    // onClick is protected on the public View type; the phase dispatches reflectively
    private static void invokeOnClick(View view, SlotClickContext context) {
        try {
            Method method = View.class.getDeclaredMethod("onClick", SlotClickContext.class);
            method.setAccessible(true);
            method.invoke(view, context);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onClick failed for view " + view.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onClick for view " + view.getClass().getName(), ex);
        }
    }
}
```

**internal/engine/ViewEngine.java** — add `import org.bukkit.inventory.Inventory;`, add the `inClickDispatch` field next to the existing fields, replace the `click`/`drag`/`defer`/`isInClickDispatch` members and add the `clickDispatch` setter (everything else stays exactly as Task 13 left it; `flushDirty` keeps throwing until Task 16, Task 15 replaces `defer` with the guarded drain version):

```java
    private boolean inClickDispatch;

    /**
     * Routes a Bukkit click event into the session per the click policy. Context
     * {@code close()}/{@code openView()} calls and component post-actions made while this
     * method runs are deferred to end of tick; dirty state written by handlers is flushed
     * after dispatch completes.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void click(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        assertMainThread("ViewEngine.click");
        clickDispatch(true);
        try {
            clickRoutingPhase.route(session, event);
        } finally {
            clickDispatch(false);
        }
        // coalesced reactive flush; guarded so handler-less clicks never hit the
        // not-yet-implemented flush (plan task 16)
        if (session.stateStore().hasDirty()) {
            flushDirty(session);
        }
    }

    /**
     * Applies the drag policy of a session: when {@code cancelOnDrag} is enabled, any drag
     * touching the top container is cancelled (mirrors the 2.x listener behavior).
     *
     * @param session the affected session
     * @param event   the Bukkit event
     */
    public void drag(@NotNull ViewSession session, @NotNull InventoryDragEvent event) {
        assertMainThread("ViewEngine.drag");
        if (!session.effectiveConfig().cancelOnDrag()) {
            return;
        }
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }
        int topSize = inventory.getSize();
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Defers an operation to the end of the current tick. The session leaves ACTIVE
     * immediately (TRANSITIONING) so further clicks are swallowed until the operation runs.
     *
     * @param session the session the operation belongs to
     * @param op      the operation to run at end of tick
     */
    public void defer(@NotNull ViewSession session, @NotNull Runnable op) {
        assertMainThread("ViewEngine.defer");
        session.status(ViewSession.Status.TRANSITIONING);
        session.deferredOps().add(op);
        Bukkit.getScheduler().runTask(plugin, op);
    }

    /**
     * Returns whether a click event is currently being dispatched (drives operation
     * deferral).
     *
     * @return {@code true} while a click is being dispatched
     */
    public boolean isInClickDispatch() {
        return inClickDispatch;
    }

    /**
     * Marks the engine as inside or outside click dispatch; toggled by {@link #click}
     * around routing.
     *
     * @param active {@code true} while a click is being dispatched
     */
    @ApiStatus.Internal
    public void clickDispatch(boolean active) {
        this.inClickDispatch = active;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ClickRoutingTest"
Expected: PASS

Also re-run the neighboring suites to confirm the restored dispatch flag and filled `defer` do not regress contexts or lifecycle:

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ContextPhaseValidityTest,ViewEngineOpenCloseTest,ViewEngineOpenOrderingTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/ClickRoutingPhase.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ClickRoutingTest.java
git commit -m "feat(inventory-api): implement click routing policy and drag protection"
```

- [ ] **Step 6: Write the failing listener test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
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
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewListenerTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private ViewListener listener;
    private PlayerMock player;
    private PlayerMock second;
    private ListenerView listenerView;

    static final class ListenerView extends View {
        int clicks;
        final List<CloseReason> closeReasons = new ArrayList<>();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Listener").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .onClick(ctx -> clicks++);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            closeReasons.add(context.reason());
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        second = server.addPlayer("second");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        listenerView = new ListenerView();
        views.register(listenerView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        });
        listener = new ViewListener(sessions, engine);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession sessionOf(PlayerMock who) {
        return sessions.find(who.getUniqueId()).orElseThrow(IllegalStateException::new);
    }

    private InventoryView mockView(PlayerMock who, Inventory top) {
        InventoryView invView = mock(InventoryView.class);
        when(invView.getTopInventory()).thenReturn(top);
        when(invView.getBottomInventory()).thenReturn(who.getInventory());
        when(invView.getPlayer()).thenReturn(who);
        when(invView.convertSlot(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        return invView;
    }

    private InventoryClickEvent clickFor(PlayerMock who, Inventory top, int rawSlot) {
        return new InventoryClickEvent(mockView(who, top), InventoryType.SlotType.CONTAINER,
                rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private InventoryDragEvent dragFor(PlayerMock who, Inventory top, int rawSlot) {
        Map<Integer, ItemStack> slots = new HashMap<>();
        slots.put(rawSlot, new ItemStack(Material.STONE));
        return new InventoryDragEvent(mockView(who, top), null,
                new ItemStack(Material.STONE), false, slots);
    }

    private InventoryCloseEvent closeEventFor(PlayerMock who, Inventory top) {
        InventoryView invView = mock(InventoryView.class);
        when(invView.getPlayer()).thenReturn(who);
        when(invView.getTopInventory()).thenReturn(top);
        return new InventoryCloseEvent(invView);
    }

    @Test
    void click_withSession_delegatesToEngine() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        InventoryClickEvent event = clickFor(player, sessionOf(player).inventory(), 0);

        listener.onClick(event);

        assertEquals(1, listenerView.clicks);
        assertTrue(event.isCancelled());
    }

    @Test
    void click_withoutSession_isIgnored() {
        InventoryClickEvent event = clickFor(player, Bukkit.createInventory(null, 9), 0);

        listener.onClick(event);

        assertEquals(0, listenerView.clicks);
        assertFalse(event.isCancelled());
    }

    @Test
    void drag_withSession_appliesDragPolicy() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        InventoryDragEvent event = dragFor(player, sessionOf(player).inventory(), 0);

        listener.onDrag(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void drag_withoutSession_isIgnored() {
        InventoryDragEvent event = dragFor(player, Bukkit.createInventory(null, 9), 0);

        listener.onDrag(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void close_forOwnContainer_closesWithPlayerReason() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);

        listener.onClose(closeEventFor(player, session.inventory()));

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(Arrays.asList(CloseReason.PLAYER), listenerView.closeReasons);
    }

    @Test
    void close_forDifferentPlayersInventory_doesNothing() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);

        // the close event belongs to another player without a session, over the same container
        listener.onClose(closeEventFor(second, session.inventory()));

        assertEquals(ViewSession.Status.ACTIVE, session.status());
        assertTrue(listenerView.closeReasons.isEmpty());
    }

    @Test
    void quit_withSession_closesWithDisconnect() {
        engine.open(player, ListenerView.class, ViewArguments.empty());

        listener.onQuit(new PlayerQuitEvent(player, "bye"));

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(Arrays.asList(CloseReason.DISCONNECT), listenerView.closeReasons);
    }

    @Test
    void quit_withoutSession_isIgnored() {
        assertDoesNotThrow(() -> listener.onQuit(new PlayerQuitEvent(player, "bye")));

        assertTrue(listenerView.closeReasons.isEmpty());
    }

    @Test
    void pluginDisable_closesEverySessionOfTheEnginePlugin() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        engine.open(second, ListenerView.class, ViewArguments.empty());

        listener.onPluginDisable(new PluginDisableEvent(engine.plugin()));

        assertTrue(sessions.all().isEmpty());
        assertEquals(Arrays.asList(CloseReason.PLUGIN_DISABLE, CloseReason.PLUGIN_DISABLE),
                listenerView.closeReasons);
    }

    @Test
    void pluginDisable_ofAnotherPlugin_isIgnored() {
        engine.open(player, ListenerView.class, ViewArguments.empty());
        Plugin other = MockBukkit.createMockPlugin("other");

        listener.onPluginDisable(new PluginDisableEvent(other));

        assertEquals(1, sessions.all().size());
        assertTrue(listenerView.closeReasons.isEmpty());
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewListenerTest"
Expected: FAIL (compilation error: class ViewListener does not exist)

- [ ] **Step 8: Write minimal implementation**

**internal/listener/ViewListener.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Single Bukkit listener bridge of the v3 view engine: resolves the clicking, dragging,
 * closing, quitting or disabling player's session in the {@link SessionRegistry} and
 * delegates to the matching {@link ViewEngine} entry point. Events of players without a
 * session are ignored.
 *
 * <p>Auto-registered with Bukkit by spigot-boot's {@code BukkitListenerAutoRegistrar} when
 * the context becomes ready — no manual {@code registerEvents} call required.
 */
@Component
@ApiStatus.Internal
public final class ViewListener implements Listener {

    private final SessionRegistry sessions;
    private final ViewEngine engine;

    /**
     * Creates the listener.
     *
     * @param sessions the per-player session registry
     * @param engine   the engine receiving the bridged events
     */
    public ViewListener(@NotNull SessionRegistry sessions, @NotNull ViewEngine engine) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /**
     * Routes inventory clicks of players with an open view session into the engine.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        sessions.find(event.getWhoClicked().getUniqueId())
                .ifPresent(session -> engine.click(session, event));
    }

    /**
     * Applies the session drag policy to drags of players with an open view session.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        sessions.find(event.getWhoClicked().getUniqueId())
                .ifPresent(session -> engine.drag(session, event));
    }

    /**
     * Bridges container closes into the engine; the engine guards by container identity.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        sessions.find(event.getPlayer().getUniqueId())
                .ifPresent(session -> engine.bukkitClose(session, event));
    }

    /**
     * Closes the quitting player's session with the DISCONNECT reason.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.find(event.getPlayer().getUniqueId())
                .ifPresent(session -> engine.close(session, CloseReason.DISCONNECT));
    }

    /**
     * Closes every open session when the plugin owning the inventory-api runtime disables.
     *
     * @param event the Bukkit event
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != engine.plugin()) {
            return;
        }
        // closing unregisters sessions while iterating; copy the collection first
        for (ViewSession session : new ArrayList<>(sessions.all())) {
            engine.close(session, CloseReason.PLUGIN_DISABLE);
        }
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewListenerTest"
Expected: PASS

Also run the full module suite to confirm the new `@Component` listener bean and the click wiring do not regress any existing 2.x or earlier v3 test:

Run: mvnw.cmd -pl modules/inventory-api/api -am test
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/listener/ViewListener.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/listener/ViewListenerTest.java
git commit -m "feat(inventory-api): bridge bukkit events into the view engine"
```

---

### Task 15: Deferred operations

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/DeferredOpsTest.java

Note: cases (1)-(5) are end-to-end integration pins of behavior owned by Task 11 (contexts defer `close()`/`openView()` while `engine.isInClickDispatch()`; `CloseContextImpl.openView` logs SEVERE and drops), Task 12 (`defer` = TRANSITIONING + `deferredOps().add` + scheduler, clicks on non-ACTIVE sessions swallowed) and Task 14 (`closeOnClick`/`openOnClick` post-actions go through `engine.defer`) — if one of those fails, fix the owning phase/context per the pinned rules, not the test. Case (6) drives the one glue gap implemented HERE: a deferred op must no-op when its session was closed before the tick ran, and queuing several ops in one click must stay safe after the first op closes the session.

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
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
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.CloseContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeferredOpsTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private PlayerMock player;
    private MainView mainView;
    private OtherView otherView;
    private CloseNavView closeNavView;

    static final class MainView extends View {
        int clickCount;
        int closeCount;
        CloseReason lastCloseReason;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Main").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0, new ItemStack(Material.STONE))
                    .onClick(ctx -> {
                        clickCount++;
                        ctx.close();
                    });
            render.slot(1, new ItemStack(Material.PAPER))
                    .onClick(ctx -> {
                        clickCount++;
                        ctx.openView(OtherView.class);
                    });
            render.slot(2, new ItemStack(Material.ARROW))
                    .closeOnClick();
            render.slot(3, new ItemStack(Material.GOLD_INGOT))
                    .onClick(ctx -> {
                        clickCount++;
                        // two deferred ops queued by a single click
                        ctx.close();
                        ctx.close();
                    });
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            closeCount++;
            lastCloseReason = context.reason();
        }
    }

    static final class OtherView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Other").rows(1);
        }
    }

    static final class CloseNavView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("CloseNav").rows(1);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            // forbidden navigation: the engine must log SEVERE and drop it
            context.openView(OtherView.class);
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
        Plugin plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        ViewRegistry views = new ViewRegistry();
        mainView = new MainView();
        otherView = new OtherView();
        closeNavView = new CloseNavView();
        views.register(mainView);
        views.register(otherView);
        views.register(closeNavView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
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

    @Test
    void contextCloseInsideClickHandler_isDeferredToEndOfTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();

        engine.click(session, click(0));

        assertEquals(1, mainView.clickCount, "the handler itself runs synchronously");
        assertEquals(ViewSession.Status.TRANSITIONING, session.status(),
                "close inside click dispatch must defer, not tear down inline");
        assertSame(session, sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new));
        assertSame(session.inventory(), player.getOpenInventory().getTopInventory(),
                "the container stays open until the deferred op runs");

        server.getScheduler().performTicks(1);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void contextOpenViewInsideClickHandler_replacesViewAtEndOfTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession oldSession = session();

        engine.click(oldSession, click(1));

        assertEquals(ViewSession.Status.TRANSITIONING, oldSession.status());
        assertSame(oldSession, sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new),
                "the old session stays registered until the deferred open runs");

        server.getScheduler().performTicks(1);

        assertEquals(ViewSession.Status.CLOSED, oldSession.status());
        assertEquals(CloseReason.REPLACED, mainView.lastCloseReason,
                "the old view observes the replacement through onClose");
        ViewSession current = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        assertNotSame(oldSession, current);
        assertSame(otherView, current.registered().instance());
        assertEquals(ViewSession.Status.ACTIVE, current.status());
        assertSame(current.inventory(), player.getOpenInventory().getTopInventory());
    }

    @Test
    void closeOnClickPostAction_isDeferredToEndOfTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();

        engine.click(session, click(2));

        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        assertSame(session, sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new));
        assertSame(session.inventory(), player.getOpenInventory().getTopInventory());

        server.getScheduler().performTicks(1);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void clicksWhileTransitioning_areCancelledAndNotRouted() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();
        engine.click(session, click(0));
        assertEquals(ViewSession.Status.TRANSITIONING, session.status());
        assertEquals(1, mainView.clickCount);

        InventoryClickEvent second = click(1);
        engine.click(session, second);

        assertTrue(second.isCancelled(), "clicks during a transition are swallowed");
        assertEquals(1, mainView.clickCount, "no component handler may run while TRANSITIONING");
    }

    @Test
    void openViewInsideOnClose_logsSevereAndIsDropped() {
        engine.open(player, CloseNavView.class, ViewArguments.empty());
        ViewSession session = session();
        Logger logger = Logger.getLogger(CloseContextImpl.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);

        try {
            engine.close(session, CloseReason.API);
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(handler.records.stream().anyMatch(record -> record.getLevel() == Level.SEVERE),
                "openView from onClose must emit a SEVERE log");
        assertEquals(ViewSession.Status.CLOSED, session.status());

        server.getScheduler().performTicks(1);

        assertFalse(sessions.find(player.getUniqueId()).isPresent(),
                "no new session may be created from onClose navigation");
    }

    @Test
    void deferredOps_noOpWhenSessionWasClosedBeforeTick() {
        engine.open(player, MainView.class, ViewArguments.empty());
        ViewSession session = session();
        engine.click(session, click(3));
        assertEquals(ViewSession.Status.TRANSITIONING, session.status());

        // manual close races ahead of the scheduled deferred ops
        engine.close(session, CloseReason.API);

        assertEquals(ViewSession.Status.CLOSED, session.status());
        assertEquals(1, mainView.closeCount);
        assertEquals(CloseReason.API, mainView.lastCloseReason);

        assertDoesNotThrow(() -> server.getScheduler().performTicks(1));

        assertEquals(1, mainView.closeCount, "stale deferred ops must not close the session twice");
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=DeferredOpsTest"
Expected: FAIL (`deferredOps_noOpWhenSessionWasClosedBeforeTick` fails: without the defer guard the two stale deferred close ops still run at tick time against the already-closed session, so `closeCount` becomes 2 (or the drain throws); tests (1)-(5) pass as integration pins of Tasks 11/12/14 — if one of them fails instead, fix the owning context/phase per pinned rules 2-3, not the test)

- [ ] **Step 3: Write minimal implementation**

In `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java`, replace the `defer` method and its private end-of-tick drain helper (everything else stays exactly as Task 12 left it; if Task 12's `defer` scheduled the op directly without a drain helper, delete that scheduling line and use this pair — no new imports are required, `Bukkit`, `List` and `ViewSession` are already imported):

```java
    /**
     * Defers an operation to the end of the current tick. The session leaves ACTIVE
     * immediately (TRANSITIONING) so further clicks are swallowed until the operation runs;
     * sessions still TRANSITIONING after the drain return to ACTIVE. Every queued operation
     * is guarded so it no-ops when the session was closed before the tick ran (manual close,
     * disconnect, plugin disable, or an earlier deferred operation queued by the same click).
     *
     * @param session the session the operation belongs to
     * @param op      the operation to run at end of tick
     */
    public void defer(@NotNull ViewSession session, @NotNull Runnable op) {
        assertMainThread("ViewEngine.defer");
        if (session.status() == ViewSession.Status.ACTIVE) {
            session.status(ViewSession.Status.TRANSITIONING);
        }
        // cleanup safety: a stale op against a closed session must do nothing
        session.deferredOps().add(() -> {
            if (session.status() != ViewSession.Status.CLOSED) {
                op.run();
            }
        });
        Bukkit.getScheduler().runTask(plugin, () -> drainDeferred(session));
    }

    private void drainDeferred(ViewSession session) {
        List<Runnable> ops = session.deferredOps();
        while (!ops.isEmpty()) {
            ops.remove(0).run();
        }
        // deferred ops that neither closed nor replaced the session leave it usable again
        if (session.status() == ViewSession.Status.TRANSITIONING) {
            session.status(ViewSession.Status.ACTIVE);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=DeferredOpsTest"
Expected: PASS

Also re-run the neighboring engine suites to confirm no regression in open/close, ordering and click routing:

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewEngineOpenCloseTest,ViewEngineOpenOrderingTest,ClickRoutingTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/DeferredOpsTest.java
git commit -m "feat(inventory-api): guard deferred view operations against early session close"
```

---

### Task 16: UpdatePhase + reactive flush + SharedState wiring + scheduled repaints

**Files:**
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/UpdatePhase.java (complete replacement of the Task 12 skeleton)
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java (fill `update`/`flushDirty`/`flushShared`, add shared-flush wiring, flush after click dispatch)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/UpdateFlushTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
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
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;
import tech.guilhermekaua.spigotboot.inventoryapi.state.SharedState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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

class UpdateFlushTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;
    private PlayerMock second;

    private ReactiveView reactiveView;
    private CascadeView cascadeView;
    private FeedbackView feedbackView;
    private ScheduledView scheduledView;
    private SharedView sharedView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        reactiveView = new ReactiveView();
        cascadeView = new CascadeView();
        feedbackView = new FeedbackView();
        scheduledView = new ScheduledView();
        sharedView = new SharedView();
        views.register(reactiveView);
        views.register(cascadeView);
        views.register(feedbackView);
        views.register(scheduledView);
        views.register(sharedView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
        });
        player = server.addPlayer("first");
        second = server.addPlayer("second");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession sessionOf(PlayerMock who) {
        return sessions.find(who.getUniqueId()).orElseThrow(IllegalStateException::new);
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

    @Test
    void stateChange_insideClickHandler_repaintsOnlyWatchingComponent() {
        engine.open(player, ReactiveView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);
        Inventory inventory = session.inventory();

        // initial paint runs every renderer exactly once
        assertEquals(Material.EMERALD, inventory.getItem(0).getType());
        assertEquals(1, reactiveView.watchedRenders.get());
        assertEquals(1, reactiveView.unwatchedRenders.get());
        assertEquals(1, inventory.getItem(1).getAmount());

        engine.click(session, click(player, session, 2));

        assertEquals(Material.DIAMOND, inventory.getItem(0).getType(),
                "watching component must repaint with the new state");
        assertEquals(2, reactiveView.watchedRenders.get());
        assertEquals(1, reactiveView.unwatchedRenders.get(),
                "unwatched component must not re-render on a state flush");
        assertEquals(1, inventory.getItem(1).getAmount(),
                "unwatched slot must keep its previous content");
    }

    @Test
    void stateChange_duringOnUpdate_cascadesExactlyOneExtraPass() {
        engine.open(player, CascadeView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);
        assertEquals(1, cascadeView.renders.get());
        assertEquals(0, cascadeView.updates.get());

        engine.update(session, UpdateTrigger.EXPLICIT);

        // explicit pass + exactly one cascaded STATE_CHANGE mini-pass
        assertEquals(2, cascadeView.updates.get());
        assertEquals(3, cascadeView.renders.get());
        assertFalse(session.stateStore().hasDirty());
    }

    @Test
    void selfFeedingRenderer_stopsAtCascadeCapWithWarning() {
        Logger logger = Logger.getLogger(ViewEngine.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, FeedbackView.class, ViewArguments.empty());
            ViewSession session = sessionOf(player);
            assertEquals(1, feedbackView.renders.get());

            engine.update(session, UpdateTrigger.EXPLICIT);

            // one explicit pass plus eight capped cascade passes, then stop
            assertEquals(10, feedbackView.renders.get());
            assertFalse(session.stateStore().hasDirty(),
                    "remaining dirty tokens must be dropped at the cap");
            assertTrue(handler.hasWarningContaining("feedback loop"),
                    "hitting the cascade cap must log a WARNING");

            // the engine stays responsive after the cap
            server.getScheduler().performTicks(1);
            assertEquals(ViewSession.Status.ACTIVE, session.status());
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void scheduledUpdate_repaintsDynamicItems() {
        engine.open(player, ScheduledView.class, ViewArguments.empty());
        ViewSession session = sessionOf(player);
        Inventory inventory = session.inventory();
        assertEquals(1, scheduledView.renders.get());
        assertEquals(1, inventory.getItem(0).getAmount());

        server.getScheduler().performTicks(2);

        assertEquals(2, scheduledView.renders.get());
        assertEquals(2, inventory.getItem(0).getAmount());
    }

    @Test
    void sharedStateSet_onMain_flushesEverySessionOfTheView() {
        engine.open(player, SharedView.class, ViewArguments.empty());
        engine.open(second, SharedView.class, ViewArguments.empty());
        assertEquals(2, sharedView.renders.get());

        sharedView.shared.set("changed");

        assertEquals(4, sharedView.renders.get(),
                "both sessions of the owning view must repaint exactly once");
    }

    @Test
    void sharedStateSet_offMain_isScheduledAndFlushesNextTick() throws InterruptedException {
        engine.open(player, SharedView.class, ViewArguments.empty());
        engine.open(second, SharedView.class, ViewArguments.empty());
        assertEquals(2, sharedView.renders.get());

        Thread writer = new Thread(() -> sharedView.shared.set("background"));
        writer.start();
        writer.join();

        assertEquals(2, sharedView.renders.get(),
                "off-main writes must not flush synchronously");

        server.getScheduler().performTicks(1);

        assertEquals(4, sharedView.renders.get());
    }

    @Test
    void sharedStateSets_offMain_coalesceToOneFlushPerSessionPerTick() throws InterruptedException {
        engine.open(player, SharedView.class, ViewArguments.empty());
        engine.open(second, SharedView.class, ViewArguments.empty());
        assertEquals(2, sharedView.renders.get());

        Thread writer = new Thread(() -> {
            sharedView.shared.set("a");
            sharedView.shared.set("b");
        });
        writer.start();
        writer.join();
        server.getScheduler().performTicks(1);

        assertEquals(4, sharedView.renders.get(),
                "two pre-tick writes must coalesce into one flush pass per session");
    }

    static final class ReactiveView extends View {
        final MutableState<Integer> counter = mutableState(0);
        final AtomicInteger watchedRenders = new AtomicInteger();
        final AtomicInteger unwatchedRenders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Reactive").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        watchedRenders.incrementAndGet();
                        Integer value = counter.get(ctx);
                        return new ItemStack(value != null && value > 0
                                ? Material.DIAMOND : Material.EMERALD);
                    })
                    .updateOnStateChange(counter);
            render.slot(1)
                    .item(ctx -> new ItemStack(Material.PAPER, unwatchedRenders.incrementAndGet()));
            render.slot(2, new ItemStack(Material.STONE))
                    .onClick(ctx -> counter.update(ctx, value -> value + 1));
        }
    }

    static final class CascadeView extends View {
        final MutableState<Integer> token = mutableState(0);
        final AtomicInteger updates = new AtomicInteger();
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Cascade").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        renders.incrementAndGet();
                        return new ItemStack(Material.PAPER);
                    })
                    .updateOnStateChange(token);
        }

        @Override
        protected void onUpdate(@NotNull UpdateContext context) {
            // dirty the token on the first pass only: exactly one cascade expected
            if (updates.incrementAndGet() == 1) {
                token.set(context, 1);
            }
        }
    }

    static final class FeedbackView extends View {
        final MutableState<Integer> token = mutableState(0);
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Feedback").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        renders.incrementAndGet();
                        // self-feeding: every render re-dirties the watched token
                        token.update(ctx, value -> value + 1);
                        return new ItemStack(Material.PAPER);
                    })
                    .updateOnStateChange(token);
        }
    }

    static final class ScheduledView extends View {
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Scheduled").rows(1).scheduleUpdate(2);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> new ItemStack(Material.PAPER, renders.incrementAndGet()));
        }
    }

    static final class SharedView extends View {
        final SharedState<String> shared = sharedState("initial");
        final AtomicInteger renders = new AtomicInteger();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Shared").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.slot(0)
                    .item(ctx -> {
                        renders.incrementAndGet();
                        return new ItemStack(Material.PAPER);
                    })
                    .updateOnStateChange(shared);
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        boolean hasWarningContaining(String fragment) {
            for (LogRecord record : records) {
                if (record.getLevel() == Level.WARNING && record.getMessage() != null
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

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=UpdateFlushTest"
Expected: FAIL (the Task 12 `UpdatePhase` skeleton performs no onUpdate/repaint work and `ViewEngine.flushDirty`/`flushShared` still throw `UnsupportedOperationException: implemented in Task 16`; the reactive, cascade, scheduled and shared assertions all fail)

- [ ] **Step 3: Write minimal implementation**

**internal/engine/phase/UpdatePhase.java** (complete replacement of the Task 12 skeleton; `ViewEngine`'s constructor must build it as `this.updatePhase = new UpdatePhase(this, painter);` — if Task 12's draft constructed it with different arguments, update that single construction line to match)

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.UpdateTrigger;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.component.ComponentInstance;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.UpdateContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Update pass: invokes {@code View.onUpdate} with the trigger, then repaints components —
 * all of them, or only the watchers of a dirty token set during a state flush. An
 * {@code onUpdate} failure is logged and the repaint still runs (§9 error table).
 */
@ApiStatus.Internal
public final class UpdatePhase {

    private static final Logger LOGGER = Logger.getLogger(UpdatePhase.class.getName());

    private final ViewEngine engine;
    private final SlotPainter painter;

    /**
     * Creates the phase.
     *
     * @param engine  the engine providing context plumbing
     * @param painter the slot painter writing repaint results
     */
    public UpdatePhase(@NotNull ViewEngine engine, @NotNull SlotPainter painter) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.painter = Objects.requireNonNull(painter, "painter");
    }

    /**
     * Runs one update pass on a session. Non-active sessions skip every trigger except
     * {@link UpdateTrigger#STATE_CHANGE}, which flushes are allowed to deliver.
     *
     * @param session     the session to update
     * @param trigger     the cause of this pass
     * @param dirtyOrNull the dirty token ids restricting the repaint to their watchers,
     *                    or {@code null} to repaint every component
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger,
                       @Nullable Set<Integer> dirtyOrNull) {
        if (!session.isActive() && trigger != UpdateTrigger.STATE_CHANGE) {
            return;
        }

        UpdateContextImpl context = new UpdateContextImpl(session, engine, trigger);
        try {
            invokeOnUpdate(session.registered().instance(), context);
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

    private void repaint(ViewSession session, UpdateContextImpl context,
                         @Nullable Set<Integer> dirtyOrNull) {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }

        List<ComponentInstance> targets = dirtyOrNull == null
                ? session.components().all()
                : session.components().watchersOf(dirtyOrNull);
        Player player = session.player();
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();

        for (ComponentInstance component : targets) {
            ItemStack item = component.renderForPaint(context);
            if (item == ComponentInstance.RENDER_FAILURE) {
                // identity check: render failed, keep the previous slot content (§9)
                continue;
            }
            for (int slot : component.slots()) {
                painter.paint(player, inventory, slot, item, applyPlaceholders);
            }
        }
    }

    // onUpdate is protected on the public View type; the phase dispatches reflectively
    private static void invokeOnUpdate(View view, UpdateContext context) {
        try {
            Method method = View.class.getDeclaredMethod("onUpdate", UpdateContext.class);
            method.setAccessible(true);
            method.invoke(view, context);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onUpdate failed for view "
                    + view.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onUpdate for view "
                    + view.getClass().getName(), ex);
        }
    }
}
```

**internal/engine/ViewEngine.java** (targeted edits; everything else stays exactly as Tasks 12/13/14/15 left it)

(a) Ensure these imports exist (add the missing ones):

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.RegisteredView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.state.SharedStateImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.state.StateToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
```

(b) Add these fields (skip any that Task 12's draft already declares):

```java
    private static final Logger LOGGER = Logger.getLogger(ViewEngine.class.getName());
    private static final int CASCADE_CAP = 8;

    // re-entrancy guard for main-thread shared flushes: a renderer writing shared state
    // while its view is being flushed must not recurse; main thread only
    private final Set<View> sharedFlushPending = new HashSet<>();
    // per-tick coalescing of off-main shared writes; touched from any thread
    private final Set<View> sharedFlushScheduled =
            Collections.newSetFromMap(new ConcurrentHashMap<View, Boolean>());
```

(c) In `open(Player, Class, ViewArguments)`, immediately after the line where `views.find(viewType)` resolves the registration (`RegisteredView registered = views.find(viewType).orElseThrow(...)`), insert this single line:

```java
        wireSharedFlush(registered);
```

(d) Replace the `update` method with:

```java
    /**
     * Runs an update pass on a session, then flushes any state the handlers dirtied.
     *
     * @param session the session to update
     * @param trigger the cause of the update
     */
    public void update(@NotNull ViewSession session, @NotNull UpdateTrigger trigger) {
        assertMainThread("ViewEngine.update");
        updatePhase.update(session, trigger, null);
        flushDirty(session);
    }
```

(e) Replace the `click` method with (only change: `flushDirty(session);` after dispatch — handlers write state during routing and the flush coalesces at the end of the entry point, §5.5):

```java
    /**
     * Routes a Bukkit click event into the session per the click policy; clicks on sessions
     * that are not ACTIVE are swallowed (cancelled, not routed). Dirty state written by
     * click handlers is flushed once after dispatch.
     *
     * @param session the clicked session
     * @param event   the Bukkit event
     */
    public void click(@NotNull ViewSession session, @NotNull InventoryClickEvent event) {
        assertMainThread("ViewEngine.click");
        if (session.status() != ViewSession.Status.ACTIVE) {
            event.setCancelled(true);
            return;
        }
        inClickDispatch = true;
        try {
            clickRoutingPhase.route(session, event);
        } finally {
            inClickDispatch = false;
        }
        flushDirty(session);
    }
```

(f) Replace the `flushDirty` body (drops the `UnsupportedOperationException`):

```java
    /**
     * Flushes dirty state tokens of a session: each pass drains the dirty set and runs a
     * STATE_CHANGE update over the watchers; passes repeat while handlers re-dirty tokens,
     * capped at {@value #CASCADE_CAP} cascades per flush, after which the remaining dirty
     * tokens are dropped with a WARNING.
     *
     * @param session the session to flush
     */
    public void flushDirty(@NotNull ViewSession session) {
        assertMainThread("ViewEngine.flushDirty");
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
```

(g) Replace the `flushShared` body (pinned: a shared-state flush is a FULL component repaint pass — `dirtyOrNull = null` — with trigger STATE_CHANGE for every active session of the owning view):

```java
    /**
     * Runs a full STATE_CHANGE repaint pass on every active session of the given view;
     * invoked after a {@code SharedState} write through the wired flush hook.
     *
     * @param owner the view singleton whose sessions should flush
     */
    public void flushShared(@NotNull View owner) {
        assertMainThread("ViewEngine.flushShared");
        // snapshot: an onUpdate handler may close a session and mutate the registry
        List<ViewSession> snapshot = new ArrayList<>(sessions.all());
        for (ViewSession session : snapshot) {
            if (session.registered().instance() == owner && session.isActive()) {
                updatePhase.update(session, UpdateTrigger.STATE_CHANGE, null);
            }
        }
    }
```

(h) Add the wiring method (called from `open`, edit (c)):

```java
    /**
     * Wires the flush hook of every {@code SharedState} token of the view so writes fan out
     * to all of the view's open sessions: main-thread writes flush immediately (re-entrancy
     * guarded), off-main writes coalesce into one scheduled flush per view per tick. The
     * overwrite is idempotent and re-applied on every open.
     *
     * @param registered the registration whose view instance is being opened
     */
    void wireSharedFlush(@NotNull RegisteredView registered) {
        final View owner = registered.instance();
        for (StateToken token : owner.tokenTable().tokens()) {
            if (!(token instanceof SharedStateImpl)) {
                continue;
            }
            ((SharedStateImpl<?>) token).flushHook(() -> {
                if (Bukkit.isPrimaryThread()) {
                    if (sharedFlushPending.add(owner)) {
                        try {
                            flushShared(owner);
                        } finally {
                            sharedFlushPending.remove(owner);
                        }
                    }
                } else {
                    if (sharedFlushScheduled.add(owner)) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sharedFlushScheduled.remove(owner);
                            flushShared(owner);
                        });
                    }
                }
            });
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=UpdateFlushTest"
Expected: PASS

Also re-run the earlier engine suites to confirm the `click`/`update` edits did not regress them:

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewEngineOpenCloseTest,ViewEngineOpenOrderingTest,ClickRoutingTest,DeferredOpsTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/UpdatePhase.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/ViewEngine.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/UpdateFlushTest.java
git commit -m "feat(inventory-api): implement reactive update flush and shared-state fan-out"
```

---

### Task 17: ViewRegistry DI + ViewDiscoveryService + boot guard

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/ViewDiscoveryService.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistry.java (keep the no-arg constructor from Task 12; add `@Component`, the `@Inject` DI constructor and `initialize(Context)`)
- Create (test fixtures): modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/fixtures/ValidFixtureView.java
- Create (test fixtures): modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/fixtures/NotAViewFixture.java
- Create (test fixtures): modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/fixtures/AbstractFixtureView.java
- Create (test fixtures): modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/fixtures/UnannotatedFixtureView.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/ViewDiscoveryServiceTest.java
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistryTest.java

- [ ] **Step 1: Write the failing test**

The test mirrors the 2.x `InventoryDiscoveryServiceTest` structure (plain JUnit, no MockBukkit) and adds real fixture classes in a `fixtures` subpackage so the classpath scan has something to find. The fixtures are part of this step — they are test inputs, not implementation.

**ViewDiscoveryServiceTest.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures.AbstractFixtureView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures.NotAViewFixture;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures.UnannotatedFixtureView;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures.ValidFixtureView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewDiscoveryServiceTest {

    private static final String FIXTURES_PACKAGE =
            "tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures";

    @Test
    void emptyResultForPackageWithNoAnnotatedClasses() {
        ViewDiscoveryService service = new ViewDiscoveryService();

        Set<Class<? extends View>> result = service.discoverFromPackage(
                "tech.guilhermekaua.spigotboot.inventoryapi.nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void resultIsAlwaysNonNull() {
        ViewDiscoveryService service = new ViewDiscoveryService();

        Set<Class<? extends View>> result = service.discoverFromPackage(
                "com.example.does.not.exist");

        assertTrue(result.isEmpty(), "service should return empty set, not null");
    }

    @Test
    void discoversOnlyConcreteAnnotatedViewSubclasses() {
        ViewDiscoveryService service = new ViewDiscoveryService();

        Set<Class<? extends View>> result = service.discoverFromPackage(FIXTURES_PACKAGE);

        assertEquals(Collections.singleton(ValidFixtureView.class), result);
        // abstract @RegisterView subclass is excluded silently
        assertFalse(result.contains(AbstractFixtureView.class));
        // unannotated View subclass is not picked up by the annotation scan
        assertFalse(result.contains(UnannotatedFixtureView.class));
    }

    @Test
    void notAViewFixture_isExcludedAndLoggedSevere() {
        ViewDiscoveryService service = new ViewDiscoveryService();
        Logger logger = Logger.getLogger(ViewDiscoveryService.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
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
        };
        logger.addHandler(handler);

        try {
            Set<Class<? extends View>> result = service.discoverFromPackage(FIXTURES_PACKAGE);

            assertFalse(result.contains(NotAViewFixture.class));
        } finally {
            logger.removeHandler(handler);
        }

        boolean severeLogged = records.stream().anyMatch(record ->
                record.getLevel() == Level.SEVERE
                        && record.getMessage().contains(NotAViewFixture.class.getName())
                        && record.getMessage().contains("does not extend View"));
        assertTrue(severeLogged,
                "expected a SEVERE boot-guard log naming " + NotAViewFixture.class.getName());
    }
}
```

**fixtures/ValidFixtureView.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures;

import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;

/**
 * Discovery fixture: concrete annotated {@link View} subclass, the only class in this
 * package that must be discovered.
 */
@RegisterView
public final class ValidFixtureView extends View {
}
```

**fixtures/NotAViewFixture.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures;

import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;

/**
 * Discovery fixture: annotated but not a {@code View} subclass; the boot guard must log
 * SEVERE and exclude it.
 */
@RegisterView
public final class NotAViewFixture {
}
```

**fixtures/AbstractFixtureView.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures;

import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;

/**
 * Discovery fixture: abstract annotated {@link View} subclass; excluded silently.
 */
@RegisterView
public abstract class AbstractFixtureView extends View {
}
```

**fixtures/UnannotatedFixtureView.java**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.fixtures;

import tech.guilhermekaua.spigotboot.inventoryapi.View;

/**
 * Discovery fixture: concrete {@link View} subclass without {@code @RegisterView};
 * excluded from the annotation scan.
 */
public final class UnannotatedFixtureView extends View {
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewDiscoveryServiceTest"
Expected: FAIL (compilation error: class ViewDiscoveryService does not exist in package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery)

- [ ] **Step 3: Write minimal implementation**

Mirrors the 2.x `InventoryDiscoveryService` structure exactly (annotation scan + discovery-index merge + concrete/assignable filtering), adding only the §5.1 boot guard.

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryCategories;
import tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndexReader;
import tech.guilhermekaua.spigotboot.core.utils.ReflectionUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.RegisterView;

import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Finds every concrete {@link View} subclass annotated with {@link RegisterView} under the
 * user's base package. Reads the compile-time {@code DiscoveryIndex} when present and falls
 * back to runtime classpath scanning otherwise — mirrors {@code InventoryDiscoveryService}.
 *
 * <p>Boot guard: a concrete class carrying {@code @RegisterView} that does not extend
 * {@code View} is logged SEVERE and excluded instead of being dropped silently.
 */
@ApiStatus.Internal
@Component
public final class ViewDiscoveryService {

    private static final Logger LOGGER = Logger.getLogger(ViewDiscoveryService.class.getName());

    /**
     * Discovers the registrable view classes under the given base package.
     *
     * @param basePackage the package scanned recursively
     * @return every concrete {@code @RegisterView}-annotated {@link View} subclass found; never {@code null}
     */
    @SuppressWarnings("unchecked")
    public @NotNull Set<Class<? extends View>> discoverFromPackage(@NotNull String basePackage) {
        LinkedHashSet<Class<?>> candidates = new LinkedHashSet<>(
                ReflectionUtils.getClassesAnnotatedWith(basePackage, RegisterView.class));

        DiscoveryIndexReader reader = DiscoveryIndexReader.create();
        if (reader.hasAnyIndex()) {
            candidates.addAll(reader.classesInCategory(DiscoveryCategories.INVENTORY, basePackage));
        }

        Set<Class<? extends View>> views = new LinkedHashSet<>();
        for (Class<?> candidate : candidates) {
            if (candidate.isInterface() || Modifier.isAbstract(candidate.getModifiers())) {
                continue;
            }
            if (!isViewOrWarn(candidate)) {
                continue;
            }
            views.add((Class<? extends View>) candidate);
        }
        return views;
    }

    // boot guard (§5.1): the INVENTORY index category is shared with 2.x @Inventory classes,
    // so only candidates that explicitly carry @RegisterView are a user mistake worth a SEVERE
    private static boolean isViewOrWarn(Class<?> candidate) {
        if (View.class.isAssignableFrom(candidate)) {
            return true;
        }
        if (candidate.isAnnotationPresent(RegisterView.class)) {
            LOGGER.severe("class " + candidate.getName()
                    + " is annotated @RegisterView but does not extend View; it will not be registered");
        }
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewDiscoveryServiceTest"
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/ViewDiscoveryService.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/ViewDiscoveryServiceTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/discovery/fixtures
git commit -m "feat(inventory-api): discover @RegisterView views with boot guard"
```

- [ ] **Step 6: Write the failing test**

Pins the `register` contract Task 12 implemented (freeze-then-onInit order, duplicate rejection, config-error propagation, find/all) and forces the new DI constructor into existence — the test does not compile until `ViewRegistry(ViewDiscoveryService)` exists. `initialize(Context)` itself is exercised end to end by Task 18's module bootstrap; no `Context` mock here.

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.registry;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.ViewDiscoveryService;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewRegistryTest {

    public static final class CountingView extends View {
        int onInitCalls;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            onInitCalls++;
            config.title("Counting").rows(1);
        }

        MutableState<String> declareLateState() {
            return mutableState("late");
        }
    }

    public static final class SecondView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Second").rows(2);
        }
    }

    public static final class BadConfigView extends View {
        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            // neither rows nor layout: build() must reject this config
            config.title("Bad");
        }
    }

    @Test
    void register_runsOnInitExactlyOnce() {
        ViewRegistry registry = new ViewRegistry();
        CountingView view = new CountingView();

        registry.register(view);

        assertEquals(1, view.onInitCalls);
    }

    @Test
    void register_freezesTokenTable_stateFactoriesThrowAfterwards() {
        ViewRegistry registry = new ViewRegistry();
        CountingView view = new CountingView();

        registry.register(view);

        assertTrue(view.tokenTable().isFrozen());
        assertThrows(IllegalStateException.class, view::declareLateState);
    }

    @Test
    void register_badConfig_propagatesViewConfigurationException() {
        ViewRegistry registry = new ViewRegistry();

        assertThrows(ViewConfigurationException.class, () -> registry.register(new BadConfigView()));
        assertFalse(registry.find(BadConfigView.class).isPresent());
    }

    @Test
    void findAndAll_exposeRegistrations() {
        ViewRegistry registry = new ViewRegistry();
        CountingView counting = new CountingView();
        SecondView second = new SecondView();
        registry.register(counting);
        registry.register(second);

        Optional<RegisteredView> found = registry.find(CountingView.class);
        assertTrue(found.isPresent());
        assertEquals(CountingView.class, found.get().type());
        assertSame(counting, found.get().instance());
        assertEquals("Counting", found.get().config().title());

        assertFalse(registry.find(BadConfigView.class).isPresent());
        assertEquals(2, registry.all().size());
    }

    @Test
    void register_duplicateType_throwsIllegalStateException() {
        ViewRegistry registry = new ViewRegistry();
        CountingView first = new CountingView();
        registry.register(first);

        CountingView second = new CountingView();
        assertThrows(IllegalStateException.class, () -> registry.register(second));

        // the duplicate is rejected before its onInit runs and the first registration survives
        assertEquals(0, second.onInitCalls);
        assertSame(first, registry.find(CountingView.class).map(RegisteredView::instance).orElse(null));
    }

    @Test
    void diConstructor_supportsDirectRegistration() {
        ViewRegistry registry = new ViewRegistry(new ViewDiscoveryService());
        CountingView view = new CountingView();

        registry.register(view);

        assertTrue(registry.find(CountingView.class).isPresent());
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewRegistryTest"
Expected: FAIL (compilation error: constructor ViewRegistry(ViewDiscoveryService) is undefined — Task 12 only created the no-arg constructor)

- [ ] **Step 8: Write minimal implementation**

Full final file. The only changes versus Task 12 are: the `@Component` annotation, the `@Inject` DI constructor (the no-arg constructor is KEPT for tests and direct bootstrap), `initialize(Context)` and its two discovery helpers — these mirror the 2.x `InventoryRegistry.initialize` structure exactly (base-package resolution from the `Context`, `findInjectConstructor`/`resolveArguments`/`initializeBean`/`registerDependency` dependency-manager calls), substituting `ViewDiscoveryService.discoverFromPackage` and ending in `this.register(instance)`. The `register`/`find`/`all`/`invokeOnInit` bodies keep Task 12's behavior exactly (freeze FIRST, then `onInit`, then build/validate; duplicate type rejected up front).

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.registry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.utils.BeanUtils;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfig;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.ViewConfigurationException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.ViewDiscoveryService;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores every registered view singleton with its frozen config, keyed by concrete class.
 * Boot-time discovery and dependency-manager instantiation happen in
 * {@link #initialize(Context)}, mirroring the 2.x {@code InventoryRegistry} bootstrap.
 */
@ApiStatus.Internal
@Component
public final class ViewRegistry {
    private static final Logger LOGGER = Logger.getLogger(ViewRegistry.class.getName());

    private final Map<Class<? extends View>, RegisteredView> views = new ConcurrentHashMap<>();

    private final @Nullable ViewDiscoveryService discoveryService;

    /**
     * Creates a registry without discovery support; used by tests and bootstrap code that
     * register view instances directly.
     */
    public ViewRegistry() {
        this.discoveryService = null;
    }

    /**
     * Creates the registry with discovery support.
     *
     * @param discoveryService the discovery service used by {@link #initialize(Context)}
     */
    @Inject
    public ViewRegistry(@NotNull ViewDiscoveryService discoveryService) {
        this.discoveryService = Objects.requireNonNull(discoveryService, "discoveryService cannot be null.");
    }

    /**
     * Discovers {@code @RegisterView} classes under the host plugin's base package, instantiates
     * each through the dependency manager and registers it. A view that fails to instantiate or
     * register is logged SEVERE and skipped; the remaining views still register.
     *
     * @param context the host plugin's application context
     * @throws Exception when context access fails
     */
    public void initialize(@NotNull Context context) throws Exception {
        ViewDiscoveryService discovery = this.discoveryService != null
                ? this.discoveryService
                : context.getBean(ViewDiscoveryService.class);
        if (discovery == null) {
            throw new IllegalStateException("ViewDiscoveryService is not available.");
        }

        String basePackage = context.getPlugin().getMainClass().getPackage().getName();
        Set<Class<? extends View>> classes = discovery.discoverFromPackage(basePackage);
        DependencyManager dependencyManager = context.getDependencyManager();

        int registered = 0;
        for (Class<? extends View> viewClass : classes) {
            try {
                registerDiscoveredView(viewClass, dependencyManager);
                registered++;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to register view " + viewClass.getName(), ex);
            }
        }

        LOGGER.log(Level.INFO, "Registered {0} views.", registered);
    }

    private void registerDiscoveredView(
            Class<? extends View> viewClass,
            DependencyManager dependencyManager
    ) throws Exception {
        Constructor<?> constructor = dependencyManager.findInjectConstructor(viewClass);
        if (constructor == null) {
            throw new IllegalStateException("No injectable constructor found for view: " + viewClass.getName());
        }

        Object[] constructorArguments = dependencyManager.resolveArguments(constructor);
        constructor.setAccessible(true);
        Object rawInstance = constructor.newInstance(constructorArguments);

        BeanDefinition definition = new BeanDefinition(
                viewClass,
                viewClass,
                viewClass.getName() + "#view",
                false,
                null,
                null
        );
        View view = (View) dependencyManager.initializeBean(definition, rawInstance);
        injectSuperclassDependencies(dependencyManager, viewClass, view);

        dependencyManager.registerDependency(
                view,
                BeanUtils.getQualifier(viewClass),
                BeanUtils.getIsPrimary(viewClass)
        );
        register(view);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void injectSuperclassDependencies(
            DependencyManager dependencyManager,
            Class<? extends View> viewClass,
            View instance
    ) {
        for (Class type = viewClass.getSuperclass();
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            dependencyManager.injectDependencies(type, instance);
        }
    }

    /**
     * Registers a view instance directly: freezes its token table, runs {@code onInit} once and
     * validates the resulting config.
     *
     * @param instance the singleton view instance to register
     * @throws ViewConfigurationException when the built config violates the validation rules
     * @throws IllegalStateException      when the view class is already registered
     */
    public void register(@NotNull View instance) {
        Class<? extends View> type = instance.getClass();
        if (views.containsKey(type)) {
            throw new IllegalStateException("view " + type.getName() + " is already registered");
        }
        instance.tokenTable().freeze();
        ViewConfigBuilder builder = new ViewConfigBuilder();
        invokeOnInit(instance, builder);
        ViewConfig config = builder.build();
        views.put(type, new RegisteredView(type, instance, config));
    }

    /**
     * Looks up the registration of a view class.
     *
     * @param type the concrete view class
     * @return the registration, or empty when the class was never registered
     */
    public @NotNull Optional<RegisteredView> find(@NotNull Class<? extends View> type) {
        return Optional.ofNullable(views.get(type));
    }

    /**
     * Returns every registration.
     *
     * @return an unmodifiable view of all registrations
     */
    public @NotNull Collection<RegisteredView> all() {
        return Collections.unmodifiableCollection(views.values());
    }

    // onInit is protected on the public View type; the registry dispatches reflectively
    private static void invokeOnInit(View instance, ViewConfigBuilder builder) {
        try {
            Method method = View.class.getDeclaredMethod("onInit", ViewConfigBuilder.class);
            method.setAccessible(true);
            method.invoke(instance, builder);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("onInit failed for view " + instance.getClass().getName(), cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to dispatch onInit for view " + instance.getClass().getName(), ex);
        }
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewRegistryTest,ViewDiscoveryServiceTest"
Expected: PASS (the kept no-arg constructor also keeps every earlier engine test that constructs `new ViewRegistry()` green)

- [ ] **Step 10: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistry.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/registry/ViewRegistryTest.java
git commit -m "feat(inventory-api): register discovered views through the dependency manager"
```

---

### Task 18: ViewService + module wiring + end-to-end

**Files:**
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/service/ViewService.java
- Create: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/PlainViewContextImpl.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/InventoryApiModule.java
- Modify: modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/config/InventoryApiAutoConfiguration.java (verified — no change needed, see Step 3)
- Test: modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/service/ViewServiceEndToEndTest.java

- [ ] **Step 1: Write the failing test**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.service;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
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
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.discovery.ViewDiscoveryService;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.listener.ViewListener;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.state.MutableState;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewServiceEndToEndTest {

    private ServerMock server;
    private SessionRegistry sessions;
    private ViewEngine engine;
    private ViewService service;
    private ViewListener listener;
    private SampleView view;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        ViewRegistry registry = new ViewRegistry(new ViewDiscoveryService());
        sessions = new SessionRegistry();
        engine = new ViewEngine(plugin, registry, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, t) -> {
                });
        service = new ViewService(engine, sessions);
        listener = new ViewListener(sessions, engine);
        view = new SampleView();
        registry.register(view);
        player = server.addPlayer("tester");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ViewSession session() {
        return sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
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

    private static void assertSlotEmpty(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        assertTrue(item == null || item.getType() == Material.AIR, "slot " + slot + " should be empty");
    }

    @Test
    void fullFlowOpenClickReactRepaintAndDisconnectClose() {
        service.open(player, SampleView.class);
        ViewSession session = session();
        assertEquals(ViewSession.Status.ACTIVE, session.status());
        Inventory inventory = session.inventory();

        // initial paint: layout row 2 is '<  AAA  >'
        assertEquals(Material.STONE, inventory.getItem(12).getType());
        assertSlotEmpty(inventory, 9);
        assertEquals(Material.PAPER, inventory.getItem(17).getType());
        assertEquals(1, inventory.getItem(17).getAmount());

        // click protection + reactive repaint on increment
        InventoryClickEvent increment = click(12);
        listener.onClick(increment);
        assertTrue(increment.isCancelled(), "top clicks are deny-by-default");
        assertEquals(Material.ARROW, inventory.getItem(9).getType(), "back arrow appears once counter > 0");
        assertEquals(2, inventory.getItem(17).getAmount());

        // decrement via the nav-like component hides it again
        listener.onClick(click(9));
        assertSlotEmpty(inventory, 9);
        assertEquals(1, inventory.getItem(17).getAmount());

        // live context
        Optional<ViewContext> context = service.contextOf(player);
        assertTrue(context.isPresent());
        assertSame(view, context.get().view());
        assertTrue(context.get().isActive());

        // disconnect close
        listener.onQuit(new PlayerQuitEvent(player, "bye"));
        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(CloseReason.DISCONNECT, view.lastCloseReason);
        assertFalse(service.contextOf(player).isPresent());
    }

    @Test
    void apiCloseClosesSession() {
        service.open(player, SampleView.class);

        service.close(player);

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
        assertEquals(CloseReason.API, view.lastCloseReason);
    }

    @Test
    void closeWithoutSessionIsNoOp() {
        service.close(player);

        assertFalse(sessions.find(player.getUniqueId()).isPresent());
    }

    @Test
    void openUnknownViewThrows() {
        assertThrows(UnknownViewException.class, () -> service.open(player, UnregisteredView.class));
    }

    @Test
    void openOffMainThreadThrows() throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                service.open(player, SampleView.class);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        thread.start();
        thread.join();

        assertTrue(thrown.get() instanceof IllegalStateException,
                "ViewService.open must assert the main thread");
    }

    public static final class SampleView extends View {
        final MutableState<Integer> counter = mutableState(0);
        CloseReason lastCloseReason;

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("&aSample")
                    .layout("         ",
                            "<  AAA  >",
                            "         ");
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext render) {
            render.layoutSlot('A', new ItemStack(Material.STONE))
                    .onClick(ctx -> counter.update(ctx, v -> v + 1));
            render.layoutSlot('<', new ItemStack(Material.ARROW))
                    .displayIf(ctx -> counter.get(ctx) > 0)
                    .updateOnStateChange(counter)
                    .onClick(ctx -> counter.update(ctx, v -> v > 0 ? v - 1 : 0));
            render.layoutSlot('>')
                    .item(ctx -> new ItemStack(Material.PAPER, counter.get(ctx) + 1))
                    .updateOnStateChange(counter);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    public static final class UnregisteredView extends View {
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewServiceEndToEndTest"
Expected: FAIL (compilation error: class ViewService does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.service;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import tech.guilhermekaua.spigotboot.inventoryapi.exception.UnknownViewException;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.context.PlainViewContextImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;

import java.util.Objects;
import java.util.Optional;

/**
 * DI facade for opening and closing views. All methods must be called on the main thread.
 */
@Service
public final class ViewService {

    private final ViewEngine engine;
    private final SessionRegistry sessions;

    /**
     * Creates the service.
     *
     * @param engine   the view engine executing lifecycle operations
     * @param sessions the per-player session registry
     */
    public ViewService(ViewEngine engine, SessionRegistry sessions) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null.");
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null.");
    }

    /**
     * Opens a registered view for the player without arguments.
     *
     * @throws UnknownViewException  if the view class is not registered
     * @throws IllegalStateException if called off the main thread
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> view) {
        open(player, view, ViewArguments.empty());
    }

    /**
     * Opens a registered view for the player with the given arguments.
     *
     * @throws UnknownViewException  if the view class is not registered
     * @throws IllegalStateException if called off the main thread
     */
    public void open(@NotNull Player player, @NotNull Class<? extends View> view,
                     @NotNull ViewArguments arguments) {
        ViewEngine.assertMainThread("ViewService.open");
        engine.open(player, view, arguments);
    }

    /**
     * Closes the player's open view; no-op when none is open.
     *
     * @throws IllegalStateException if called off the main thread
     */
    public void close(@NotNull Player player) {
        ViewEngine.assertMainThread("ViewService.close");
        sessions.find(player.getUniqueId())
                .ifPresent(session -> engine.close(session, CloseReason.API));
    }

    /**
     * Returns a live context for the player's open view, if any.
     */
    public @NotNull Optional<ViewContext> contextOf(@NotNull Player player) {
        return sessions.find(player.getUniqueId())
                .map(session -> (ViewContext) new PlainViewContextImpl(session, engine));
    }
}
```

```java
/* MIT license header — copy from annotation/Inventory.java lines 1-22 */
package tech.guilhermekaua.spigotboot.inventoryapi.internal.context;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.ViewEngine;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;

/**
 * Plain phase-less context handed out by {@code ViewService.contextOf}; adds nothing on top of
 * the base context behavior.
 */
@ApiStatus.Internal
public final class PlainViewContextImpl extends AbstractViewContext {

    /**
     * Creates the context for a live session.
     */
    public PlainViewContextImpl(@NotNull ViewSession session, @NotNull ViewEngine engine) {
        super(session, engine);
    }
}
```

In `InventoryApiModule.java`, add the import, the injected field, and the bootstrap call:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
```

```java
    @Inject
    private ViewRegistry viewRegistry;
```

```java
        // inside onInitialize(Context), right after the existing 2.x bootstrap line:
        inventoryRegistry.initialize(context);
        viewRegistry.initialize(context);
```

`InventoryApiAutoConfiguration.java` — verified, no change needed: `SlotPainter`, `SessionRegistry`, `ViewEngine`, `ViewDiscoveryService`, `ViewRegistry` and `ViewListener` are `@Component` and `ViewService` is `@Service`, so the container constructs them all via constructor injection; their only external dependencies (`Plugin`, `TitleUpdater`, `PlaceholderApplier`) already have beans.

- [ ] **Step 4: Run test to verify it passes**
Run: mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ViewServiceEndToEndTest"
Expected: PASS

- [ ] **Step 5: Run the full module suite**
Run: mvnw.cmd -pl modules/inventory-api/api -am test
Expected: PASS (all new v3 tests and all 2.x tests stay green)

- [ ] **Step 6: Commit**
```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/service/ViewService.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/context/PlainViewContextImpl.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/InventoryApiModule.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/service/ViewServiceEndToEndTest.java
git commit -m "feat(inventory-api): wire v3 core engine into module bootstrap"
```

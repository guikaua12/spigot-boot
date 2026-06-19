# Slot-targeted empty-state and loading frames for pagination — Design Spec

- **Date:** 2026-06-18
- **Module:** `platform-spigot/inventory-api/api`
- **Status:** Approved by user (brainstorming session)
- **Branch:** `feat/inventory-api/choose-fallback-slot`

## 1. Goal

Let a paginated view show a frame item in **specific slots** rather than across the whole
pagination layout, for two render states:

- **Empty state** — when the current page settled with zero items, paint an item only in
  chosen slot(s) and leave the rest of the layout empty. Enables the common "minimalist GUI
  that shows a single centered *no results* item" without the current
  `slot N is bound to both a component and pagination` error.
- **Loading state** — while an async page load is in flight, paint the loading item only in
  chosen slot(s) (e.g. a single centered spinner) and leave the rest empty.

Today both states fill **every** layout slot:

```java
paginateAsync(this::loadPage)
    .layoutChar('O')
    .fallbackItem(ctx -> new ItemStack(Material.BARRIER)); // shows on ALL layout slots
```

Target:

```java
paginateAsync(this::loadPage)
    .layoutChar('O')
    .itemRenderer(...)
    .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 22) // only slot 22 when empty
    .loadingItem(ctx -> new ItemStack(Material.CLOCK), 22);     // only slot 22 while loading
```

**Non-goals (YAGNI):** overriding a static component while empty/loading; relative (layout-indexed)
coordinates; a per-slot item callback (different item per slot); any "whole pagination is empty
across all pages" notion beyond "the current page has zero items"; changing the behavior of
`fallbackItem(fn)` or `loadingItem(fn)` (both keep their current fill-all semantics).

## 2. Language level

Main sources of this module compile at **Java 8** (`platform-spigot/inventory-api/api/pom.xml`,
`<source>1.8</source>`). No records, no `var`, no `List.of()`/`Stream.toList()`. Use `int[]`,
`Collections.emptyList()`, `LinkedHashSet`, explicit loops. All new/changed public and protected
APIs carry the MIT license header and complete Javadoc (`@param`/`@return`/`@throws`) per CLAUDE.md.

## 3. Decisions (locked during brainstorming)

1. **Empty-state trigger:** renders **only** when the current page settled with zero items, in
   the chosen slot(s); all other layout slots are cleared. A partially-filled page shows no
   empty-state. `fallbackItem(fn)` keeps its current "fill every empty layout slot" behavior.
2. **Empty-state API:** a **dedicated** builder method `emptyStateItem(fn, int... slots)` — its
   trigger differs from `fallbackItem` (only-when-empty vs. always-fill), so a distinct name is
   clearer than overloading `fallbackItem`.
3. **Loading API:** an **overload** `loadingItem(fn, int... slots)` — the slotted form has the
   **same** trigger as `loadingItem(fn)` (shown while loading), it only restricts *where*, so an
   overload is the natural fit (a second "loading item" method name would be redundant).
4. **Coordinates:** **absolute** inventory slots (0–53), consistent with the rest of the API
   (`Layout.ofSlots`, component slots). They **may point anywhere** in the container, not only at
   the pagination's own layout slots.
5. **Collision rule:** a frame slot that lands on a **static component** is **rejected at open**
   with `ViewConfigurationException` (same posture as the existing component/pagination overlap
   check). A frame slot that lands on a **different** pagination's target or frame slots is also
   rejected. A frame slot **may** coincide with its own pagination's layout slots (the slot-22
   case) — that is the normal use.

## 4. Public API — `PaginationBuilder<T>`

`PaginationBuilder` is `@ApiStatus.NonExtendable`; the methods are added to the interface and
implemented in `PaginationBuilderImpl`. Both are available on every `paginate*` builder.
`emptyStateItem` works for eager and async sources (like `fallbackItem`); `loadingItem` (both
overloads) remains **async-only** (`build()` throws `ViewConfigurationException` on a non-async
builder, via the existing `firstAsyncOnlyOption()` check, which already keys on `loadingItem`).

### 4.1 `emptyStateItem`

```java
/**
 * Sets an item painted into the given slots when the current page settled with no elements,
 * leaving every other layout slot empty. Unlike {@link #fallbackItem(Function)} — which fills
 * every uncovered slot of every page — this renders only while the current page is empty, and
 * only in {@code slots}; when the page has any element it renders nothing. When both are set,
 * the empty page shows the empty-state item (the fallback fill is suppressed for that paint).
 *
 * <p>Slots are absolute container slots and may lie outside the pagination's layout. Evaluated
 * against the session's context once per slot at paint time. A slot bound to a static component
 * or to another pagination fails at open with {@link ViewConfigurationException}.
 *
 * @param item  the empty-state item factory
 * @param slots the absolute container slots to paint, at least one, each non-negative
 * @return this builder
 * @throws NullPointerException     if {@code item} or {@code slots} is null
 * @throws IllegalArgumentException if {@code slots} is empty or contains a negative slot
 */
@NotNull PaginationBuilder<T> emptyStateItem(@NotNull Function<ViewContext, ItemStack> item,
                                             int... slots);
```

### 4.2 `loadingItem` overload

```java
/**
 * Sets the loading item painted only into the given slots while an async load is in flight,
 * leaving every other layout slot empty. The slotted form of {@link #loadingItem(Function)};
 * it has the same trigger (shown while loading) and only restricts where the item paints.
 * Async-only: on a non-async builder {@link #build()} throws {@link ViewConfigurationException}.
 *
 * <p>Slots are absolute container slots and may lie outside the pagination's layout. Evaluated
 * against the session's context once per slot at paint time. A slot bound to a static component
 * or to another pagination fails at open with {@link ViewConfigurationException}.
 *
 * @param item  the loading item factory
 * @param slots the absolute container slots to paint, at least one, each non-negative
 * @return this builder
 * @throws NullPointerException     if {@code item} or {@code slots} is null
 * @throws IllegalArgumentException if {@code slots} is empty or contains a negative slot
 */
@NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item,
                                          int... slots);
```

The existing no-slots `loadingItem(Function)` is unchanged. Java overload resolution selects the
non-varargs method for `loadingItem(fn)` and the varargs method only when an explicit slot list is
passed, so `loadingItem(fn)` keeps fill-all behavior. `emptyStateItem` has **no** no-slots
overload; calling it with no slots throws `IllegalArgumentException` (there is no sensible
"all slots" default distinct from `fallbackItem`).

## 5. Semantics & precedence

The empty-state / loading-slots frames are **decorative and inert**, exactly like `fallbackItem`
today: they are painted as frame items, never registered as element components, so
`ClickRoutingPhase` resolves no component for them (`PaginationBindings.componentAt` reads only
`elementComponents`) and a click on them follows the view's default click policy. No click-routing
change is required, including for frame slots outside the layout.

Per repaint, the binding selects exactly one render mode and paints its owned slots
deterministically. "Owned outside slots" = the empty-state and loading slots that are **not**
in the pagination's own layout (`targetSlots`):

| Render state | Chosen frame slots | Other layout slots | Owned outside slots |
|---|---|---|---|
| Loading **and** `loadingItem` has slots | loading item | cleared | loading item (if chosen) / cleared |
| Loading, `loadingItem` has no slots / none | today's `loadingOrFallback` over **all** layout slots | — | cleared |
| Settled empty **and** `emptyStateItem` has slots | empty-state item | cleared | empty-state item (if chosen) / cleared |
| Settled, page has elements | elements + `fallbackItem` fill (today) | — | cleared |
| Settled empty, no `emptyStateItem` | today's `emptyOrFallback` over **all** layout slots | — | cleared |

Mode selection precedence: **loading-with-slots** → **empty-with-slots** → **normal engine fill**.
"Loading" wins over "empty" because a page in flight is not yet known to be empty. When both
`fallbackItem` and `emptyStateItem` are set and the page is empty, the empty-state frame takes the
slots and the rest of the layout is cleared (the fill-all is suppressed for that paint), matching
decision 1. The frame item function is evaluated **once per slot** so each slot receives a fresh
`ItemStack` (avoids mutable-stack aliasing across slots).

A frame slot may be shared by both features for the same pagination (e.g. `loadingItem(spinner, 22)`
and `emptyStateItem(barrier, 22)`): the modes are mutually exclusive per paint, so slot 22 shows the
spinner while loading, the barrier when settled empty, and an element/clear otherwise — no conflict,
no validation error.

## 6. Internal design

### 6.1 `PaginationSpec<T>` (`internal.pagination`)
Add two carried values plus accessors: `emptyStateItem` (`Function<ViewContext, ItemStack>`, nullable),
`emptyStateSlots` (`int[]`, empty when unset), and `loadingSlots` (`int[]`, empty when unset; the
existing `loadingItem` function is reused). Store defensive copies; arrays returned by accessors are
clones. Constructor stays package-private; `PaginationBuilderImpl` and same-package tests construct it.

### 6.2 `PaginationBuilderImpl<T>`
- New fields `Function<ViewContext, ItemStack> emptyStateItem`, `int[] emptyStateSlots = {}`,
  `int[] loadingSlots = {}`.
- `emptyStateItem(fn, slots)` and `loadingItem(fn, slots)`: `Objects.requireNonNull` both args;
  throw `IllegalArgumentException` when `slots.length == 0` or any slot `< 0`; store the fn and a
  **de-duplicated, order-preserving** copy of `slots` (e.g. via `LinkedHashSet<Integer>`).
- `loadingItem(fn, slots)` sets the shared `loadingItem` field (so the async-only check still
  fires) and `loadingSlots`; the existing `loadingItem(fn)` sets `loadingItem` and resets
  `loadingSlots = {}`.
- `build()` passes the three new values into the `PaginationSpec` constructor. No new
  combination validation in `validate()` — async-only is already covered by `loadingItem != null`.

### 6.3 `Paginator` + `AbstractPageSourcePagination`
Add one query so the binding can detect the empty mode without reaching into geometry state:

```java
/** @return whether the most recently settled page rendered no elements */
boolean isCurrentPageEmpty();
```

Implemented in `AbstractPageSourcePagination` as `return this.currentItems.isEmpty();` (the field is
already `volatile`). The three concrete paginations inherit it. `insertPageItems()` is **unchanged**
and remains the "normal engine fill" path (rows 2 and 5 of the §5 table).

### 6.4 `PaginationBinding`
This class already owns the painter, `targetSlots`, `lastApplied`, `elementComponents`, the
`PlainViewContextImpl`, and the `applyToSlot` frame path — so all new painting lives here.

- **`initialize(...)`:** after resolving `targetSlots`, build `this.emptyStateSupplier =
  frameSupplier(spec.emptyStateItem(), "empty-state item");` (mirrors the existing
  `fallbackSupplier`/`loadingSupplier`). Capture `emptyStateSlots`/`loadingSlots` from the spec and
  precompute `ownedOutsideSlots = (emptyStateSlots ∪ loadingSlots) \ targetSlots`. Bounds-check the
  frame slots here, alongside the existing `checkBounds` for layout/pattern slots:
  each slot `< effectiveConfig.rows() * Layout.ROW_WIDTH`, else `ViewConfigurationException`.
- **`repaint()`:** replace the body with mode selection:

  ```text
  if (paginator == null) return;
  inventory = session.inventory(); if (inventory == null) return;
  applyPlaceholders = session.effectiveConfig().applyPlaceholders();
  loading = paginator.isLoading();
  empty   = !loading && paginator.isCurrentPageEmpty();

  if (loading && loadingSlots.length > 0)
      paintFrameMode(inventory, loadingSupplier, loadingSlots, applyPlaceholders);
  else if (empty && emptyStateSlots.length > 0)
      paintFrameMode(inventory, emptyStateSupplier, emptyStateSlots, applyPlaceholders);
  else {
      paginator.insertPageItems();                                  // paints targetSlots
      clearSlots(inventory, ownedOutsideSlots, applyPlaceholders);  // outside slots reset
  }
  ```

  ```text
  paintFrameMode(inventory, supplier, frameSlots, applyPlaceholders):
      chosen = setOf(frameSlots)
      for slot in frameSlots:                       // per-slot eval -> fresh ItemStack
          applyToSlot(inventory, slot, supplier.get(), applyPlaceholders)
      for slot in targetSlots:      if !chosen.contains(slot) applyToSlot(.., clear, ..)
      for slot in ownedOutsideSlots: if !chosen.contains(slot) applyToSlot(.., clear, ..)
  // clear == RenderedItem.ofItem(null); applyToSlot's frame branch drops any element
  // component and records lastApplied, so bookkeeping stays consistent.
  ```

  Clearing is idempotent (painting `null`/air into already-free, validated slots), so no
  transition flag is needed; correctness does not depend on the previous mode.
- **Accessors for validation:** add `frameSlots()` returning the de-duplicated union of
  `emptyStateSlots` and `loadingSlots` (clone). Keep existing `targetSlots()`.

Because every repaint path funnels through `PaginationBinding.repaint()` (the initial paint in
`FirstRenderPhase.paintAll`, and async settles / state flushes via
`ViewEngine.paginationSettle` → `UpdatePhase` → `repaint()`; `paginator.insertPageItems()` has no
other caller), this single change covers first paint, navigation, async settle, and refresh.

### 6.5 `FirstRenderPhase.validatePaginationOverlap`
Extend the existing check (which runs inside the open try-block, so failures abort with
`OPEN_FAILED`):

```text
for binding in PaginationBindings.of(session):
    for slot in binding.targetSlots():
        if components.componentAt(slot) != null:
            throw "slot " + slot + " is bound to both a component and pagination"
    for slot in binding.frameSlots():
        if components.componentAt(slot) != null:
            throw "slot " + slot + " is bound to both a component and a pagination frame item"
        for other in bindings where other != binding:
            if other.targetSlots() or other.frameSlots() contains slot:
                throw "slot " + slot + " is bound to two paginations"
```

(The component table comparison uses `session.components().componentAt(slot)`, as today.)

## 7. Backward compatibility

Purely additive. No existing signature changes; `fallbackItem(fn)`, `loadingItem(fn)`, all three
geometries (normal/scroll/pattern) and the existing render paths behave exactly as before when the
new methods are unused (the new slot arrays are empty, so every new branch is skipped and `repaint()`
falls through to the unchanged `insertPageItems()` with an empty `ownedOutsideSlots` clear pass).

## 8. Testing

JUnit 5 + Mockito + MockBukkit, beside the code under test.

- **`PaginationBuilderImplTest`** — `emptyStateItem`/`loadingItem(fn, slots)` store fn + slots;
  reject null fn (NPE), null slots (NPE), zero slots (IAE), negative slot (IAE); dedupe slots;
  `loadingItem(fn, slots)` is async-only (`build()` throws on a non-async builder);
  `loadingItem(fn)` still resets to fill-all.
- **`PaginationBindingTest`** — empty page paints the empty-state item at chosen slots and clears the
  rest of the layout; a page with elements paints no empty-state and leaves `fallbackItem` behavior
  intact; loading with `loadingItem(fn, slots)` paints only chosen slots and clears the rest;
  legacy `loadingItem(fn)` still fills all; an outside-layout frame slot is painted when active and
  cleared on the transition to elements/loaded; per-slot evaluation yields a fresh `ItemStack` per
  slot; empty-state suppresses `fallbackItem` fill on the empty page.
- **Overlap (`FirstRenderPhase` / open path)** — a frame slot on a static component throws; a frame
  slot out of container bounds throws; a frame slot on a different pagination throws; a frame slot on
  the binding's own layout slot is accepted.
- **Integration (`PaginationSampleFlowsTest`)** — eager empty source renders the empty-state frame
  on first paint; async source shows the loading frame at chosen slots while in flight and the
  empty-state frame after settling empty.

## 9. Risk notes

- `isCurrentPageEmpty()` reflects the **current page**, not the whole source. With normal
  page-clamping an empty source means page 1 is empty, so this is the empty-state in practice; a
  genuinely empty non-first page (should not occur under clamping) would also show the empty-state,
  which is acceptable.
- Owned outside slots are only ever painted by this binding's frames (validated free of components
  and other paginations), so the idempotent clear pass can never erase another owner's content.

# Slot-targeted empty-state and loading frames — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a paginated view paint its empty-state and loading frame items into specific absolute slots instead of across the whole pagination layout.

**Architecture:** A new builder method `emptyStateItem(fn, int... slots)` and a `loadingItem(fn, int... slots)` overload record the item + slots on `PaginationSpec`. `PaginationBinding.repaint()` becomes mode-based (loading-slots → empty-slots → normal engine fill), painting frame items at the chosen slots and clearing the rest via the existing `applyToSlot` path. The engine's `insertPageItems()` is unchanged except for a new `Paginator.isCurrentPageEmpty()` query. Open-time validation rejects frame slots that collide with a static component or another pagination.

**Tech Stack:** Java 8, Maven (wrapper), JUnit 5, Mockito, MockBukkit.

**Spec:** `docs/superpowers/specs/2026-06-18-pagination-slot-targeted-frames-design.md`

## Global Constraints

- **Java 8 language level** for all main sources in `platform-spigot/inventory-api/api` (`pom.xml` `<source>1.8</source>`): no records, no `var`, no `List.of()`/`Stream.toList()`. Use `int[]`, `LinkedHashSet`, `Collections.emptyList()`, explicit loops.
- **MIT license header** (copy verbatim from any sibling file) on every new file; complete Javadoc (`@param`/`@return`/`@throws`) on every new/changed public and protected API.
- **Build with JDK 21.** The shell-default JDK 25 crashes Lombok 1.18.36 (`TypeTag :: UNKNOWN`). If Maven runs in a sandbox, disable the sandbox (`dangerouslyDisableSandbox`) or edits run against a stale overlay.
- **Canonical test command (verified working in this environment):** run from the worktree root with the JDK-21 `JAVA_HOME` and the Maven sandbox disabled. The `-Dsurefire.failIfNoSpecifiedTests=false` flag is REQUIRED whenever `-Dtest=` is used with `-am`, because the filter also reaches upstream reactor modules (e.g. `spigot-boot-utils`) that lack the named test and would otherwise fail the build:
  ```
  JAVA_HOME="C:/Users/Guilherme/.jdks/ms-21.0.10" ./mvnw -q -pl platform-spigot/inventory-api/api -am test \
    -Danimal.sniffer.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest=<TestClassOrPattern>
  ```
  All `Run:` lines in the tasks below are shorthand for WHICH test to run — execute them with this canonical recipe, substituting the named class(es) into `-Dtest=`. The full-suite run in Task 5 Step 5 drops `-Dtest`/`-Dsurefire.failIfNoSpecifiedTests` (and may keep `-Danimal.sniffer.skip=true`).
- **Absolute slots everywhere** (0 .. rows*9 - 1), consistent with `Layout.ofSlots` and component slots.
- **Commit after each task** with a Conventional Commit message (`feat:`/`test:`).

## File Structure

Main sources (all in `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/`):
- `pagination/PaginationBuilder.java` — interface: add `emptyStateItem(fn, slots)` + `loadingItem(fn, slots)`.
- `internal/pagination/PaginationBuilderImpl.java` — fields, the two methods, slot validation, reset `loadingSlots` in the no-slots overload, wire `build()`.
- `internal/pagination/PaginationSpec.java` — carry `emptyStateItem`, `emptyStateSlots`, `loadingSlots` + accessors.
- `internal/pagination/engine/Paginator.java` — add `isCurrentPageEmpty()`.
- `internal/pagination/engine/AbstractPageSourcePagination.java` — implement `isCurrentPageEmpty()`.
- `internal/pagination/PaginationBinding.java` — resolve frame slots + bounds in `initialize`, mode-based `repaint()`, `paintFrameMode`/`clearSlots`/`frameSlots()`.
- `internal/engine/phase/FirstRenderPhase.java` — extend `validatePaginationOverlap`.

Tests:
- `internal/pagination/PaginationBuilderImplTest.java` — builder validation/storage (modify).
- `internal/pagination/PaginationBindingTest.java` — `isCurrentPageEmpty`, render modes, bounds (modify; the `specOf` helper must be updated for the new constructor).
- `internal/engine/PaginationFrameSlotFlowsTest.java` — open-path overlap rejection + happy-path empty-state/loading flows (create; modeled on `PaginationOpenWiringTest`).

---

### Task 1: Configuration layer — carry empty-state and loading-slot declarations

**Files:**
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSpec.java`
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/PaginationBuilder.java`
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImpl.java`
- Test: `platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImplTest.java`
- Compile-fix: `platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java` (the `specOf` helper)

**Interfaces:**
- Produces:
  - `PaginationBuilder.emptyStateItem(Function<ViewContext, ItemStack> item, int... slots): PaginationBuilder<T>`
  - `PaginationBuilder.loadingItem(Function<ViewContext, ItemStack> item, int... slots): PaginationBuilder<T>`
  - `PaginationSpec.emptyStateItem(): Function<ViewContext, ItemStack>` (nullable)
  - `PaginationSpec.emptyStateSlots(): int[]` (clone, empty when unset)
  - `PaginationSpec.loadingSlots(): int[]` (clone, empty when unset)
  - `PaginationSpec` constructor gains three trailing params: `Function<ViewContext, ItemStack> emptyStateItem, int[] emptyStateSlots, int[] loadingSlots`.

- [ ] **Step 1: Extend the `PaginationSpec` constructor and add fields + accessors**

In `PaginationSpec.java`, add three fields after `cacheMaxPages` (line ~101):

```java
    private final int cacheMaxPages;
    private final Function<ViewContext, ItemStack> emptyStateItem;
    private final int[] emptyStateSlots;
    private final int[] loadingSlots;
```

Change the constructor signature: after the `int cacheMaxPages) {` parameter add three params, and add three `@param` lines to the Javadoc:

```java
     * @param cacheMaxPages  the async cache LRU bound; 128 unless overridden (the 2.x default)
     * @param emptyStateItem the item painted at {@code emptyStateSlots} when the current page is
     *                       empty, or null when no empty-state is declared
     * @param emptyStateSlots the absolute slots the empty-state item paints into; never null,
     *                       empty when no empty-state is declared
     * @param loadingSlots   the absolute slots the loading item paints into; never null, empty
     *                       when the loading item fills the whole layout (the default)
```

```java
                   @Nullable Duration cacheTtl,
                   int cacheMaxPages,
                   @Nullable Function<ViewContext, ItemStack> emptyStateItem,
                   int[] emptyStateSlots,
                   int[] loadingSlots) {
```

At the end of the constructor body (after `this.cacheMaxPages = cacheMaxPages;`) add:

```java
        this.cacheMaxPages = cacheMaxPages;
        this.emptyStateItem = emptyStateItem;
        this.emptyStateSlots = emptyStateSlots == null ? new int[0] : emptyStateSlots.clone();
        this.loadingSlots = loadingSlots == null ? new int[0] : loadingSlots.clone();
```

Add three accessors after `cacheMaxPages()` (line ~276):

```java
    /**
     * Returns the empty-state item painted at {@link #emptyStateSlots()} when the current page
     * settled with no elements.
     *
     * @return the empty-state item factory, or null when no empty-state is declared
     */
    public @Nullable Function<ViewContext, ItemStack> emptyStateItem() {
        return emptyStateItem;
    }

    /**
     * Returns the absolute slots the empty-state item paints into.
     *
     * @return a defensive copy; empty when no empty-state is declared
     */
    public int[] emptyStateSlots() {
        return emptyStateSlots.clone();
    }

    /**
     * Returns the absolute slots the loading item paints into.
     *
     * @return a defensive copy; empty when the loading item fills the whole layout
     */
    public int[] loadingSlots() {
        return loadingSlots.clone();
    }
```

- [ ] **Step 2: Update the two call sites so the module compiles**

In `PaginationBuilderImpl.build()` (line ~197) change the `new PaginationSpec<>(...)` call to pass the new builder fields (added in Step 4):

```java
        PaginationSpec<T> spec = new PaginationSpec<>(geometry, target, layoutChar, explicitLayout,
                patterns == null ? Collections.<Layout>emptyList() : patterns, renderer, fallbackItem,
                loadingItem, source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages,
                emptyStateItem, emptyStateSlots, loadingSlots);
```

In `PaginationBindingTest.specOf(...)` (line ~208) append the three new arguments:

```java
        return new PaginationSpec<>(geometry, target, layoutChar, explicitLayout, patterns,
                renderer, fallbackItem, null, source, null, null, null, 128,
                null, new int[0], new int[0]);
```

(`PaginationBuilderImpl` references `emptyStateItem`/`emptyStateSlots`/`loadingSlots` fields that are added in Step 4 — do Steps 3–4 in the same edit so the module compiles before running tests.)

- [ ] **Step 3: Add the interface methods with full Javadoc**

In `PaginationBuilder.java`, add after `fallbackItem(...)` (line ~124):

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

And add after the existing `loadingItem(Function)` (line ~133):

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

- [ ] **Step 4: Implement the methods + validation in `PaginationBuilderImpl`**

Add the import `import java.util.LinkedHashSet;` (next to the other `java.util` imports).

Add three fields after `fallbackItem`/`loadingItem` (line ~72):

```java
    private Function<ViewContext, ItemStack> loadingItem;
    private Function<ViewContext, ItemStack> emptyStateItem;
    private int[] emptyStateSlots = new int[0];
    private int[] loadingSlots = new int[0];
```

Replace the existing no-slots `loadingItem(Function)` method body so it resets `loadingSlots`:

```java
    @Override
    public @NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item) {
        this.loadingItem = Objects.requireNonNull(item, "item");
        this.loadingSlots = new int[0];
        return this;
    }
```

Add the two new methods (after the no-slots `loadingItem`):

```java
    @Override
    public @NotNull PaginationBuilder<T> emptyStateItem(@NotNull Function<ViewContext, ItemStack> item,
                                                        int... slots) {
        this.emptyStateItem = Objects.requireNonNull(item, "item");
        this.emptyStateSlots = validatedFrameSlots(slots, "emptyStateItem");
        return this;
    }

    @Override
    public @NotNull PaginationBuilder<T> loadingItem(@NotNull Function<ViewContext, ItemStack> item,
                                                     int... slots) {
        this.loadingItem = Objects.requireNonNull(item, "item");
        this.loadingSlots = validatedFrameSlots(slots, "loadingItem");
        return this;
    }

    // validates and de-duplicates frame slots (empty-state / slotted loading), preserving order
    private static int[] validatedFrameSlots(int[] slots, String method) {
        Objects.requireNonNull(slots, "slots");
        if (slots.length == 0) {
            throw new IllegalArgumentException(method + " requires at least one slot");
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (int slot : slots) {
            if (slot < 0) {
                throw new IllegalArgumentException(method + " slot must not be negative: " + slot);
            }
            unique.add(slot);
        }
        int[] result = new int[unique.size()];
        int index = 0;
        for (int slot : unique) {
            result[index++] = slot;
        }
        return result;
    }
```

- [ ] **Step 5: Write the failing builder tests**

In `PaginationBuilderImplTest.java` add imports:

```java
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.context.ViewContext;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
```

Add these tests:

```java
    @Test
    void emptyStateItem_storesFunctionAndDeduplicatedSlots() {
        Function<ViewContext, ItemStack> fn = ctx -> new ItemStack(Material.BARRIER);
        PaginationImpl<String> token = (PaginationImpl<String>) eagerBuilder(new TestView())
                .itemRenderer(renderer()).emptyStateItem(fn, 22, 22, 4).build();

        assertSame(fn, token.spec().emptyStateItem());
        assertArrayEquals(new int[]{22, 4}, token.spec().emptyStateSlots());
    }

    @Test
    void emptyStateItem_nullFunction_throwsNpe() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        assertThrows(NullPointerException.class, () -> builder.emptyStateItem(null, 1));
    }

    @Test
    void emptyStateItem_noSlots_throwsIllegalArgument() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        assertThrows(IllegalArgumentException.class,
                () -> builder.emptyStateItem(ctx -> new ItemStack(Material.BARRIER)));
    }

    @Test
    void emptyStateItem_negativeSlot_throwsIllegalArgument() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        assertThrows(IllegalArgumentException.class,
                () -> builder.emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 0, -1));
    }

    @Test
    void loadingItem_withSlots_storesDeduplicatedSlots_onAsyncBuilder() {
        PaginationImpl<String> token = (PaginationImpl<String>) asyncBuilder(new TestView())
                .itemRenderer(renderer())
                .loadingItem(ctx -> new ItemStack(Material.EMERALD), 4, 4)
                .build();

        assertArrayEquals(new int[]{4}, token.spec().loadingSlots());
    }

    @Test
    void loadingItem_withSlots_onEagerSource_throwsAsyncOnly() {
        PaginationBuilderImpl<String> builder = eagerBuilder(new TestView());
        builder.itemRenderer(renderer()).loadingItem(ctx -> new ItemStack(Material.EMERALD), 4);

        ViewConfigurationException thrown = assertThrows(ViewConfigurationException.class, builder::build);
        assertTrue(thrown.getMessage().contains("loadingItem"));
    }

    @Test
    void loadingItem_withoutSlots_resetsToFillAll() {
        PaginationImpl<String> token = (PaginationImpl<String>) asyncBuilder(new TestView())
                .itemRenderer(renderer())
                .loadingItem(ctx -> new ItemStack(Material.EMERALD), 4)
                .loadingItem(ctx -> new ItemStack(Material.EMERALD))
                .build();

        assertEquals(0, token.spec().loadingSlots().length);
    }

    @Test
    void defaults_noEmptyStateItemNorFrameSlots() {
        PaginationSpec<String> spec = ((PaginationImpl<String>) eagerBuilder(new TestView())
                .itemRenderer(renderer()).build()).spec();

        assertNull(spec.emptyStateItem());
        assertEquals(0, spec.emptyStateSlots().length);
        assertEquals(0, spec.loadingSlots().length);
    }
```

- [ ] **Step 6: Run the tests — expect compile success and PASS**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationBuilderImplTest`
Expected: PASS (all old and new tests). If `PaginationBindingTest` fails to compile, the `specOf` helper edit from Step 2 is missing.

- [ ] **Step 7: Commit**

```bash
git add platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/PaginationBuilder.java \
        platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImpl.java \
        platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationSpec.java \
        platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBuilderImplTest.java \
        platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java
git commit -m "feat: declare slot-targeted empty-state and loading frames on the pagination builder"
```

---

### Task 2: `Paginator.isCurrentPageEmpty()` query

**Files:**
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/Paginator.java`
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/AbstractPageSourcePagination.java`
- Test: `platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java`

**Interfaces:**
- Consumes: `PaginationBinding.paginator(): Paginator<?>` (existing), `binding.initialize(...)` (existing).
- Produces: `Paginator.isCurrentPageEmpty(): boolean` — whether the most recently settled page rendered no elements.

- [ ] **Step 1: Write the failing test**

In `PaginationBindingTest.java` add:

```java
    @Test
    void isCurrentPageEmpty_trueForEmptySource_falseWhenItemsPresent() {
        ViewSession emptySession = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding empty = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Collections.<Integer>emptyList())),
                0, emptySession, engine);
        empty.initialize(emptySession.layout(), emptySession.effectiveConfig());
        assertTrue(empty.paginator().isCurrentPageEmpty());

        ViewSession itemsSession = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding withItems = new PaginationBinding(
                layoutCharSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2))),
                0, itemsSession, engine);
        withItems.initialize(itemsSession.layout(), itemsSession.effectiveConfig());
        assertFalse(withItems.paginator().isCurrentPageEmpty());
    }
```

- [ ] **Step 2: Run it — expect compile failure**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationBindingTest#isCurrentPageEmpty_trueForEmptySource_falseWhenItemsPresent`
Expected: FAIL — `isCurrentPageEmpty()` is not defined on `Paginator`.

- [ ] **Step 3: Add the interface method**

In `Paginator.java`, after `insertPageItems();` (line ~58) add:

```java
    /**
     * Reports whether the most recently settled page rendered no elements. Used by the host to
     * decide whether to paint the empty-state frame.
     *
     * @return {@code true} when the current page has no elements
     */
    boolean isCurrentPageEmpty();
```

- [ ] **Step 4: Implement it in `AbstractPageSourcePagination`**

After `insertPageItems()` (line ~192) add:

```java
    @Override
    public boolean isCurrentPageEmpty() {
        return this.currentItems.isEmpty();
    }
```

- [ ] **Step 5: Run the test — expect PASS**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationBindingTest#isCurrentPageEmpty_trueForEmptySource_falseWhenItemsPresent`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/Paginator.java \
        platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/engine/AbstractPageSourcePagination.java \
        platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java
git commit -m "feat: add Paginator.isCurrentPageEmpty query"
```

---

### Task 3: `PaginationBinding` — frame-slot resolution, bounds, and empty-state rendering

**Files:**
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBinding.java`
- Test: `platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java`

**Interfaces:**
- Consumes: `PaginationSpec.emptyStateItem()/emptyStateSlots()/loadingSlots()` (Task 1), `Paginator.isCurrentPageEmpty()` (Task 2), existing `applyToSlot`, `frameSupplier`, `targetSlots`, `RenderedItem.ofItem(null)`.
- Produces: `PaginationBinding.frameSlots(): int[]` — the de-duplicated union of empty-state and loading slots (for Task 5 validation). `repaint()` now paints the empty-state frame when the current page is empty.

- [ ] **Step 1: Write the failing tests**

In `PaginationBindingTest.java` add the import `import java.util.concurrent.atomic.AtomicInteger;` and a spec helper plus tests:

```java
    // spec carrying an empty-state frame (LAYOUT_CHAR 'O', normal geometry)
    private static <T> PaginationSpec<T> emptyStateSpec(PaginationItemRenderer<T> renderer,
                                                        PaginationSourceSpec<T> source,
                                                        Function<ViewContext, ItemStack> fallbackItem,
                                                        Function<ViewContext, ItemStack> emptyStateItem,
                                                        int[] emptyStateSlots) {
        return new PaginationSpec<>(PaginationSpec.Geometry.NORMAL, PaginationSpec.Target.LAYOUT_CHAR,
                'O', null, Collections.emptyList(), renderer, fallbackItem, null, source,
                null, null, null, 128, emptyStateItem, emptyStateSlots, new int[0]);
    }

    @Test
    void emptyState_emptyPage_paintsItemAtChosenSlotsAndClearsRestOfLayout() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                emptyStateSpec(amountRenderer(), PaginationSourceSpec.eager(Collections.<Integer>emptyList()),
                        null, ctx -> new ItemStack(Material.BARRIER), new int[]{3}),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(Material.BARRIER, inventory.getItem(3).getType());
        assertNull(inventory.getItem(2), "non-chosen layout slots clear when the page is empty");
        assertNull(inventory.getItem(4));
    }

    @Test
    void emptyState_pageWithItems_paintsNoEmptyState_andKeepsFallbackFill() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                emptyStateSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1, 2)),
                        ctx -> new ItemStack(Material.STONE),
                        ctx -> new ItemStack(Material.BARRIER), new int[]{3}),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(1, inventory.getItem(2).getAmount(), "elements render normally");
        assertEquals(2, inventory.getItem(3).getAmount(), "chosen slot shows the element, not the empty-state");
        assertEquals(Material.STONE, inventory.getItem(4).getType(), "uncovered slot keeps the fallback fill");
    }

    @Test
    void emptyState_evaluatesItemOncePerSlot() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        AtomicInteger counter = new AtomicInteger();
        PaginationBinding binding = new PaginationBinding(
                emptyStateSpec(amountRenderer(), PaginationSourceSpec.eager(Collections.<Integer>emptyList()),
                        null, ctx -> new ItemStack(Material.BARRIER, counter.incrementAndGet()),
                        new int[]{2, 3}),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(1, inventory.getItem(2).getAmount());
        assertEquals(2, inventory.getItem(3).getAmount(), "the factory runs once per chosen slot");
    }

    @Test
    void emptyState_outsideLayoutSlot_paintedWhenEmpty_clearedOnTransitionToItems() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        AtomicReference<List<Integer>> backing = new AtomicReference<>(Collections.<Integer>emptyList());
        PaginationBinding binding = new PaginationBinding(
                emptyStateSpec(amountRenderer(), PaginationSourceSpec.lazy(context -> backing.get()),
                        null, ctx -> new ItemStack(Material.BARRIER), new int[]{7}),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();
        assertEquals(Material.BARRIER, session.inventory().getItem(7).getType(),
                "the outside empty-state slot is painted while empty");

        backing.set(Arrays.asList(1, 2, 3));
        binding.refreshLazy(new PlainViewContextImpl(session, engine));
        binding.repaint();

        assertNull(session.inventory().getItem(7), "the outside empty-state slot clears once items arrive");
        assertEquals(1, session.inventory().getItem(2).getAmount());
    }

    @Test
    void initialize_emptyStateSlotOutOfBounds_throwsNamingSlotAndKind() {
        // layoutConfig is a single row (size 9): slot 9 is out of bounds
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                emptyStateSpec(amountRenderer(), PaginationSourceSpec.eager(Arrays.asList(1)),
                        null, ctx -> new ItemStack(Material.BARRIER), new int[]{9}),
                0, session, engine);

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> binding.initialize(session.layout(), session.effectiveConfig()));
        assertTrue(error.getMessage().contains("slot 9"));
        assertTrue(error.getMessage().contains("empty-state"));
    }
```

- [ ] **Step 2: Run them — expect FAIL**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationBindingTest`
Expected: the new tests FAIL — empty-state is not yet painted (e.g. slot 3 is null / slot 9 does not throw).

- [ ] **Step 3: Add frame-slot fields and resolution in `PaginationBinding`**

Add fields after `targetSlots` (line ~114):

```java
    private int[] targetSlots = new int[0];

    // frame slots resolved at initialize; emptyState/loading slots may lie outside targetSlots
    private int[] emptyStateSlots = new int[0];
    private int[] loadingSlots = new int[0];
    private int[] ownedOutsideSlots = new int[0];
    private Supplier<RenderedItem> emptyStateSupplier;
```

In `initialize(...)`, immediately after `this.targetSlots = resolveTargetSlots(fillLayout, patterns);` (line ~199) add:

```java
        this.targetSlots = resolveTargetSlots(fillLayout, patterns);
        this.emptyStateSlots = spec.emptyStateSlots();
        this.loadingSlots = spec.loadingSlots();
        checkFrameSlotBounds(this.emptyStateSlots, "empty-state", effectiveConfig);
        checkFrameSlotBounds(this.loadingSlots, "loading", effectiveConfig);
        this.ownedOutsideSlots = outsideLayout(this.emptyStateSlots, this.loadingSlots, this.targetSlots);
```

After the existing `this.loadingSupplier = frameSupplier(spec.loadingItem(), "loading item");` (line ~204) add:

```java
        this.emptyStateSupplier = frameSupplier(spec.emptyStateItem(), "empty-state item");
```

- [ ] **Step 4: Add the helper methods and `frameSlots()` accessor**

Add near `checkBounds(...)` (line ~455):

```java
    private void checkFrameSlotBounds(int[] slots, String label, ViewConfig effectiveConfig) {
        int size = effectiveConfig.rows() * Layout.ROW_WIDTH;
        for (int slot : slots) {
            if (slot >= size) {
                throw new ViewConfigurationException("pagination " + label + " slot " + slot
                        + " is out of bounds for a " + effectiveConfig.rows() + "-row view of "
                        + session.registered().type().getName());
            }
        }
    }

    // the frame slots that fall outside the pagination's own layout, de-duplicated in order
    private static int[] outsideLayout(int[] emptyStateSlots, int[] loadingSlots, int[] layout) {
        Set<Integer> layoutSet = new LinkedHashSet<>();
        for (int slot : layout) {
            layoutSet.add(slot);
        }
        LinkedHashSet<Integer> outside = new LinkedHashSet<>();
        for (int slot : emptyStateSlots) {
            if (!layoutSet.contains(slot)) {
                outside.add(slot);
            }
        }
        for (int slot : loadingSlots) {
            if (!layoutSet.contains(slot)) {
                outside.add(slot);
            }
        }
        int[] result = new int[outside.size()];
        int index = 0;
        for (int slot : outside) {
            result[index++] = slot;
        }
        return result;
    }
```

Add the public accessor after `targetSlots()` (line ~258):

```java
    /**
     * Returns the empty-state and loading frame slots this binding paints into, de-duplicated.
     *
     * @return a defensive copy of the frame slots; empty when no frame slots are declared
     */
    public @NotNull int[] frameSlots() {
        LinkedHashSet<Integer> union = new LinkedHashSet<>();
        for (int slot : emptyStateSlots) {
            union.add(slot);
        }
        for (int slot : loadingSlots) {
            union.add(slot);
        }
        int[] result = new int[union.size()];
        int index = 0;
        for (int slot : union) {
            result[index++] = slot;
        }
        return result;
    }
```

- [ ] **Step 5: Make `repaint()` mode-based and add the paint/clear helpers**

Replace the existing `repaint()` body (line ~295):

```java
    public void repaint() {
        // full passes may reach a binding whose initialize aborted mid-open (the abort
        // tears the session down right after); painting nothing is the safe behavior
        if (paginator == null) {
            return;
        }
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }
        boolean applyPlaceholders = session.effectiveConfig().applyPlaceholders();
        boolean loading = paginator.isLoading();
        boolean empty = !loading && paginator.isCurrentPageEmpty();

        if (empty && emptyStateSlots.length > 0) {
            paintFrameMode(inventory, emptyStateSupplier, emptyStateSlots, applyPlaceholders);
        } else {
            paginator.insertPageItems();
            clearSlots(inventory, ownedOutsideSlots, applyPlaceholders);
        }
    }

    // paints the frame item at each frame slot (one supplier call per slot, so each slot gets a
    // fresh ItemStack) and clears every other slot this binding owns (layout + owned-outside)
    private void paintFrameMode(Inventory inventory, Supplier<RenderedItem> supplier,
                                int[] frameSlots, boolean applyPlaceholders) {
        Set<Integer> chosen = new LinkedHashSet<>();
        for (int slot : frameSlots) {
            chosen.add(slot);
        }
        for (int slot : frameSlots) {
            applyToSlot(inventory, slot, supplier.get(), applyPlaceholders);
        }
        for (int slot : targetSlots) {
            if (!chosen.contains(slot)) {
                applyToSlot(inventory, slot, RenderedItem.ofItem(null), applyPlaceholders);
            }
        }
        for (int slot : ownedOutsideSlots) {
            if (!chosen.contains(slot)) {
                applyToSlot(inventory, slot, RenderedItem.ofItem(null), applyPlaceholders);
            }
        }
    }

    private void clearSlots(Inventory inventory, int[] slots, boolean applyPlaceholders) {
        for (int slot : slots) {
            applyToSlot(inventory, slot, RenderedItem.ofItem(null), applyPlaceholders);
        }
    }
```

(No new imports needed: `Inventory`, `Set`, `LinkedHashSet`, `Supplier`, `RenderedItem` are already imported in this file.)

- [ ] **Step 6: Run the tests — expect PASS**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationBindingTest`
Expected: PASS (new empty-state tests and all existing binding tests).

- [ ] **Step 7: Commit**

```bash
git add platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBinding.java \
        platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java
git commit -m "feat: render slot-targeted empty-state frame in PaginationBinding"
```

---

### Task 4: `PaginationBinding` — loading-state slots

**Files:**
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBinding.java`
- Test: `platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java`

**Interfaces:**
- Consumes: `paintFrameMode`, `loadingSupplier`, `loadingSlots`, `ownedOutsideSlots` (Task 3); existing `loadingSupplier` field.
- Produces: `repaint()` paints the loading frame only at `loadingSlots` while loading (when any are declared).

- [ ] **Step 1: Write the failing tests**

In `PaginationBindingTest.java` add a stuck-loading source double and a spec helper plus tests:

```java
    /** a source that never settles and always reports loading */
    static final class LoadingPageSource implements PageSource<Integer> {
        @Override
        public void request(PageRequest request, BiConsumer<PageResult<Integer>, Throwable> onSettle) {
            // never settles: stays loading
        }

        @Override
        public int totalElements() {
            return 0;
        }

        @Override
        public boolean totalsKnown() {
            return false;
        }

        @Override
        public boolean isLoading() {
            return true;
        }

        @Override
        public Throwable lastError() {
            return null;
        }

        @Override
        public List<Integer> elements() {
            return Collections.emptyList();
        }
    }

    // spec carrying a loading frame with explicit slots (LAYOUT_CHAR 'O', normal geometry)
    private static <T> PaginationSpec<T> loadingSlotsSpec(PaginationItemRenderer<T> renderer,
                                                          PaginationSourceSpec<T> source,
                                                          Function<ViewContext, ItemStack> loadingItem,
                                                          int[] loadingSlots) {
        return new PaginationSpec<>(PaginationSpec.Geometry.NORMAL, PaginationSpec.Target.LAYOUT_CHAR,
                'O', null, Collections.emptyList(), renderer, null, loadingItem, source,
                null, null, null, 128, null, new int[0], loadingSlots);
    }

    @Test
    void loading_withSlots_paintsLoadingItemAtChosenSlotsAndClearsRest() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                loadingSlotsSpec(amountRenderer(), PaginationSourceSpec.custom(context -> new LoadingPageSource()),
                        ctx -> new ItemStack(Material.EMERALD), new int[]{3}),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(Material.EMERALD, inventory.getItem(3).getType());
        assertNull(inventory.getItem(2), "non-chosen layout slots clear while loading");
        assertNull(inventory.getItem(4));
    }

    @Test
    void loading_withSlots_paintsOutsideLayoutSlot() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                loadingSlotsSpec(amountRenderer(), PaginationSourceSpec.custom(context -> new LoadingPageSource()),
                        ctx -> new ItemStack(Material.EMERALD), new int[]{7}),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();

        assertEquals(Material.EMERALD, session.inventory().getItem(7).getType());
        assertNull(session.inventory().getItem(2), "layout cleared; only the chosen loading slot painted");
    }

    @Test
    void loading_withoutSlots_stillFillsAllLayoutSlots() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                loadingSlotsSpec(amountRenderer(), PaginationSourceSpec.custom(context -> new LoadingPageSource()),
                        ctx -> new ItemStack(Material.EMERALD), new int[0]),
                0, session, engine);
        binding.initialize(session.layout(), session.effectiveConfig());

        binding.repaint();

        Inventory inventory = session.inventory();
        assertEquals(Material.EMERALD, inventory.getItem(2).getType());
        assertEquals(Material.EMERALD, inventory.getItem(3).getType());
        assertEquals(Material.EMERALD, inventory.getItem(4).getType());
    }

    @Test
    void initialize_loadingSlotOutOfBounds_throwsNamingSlotAndKind() {
        ViewSession session = sessionFor(new PagedView(), layoutConfig());
        PaginationBinding binding = new PaginationBinding(
                loadingSlotsSpec(amountRenderer(), PaginationSourceSpec.custom(context -> new LoadingPageSource()),
                        ctx -> new ItemStack(Material.EMERALD), new int[]{9}),
                0, session, engine);

        ViewConfigurationException error = assertThrows(ViewConfigurationException.class,
                () -> binding.initialize(session.layout(), session.effectiveConfig()));
        assertTrue(error.getMessage().contains("slot 9"));
        assertTrue(error.getMessage().contains("loading"));
    }
```

- [ ] **Step 2: Run them — expect FAIL**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationBindingTest#loading_withSlots_paintsLoadingItemAtChosenSlotsAndClearsRest+loading_withSlots_paintsOutsideLayoutSlot`
Expected: FAIL — without the loading branch, `insertPageItems()` fills all layout slots (slot 2 is EMERALD, not null).

(The `loading_withoutSlots_stillFillsAllLayoutSlots` and `initialize_loadingSlotOutOfBounds...` tests already pass after Task 3 — the bounds check covers `loadingSlots` and the legacy fill path is unchanged; run them too to confirm no regression.)

- [ ] **Step 3: Add the loading-slots branch to `repaint()`**

In `PaginationBinding.repaint()`, insert the loading branch **before** the empty-state branch:

```java
        if (loading && loadingSlots.length > 0) {
            paintFrameMode(inventory, loadingSupplier, loadingSlots, applyPlaceholders);
        } else if (empty && emptyStateSlots.length > 0) {
            paintFrameMode(inventory, emptyStateSupplier, emptyStateSlots, applyPlaceholders);
        } else {
            paginator.insertPageItems();
            clearSlots(inventory, ownedOutsideSlots, applyPlaceholders);
        }
```

- [ ] **Step 4: Run the tests — expect PASS**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationBindingTest`
Expected: PASS (all loading tests and the full binding suite).

- [ ] **Step 5: Commit**

```bash
git add platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBinding.java \
        platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/pagination/PaginationBindingTest.java
git commit -m "feat: render slot-targeted loading frame in PaginationBinding"
```

---

### Task 5: Open-time overlap validation + end-to-end flows

**Files:**
- Modify: `platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java`
- Create: `platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationFrameSlotFlowsTest.java`

**Interfaces:**
- Consumes: `PaginationBinding.frameSlots()` (Task 3), `PaginationBindings.of(session)` (existing), `session.components().componentAt(slot)` (existing), the whole feature from Tasks 1–4.
- Produces: `FirstRenderPhase.validatePaginationOverlap` now also rejects frame slots that collide with a static component or another pagination.

This test file is modeled on the existing `PaginationOpenWiringTest` (same `engine.open` / `sessions.find` / `CapturingHandler` patterns); a new file is used rather than editing `PaginationSampleFlowsTest` so the open-flow template is exactly the known-good one.

- [ ] **Step 1: Write the failing tests (create the file)**

Create `PaginationFrameSlotFlowsTest.java` with the MIT header copied from `PaginationOpenWiringTest.java`, then:

```java
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
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.BukkitPlatformScheduler;
import tech.guilhermekaua.spigotboot.inventoryapi.View;
import tech.guilhermekaua.spigotboot.inventoryapi.config.ViewConfigBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseContext;
import tech.guilhermekaua.spigotboot.inventoryapi.context.CloseReason;
import tech.guilhermekaua.spigotboot.inventoryapi.context.RenderContext;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.engine.phase.FirstRenderPhase;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.registry.ViewRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.render.SlotPainter;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.SessionRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.internal.session.ViewSession;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.Layout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.service.ViewArguments;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationFrameSlotFlowsTest {

    private ServerMock server;
    private Plugin plugin;
    private SessionRegistry sessions;
    private ViewRegistry views;
    private ViewEngine engine;
    private PlayerMock player;

    private final ComponentConflictView componentConflictView = new ComponentConflictView();
    private final TwoPaginationConflictView twoPaginationConflictView = new TwoPaginationConflictView();
    private final EmptyStateView emptyStateView = new EmptyStateView();
    private final AsyncEmptyStateView asyncEmptyStateView = new AsyncEmptyStateView();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("tester");
        sessions = new SessionRegistry();
        views = new ViewRegistry();
        views.register(componentConflictView);
        views.register(twoPaginationConflictView);
        views.register(emptyStateView);
        views.register(asyncEmptyStateView);
        engine = new ViewEngine(plugin, views, sessions,
                new SlotPainter(new NoopPlaceholderApplier()), (p, title) -> {
        }, new BukkitPlatformScheduler(plugin));
    }

    @AfterEach
    void tearDown() {
        server.getScheduler().cancelTasks(plugin);
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- fixture views

    static final class ComponentConflictView extends View {
        CloseReason lastCloseReason;
        final Pagination<String> pagination = this.<String>paginate(Collections.<String>emptyList())
                .layout(Layout.ofSlots(0, 1))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 8)
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("Conflict").rows(1);
        }

        @Override
        protected void onFirstRender(@NotNull RenderContext context) {
            context.slot(8, new ItemStack(Material.STONE)); // collides with the empty-state frame slot
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class TwoPaginationConflictView extends View {
        CloseReason lastCloseReason;
        final Pagination<String> a = this.<String>paginate(Collections.<String>emptyList())
                .layout(Layout.ofSlots(0, 1))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 4) // 4 is pagination b's target
                .build();
        final Pagination<String> b = this.<String>paginate(Collections.singletonList("x"))
                .layout(Layout.ofSlots(3, 4))
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("TwoPag").rows(1);
        }

        @Override
        protected void onClose(@NotNull CloseContext context) {
            lastCloseReason = context.reason();
        }
    }

    static final class EmptyStateView extends View {
        final Pagination<String> pagination = this.<String>paginate(Collections.<String>emptyList())
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 4)
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("EmptyState").layout("OOOOOOOOO");
        }
    }

    static final class AsyncEmptyStateView extends View {
        final Map<UUID, CompletableFuture<PageResult<String>>> futures = new ConcurrentHashMap<>();
        final Pagination<String> pagination = this.<String>paginateAsync(request -> {
            CompletableFuture<PageResult<String>> future = new CompletableFuture<>();
            futures.put(request.playerId(), future);
            return future;
        })
                .itemRenderer((ctx, item, index, value) -> item.item(new ItemStack(Material.PAPER)))
                .loadingItem(ctx -> new ItemStack(Material.EMERALD), 4)
                .emptyStateItem(ctx -> new ItemStack(Material.BARRIER), 4)
                .build();

        @Override
        protected void onInit(@NotNull ViewConfigBuilder config) {
            config.title("AsyncEmpty").layout("OOOOOOOOO");
        }
    }

    // ---------------------------------------------------------------- tests

    @Test
    void emptyStateSlotOnComponent_abortsOpenFailed() {
        Logger logger = Logger.getLogger(FirstRenderPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, ComponentConflictView.class, ViewArguments.empty());

            assertFalse(sessions.find(player.getUniqueId()).isPresent(),
                    "the overlap must abort the open, registering nothing");
            assertEquals(CloseReason.OPEN_FAILED, componentConflictView.lastCloseReason);
            assertTrue(handler.hasThrownContaining("a pagination frame item"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void frameSlotOnAnotherPagination_abortsOpenFailed() {
        Logger logger = Logger.getLogger(FirstRenderPhase.class.getName());
        CapturingHandler handler = new CapturingHandler();
        logger.addHandler(handler);
        try {
            engine.open(player, TwoPaginationConflictView.class, ViewArguments.empty());

            assertFalse(sessions.find(player.getUniqueId()).isPresent());
            assertEquals(CloseReason.OPEN_FAILED, twoPaginationConflictView.lastCloseReason);
            assertTrue(handler.hasThrownContaining("two paginations"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void eagerEmptySource_paintsEmptyStateOnOpen() {
        engine.open(player, EmptyStateView.class, ViewArguments.empty());

        ViewSession session = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        Inventory inventory = session.inventory();
        assertEquals(Material.BARRIER, inventory.getItem(4).getType());
        assertNull(inventory.getItem(0), "the rest of the layout is empty");
        assertNull(inventory.getItem(8));
    }

    @Test
    void async_showsLoadingSlot_thenEmptyStateAfterSettlingEmpty() {
        engine.open(player, AsyncEmptyStateView.class, ViewArguments.empty());

        ViewSession session = sessions.find(player.getUniqueId()).orElseThrow(IllegalStateException::new);
        Inventory inventory = session.inventory();
        // in flight: the loading slot shows the loading item, the rest of the layout is empty
        assertEquals(Material.EMERALD, inventory.getItem(4).getType());
        assertNull(inventory.getItem(0));

        // settle empty on the main thread: the inline settle repaints
        asyncEmptyStateView.futures.get(player.getUniqueId())
                .complete(PageResult.of(Collections.<String>emptyList(), 0));

        assertEquals(Material.BARRIER, inventory.getItem(4).getType(),
                "settled-empty replaces the loading frame with the empty-state frame");
        assertNull(inventory.getItem(0));
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        boolean hasThrownContaining(String fragment) {
            for (LogRecord record : records) {
                Throwable thrown = record.getThrown();
                if (thrown != null && thrown.getMessage() != null
                        && thrown.getMessage().contains(fragment)) {
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

- [ ] **Step 2: Run them — expect the overlap tests to FAIL**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationFrameSlotFlowsTest`
Expected: `eagerEmptySource_...` and `async_...` PASS (the feature works end-to-end already), but the two overlap tests FAIL — `validatePaginationOverlap` does not yet inspect frame slots, so the conflicting views open instead of aborting.

- [ ] **Step 3: Extend `validatePaginationOverlap` in `FirstRenderPhase`**

Add the import `import java.util.List;` to `FirstRenderPhase.java`. Replace the `validatePaginationOverlap` method (line ~142):

```java
    // overlap validation (§5.3/§6): a slot cannot be both statically bound and a pagination
    // target; frame slots (empty-state / loading) must not collide with a component or another
    // pagination. runs inside the try so the failure flows into the OPEN_FAILED abort path.
    private void validatePaginationOverlap(ViewSession session) {
        List<PaginationBinding> bindings = PaginationBindings.of(session);
        for (PaginationBinding binding : bindings) {
            for (int slot : binding.targetSlots()) {
                if (session.components().componentAt(slot) != null) {
                    throw new ViewConfigurationException(
                            "slot " + slot + " is bound to both a component and pagination");
                }
            }
            for (int slot : binding.frameSlots()) {
                if (session.components().componentAt(slot) != null) {
                    throw new ViewConfigurationException(
                            "slot " + slot + " is bound to both a component and a pagination frame item");
                }
                for (PaginationBinding other : bindings) {
                    if (other == binding) {
                        continue;
                    }
                    if (contains(other.targetSlots(), slot) || contains(other.frameSlots(), slot)) {
                        throw new ViewConfigurationException(
                                "slot " + slot + " is bound to two paginations");
                    }
                }
            }
        }
    }

    private static boolean contains(int[] slots, int value) {
        for (int slot : slots) {
            if (slot == value) {
                return true;
            }
        }
        return false;
    }
```

- [ ] **Step 4: Run the tests — expect PASS**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test -Danimal.sniffer.skip=true -Dtest=PaginationFrameSlotFlowsTest`
Expected: PASS (all four tests).

- [ ] **Step 5: Run the full module suite (no skip) to confirm no regressions**

Run: `mvnw.cmd -q -pl platform-spigot/inventory-api/api -am test`
Expected: BUILD SUCCESS. (On a fresh clone, if animal-sniffer fails because the 1.8 signature jar is missing, first run `mvnw.cmd -pl spigot-api-1_8-signature install`, then re-run.)

- [ ] **Step 6: Commit**

```bash
git add platform-spigot/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/phase/FirstRenderPhase.java \
        platform-spigot/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/internal/engine/PaginationFrameSlotFlowsTest.java
git commit -m "feat: reject frame slots overlapping a component or another pagination at open"
```

---

## Self-Review

**1. Spec coverage**
- §4.1 `emptyStateItem` API → Task 1 (interface + impl + tests). ✓
- §4.2 `loadingItem` overload (async-only) → Task 1 (impl + async-only test). ✓
- §5 precedence (loading-slots → empty-slots → normal; empty-state suppresses fallback fill) → Task 3 (`emptyState_pageWithItems_...`) + Task 4 (loading branch ordering). ✓
- §5 decorative/inert frame items, no click change → relies on existing `applyToSlot` frame path (no `elementComponents` entry); no production change needed; covered implicitly by render tests. ✓
- §5 per-slot evaluation → Task 3 (`emptyState_evaluatesItemOncePerSlot`). ✓
- §6.1 spec carries values → Task 1. ✓
- §6.3 `isCurrentPageEmpty()` → Task 2. ✓
- §6.4 binding render + `ownedOutsideSlots` clear + bounds + `frameSlots()` → Tasks 3 & 4. ✓
- §6.5 component + cross-pagination overlap → Task 5. ✓
- §7 backward compat (`loadingItem(fn)` fill-all, `fallbackItem` intact) → Task 1 (`loadingItem_withoutSlots_resetsToFillAll`), Task 3 (`emptyState_pageWithItems_...`), Task 4 (`loading_withoutSlots_stillFillsAllLayoutSlots`). ✓
- §8 tests (builder, binding, overlap, integration) → Tasks 1, 3, 4, 5. ✓

**2. Placeholder scan** — No `TBD`/`TODO`/"handle edge cases"/"similar to Task N"; every code step shows complete code. ✓

**3. Type consistency** — `emptyStateSlots`/`loadingSlots`/`emptyStateItem` names and `int[]`/`Function<ViewContext, ItemStack>` types are identical across `PaginationSpec`, `PaginationBuilderImpl`, and `PaginationBinding`. `frameSlots()`, `paintFrameMode`, `clearSlots`, `outsideLayout`, `checkFrameSlotBounds`, `validatedFrameSlots`, `isCurrentPageEmpty`, `contains` are each defined once and referenced consistently. The `PaginationSpec` constructor's three trailing params (`emptyStateItem, emptyStateSlots, loadingSlots`) match every call site (`build()`, `specOf`, `emptyStateSpec`, `loadingSlotsSpec`). ✓

## Execution Handoff

(Filled in by the brainstorming/writing-plans flow after the plan is saved.)

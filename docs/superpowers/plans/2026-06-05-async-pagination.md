# Async Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add async (e.g. DB-backed) page loading to all three inventory-api pagination types via a `PageSource<T>` strategy, with loading-state rendering, error rollback, opt-in timeout and TTL cache.

**Architecture:** Extract "where do page items come from" into a `PageSource<T>` strategy. `EagerPageSource` reproduces today's in-memory behavior as a synchronous degenerate case; `AsyncPageSource` implements all async machinery (request ids, at-most-once settle, timeout, cache) exactly once, pure-Java testable. The three pagination impls delegate to it and render a `currentItems` snapshot. Settles hop to the main thread unless the inventory opted into `tickAsync`. Spec: `docs/superpowers/specs/2026-06-05-async-pagination-design.md` — read it before starting.

**Tech Stack:** Java 8 main sources (`pom` sets `-source/target 1.8` — NO records, NO `List.of()`, NO `orTimeout`; tests compile at 17), Lombok, JUnit 5, Mockito, MockBukkit.

**Build environment:** Build with **JDK 21** (`$env:JAVA_HOME` must point to a JDK 21 install; the shell-default JDK 25 crashes Lombok 1.18.36 with `TypeTag :: UNKNOWN`). Run module tests with:

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test
```

**Conventions (CLAUDE.md):** every new file starts with the MIT license header (copy verbatim from `Pagination.java` lines 1-22); complete Javadoc with `@param`/`@return`/`@throws` on all public/protected APIs; normal comments start lowercase; no fully-qualified inline types; 4-space indent, same-line braces.

---

## File Structure

New package `tech.guilhermekaua.spigotboot.inventoryapi.pagination.source` (base: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/`):

| File | Responsibility |
|---|---|
| Create `pagination/source/PageRequest.java` | immutable request: page, pageSize, offset, viewer |
| Create `pagination/source/PageResult.java` | immutable result: items + totalElements |
| Create `pagination/source/AsyncPageSupplier.java` | user-facing functional interface → `CompletableFuture<PageResult<T>>` |
| Create `pagination/source/PaginationErrorCallback.java` | functional interface `(PageRequest, Throwable)` |
| Create `pagination/source/PageSource.java` | strategy interface |
| Create `pagination/source/SettleDispatcher.java` | functional interface deciding which thread runs a settle |
| Create `pagination/source/BukkitSettleDispatcher.java` | main-thread hop unless `tickAsync`/already primary |
| Create `pagination/source/EagerPageSource.java` | in-memory source, synchronous settles |
| Create `pagination/source/AsyncPageSource.java` | async machinery: ids, lock, timeout, cache |
| Create `pagination/builder/AsyncPaginationOptions.java` | options object for the single `async(...)` builder method |
| Modify `pagination/Pagination.java` | add default `isLoading()`, `lastError()`, `getTotalElements()`, `refresh()`; Javadoc updates |
| Modify `pagination/impl/NormalPagination.java` | delegate to PageSource; snapshot rendering; rollback |
| Modify `pagination/impl/ScrollPagination.java` | same + `getPageOfIndex` window fix |
| Modify `pagination/impl/PatternPagination.java` | same + closed-form cycle math + pattern rollback |
| Modify `pagination/builder/NormalPaginationBuilder.java` | `async(Consumer<AsyncPaginationOptions<T>>)` |
| Modify `pagination/builder/ScrollPaginationBuilder.java` | same |
| Modify `pagination/builder/PatternPaginationBuilder.java` | same |
| Modify `listener/CustomInventoryListener.java` | close-event identity guard (prerequisite fix) |
| Create test `pagination/source/PageResultTest.java` | validation + defensive copy |
| Create test `pagination/source/EagerPageSourceTest.java` | clamping edges |
| Create test `pagination/source/AsyncPageSourceTest.java` | machinery, timeout, cache (pure JUnit) |
| Create test `listener/CustomInventoryListenerCloseTest.java` | identity guard |
| Create test `pagination/impl/AsyncPaginationIntegrationTest.java` | MockBukkit cross-type async behavior |
| Modify test `pagination/impl/PatternPaginationTest.java` | stays green (pins refactor) |
| Create `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleAsyncPagedInventory.java` | manual-verification sample |

Tasks are ordered so every commit compiles and the suite stays green.

---

### Task 1: Close-listener identity guard (prerequisite fix)

Menu→menu navigation fires the OLD container's `InventoryCloseEvent` synchronously inside `player.openInventory`, after `defaultOpenInventory` already registered the NEW viewer — unregistering it. Fatal under async: settles no-op via `findViewer` → permanent loading frame.

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/listener/CustomInventoryListener.java:60-69`
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/listener/CustomInventoryListenerCloseTest.java`

- [ ] **Step 1.1: Write the failing test**

MockBukkit cannot reproduce the close-on-reopen sequence (`PlayerMock.openInventory` does not fire the previous container's close event), so test the listener directly with hand-fired events.

```java
/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.inventoryapi.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomInventoryListenerCloseTest {

    private ViewerRegistry viewerRegistry;
    private CustomInventoryListener listener;
    private PlayerMock player;
    private Inventory viewerInventory;
    private Inventory unrelatedInventory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewerRegistry = new ViewerRegistry();
        listener = new CustomInventoryListener(viewerRegistry);
        player = MockBukkit.getMock().addPlayer("tester");
        viewerInventory = Bukkit.createInventory(null, 27);
        unrelatedInventory = Bukkit.createInventory(null, 27);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Viewer registerViewer(Inventory inventory) {
        Viewer viewer = mock(Viewer.class);
        when(viewer.getUniqueId()).thenReturn(player.getUniqueId());
        lenient().when(viewer.getInventory()).thenReturn(inventory);
        viewerRegistry.registerViewer(viewer);
        return viewer;
    }

    private InventoryCloseEvent closeEventFor(Inventory inventory) {
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inventory);
        return new InventoryCloseEvent(view);
    }

    @Test
    void close_ofUnrelatedInventory_keepsViewerRegistered() {
        registerViewer(viewerInventory);

        listener.onInventoryClose(closeEventFor(unrelatedInventory));

        assertTrue(viewerRegistry.findViewer(player).isPresent(),
                "closing a different container must not unregister the viewer");
    }

    @Test
    void close_ofViewerInventory_unregistersViewer() {
        registerViewer(viewerInventory);

        listener.onInventoryClose(closeEventFor(viewerInventory));

        assertFalse(viewerRegistry.findViewer(player).isPresent());
    }

    @Test
    void close_withNoRegisteredViewer_doesNothing() {
        listener.onInventoryClose(closeEventFor(viewerInventory));

        assertFalse(viewerRegistry.findViewer(player).isPresent());
    }
}
```

Note: `InventoryCloseEvent.getInventory()` delegates to `view.getTopInventory()`. If `UUID` ends up unused after writing, drop the import.

- [ ] **Step 1.2: Run the test to verify it fails**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=CustomInventoryListenerCloseTest"
```

Expected: `close_ofUnrelatedInventory_keepsViewerRegistered` FAILS (current listener unregisters unconditionally). The other two pass.

- [ ] **Step 1.3: Implement the identity guard**

In `CustomInventoryListener.java`, replace the `onInventoryClose` body:

```java
@EventHandler
public void onInventoryClose(InventoryCloseEvent event) {
    Player player = (Player) event.getPlayer();

    Viewer viewer = viewerRegistry.findViewer(player).orElse(null);
    if (viewer == null) {
        return;
    }
    // a close event for a previous container (fired synchronously while opening a new custom
    // inventory) must not unregister the viewer of the inventory that is being opened
    if (viewer.getInventory() != event.getInventory()) {
        return;
    }

    viewerRegistry.unregisterViewer(viewer);
    CustomInventoryCloseEvent closeEvent = new CustomInventoryCloseEvent(viewer, event);
    Bukkit.getPluginManager().callEvent(closeEvent);
}
```

- [ ] **Step 1.4: Run the test to verify it passes, then the module suite**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: all green.

- [ ] **Step 1.5: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/listener/CustomInventoryListener.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/listener/CustomInventoryListenerCloseTest.java
git commit -m "fix(inventory-api): only unregister viewer when its own inventory closes"
```

---

### Task 2: `PageRequest` + `PageResult`

**Files:**
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PageRequest.java`
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PageResult.java`
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/PageResultTest.java`

- [ ] **Step 2.1: Write the failing test**

```java
// MIT header (copy from Pagination.java lines 1-22)
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageResultTest {

    @Test
    void of_copiesItemsDefensively() {
        List<String> items = new ArrayList<>(Arrays.asList("a", "b"));
        PageResult<String> result = PageResult.of(items, 10);

        items.add("c");

        assertEquals(2, result.getItems().size());
        assertEquals(10, result.getTotalElements());
    }

    @Test
    void of_returnsUnmodifiableItems() {
        PageResult<String> result = PageResult.of(Arrays.asList("a"), 1);

        assertThrows(UnsupportedOperationException.class, () -> result.getItems().add("b"));
    }

    @Test
    void of_rejectsNullItems() {
        assertThrows(NullPointerException.class, () -> PageResult.of(null, 0));
    }

    @Test
    void of_rejectsNegativeTotal() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(Arrays.asList("a"), -1));
    }
}
```

- [ ] **Step 2.2: Run the test to verify it fails to compile**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=PageResultTest"
```

Expected: COMPILATION ERROR (`PageResult` does not exist).

- [ ] **Step 2.3: Implement both classes**

`PageRequest.java`:

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

/**
 * Immutable description of one page load issued by a paginator.
 *
 * <p>{@link #getOffset()} and {@link #getPageSize()} are the <strong>authoritative query
 * bounds</strong> — a backing store should fetch with {@code LIMIT pageSize OFFSET offset}.
 * {@link #getPage()} is informational only: scroll paginators advance one element per page, so
 * deriving the offset as {@code (page - 1) * pageSize} is wrong for them.
 */
@Getter
@RequiredArgsConstructor
public final class PageRequest {

    /**
     * The 1-indexed page being requested.
     */
    private final int page;

    /**
     * The maximum number of items the requested page can display.
     */
    private final int pageSize;

    /**
     * The global element offset of the first item on the requested page.
     */
    private final int offset;

    /**
     * The viewer the page is being loaded for.
     */
    private final Viewer viewer;
}
```

`PageResult.java`:

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of one page load: the page's items plus the total number of elements in the
 * backing store, which drives {@code getTotalPages()} and navigation clamping.
 */
@Getter
public final class PageResult<T> {

    /**
     * The items of the loaded page, as an unmodifiable defensive copy.
     */
    private final List<T> items;

    /**
     * The total number of elements in the backing store.
     */
    private final int totalElements;

    private PageResult(List<T> items, int totalElements) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.totalElements = totalElements;
    }

    /**
     * Creates a page result.
     *
     * @param items         the page's items; copied defensively, must contain at most the
     *                      requested page size (oversized results are truncated with a warning)
     * @param totalElements the total number of elements in the backing store
     * @param <T>           the element type
     * @return the immutable result
     * @throws NullPointerException     if {@code items} is null
     * @throws IllegalArgumentException if {@code totalElements} is negative
     */
    public static <T> PageResult<T> of(List<T> items, int totalElements) {
        Objects.requireNonNull(items, "items is required.");
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements cannot be negative.");
        }
        return new PageResult<>(items, totalElements);
    }
}
```

- [ ] **Step 2.4: Run the test to verify it passes**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=PageResultTest"
```

Expected: 4 tests PASS.

- [ ] **Step 2.5: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/ modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/
git commit -m "feat(inventory-api): add PageRequest and PageResult for async pagination"
```

---

### Task 3: Source-layer interfaces

**Files:**
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSupplier.java`
- Create: `.../pagination/source/PaginationErrorCallback.java`
- Create: `.../pagination/source/PageSource.java`
- Create: `.../pagination/source/SettleDispatcher.java`

No behavior to test yet (interfaces only); the compile gate is the existing suite.

- [ ] **Step 3.1: Create the four interfaces**

`AsyncPageSupplier.java`:

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import java.util.concurrent.CompletableFuture;

/**
 * Loads one page of elements asynchronously, typically from a database.
 *
 * <p>Called on every page change unless a fresh cached entry exists. The returned future may
 * complete on any thread; the framework applies the result on the appropriate thread for the
 * owning inventory. Return at most {@link PageRequest#getPageSize()} items — oversized results
 * are truncated with a warning.
 *
 * <p>Returning {@code null} is treated as a failed load. A future that never completes leaves the
 * paginator loading until a superseding navigation or the configured request timeout recovers it.
 */
@FunctionalInterface
public interface AsyncPageSupplier<T> {

    /**
     * Loads the requested page.
     *
     * @param request the page being requested, never null
     * @return a future completing with the page's items and total element count
     */
    CompletableFuture<PageResult<T>> load(PageRequest request);
}
```

`PaginationErrorCallback.java`:

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

/**
 * Invoked when an async page load fails (exceptional completion, {@code null} future, synchronous
 * throw, or timeout). Runs on the same thread that applies the settle — the main server thread
 * unless the owning inventory opted into async ticking.
 */
@FunctionalInterface
public interface PaginationErrorCallback {

    /**
     * Handles a failed page load.
     *
     * @param request the request that failed, never null
     * @param error   the failure cause, never null
     */
    void onError(PageRequest request, Throwable error);
}
```

`SettleDispatcher.java`:

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

/**
 * Decides which thread runs the settle (state application + callback delivery) of an
 * asynchronously completed page load. Synchronous settles never pass through a dispatcher.
 */
@FunctionalInterface
public interface SettleDispatcher {

    /**
     * Runs the settle task on the appropriate thread for the given request.
     *
     * @param request the request being settled, never null
     * @param task    the settle work, never null
     */
    void dispatch(PageRequest request, Runnable task);

    /**
     * @return a dispatcher that runs settles on the calling thread; intended for tests
     */
    static SettleDispatcher inline() {
        return (request, task) -> task.run();
    }
}
```

`PageSource.java`:

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Strategy answering "where do page items come from" for a paginator. {@link EagerPageSource}
 * serves an in-memory list synchronously; {@link AsyncPageSource} loads pages on demand through
 * an {@link AsyncPageSupplier}.
 */
public interface PageSource<T> {

    /**
     * Requests the page described by {@code request}.
     *
     * <p>Contract for implementations:
     * <ul>
     *   <li>{@code onSettle} is invoked <strong>at most once</strong> per request, with exactly
     *       one of result/error non-null — and possibly never (a superseded request).</li>
     *   <li>It MAY be invoked synchronously inside this method and MAY be invoked on any
     *       thread.</li>
     *   <li>A request superseded by a newer one MUST NOT be settled.</li>
     *   <li>{@link #isLoading()} must return {@code false} once the latest request settled.</li>
     * </ul>
     *
     * @param request  the page to load, never null
     * @param onSettle receives the result or the failure, never null
     */
    void request(PageRequest request, BiConsumer<PageResult<T>, Throwable> onSettle);

    /**
     * @return the total number of elements in the backing store; {@code 0} until known for
     * async sources
     */
    int totalElements();

    /**
     * @return {@code true} once {@link #totalElements()} reflects the real store size — always
     * for eager sources, after the first successful settle for async sources
     */
    boolean totalsKnown();

    /**
     * @return {@code true} while the latest request has not settled
     */
    boolean isLoading();

    /**
     * @return the failure of the most recently settled request, or {@code null}; cleared when a
     * new request is dispatched
     */
    Throwable lastError();

    /**
     * @return the elements currently held locally — the full backing list for eager sources, the
     * items of the most recently applied page for async sources
     */
    List<T> elements();

    /**
     * Clears any cached pages. No-op by default.
     */
    default void invalidate() {
    }
}
```

- [ ] **Step 3.2: Verify the module compiles**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: BUILD SUCCESS (note: `PageSource` Javadoc references `EagerPageSource`/`AsyncPageSource` which do not exist yet — Javadoc `{@link}` to a missing class is NOT a compile error in normal builds, but if the build fails on it, temporarily write those two names as plain text and restore the links in Tasks 4-5).

- [ ] **Step 3.3: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/
git commit -m "feat(inventory-api): add PageSource strategy interfaces for async pagination"
```

---

### Task 4: `EagerPageSource`

**Files:**
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/EagerPageSource.java`
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/EagerPageSourceTest.java`

- [ ] **Step 4.1: Write the failing test**

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EagerPageSourceTest {

    private static PageRequest request(int page, int pageSize, int offset) {
        return new PageRequest(page, pageSize, offset, null);
    }

    private static <T> PageResult<T> settleOf(EagerPageSource<T> source, PageRequest request) {
        AtomicReference<PageResult<T>> settled = new AtomicReference<>();
        source.request(request, (result, error) -> settled.set(result));
        return settled.get();
    }

    @Test
    void request_settlesSynchronouslyWithSlice() {
        EagerPageSource<Integer> source = new EagerPageSource<>(Arrays.asList(1, 2, 3, 4, 5));

        PageResult<Integer> result = settleOf(source, request(2, 2, 2));

        assertEquals(Arrays.asList(3, 4), result.getItems());
        assertEquals(5, result.getTotalElements());
    }

    @Test
    void request_partialLastPage_clampsEnd() {
        EagerPageSource<Integer> source = new EagerPageSource<>(Arrays.asList(1, 2, 3, 4, 5));

        PageResult<Integer> result = settleOf(source, request(2, 3, 3));

        assertEquals(Arrays.asList(4, 5), result.getItems());
    }

    @Test
    void request_offsetBeyondSize_returnsEmptyItemsWithRealTotal() {
        EagerPageSource<Integer> source = new EagerPageSource<>(Arrays.asList(1, 2, 3));

        PageResult<Integer> result = settleOf(source, request(5, 3, 12));

        assertTrue(result.getItems().isEmpty());
        assertEquals(3, result.getTotalElements());
    }

    @Test
    void request_offsetEqualToSize_returnsEmptyItems() {
        EagerPageSource<Integer> source = new EagerPageSource<>(Arrays.asList(1, 2, 3));

        assertTrue(settleOf(source, request(2, 3, 3)).getItems().isEmpty());
    }

    @Test
    void empty_hasNoElementsAndKnownTotals() {
        EagerPageSource<Integer> source = EagerPageSource.empty();

        assertEquals(0, source.totalElements());
        assertTrue(source.totalsKnown());
        assertFalse(source.isLoading());
        assertNull(source.lastError());
        assertTrue(source.elements().isEmpty());
    }

    @Test
    void constructor_copiesListDefensively() {
        List<Integer> backing = new ArrayList<>(Arrays.asList(1, 2));
        EagerPageSource<Integer> source = new EagerPageSource<>(backing);

        backing.add(3);

        assertEquals(2, source.totalElements());
    }
}
```

- [ ] **Step 4.2: Run the test to verify it fails to compile**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=EagerPageSourceTest"
```

Expected: COMPILATION ERROR (`EagerPageSource` does not exist).

- [ ] **Step 4.3: Implement `EagerPageSource`**

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * In-memory {@link PageSource}: serves slices of a fixed list and settles every request
 * synchronously on the calling thread. Totals are always known, loading is never observable and
 * requests never fail.
 */
public final class EagerPageSource<T> implements PageSource<T> {

    private final List<T> elements;

    /**
     * Creates an eager source over a defensive copy of the given list.
     *
     * @param elements the backing elements, not null
     * @throws NullPointerException if {@code elements} is null
     */
    public EagerPageSource(List<T> elements) {
        Objects.requireNonNull(elements, "elements is required.");
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    /**
     * @param <T> the element type
     * @return an eager source over an empty list
     */
    public static <T> EagerPageSource<T> empty() {
        return new EagerPageSource<>(Collections.emptyList());
    }

    @Override
    public void request(PageRequest request, BiConsumer<PageResult<T>, Throwable> onSettle) {
        // clamp BOTH ends: a stale currentPage left over from a larger previous source must
        // yield an empty page, not an IndexOutOfBoundsException
        int from = Math.min(Math.max(0, request.getOffset()), elements.size());
        int to = Math.min(from + request.getPageSize(), elements.size());
        onSettle.accept(PageResult.of(elements.subList(from, to), elements.size()), null);
    }

    @Override
    public int totalElements() {
        return elements.size();
    }

    @Override
    public boolean totalsKnown() {
        return true;
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
    public List<T> elements() {
        return elements;
    }
}
```

- [ ] **Step 4.4: Run the test to verify it passes**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=EagerPageSourceTest"
```

Expected: 6 tests PASS.

- [ ] **Step 4.5: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/EagerPageSource.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/EagerPageSourceTest.java
git commit -m "feat(inventory-api): add EagerPageSource in-memory page source"
```

---

### Task 5: `AsyncPageSource` — core machinery

The heart of the feature. All state transitions happen inside one lock; a single `AtomicLong` is the only source of truth for request ids (review found volatile-only check-then-act could render a stale page or stick `isLoading=true` forever). Timeout and cache land in Tasks 6-7 — this task creates the full constructor surface but leaves those paths inert (`requestTimeout`/`cacheTtl` null).

**Files:**
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSource.java`
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java`

- [ ] **Step 5.1: Write the failing tests (core behaviors)**

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncPageSourceTest {

    private static PageRequest request(int page) {
        return new PageRequest(page, 3, (page - 1) * 3, null);
    }

    private static <T> AsyncPageSource<T> source(AsyncPageSupplier<T> supplier) {
        return new AsyncPageSource<>(supplier, null, null, null, 128, SettleDispatcher.inline());
    }

    @Test
    void completedFuture_settlesInlineAndPublishesState() {
        AsyncPageSource<Integer> source = source(req ->
                CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1, 2, 3), 9)));
        AtomicReference<PageResult<Integer>> settled = new AtomicReference<>();

        source.request(request(1), (result, error) -> settled.set(result));

        assertNotNull(settled.get());
        assertEquals(Arrays.asList(1, 2, 3), source.elements());
        assertEquals(9, source.totalElements());
        assertTrue(source.totalsKnown());
        assertFalse(source.isLoading());
        assertNull(source.lastError());
    }

    @Test
    void pendingFuture_reportsLoadingUntilCompletion() {
        CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
        AsyncPageSource<Integer> source = source(req -> future);
        AtomicReference<PageResult<Integer>> settled = new AtomicReference<>();

        source.request(request(1), (result, error) -> settled.set(result));

        assertTrue(source.isLoading());
        assertFalse(source.totalsKnown());
        assertNull(settled.get());

        future.complete(PageResult.of(Arrays.asList(1), 1));

        assertFalse(source.isLoading());
        assertEquals(Arrays.asList(1), settled.get().getItems());
    }

    @Test
    void staleCompletion_afterNewerRequest_isDiscardedEntirely() {
        List<CompletableFuture<PageResult<Integer>>> futures = new ArrayList<>();
        AsyncPageSource<Integer> source = source(req -> {
            CompletableFuture<PageResult<Integer>> f = new CompletableFuture<>();
            futures.add(f);
            return f;
        });
        AtomicInteger settleCount = new AtomicInteger();

        source.request(request(1), (result, error) -> settleCount.incrementAndGet());
        source.request(request(2), (result, error) -> settleCount.incrementAndGet());

        // newer request completes first
        futures.get(1).complete(PageResult.of(Arrays.asList(4, 5, 6), 9));
        assertEquals(Arrays.asList(4, 5, 6), source.elements());
        assertFalse(source.isLoading());

        // the stale completion must not overwrite state, settle, or resurrect loading
        futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9));
        assertEquals(Arrays.asList(4, 5, 6), source.elements());
        assertFalse(source.isLoading());
        assertEquals(1, settleCount.get());
    }

    @Test
    void supersededRequest_doesNotClearLoadingOfNewerInFlightRequest() {
        List<CompletableFuture<PageResult<Integer>>> futures = new ArrayList<>();
        AsyncPageSource<Integer> source = source(req -> {
            CompletableFuture<PageResult<Integer>> f = new CompletableFuture<>();
            futures.add(f);
            return f;
        });

        source.request(request(1), (result, error) -> { });
        source.request(request(2), (result, error) -> { });

        futures.get(0).complete(PageResult.of(Arrays.asList(1), 9));

        assertTrue(source.isLoading(), "newer request still in flight; stale settle must not clear loading");
    }

    @Test
    void failedFuture_capturesErrorAndKeepsLastGoodElements() {
        List<CompletableFuture<PageResult<Integer>>> futures = new ArrayList<>();
        AsyncPageSource<Integer> source = source(req -> {
            CompletableFuture<PageResult<Integer>> f = new CompletableFuture<>();
            futures.add(f);
            return f;
        });
        AtomicReference<Throwable> settledError = new AtomicReference<>();

        source.request(request(1), (result, error) -> { });
        futures.get(0).complete(PageResult.of(Arrays.asList(1, 2), 6));

        source.request(request(2), (result, error) -> settledError.set(error));
        RuntimeException boom = new RuntimeException("db down");
        futures.get(1).completeExceptionally(boom);

        assertEquals(boom, settledError.get().getCause() == null ? settledError.get() : settledError.get().getCause());
        assertEquals(Arrays.asList(1, 2), source.elements(), "last good elements survive a failure");
        assertFalse(source.isLoading());
        assertNotNull(source.lastError());
    }

    @Test
    void newRequest_clearsLastError() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = source(req -> calls.incrementAndGet() == 1
                ? failedFuture(new RuntimeException("boom"))
                : CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1)));

        source.request(request(1), (result, error) -> { });
        assertNotNull(source.lastError());

        source.request(request(1), (result, error) -> { });
        assertNull(source.lastError());
    }

    @Test
    void nullFuture_settlesAsFailure() {
        AsyncPageSource<Integer> source = source(req -> null);
        AtomicReference<Throwable> settledError = new AtomicReference<>();

        source.request(request(1), (result, error) -> settledError.set(error));

        assertInstanceOf(IllegalStateException.class, settledError.get());
        assertFalse(source.isLoading());
        assertNotNull(source.lastError());
    }

    @Test
    void synchronousSupplierThrow_settlesAsFailure() {
        RuntimeException boom = new RuntimeException("sync boom");
        AsyncPageSource<Integer> source = source(req -> {
            throw boom;
        });
        AtomicReference<Throwable> settledError = new AtomicReference<>();

        source.request(request(1), (result, error) -> settledError.set(error));

        assertEquals(boom, settledError.get());
        assertFalse(source.isLoading());
    }

    @Test
    void errorCallback_isInvokedOnFailure() {
        AtomicReference<Throwable> callbackError = new AtomicReference<>();
        PaginationErrorCallback callback = (request, error) -> callbackError.set(error);
        AsyncPageSource<Integer> source = new AsyncPageSource<>(
                req -> failedFuture(new RuntimeException("boom")),
                callback, null, null, 128, SettleDispatcher.inline());

        source.request(request(1), (result, error) -> { });

        assertNotNull(callbackError.get());
    }

    @Test
    void oversizedResult_isTruncatedToPageSize() {
        AsyncPageSource<Integer> source = source(req ->
                CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1, 2, 3, 4, 5), 5)));
        AtomicReference<PageResult<Integer>> settled = new AtomicReference<>();

        source.request(request(1), (result, error) -> settled.set(result)); // pageSize = 3

        assertEquals(Arrays.asList(1, 2, 3), settled.get().getItems());
        assertEquals(Arrays.asList(1, 2, 3), source.elements());
    }

    @Test
    void racingRequests_fromTwoThreads_latestDispatchAlwaysWins() throws Exception {
        for (int round = 0; round < 50; round++) {
            List<CompletableFuture<PageResult<Integer>>> futures =
                    java.util.Collections.synchronizedList(new ArrayList<>());
            AsyncPageSource<Integer> source = source(req -> {
                CompletableFuture<PageResult<Integer>> f = new CompletableFuture<>();
                futures.add(f);
                return f;
            });

            CountDownLatch start = new CountDownLatch(1);
            Runnable dispatch = () -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                source.request(request(1), (result, error) -> { });
            };
            Thread t1 = new Thread(dispatch);
            Thread t2 = new Thread(dispatch);
            t1.start();
            t2.start();
            start.countDown();
            t1.join(5000);
            t2.join(5000);

            assertEquals(2, futures.size());
            // complete both in arbitrary order; the later dispatch must own the final state
            futures.get(0).complete(PageResult.of(Arrays.asList(10), 1));
            futures.get(1).complete(PageResult.of(Arrays.asList(20), 1));

            assertFalse(source.isLoading(), "round " + round + ": loading must clear once the latest request settles");
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
```

Note on `failedFuture_capturesErrorAndKeepsLastGoodElements`: `CompletableFuture.whenComplete` may deliver the raw throwable or a `CompletionException` wrapping it depending on completion path — the implementation must unwrap `CompletionException` before storing/delivering (see Step 5.3), after which the assertion can be simplified to `assertEquals(boom, settledError.get())`. Write the implementation to unwrap, then tighten the assertion.

- [ ] **Step 5.2: Run the tests to verify they fail to compile**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=AsyncPageSourceTest"
```

Expected: COMPILATION ERROR (`AsyncPageSource` does not exist).

- [ ] **Step 5.3: Implement `AsyncPageSource` (core)**

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous {@link PageSource} backed by an {@link AsyncPageSupplier}. Owns all async
 * machinery: monotonically increasing request ids with at-most-once settle semantics, stale
 * response discarding, optional request timeouts and an optional TTL-bounded page cache.
 *
 * <p>Thread-safety: every state transition happens inside one internal lock; the id check and the
 * state write are atomic. Asynchronously completed settles are routed through the configured
 * {@link SettleDispatcher}; synchronous settles (cache hits, immediate failures) run on the
 * calling thread.
 */
public final class AsyncPageSource<T> implements PageSource<T> {

    private static final Logger LOGGER = Logger.getLogger(AsyncPageSource.class.getName());
    private static volatile ScheduledExecutorService sharedTimeoutScheduler;

    private final AsyncPageSupplier<T> supplier;
    private final PaginationErrorCallback errorCallback;
    private final Duration requestTimeout;
    private final Duration cacheTtl;
    private final SettleDispatcher settleDispatcher;
    private final ScheduledExecutorService timeoutScheduler;

    private final Object lock = new Object();
    private final AtomicLong requestIds = new AtomicLong();
    private final Map<String, CacheEntry<T>> cache;

    // all guarded by lock
    private long settledId;
    private boolean loading;
    private Throwable lastError;
    private List<T> elements = Collections.emptyList();
    private int totalElements;
    private boolean totalsKnown;

    /**
     * Creates an async page source.
     *
     * @param supplier         loads pages, not null
     * @param errorCallback    invoked on failed loads, may be null
     * @param requestTimeout   per-request timeout, may be null to disable
     * @param cacheTtl         cache entry freshness window, may be null to disable caching
     * @param cacheMaxPages    cache LRU bound, must be {@code >= 1}
     * @param settleDispatcher routes asynchronously completed settles, not null
     * @throws NullPointerException     if {@code supplier} or {@code settleDispatcher} is null
     * @throws IllegalArgumentException if {@code cacheMaxPages < 1}
     */
    public AsyncPageSource(AsyncPageSupplier<T> supplier,
                           PaginationErrorCallback errorCallback,
                           Duration requestTimeout,
                           Duration cacheTtl,
                           int cacheMaxPages,
                           SettleDispatcher settleDispatcher) {
        this(supplier, errorCallback, requestTimeout, cacheTtl, cacheMaxPages, settleDispatcher, null);
    }

    AsyncPageSource(AsyncPageSupplier<T> supplier,
                    PaginationErrorCallback errorCallback,
                    Duration requestTimeout,
                    Duration cacheTtl,
                    int cacheMaxPages,
                    SettleDispatcher settleDispatcher,
                    ScheduledExecutorService timeoutScheduler) {
        Objects.requireNonNull(supplier, "supplier is required.");
        Objects.requireNonNull(settleDispatcher, "settleDispatcher is required.");
        if (cacheMaxPages < 1) {
            throw new IllegalArgumentException("cacheMaxPages must be >= 1.");
        }
        this.supplier = supplier;
        this.errorCallback = errorCallback;
        this.requestTimeout = requestTimeout;
        this.cacheTtl = cacheTtl;
        this.settleDispatcher = settleDispatcher;
        this.timeoutScheduler = timeoutScheduler;
        this.cache = new LinkedHashMap<String, CacheEntry<T>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<T>> eldest) {
                return size() > cacheMaxPages;
            }
        };
    }

    @Override
    public void request(PageRequest request, BiConsumer<PageResult<T>, Throwable> onSettle) {
        Objects.requireNonNull(request, "request is required.");
        Objects.requireNonNull(onSettle, "onSettle is required.");

        long id = requestIds.incrementAndGet();
        PageResult<T> cached;
        synchronized (lock) {
            loading = true;
            lastError = null;
            cached = cacheTtl == null ? null : cachedResult(cacheKey(request));
        }
        if (cached != null) {
            settle(id, request, cached, null, onSettle, true);
            return;
        }

        CompletableFuture<PageResult<T>> future;
        try {
            future = supplier.load(request);
        } catch (Throwable t) {
            settle(id, request, null, t, onSettle, true);
            return;
        }
        if (future == null) {
            settle(id, request, null,
                    new IllegalStateException("AsyncPageSupplier returned a null future."), onSettle, true);
            return;
        }

        ScheduledFuture<?> timeoutTask = scheduleTimeout(id, request, future, onSettle);
        future.whenComplete((result, error) -> {
            if (timeoutTask != null) {
                timeoutTask.cancel(false);
            }
            settle(id, request, result, unwrap(error), onSettle, false);
        });
    }

    private void settle(long id, PageRequest request, PageResult<T> result, Throwable error,
                        BiConsumer<PageResult<T>, Throwable> onSettle, boolean inline) {
        Runnable task = () -> applyAndDeliver(id, request, result, error, onSettle);
        if (inline) {
            task.run();
        } else {
            settleDispatcher.dispatch(request, task);
        }
    }

    private void applyAndDeliver(long id, PageRequest request, PageResult<T> rawResult,
                                 Throwable error, BiConsumer<PageResult<T>, Throwable> onSettle) {
        PageResult<T> result = rawResult;
        if (error == null && result == null) {
            error = new IllegalStateException("AsyncPageSupplier completed with a null result.");
        }
        synchronized (lock) {
            // superseded requests must not settle, mutate state, or clear the newer
            // request's loading flag; an already-settled id (timeout vs late completion)
            // must not settle twice
            if (id != requestIds.get() || id <= settledId) {
                return;
            }
            settledId = id;
            loading = false;
            if (error == null) {
                result = truncateIfOversized(request, result);
                elements = result.getItems();
                totalElements = result.getTotalElements();
                totalsKnown = true;
                if (cacheTtl != null) {
                    cache.put(cacheKey(request), new CacheEntry<>(result, System.nanoTime()));
                }
            } else {
                lastError = error;
            }
        }
        if (error != null && errorCallback != null) {
            try {
                errorCallback.onError(request, error);
            } catch (Throwable callbackError) {
                LOGGER.log(Level.WARNING, "PaginationErrorCallback threw while handling a page load failure.", callbackError);
            }
        }
        if (error != null) {
            onSettle.accept(null, error);
        } else {
            onSettle.accept(result, null);
        }
    }

    private PageResult<T> truncateIfOversized(PageRequest request, PageResult<T> result) {
        if (result.getItems().size() <= request.getPageSize()) {
            return result;
        }
        LOGGER.warning("AsyncPageSupplier returned " + result.getItems().size()
                + " items for a page of size " + request.getPageSize() + "; truncating.");
        return PageResult.of(new ArrayList<>(result.getItems().subList(0, request.getPageSize())),
                result.getTotalElements());
    }

    private ScheduledFuture<?> scheduleTimeout(long id, PageRequest request,
                                               CompletableFuture<PageResult<T>> future,
                                               BiConsumer<PageResult<T>, Throwable> onSettle) {
        if (requestTimeout == null) {
            return null;
        }
        return timeoutScheduler().schedule(() -> {
            future.cancel(true);
            settle(id, request, null,
                    new TimeoutException("page request timed out after " + requestTimeout), onSettle, false);
        }, requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private ScheduledExecutorService timeoutScheduler() {
        if (timeoutScheduler != null) {
            return timeoutScheduler;
        }
        ScheduledExecutorService shared = sharedTimeoutScheduler;
        if (shared == null) {
            synchronized (AsyncPageSource.class) {
                shared = sharedTimeoutScheduler;
                if (shared == null) {
                    ThreadFactory factory = runnable -> {
                        Thread thread = new Thread(runnable, "spigotboot-inventoryapi-pagination-timeout");
                        thread.setDaemon(true);
                        return thread;
                    };
                    shared = Executors.newSingleThreadScheduledExecutor(factory);
                    sharedTimeoutScheduler = shared;
                }
            }
        }
        return shared;
    }

    private PageResult<T> cachedResult(String key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.nanoTime() - entry.createdNanos > cacheTtl.toNanos()) {
            cache.remove(key);
            return null;
        }
        return entry.result;
    }

    private static String cacheKey(PageRequest request) {
        return request.getOffset() + ":" + request.getPageSize();
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    @Override
    public int totalElements() {
        synchronized (lock) {
            return totalElements;
        }
    }

    @Override
    public boolean totalsKnown() {
        synchronized (lock) {
            return totalsKnown;
        }
    }

    @Override
    public boolean isLoading() {
        synchronized (lock) {
            return loading;
        }
    }

    @Override
    public Throwable lastError() {
        synchronized (lock) {
            return lastError;
        }
    }

    @Override
    public List<T> elements() {
        synchronized (lock) {
            return elements;
        }
    }

    @Override
    public void invalidate() {
        synchronized (lock) {
            cache.clear();
        }
    }

    private static final class CacheEntry<T> {
        private final PageResult<T> result;
        private final long createdNanos;

        private CacheEntry(PageResult<T> result, long createdNanos) {
            this.result = result;
            this.createdNanos = createdNanos;
        }
    }
}
```

After implementing, simplify the assertion in `failedFuture_capturesErrorAndKeepsLastGoodElements` to `assertEquals(boom, settledError.get())` — the `unwrap` guarantees the raw cause is delivered.

- [ ] **Step 5.4: Run the tests to verify they pass**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=AsyncPageSourceTest"
```

Expected: 11 tests PASS.

- [ ] **Step 5.5: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSource.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java
git commit -m "feat(inventory-api): add AsyncPageSource with atomic settle machinery"
```

---

### Task 6: `AsyncPageSource` — timeout behavior tests

The timeout code already exists (Task 5); this task pins its behavior with a deterministic injected scheduler.

**Files:**
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java`

- [ ] **Step 6.1: Add a manually-triggered scheduler and the timeout tests**

Add to `AsyncPageSourceTest`:

```java
    /**
     * Deterministic scheduler: captures scheduled tasks so tests fire timeouts by hand.
     */
    private static final class ManualScheduler extends java.util.concurrent.ScheduledThreadPoolExecutor {
        private final List<Runnable> scheduled = new ArrayList<>();
        private final List<ScheduledFutureStub> futures = new ArrayList<>();

        private ManualScheduler() {
            super(1);
        }

        @Override
        public java.util.concurrent.ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduled.add(command);
            ScheduledFutureStub stub = new ScheduledFutureStub();
            futures.add(stub);
            return stub;
        }

        private void fire(int index) {
            scheduled.get(index).run();
        }

        private static final class ScheduledFutureStub implements java.util.concurrent.ScheduledFuture<Object> {
            private boolean cancelled;

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancelled = true;
                return true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(java.util.concurrent.Delayed o) {
                return 0;
            }

            @Override
            public boolean isDone() {
                return cancelled;
            }

            @Override
            public Object get() {
                return null;
            }

            @Override
            public Object get(long timeout, TimeUnit unit) {
                return null;
            }
        }
    }

    private static <T> AsyncPageSource<T> timeoutSource(AsyncPageSupplier<T> supplier, ManualScheduler scheduler) {
        return new AsyncPageSource<>(supplier, null, java.time.Duration.ofSeconds(5), null, 128,
                SettleDispatcher.inline(), scheduler);
    }

    @Test
    void timeout_settlesAsTimeoutExceptionFailure() {
        ManualScheduler scheduler = new ManualScheduler();
        CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
        AsyncPageSource<Integer> source = timeoutSource(req -> future, scheduler);
        AtomicReference<Throwable> settledError = new AtomicReference<>();

        source.request(request(1), (result, error) -> settledError.set(error));
        scheduler.fire(0);

        assertInstanceOf(java.util.concurrent.TimeoutException.class, settledError.get());
        assertFalse(source.isLoading());
        assertTrue(future.isCancelled(), "best-effort cancel must be attempted");
    }

    @Test
    void lateCompletion_afterTimeout_isDiscarded() {
        ManualScheduler scheduler = new ManualScheduler();
        CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
        AsyncPageSource<Integer> source = timeoutSource(req -> future, scheduler);
        AtomicInteger settleCount = new AtomicInteger();

        source.request(request(1), (result, error) -> settleCount.incrementAndGet());
        scheduler.fire(0);
        // CompletableFuture.cancel completes the future as cancelled, so obtrude a value to
        // simulate a genuinely late real completion racing the timeout settle
        future.obtrudeValue(PageResult.of(Arrays.asList(1, 2), 6));

        assertEquals(1, settleCount.get(), "timeout already settled this id; late completion must be discarded");
        assertTrue(source.elements().isEmpty(), "late result must not be applied");
    }

    @Test
    void completionBeforeTimeout_cancelsTheTimeoutTask() {
        ManualScheduler scheduler = new ManualScheduler();
        CompletableFuture<PageResult<Integer>> future = new CompletableFuture<>();
        AsyncPageSource<Integer> source = timeoutSource(req -> future, scheduler);

        source.request(request(1), (result, error) -> { });
        future.complete(PageResult.of(Arrays.asList(1), 1));

        assertTrue(scheduler.futures.get(0).isCancelled());
        // even if the timeout fires anyway (cancellation race), it must not settle twice
        scheduler.fire(0);
        assertEquals(Arrays.asList(1), source.elements());
        assertNull(source.lastError());
    }
```

Note: `obtrudeValue` forcibly overwrites a completed future's outcome and re-triggers nothing by itself — but our `whenComplete` already ran on cancel. The discard assertion therefore exercises the `id <= settledId` guard via the cancel-triggered completion (a `CancellationException` arriving after the timeout settle). If the cancel-path completion makes `settleCount` flaky, replace `future.cancel` semantics in the test with a non-cancelling timeout source (pass a supplier whose future you complete manually AFTER `scheduler.fire(0)`) and assert `settleCount == 1` — the guard is what matters, not the delivery vehicle.

- [ ] **Step 6.2: Run the tests**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=AsyncPageSourceTest"
```

Expected: all PASS (timeout machinery was implemented in Task 5; if any timeout test fails, fix `AsyncPageSource` — these tests are the specification).

- [ ] **Step 6.3: Commit**

```powershell
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java
git commit -m "test(inventory-api): pin AsyncPageSource timeout semantics"
```

---

### Task 7: `AsyncPageSource` — cache behavior tests

Cache code exists (Task 5); pin it.

**Files:**
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java`

- [ ] **Step 7.1: Add the cache tests**

```java
    private static <T> AsyncPageSource<T> cachedSource(AsyncPageSupplier<T> supplier, int maxPages) {
        return new AsyncPageSource<>(supplier, null, null, java.time.Duration.ofMinutes(5), maxPages,
                SettleDispatcher.inline());
    }

    @Test
    void cacheHit_skipsSupplierAndSettlesSynchronously() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1, 2, 3), 9));
        }, 128);

        source.request(request(1), (result, error) -> { });
        source.request(request(1), (result, error) -> { });

        assertEquals(1, calls.get(), "second request for the same offset:pageSize must hit the cache");
        assertFalse(source.isLoading());
        assertEquals(Arrays.asList(1, 2, 3), source.elements());
    }

    @Test
    void cacheHit_updatesTotalsToo() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req ->
                CompletableFuture.completedFuture(PageResult.of(
                        Arrays.asList(10 * calls.incrementAndGet()), 7)), 128);

        source.request(request(1), (result, error) -> { });
        source.request(request(2), (result, error) -> { });
        source.request(request(1), (result, error) -> { }); // cache hit for page 1

        assertEquals(Arrays.asList(10), source.elements());
        assertEquals(7, source.totalElements());
    }

    @Test
    void failures_areNeverCached() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req -> calls.incrementAndGet() == 1
                ? failedFuture(new RuntimeException("boom"))
                : CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1)), 128);

        source.request(request(1), (result, error) -> { });
        source.request(request(1), (result, error) -> { });

        assertEquals(2, calls.get(), "a failure must not populate the cache");
        assertEquals(Arrays.asList(1), source.elements());
    }

    @Test
    void invalidate_clearsCache() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1));
        }, 128);

        source.request(request(1), (result, error) -> { });
        source.invalidate();
        source.request(request(1), (result, error) -> { });

        assertEquals(2, calls.get());
    }

    @Test
    void cache_evictsLeastRecentlyUsedBeyondMaxPages() {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = cachedSource(req -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(PageResult.of(Arrays.asList(req.getPage()), 9));
        }, 2);

        source.request(request(1), (result, error) -> { }); // cache: {1}
        source.request(request(2), (result, error) -> { }); // cache: {1, 2}
        source.request(request(3), (result, error) -> { }); // cache: {2, 3} — 1 evicted
        source.request(request(1), (result, error) -> { }); // miss → supplier again

        assertEquals(4, calls.get());
    }

    @Test
    void expiredEntry_missesAndRefetches() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AsyncPageSource<Integer> source = new AsyncPageSource<>(req -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(PageResult.of(Arrays.asList(1), 1));
        }, null, null, java.time.Duration.ofMillis(20), 128, SettleDispatcher.inline());

        source.request(request(1), (result, error) -> { });
        Thread.sleep(60);
        source.request(request(1), (result, error) -> { });

        assertEquals(2, calls.get(), "an entry older than the TTL must not be served");
    }
```

- [ ] **Step 7.2: Run the tests**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=AsyncPageSourceTest"
```

Expected: all PASS.

- [ ] **Step 7.3: Commit**

```powershell
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/AsyncPageSourceTest.java
git commit -m "test(inventory-api): pin AsyncPageSource cache semantics"
```

---

### Task 8: `Pagination` interface additions

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/Pagination.java`

- [ ] **Step 8.1: Add the default methods and update contracts**

Add the import `tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource` is NOT needed (no signature references it). Apply these edits to `Pagination.java`:

Replace the `changePage` Javadoc:

```java
    /**
     * Navigates directly to the given 1-indexed page.
     *
     * <p>The target is clamped to at least 1, and to {@code getTotalPages()} once the backing
     * source's totals are known. For async sources, navigation issued before the first load
     * completes is honored optimistically and re-clamped downward when totals arrive. A call that
     * targets the current page while a request for it is already in flight is ignored; use
     * {@link #refresh()} to force a reload.
     */
    void changePage(int page);
```

Replace the `setSource` Javadoc:

```java
    /**
     * Replaces the backing source with an eager in-memory list and resets internal state
     * accordingly. On a paginator built with an async source this discards the async supplier —
     * the configured loading item, error callback, timeout and cache become inert.
     */
    void setSource(List<T> source);
```

Replace the `getSource` Javadoc:

```java
    /**
     * @return an unmodifiable view of the elements currently loaded locally — the full backing
     * list for eager sources, the items of the most recently delivered page for async sources.
     * Mutations must go through {@link #setSource(List)}; use {@link #getTotalElements()} for
     * counts.
     */
    List<T> getSource();
```

Replace the `getPageOfIndex` Javadoc:

```java
    /**
     * @return the 1-indexed page containing the given global source index (for scroll paginators:
     * the first page on which the index becomes visible), or {@code -1} if the index is outside
     * {@code [0, getTotalElements())}
     */
    int getPageOfIndex(int index);
```

Append before the closing brace:

```java
    /**
     * @return {@code true} while an async page load for this paginator is in flight; always
     * {@code false} for eager sources
     */
    default boolean isLoading() {
        return false;
    }

    /**
     * @return the failure of the most recent async page load, or {@code null}; cleared when a new
     * load is dispatched. Always {@code null} for eager sources.
     */
    default Throwable lastError() {
        return null;
    }

    /**
     * @return the total number of elements in the backing source, independent of how many are
     * loaded locally
     */
    default int getTotalElements() {
        return getSource().size();
    }

    /**
     * Re-requests the current page, invalidating any cached copy first. For async sources this is
     * the supported idiom to re-query after the backing store changed; for eager sources it
     * re-renders the current page.
     */
    default void refresh() {
        changePage(getCurrentPage());
    }
```

- [ ] **Step 8.2: Verify the module compiles and the suite is green**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: BUILD SUCCESS, all tests pass (default methods are additive).

- [ ] **Step 8.3: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/Pagination.java
git commit -m "feat(inventory-api): add isLoading/lastError/getTotalElements/refresh to Pagination"
```

---

### Task 9: `BukkitSettleDispatcher`, `AsyncPaginationOptions`, builder `async(...)`

**Files:**
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/BukkitSettleDispatcher.java`
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/AsyncPaginationOptions.java`
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/NormalPaginationBuilder.java`
- Modify: `.../builder/ScrollPaginationBuilder.java`
- Modify: `.../builder/PatternPaginationBuilder.java`
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/AsyncPaginationOptionsTest.java`

- [ ] **Step 9.1: Write the failing options-validation test**

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsyncPaginationOptionsTest {

    private static AsyncPaginationOptions<Integer> optionsWithSource() {
        AsyncPaginationOptions<Integer> options = new AsyncPaginationOptions<>();
        options.source(request -> CompletableFuture.completedFuture(
                PageResult.of(Collections.emptyList(), 0)));
        return options;
    }

    @Test
    void buildPageSource_requiresSource() {
        AsyncPaginationOptions<Integer> options = new AsyncPaginationOptions<>();

        assertThrows(NullPointerException.class, options::buildPageSource);
    }

    @Test
    void buildPageSource_withSource_succeeds() {
        PageSource<Integer> source = optionsWithSource().buildPageSource();

        assertNotNull(source);
    }

    @Test
    void requestTimeout_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> optionsWithSource().requestTimeout(Duration.ZERO));
    }

    @Test
    void cacheTtl_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> optionsWithSource().cacheTtl(Duration.ofSeconds(-1)));
    }

    @Test
    void cacheMaxPages_rejectsBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> optionsWithSource().cacheMaxPages(0));
    }

    @Test
    void cacheMaxPages_withoutCacheTtl_failsAtBuild() {
        AsyncPaginationOptions<Integer> options = optionsWithSource();
        options.cacheMaxPages(10);

        assertThrows(IllegalArgumentException.class, options::buildPageSource);
    }
}
```

- [ ] **Step 9.2: Run it to verify compilation failure**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=AsyncPaginationOptionsTest"
```

Expected: COMPILATION ERROR.

- [ ] **Step 9.3: Implement `BukkitSettleDispatcher` and `AsyncPaginationOptions`**

`BukkitSettleDispatcher.java`:

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.source;

import org.bukkit.Bukkit;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

/**
 * Default {@link SettleDispatcher}: applies asynchronously completed page loads on the main
 * server thread, mirroring how click-triggered updates always run there. Inventories that opted
 * into {@link InventoryConfiguration#tickAsync()} settle directly on the completing thread, the
 * same trust the async tick task already extends to them.
 */
public final class BukkitSettleDispatcher implements SettleDispatcher {

    @Override
    public void dispatch(PageRequest request, Runnable task) {
        Viewer viewer = request.getViewer();
        if (viewer == null || Bukkit.isPrimaryThread() || tickAsync(viewer)) {
            task.run();
            return;
        }
        Bukkit.getScheduler().runTask(viewer.getPlugin(), task);
    }

    private static boolean tickAsync(Viewer viewer) {
        CustomInventory customInventory = viewer.getCustomInventory();
        if (customInventory == null) {
            return false;
        }
        InventoryConfiguration configuration = customInventory.getConfiguration();
        return configuration != null && configuration.tickAsync();
    }
}
```

`AsyncPaginationOptions.java`:

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder;

import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.BukkitSettleDispatcher;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PaginationErrorCallback;

import java.time.Duration;
import java.util.Objects;

/**
 * Mutable options collected by the single {@code async(...)} method of the pagination builders.
 * Mirrors the {@code configure(InventorySettings)} mutation pattern used by custom inventories.
 */
public final class AsyncPaginationOptions<T> {

    private static final int DEFAULT_CACHE_MAX_PAGES = 128;

    private AsyncPageSupplier<T> source;
    private InventoryItemSupplier loadingItem;
    private PaginationErrorCallback errorCallback;
    private Duration requestTimeout;
    private Duration cacheTtl;
    private int cacheMaxPages = DEFAULT_CACHE_MAX_PAGES;
    private boolean cacheMaxPagesSet;

    /**
     * Sets the async page supplier. Required.
     *
     * @param source loads pages on demand, not null
     * @return this options object, for chaining
     */
    public AsyncPaginationOptions<T> source(AsyncPageSupplier<T> source) {
        this.source = Objects.requireNonNull(source, "source is required.");
        return this;
    }

    /**
     * Sets the item rendered in every page slot while a load is in flight.
     *
     * @param loadingItem the loading placeholder supplier
     * @return this options object, for chaining
     */
    public AsyncPaginationOptions<T> loadingItem(InventoryItemSupplier loadingItem) {
        this.loadingItem = loadingItem;
        return this;
    }

    /**
     * Sets the callback invoked when a page load fails.
     *
     * @param errorCallback the failure callback
     * @return this options object, for chaining
     */
    public AsyncPaginationOptions<T> errorCallback(PaginationErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
        return this;
    }

    /**
     * Enables a per-request timeout; a request exceeding it fails with a
     * {@code TimeoutException} and follows the normal error path.
     *
     * @param requestTimeout the timeout, must be positive
     * @return this options object, for chaining
     * @throws IllegalArgumentException if {@code requestTimeout} is zero or negative
     */
    public AsyncPaginationOptions<T> requestTimeout(Duration requestTimeout) {
        Objects.requireNonNull(requestTimeout, "requestTimeout is required.");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must be positive.");
        }
        this.requestTimeout = requestTimeout;
        return this;
    }

    /**
     * Enables page caching: a revisit within the TTL renders from cache without calling the
     * supplier. {@code refresh()} invalidates the cache.
     *
     * @param cacheTtl entry freshness window, must be positive
     * @return this options object, for chaining
     * @throws IllegalArgumentException if {@code cacheTtl} is zero or negative
     */
    public AsyncPaginationOptions<T> cacheTtl(Duration cacheTtl) {
        Objects.requireNonNull(cacheTtl, "cacheTtl is required.");
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("cacheTtl must be positive.");
        }
        this.cacheTtl = cacheTtl;
        return this;
    }

    /**
     * Bounds the page cache (least-recently-used eviction). Defaults to 128. Only meaningful
     * together with {@link #cacheTtl(Duration)}.
     *
     * @param cacheMaxPages the maximum number of cached pages, at least 1
     * @return this options object, for chaining
     * @throws IllegalArgumentException if {@code cacheMaxPages} is below 1
     */
    public AsyncPaginationOptions<T> cacheMaxPages(int cacheMaxPages) {
        if (cacheMaxPages < 1) {
            throw new IllegalArgumentException("cacheMaxPages must be >= 1.");
        }
        this.cacheMaxPages = cacheMaxPages;
        this.cacheMaxPagesSet = true;
        return this;
    }

    InventoryItemSupplier getLoadingItem() {
        return loadingItem;
    }

    /**
     * Builds the configured {@link AsyncPageSource}.
     *
     * @return the page source
     * @throws NullPointerException     if no source was configured
     * @throws IllegalArgumentException if {@code cacheMaxPages} was set without {@code cacheTtl}
     */
    PageSource<T> buildPageSource() {
        Objects.requireNonNull(source, "async source is required.");
        if (cacheMaxPagesSet && cacheTtl == null) {
            throw new IllegalArgumentException("cacheMaxPages requires cacheTtl.");
        }
        return new AsyncPageSource<>(source, errorCallback, requestTimeout, cacheTtl, cacheMaxPages,
                new BukkitSettleDispatcher());
    }
}
```

Note: `buildPageSource()` and `getLoadingItem()` are package-private — only builders call them. The test lives in the same package, so it compiles.

- [ ] **Step 9.4: Add `async(...)` to the three builders**

`NormalPaginationBuilder.java` — add field, method, and rework `build()`:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;

import java.util.function.Consumer;
```

```java
    private AsyncPaginationOptions<T> asyncOptions;

    /**
     * Configures asynchronous page loading. All async-related settings (source, loading item,
     * error callback, timeout, cache) are set on the options object passed to the consumer.
     *
     * @param configurer receives the options to populate, not null
     * @return this builder, for chaining
     */
    public NormalPaginationBuilder<T> async(Consumer<AsyncPaginationOptions<T>> configurer) {
        Objects.requireNonNull(configurer, "configurer is required.");
        this.asyncOptions = new AsyncPaginationOptions<>();
        configurer.accept(this.asyncOptions);
        return this;
    }

    public NormalPagination<T> build() {
        Objects.requireNonNull(this.itemFactory, "itemFactory is required.");
        Objects.requireNonNull(this.layout, "layout is required.");
        InventoryLayout.requireItemSlots(this.layout);

        PageSource<T> pageSource = this.asyncOptions != null
                ? this.asyncOptions.buildPageSource()
                : EagerPageSource.empty();
        InventoryItemSupplier loadingItem = this.asyncOptions != null
                ? this.asyncOptions.getLoadingItem()
                : null;

        return new NormalPagination<>(this.fallbackItem, this.itemFactory, this.layout, loadingItem, pageSource);
    }
```

`ScrollPaginationBuilder.java` — identical shape; `build()` returns:

```java
        return new ScrollPagination<>(this.fallbackItem, this.itemFactory, this.layout, loadingItem, pageSource);
```

`PatternPaginationBuilder.java` — identical shape (method returns `PatternPaginationBuilder<T>`); `build()` returns:

```java
        return new PatternPagination<>(this.fallbackItem, this.itemFactory, new ArrayList<>(this.patterns), loadingItem, pageSource);
```

**Ordering note:** the five-argument constructors do not exist until Task 10-12. To keep every commit compiling, Tasks 9-12 land as ONE commit series in this order: Task 9 writes builders against the new constructors but does NOT commit; Tasks 10-12 add the constructors; the combined `git commit` happens at each pagination task's end with the builder file included (exact `git add` lists below say which). Alternatively (simpler, recommended): implement Steps 9.3 and 9.5's options/test first, commit those, and apply the builder edits inside Tasks 10-12 where each constructor appears. The task text below assumes the recommended order — builder edits for Normal land in Task 10, Scroll in Task 11, Pattern in Task 12.

- [ ] **Step 9.5: Run the options test, then commit options + dispatcher only**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=AsyncPaginationOptionsTest"
```

Expected: 6 tests PASS (only `AsyncPaginationOptions` + `BukkitSettleDispatcher` + test are new; builders untouched yet).

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/source/BukkitSettleDispatcher.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/AsyncPaginationOptions.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/AsyncPaginationOptionsTest.java
git commit -m "feat(inventory-api): add AsyncPaginationOptions and Bukkit settle dispatcher"
```

---

### Task 10: `NormalPagination` refactor (+ its builder)

The canonical impl refactor — Tasks 11/12 repeat the same shape with per-type math. The existing suite pins eager behavior throughout.

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/NormalPagination.java` (full rewrite below)
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/NormalPaginationBuilder.java` (per Task 9 Step 9.4)
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/NormalPaginationTest.java`

- [ ] **Step 10.1: Write the failing eager-regression + bounds tests**

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.impl;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.NormalPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    private static InventoryLayout nineSlotLayout() {
        return InventoryLayout.ofSlots(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    private static NormalPagination<Integer> eagerPagination() {
        return new NormalPaginationBuilder<Integer>()
                .layout(nineSlotLayout())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .build();
    }

    private Viewer mockViewer(InventoryEditor editor) {
        Viewer viewer = mock(Viewer.class);
        CustomInventory customInventory = mock(CustomInventory.class);
        lenient().when(viewer.getEditor()).thenReturn(editor);
        lenient().when(viewer.getPlugin()).thenReturn(plugin);
        lenient().when(viewer.getCustomInventory()).thenReturn(customInventory);
        return viewer;
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }

    @Test
    void eagerFlow_rendersFirstPageSlice() {
        NormalPagination<Integer> pagination = eagerPagination();
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor));
        pagination.setSource(sourceOf(20));
        pagination.apply();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(captor.capture(), any(), eq(pagination));

        List<InventoryItem> items = captor.getValue();
        assertEquals(9, items.size());
        for (int i = 0; i < 9; i++) {
            assertEquals(i + 1, items.get(i).getItemStack().getAmount());
        }
    }

    @Test
    void eagerFlow_totalsAndNavigationMatchListSize() {
        NormalPagination<Integer> pagination = eagerPagination();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(20));

        assertEquals(3, pagination.getTotalPages());
        assertEquals(20, pagination.getTotalElements());

        pagination.changePage(3);
        assertEquals(3, pagination.getCurrentPage());
        assertFalse(pagination.hasNextPage());
    }

    @Test
    void changePage_beyondTotal_clampsToLastPage() {
        NormalPagination<Integer> pagination = eagerPagination();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(20));

        pagination.changePage(99);

        assertEquals(3, pagination.getCurrentPage());
    }

    @Test
    void getPageOfIndex_returnsMinusOneOutOfRange() {
        NormalPagination<Integer> pagination = eagerPagination();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(20));

        assertEquals(1, pagination.getPageOfIndex(0));
        assertEquals(1, pagination.getPageOfIndex(8));
        assertEquals(2, pagination.getPageOfIndex(9));
        assertEquals(3, pagination.getPageOfIndex(19));
        assertEquals(-1, pagination.getPageOfIndex(20));
        assertEquals(-1, pagination.getPageOfIndex(-1));
    }

    @Test
    void setSource_beforeInit_isHonoredByInit() {
        NormalPagination<Integer> pagination = eagerPagination();
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.setSource(sourceOf(5));
        pagination.init(mockViewer(editor));
        pagination.apply();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(captor.capture(), any(), eq(pagination));
        assertEquals(1, captor.getValue().get(0).getItemStack().getAmount());
    }
}
```

- [ ] **Step 10.2: Run to verify failure**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=NormalPaginationTest"
```

Expected: COMPILATION ERROR if written against the new 5-arg constructor, otherwise FAILS on `getPageOfIndex_returnsMinusOneOutOfRange` (current impl returns 3 for index 20, no -1). Builder still compiles unchanged at this point — the test above only uses existing builder API, so the failure is the `-1` assertions plus `getTotalElements` (interface default exists since Task 8 and works via `getSource()` — passes; the -1 test is the red bar).

- [ ] **Step 10.3: Rewrite `NormalPagination` (complete file body below the MIT header)**

```java
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.impl;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.GenericInventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Page-by-page paginator. Each page renders a contiguous slice of the source, clamped to
 * {@code itemPageLimit} (the number of slots in the configured layout). Pages come from a
 * {@link PageSource}: an in-memory list by default, or an async supplier configured through the
 * builder's {@code async(...)} method.
 */
@Getter
public class NormalPagination<T> implements Pagination<T> {

    private static final Logger LOGGER = Logger.getLogger(NormalPagination.class.getName());

    private final InventoryItemSupplier fallbackItem;
    private final GenericInventoryItemSupplier<T> itemSupplier;
    private final InventoryLayout layout;
    @Getter(AccessLevel.NONE)
    private final InventoryItemSupplier loadingItem;
    @Getter(AccessLevel.NONE)
    private PageSource<T> pageSource;
    @Getter(AccessLevel.NONE)
    private volatile List<T> currentItems = Collections.emptyList();
    @Getter(AccessLevel.NONE)
    private volatile Thread dispatchingThread;
    private Viewer viewer;
    private int currentPage = 1;
    private int itemPageLimit;

    /**
     * Creates an eager paginator over an initially empty source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param layout       the slots the page renders into, not null
     */
    public NormalPagination(InventoryItemSupplier fallbackItem,
                            GenericInventoryItemSupplier<T> itemSupplier,
                            InventoryLayout layout) {
        this(fallbackItem, itemSupplier, layout, null, EagerPageSource.empty());
    }

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param layout       the slots the page renders into, not null
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    public NormalPagination(InventoryItemSupplier fallbackItem,
                            GenericInventoryItemSupplier<T> itemSupplier,
                            InventoryLayout layout,
                            InventoryItemSupplier loadingItem,
                            PageSource<T> pageSource) {
        this.fallbackItem = fallbackItem;
        this.itemSupplier = itemSupplier;
        this.layout = layout;
        this.loadingItem = loadingItem;
        this.pageSource = Objects.requireNonNull(pageSource, "pageSource is required.");
    }

    @Override
    public void init(Viewer viewer) {
        this.viewer = viewer;
        this.itemPageLimit = layout.getSlots().size();
        dispatch(this.currentPage, false);
    }

    @Override
    public void apply() {
        insertPageItems();
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
        InventoryEditor editor = this.viewer.getEditor();
        // snapshot volatile state once so one consistent view feeds the whole render
        List<T> items = this.currentItems;
        boolean loading = this.pageSource.isLoading();
        List<InventoryItem> inventoryItems = new LinkedList<>();

        for (int i = 0; i < this.itemPageLimit; i++) {
            if (loading) {
                inventoryItems.add(loadingOrFallback());
            } else if (i < items.size()) {
                inventoryItems.add(this.itemSupplier.get(this.viewer, items.get(i)));
            } else {
                inventoryItems.add(emptyOrFallback());
            }
        }

        editor.fillPage(inventoryItems, layout, this);
    }

    @Override
    public void changePage(int page) {
        changePageInternal(page, false);
    }

    private void changePageInternal(int page, boolean forceDispatch) {
        int target = Math.max(1, page);
        if (this.pageSource.totalsKnown()) {
            target = Math.min(target, this.getTotalPages());
        }
        // a click targeting the page already being loaded must not re-dispatch and starve
        // the in-flight request
        if (!forceDispatch && target == this.currentPage && this.pageSource.isLoading()) {
            return;
        }
        int rollbackPage = this.currentPage;
        this.currentPage = target;
        dispatch(rollbackPage, true);
    }

    private void dispatch(int rollbackPage, boolean render) {
        PageRequest request = new PageRequest(
                this.currentPage, this.itemPageLimit,
                (this.currentPage - 1) * this.itemPageLimit, this.viewer);
        this.dispatchingThread = Thread.currentThread();
        try {
            this.pageSource.request(request, (result, error) -> onSettle(rollbackPage, result, error));
        } finally {
            this.dispatchingThread = null;
        }
        if (render) {
            renderIfOnline();
        }
    }

    private void onSettle(int rollbackPage, PageResult<T> result, Throwable error) {
        // settles that ran synchronously inside dispatch() must not render: the dispatching
        // caller renders once, preserving today's eager behavior (and preventing
        // setSource-in-update() recursion)
        boolean inline = Thread.currentThread() == this.dispatchingThread;
        try {
            if (error != null) {
                this.currentPage = rollbackPage;
            } else {
                this.currentItems = result.getItems();
                if (this.pageSource.totalsKnown() && this.currentPage > this.getTotalPages()) {
                    // totals shrank below the optimistic target; converge onto the last page
                    changePageInternal(this.getTotalPages(), true);
                    return;
                }
            }
            if (!inline) {
                renderIfOnline();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to apply a settled page load.", t);
        }
    }

    private void renderIfOnline() {
        Viewer viewer = this.viewer;
        if (viewer == null) {
            return;
        }
        Player player = viewer.getPlayer();
        if (player == null) {
            return;
        }
        CustomInventory customInventory = viewer.getCustomInventory();
        if (customInventory == null) {
            return;
        }
        customInventory.updateInventory(player);
    }

    @Override
    public int getTotalPages() {
        int total = this.pageSource.totalElements();
        if (total == 0) {
            return 1;
        }
        // long math: async totals can approach Integer.MAX_VALUE and overflow the int ceil
        return (int) (((long) total + this.itemPageLimit - 1) / this.itemPageLimit);
    }

    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.pageSource.totalElements()) {
            return -1;
        }
        return index / this.itemPageLimit + 1;
    }

    @Override
    public void setSource(List<T> source) {
        if (this.pageSource instanceof AsyncPageSource) {
            LOGGER.warning("setSource(List) called on an async-built pagination: the async supplier"
                    + " (and its loading item, error callback, timeout and cache) is discarded.");
        }
        this.pageSource = new EagerPageSource<>(source);
        this.currentItems = Collections.emptyList();
        if (this.viewer != null) {
            dispatch(this.currentPage, false);
        }
    }

    @Override
    public List<T> getSource() {
        return this.pageSource.elements();
    }

    @Override
    public boolean isLoading() {
        return this.pageSource.isLoading();
    }

    @Override
    public Throwable lastError() {
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

    private InventoryItem emptyOrFallback() {
        return fallbackItem == null ? InventoryItem.of((ItemStack) null) : fallbackItem.get(viewer);
    }

    private InventoryItem loadingOrFallback() {
        return loadingItem != null ? loadingItem.get(viewer) : emptyOrFallback();
    }

    @Override
    public InventoryItem getFallbackItem() {
        if (fallbackItem == null) return null;
        return this.fallbackItem.get(viewer);
    }
}
```

(The old private helpers `getPageIndex`/`getPageEndIndex`/`getPageMaxIndex` are deleted — slicing moved into the page sources.)

- [ ] **Step 10.4: Apply the Task 9 Step 9.4 builder edits to `NormalPaginationBuilder`**

(Field `asyncOptions`, method `async(...)`, reworked `build()` — code in Task 9 Step 9.4. Add imports `EagerPageSource`, `PageSource`, `java.util.function.Consumer`.)

- [ ] **Step 10.5: Run the new test and the whole module suite**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: `NormalPaginationTest` PASSES (5 tests) and every pre-existing test stays green.

- [ ] **Step 10.6: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/NormalPagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/NormalPaginationBuilder.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/NormalPaginationTest.java
git commit -m "feat(inventory-api): NormalPagination delegates to PageSource with async support"
```

---

### Task 11: `ScrollPagination` refactor + `getPageOfIndex` window fix (+ its builder)

Same shape as Task 10 with scroll math: `offset = page - 1` (the window slides one element per page), totals `max(1, total - window + 1)`, and the corrected `getPageOfIndex`.

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/ScrollPagination.java` (full rewrite)
- Modify: `.../pagination/builder/ScrollPaginationBuilder.java` (per Task 9 Step 9.4)
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/ScrollPaginationTest.java`

- [ ] **Step 11.1: Write the failing test**

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.impl;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.ScrollPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScrollPaginationTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ScrollPagination<Integer> sevenSlotScroll() {
        return new ScrollPaginationBuilder<Integer>()
                .layout(InventoryLayout.ofSlots(0, 1, 2, 3, 4, 5, 6))
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .build();
    }

    private static Viewer mockViewer(InventoryEditor editor) {
        Viewer viewer = mock(Viewer.class);
        lenient().when(viewer.getEditor()).thenReturn(editor);
        lenient().when(viewer.getCustomInventory()).thenReturn(mock(CustomInventory.class));
        return viewer;
    }

    private static List<Integer> sourceOf(int count) {
        List<Integer> source = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(source::add);
        return source;
    }

    @Test
    void totals_windowOfSevenOverTen_isFourPages() {
        ScrollPagination<Integer> pagination = sevenSlotScroll();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(10));

        assertEquals(4, pagination.getTotalPages());
    }

    @Test
    void pageTwo_slidesWindowByOneElement() {
        ScrollPagination<Integer> pagination = sevenSlotScroll();
        InventoryEditor editor = mock(InventoryEditor.class);
        pagination.init(mockViewer(editor));
        pagination.setSource(sourceOf(10));

        pagination.changePage(2);
        pagination.apply();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(captor.capture(), any(), eq(pagination));
        List<InventoryItem> items = captor.getAllValues().get(captor.getAllValues().size() - 1);

        // window slid by ONE element: values 2..8
        for (int i = 0; i < 7; i++) {
            assertEquals(i + 2, items.get(i).getItemStack().getAmount());
        }
    }

    @Test
    void getPageOfIndex_usesFirstVisiblePageSemantics() {
        ScrollPagination<Integer> pagination = sevenSlotScroll();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(10)); // window 7 => 4 pages

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
        ScrollPagination<Integer> pagination = sevenSlotScroll();
        pagination.init(mockViewer(mock(InventoryEditor.class)));
        pagination.setSource(sourceOf(3));

        assertEquals(1, pagination.getTotalPages());
        assertEquals(1, pagination.getPageOfIndex(2));
    }
}
```

- [ ] **Step 11.2: Run to verify failure**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=ScrollPaginationTest"
```

Expected: FAILS on `getPageOfIndex_usesFirstVisiblePageSemantics` (current chunked math returns 2 for index 9) — the others pass against the old impl.

- [ ] **Step 11.3: Rewrite `ScrollPagination` (complete file body below the MIT header)**

Identical to Task 10's `NormalPagination` except: class Javadoc, class name, and the four members shown below. Copy the Task 10 file and apply these deltas (the engineer must end with a complete compiling class — every method listed in Task 10 exists here too, with `NormalPagination` replaced by `ScrollPagination`):

Class Javadoc:

```java
/**
 * Sliding-window paginator. Each "page" advances the visible window by one source element rather
 * than chunking into discrete pages. Pages come from a {@link PageSource}: an in-memory list by
 * default, or an async supplier configured through the builder's {@code async(...)} method.
 */
```

The dispatch request (offset slides by one element per page — this is the line that differs):

```java
    private void dispatch(int rollbackPage, boolean render) {
        PageRequest request = new PageRequest(
                this.currentPage, this.itemPageLimit,
                this.currentPage - 1, this.viewer);
        this.dispatchingThread = Thread.currentThread();
        try {
            this.pageSource.request(request, (result, error) -> onSettle(rollbackPage, result, error));
        } finally {
            this.dispatchingThread = null;
        }
        if (render) {
            renderIfOnline();
        }
    }
```

Totals (window semantics, long math not needed — the subtraction cannot overflow):

```java
    @Override
    public int getTotalPages() {
        int total = this.pageSource.totalElements();
        return Math.max(1, (total - this.itemPageLimit) + 1);
    }
```

First-visible-page semantics:

```java
    @Override
    public int getPageOfIndex(int index) {
        if (index < 0 || index >= this.pageSource.totalElements()) {
            return -1;
        }
        // the first page whose window contains the index; page p shows indices [p-1, p-2+window]
        return Math.max(1, index - this.itemPageLimit + 2);
    }
```

`hasNextPage()` keeps today's form (`return this.currentPage < getTotalPages();`), `hasPreviousPage()` becomes `return this.currentPage > 1;` (equivalent to today's `getPageIndex() > 0`). The old `getPageIndex()` private helper is deleted.

- [ ] **Step 11.4: Apply the Task 9 Step 9.4 builder edits to `ScrollPaginationBuilder`**

- [ ] **Step 11.5: Run the module suite**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: all green.

- [ ] **Step 11.6: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/ScrollPagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/ScrollPaginationBuilder.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/ScrollPaginationTest.java
git commit -m "feat(inventory-api): ScrollPagination delegates to PageSource; fix getPageOfIndex window math"
```

---

### Task 12: `PatternPagination` refactor — closed-form cycle math + pattern rollback (+ its builder)

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPagination.java` (full rewrite)
- Modify: `.../pagination/builder/PatternPaginationBuilder.java` (per Task 9 Step 9.4)
- Existing suite `PatternPaginationTest` pins behavior — it must stay green untouched.

- [ ] **Step 12.1: Run the existing suite first to record the green baseline**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=PatternPaginationTest"
```

Expected: all PASS (baseline).

- [ ] **Step 12.2: Rewrite `PatternPagination` (complete file body below the MIT header)**

Keep the existing class Javadoc (the three paragraphs about patterns, slot order, and `clearLastPattern`) and append one sentence: `Pages come from a {@link PageSource}: an in-memory list by default, or an async supplier configured through the builder's {@code async(...)} method.`

```java
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.impl;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.GenericInventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.item.supplier.InventoryItemSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.EagerPageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageSource;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
public class PatternPagination<T> implements Pagination<T> {

    private static final Logger LOGGER = Logger.getLogger(PatternPagination.class.getName());

    private final InventoryItemSupplier fallbackItem;
    private final GenericInventoryItemSupplier<T> itemSupplier;
    private final List<InventoryLayout> patterns;
    @Getter(AccessLevel.NONE)
    private final int cycleSize;
    @Getter(AccessLevel.NONE)
    private final InventoryItemSupplier loadingItem;
    @Getter(AccessLevel.NONE)
    private PageSource<T> pageSource;
    @Getter(AccessLevel.NONE)
    private volatile List<T> currentItems = Collections.emptyList();
    @Getter(AccessLevel.NONE)
    private volatile Thread dispatchingThread;
    private Viewer viewer;
    private int currentPage = 1;
    private int itemPageLimit;
    private InventoryLayout currentPattern;
    private InventoryLayout lastPattern;

    /**
     * Creates an eager paginator over an initially empty source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param patterns     the layouts cycled across pages, not null and not empty
     */
    public PatternPagination(InventoryItemSupplier fallbackItem,
                             GenericInventoryItemSupplier<T> itemSupplier,
                             List<InventoryLayout> patterns) {
        this(fallbackItem, itemSupplier, patterns, null, EagerPageSource.empty());
    }

    /**
     * Creates a paginator over the given page source.
     *
     * @param fallbackItem item used for empty slots, may be null
     * @param itemSupplier renders one source element, not null
     * @param patterns     the layouts cycled across pages, not null and not empty
     * @param loadingItem  item rendered while an async load is in flight, may be null
     * @param pageSource   where page items come from, not null
     * @throws NullPointerException if {@code pageSource} is null
     */
    public PatternPagination(InventoryItemSupplier fallbackItem,
                             GenericInventoryItemSupplier<T> itemSupplier,
                             List<InventoryLayout> patterns,
                             InventoryItemSupplier loadingItem,
                             PageSource<T> pageSource) {
        this.fallbackItem = fallbackItem;
        this.itemSupplier = itemSupplier;
        this.patterns = patterns;
        this.loadingItem = loadingItem;
        this.pageSource = Objects.requireNonNull(pageSource, "pageSource is required.");
        int slots = 0;
        for (InventoryLayout pattern : patterns) {
            slots += pattern.getSlots().size();
        }
        this.cycleSize = slots;
    }

    @Override
    public void init(Viewer viewer) {
        this.viewer = viewer;
        this.currentPattern = fromPage(currentPage);
        this.itemPageLimit = currentPattern.getSlots().size();
        dispatch(snapshotState(), false);
    }

    @Override
    public void apply() {
        insertPageItems();
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
        InventoryEditor editor = this.viewer.getEditor();
        // snapshot pattern and limit together so a concurrent pattern switch cannot mismatch
        // the item-list size against the layout passed to fillPage
        InventoryLayout pattern = this.currentPattern;
        int limit = pattern.getSlots().size();
        List<T> items = this.currentItems;
        boolean loading = this.pageSource.isLoading();
        List<InventoryItem> inventoryItems = new LinkedList<>();

        for (int i = 0; i < limit; i++) {
            if (loading) {
                inventoryItems.add(loadingOrFallback());
            } else if (i < items.size()) {
                inventoryItems.add(this.itemSupplier.get(this.viewer, items.get(i)));
            } else {
                inventoryItems.add(emptyOrFallback());
            }
        }

        editor.fillPage(inventoryItems, pattern, this);
    }

    private void clearPattern(InventoryLayout pattern) {
        if (pattern == null) return;

        InventoryEditor editor = this.viewer.getEditor();
        List<InventoryItem> fillers = new LinkedList<>();

        for (int i = 0; i < pattern.getSlots().size(); i++) {
            fillers.add(emptyOrFallback());
        }

        editor.fillPage(fillers, pattern, this);
    }

    private InventoryLayout fromPage(int page) {
        return patterns.get((page - 1) % patterns.size());
    }

    @Override
    public void changePage(int page) {
        changePageInternal(page, false);
    }

    private void changePageInternal(int page, boolean forceDispatch) {
        int target = Math.max(1, page);
        if (this.pageSource.totalsKnown()) {
            target = Math.min(target, this.getTotalPages());
        }
        if (!forceDispatch && target == this.currentPage && this.pageSource.isLoading()) {
            return;
        }
        PatternState rollback = snapshotState();

        this.currentPage = target;
        this.lastPattern = this.currentPattern;
        this.currentPattern = fromPage(this.currentPage);
        this.itemPageLimit = this.currentPattern.getSlots().size();
        clearPattern(this.lastPattern);

        dispatch(rollback, true);
    }

    private void dispatch(PatternState rollback, boolean render) {
        PageRequest request = new PageRequest(
                this.currentPage, this.itemPageLimit, getPageIndex(), this.viewer);
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

    private void onSettle(PatternState rollback, PageResult<T> result, Throwable error) {
        boolean inline = Thread.currentThread() == this.dispatchingThread;
        try {
            if (error != null) {
                // wipe the failed page's pattern before restoring the pre-dispatch state, so
                // the old page renders into a clean inventory
                clearPattern(this.currentPattern);
                this.currentPage = rollback.page;
                this.currentPattern = rollback.currentPattern;
                this.lastPattern = rollback.lastPattern;
                this.itemPageLimit = rollback.itemPageLimit;
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
            LOGGER.log(Level.WARNING, "Failed to apply a settled page load.", t);
        }
    }

    private PatternState snapshotState() {
        return new PatternState(this.currentPage, this.currentPattern, this.lastPattern, this.itemPageLimit);
    }

    private void renderIfOnline() {
        Viewer viewer = this.viewer;
        if (viewer == null) {
            return;
        }
        Player player = viewer.getPlayer();
        if (player == null) {
            return;
        }
        CustomInventory customInventory = viewer.getCustomInventory();
        if (customInventory == null) {
            return;
        }
        customInventory.updateInventory(player);
    }

    @Override
    public int getTotalPages() {
        int total = this.pageSource.totalElements();
        if (total == 0) {
            return 1;
        }
        // closed-form cycle math: O(patterns.size()) instead of O(totalPages), which matters
        // once async sources unlock large totals
        int fullCycles = total / this.cycleSize;
        int remainder = total % this.cycleSize;
        int pages = fullCycles * this.patterns.size();
        int consumed = 0;
        int patternIndex = 0;
        while (consumed < remainder) {
            consumed += this.patterns.get(patternIndex).getSlots().size();
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
        while (remainder >= consumed + this.patterns.get(patternIndex).getSlots().size()) {
            consumed += this.patterns.get(patternIndex).getSlots().size();
            patternIndex++;
            page++;
        }
        return page;
    }

    @Override
    public void setSource(List<T> source) {
        if (this.pageSource instanceof AsyncPageSource) {
            LOGGER.warning("setSource(List) called on an async-built pagination: the async supplier"
                    + " (and its loading item, error callback, timeout and cache) is discarded.");
        }
        this.pageSource = new EagerPageSource<>(source);
        this.currentItems = Collections.emptyList();
        if (this.viewer != null) {
            dispatch(snapshotState(), false);
        }
    }

    @Override
    public List<T> getSource() {
        return this.pageSource.elements();
    }

    @Override
    public boolean isLoading() {
        return this.pageSource.isLoading();
    }

    @Override
    public Throwable lastError() {
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

    private int getPageIndex(int page) {
        // closed-form: full cycles plus the partial cycle before this page
        int completedPages = page - 1;
        int fullCycles = completedPages / this.patterns.size();
        int partial = completedPages % this.patterns.size();
        int offset = fullCycles * this.cycleSize;
        for (int i = 0; i < partial; i++) {
            offset += this.patterns.get(i).getSlots().size();
        }
        return offset;
    }

    private int getPageIndex() {
        return getPageIndex(currentPage);
    }

    private InventoryItem emptyOrFallback() {
        return fallbackItem == null ? InventoryItem.of((ItemStack) null) : fallbackItem.get(viewer);
    }

    private InventoryItem loadingOrFallback() {
        return loadingItem != null ? loadingItem.get(viewer) : emptyOrFallback();
    }

    @Override
    public InventoryItem getFallbackItem() {
        if (fallbackItem == null) return null;
        return this.fallbackItem.get(viewer);
    }

    private static final class PatternState {
        private final int page;
        private final InventoryLayout currentPattern;
        private final InventoryLayout lastPattern;
        private final int itemPageLimit;

        private PatternState(int page, InventoryLayout currentPattern,
                             InventoryLayout lastPattern, int itemPageLimit) {
            this.page = page;
            this.currentPattern = currentPattern;
            this.lastPattern = lastPattern;
            this.itemPageLimit = itemPageLimit;
        }
    }
}
```

(Deleted relative to today: `clearLastPattern()` → generalized to `clearPattern(layout)`; `hasPreviousPage(int)`; `getPageMaxIndex` helpers; the O(totalPages) walks.)

- [ ] **Step 12.3: Apply the Task 9 Step 9.4 builder edits to `PatternPaginationBuilder`**

- [ ] **Step 12.4: Run the full module suite — the existing `PatternPaginationTest` is the regression gate**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: all green, especially every pre-existing `PatternPaginationTest` case (closed-form math must be behaviorally identical to the old walks).

- [ ] **Step 12.5: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPagination.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/PatternPaginationBuilder.java
git commit -m "feat(inventory-api): PatternPagination delegates to PageSource with closed-form cycle math"
```

---

### Task 13: Cross-type async integration tests (MockBukkit)

**Files:**
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AsyncPaginationIntegrationTest.java`

- [ ] **Step 13.1: Write the integration tests**

```java
// MIT header
package tech.guilhermekaua.spigotboot.inventoryapi.pagination.impl;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.NormalPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.PatternPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.ScrollPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.AsyncPageSupplier;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AsyncPaginationIntegrationTest {

    private ServerMock server;
    private MockPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TestPlugin");
    }

    @AfterEach
    void tearDown() {
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

    private Viewer mockViewer(InventoryEditor editor, CustomInventory customInventory) {
        Viewer viewer = mock(Viewer.class);
        lenient().when(viewer.getEditor()).thenReturn(editor);
        lenient().when(viewer.getPlugin()).thenReturn(plugin);
        lenient().when(viewer.getCustomInventory()).thenReturn(customInventory);
        return viewer;
    }

    private static InventoryLayout threeSlots() {
        return InventoryLayout.ofSlots(0, 1, 2);
    }

    private static NormalPagination<Integer> asyncNormal(CapturingSupplier supplier) {
        return new NormalPaginationBuilder<Integer>()
                .layout(threeSlots())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options
                        .source(supplier)
                        .loadingItem(viewer -> InventoryItem.of(new ItemStack(Material.CLOCK))))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<InventoryItem> lastFillPage(InventoryEditor editor, Object pagination) {
        ArgumentCaptor<List<InventoryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(editor, atLeastOnce()).fillPage(captor.capture(), any(), eq(pagination));
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    @Test
    void whileLoading_rendersLoadingItemInEverySlot() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor, mock(CustomInventory.class)));
        assertTrue(pagination.isLoading());
        pagination.apply();

        List<InventoryItem> items = lastFillPage(editor, pagination);
        assertEquals(3, items.size());
        for (InventoryItem item : items) {
            assertEquals(Material.CLOCK, item.getItemStack().getType());
        }
    }

    @Test
    void offThreadCompletion_appliesOnNextSchedulerTick() throws Exception {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor, mock(CustomInventory.class)));

        Thread completer = new Thread(() ->
                supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9)));
        completer.start();
        completer.join(5000);

        // the settle was dispatched to the main thread, not applied inline on the completer
        assertTrue(pagination.isLoading(), "state must not be applied before the scheduler tick");
        server.getScheduler().performOneTick();

        assertFalse(pagination.isLoading());
        assertEquals(9, pagination.getTotalElements());
        pagination.apply();
        assertEquals(Material.DIAMOND, lastFillPage(editor, pagination).get(0).getItemStack().getType());
    }

    @Test
    void failedLoad_rollsBackPageAndInvokesErrorCallback() {
        CapturingSupplier supplier = new CapturingSupplier();
        AtomicReference<Throwable> callbackError = new AtomicReference<>();
        NormalPagination<Integer> pagination = new NormalPaginationBuilder<Integer>()
                .layout(threeSlots())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options
                        .source(supplier)
                        .errorCallback((request, error) -> callbackError.set(error)))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9)); // page 1 ok

        pagination.nextPage();
        assertEquals(2, pagination.getCurrentPage());
        supplier.futures.get(1).completeExceptionally(new RuntimeException("db down"));

        assertEquals(1, pagination.getCurrentPage(), "failed navigation must roll back");
        assertNotNull(callbackError.get());
        assertNotNull(pagination.lastError());
        assertFalse(pagination.isLoading());
    }

    @Test
    void navigationBeforeFirstSettle_isHonoredThenReclampedByTotals() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        pagination.changePage(5); // totals unknown: honored optimistically

        assertEquals(5, pagination.getCurrentPage());
        assertEquals(12, supplier.requests.get(1).getOffset(), "page 5 of size 3 => offset 12");

        // totals arrive: only 2 pages exist => re-clamp dispatches page 2
        supplier.futures.get(1).complete(PageResult.of(Arrays.asList(), 6));

        assertEquals(2, pagination.getCurrentPage());
        assertEquals(3, supplier.requests.get(2).getOffset());
    }

    @Test
    void changePage_targetingInFlightPage_doesNotRedispatch() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        int dispatched = supplier.requests.size(); // init's page-1 request

        pagination.changePage(1);
        pagination.changePage(1);

        assertEquals(dispatched, supplier.requests.size(), "click spam on the loading page must not re-dispatch");
    }

    @Test
    void refresh_invalidatesCacheAndForcesRedispatch() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = new NormalPaginationBuilder<Integer>()
                .layout(threeSlots())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options
                        .source(supplier)
                        .cacheTtl(java.time.Duration.ofMinutes(5)))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9)); // page 1 now cached
        int dispatched = supplier.requests.size();

        pagination.refresh();

        // without invalidation this would be a cache hit and the supplier would not be called
        assertEquals(dispatched + 1, supplier.requests.size());
        assertEquals(1, supplier.requests.get(supplier.requests.size() - 1).getPage());
    }

    @Test
    void patternFailedLoad_restoresPagePatternAndLimit() {
        CapturingSupplier supplier = new CapturingSupplier();
        InventoryLayout five = InventoryLayout.ofSlots(0, 1, 2, 3, 4);
        InventoryLayout two = InventoryLayout.ofSlots(9, 10);
        PatternPagination<Integer> pagination = new PatternPaginationBuilder<Integer>()
                .pattern(five)
                .pattern(two)
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options.source(supplier))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3, 4, 5), 9));

        pagination.nextPage(); // switches to pattern `two`, limit 2
        assertEquals(2, pagination.getCurrentPage());
        supplier.futures.get(1).completeExceptionally(new RuntimeException("db down"));

        assertEquals(1, pagination.getCurrentPage(), "failed navigation must roll back the page");
        assertEquals(five, pagination.getCurrentPattern(), "failed navigation must roll back the pattern");
        assertEquals(5, pagination.getItemPageLimit(), "failed navigation must roll back the page limit");
    }

    @Test
    void setSource_afterInit_neverTriggersUpdateInventory() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);
        CustomInventory customInventory = mock(CustomInventory.class);

        pagination.init(mockViewer(mock(InventoryEditor.class), customInventory));
        pagination.setSource(Arrays.asList(1, 2, 3));

        verify(customInventory, never()).updateInventory(any());
        assertFalse(pagination.isLoading());
        assertEquals(3, pagination.getTotalElements());
    }

    @Test
    void scrollSupplier_receivesSlidingWindowOffsets() {
        CapturingSupplier supplier = new CapturingSupplier();
        ScrollPagination<Integer> pagination = new ScrollPaginationBuilder<Integer>()
                .layout(threeSlots())
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options.source(supplier))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 10));

        pagination.changePage(2);

        PageRequest second = supplier.requests.get(1);
        assertEquals(1, second.getOffset(), "scroll slides one element per page");
        assertEquals(3, second.getPageSize());
    }

    @Test
    void patternSupplier_receivesCumulativeOffsetsAndPerPatternSizes() {
        CapturingSupplier supplier = new CapturingSupplier();
        PatternPagination<Integer> pagination = new PatternPaginationBuilder<Integer>()
                .pattern(InventoryLayout.ofSlots(0, 1, 2, 3, 4))      // 5 slots
                .pattern(InventoryLayout.ofSlots(9, 10))               // 2 slots
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .async(options -> options.source(supplier))
                .build();

        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));
        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3, 4, 5), 9));

        pagination.changePage(2);

        PageRequest second = supplier.requests.get(1);
        assertEquals(5, second.getOffset(), "page 2 starts after pattern 1's 5 slots");
        assertEquals(2, second.getPageSize(), "page 2 uses pattern 2's slot count");
    }

    @Test
    void settleAfterLogoff_doesNotThrow() {
        CapturingSupplier supplier = new CapturingSupplier();
        NormalPagination<Integer> pagination = asyncNormal(supplier);

        // mock viewer's getPlayer() defaults to null => "player offline"
        pagination.init(mockViewer(mock(InventoryEditor.class), mock(CustomInventory.class)));

        supplier.futures.get(0).complete(PageResult.of(Arrays.asList(1, 2, 3), 9));

        assertFalse(pagination.isLoading());
    }
}
```

Note on `offThreadCompletion_appliesOnNextSchedulerTick`: the mocked `Viewer.getPlayer()` returns null, so the render is skipped — the assertions target state application, which is the dispatcher-routed part. Note on `navigationBeforeFirstSettle...`: request index 1 is the `changePage(5)` dispatch (index 0 was init's page-1 request, superseded).

- [ ] **Step 13.2: Run the tests**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=AsyncPaginationIntegrationTest"
```

Expected: 11 tests PASS. If `offThreadCompletion` fails on the pre-tick `isLoading` assertion, the dispatcher is applying state inline — verify `BukkitSettleDispatcher` schedules instead of running when `!Bukkit.isPrimaryThread()`.

- [ ] **Step 13.3: Run the full module suite, then commit**

```powershell
.\mvnw.cmd -pl modules/inventory-api/api -am test
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/AsyncPaginationIntegrationTest.java
git commit -m "test(inventory-api): cover async pagination across all three types"
```

---

### Task 14: Sample inventory + full build

**Files:**
- Create: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleAsyncPagedInventory.java`

- [ ] **Step 14.1: Create the sample**

```java
// MIT header
package tech.guilhermekaua.spigotboot.testPlugin.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.annotation.Inventory;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.item.InventoryItem;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.Pagination;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.builder.NormalPaginationBuilder;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageRequest;
import tech.guilhermekaua.spigotboot.inventoryapi.pagination.source.PageResult;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.property.ViewerPropertyMap;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Demonstrates async pagination: every page is "fetched" with a simulated 1.5s database delay,
 * rendering clocks while loading. The page indicator is rendered from {@link #update}, where the
 * page number is settled — never from click callbacks, where it is stale by design.
 */
@Inventory
public final class SampleAsyncPagedInventory extends CustomInventoryImpl {

    private static final Logger LOGGER = Logger.getLogger(SampleAsyncPagedInventory.class.getName());
    private static final String PAGINATION_KEY = "pagination";
    private static final int TOTAL_ELEMENTS = 50;

    private final NormalPaginationBuilder<Integer> paginationBuilder = new NormalPaginationBuilder<Integer>()
            .layout(InventoryLayout.ofGrid(
                    "         ",
                    " OOOOOOO ",
                    " OOOOOOO ",
                    " OOOOOOO ",
                    "         ",
                    "         "
            ))
            .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.EMERALD, value)))
            .async(options -> options
                    .source(SampleAsyncPagedInventory::loadPage)
                    .loadingItem(viewer -> InventoryItem.of(new ItemStack(Material.CLOCK)))
                    .errorCallback((request, error) ->
                            LOGGER.warning("sample page " + request.getPage() + " failed: " + error))
                    .requestTimeout(Duration.ofSeconds(10))
                    .cacheTtl(Duration.ofSeconds(15)));

    private static CompletableFuture<PageResult<Integer>> loadPage(PageRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // simulate a slow database query
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

    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&bSample Async Paged Inventory")
                .rows(6)
                .tickUpdate(20);
    }

    @Override
    protected void firstOpen(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        ViewerPropertyMap propertyMap = viewer.getPropertyMap();
        Pagination<Integer> pagination = propertyMap.get(PAGINATION_KEY, paginationBuilder::build);
        pagination.init(viewer);
    }

    @Override
    protected void configureInventory(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        Pagination<Integer> pagination = viewer.getPropertyMap().get(PAGINATION_KEY);
        pagination.apply();

        editor.setItem(45, pagination.hasPreviousPage() ? InventoryItem.of(new ItemStack(Material.ARROW))
                .defaultCallback(click -> pagination.previousPage())
                : null);

        editor.setItem(53, pagination.hasNextPage() ? InventoryItem.of(new ItemStack(Material.ARROW))
                .defaultCallback(click -> pagination.nextPage())
                : null);

        // page indicator: rendered here (re-runs after every settle), amount = settled page
        editor.setItem(49, InventoryItem.of(new ItemStack(
                pagination.isLoading() ? Material.CLOCK : Material.PAPER,
                Math.max(1, pagination.getCurrentPage()))));
    }

    @Override
    protected void update(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        configureInventory(viewer, editor);
    }
}
```

- [ ] **Step 14.2: Package the test plugin and run the full build**

```powershell
.\mvnw.cmd -pl test-plugin -am package -DskipTests
.\mvnw.cmd clean test
```

Expected: BUILD SUCCESS on both (the second is the repo-wide gate CI runs).

- [ ] **Step 14.3: Commit**

```powershell
git add test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleAsyncPagedInventory.java
git commit -m "feat(test-plugin): add async pagination sample inventory"
```

---

## Verification checklist (after all tasks)

- [ ] `.\mvnw.cmd clean test` green from the repo root (JDK 21).
- [ ] Spec §4 render rules: `setSource` in `update()` cannot recurse (`setSource_afterInit_neverTriggersUpdateInventory` covers the no-render rule).
- [ ] Spec §8: close-listener guard committed before/with the async work (Task 1 is first).
- [ ] Every new public type has the MIT header and complete Javadoc.
- [ ] No `List.of`/records/`orTimeout`/`var` in `modules/inventory-api/api/src/main/java` (Java 8): `grep -rn "List.of(" modules/inventory-api/api/src/main/java` returns nothing.
- [ ] Optionally start a local server with the packaged test-plugin and open the sample to see clocks → emeralds after ~1.5s, arrows navigating, cached pages rendering instantly within 15s.

## Out of scope (per spec §15)

Stale-while-revalidate / cross-viewer caches; Folia scheduler abstraction (the `SettleDispatcher` seam exists for it); back-porting to `local-only/inventory-api-maven`.






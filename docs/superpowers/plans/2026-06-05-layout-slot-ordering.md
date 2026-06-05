# InventoryLayout Interface Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `InventoryLayout` into an interface with two implementations — `GridLayout` (the existing ASCII-grid parser) and `OrderedSlotsLayout` (explicit slot indices in fill order) — so users can define custom fill orders beyond the 26-letter grid vocabulary.

**Architecture:** The interface keeps the exact name/package `tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout`, so every existing type reference compiles unchanged; only `new InventoryLayout(...)` construction sites migrate to `InventoryLayout.ofGrid(...)`. Implementations live in a new `layout.impl` subpackage. `getColumnSizes()` becomes a `default` method derived from `getSlots()`. Approved spec: `docs/superpowers/specs/2026-06-05-layout-slot-ordering-design.md`.

**Tech Stack:** Java (Maven multi-module), Lombok, JUnit 5, Mockito, MockBukkit.

---

## Conventions (read first)

**JDK:** Every `mvnw.cmd` invocation MUST run under JDK 21 — the shell-default JDK crashes Lombok. Prefix every Maven command exactly as shown in the steps:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd ...
```

Run all commands from the repo root `C:\Users\Guilherme\IdeaProjects\spigot-boot`.

**License header:** Every new `.java` file MUST start with this exact 22-line MIT header (identical to every existing file in the repo). In the code blocks below it is abbreviated as `/* <MIT license header> */` — replace that marker with this full block verbatim:

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
```

**Do not touch** the untracked `local-only/` and `inventory-framework/` directories — they are external reference copies that also contain a class named `InventoryLayout`.

**File map (whole plan):**

| Action | Path |
| --- | --- |
| Modify (rewrite) | `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayout.java` |
| Create | `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/GridLayout.java` |
| Create | `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayout.java` |
| Modify | `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/slot/InventorySlot.java` |
| Modify (Javadoc) | `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPagination.java` |
| Modify (Javadoc) | `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/PatternPaginationBuilder.java` |
| Modify, then move | `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayoutTest.java` → `.../layout/impl/GridLayoutTest.java` |
| Create | `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/item/slot/InventorySlotTest.java` |
| Create | `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayoutTest.java` |
| Modify | `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPaginationTest.java` |
| Modify (1 line each) | `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java`, `SampleNormalPagedInventory.java`, `SamplePatternPagedInventory.java` (3 lines) |

---

### Task 1: Pin current grid parsing behavior with regression tests

The interface extraction in Task 2 moves the parser wholesale. Before moving it, pin its observable behavior (letter sort, stable ties, nav chars, defaults, column sizes) so Task 2's green run actually proves preservation. These tests run against the *current* `InventoryLayout` class and must pass immediately.

**Files:**
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayoutTest.java`

- [ ] **Step 1: Rewrite `InventoryLayoutTest.java` with the pinning tests**

Replace the entire file with (keep the existing license header):

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.inventoryapi.layout;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryLayoutTest {

    @Test
    void constructor_rejectsRowWithWrongWidth() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryLayout(
                "    O    ",
                "  OOOO  " // 8 characters wide — not nine, so it is rejected
        ));
    }

    @Test
    void distinctLetters_sortAlphabetically_definingFillOrder() {
        InventoryLayout layout = new InventoryLayout(
                "   CAB   "
        );

        List<Character> letters = layout.getSlots().stream()
                .map(InventorySlot::getLetter)
                .toList();
        List<Integer> slots = layout.getSlots().stream()
                .map(InventorySlot::getSlot)
                .toList();

        assertEquals(List.of('A', 'B', 'C'), letters);
        assertEquals(List.of(4, 5, 3), slots); // A sits at column 4, B at 5, C at 3
    }

    @Test
    void repeatedLetters_keepRowMajorGridOrder() {
        InventoryLayout layout = new InventoryLayout(
                "  OO     ",
                " O       "
        );

        List<Integer> slots = layout.getSlots().stream()
                .map(InventorySlot::getSlot)
                .toList();

        assertEquals(List.of(2, 3, 10), slots); // stable sort: ties keep grid order
    }

    @Test
    void backAndNextChars_overrideDefaultNavSlots() {
        InventoryLayout layout = new InventoryLayout(
                "<       >"
        );

        assertEquals(0, layout.getBackSlot());
        assertEquals(8, layout.getNextSlot());
    }

    @Test
    void navSlots_defaultTo45And53() {
        InventoryLayout layout = new InventoryLayout(
                "    O    "
        );

        assertEquals(45, layout.getBackSlot());
        assertEquals(53, layout.getNextSlot());
    }

    @Test
    void columnSizes_countNamedSlotsPerColumn() {
        InventoryLayout layout = new InventoryLayout(
                " A A     ",
                " B       "
        );

        assertEquals(2, layout.getColumnSizes().get(1));
        assertEquals(1, layout.getColumnSizes().get(3));
        assertFalse(layout.getColumnSizes().containsKey(0));
    }
}
```

- [ ] **Step 2: Run the test class — all 6 tests must pass**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=InventoryLayoutTest" "-DfailIfNoTests=false"
```
Expected: `BUILD SUCCESS`, `Tests run: 6, Failures: 0, Errors: 0` for `InventoryLayoutTest`. If `distinctLetters_sortAlphabetically_definingFillOrder` or any other fails, the pin is wrong — fix the *test* to match actual behavior (this task must not change production code).

- [ ] **Step 3: Commit**

```powershell
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayoutTest.java
git commit -m "test(inventory-api): pin grid layout parsing behavior before refactor"
```

---

### Task 2: Extract the `InventoryLayout` interface and `GridLayout` implementation

Pure refactor: move the parser to `GridLayout`, rewrite `InventoryLayout` as an interface (same name/package), migrate every construction site, move the pinning test next to the impl. The pre-existing suite (including Task 1's pins) is the safety net — no behavior change allowed.

**Files:**
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/GridLayout.java`
- Modify (rewrite): `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayout.java`
- Move: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayoutTest.java` → `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/GridLayoutTest.java`
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPaginationTest.java` (4 construction sites)
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java:56`, `SampleNormalPagedInventory.java:56`, `SamplePatternPagedInventory.java:56,64,72`

- [ ] **Step 1: Create `GridLayout.java`**

The constructor body is the current `InventoryLayout` constructor *minus* the `columnSizes` bookkeeping (lines 52, 77–81 of the old class), which the interface default replaces in Step 2.

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.inventoryapi.layout.impl;

import lombok.Getter;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Parses a row-by-row ASCII grid into a list of named slots plus the back/next navigation slots.
 * Each row must be exactly {@link InventoryLayout#INVENTORY_ROW_WIDTH} characters; characters not
 * matching {@code empty}, {@code back} or {@code next} are treated as named item slots and sorted
 * alphabetically by letter (raw {@code char} order, stable so repeated letters keep row-major grid
 * order).
 */
@Getter
public class GridLayout implements InventoryLayout {

    private final String[] layout;
    private final int backSlot, nextSlot;
    private final List<InventorySlot> slots = new LinkedList<>();

    /**
     * Parses the given grid rows using custom control characters.
     *
     * @param empty  the character marking a slot without an item
     * @param back   the character marking the back-navigation slot
     * @param next   the character marking the next-navigation slot
     * @param layout the grid rows, each exactly {@link InventoryLayout#INVENTORY_ROW_WIDTH} characters wide
     * @throws IllegalArgumentException if a row is not exactly {@link InventoryLayout#INVENTORY_ROW_WIDTH} characters wide
     */
    public GridLayout(char empty, char back, char next, String... layout) {
        this.layout = layout;
        int backSlot = 45, nextSlot = 53; // default

        for (int row = 0; row < layout.length; row++) {
            if (layout[row].length() != INVENTORY_ROW_WIDTH) {
                throw new IllegalArgumentException(
                        "layout row " + row + " must be " + INVENTORY_ROW_WIDTH
                                + " characters wide, but was " + layout[row].length()
                );
            }

            for (int column = 0; column < layout[row].length(); column++) {
                char letter = layout[row].charAt(column);

                int slot = row * INVENTORY_ROW_WIDTH + column;
                if (letter == back) {
                    backSlot = slot;
                } else if (letter == next) {
                    nextSlot = slot;
                } else if (letter != empty) {
                    slots.add(new InventorySlot(letter, slot));
                }
            }
        }

        this.backSlot = backSlot;
        this.nextSlot = nextSlot;

        slots.sort(Comparator.comparing(InventorySlot::getLetter));
    }

    /**
     * Parses the given grid rows using the default {@code ' '} (empty), {@code '<'} (back) and
     * {@code '>'} (next) control characters.
     *
     * @param layout the grid rows, each exactly {@link InventoryLayout#INVENTORY_ROW_WIDTH} characters wide
     * @throws IllegalArgumentException if a row is not exactly {@link InventoryLayout#INVENTORY_ROW_WIDTH} characters wide
     */
    public GridLayout(String... layout) {
        this(' ', '<', '>', layout);
    }
}
```

- [ ] **Step 2: Rewrite `InventoryLayout.java` as an interface**

Replace the entire file (keep the license header):

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.inventoryapi.layout;

import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.impl.GridLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Defines where and in what order pagination items are placed inside an inventory.
 *
 * <p>The core contract is {@link #getSlots()}: an ordered list of fill positions. Consumers such
 * as {@code InventoryEditor#fillPage} place the i-th page item into the i-th slot of that list.
 *
 * <p>Use {@link #ofGrid(String...)} to build a layout from a visual row-by-row ASCII grid whose
 * letters define the fill order alphabetically.
 */
public interface InventoryLayout {

    /**
     * Width of a single chest inventory row in slots.
     */
    int INVENTORY_ROW_WIDTH = 9;

    /**
     * Returns the ordered item slot positions: the i-th page item is placed into the i-th element
     * of this list.
     *
     * @return the ordered fill positions, never null
     */
    List<InventorySlot> getSlots();

    /**
     * @return the inventory slot holding the back-navigation button
     */
    int getBackSlot();

    /**
     * @return the inventory slot holding the next-navigation button
     */
    int getNextSlot();

    /**
     * Counts the item slots in each inventory column, keyed by column index (0-8). Columns
     * without item slots are absent from the map.
     *
     * @return the number of item slots per column
     */
    default Map<Integer, Integer> getColumnSizes() {
        Map<Integer, Integer> columnSizes = new HashMap<>();
        for (InventorySlot slot : getSlots()) {
            columnSizes.merge(slot.getSlot() % INVENTORY_ROW_WIDTH, 1, Integer::sum);
        }
        return columnSizes;
    }

    /**
     * Creates a grid layout using the default {@code ' '} (empty), {@code '<'} (back) and
     * {@code '>'} (next) control characters.
     *
     * @param rows the grid rows, each exactly {@link #INVENTORY_ROW_WIDTH} characters wide
     * @return the parsed grid layout
     * @throws IllegalArgumentException if a row is not exactly {@link #INVENTORY_ROW_WIDTH} characters wide
     */
    static GridLayout ofGrid(String... rows) {
        return new GridLayout(rows);
    }

    /**
     * Creates a grid layout using custom control characters.
     *
     * @param empty the character marking a slot without an item
     * @param back  the character marking the back-navigation slot
     * @param next  the character marking the next-navigation slot
     * @param rows  the grid rows, each exactly {@link #INVENTORY_ROW_WIDTH} characters wide
     * @return the parsed grid layout
     * @throws IllegalArgumentException if a row is not exactly {@link #INVENTORY_ROW_WIDTH} characters wide
     */
    static GridLayout ofGrid(char empty, char back, char next, String... rows) {
        return new GridLayout(empty, back, next, rows);
    }

    /**
     * @throws IllegalArgumentException if {@code layout} defines no item slots (only empty/back/next)
     */
    static void requireItemSlots(InventoryLayout layout) {
        Objects.requireNonNull(layout, "layout");
        if (layout.getSlots().isEmpty()) {
            throw new IllegalArgumentException("layout must define at least one item slot");
        }
    }
}
```

- [ ] **Step 3: Move the pinning test to `GridLayoutTest.java`**

Create `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/GridLayoutTest.java` — the Task 1 file with: package `tech.guilhermekaua.spigotboot.inventoryapi.layout.impl`, an added import for `InventoryLayout`, class renamed `GridLayoutTest`, every `new InventoryLayout(` replaced by `InventoryLayout.ofGrid(`, and every local variable type `InventoryLayout` kept (it is now the interface). Full content:

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.inventoryapi.layout.impl;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GridLayoutTest {

    @Test
    void constructor_rejectsRowWithWrongWidth() {
        assertThrows(IllegalArgumentException.class, () -> InventoryLayout.ofGrid(
                "    O    ",
                "  OOOO  " // 8 characters wide — not nine, so it is rejected
        ));
    }

    @Test
    void distinctLetters_sortAlphabetically_definingFillOrder() {
        InventoryLayout layout = InventoryLayout.ofGrid(
                "   CAB   "
        );

        List<Character> letters = layout.getSlots().stream()
                .map(InventorySlot::getLetter)
                .toList();
        List<Integer> slots = layout.getSlots().stream()
                .map(InventorySlot::getSlot)
                .toList();

        assertEquals(List.of('A', 'B', 'C'), letters);
        assertEquals(List.of(4, 5, 3), slots); // A sits at column 4, B at 5, C at 3
    }

    @Test
    void repeatedLetters_keepRowMajorGridOrder() {
        InventoryLayout layout = InventoryLayout.ofGrid(
                "  OO     ",
                " O       "
        );

        List<Integer> slots = layout.getSlots().stream()
                .map(InventorySlot::getSlot)
                .toList();

        assertEquals(List.of(2, 3, 10), slots); // stable sort: ties keep grid order
    }

    @Test
    void backAndNextChars_overrideDefaultNavSlots() {
        InventoryLayout layout = InventoryLayout.ofGrid(
                "<       >"
        );

        assertEquals(0, layout.getBackSlot());
        assertEquals(8, layout.getNextSlot());
    }

    @Test
    void navSlots_defaultTo45And53() {
        InventoryLayout layout = InventoryLayout.ofGrid(
                "    O    "
        );

        assertEquals(45, layout.getBackSlot());
        assertEquals(53, layout.getNextSlot());
    }

    @Test
    void columnSizes_countNamedSlotsPerColumn() {
        InventoryLayout layout = InventoryLayout.ofGrid(
                " A A     ",
                " B       "
        );

        assertEquals(2, layout.getColumnSizes().get(1));
        assertEquals(1, layout.getColumnSizes().get(3));
        assertFalse(layout.getColumnSizes().containsKey(0));
    }
}
```

Then delete the old file:

```powershell
git rm modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayoutTest.java
```

- [ ] **Step 4: Migrate the 4 construction sites in `PatternPaginationTest.java`**

In `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPaginationTest.java`, replace all 4 occurrences of `new InventoryLayout(` with `InventoryLayout.ofGrid(`:

1. Line ~274 in `centeredDiamondLayout()`: `return new InventoryLayout(` → `return InventoryLayout.ofGrid(`
2. Lines ~313, ~321, ~329 in `samplePatternPagination()`: `.pattern(new InventoryLayout(` → `.pattern(InventoryLayout.ofGrid(`

The closing `)` of each call stays — only the call prefix changes (one `(` is removed together with `new …(`, e.g. `.pattern(new InventoryLayout(` has 2 open parens and `.pattern(InventoryLayout.ofGrid(` also has 2). No import changes (the `InventoryLayout` import already exists and the name is unchanged).

- [ ] **Step 5: Run the module test suite — must be fully green**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd -pl modules/inventory-api/api -am test
```
Expected: `BUILD SUCCESS`; `GridLayoutTest` runs 6 tests, `PatternPaginationTest` runs its existing 8 tests, all green. A green run here is the proof that the parser move and `getColumnSizes()` derivation preserved behavior.

- [ ] **Step 6: Migrate the 5 construction sites in `test-plugin`**

Replace `new InventoryLayout(` with `InventoryLayout.ofGrid(` at:

1. `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java:56` — `.layout(new InventoryLayout(` → `.layout(InventoryLayout.ofGrid(`
2. `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalPagedInventory.java:56` — `.layout(new InventoryLayout(` → `.layout(InventoryLayout.ofGrid(`
3. `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternPagedInventory.java:56,64,72` — `.pattern(new InventoryLayout(` → `.pattern(InventoryLayout.ofGrid(`

Imports are already correct in all three files.

- [ ] **Step 7: Package the test plugin to prove it compiles**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd -pl test-plugin -am package "-DskipTests"
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/ modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/ test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/
git commit -m "refactor(inventory-api): extract InventoryLayout interface with GridLayout impl"
```

---

### Task 3: Add `InventorySlot.NO_LETTER` and the letterless constructor

`OrderedSlotsLayout` (Task 4) needs slots without grid letters.

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/slot/InventorySlot.java`
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/item/slot/InventorySlotTest.java`

- [ ] **Step 1: Write the failing test**

Create `InventorySlotTest.java`:

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.inventoryapi.item.slot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventorySlotTest {

    @Test
    void singleArgConstructor_usesNoLetter() {
        InventorySlot slot = new InventorySlot(7);

        assertEquals(InventorySlot.NO_LETTER, slot.getLetter());
        assertEquals(7, slot.getSlot());
    }

    @Test
    void twoArgConstructor_keepsLetterAndSlot() {
        InventorySlot slot = new InventorySlot('A', 4);

        assertEquals('A', slot.getLetter());
        assertEquals(4, slot.getSlot());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=InventorySlotTest" "-DfailIfNoTests=false"
```
Expected: `BUILD FAILURE` — compilation error (`cannot find symbol: NO_LETTER` and no single-int constructor).

- [ ] **Step 3: Implement the change in `InventorySlot.java`**

Replace the class body (keep header and package):

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.inventoryapi.item.slot;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Pairs an inventory slot with the layout letter that produced it. Grid-based layouts assign the
 * parsed letter; layouts defined directly from slot indices use {@link #NO_LETTER}.
 */
@Data
@AllArgsConstructor
public class InventorySlot {

    /**
     * Placeholder letter for slots that were not produced by grid parsing.
     */
    public static final char NO_LETTER = '\0';

    private final char letter;
    private final int slot;

    /**
     * Creates a slot without a grid letter, using {@link #NO_LETTER}.
     *
     * @param slot the inventory slot index
     */
    public InventorySlot(int slot) {
        this(NO_LETTER, slot);
    }
}
```

Note: `@AllArgsConstructor` is required — once an explicit constructor exists, `@Data` no longer generates the `(char, int)` one that `GridLayout` uses.

- [ ] **Step 4: Run the test to verify it passes**

Same command as Step 2. Expected: `BUILD SUCCESS`, `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/item/slot/InventorySlot.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/item/slot/InventorySlotTest.java
git commit -m "feat(inventory-api): add letterless InventorySlot constructor"
```

---

### Task 4: Add `OrderedSlotsLayout` and the `ofSlots` factory

The new layout type: explicit slot indices, order preserved verbatim, fail-fast validation.

**Files:**
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayout.java`
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/InventoryLayout.java` (add `ofSlots`)
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayoutTest.java`

- [ ] **Step 1: Write the failing tests**

Create `OrderedSlotsLayoutTest.java`:

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.inventoryapi.layout.impl;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedSlotsLayoutTest {

    @Test
    void ofSlots_preservesInputOrderVerbatim() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(36, 27, 18, 9, 10, 11);

        List<Integer> slots = layout.getSlots().stream()
                .map(InventorySlot::getSlot)
                .toList();

        assertEquals(List.of(36, 27, 18, 9, 10, 11), slots);
    }

    @Test
    void ofSlots_rejectsNegativeIndex() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> InventoryLayout.ofSlots(0, -1));

        assertTrue(exception.getMessage().contains("-1"));
    }

    @Test
    void ofSlots_rejectsIndexBeyondLargestChest() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> InventoryLayout.ofSlots(54));

        assertTrue(exception.getMessage().contains("54"));
    }

    @Test
    void ofSlots_rejectsDuplicateIndex() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> InventoryLayout.ofSlots(10, 11, 10));

        assertTrue(exception.getMessage().contains("10"));
    }

    @Test
    void ofSlots_emptyConstructs_butRequireItemSlotsRejects() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots();

        assertTrue(layout.getSlots().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> InventoryLayout.requireItemSlots(layout));
    }

    @Test
    void navSlots_defaultTo45And53() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(0);

        assertEquals(45, layout.getBackSlot());
        assertEquals(53, layout.getNextSlot());
    }

    @Test
    void slots_carryNoLetter() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(7);

        assertEquals(InventorySlot.NO_LETTER, layout.getSlots().get(0).getLetter());
    }

    @Test
    void getColumnSizes_countsItemSlotsPerColumn() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(1, 10, 3);

        assertEquals(2, layout.getColumnSizes().get(1));
        assertEquals(1, layout.getColumnSizes().get(3));
        assertFalse(layout.getColumnSizes().containsKey(0));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=OrderedSlotsLayoutTest" "-DfailIfNoTests=false"
```
Expected: `BUILD FAILURE` — compilation error (`cannot find symbol: class OrderedSlotsLayout` / `method ofSlots`).

- [ ] **Step 3: Create `OrderedSlotsLayout.java`**

```java
/* <MIT license header> */
package tech.guilhermekaua.spigotboot.inventoryapi.layout.impl;

import lombok.Getter;
import tech.guilhermekaua.spigotboot.inventoryapi.item.slot.InventorySlot;
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Layout defined directly from explicit slot indices: the i-th index given to the constructor (or
 * to {@link InventoryLayout#ofSlots(int...)}) is the i-th item placed on a page. Use it for fill
 * orders the grid letter vocabulary cannot express, such as fully custom orders over more than 26
 * slots.
 *
 * <p>Instances are immutable; {@link #withBackSlot(int)} and {@link #withNextSlot(int)} return
 * adjusted copies. Navigation slots default to 45 and 53, matching {@link GridLayout}.
 */
@Getter
public final class OrderedSlotsLayout implements InventoryLayout {

    private static final int MAX_SLOT_EXCLUSIVE = 6 * INVENTORY_ROW_WIDTH;
    private static final int DEFAULT_BACK_SLOT = 45;
    private static final int DEFAULT_NEXT_SLOT = 53;

    private final List<InventorySlot> slots;
    private final int backSlot, nextSlot;

    /**
     * Creates a layout that places items in exactly the order of the given slot indices.
     *
     * @param slots the slot indices in fill order; each must be within 0-53 and unique
     * @throws IllegalArgumentException if an index is negative, {@code >= 54} or duplicated
     */
    public OrderedSlotsLayout(int... slots) {
        this(toInventorySlots(slots), DEFAULT_BACK_SLOT, DEFAULT_NEXT_SLOT);
    }

    private OrderedSlotsLayout(List<InventorySlot> slots, int backSlot, int nextSlot) {
        this.slots = slots;
        this.backSlot = backSlot;
        this.nextSlot = nextSlot;
    }

    /**
     * Returns a copy of this layout with the back-navigation slot replaced.
     *
     * @param backSlot the new back slot; must be within 0-53 and collide with neither an item
     *                 slot nor the next slot
     * @return a new layout with the back slot replaced; this instance is unchanged
     * @throws IllegalArgumentException if {@code backSlot} is out of bounds or collides
     */
    public OrderedSlotsLayout withBackSlot(int backSlot) {
        validateNavSlot(backSlot, this.nextSlot, "backSlot");
        return new OrderedSlotsLayout(this.slots, backSlot, this.nextSlot);
    }

    /**
     * Returns a copy of this layout with the next-navigation slot replaced.
     *
     * @param nextSlot the new next slot; must be within 0-53 and collide with neither an item
     *                 slot nor the back slot
     * @return a new layout with the next slot replaced; this instance is unchanged
     * @throws IllegalArgumentException if {@code nextSlot} is out of bounds or collides
     */
    public OrderedSlotsLayout withNextSlot(int nextSlot) {
        validateNavSlot(nextSlot, this.backSlot, "nextSlot");
        return new OrderedSlotsLayout(this.slots, this.backSlot, nextSlot);
    }

    private void validateNavSlot(int slot, int otherNavSlot, String name) {
        if (slot < 0 || slot >= MAX_SLOT_EXCLUSIVE) {
            throw new IllegalArgumentException(
                    name + " " + slot + " is out of bounds (0-" + (MAX_SLOT_EXCLUSIVE - 1) + ")"
            );
        }
        if (slot == otherNavSlot) {
            throw new IllegalArgumentException(
                    name + " " + slot + " collides with the other navigation slot"
            );
        }
        for (InventorySlot itemSlot : this.slots) {
            if (itemSlot.getSlot() == slot) {
                throw new IllegalArgumentException(name + " " + slot + " collides with an item slot");
            }
        }
    }

    private static List<InventorySlot> toInventorySlots(int[] slots) {
        List<InventorySlot> inventorySlots = new ArrayList<>(slots.length);
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
            inventorySlots.add(new InventorySlot(slot));
        }

        return Collections.unmodifiableList(inventorySlots);
    }
}
```

Note: Task 5 adds the with-er *tests*; the methods are implemented here so the class Javadoc is accurate from the start. The constructor stays public so the interface factory is convenience, not a gate.

- [ ] **Step 4: Add the `ofSlots` factory to `InventoryLayout.java`**

Add the import:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.layout.impl.OrderedSlotsLayout;
```

Add this method after the second `ofGrid` overload:

```java
    /**
     * Creates a layout that places items in exactly the order of the given slot indices, for fill
     * orders the grid letters cannot express (for example fully custom orders over more than 26
     * slots).
     *
     * @param slots the slot indices in fill order; each must be within 0-53 and unique
     * @return the ordered layout
     * @throws IllegalArgumentException if an index is negative, {@code >= 54} or duplicated
     */
    static OrderedSlotsLayout ofSlots(int... slots) {
        return new OrderedSlotsLayout(slots);
    }
```

And extend the interface's class-level Javadoc: after the `ofGrid` sentence, add:

```java
 * <p>Use {@link #ofSlots(int...)} to state the fill order directly as slot indices when the grid
 * vocabulary cannot express it.
```

- [ ] **Step 5: Run the tests to verify they pass**

Same command as Step 2. Expected: `BUILD SUCCESS`, `Tests run: 8, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/ modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayoutTest.java
git commit -m "feat(inventory-api): add OrderedSlotsLayout for explicit slot-order layouts"
```

---

### Task 5: Cover the nav-slot with-ers

The with-ers were implemented in Task 4; this task locks their contract (immutability + all three collision rules) with tests.

**Files:**
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayoutTest.java`

- [ ] **Step 1: Add the with-er tests**

Append inside `OrderedSlotsLayoutTest` (also add `import static org.junit.jupiter.api.Assertions.assertNotSame;`):

```java
    @Test
    void withBackSlot_returnsNewInstance_originalUnchanged() {
        OrderedSlotsLayout original = InventoryLayout.ofSlots(10, 11);
        OrderedSlotsLayout adjusted = original.withBackSlot(36);

        assertNotSame(original, adjusted);
        assertEquals(45, original.getBackSlot());
        assertEquals(36, adjusted.getBackSlot());
        assertEquals(original.getSlots(), adjusted.getSlots());
        assertEquals(original.getNextSlot(), adjusted.getNextSlot());
    }

    @Test
    void withNextSlot_returnsNewInstance_originalUnchanged() {
        OrderedSlotsLayout original = InventoryLayout.ofSlots(10, 11);
        OrderedSlotsLayout adjusted = original.withNextSlot(44);

        assertNotSame(original, adjusted);
        assertEquals(53, original.getNextSlot());
        assertEquals(44, adjusted.getNextSlot());
        assertEquals(original.getBackSlot(), adjusted.getBackSlot());
    }

    @Test
    void withBackSlot_rejectsOutOfBounds() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(10);

        assertThrows(IllegalArgumentException.class, () -> layout.withBackSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> layout.withBackSlot(54));
    }

    @Test
    void withBackSlot_rejectsItemSlotCollision() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(10);

        assertThrows(IllegalArgumentException.class, () -> layout.withBackSlot(10));
    }

    @Test
    void withBackSlot_rejectsNextSlotCollision() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(10);

        assertThrows(IllegalArgumentException.class, () -> layout.withBackSlot(53));
    }

    @Test
    void withNextSlot_rejectsAllCollisions() {
        OrderedSlotsLayout layout = InventoryLayout.ofSlots(10);

        assertThrows(IllegalArgumentException.class, () -> layout.withNextSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> layout.withNextSlot(54));
        assertThrows(IllegalArgumentException.class, () -> layout.withNextSlot(10)); // item slot
        assertThrows(IllegalArgumentException.class, () -> layout.withNextSlot(45)); // back slot
    }
```

- [ ] **Step 2: Run the test class — all 14 tests must pass**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=OrderedSlotsLayoutTest" "-DfailIfNoTests=false"
```
Expected: `BUILD SUCCESS`, `Tests run: 14, Failures: 0, Errors: 0`. If a with-er test fails, fix `OrderedSlotsLayout.validateNavSlot` — not the test.

- [ ] **Step 3: Commit**

```powershell
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/layout/impl/OrderedSlotsLayoutTest.java
git commit -m "test(inventory-api): cover OrderedSlotsLayout nav-slot with-ers"
```

---

### Task 6: Prove pagination is polymorphic over the interface

Add a `PatternPaginationTest` scenario cycling a grid pattern and an ordered-slots pattern — the point of the whole extraction.

**Files:**
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPaginationTest.java`

- [ ] **Step 1: Add the mixed-pattern test**

Append after `diamondPattern_pageTwo_advancesSourceWindow()` (all helpers referenced below — `centeredDiamondLayout`, `sourceOf`, `mockViewer`, `assertMappedSourceValues` — already exist in this class):

```java
    @Test
    void mixedGridAndOrderedSlotsPatterns_pageTwo_fillsOrderedLayoutInGivenOrder() {
        InventoryLayout diamond = centeredDiamondLayout(); // 13 slots, grid-based
        InventoryLayout snake = InventoryLayout.ofSlots(36, 27, 18, 9, 0, 1, 10, 19); // 8 slots
        PatternPagination<Integer> pagination = new PatternPaginationBuilder<Integer>()
                .fallbackItem(viewer -> InventoryItem.of(new ItemStack(Material.BLACK_STAINED_GLASS_PANE)))
                .pattern(diamond)
                .pattern(snake)
                .itemFactory((viewer, value) -> InventoryItem.of(new ItemStack(Material.DIAMOND, value)))
                .build();
        InventoryEditor editor = mock(InventoryEditor.class);

        pagination.init(mockViewer(editor));
        pagination.setSource(sourceOf(21)); // 13 on the diamond page + 8 on the snake page

        assertEquals(2, pagination.getTotalPages());

        pagination.changePage(2);
        pagination.apply();

        List<Integer> expectedValues = IntStream.rangeClosed(14, 21).boxed().toList();
        assertMappedSourceValues(editor, snake, pagination, expectedValues, true);
    }
```

No new imports needed — `InventoryLayout`, `InventoryEditor`, `InventoryItem`, `ItemStack`, `Material`, `IntStream`, `List` and the Mockito statics are already imported.

- [ ] **Step 2: Run the test class — must pass without production changes**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd -pl modules/inventory-api/api -am test "-Dtest=PatternPaginationTest" "-DfailIfNoTests=false"
```
Expected: `BUILD SUCCESS`, `Tests run: 9, Failures: 0, Errors: 0`. Pagination never touched letters, so this should pass as-is; if it fails, debug `PatternPagination`/`fillPage` interaction before changing anything (use superpowers:systematic-debugging).

- [ ] **Step 3: Commit**

```powershell
git add modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPaginationTest.java
git commit -m "test(inventory-api): cover pattern pagination with ordered-slots layouts"
```

---

### Task 7: Reword letter-centric Javadocs and run full verification

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/impl/PatternPagination.java` (class Javadoc)
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/builder/PatternPaginationBuilder.java` (class Javadoc)

- [ ] **Step 1: Reword `PatternPagination` class Javadoc**

Replace this paragraph (currently lines 47–50):

```java
 * <p>The pattern controls only <em>where</em> and in <em>what order</em> items are placed: slots are
 * filled in the letter order defined by {@link InventoryLayout}, which lets a pattern lay items out
 * horizontally, vertically, or in any custom order. Each row must be exactly
 * {@link InventoryLayout#INVENTORY_ROW_WIDTH} characters wide.
```

with:

```java
 * <p>The pattern controls only <em>where</em> and in <em>what order</em> items are placed: slots are
 * filled in the order of the layout's {@link InventoryLayout#getSlots()} list, which lets a pattern
 * lay items out horizontally, vertically, or in any custom order. Grid-based layouts derive that
 * order from their letters; ordered-slots layouts state it explicitly.
```

- [ ] **Step 2: Reword `PatternPaginationBuilder` class Javadoc**

Replace the second paragraph (currently lines 37–40):

```java
 * <p>The paginator cycles through the supplied patterns as the viewer pages forward and fills each
 * page's slots sequentially from the source, so every item is shown exactly once regardless of the
 * pattern shape. Use the slot letters within each {@link InventoryLayout} to control placement order
 * (horizontal, vertical, or custom); see {@link PatternPagination} for details.
```

with:

```java
 * <p>The paginator cycles through the supplied patterns as the viewer pages forward and fills each
 * page's slots sequentially from the source, so every item is shown exactly once regardless of the
 * pattern shape. Each {@link InventoryLayout} defines its own placement order (grid letters or
 * explicit slot indices); see {@link PatternPagination} for details.
```

- [ ] **Step 3: Run the full build**

Run:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd clean test
```
Expected: `BUILD SUCCESS` across all modules.

Then:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.10"; .\mvnw.cmd -pl test-plugin -am package "-DskipTests"
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/pagination/
git commit -m "docs(inventory-api): reword letter-order Javadocs for layout polymorphism"
```

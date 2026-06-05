# Design: `InventoryLayout` interface extraction with `GridLayout` and `OrderedSlotsLayout`

- **Date:** 2026-06-05
- **Status:** Approved
- **Branch:** `feat/inventory-api`
- **Modules affected:** `modules/inventory-api/api`, `test-plugin`

## Problem

`InventoryLayout` defines item placement order by sorting the ASCII-grid letters with
`Comparator.comparing(InventorySlot::getLetter)` — raw `char`-code order. The practical authoring
vocabulary is A–Z: beyond 26 slots the ordering still "works" (`'0'-'9' < 'A'-'Z' < 'a'-'z'`) but
is undocumented and unintuitive, so a user who wants a fully custom fill order over more than 26
slots (a 6-row chest has 54) has no supported way to express it.

The grid is also the *only* way to construct a layout, even though every consumer
(`InventoryEditorImpl.fillPage`, `NormalPagination`, `ScrollPagination`, `PatternPagination`, the
three pagination builders) only ever reads the parsed result: an ordered slot list plus the
back/next nav slots.

## Decisions

Settled during brainstorming, in order:

1. **Alternative definition API** — keep the visual grid for simple cases; add a second, non-grid
   way to define layouts for orders the grid cannot express. (Rejected: merely documenting or
   redefining the char ordering.)
2. **Ordered slot indices** as the input shape — `InventoryLayout.ofSlots(36, 27, 18, 9, ...)`:
   the i-th index is the i-th item placed. (Rejected: row/column builder, shape helpers.)
3. **Nav slots via copy-methods** — `ofSlots(...)` uses the existing 45/53 defaults;
   `withBackSlot(int)` / `withNextSlot(int)` return adjusted copies. (Rejected: leading-int
   overload, always-explicit factory.)
4. **Grid behavior is untouched** — char-code sort, stable ties, byte-identical. The new API is
   the documented answer for >26 ordered slots. (Rejected: changing or documenting grid order.)
5. **Full interface extraction (approach B)** — `InventoryLayout` becomes an interface with two
   implementations. (Rejected: static factory on the existing class (A), subclassing (C).)
6. **Implementation names:** `GridLayout` and `OrderedSlotsLayout`.

## Design

### `InventoryLayout` (interface, keeps name and package)

`tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout` becomes an interface. Keeping
the exact name and package means every existing type reference — `InventoryEditor.fillPage`
signature, pagination fields, builder params, `CustomInventory`'s use of `INVENTORY_ROW_WIDTH` —
compiles unchanged. Only construction sites migrate.

```java
public interface InventoryLayout {
    int INVENTORY_ROW_WIDTH = 9;

    List<InventorySlot> getSlots();   // ordered fill positions — the core contract
    int getBackSlot();
    int getNextSlot();

    default Map<Integer, Integer> getColumnSizes() {
        // derived from getSlots(): column = slot % INVENTORY_ROW_WIDTH, counted per column
    }

    static GridLayout ofGrid(String... rows) { ... }
    static GridLayout ofGrid(char empty, char back, char next, String... rows) { ... }
    static OrderedSlotsLayout ofSlots(int... slots) { ... }

    static void requireItemSlots(InventoryLayout layout) { ... }  // unchanged behavior
}
```

- `getColumnSizes()` becomes a `default` method derived from `getSlots()`. It is consumed by no
  main code (one test assertion only); the derivation (`slot % 9`) is mathematically identical to
  the current parser bookkeeping, which is deleted.
- Factories return the concrete types so `ofSlots(...).withBackSlot(36)` chains without casts.
- `requireItemSlots` keeps its current contract (`NullPointerException` on null,
  `IllegalArgumentException` on empty slots) against the interface type.

### `GridLayout` (in `inventoryapi.layout.impl`)

The current parsing logic moved verbatim: same public constructors
(`(String...)` and `(char empty, char back, char next, String...)`), same row-width validation,
same char-code letter sort with stable ties (repeated letters keep grid order — relied on by
`SamplePagedInventory`'s `"OOOOO"` row), same 45/53 nav defaults, same grid-specific
`getLayout()` row-strings accessor (lives here, **not** on the interface). The `columnSizes`
field and its parser bookkeeping are removed in favour of the interface default.

The `impl` subpackage matches the existing `editor.impl` / `inventory.impl` / `pagination.impl`
convention.

### `OrderedSlotsLayout` (in `inventoryapi.layout.impl`)

- Immutable; holds the indices exactly in the order given — index `i` of the input is the `i`-th
  item `fillPage` places. No sorting ever.
- Construction (via `InventoryLayout.ofSlots`) fails fast with `IllegalArgumentException` on:
  negative index, index `>= 6 * INVENTORY_ROW_WIDTH` (54, the largest chest GUI), or duplicate
  index (two page items fighting for one slot is always a bug).
- Empty `ofSlots()` is allowed at construction, mirroring grid mode (an all-spaces grid also
  constructs); the pagination builders' existing `requireItemSlots` call rejects it at `build()`.
- Nav slots default to 45/53. `withBackSlot(int)` / `withNextSlot(int)` return a **new**
  `OrderedSlotsLayout` with that slot replaced; they validate the argument is within 0–53 and is
  **not one of the item slots** (explicit intent → fail fast on collision). The *defaults* are not
  collision-checked, matching grid mode's existing leniency.
- A layout slot may still exceed the actual inventory size (e.g. slot 40 in a 3-row inventory);
  that remains a write-time `IllegalArgumentException` from `InventoryEditorImpl.validateSlot`,
  same as grid mode.

### `InventorySlot`

Stays a `(char letter, int slot)` pair. Gains:

- `public static final char NO_LETTER = '\0';`
- a convenience constructor `InventorySlot(int slot)` that sets `letter = NO_LETTER`.

`OrderedSlotsLayout` slots carry `NO_LETTER`; nothing downstream reads the letter
(`fillPage` only calls `getSlot()`), so this is cosmetic but documented. Class Javadoc is
reworded since `InventorySlot` is no longer built only by grid parsing.

## Migration

All `new InventoryLayout(...)` sites become `InventoryLayout.ofGrid(...)`:

| Caller | Sites |
| --- | --- |
| `test-plugin` (`SamplePagedInventory`, `SampleNormalPagedInventory`, `SamplePatternPagedInventory`) | 5 |
| `InventoryLayoutTest` | 1 |
| `PatternPaginationTest` | 5 |

No other source changes are required — all remaining references are type references against the
unchanged interface name. The untracked `local-only/` and `inventory-framework/` reference
directories are not touched.

Javadoc updates where letters are presented as *the* ordering mechanism:

- `PatternPagination` class doc: "slots are filled in the letter order defined by
  `InventoryLayout`" → reworded to "slots are filled in the order defined by the layout's
  `getSlots()` list", with letters described as the grid-mode mechanism.
- `PatternPaginationBuilder` class doc: same rewording of "use the slot letters … to control
  placement order".
- `InventorySlot` class doc as noted above.

The module is unreleased (unmerged `feat/inventory-api` branch), so the constructor break is a
clean break with no deprecation shim — consistent with the `rows(int)` design decision.

## Error handling

- `ofSlots` with a negative, `>= 54`, or duplicate index → `IllegalArgumentException` at the call
  site, message naming the offending index.
- `withBackSlot`/`withNextSlot` with an out-of-range index or an index already used as an item
  slot → `IllegalArgumentException`.
- Empty layouts → unchanged: `requireItemSlots` rejects them when a pagination builder builds.
- Slot beyond the actual inventory size → unchanged write-time validation in
  `InventoryEditorImpl`.
- `GridLayout` keeps its existing row-width `IllegalArgumentException`.

## Testing

New and migrated suites in `modules/inventory-api/api`:

- **`OrderedSlotsLayoutTest`** (new):
  - `getSlots()` preserves input order verbatim (including a non-monotonic "snake" order);
  - duplicate, negative, and `>= 54` indices rejected;
  - empty `ofSlots()` constructs, then `requireItemSlots` rejects it;
  - default nav slots are 45/53;
  - `withBackSlot`/`withNextSlot` return new instances, leave the original unchanged, and reject
    out-of-range or item-slot-colliding arguments;
  - slots carry `InventorySlot.NO_LETTER`;
  - derived `getColumnSizes()` counts per column correctly.
- **`GridLayoutTest`**: current `InventoryLayoutTest` migrated/renamed; passing unchanged proves
  the parser move preserved behavior. The diamond-layout `getColumnSizes()` assertion in
  `PatternPaginationTest` now exercises the interface default.
- **`PatternPaginationTest`**: add a scenario where one pattern page is an `OrderedSlotsLayout`,
  including mixed grid + slot-list pattern cycling — proving paginations are genuinely
  polymorphic over the interface.

Existing suites updated per the migration table; CI must stay green.

## Verification

Run with JDK 21:

```
mvnw.cmd -pl modules/inventory-api/api -am test
mvnw.cmd -pl test-plugin -am package
```

## Out of scope

- Changing or documenting grid-mode character ordering beyond A–Z (decision 4).
- With-er methods on `GridLayout` (grid users set nav via `<`/`>` chars; can be added later if
  needed).
- Shape/region helper APIs (`rect`, `column`, fill directions) — rejected in decision 2.
- Documentation outside the module (wiki prompts, README).

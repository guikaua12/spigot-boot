# Design: Row-based sizing for InventorySettings (`rows(int)` replaces `size(int)`)

- **Date:** 2026-06-05
- **Status:** Approved
- **Branch:** `feat/inventory-api`
- **Modules affected:** `modules/inventory-api/api`, `test-plugin`

## Problem

`InventorySettings.size(int)` accepts a raw slot count (e.g. `.size(9 * 6)`). Minecraft chest
inventories always have exactly 9 slots per row and support 1–6 rows, so the slot count is a
derived value, not the authoring concept. Worse, nothing validates it today:
`CustomInventoryImpl.applyConfiguration()` only checks `size > 0`, so an impossible value like
`.size(53)` passes silently and only fails later inside Bukkit, far from the bad call.

The API should ask authors for the row count instead: `.rows(6)`.

## Decisions

1. **Remove `size(int)` entirely** — hard replace. The module is unreleased (lives on the
   unmerged `feat/inventory-api` branch); the only callers are 3 test-plugin samples and 3 test
   classes, all migrated in the same change.
2. **`rows(int)` validates 1–6 and fails fast** — `IllegalArgumentException` at the call site,
   so errors point at the exact line of the bad call.
3. **Read side exposes both units** — `CustomInventory` keeps slot-based `getSize()` (internal
   consumers do slot math) and gains `getRows()`; `InventorySettings` stores and exposes rows
   only.
4. **Rows are canonical in settings** — the rows→slots conversion happens once, in
   `CustomInventoryImpl.applyConfiguration()`, using the existing
   `InventoryLayout.INVENTORY_ROW_WIDTH` constant.

## Design

### `InventorySettings` (authoring surface)

- Delete `size(int)` and `getSize()`; the `int size` field becomes `int rows`.
- Add `rows(int rows)`: throws
  `IllegalArgumentException("rows must be between 1 and 6, got " + rows)` for values outside
  1–6; otherwise stores the value and returns `this` for chaining.
- Add `getRows()`: returns the configured row count, or `0` if `rows(...)` was never called
  (mirrors the existing unset convention).
- Javadocs rewritten in terms of rows ("each row spans 9 slots").

### `CustomInventoryImpl.applyConfiguration()`

- Reads `settings.getRows()`. If `0` (unset), throws
  `IllegalStateException(getClass().getName() + ": configure(...) must set the number of rows.")`
  — same style as the existing title check. Out-of-range values cannot reach this point because
  `rows(...)` already failed fast.
- Converts once: `this.size = rows * InventoryLayout.INVENTORY_ROW_WIDTH`.
- The existing `size` field, its Lombok `@Getter`, and every downstream consumer
  (`ViewerImpl`, `CustomInventoryListener`, pagination) remain unchanged.

### `CustomInventory` interface

- Gains a default method so existing implementers (including the inline stub in
  `InventoryRegistryTest`) keep compiling:

  ```java
  default int getRows() {
      return getSize() / InventoryLayout.INVENTORY_ROW_WIDTH;
  }
  ```

- Full Javadoc with `@return`.

## Migration

| Caller | Change |
| --- | --- |
| `test-plugin` samples (`SamplePagedInventory`, `SampleNormalPagedInventory`, `SamplePatternPagedInventory`) | `.size(9 * 6)` → `.rows(6)` |
| `InventorySettingsTest` | reworked around `rows(...)`/`getRows()` |
| `CustomInventoryImplTest` | `.size(54)` → `.rows(6)`; keep `getSize() == 54` assertion (now proves the conversion) |
| `InventoryRegistryTest` | `.size(54)` → `.rows(6)`; inline `CustomInventory` stub untouched (inherits default `getRows()`) |

## Error handling

- Out-of-range row count → `IllegalArgumentException` thrown immediately by `rows(int)`.
- `configure(...)` never calls `rows(...)` → `IllegalStateException` from
  `applyConfiguration()`, naming the subclass.

## Testing

New focused tests in `modules/inventory-api/api`:

- `rows(0)`, `rows(7)`, `rows(-1)` → `IllegalArgumentException`.
- `rows(1)` and `rows(6)` accepted; `rows(...)` returns `this` for chaining.
- `getRows()` returns `0` when unset.
- `rows(6)` on a configured inventory → `CustomInventoryImpl.getSize() == 54` and
  `getRows() == 6`.
- Interface default: a stub with `getSize() == 9` reports `getRows() == 1`.
- `configure(...)` without `rows(...)` → `IllegalStateException` ("must set the number of
  rows").

Existing suites updated per the migration table; CI must stay green.

## Verification

Run with JDK 21:

```
mvnw.cmd -pl modules/inventory-api/api -am test
mvnw.cmd -pl test-plugin -am package
```

## Out of scope

- Non-chest inventory geometries (hopper 5, dispenser 3×3) — the API targets chest GUIs; every
  current consumer assumes 9-wide rows.
- Keeping a deprecated `size(int)` shim — rejected in favour of a clean break while the module
  is unreleased.
- Documentation outside the module (wiki prompts, README).

# Inventory Rows Sizing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `InventorySettings.size(int)` with row-based `rows(int)` (1–6, fail-fast) and expose `CustomInventory.getRows()`.

**Architecture:** Rows become the canonical authored dimension in `InventorySettings`. `CustomInventoryImpl.applyConfiguration()` converts rows to slots exactly once via `InventoryLayout.INVENTORY_ROW_WIDTH`; the `CustomInventory` interface derives `getRows()` from `getSize()` with a default method. All downstream slot math (`ViewerImpl`, listener, pagination) is untouched. Tasks are ordered so every commit compiles and passes: add `rows()` alongside `size()`, switch the conversion point and migrate callers, then delete `size()`.

**Tech Stack:** Maven multi-module (Maven wrapper), Java, JUnit 5, Lombok.

**Spec:** `docs/superpowers/specs/2026-06-05-inventory-rows-design.md`

**Worker setup (every shell):** the repo must be built with **JDK 21** — the shell-default JDK 25 crashes Lombok 1.18.36 (`TypeTag :: UNKNOWN`). Prefix every Maven command with:

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'
```

All Maven commands below assume the repo root as working directory.

---

### Task 1: Add `rows(int)` / `getRows()` to InventorySettings

`size(int)` stays for now — `CustomInventoryImpl` still reads it; it is deleted in Task 3.

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettings.java`
- Test: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettingsTest.java`

- [ ] **Step 1: Rewrite InventorySettingsTest to its final, rows-based form**

Replace the class body of `InventorySettingsTest.java` (keep the license header and `package`/`import` lines exactly as they are — the imports already cover everything needed) with:

```java
class InventorySettingsTest {

    @Test
    void settersAreFluentAndStoreValues() {
        InventoryConfiguration configuration = new InventoryConfigurationImpl();
        InventorySettings settings = new InventorySettings(configuration);

        assertSame(settings, settings.title("&aShop"));
        assertSame(settings, settings.rows(6));

        assertEquals("&aShop", settings.getTitle());
        assertEquals(6, settings.getRows());
    }

    @Test
    void rowsAcceptsChestRangeBounds() {
        InventorySettings settings = new InventorySettings(new InventoryConfigurationImpl());

        assertEquals(1, settings.rows(1).getRows());
        assertEquals(6, settings.rows(6).getRows());
    }

    @Test
    void rowsRejectsValuesOutsideChestRange() {
        InventorySettings settings = new InventorySettings(new InventoryConfigurationImpl());

        assertThrows(IllegalArgumentException.class, () -> settings.rows(0));
        assertThrows(IllegalArgumentException.class, () -> settings.rows(-1));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> settings.rows(7));
        assertEquals("rows must be between 1 and 6, got 7", error.getMessage());
    }

    @Test
    void getRowsReturnsZeroWhenUnset() {
        InventorySettings settings = new InventorySettings(new InventoryConfigurationImpl());

        assertEquals(0, settings.getRows());
    }

    @Test
    void tickOptionsDelegateToWrappedConfiguration() {
        InventoryConfiguration configuration = new InventoryConfigurationImpl();
        InventorySettings settings = new InventorySettings(configuration);

        assertSame(settings, settings.tickUpdate(20));
        assertSame(settings, settings.tickAsync(true));

        assertEquals(20, configuration.tickUpdate());
        assertTrue(configuration.tickAsync());
        assertSame(configuration, settings.getConfiguration());
    }

    @Test
    void constructorRejectsNullConfiguration() {
        assertThrows(NullPointerException.class, () -> new InventorySettings(null));
    }
}
```

- [ ] **Step 2: Run the module tests to verify the new tests fail**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: **BUILD FAILURE** — test compilation error in `InventorySettingsTest.java`: `cannot find symbol: method rows(int)`.

- [ ] **Step 3: Implement `rows(int)` and `getRows()` in InventorySettings**

In `InventorySettings.java`:

a) Add a `rows` field below the existing `size` field:

```java
    private final InventoryConfiguration configuration;
    private String title;
    private int size;
    private int rows;
```

b) Insert this method directly after the existing `size(int)` method (which stays for now):

```java
    /**
     * Sets the inventory height in rows; each row spans nine slots.
     *
     * @param rows the row count, between 1 and 6 (inclusive)
     * @return this, for chaining
     * @throws IllegalArgumentException if {@code rows} is less than 1 or greater than 6
     */
    public InventorySettings rows(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6, got " + rows);
        }
        this.rows = rows;
        return this;
    }
```

c) Insert this method directly after the existing `getSize()` method:

```java
    /**
     * Returns the configured row count, or {@code 0} if {@link #rows} has not been called.
     *
     * @return the row count
     */
    public int getRows() {
        return rows;
    }
```

- [ ] **Step 4: Run the module tests to verify they pass**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: **BUILD SUCCESS**, all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettings.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettingsTest.java
git commit -m "feat(inventory-api): add row-based sizing to InventorySettings"
```

---

### Task 2: Convert rows→slots in `applyConfiguration()` and migrate all callers

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImpl.java`
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java`
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java`
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalPagedInventory.java`
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternPagedInventory.java`
- Test: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImplTest.java`

- [ ] **Step 1: Update CustomInventoryImplTest to the rows-based contract**

Replace the class body of `CustomInventoryImplTest.java` (keep license header, `package`, and `import` lines unchanged) with:

```java
class CustomInventoryImplTest {

    @Test
    void applyConfigurationPopulatesTitleSizeAndTickConfig() {
        ConfiguredInventory inventory = new ConfiguredInventory();

        inventory.applyConfiguration();

        assertEquals("&aShop", inventory.getTitle());
        assertEquals(54, inventory.getSize());
        assertEquals(20, inventory.getConfiguration().tickUpdate());
    }

    @Test
    void applyConfigurationIsIdempotent() {
        ConfiguredInventory inventory = new ConfiguredInventory();

        inventory.applyConfiguration();
        inventory.applyConfiguration();

        assertEquals(1, inventory.configureCalls);
    }

    @Test
    void applyConfigurationThrowsWhenTitleMissing() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MissingTitleInventory().applyConfiguration());

        assertTrue(error.getMessage().contains(MissingTitleInventory.class.getName()));
    }

    @Test
    void applyConfigurationThrowsWhenRowsMissing() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MissingRowsInventory().applyConfiguration());

        assertTrue(error.getMessage().contains(MissingRowsInventory.class.getName()));
        assertTrue(error.getMessage().contains("must set the number of rows"));
    }

    private static final class ConfiguredInventory extends CustomInventoryImpl {
        private int configureCalls;

        @Override
        protected void configure(@NotNull InventorySettings settings) {
            configureCalls++;
            settings.title("&aShop").rows(6).tickUpdate(20);
        }
    }

    private static final class MissingTitleInventory extends CustomInventoryImpl {
        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.rows(6);
        }
    }

    private static final class MissingRowsInventory extends CustomInventoryImpl {
        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.title("&aShop");
        }
    }
}
```

Notes on what changed: `ConfiguredInventory` and `MissingTitleInventory` now call `.rows(6)` instead of `.size(54)`; `MissingSizeInventory`/`applyConfigurationThrowsWhenSizeNotPositive` became `MissingRowsInventory`/`applyConfigurationThrowsWhenRowsMissing` with an extra message assertion. `MissingRowsInventory` deliberately sets a title so the rows branch (not the title branch) is exercised.

- [ ] **Step 2: Run the module tests to verify the new tests fail**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: **BUILD FAILURE** — 3 failures in `CustomInventoryImplTest` (`applyConfigurationPopulatesTitleSizeAndTickConfig` and `applyConfigurationIsIdempotent` hit `IllegalStateException: ... must set a positive size.` because `rows(6)` does not feed the old size path; `applyConfigurationThrowsWhenRowsMissing` fails its "must set the number of rows" message assertion). `applyConfigurationThrowsWhenTitleMissing` and all other classes still pass.

- [ ] **Step 3: Switch CustomInventoryImpl to read rows and convert once**

In `CustomInventoryImpl.java`:

a) Add the import (alphabetical position, after the `...inventoryapi.inventory.configuration.impl.InventoryConfigurationImpl` import):

```java
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
```

b) In the class-level Javadoc, change

```java
 * Base class for user-defined inventories. Subclasses implement {@link #configure} to declare the
 * title, size and tick configuration, and override {@link #firstOpen}, {@link #configureInventory},
```

to

```java
 * Base class for user-defined inventories. Subclasses implement {@link #configure} to declare the
 * title, row count and tick configuration, and override {@link #firstOpen}, {@link #configureInventory},
```

c) Replace the `applyConfiguration()` Javadoc `@throws` line and body. Change

```java
     * @throws IllegalStateException if {@link #configure} leaves the title unset or the size
     *                               non-positive
     */
    public final void applyConfiguration() {
        if (configured) {
            return;
        }

        InventorySettings settings = new InventorySettings(configuration);
        configure(settings);

        String configuredTitle = settings.getTitle();
        int configuredSize = settings.getSize();

        if (configuredTitle == null) {
            throw new IllegalStateException(getClass().getName() + ": configure(...) must set a title.");
        }
        if (configuredSize <= 0) {
            throw new IllegalStateException(getClass().getName() + ": configure(...) must set a positive size.");
        }

        this.title = configuredTitle;
        this.size = configuredSize;
        this.configured = true;
    }
```

to

```java
     * @throws IllegalStateException if {@link #configure} leaves the title or the row count
     *                               unset
     */
    public final void applyConfiguration() {
        if (configured) {
            return;
        }

        InventorySettings settings = new InventorySettings(configuration);
        configure(settings);

        String configuredTitle = settings.getTitle();
        int configuredRows = settings.getRows();

        if (configuredTitle == null) {
            throw new IllegalStateException(getClass().getName() + ": configure(...) must set a title.");
        }
        if (configuredRows == 0) {
            throw new IllegalStateException(getClass().getName() + ": configure(...) must set the number of rows.");
        }

        this.title = configuredTitle;
        this.size = configuredRows * InventoryLayout.INVENTORY_ROW_WIDTH;
        this.configured = true;
    }
```

(The rows check sits **after** the title check: a missing title is still reported first. Out-of-range rows can never reach this point — `rows(...)` already threw — so `== 0` is the only unset signal.)

d) In the `configure(...)` method Javadoc, change

```java
     * Declares this inventory's title, size and tick configuration. Invoked once after construction
```

to

```java
     * Declares this inventory's title, row count and tick configuration. Invoked once after construction
```

- [ ] **Step 4: Run the module tests — CustomInventoryImplTest passes, InventoryRegistryTest now fails**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: **BUILD FAILURE** — `CustomInventoryImplTest` is green, but `InventoryRegistryTest.lombokStyleInventoryIsConstructedInjectedAndConfigured` fails with `IllegalStateException: ... must set the number of rows.` (its `LombokStyleInventory` still calls `.size(54)`). That is the expected red for the next step.

- [ ] **Step 5: Migrate InventoryRegistryTest's LombokStyleInventory to rows**

In `InventoryRegistryTest.java`, change

```java
        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.title("&aLombok").size(54).tickUpdate(20);
        }
```

to

```java
        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.title("&aLombok").rows(6).tickUpdate(20);
        }
```

The `assertEquals(54, inventory.getSize());` assertion stays — it now proves the rows→slots conversion.

- [ ] **Step 6: Run the module tests to verify everything passes**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: **BUILD SUCCESS**, all tests pass.

- [ ] **Step 7: Migrate the three test-plugin samples**

Apply the same one-line change in each of the three files. In `SamplePagedInventory.java`:

```java
    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&aSample Paged Inventory")
                .rows(6)
                .tickUpdate(20);
    }
```

In `SampleNormalPagedInventory.java`:

```java
    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&aSample Normal Paged Inventory")
                .rows(6)
                .tickUpdate(20);
    }
```

In `SamplePatternPagedInventory.java`:

```java
    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&aSample Pattern Paged Inventory")
                .rows(6)
                .tickUpdate(20);
    }
```

(Only the `.size(9 * 6)` line becomes `.rows(6)` in each; titles differ per file as shown.)

- [ ] **Step 8: Package the test-plugin to verify the samples compile**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl test-plugin -am package
```

Expected: **BUILD SUCCESS**.

- [ ] **Step 9: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImpl.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImplTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalPagedInventory.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternPagedInventory.java
git commit -m "feat(inventory-api): size inventories from configured rows"
```

---

### Task 3: Remove `size(int)` / `getSize()` from InventorySettings

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettings.java`

- [ ] **Step 1: Delete the slot-based API**

In `InventorySettings.java`, delete all three of:

a) the `size` field (`private int size;` — keep `rows`);

b) the `size(int)` method and its Javadoc:

```java
    /**
     * Sets the inventory size in slots (a positive multiple of nine).
     *
     * @param size the slot count
     * @return this, for chaining
     */
    public InventorySettings size(int size) {
        this.size = size;
        return this;
    }
```

c) the `getSize()` method and its Javadoc:

```java
    /**
     * Returns the configured size in slots, or {@code 0} if {@link #size} has not been called.
     *
     * @return the slot count
     */
    public int getSize() {
        return size;
    }
```

- [ ] **Step 2: Verify no stragglers reference the removed API**

```powershell
Get-ChildItem modules\inventory-api, test-plugin -Recurse -Filter *.java | Select-String -Pattern '\.size\(\d'
```

Expected: no output. (The pattern requires a digit after the parenthesis, so it catches leftover `.size(54)` / `.size(9 * 6)` calls while ignoring no-arg collection `.size()` calls. The real safety net is the compile in the next step — the methods no longer exist.)

- [ ] **Step 3: Run the module tests and package the test-plugin**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl test-plugin -am package
```

Expected: **BUILD SUCCESS** for both.

- [ ] **Step 4: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettings.java
git commit -m "refactor(inventory-api): remove slot-based InventorySettings.size"
```

---

### Task 4: `CustomInventory.getRows()` default method

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/CustomInventory.java`
- Test: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java`
- Test: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImplTest.java`

- [ ] **Step 1: Write the failing tests**

a) In `InventoryRegistryTest.java`, add this test method after `lombokStyleInventoryIsConstructedInjectedAndConfigured` (the existing `StubInventory` returns `getSize() == 9`, so the default must derive 1 row):

```java
    @Test
    void stubInventoryDerivesRowsFromSize() {
        assertEquals(1, new StubInventory().getRows());
    }
```

b) In `CustomInventoryImplTest.java`, inside `applyConfigurationPopulatesTitleSizeAndTickConfig`, add one assertion after the `getSize()` one:

```java
        assertEquals("&aShop", inventory.getTitle());
        assertEquals(54, inventory.getSize());
        assertEquals(6, inventory.getRows());
        assertEquals(20, inventory.getConfiguration().tickUpdate());
```

- [ ] **Step 2: Run the module tests to verify they fail**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: **BUILD FAILURE** — test compilation error: `cannot find symbol: method getRows()` (in both test classes).

- [ ] **Step 3: Add the default method to CustomInventory**

In `CustomInventory.java`:

a) Add the import (after the `...inventoryapi.inventory.configuration.InventoryConfiguration` import):

```java
import tech.guilhermekaua.spigotboot.inventoryapi.layout.InventoryLayout;
```

b) In the interface Javadoc, change

```java
 * a GUI's title, size, configuration and per-render lifecycle hooks.
```

to

```java
 * a GUI's title, row count, configuration and per-render lifecycle hooks.
```

c) Add the default method directly after `int getSize();`:

```java
    /**
     * Returns the inventory height in rows; each row spans
     * {@link InventoryLayout#INVENTORY_ROW_WIDTH} slots.
     *
     * @return the row count
     */
    default int getRows() {
        return getSize() / InventoryLayout.INVENTORY_ROW_WIDTH;
    }
```

- [ ] **Step 4: Run the module tests to verify they pass**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl modules/inventory-api/api -am test
```

Expected: **BUILD SUCCESS**, all tests pass (the `InventoryRegistryTest.StubInventory` inline implementer compiles untouched thanks to the default method).

- [ ] **Step 5: Commit**

```powershell
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/CustomInventory.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImplTest.java
git commit -m "feat(inventory-api): add CustomInventory.getRows default accessor"
```

---

### Task 5: Full-suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the entire repository test suite**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd clean test
```

Expected: **BUILD SUCCESS** across all modules.

- [ ] **Step 2: Package the sample plugin**

```powershell
$env:JAVA_HOME = 'C:\Users\Guilherme\.jdks\ms-21.0.10'; .\mvnw.cmd -pl test-plugin -am package
```

Expected: **BUILD SUCCESS**.

- [ ] **Step 3: Confirm a clean working tree**

```powershell
git status --short
```

Expected: no modified tracked files (everything committed in Tasks 1–4).

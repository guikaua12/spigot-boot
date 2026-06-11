# Lombok-Friendly Inventory Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move an inventory's title, size, and tick configuration out of the constructor into an overridable `configure(InventorySettings)` hook, freeing the subclass constructor for Lombok-generated dependency injection.

**Architecture:** `CustomInventoryImpl` becomes no-arg and abstract over a new `protected abstract void configure(InventorySettings)` hook. A new `InventorySettings` holder carries title/size and delegates tick options to the existing `InventoryConfiguration`. The framework calls a final `applyConfiguration()` once, after dependency injection, from `InventoryRegistry`. The old `(title, size)` constructor and `configuration(Consumer)` API are removed (clean break).

**Tech Stack:** Java, Maven (multi-module), Lombok 1.18.36 (`provided`), JUnit 5, the project's own DI container (`DependencyManager`).

---

## Build & Test Notes (read first)

- **JDK 21 is required.** Lombok 1.18.36 crashes on JDK 25 (`TypeTag :: UNKNOWN`). Ensure `JAVA_HOME` points to a JDK 21 before running any Maven command below.
- **Run all commands from the repo root** using the wrapper `mvnw.cmd` (Windows).
- **Run the inventory-api module's tests:**
  `mvnw.cmd -pl :spigot-boot-inventory-api -am test`
  (`-am` builds upstream deps but NOT the downstream `test-plugin`, so the api module's tests run green even while the samples are mid-migration.)
- **Full verification incl. samples:** `mvnw.cmd -pl test-plugin -am test`
- **License header convention:** every source file in this repo begins with the 22-line MIT header below. Prepend it verbatim to every **new** file created in this plan:

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

## File Structure

All paths under `modules/inventory-api/api/` unless noted.

| File | Responsibility | Change |
|------|----------------|--------|
| `src/main/java/.../inventory/configuration/InventorySettings.java` | Fluent holder for title + size; delegates tick options to `InventoryConfiguration` | **Create** |
| `src/main/java/.../inventory/CustomInventory.java` | Inventory contract | **Modify** — remove `configuration(Consumer)` |
| `src/main/java/.../inventory/impl/CustomInventoryImpl.java` | Base class; no-arg, `configure` hook, `applyConfiguration` | **Modify** — full rework |
| `src/main/java/.../registry/InventoryRegistry.java` | Discovers + builds inventories | **Modify** — call `applyConfiguration()` after injection |
| `src/test/java/.../inventory/configuration/InventorySettingsTest.java` | Unit tests for settings | **Create** |
| `src/test/java/.../inventory/impl/CustomInventoryImplTest.java` | Unit tests for `applyConfiguration` | **Create** |
| `src/test/java/.../registry/InventoryRegistryTest.java` | Registry tests | **Modify** — stub fix + DI regression |
| `test-plugin/.../inventory/Sample*Inventory.java` (×3) | Sample inventories | **Modify** — migrate to `configure(...)` |

Package root abbreviation: `.../` = `tech/guilhermekaua/spigotboot/inventoryapi/`.

---

### Task 1: `InventorySettings` holder

**Files:**
- Create: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettings.java`
- Test: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettingsTest.java`

- [ ] **Step 1: Write the failing test**

Create `InventorySettingsTest.java` (prepend the MIT header):

```java
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.impl.InventoryConfigurationImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySettingsTest {

    @Test
    void settersAreFluentAndStoreValues() {
        InventoryConfiguration configuration = new InventoryConfigurationImpl();
        InventorySettings settings = new InventorySettings(configuration);

        assertSame(settings, settings.title("&aShop"));
        assertSame(settings, settings.size(54));

        assertEquals("&aShop", settings.getTitle());
        assertEquals(54, settings.getSize());
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
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl :spigot-boot-inventory-api -am test`
Expected: BUILD FAILURE — compilation error, `cannot find symbol: class InventorySettings`.

- [ ] **Step 3: Write the implementation**

Create `InventorySettings.java` (prepend the MIT header):

```java
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Mutable, fluent description of an inventory's identity and behaviour, populated by a
 * subclass through
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl#configure}.
 *
 * <p>Tick options delegate to the wrapped {@link InventoryConfiguration}, so this type never has
 * to mirror new configuration fields.
 */
public final class InventorySettings {

    private final InventoryConfiguration configuration;
    private String title;
    private int size;

    /**
     * Creates settings that write tick options through to the given configuration.
     *
     * @param configuration the inventory's configuration instance, not null
     */
    public InventorySettings(@NotNull InventoryConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration cannot be null.");
    }

    /**
     * Sets the inventory title.
     *
     * @param title the title; legacy colour codes are allowed
     * @return this, for chaining
     */
    public InventorySettings title(String title) {
        this.title = title;
        return this;
    }

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

    /**
     * Sets the periodic update tick rate.
     *
     * @param ticks the tick interval, or a value {@code <= 0} to disable periodic updates
     * @return this, for chaining
     */
    public InventorySettings tickUpdate(int ticks) {
        this.configuration.tickUpdate(ticks);
        return this;
    }

    /**
     * Sets whether the periodic update runs off the main server thread.
     *
     * @param async {@code true} to dispatch periodic updates asynchronously
     * @return this, for chaining
     */
    public InventorySettings tickAsync(boolean async) {
        this.configuration.tickAsync(async);
        return this;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    @NotNull
    public InventoryConfiguration getConfiguration() {
        return configuration;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl :spigot-boot-inventory-api -am test`
Expected: BUILD SUCCESS — `InventorySettingsTest` passes (2 tests), existing module tests still pass.

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettings.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/configuration/InventorySettingsTest.java
git commit -m "feat(inventory-api): add InventorySettings holder for inventory configuration"
```

---

### Task 2: `configure` hook on `CustomInventoryImpl` + interface clean-break

**Files:**
- Create: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImplTest.java`
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/CustomInventory.java`
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImpl.java`
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java`

> These edits form one atomic compile unit: removing `configuration(Consumer)` from the interface breaks `CustomInventoryImpl` and the test stub simultaneously, so all four files must land before the module compiles.

- [ ] **Step 1: Write the failing test**

Create `CustomInventoryImplTest.java` (prepend the MIT header):

```java
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void applyConfigurationThrowsWhenSizeNotPositive() {
        assertThrows(IllegalStateException.class,
                () -> new MissingSizeInventory().applyConfiguration());
    }

    private static final class ConfiguredInventory extends CustomInventoryImpl {
        private int configureCalls;

        @Override
        protected void configure(@NotNull InventorySettings settings) {
            configureCalls++;
            settings.title("&aShop").size(54).tickUpdate(20);
        }
    }

    private static final class MissingTitleInventory extends CustomInventoryImpl {
        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.size(54);
        }
    }

    private static final class MissingSizeInventory extends CustomInventoryImpl {
        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.title("&aShop");
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl :spigot-boot-inventory-api -am test`
Expected: BUILD FAILURE — compilation errors in `CustomInventoryImplTest` (the `configure` / `applyConfiguration` hooks and the no-arg superclass constructor the fixtures rely on do not exist yet).

- [ ] **Step 3: Remove `configuration(Consumer)` from the `CustomInventory` interface**

Replace the entire body of `CustomInventory.java` (keep the existing MIT header above the package line) with:

```java
package tech.guilhermekaua.spigotboot.inventoryapi.inventory;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.function.Consumer;

/**
 * Top-level abstraction for an inventory definition. Concrete subclasses (typically extending
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl}) describe
 * a GUI's title, size, configuration and per-render lifecycle hooks.
 *
 * <p>Discovered automatically by the module when annotated with
 * {@link tech.guilhermekaua.spigotboot.inventoryapi.annotation.Inventory}.
 */
public interface CustomInventory {

    @NotNull
    String getTitle();

    int getSize();

    @NotNull <T extends InventoryConfiguration> T getConfiguration();

    void defaultOpenInventory(Player player, Viewer viewer, Consumer<Viewer> viewerConsumer);

    void updateInventory(@NotNull Player player);

}
```

- [ ] **Step 4: Rework `CustomInventoryImpl`**

Replace the entire body of `CustomInventoryImpl.java` (keep the existing MIT header above the package line) with:

```java
package tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl;

import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Inject;
import tech.guilhermekaua.spigotboot.inventoryapi.editor.InventoryEditor;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.CustomInventory;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventoryConfiguration;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.impl.InventoryConfigurationImpl;
import tech.guilhermekaua.spigotboot.inventoryapi.registry.ViewerRegistry;
import tech.guilhermekaua.spigotboot.inventoryapi.viewer.Viewer;

import java.util.function.Consumer;

/**
 * Base class for user-defined inventories. Subclasses implement {@link #configure} to declare the
 * title, size and tick configuration, and override {@link #firstOpen}, {@link #configureInventory},
 * {@link #update} and {@link #configureViewer} to populate items and react to renders.
 *
 * <p>The no-arg constructor leaves the subclass constructor free for dependency injection (for
 * example Lombok's {@code @RequiredArgsConstructor}). The framework calls {@link #applyConfiguration}
 * once, after construction and dependency injection, so {@link #configure} may reference injected
 * collaborators.
 *
 * <p>The {@code viewerRegistry} field is filled by the framework after construction when the
 * inventory is registered in {@link tech.guilhermekaua.spigotboot.inventoryapi.registry.InventoryRegistry},
 * so subclass authors never see it.
 */
@Getter
public abstract class CustomInventoryImpl implements CustomInventory {

    private final InventoryConfiguration configuration = new InventoryConfigurationImpl();

    private String title;
    private int size;

    @Getter(AccessLevel.NONE)
    private boolean configured;

    @Inject
    private ViewerRegistry viewerRegistry;

    /**
     * Populates this inventory's title, size and tick configuration by invoking {@link #configure}.
     *
     * <p>Called once by the framework after construction and dependency injection. Idempotent — a
     * second call is a no-op.
     *
     * @throws IllegalStateException if {@link #configure} leaves the title unset or the size
     *                               non-positive
     */
    public final void applyConfiguration() {
        if (configured) {
            return;
        }

        InventorySettings settings = new InventorySettings(configuration);
        configure(settings);

        this.title = settings.getTitle();
        this.size = settings.getSize();

        if (this.title == null) {
            throw new IllegalStateException(getClass().getName() + ": configure(...) must set a title.");
        }
        if (this.size <= 0) {
            throw new IllegalStateException(getClass().getName() + ": configure(...) must set a positive size.");
        }

        this.configured = true;
    }

    /**
     * Declares this inventory's title, size and tick configuration. Invoked once after construction
     * and dependency injection.
     *
     * @param settings the settings to populate, not null
     */
    protected abstract void configure(@NotNull InventorySettings settings);

    @Override
    public void updateInventory(@NotNull Player player) {
        viewerRegistry.findViewer(player).ifPresent(viewer -> {
            if (viewer.getCustomInventory() == this) {
                InventoryEditor editor = viewer.getEditor();
                update(viewer, editor);
                editor.updateAllItemStacks();

                player.updateInventory();
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T extends InventoryConfiguration> @NotNull T getConfiguration() {
        return (T) configuration;
    }

    @Override
    public void defaultOpenInventory(Player player, Viewer viewer, Consumer<Viewer> viewerConsumer) {
        viewer.resetConfigurations();
        this.configureViewer(viewer);

        if (viewerConsumer != null) {
            viewerConsumer.accept(viewer);
        }

        Inventory inventory = viewer.createInventory();
        viewerRegistry.registerViewer(viewer);
        InventoryEditor editor = viewer.getEditor();

        player.openInventory(inventory);

        firstOpen(viewer, editor);
        configureInventory(viewer, editor);
        update(viewer, editor);
    }

    protected void configureViewer(@NotNull Viewer viewer) {
        // empty default — override to seed per-viewer configuration before the inventory opens
    }

    protected void configureInventory(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        // empty default — override to lay out static items on first open
    }

    protected void update(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        // empty default — override to refresh dynamic items on each tick or click
    }

    protected void firstOpen(@NotNull Viewer viewer, @NotNull InventoryEditor editor) {
        // empty default — override for one-shot setup that runs once when the player opens the GUI
    }
}
```

Notes:
- `@Getter` still skips generating `getConfiguration()` because the explicit zero-arg `getConfiguration()` already exists (same as before).
- `@Getter(AccessLevel.NONE)` on `configured` keeps the internal flag off the public API.
- `@RequiredArgsConstructor` is gone; the implicit no-arg constructor is what Lombok-generated subclass constructors call via their implicit `super()`.

- [ ] **Step 5: Update the test stub in `InventoryRegistryTest`**

In `modules/inventory-api/api/src/test/java/.../registry/InventoryRegistryTest.java`, delete the stub's now-orphaned override (it no longer overrides anything). Remove exactly these lines:

```java
        @Override
        public <T extends InventoryConfiguration> void configuration(@NotNull Consumer<T> consumer) { }
```

Leave the rest of `StubInventory` unchanged (the `import java.util.function.Consumer;` is still used by `defaultOpenInventory`).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvnw.cmd -pl :spigot-boot-inventory-api -am test`
Expected: BUILD SUCCESS — `CustomInventoryImplTest` passes (4 tests); `InventoryRegistryTest`, `InventorySettingsTest`, `InventoryConfigurationImplTest` still pass.

- [ ] **Step 7: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/CustomInventory.java modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImpl.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/inventory/impl/CustomInventoryImplTest.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java
git commit -m "feat(inventory-api)!: replace constructor config with configure(InventorySettings) hook"
```

---

### Task 3: Run `configure()` from the registry + end-to-end DI regression

**Files:**
- Modify: `modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistry.java`
- Modify: `modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java`

> Scope note: `registerDiscoveredInventory` is private and needs a full `Context` (plugin + discovery) to drive, so its call site is not directly unit-tested. The regression test below mirrors the exact construct → inject → configure sequence inline using a real `DependencyManager`, proving the Lombok-DI mechanism the one-liner invokes.

- [ ] **Step 1: Write the regression test**

In `InventoryRegistryTest.java`, add these imports (alongside the existing ones):

```java
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
```

Add this test method inside the `InventoryRegistryTest` class:

```java
    @Test
    void lombokStyleInventoryIsConstructedInjectedAndConfigured() throws Exception {
        DependencyManager dependencyManager = new DependencyManager();
        FakeService service = new FakeService();
        dependencyManager.registerDependency(service, null, false);

        Constructor<?> constructor = dependencyManager.findInjectConstructor(LombokStyleInventory.class);
        assertNotNull(constructor);

        Object[] arguments = dependencyManager.resolveArguments(constructor);
        constructor.setAccessible(true);
        LombokStyleInventory inventory = (LombokStyleInventory) constructor.newInstance(arguments);

        inventory.applyConfiguration();

        assertSame(service, inventory.getService());
        assertEquals("&aLombok", inventory.getTitle());
        assertEquals(54, inventory.getSize());
        assertEquals(20, inventory.getConfiguration().tickUpdate());
    }
```

Add these fixtures inside the `InventoryRegistryTest` class (next to `StubInventory`):

```java
    static final class FakeService {
    }

    @Getter
    @RequiredArgsConstructor
    static final class LombokStyleInventory extends CustomInventoryImpl {
        private final FakeService service;

        @Override
        protected void configure(@NotNull InventorySettings settings) {
            settings.title("&aLombok").size(54).tickUpdate(20);
        }
    }
```

- [ ] **Step 2: Run the test to verify the mechanism**

Run: `mvnw.cmd -pl :spigot-boot-inventory-api -am test`
Expected: BUILD SUCCESS — `lombokStyleInventoryIsConstructedInjectedAndConfigured` passes. This proves a Lombok-`@RequiredArgsConstructor` inventory is constructed via the DI container, has its dependency injected, and is fully configured by `applyConfiguration()`. The next step wires that same call into the registry so real discovery uses it.

- [ ] **Step 3: Call `applyConfiguration()` from the registry**

In `InventoryRegistry.java`, add the import:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.impl.CustomInventoryImpl;
```

Then in `registerDiscoveredInventory`, insert the `applyConfiguration()` call between `injectSuperclassDependencies(...)` and `dependencyManager.registerDependency(...)`. The region becomes:

```java
        CustomInventory inventory = (CustomInventory) dependencyManager.initializeBean(definition, rawInstance);
        injectSuperclassDependencies(dependencyManager, inventoryClass, inventory);

        if (inventory instanceof CustomInventoryImpl) {
            ((CustomInventoryImpl) inventory).applyConfiguration();
        }

        dependencyManager.registerDependency(
                inventory,
                BeanUtils.getQualifier(inventoryClass),
                BeanUtils.getIsPrimary(inventoryClass)
        );
        registerInventory(inventory);
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvnw.cmd -pl :spigot-boot-inventory-api -am test`
Expected: BUILD SUCCESS — all inventory-api tests pass.

- [ ] **Step 5: Commit**

```bash
git add modules/inventory-api/api/src/main/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistry.java modules/inventory-api/api/src/test/java/tech/guilhermekaua/spigotboot/inventoryapi/registry/InventoryRegistryTest.java
git commit -m "feat(inventory-api): apply inventory configuration after dependency injection"
```

---

### Task 4: Migrate the sample inventories + full verification

**Files:**
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java`
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalPagedInventory.java`
- Modify: `test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternPagedInventory.java`

Each sample has the same shape of change: add one import, delete the constructor, add a `configure(...)` override. Do all three, then build.

- [ ] **Step 1: Migrate `SamplePagedInventory`**

Add this import (with the other `inventoryapi` imports):

```java
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
```

Delete this constructor:

```java
    public SamplePagedInventory() {
        super("&aSample Paged Inventory", 9 * 6);

        configuration(config -> config.tickUpdate(20));
    }
```

Replace it with this override (same position, above `firstOpen`):

```java
    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&aSample Paged Inventory")
                .size(9 * 6)
                .tickUpdate(20);
    }
```

- [ ] **Step 2: Migrate `SampleNormalPagedInventory`**

Add the import:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
```

Delete this constructor:

```java
    public SampleNormalPagedInventory() {
        super("&aSample Normal Paged Inventory", 9 * 6);

        configuration(config -> config.tickUpdate(20));
    }
```

Replace it with:

```java
    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&aSample Normal Paged Inventory")
                .size(9 * 6)
                .tickUpdate(20);
    }
```

- [ ] **Step 3: Migrate `SamplePatternPagedInventory`**

Add the import:

```java
import tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration.InventorySettings;
```

Delete this constructor:

```java
    public SamplePatternPagedInventory() {
        super("&aSample Pattern Paged Inventory", 9 * 6);

        configuration(config -> config.tickUpdate(20));
    }
```

Replace it with:

```java
    @Override
    protected void configure(@NotNull InventorySettings settings) {
        settings.title("&aSample Pattern Paged Inventory")
                .size(9 * 6)
                .tickUpdate(20);
    }
```

- [ ] **Step 4: Build the sample plugin and run the full module suite**

Run: `mvnw.cmd -pl test-plugin -am test`
Expected: BUILD SUCCESS — `test-plugin` compiles (all three samples) and the inventory-api tests run green via `-am`.

- [ ] **Step 5: Commit**

```bash
git add test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePagedInventory.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SampleNormalPagedInventory.java test-plugin/src/main/java/tech/guilhermekaua/spigotboot/testPlugin/inventory/SamplePatternPagedInventory.java
git commit -m "refactor(test-plugin): migrate sample inventories to configure(InventorySettings)"
```

---

## Spec Coverage Check

| Spec requirement | Task |
|------------------|------|
| `InventorySettings` wraps `InventoryConfiguration`, fluent title/size/tick | Task 1 |
| `configure` is an abstract hook | Task 2 |
| `CustomInventoryImpl` no-arg, non-final title/size, `applyConfiguration` + fail-fast validation, idempotent | Task 2 |
| Remove `configuration(Consumer)` from interface + impl; keep `getConfiguration()` | Task 2 |
| Update `InventoryRegistryTest.StubInventory` | Task 2 |
| `applyConfiguration()` called after DI in the registry (classic cast) | Task 3 |
| End-to-end Lombok-DI regression | Task 3 |
| Migrate the three samples | Task 4 |
| Tests: `InventorySettingsTest`, `CustomInventoryImplTest`, registry regression | Tasks 1–3 |

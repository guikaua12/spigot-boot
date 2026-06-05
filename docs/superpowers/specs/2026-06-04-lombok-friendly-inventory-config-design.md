# Lombok-Friendly Inventory Configuration

**Date:** 2026-06-04
**Module:** `modules/inventory-api`
**Status:** Approved design — ready for implementation planning

## Problem

Inventories extend `CustomInventoryImpl`, which carries `@RequiredArgsConstructor`
(Lombok) and therefore exposes a single constructor `CustomInventoryImpl(String title,
int size)`. Every subclass must call `super(title, size)` and run its tick configuration
inside its own constructor body, e.g.:

```java
public SamplePatternPagedInventory() {
    super("&aSample Pattern Paged Inventory", 9 * 6);
    configuration(config -> config.tickUpdate(20));
}
```

This makes the subclass constructor unavailable for dependency injection via Lombok. A
Lombok-generated constructor (`@RequiredArgsConstructor` / `@AllArgsConstructor`) always
emits an implicit `super()` call and cannot pass `title`/`size` to `super(...)` or run
`configuration(...)`. So any inventory that wants injected collaborators (e.g. a
`UserService`) must hand-write its constructor and maintain those dependencies manually.

## Goals

- Free the subclass constructor so `@RequiredArgsConstructor` (or constructor injection)
  works for inventories.
- Move title, size, and tick configuration into a single overridable hook.
- Keep one clear way to configure an inventory (clean break — no dual code paths).

## Non-Goals

- No change to the per-render lifecycle hooks (`firstOpen`, `configureInventory`,
  `update`, `configureViewer`).
- No new inventory-size validation beyond "must be positive" (the multiple-of-9 Bukkit
  rule stays unvalidated, as it is today). Can be tightened later.
- No support for user-supplied custom `InventoryConfiguration` subtypes (not currently
  possible; out of scope).

## Background: how inventories are built today

1. `InventoryRegistry.registerDiscoveredInventory(...)` resolves a constructor through
   `DependencyManager.findInjectConstructor(...)` — it uses a constructor annotated
   `@Inject`, or the single declared constructor, and throws `MultipleConstructorException`
   if there are multiple unannotated ones.
2. It calls `constructor.newInstance(resolvedArgs)`.
3. It runs `initializeBean(...)` (field/setter injection on the concrete class + bean
   post-processors), then `injectSuperclassDependencies(...)` walks up the superclass
   chain injecting fields — this is how the `@Inject ViewerRegistry viewerRegistry` field
   on `CustomInventoryImpl` gets populated.
4. The instance is registered.

There is **no `@PostConstruct`-style per-bean hook** in the core — only `@OnEnable` /
`@OnDisable`, which are plugin-level and fire at the wrong time. Inventories are built
through this dedicated manual path, not the normal bean lifecycle. The registry is the
single instantiation point and is therefore the correct place to trigger configuration.

`getTitle()` / `getSize()` are read only at open time
(`ViewerImpl.createInventory()` → `configuration.titleInventory(...)` /
`inventorySize(...)`), long after registration. `getConfiguration()` is read once at boot
by `InventoryApiModule.onInitialize` to schedule the tick task.

## Design

### InventorySettings (new)

A fluent holder in `tech.guilhermekaua.spigotboot.inventoryapi.inventory.configuration`
that wraps the existing `InventoryConfiguration` instead of duplicating its fields:

```java
public final class InventorySettings {
    private String title;
    private int size;
    private final InventoryConfiguration configuration; // the inventory's own config instance

    public InventorySettings(@NotNull InventoryConfiguration configuration) { ... }

    public InventorySettings title(String title)     { this.title = title; return this; }
    public InventorySettings size(int size)           { this.size = size;   return this; }
    public InventorySettings tickUpdate(int ticks)    { configuration.tickUpdate(ticks); return this; }
    public InventorySettings tickAsync(boolean async) { configuration.tickAsync(async);  return this; }

    public String getTitle();
    public int getSize();
    public InventoryConfiguration getConfiguration();
}
```

`tickUpdate` / `tickAsync` delegate to the wrapped `InventoryConfiguration`, so future
config options never need to be mirrored here (Open/Closed).

### CustomInventoryImpl changes

- Drop `@RequiredArgsConstructor`. `title` / `size` become non-final fields populated
  after construction. Keep `@Getter`, the `configuration` field
  (`new InventoryConfigurationImpl()`), and the `@Inject viewerRegistry` field.
- Add the abstract hook — **abstract, not a no-op default** — so the compiler forces every
  inventory to set its title/size (they are mandatory and no longer carried by a
  constructor):

```java
protected abstract void configure(@NotNull InventorySettings settings);
```

- Add a framework-internal, idempotent trigger:

```java
public final void applyConfiguration() {
    if (configured) return;
    InventorySettings settings = new InventorySettings(configuration);
    configure(settings);
    this.title = settings.getTitle();
    this.size  = settings.getSize();
    if (title == null) throw new IllegalStateException(getClass().getName() + ": configure(...) must set a title");
    if (size <= 0)     throw new IllegalStateException(getClass().getName() + ": configure(...) must set a positive size");
    configured = true;
}
```

### Wiring — when configure() runs

In `InventoryRegistry.registerDiscoveredInventory`, after `injectSuperclassDependencies(...)`
and before registering the bean:

```java
if (inventory instanceof CustomInventoryImpl) {
    ((CustomInventoryImpl) inventory).applyConfiguration();
}
```

Running after DI lets `configure(...)` reference injected dependencies. A missing title
surfaces at server boot with the offending class name, not on first open. The classic
cast (rather than pattern-matching `instanceof`) avoids any language-level assumption.

The `CustomInventory` interface is intentionally left unchanged by this trigger (no
framework-internal method added to it); the registry already performs type-specific
post-construction work and is the single instantiation point.

### Clean-break removal

Remove `configuration(Consumer)` from both `CustomInventory` and `CustomInventoryImpl`.
Its role is now covered by `InventorySettings.tickUpdate` / `tickAsync`, keeping one clear
way to configure. `getConfiguration()` stays — the module reads it to schedule the tick
task.

### User-facing result

```java
@Inventory
@RequiredArgsConstructor
public final class ShopInventory extends CustomInventoryImpl {
    private final ShopService shop;          // Lombok-generated ctor, DI-injected

    @Override protected void configure(InventorySettings s) {
        s.title("&aShop").size(9 * 6).tickUpdate(20);
    }
}
```

No hand-written constructor; dependencies inject cleanly.

## Migration

Migrate every `@Inventory` subclass in `test-plugin` — the three samples
`SamplePagedInventory`, `SampleNormalPagedInventory`, `SamplePatternPagedInventory`:
delete the constructor and add a `configure(...)` override that sets title, size, and
`tickUpdate(20)`.

Update `InventoryRegistryTest.StubInventory` (implements `CustomInventory` directly): drop
its now-orphaned `configuration(Consumer)` override.

## Testing

- **`InventorySettingsTest`** — fluent setters return `this`; `tickUpdate` / `tickAsync`
  mutate the wrapped `InventoryConfiguration`; getters reflect set values.
- **`CustomInventoryImplTest`** — `applyConfiguration()` populates title/size and the
  configuration from a subclass `configure()`; throws `IllegalStateException` (naming the
  class) on a null title and on a non-positive size; is idempotent (second call is a
  no-op).
- **`InventoryRegistry` regression** — an inventory using `@RequiredArgsConstructor` with
  an injected dependency *and* a `configure()` override is constructed, injected, and
  configured end-to-end, proving the Lombok-DI path works.

## Affected files

- `inventory/configuration/InventorySettings.java` (new)
- `inventory/impl/CustomInventoryImpl.java` (drop `@RequiredArgsConstructor` + the
  `(title,size)` ctor, add `configure` abstract hook + `applyConfiguration`, remove
  `configuration(Consumer)`)
- `inventory/CustomInventory.java` (remove `configuration(Consumer)`)
- `registry/InventoryRegistry.java` (call `applyConfiguration()` after injection)
- `test-plugin` — three sample inventories migrated
- Tests: `InventorySettingsTest` (new), `CustomInventoryImplTest` (new),
  `InventoryRegistryTest` (stub update + regression)

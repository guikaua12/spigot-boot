# Custom Entities Architecture

The `versions/` area is now a generic custom-entity framework for Bukkit `Entity`, not a zombie-only MVP.

The public API is stable across supported server versions, while the version modules decide what the active server can actually spawn and attach. The same plugin jar can keep referencing a logical `CustomEntityBaseType` even when that entity kind only exists on newer servers.

## Design goals

- expose one public definition model: `CustomEntityDefinition<T extends Entity>`
- keep plugin code on Bukkit types and shared controller contracts
- let `spawn(...)` and `entity(...)` work through the same runtime lifecycle
- make hook dispatch capability-driven instead of hard-coded around living zombies
- keep newer entity kinds available in the enum without breaking older servers

## Module layout

- `versions/api`
  - public plugin-facing contracts such as `CustomEntityId`, `CustomEntityBaseType`, `CustomEntityDefinition`, `EntityController`, `CustomEntityContext`, `CustomEntityHandle`, and `CustomEntitySpawnRequest`
  - no NMS classes leak into the public API
- `versions/runtime`
  - version parsing, adapter discovery, definition registration, shared lifecycle handling, and generated native hook plumbing
  - `VersionedEntityPlatform` is the plugin-facing runtime entry point
- `versions/1.8.8`
  - 1.8.8 adapter, metadata registry, and hook binder for that server line
- `versions/1.21.11`
  - 1.21.11 adapter, metadata registry, and hook binder for that server line

## Public API

`CustomEntityDefinition<T extends Entity>` is the canonical definition type.

Available builders:

```java
CustomEntityDefinition.builder(CustomEntityId.of("demo", "orbit-zombie"), CustomEntityBaseType.ZOMBIE)
CustomEntityDefinition.builder(CustomEntityId.of("demo", "orbit-zombie"), EntityType.ZOMBIE)
```

`CustomEntityBaseType` is the stable portability contract. Each enum constant stores:

- a stable logical id
- the primary Bukkit `EntityType` name
- the preferred Bukkit wrapper class name
- optional aliases for renamed Bukkit enum constants

Resolution is lazy:

- `CustomEntityBaseType.fromEntityType(EntityType)` maps the active Bukkit enum back to the logical type
- `entityTypeOrNull()` returns `null` when that type does not exist on the active server version
- `bukkitTypeOrNull()` resolves the Bukkit wrapper type only when it is present

That lets a single plugin jar reference types such as `BREEZE` or `TEXT_DISPLAY` while older servers simply report them as unsupported.

## Runtime entry points

`VersionedEntityPlatform` exposes two public operations:

- `spawn(CustomEntityDefinition<T>, CustomEntitySpawnRequest)`
- `entity(T entity)`

`spawn(...)` is for registered definitions.
`entity(...)` attaches the shared controller runtime to an already-existing supported entity.

There are no zombie-specific public entry points or public runtime specializations anymore.

## Spawn flow

1. Plugin code boots the runtime through `SpigotEntityBootstrap.boot()`.
2. The runtime resolves the active Minecraft version and selects an `EntityVersionAdapter`.
3. Plugin code registers one or more `CustomEntityDefinition<?>` instances.
4. `VersionedEntityPlatform.spawn(...)` creates a `RuntimeNativeEntityLifecycle<T>`.
5. The selected version factory spawns a supported vanilla Bukkit entity with the resolved Bukkit `EntityType`.
6. The version factory replaces the live native handle with a generated lifecycle-aware subclass of the resolved native handle class.
7. The shared runtime binds the controller pipeline and runs the definition initializer plus `onSpawn(...)`.

The generated native subclass is specific to the resolved native superclass on that server version, but plugin code still sees the normal Bukkit wrapper.

## Attach flow

1. Plugin code calls `VersionedEntityPlatform.entity(existingEntity)`.
2. The platform resolves the logical `CustomEntityBaseType` from the Bukkit `EntityType`.
3. The selected version factory reads the current native handle.
4. If the entity is already lifecycle-aware, the existing controlled handle is reused.
5. Otherwise, the factory allocates a generated subclass instance, copies native instance state, rebinds world and Bukkit references, and installs the shared lifecycle.
6. The platform caches the controlled handle by Bukkit identity and native handle identity for future lookups.

`supports(baseType)` means that both generic spawn and generic attach are implemented for that logical type on the active adapter.

## Shared lifecycle model

Both spawned and attached entities run through the same `EntityController<T extends Entity>` contract.

The shared hook surface is:

- `tick`
- `move`
- `push`
- `damage`
- `interact`
- `die`
- `remove`
- `collide`
- `positionPassenger`
- `inventoryChange`

Removal is generic:

- the runtime tracks explicit removal state
- `isRemoved()` falls back to `Entity.isValid()`
- the shared lifecycle does not depend on `LivingEntity.isDead()`

This matters for non-living entities such as projectiles, vehicles, item frames, and dropped items.

## Capability-driven hooks

Not every entity class on every server version exposes every hook.

Each version module has a capability-aware hook binder that inspects the resolved native superclass and only registers hooks that actually exist there. Unsupported hooks are not emulated and are never dispatched.

Examples:

- entity-wide hooks such as tick, move, push, interact, remove, collide, and passenger positioning are bound when the native class exposes them
- living-only hooks such as damage or equipment changes are only bound on native types that actually implement them

This keeps the public controller surface uniform while preventing invalid hook dispatch for unsupported entity and version combinations.

## Version modules

Each version module owns:

- the metadata registry of supported logical `CustomEntityBaseType` values for that server line
- native handle replacement and rebinding logic
- reflective hook resolution for the active native superclass
- generated subclass creation and caching per resolved native type

The registry is version-local by design. The public enum is the union of entity kinds across supported versions, but each adapter only exposes the subset it can actually run on that server.

## Sample plugin usage

The sample plugin now registers its demo entity with the generic definition builder:

```java
VersionedEntityPlatform platform = SpigotEntityBootstrap.boot();

CustomEntityDefinition<Zombie> definition = CustomEntityDefinition.<Zombie>builder(
        CustomEntityId.of("test-plugin", "orbit-zombie"),
        CustomEntityBaseType.ZOMBIE
)
        .initializer(context -> context.bukkitEntity().setCustomName("Orbit Zombie"))
        .controllerFactory(context -> new OrbitingZombieController(
                context.spawnRequest().data().getRequired("trackedPlayerId", UUID.class)
        ))
        .build();

platform.registerDefinition(definition);

Zombie spawned = platform.spawn(
        definition,
        CustomEntitySpawnRequest.builder(location)
                .put("trackedPlayerId", player.getUniqueId())
                .build()
).bukkitEntity();

ControlledEntity<Zombie> attached = platform.entity(existingZombie);
```

The sample still demonstrates zombies, but it does so entirely through the generic API.

## Manual verification matrix

Run the sample plugin on both 1.8.8 and 1.21.11 and verify representative categories:

- hostile mob: `ZOMBIE` or `SKELETON`
- passive or ambient mob: `COW`, `BAT`, or another version-supported passive type
- projectile: `ARROW` or `SNOWBALL`
- vehicle: `BOAT` or `MINECART`
- hanging or display-style entity:
  - 1.8.8: `ITEM_FRAME` or `PAINTING`
  - 1.21.11: `ITEM_FRAME`, `BLOCK_DISPLAY`, `ITEM_DISPLAY`, or `TEXT_DISPLAY`
- dropped item or misc entity: `ITEM`, `EXPERIENCE_ORB`, or `ARMOR_STAND`

For each case verify:

- `supports(baseType)` is accurate for the active server
- generic `spawn(...)` succeeds when the type is supported
- generic `entity(...)` succeeds for an existing entity of that type
- only supported hooks fire for that entity and version combination
- `isRemoved()` behaves correctly for both living and non-living entities

## Extending the framework

To add support for more entities or another server version:

1. add or update the logical entry in `CustomEntityBaseType` when the Bukkit union changes
2. keep the public API generic on `Entity`
3. teach the version module metadata registry whether that logical type is supported there
4. bind only the hooks the resolved native superclass actually exposes
5. verify both generic spawn and generic attach paths for the new support surface

The framework should be extended by version-aware metadata and hook binders, not by reintroducing entity-specific public wrappers.

# Custom Entities Architecture

The `versions/` area now exposes an entity-centric public model instead of the older definition/context/handle-heavy API.

The runtime still keeps the important internal strengths that were already working well:

- multi-version adapter selection through `SpigotEntityBootstrap`
- version-local support matrices for `CustomEntityBaseType`
- generated native subclass replacement for spawn and attach
- capability-driven hook binding per native class
- attach support for existing Bukkit entities
- caching of attached entities by Bukkit and native identity

## Public model

The public API is now split into four clear concepts.

- `EntityTemplate<T extends Entity>`
  - immutable reusable spawn blueprint
  - optional `CustomEntityId` for registration and lookup
  - holds base type, exposed Bukkit type, default initializer, and default controller factory
- `SpawnContext<T extends Entity>`
  - spawn-only read-only context used while creating the controller for one spawn
  - exposes template, base type, Minecraft version, location, and data
- `ControlledEntity<T extends Entity>`
  - live controlled entity abstraction shared by attached and spawned entities
  - exposes Bukkit entity access, controller replacement, state, removal, base type, and hook state
- `SpawnedEntity<T extends Entity>`
  - live controlled entity that came from the spawn pipeline
  - exposes the effective template and immutable spawn options

`CustomEntityContext`, `CustomEntityHandle`, `CustomEntitySpawnContext`, `CustomEntityDefinition`, and `CustomEntitySpawnRequest` still exist as deprecated migration adapters, but they are no longer the primary model.

## Runtime entry points

`VersionedEntityPlatform` now reads around three obvious operations.

- `spawn(CustomEntityBaseType, location, spawn -> ...)`
- `spawn(EntityTemplate<T>, location, spawn -> ...)`
- `get(existingEntity)`

For registered reusable templates, the platform also exposes:

- `register(template)`
- `template(id)`
- `spawn(id, options)`

The old `entity(existing)` method is still available as a deprecated alias for `get(existing)`.

## Usage

### One-off spawn

Use one-off spawn when you do not need registration or reuse.

```java
SpawnedEntity<Zombie> zombie = entities.spawn(
        CustomEntityBaseType.ZOMBIE,
        Zombie.class,
        location,
        spawn -> spawn
                .data("trackedPlayerId", player.getUniqueId())
                .initialize(entity -> entity.bukkitEntity().setCustomName("Orbit Zombie"))
                .controller(ctx -> new OrbitingZombieController(
                        ctx.data().getRequired("trackedPlayerId", UUID.class)
                ))
);
```

If you do not need a concrete Bukkit generic type, `spawn(CustomEntityBaseType, location, ...)` is also available and returns `SpawnedEntity<?>`.

### Reusable template

Use templates when the spawn behavior is reusable or when you want registration and lookup by id.

```java
EntityTemplate<Zombie> orbitZombie = EntityTemplate.<Zombie>builder(
        CustomEntityId.of("test-plugin", "orbit-zombie"),
        CustomEntityBaseType.ZOMBIE
)
        .initialize(entity -> entity.bukkitEntity().setCustomName("Orbit Zombie"))
        .controller(ctx -> new OrbitingZombieController(
                ctx.data().getRequired("trackedPlayerId", UUID.class)
        ))
        .build();

entities.register(orbitZombie);

SpawnedEntity<Zombie> zombie = entities.spawn(
        orbitZombie,
        location,
        spawn -> spawn.data("trackedPlayerId", player.getUniqueId())
);
```

Registration is now optional for direct `spawn(template, ...)`. Register only when you want id-based lookup or central registration.

### Attach existing entity

```java
ControlledEntity<Zombie> attached = entities.get(existingZombie);
attached.setController(new AttachedHookDemoController());
```

This replaces the older `entity(existingZombie)` flow.

## Templates vs live entities

`EntityTemplate<T>` is configuration.

- immutable
- reusable
- safe to register
- does not represent a live entity instance

`ControlledEntity<T>` and `SpawnedEntity<T>` are live runtime objects.

- wrap a real Bukkit entity already in the world
- expose controller replacement and mutable state
- reflect removal and hook status
- should not be treated as reusable definitions

`SpawnContext<T>` only exists while a spawned controller is being created.

- it is not a live entity handle
- it exposes spawn-time data and version information only

## Spawn pipeline

1. plugin code boots the runtime through `SpigotEntityBootstrap.boot()`
2. the runtime resolves the active Minecraft version and selects an `EntityVersionAdapter`
3. `VersionedEntityPlatform.spawn(...)` creates a spawned runtime lifecycle plus a separate immutable `SpawnContext`
4. the selected version factory spawns the vanilla Bukkit entity for the chosen `CustomEntityBaseType`
5. the version factory swaps the native handle for a generated lifecycle-aware subclass
6. the runtime binds controller hooks, runs the initializer, and then calls `EntityController.onSpawn(...)`

The important semantic change is that the spawn-time context is now separate from the live entity object.

## Attach pipeline

1. plugin code calls `VersionedEntityPlatform.get(existingEntity)`
2. the platform resolves the logical `CustomEntityBaseType`
3. the selected version factory reads the current native handle
4. if the entity is already lifecycle-aware, the existing controlled entity is reused
5. otherwise, the version factory allocates a generated subclass, copies native state, rebinds references, and installs the shared lifecycle
6. the platform caches the controlled entity by Bukkit identity and native identity

Attach and spawn still share the same internal controller runtime, but they no longer share a confusing public type hierarchy.

## Internal runtime shape

The public API no longer mirrors the runtime internals.

- `RuntimeAttachedEntityLifecycle<T>` handles live attached entities
- `RuntimeNativeEntityLifecycle<T>` handles live spawned entities
- `RuntimeSpawnContext<T>` is a separate immutable spawn-only object
- both lifecycle classes share behavior through `AbstractRuntimeControlledEntity<T>`

`RuntimeNativeEntityLifecycle<T>` no longer inherits from the attach lifecycle type.

## Capability-driven hooks

Hook dispatch is still capability-driven per server version and per resolved native superclass.

- common hooks such as tick, move, push, interact, remove, collide, and passenger positioning are only bound when available
- living-only hooks such as damage and inventory changes are only bound when the native type supports them
- unsupported hooks are not emulated and are never dispatched

This keeps the controller surface stable while avoiding invalid hook dispatch on unsupported entity and version combinations.

## Migration

### Types

- `CustomEntityDefinition<T>` -> `EntityTemplate<T>`
- `CustomEntitySpawnRequest` -> `SpawnOptions`
- `CustomEntitySpawnContext<T>` -> `SpawnContext<T>`
- `CustomEntityContext<T>` -> `SpawnedEntity<T>`
- `CustomEntityHandle<T>` -> `SpawnedEntity<T>`

### Methods

- `registerDefinition(...)` -> `register(...)`
- `registerDefinitions(...)` -> `registerAll(...)`
- `definition(id)` -> `template(id)`
- `definitions()` -> `templates()`
- `entity(existing)` -> `get(existing)`
- `spawn(definition, request)` -> `spawn(template, location/options)`
- `CustomEntitySpawnRequest.builder(location).put(...)` -> `SpawnOptions.builder(location).data(...)`
  - in most cases prefer inline customization with `spawn -> spawn.data(...)`

### Old to new examples

Old:

```java
CustomEntityDefinition<Zombie> definition = CustomEntityDefinition.<Zombie>builder(
        CustomEntityId.of("demo", "orbit-zombie"),
        CustomEntityBaseType.ZOMBIE
)
        .initializer(context -> context.bukkitEntity().setCustomName("Orbit Zombie"))
        .controllerFactory(context -> new OrbitingZombieController(
                context.spawnRequest().data().getRequired("trackedPlayerId", UUID.class)
        ))
        .build();

platform.registerDefinition(definition);

CustomEntityHandle<Zombie> handle = platform.spawn(
        definition,
        CustomEntitySpawnRequest.builder(location)
                .put("trackedPlayerId", player.getUniqueId())
                .build()
);
```

New:

```java
EntityTemplate<Zombie> template = EntityTemplate.<Zombie>builder(
        CustomEntityId.of("demo", "orbit-zombie"),
        CustomEntityBaseType.ZOMBIE
)
        .initialize(entity -> entity.bukkitEntity().setCustomName("Orbit Zombie"))
        .controller(ctx -> new OrbitingZombieController(
                ctx.data().getRequired("trackedPlayerId", UUID.class)
        ))
        .build();

platform.register(template);

SpawnedEntity<Zombie> zombie = platform.spawn(
        template,
        location,
        spawn -> spawn.data("trackedPlayerId", player.getUniqueId())
);
```

Old attach:

```java
ControlledEntity<Zombie> attached = platform.entity(existingZombie);
attached.setController(new AttachedHookDemoController());
```

New attach:

```java
ControlledEntity<Zombie> attached = platform.get(existingZombie);
attached.setController(new AttachedHookDemoController());
```

## Verification matrix

The automated coverage kept for this refactor verifies:

- registered template lookup
- spawn through reusable templates without mandatory registration
- one-off non-registered spawn flow
- attaching existing entities and identity caching
- removal state for non-living entities through `Entity.isValid()`
- controller swapping preserving state
- initializer and spawn callback ordering
- adapter bootstrap and version selection
- existing 1.8.8 and 1.21.11 module tests

Broader plugin-module verification still depends on the rest of the repository compiling cleanly.

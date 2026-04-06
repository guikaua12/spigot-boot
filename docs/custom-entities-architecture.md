# Custom Entities Architecture

The `versions/` area is now a true multi-version custom-entity framework for native zombies.
The old scaffold spawned a normal Bukkit entity with `world.spawnEntity(...)` and then wrapped it.
That approach is gone for the MVP path.

The new flow creates a real native zombie subclass for the active Minecraft version, inserts it through the native world path, and delegates the native lifecycle into shared library controllers.

## What changed

Previous scaffold:

- Spawned a vanilla Bukkit entity.
- Wrapped the vanilla entity in library abstractions.
- Simulated extensibility around wrappers and goal metadata.
- Did not own the real native entity lifecycle.

Current design:

- Registers logical custom entity definitions in shared Java code.
- Resolves the active Minecraft version at runtime.
- Selects the matching version adapter with `ServiceLoader` discovery or explicit registration.
- Creates a real native zombie subclass for that version.
- Spawns it through the native world insertion path.
- Hooks the real native entity methods for that version and delegates into version-agnostic controllers.
- Exposes the normal Bukkit `Zombie` view backed by that native custom entity.

## Module layout

- `versions/api`
  - Public plugin-facing contracts such as `CustomEntityId`, `CustomEntityDefinition`, `ZombieEntityDefinition`, `EntityController`, `EntityControllerFactory`, `CustomEntityContext`, `CustomEntityHandle`, and `CustomEntitySpawnRequest`.
  - The shared API is version-agnostic and does not expose NMS types.
- `versions/runtime`
  - Owns version parsing, adapter discovery, adapter selection, definition registration, and shared lifecycle orchestration.
  - `VersionedEntityPlatform` is the single runtime entry point for plugin code.
  - `RuntimeNativeEntityLifecycle` bridges a live native entity to the shared controller pipeline.
- `versions/1.8.8`
  - Contains the 1.8.8 adapter and native zombie factory.
  - Extends the real `net.minecraft.server.v1_8_R3.EntityZombie` type for spawned custom zombies.
- `versions/1.21.11`
  - Contains the 1.21.11 adapter and native zombie factory.
  - Extends the real `net.minecraft.world.entity.monster.Zombie` type for spawned custom zombies.

## Runtime flow

1. A plugin boots the runtime with `SpigotEntityBootstrap.boot()`.
2. The runtime parses the server version and discovers version adapters.
3. The plugin registers a logical `ZombieEntityDefinition`.
4. The plugin spawns that definition through `VersionedEntityPlatform`.
5. The selected version module creates a native zombie subclass and inserts it into the native world.
6. The native subclass binds to `RuntimeNativeEntityLifecycle`.
7. The version-specific native method hooks dispatch into the active shared `EntityController`.
8. Plugin code interacts with the normal Bukkit `Zombie`.

## Public API shape

Plugin code stays version-agnostic:

```java
VersionedEntityPlatform platform = SpigotEntityBootstrap.boot();

ZombieEntityDefinition definition = ZombieEntityDefinition.builder(
        CustomEntityId.of("demo", "orbit-zombie")
)
        .initializer(context -> context.bukkitEntity().setCustomName("Orbit Zombie"))
        .controllerFactory(context -> new OrbitingZombieController(
                context.spawnRequest().data().getRequired("trackedPlayerId", UUID.class)
        ))
        .build();

platform.registerDefinition(definition);

Zombie zombie = platform.spawn(
        definition,
        CustomEntitySpawnRequest.builder(location)
                .put("trackedPlayerId", player.getUniqueId())
                .build()
).bukkitEntity();
```

The controller contract is shared across versions:

- `onSpawn`
- `onTick`
- `onMove`
- `onPush`
- `onDamage`
- `onInteract`
- `onDie`
- `onRemove`
- `onCollide`
- `onPositionPassenger`
- `onInventoryChange`

The context gives plugin code:

- Bukkit entity access
- resolved Minecraft version
- logical custom entity id
- spawn metadata
- a state bag
- removal control

## Where native ticking happens

The per-version modules now own the real tick bridge.

### Minecraft 1.8.8

- Native superclass: `net.minecraft.server.v1_8_R3.EntityZombie`
- Native insertion path: native world `addEntity(...)`
- Native tick hook: `EntityZombie#m()`
- Native remove hooks: `die()` and the damage-source death variant when present

The generated native subclass calls `super.m()` first and then delegates to `RuntimeNativeEntityLifecycle#onNativeTick()`.

### Minecraft 1.21.11

- Native superclass: `net.minecraft.world.entity.monster.Zombie`
- Native insertion path: `ServerLevel#addFreshEntity(...)` or the compatible native insertion fallback
- Native tick hook: `Zombie#aiStep()` with `tick()` as a reflective fallback for mapping differences
- Native remove hook: `remove(RemovalReason)`

The generated native subclass calls the native superclass first and then delegates to `RuntimeNativeEntityLifecycle#onNativeTick()`.

## Why the native subclass is generated

The concrete NMS subclass is still real, but it is generated at runtime inside the version module instead of being handwritten in source.
That keeps mapping-sensitive code isolated to each version module while still satisfying the core requirement:

- the spawned entity is a true subclass of the native zombie class
- ticking happens inside the entity's real native lifecycle
- Bukkit receives the normal wrapper backed by that custom native instance

`GeneratedNativeEntityClassFactory` only lives in the entity runtime and is configured by the version modules.
Each version module still chooses the native superclass, constructor shape, insertion method, tick method, and removal hooks.

## Adapter discovery and one-jar packaging

The runtime supports both:

- `ServiceLoader` discovery through `META-INF/services`
- explicit adapter registration through `EntityAdapterRegistry`

The sample plugin shades both supported version modules and merges service descriptors with the Maven shade `ServicesResourceTransformer`.
That lets one plugin jar ship:

- `spigot-boot-entity-v1_8_8`
- `spigot-boot-entity-v1_21_11`

The runtime then selects the correct adapter for the current server version without Paper-only selection logic.

## MVP proof controller

The sample plugin now registers `test-plugin:orbit-zombie`.
Its shared `OrbitingZombieController` runs from the native entity tick and continuously moves the zombie around the tracked player.

Important properties of the proof:

- no Bukkit scheduler drives the controller
- the zombie keeps a normal Bukkit `Zombie` surface
- the controller runs from native tick delegation
- the same shared controller class is used on both supported versions

## Manual verification

Build the sample plugin:

```powershell
./mvnw.cmd -pl test-plugin -am package -DskipTests
```

Use the shaded jar from `test-plugin/target/` on each server below.

### Spigot 1.8.8

1. Start the server with the packaged `test-plugin` jar.
2. Confirm the plugin enables with no startup exception.
3. Join the server and confirm you receive the `Orbit Zombie Wand`.
4. Right-click with the wand and confirm a zombie spawns a few blocks in front of you.
5. Watch the zombie orbit and keep facing/following you without any scheduler task driving it.
6. Sneak-right-click and confirm the zombie is removed.
7. In a debugger or temporary logpoint, inspect the Bukkit zombie's native handle class name and confirm it is the generated `v1_8_8` subclass rather than vanilla `EntityZombie`.

### Paper 1.8.8

Repeat the Spigot 1.8.8 steps and confirm there is no Paper-specific failure.
The controller path should be identical because the runtime and shared controller do not depend on Paper-only APIs.

### Spigot 1.21.11

1. Start the server with the same packaged `test-plugin` jar.
2. Confirm the plugin enables with no adapter-resolution or reflection failure.
3. Join the server and confirm you receive the `Orbit Zombie Wand`.
4. Right-click with the wand and confirm the zombie spawns successfully.
5. Watch the zombie orbit and track you continuously.
6. Sneak-right-click and confirm the zombie is removed.
7. In a debugger or temporary logpoint, inspect the Bukkit zombie's native handle class name and confirm it is the generated `v1_21_11` subclass rather than vanilla `Zombie`.

### Paper 1.21.11

Repeat the Spigot 1.21.11 steps and confirm the same jar works unchanged.
No Paper-only API is required for adapter selection, lifecycle delegation, or the shared controller path.

## Manual acceptance checklist

- plugin enables successfully
- the custom zombie spawns successfully
- the spawned entity is backed by a real native custom subclass
- the visible orbit controller runs from native ticking
- Bukkit code sees a usable `Zombie`
- Spigot 1.21.11 does not fail due to Paper-only API usage
- no scheduler-based fake tick is required

## Adding future entity types

To add a new entity type after the zombie MVP:

1. Add a new logical base type to `versions/api`.
2. Add a typed definition builder similar to `ZombieEntityDefinition`.
3. Extend the shared controller and initializer path only where the Bukkit type changes.
4. Teach each version adapter whether it supports the new base type.
5. Add a version-specific native factory that chooses the correct native superclass, insertion path, tick hook, and removal hooks.
6. Return the normal Bukkit wrapper for that native entity.
7. Add runtime tests for registration and adapter selection, plus a sample controller proof.

The important rule is to extend the framework by adding new native factories per version, not by falling back to a Bukkit spawn-and-wrap design.

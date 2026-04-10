You are working inside Guilherme's multi-module Maven repository `spigot-boot`, and your task is to heavily refactor the `versions` module, specifically the custom entity feature, so the public API feels much closer to BKCommonLib's syntax and ergonomics.

This is not a cosmetic cleanup. This is a structural refactor of the public API, internal runtime model, and naming/hierarchy semantics.

Primary objective
Refactor the custom-entity system so that:
- the public API becomes entity-centric and much less verbose
- the class hierarchy becomes semantically clean
- spawn-time concepts, live-entity concepts, handles, and runtime internals are clearly separated
- the API feels closer to BKCommonLib's create/get/setController style
- the strong parts of the current implementation are preserved internally: multi-version support, version adapters, capability-driven hook binding, attach support, and caching of attached entities

Before changing code, verify these current pain points in the repo
Audit and confirm the current state of:
- `versions/api`
- `versions/runtime`
- `versions/1.8.8`
- `versions/1.21.11`
- `docs/custom-entities-architecture.md`
- the sample usage in `test-plugin`

Specifically verify and treat as refactor targets:
- `CustomEntityContext<T>` mixes spawn-time metadata and live entity control
- `CustomEntityHandle<T>` extends `CustomEntityContext<T>`
- `RuntimeNativeEntityLifecycle<T>` extends `RuntimeAttachedEntityLifecycle<T>`
- sample spawn flow requires definition builder + registration + spawn request builder
- attach flow uses `VersionedEntityPlatform.entity(existingEntity)` and then `setController(...)`
- the public API is definition/context heavy instead of entity-centric
- public semantics are muddied by names like “context” representing a live object

Design target
Use BKCommonLib as the style reference, not as something to copy literally.

The target feeling should be:
- create or spawn entities in one obvious flow
- wrap or hook existing entities in one obvious flow
- assign controllers directly and naturally
- no public lifecycle plumbing
- no public inheritance weirdness where “handle extends context”
- no public type whose name says one thing but whose responsibility is another

Non-negotiable design rules
1. Clean semantic boundaries
   A context must be a context.
   A handle must be a handle.
   A live controlled entity must be a live controlled entity.
   A spawn request/options object must be spawn-only.
   Do not let one object pretend to be all of these at once.

2. Prefer composition over semantic-inheritance abuse
   If spawned and attached entities share runtime behavior, that is fine.
   But do not keep inheritance chains that make the domain model confusing just because they are technically reusable.

3. Keep the public API small and obvious
   The current system exposes too many concepts too early.
   Move lifecycle machinery, hook binder details, reflective dispatch details, and native replacement mechanics behind internal runtime boundaries.

4. Preserve the multi-version architecture
   Keep:
    - `EntityVersionAdapter`
    - version-local support matrices for `CustomEntityBaseType`
    - attach support for existing Bukkit entities
    - generated native subclass approach
    - capability-driven hook dispatch
    - adapter selection through bootstrap/runtime
      These are good internal strengths and should not be lost.

5. Controlled breaking changes are acceptable
   Do not preserve obviously bad public abstractions for compatibility.
   However, provide a migration map and, where low-cost, temporary deprecations or adapters.

Desired public API direction
Design a cleaner API that supports both of these use cases:

A. One-off ergonomic spawning
The user should be able to spawn a controlled entity without mandatory pre-registration ceremony for simple cases.

Target feel, example only:
- `entities.spawn(CustomEntityBaseType.ZOMBIE, location, spec -> ...)`
  or
- `entities.create(CustomEntityBaseType.ZOMBIE)...spawn(location)`
  or
- another similarly clean shape

B. Wrapping existing entities
The user should be able to do something equivalent to:
- `ControlledEntity<Zombie> attached = entities.get(existingZombie);`
- `attached.setController(new AttachedHookDemoController());`

This should feel closer to BKCommonLib’s `CommonEntity.get(...)` flow than the current more awkward public model.

Keep reusable definitions, but make them a reusable advanced path
Reusable named definitions still matter in this project.
So keep the idea of reusable entity definitions/templates/specs, but make them:
- optional for one-off spawns
- clearly separate from live entity instances
- semantically named

For example, a reusable type might become something like:
- `EntityTemplate<T>`
- `CustomEntitySpec<T>`
- `RegisteredEntityType<T>`
  Use the final name that best matches the semantics.

Public model you should converge toward
Refactor toward a model like this conceptually:

1. A reusable spawn blueprint
   Immutable, reusable, named when needed
   Example responsibilities:
    - base type
    - optional id
    - default controller factory
    - default initializer
    - default options

2. A spawn-only context/options object
   Exists only while preparing a spawn
   Example responsibilities:
    - location
    - user data / spawn data
    - minecraft version
    - chosen base type
      This must not also be the live entity handle.

3. A live controlled entity abstraction
   Common contract for both attached and spawned entities
   Example responsibilities:
    - access Bukkit entity
    - current controller
    - state bag
    - remove / removed state
    - base type
    - hooked/attached state

4. A spawned-entity specialization only if actually needed
   Only add a spawned subtype if there is truly spawn-specific information worth exposing.
   For example:
    - definition/template reference
    - definition id
    - spawn metadata reference
      If you keep a spawned subtype, make sure it extends the live entity abstraction cleanly and semantically.

What must go away
Do not keep any equivalent of:
- `Handle extends Context`
- `Context extends SpawnContext + ControlledEntity`
- spawned runtime type inheriting attached runtime type in a way that makes the domain model feel wrong

You may keep internal sharing, but the semantics must become clean.

Strong recommendation for naming cleanup
Do a naming pass across the public API and runtime:
- rename or remove `CustomEntityContext` unless it becomes a true context object
- rename or remove `CustomEntityHandle` unless it becomes a true handle
- rename methods if needed so the public surface reads cleanly
- consider aliasing `entity(existing)` to `get(existing)` or another more natural verb
- avoid names that expose implementation history

Controller API expectations
Keep the controller API powerful, but make the live API around it cleaner.
You can preserve:
- hook contexts
- convenience methods
- internal reflection-based override detection
- version-aware hook binding
  But ensure:
- attaching a controller feels direct and obvious
- a user does not need to understand runtime lifecycle classes
- controller replacement/clearing remains safe
- state survives controller swaps when that is the intended behavior

Spawn-data ergonomics
The current `CustomEntitySpawnRequest.builder(location).put(...).build()` pattern is too heavy for many cases.
Refactor this into something lighter.
Possibilities:
- inline spawn options builder
- simpler fluent data API
- overloaded spawn methods for common cases
- a dedicated light `SpawnOptions`/`SpawnData` type
  Do not remove flexibility, but reduce ceremony.

Suggested target usage examples
These are directionally correct examples. You may improve the final names.

Example 1: one-off spawn
```java
SpawnedEntity<Zombie> zombie = entities.spawn(
        CustomEntityBaseType.ZOMBIE,
        location,
        spawn -> spawn
                .data("trackedPlayerId", player.getUniqueId())
                .initialize(entity -> entity.bukkitEntity().setCustomName("Orbit Zombie"))
                .controller(ctx -> new OrbitingZombieController(
                        ctx.data().getRequired("trackedPlayerId", UUID.class)
                ))
);
```

Example 2: reusable template / definition

```java
EntityTemplate<Zombie> orbitZombie = EntityTemplate.<Zombie>builder(CustomEntityBaseType.ZOMBIE)
        .id("test-plugin", "orbit-zombie")
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

Example 3: attach existing entity

```java
ControlledEntity<Zombie> attached = entities.get(existingZombie);
attached.setController(new AttachedHookDemoController());
```

You do not have to use these exact names.
You do have to deliver this exact level of simplicity and semantic clarity.

Internal architecture guidance
Refactor internals so that:

* spawn and attach share behavior through a clean internal runtime core
* shared logic is extracted into composition-friendly components where appropriate
* public API types do not leak internal runtime lifecycle structures
* version adapters remain thin bridges over the shared runtime
* adapter-specific metadata/hook resolution stays version-local
* attached-entity caching by Bukkit/native identity remains intact

Migration strategy
Provide a deliberate migration path from the old API to the new API.
At minimum:

* list every renamed/removed public type
* list every renamed/removed public method
* show old usage -> new usage examples
* deprecate instead of instantly deleting when it keeps the transition cheap and clean
* remove dead abstractions that only exist because of the old design

Tests that must continue to pass or be replaced with equivalent stronger coverage
Refactor or add tests for:

* registering reusable definitions/templates
* spawning registered definitions/templates
* one-off non-registered spawn flow
* attaching existing entities
* caching attached entities by Bukkit/native identity
* non-living removal state using `Entity.isValid()`
* controller swapping preserving state when intended
* initializer/controller ordering semantics
* supported-hook dispatch behavior on 1.8.8 and 1.21.11
* adapter bootstrap/version selection
* test-plugin examples updated to the new API

Files to review and update
At minimum inspect and modify where relevant:

* `versions/api/...`
* `versions/runtime/...`
* `versions/1.8.8/...`
* `versions/1.21.11/...`
* `docs/custom-entities-architecture.md`
* `test-plugin/...`
* all tests that mention the current custom entity API

Documentation requirements
After refactoring:

* rewrite the architecture doc so the public mental model is obvious
* document the new public API with concise examples
* explicitly explain the difference between reusable templates/definitions and live controlled entities
* document the migration from the old API

Repository conventions to follow
Follow existing repo conventions:

* 4-space indentation
* same-line braces
* null-safe public APIs
* imports instead of fully-qualified inline types
* complete Javadocs for public/protected APIs
* preserve license headers
* match existing package conventions under `tech.guilhermekaua.spigotboot.*`

Build and validation
Use the Maven wrapper from the repo root.
Run focused tests for the affected module(s), then run a broader test pass.
Do not stop at compilation only.

Execution plan
Work in this order:

1. Audit the current public API and identify semantic overlaps.
2. Propose the new public model and naming.
3. Implement the public API refactor first.
4. Refactor runtime internals to support the new semantics cleanly.
5. Adapt version adapters and hook binders.
6. Update tests.
7. Update docs and sample plugin.
8. Produce migration notes and final summary.

Deliverables at the end
Return:

1. A short architecture summary of the new model.
2. A list of breaking changes and deprecations.
3. Old -> new usage examples.
4. A summary of modified files.
5. A summary of tests run and results.
6. Any open tradeoffs or follow-up cleanup items.

Success criteria
This task is only successful if:

* the new public API is clearly less verbose
* the hierarchy reads naturally
* there is no longer any “entity handle extending a context” style smell
* attaching controllers to existing entities feels direct
* spawning simple entities no longer requires unnecessary ceremony
* multi-version/custom-hook internals still work
* the sample plugin code becomes obviously cleaner and more readable
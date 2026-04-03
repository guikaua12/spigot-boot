# AGENTS.md

## Purpose

This file is a working reference for agents changing this repository, with emphasis on the `core/` module because that
is the runtime container the rest of the project builds on.

It is intentionally implementation-specific. It documents what the code does today, not an idealized design.

## Repository map

- `core/`
    - The DI container, lifecycle engine, component scanning, configuration processing, method-handler proxying, module
      loading, validation utilities, and general helpers.
- `platform-spigot/core-spigot/`
    - Spigot/Bukkit integration for the core container.
    - Provides the `BootPlugin` adapter and built-in listener auto-registration.
- `platform-spigot/config-spigot/`
    - Real example of custom injectors and `@Configuration` usage on top of core.
- `platform-spigot/placeholder/`
    - Real example of a conditional module with shutdown hooks.
- `modules/data-orm-lite/`
    - Real example of a module using field injection and `context.registerBean(...)` during module initialization.
- `config/`
    - Configuration and serialization library. Not the core container itself, but it uses core lifecycle patterns such
      as ordered customizers.
- `test-plugin/`
    - End-to-end usage example for startup, components, configuration beans, and services.

## Build and runtime assumptions

- Parent project is Maven multi-module.
- Main code is compiled to Java 8 bytecode even though project properties mention Java 17.
    - Root `pom.xml` and `core/pom.xml` set main source/target to `1.8`.
    - Tests compile with Java 17.
- `core` shades and relocates `javassist` into `tech.guilhermekaua.spigotboot.shaded.javassist`.
- Core depends on `spigot-boot-utils`; several container behaviors rely on `ProxyUtils` from that module.
- `core/src/main/resources/META-INF/spigot-boot/discovery.properties` declares:
    - `original-base-package=tech.guilhermekaua.spigotboot`
    - `ModuleDiscovery` uses this to rewrite discovered module FQCNs after relocation/shading.

## Core entry points

### `tech.guilhermekaua.spigotboot.core.SpigotBoot`

- `initialize(BootPlugin plugin)`
    - Creates a builder, enables auto-discovery, initializes context.
- `initialize(BootPlugin plugin, Class<? extends Module>... modulesToLoad)`
    - Explicit module list, no auto-discovery unless caller uses builder.
- `builder(BootPlugin plugin)`
    - Returns `SpigotBootBuilder`.
- `getContext(BootPlugin plugin)`
    - Returns current context from `ContextManager`.
- `onDisable(BootPlugin plugin)`
    - Calls `context.destroy()` if the context exists and is initialized.
- `registerShutdownHook(...)` and `unregisterShutdownHook(...)`
    - Delegate to the initialized context.

### `tech.guilhermekaua.spigotboot.core.SpigotBootBuilder`

- Supports:
    - `autoDiscover()`
    - `exclude(...)`
    - `module(moduleClass)`
    - `module(moduleClass, explicitOrder)`
    - `modules(...)`
    - `initialize()`
- Resolution rules:
    - Auto-discovery scans module markers from classpath.
    - Explicit module entries override discovered ones if they refer to the same class.
    - Exclusions are applied after merge.
    - Sorting is by effective order:
        - explicit order if present
        - else `@Order` value on module class
        - else `0`
- In non-auto-discover mode, duplicate explicit module registrations are deduplicated by module class, later explicit
  entries win.

### `tech.guilhermekaua.spigotboot.core.plugin.BootPlugin`

This is the abstraction the container expects instead of depending directly on Bukkit.

Methods:

- `getName()`
- `getLogger()`
- `getDataFolder()`
- `getResource(String path)`
- `getClassLoader()`
- `getMainClass()`
- `getNativePlugin()`

Concrete Spigot implementation:

- `platform-spigot/core-spigot/.../SpigotBootPlugin.java`
    - Wraps a `JavaPlugin`.
    - Uses `ProxyUtils.getRealClass(javaPlugin)` for `getMainClass()`.
    - Equality is based on wrapped `JavaPlugin`.

## Context model

### `Context`

Primary API surface for plugins and modules.

Important methods:

- `initialize()`
- `isInitialized()`
- `getBean(type)` / `getBean(type, name)`
- `registerBean(instance)`
- `registerBean(clazz)`
- `getBeansByType(type)`
- `getDependencyManager()`
- `reload()`
- `destroy()`
- `getModulesToLoad()`
- `setModulesToLoad(...)`
- `getPlugin()`
- `registerShutdownHook(...)`
- `unregisterShutdownHook(...)`

Important detail:

- `getBeansByType(type)` reads from `BeanInstanceRegistry`, not `BeanDefinitionRegistry`.
- It returns instantiated beans only.
- After full startup that usually means "almost everything", because `INSTANTIATE` eagerly resolves definitions.

### `PluginContext`

Real context implementation.

Key behavior:

- Holds:
    - a `DependencyManager`
    - the `BootPlugin`
    - logger
    - shutdown hook list
    - module list
    - `BeanRegistrar`
    - `ContextLifecycle`
- `initialize()`
    - constructs `ContextLifecycle`
    - gets `BeanRegistrar` from lifecycle
    - runs lifecycle
    - sets `initialized = true`
- `destroy()`
    - invokes `@OnDisable` callbacks first
    - executes shutdown hooks next
    - runs `ContextPreDestroyProcessor` beans after shutdown hooks
    - clears shutdown hooks
    - clears dependency manager registries
    - sets `initialized = false`

Important caveat:

- `Context.registerBean(...)` is not generally a "runtime dynamic registration" API.
- The underlying `BeanRegistrar` refuses registration once lifecycle phase reaches `INSTANTIATE`.
- That means:
    - calling `context.registerBean(...)` inside a module during `MODULES` phase is valid
    - calling it after `SpigotBoot.initialize(...)` has returned will throw

### `ContextManager`

- Stores contexts in a `ConcurrentHashMap<BootPlugin, Context>`.
- Does not remove entries on destroy.
- Re-initialization of the same plugin reuses the same context object if it was destroyed.

## Actual lifecycle order

`ContextLifecycle.initialize()` runs these phases in order:

1. `REGISTER_CORE`
2. `SCAN`
3. `MODULES`
4. `DEFINITIONS_READY`
5. `INSTANTIATE`
6. native plugin injection
7. `@OnEnable` callbacks
8. `READY`
9. final state `RUNNING`

Related note:

- `PluginContext.destroy()` now reflects the destroy phases in `ContextPhase`:
    - `DESTROY` while `@OnDisable` callbacks and shutdown hooks run
    - `PRE_DESTROY_PROCESSORS` while `ContextPreDestroyProcessor` beans run
    - `CLEARED` after the registries are cleared

### Phase details

#### 1. `REGISTER_CORE`

Registers core instances into the `DependencyManager`:

- `Logger`
- `BootPlugin`
- `DependencyManager`
- `CustomInjectorRegistry`

Notes:

- Logger is registered both as raw instance and explicitly under `Logger.class`.
- `CustomInjectorRegistry` comes from the dependency manager itself.

#### 2. `SCAN`

Builds the package scan set:

- always scans `tech.guilhermekaua.spigotboot.core`
- scans plugin main class package
- scans module packages
- then minimizes nested packages with `ContextLifecycle.minimizePackageRoots(...)`

The minimized roots remove redundant nested packages. Example:

- `com.example.plugin`
- `com.example.plugin.module`

becomes just:

- `com.example.plugin`

What scan phase does for each base package:

- `ComponentRegistry.registerComponents(...)`
- `ConfigurationProcessor.processFromPackage(...)`
- `MethodHandlerProcessor.processFromPackage(...)`

Also:

- `MethodHandlerRegistry.clear()` is called at the start of scanning

This is important because `MethodHandlerRegistry` is static and shared globally.

#### 3. `MODULES`

- Resolves `ModuleRegistry` bean
- Initializes modules in sorted order

For each module:

- Builds a condition context using bean definitions and bean instances
- Skips module if conditions fail
- Rejects stereotype annotations on module classes
- Registers the module class as a bean
- Resolves the module bean from context
- Calls `module.onInitialize(context)`

Important rule:

- Module classes must not also be annotated with component stereotypes like `@Component`, `@Service`, `@Configuration`,
  or any custom stereotype meta-annotated with `@Component`.
- `ModuleRegistry` throws if a stereotype annotation is present.

What modules can do safely:

- use constructor/field/setter injection
- call `context.registerBean(...)` while the lifecycle is still before `INSTANTIATE`
- register shutdown hooks

Real example:

- `modules/data-orm-lite/.../DataOrmLiteModule.java`
    - uses field injection
    - creates a connection source
    - registers it via `context.registerBean(connectionSource)`

#### 4. `DEFINITIONS_READY`

- Finds beans assignable to `BeanDefinitionsReadyListener`
- Instantiates them if necessary
- Sorts instantiated listeners by `Ordered.getOrder()`
- Invokes `onBeanDefinitionsReady(context, definitionRegistry, beanRegistrar)`

This phase exists so code can add definitions after scanning but before instantiation.

Real example:

- `CustomInjectorRegistryFactory`
    - collects `CustomInjectorRegistryCustomizer` beans
    - sorts them
    - applies them to the registry before normal injection starts

#### 5. `INSTANTIATE`

- `ComponentRegistry.resolveAllComponents(...)` walks every bean definition and resolves it
- Then the native plugin instance is field/setter injected
    - class used for injection is `ProxyUtils.getRealClass(nativePlugin)`
- Then instantiated beans are walked for `@OnEnable` callbacks

Consequence:

- After full initialization, most definitions have been instantiated already.

#### 6. `READY`

- Finds `ContextReadyListener` beans after `@OnEnable` callbacks have completed
- Sorts them by `Ordered.getOrder()`
- Calls `onContextReady(context)`

Real example:

- `platform-spigot/core-spigot/.../BukkitListenerAutoRegistrar.java`
    - finds all `Listener` beans
    - registers them with Bukkit
    - adds a shutdown hook to unregister them later

## Components and stereotypes

### Base stereotype

- `@Component`
    - target: `TYPE`
    - runtime retention

### Built-in meta-stereotypes

- `@Service`
    - meta-annotated with `@Component`
    - target: `TYPE`, `METHOD`
- `@RegisterMethodHandler`
    - meta-annotated with `@Component`
    - target: `TYPE`

How component discovery works:

- `ComponentRegistry` discovers:
    - `@Component` itself
    - any annotation types annotated with `@Component`
- It then scans for classes carrying any discovered stereotype.

This means custom stereotypes are supported if they are annotation types annotated with `@Component`.

## Dependency injection model

### Core classes

- `DependencyManager`
- `BeanDefinitionRegistry`
- `BeanInstanceRegistry`
- `BeanDefinition`
- `BeanRegistrar` / `DefaultBeanRegistrar`
- `BeanNamingDefiner` / `DefaultBeanNamingDefiner`

### `BeanDefinition`

Fields:

- `requestedType`
- `type`
- `qualifierName`
- `isPrimary`
- optional `resolver`
- optional `reloadCallback`

Important detail:

- Equality/hashCode are based only on:
    - `type`
    - `qualifierName`
- They do not include `requestedType`.

Practical consequence:

- The same underlying bean registered under an interface and its concrete class intentionally shares one instance slot
  in `BeanInstanceRegistry`.

### Registration styles

Supported styles in `DependencyManager`:

- register a concrete instance
- register an instance for an explicit requested type
- register a class as lazy definition
- register interface/class with a custom resolver
- register with optional reload callback

### Qualifier rules

`DefaultBeanNamingDefiner` behavior:

- If explicit qualifier is non-blank, use it.
- Else base name is decapitalized simple class name.
- For class or instance registrations:
    - default qualifier becomes class-based name
- For interface registrations backed by a resolver:
    - qualifier becomes `baseName#<counter>` to avoid collisions

Special rule for `@Bean` methods:

- `ConfigurationProcessor` uses:
    - explicit `@Qualifier` if present
    - otherwise the method name

### Primary resolution

When resolving a single bean by type and no qualifier:

- if exactly one definition exists, use it
- if multiple definitions exist:
    - exactly one must be primary
    - zero primary beans -> exception
    - multiple primary beans -> exception

### Constructor selection

`DependencyManager.findInjectConstructor(...)` rules:

- interfaces return `null`
- if more than one constructor is annotated `@Inject`, throw `MultipleConstructorException`
- if exactly one constructor is annotated `@Inject`, use it
- else if there is exactly one declared constructor, use it
- else if there are multiple constructors and none annotated, throw `MultipleConstructorException`

### Field and setter injection

After construction:

- setter injection runs first
- field injection runs second

Injection is performed when:

- field/setter has `@Inject`
- or any registered custom injector reports support for that injection point

Important caveat:

- Field and setter injection only inspect `getDeclaredFields()` and `getDeclaredMethods()` on the specific class passed
  to `injectDependencies(...)`.
- Inherited fields/setters are not traversed.

### Constructor parameter injection

- Constructor parameters are always resolved through `InjectionPoint.fromParameter(...)`.
- A constructor itself still has to be selected by the rules above.

### Collection injection

`DependencyManager` supports injecting:

- `List<T>`
- `Set<T>`
- `Collection<T>`

Rules:

- all beans of `T` are resolved
- qualifiers are ignored
- list/collection preserve definition iteration order
- set uses `HashSet`, so no stable order
- if no beans exist, an empty collection is injected

Important caveat:

- For auto-scanned components, registration order can be unstable because discovery often uses sets.
- Do not rely on list order unless the beans are registered explicitly in a known sequence.

### Circular dependency detection

`BeanUtils.detectCircularDependencies(...)` runs during registration.

It analyzes dependencies from:

- selected constructor
- fields annotated with `@Inject`
- setter methods annotated with `@Inject`

It throws `CircularDependencyException` when a cycle is detected.

Important limitations:

- detection is class-based and happens before instantiation
- it does not model custom injectors
- it does not model runtime condition outcomes

### Reload support

- `Context.reload()` delegates to `DependencyManager.reloadDependencies()`
- only instantiated beans with non-null reload callbacks participate
- reload callbacks are now a low-level/manual API only
- no lifecycle annotation maps to `Context.reload()`
- no bean recreation happens
- no condition re-evaluation happens
- no registry rebuild happens

### `@OnEnable` / `@OnDisable`

- lifecycle methods are discovered by walking instantiated beans from `BeanInstanceRegistry`
- discovery uses `ProxyUtils.getRealClass(instance).getDeclaredMethods()`
- method arguments are resolved through `DependencyManager.resolveArguments(...)`
- the same underlying bean instance is invoked only once even if it is registered under multiple requested types
- callback order is:
    - `Ordered#getOrder()` when implemented by the bean instance
    - else class-level `@Order`
    - else `0`
    - then bean identifier for deterministic tie-breaking
- `@OnEnable` runs after instantiation and native plugin injection, before `ContextReadyListener`
- `@OnDisable` runs before shutdown hooks and before `ContextPreDestroyProcessor`
- `@OnEnable` failure aborts initialization
- `@OnDisable` failure is logged and remaining cleanup continues
- lifecycle annotations are valid on configuration classes and on `@Bean` products
- lifecycle annotations are invalid on `@Bean` factory methods themselves

## Manual registration API

### `BeanRegistrar`

Used internally and during `BeanDefinitionsReadyListener`.

Capabilities:

- register instance
- register definition
- register definition with explicit requested type and resolver
- optionally attach reload callback

Preferred use in `BeanDefinitionsReadyListener`:

- use the `BeanRegistrar` argument instead of mutating `BeanDefinitionRegistry` directly

## Conditions

### Annotations

- `@Conditional(Class<? extends Condition>[])`
- `@ConditionalOnBean`
- `@ConditionalOnMissingBean`
- `@ConditionalOnClass`

### `ConditionEvaluator`

Behavior:

- looks for direct `@Conditional`
- also inspects all annotations on the element and picks up meta-annotated `@Conditional`
- instantiates condition classes by no-arg constructor only
- first failing condition causes skip
- optional static debug report can record matched and skipped evaluations

Important caveats:

- Conditions cannot use injected constructor dependencies.
- Built-in conditions mostly look only at bean definitions, not live instances.
- Bean-aware conditions are order-dependent because they only see definitions registered so far.
- `ConditionContext.getBeanInstanceRegistry()` is often `null` during scanning/definition phases.

### `@ConditionalOnBean`

Fields:

- `value()` bean types
- `name()` qualifier names
- `message()`
- `logLevel()`

Semantics:

- if neither `value` nor `name` is provided, condition matches
- multiple `value` entries use AND
- multiple `name` entries use AND
- if both `value` and `name` are provided, the overall condition uses OR
- assignable type matching is used
- proxy types are unwrapped through `ProxyUtils.unwrapProxyType(...)`

### `@ConditionalOnMissingBean`

Same shape as `@ConditionalOnBean`, but inverted:

- multiple `value` entries mean all listed types must be absent
- multiple `name` entries mean all listed names must be absent
- if both type and name are provided, overall logic is OR

### `@ConditionalOnClass`

Fields:

- `value()` class names
- `message()`
- `logLevel()`

Semantics:

- all listed class names must be loadable from the provided classloader
- class loading uses `ClassUtils.isPresent(...)`, which caches by classloader and does not initialize the class

### Logging and reporting

- `LogLevel` maps to JUL:
    - `SILENT` -> `OFF`
    - `DEBUG` -> `FINE`
    - `INFO` -> `INFO`
    - `WARNING` -> `WARNING`
- `ConditionDebugReport` can collect summaries and print a formatted report

### Sharp edge: order-dependent bean conditions

The code only checks the registry state at the time the condition runs.

This matters in two places:

- component scanning order
- `@Bean` method registration order

And `ConfigurationProcessor.collectBeanMethods(...)` currently uses `Collectors.toSet()`, so iteration order of bean
methods is not stable.

Practical rule:

- do not rely on `@ConditionalOnBean` / `@ConditionalOnMissingBean` between sibling `@Bean` methods in the same
  configuration class
- do not rely on scan order between auto-discovered components

## Configuration classes and `@Bean` methods

### `@Configuration`

- target: `TYPE`
- processed by `ConfigurationProcessor`

### Processing flow

For each configuration class:

1. evaluate class-level conditions
2. collect declared `@Bean` methods
3. create a proxy subclass using Javassist
4. register the proxied configuration class as a bean
5. register each bean method as a lazy resolver-backed bean definition

### `ConfigurationClassProxy`

Why it exists:

- ensures singleton semantics for `@Bean` methods
- intercepts internal calls from one `@Bean` method to another

Constructor behavior:

- picks inject constructor through `DependencyManager.findInjectConstructor(...)`
- resolves constructor args
- instantiates proxy using the same constructor signature
- injects field/setter dependencies into proxy instance
- attaches a `ConfigurationClassProxy` method handler

### `@Bean` resolution semantics

When a bean method is invoked:

- qualifier is:
    - explicit `@Qualifier`, else method name
- existing instance in `BeanInstanceRegistry` is returned if already created
- otherwise:
    - parameters are resolved
    - actual bean method runs
    - null return is rejected
    - result goes through `DependencyManager.initializeBean(...)`
    - processed instance is cached

Important consequence:

- `@Bean` results now participate in:
    - field/setter injection
    - bean post processors
    - method-handler proxying

This also prevents raw instances from leaking through internal `@Bean` calls.

### `DependencyManager.initializeBean(...)`

Shared pipeline for all non-null created instances:

1. inject setter dependencies
2. inject field dependencies
3. run bean post processors in order

Built-in post processor:

- `MethodHandlerProxyBeanPostProcessor`

### Practical rules for configuration classes

- Use one constructor or mark the intended constructor with `@Inject`.
- `@Bean` factory methods can take normal bean dependencies as parameters.
- If a `@Bean` needs a stable qualifier, set `@Qualifier` explicitly.
- Do not return `null` from a `@Bean` method.

## Method-handler system and proxying

This is the closest thing the project has to lightweight AOP.

### Main pieces

- `@RegisterMethodHandler`
- `@MethodHandler`
- `MethodHandlerProcessor`
- `MethodHandlerRegistry`
- `RegisteredMethodHandler`
- `MethodHandlerContext`
- `ComponentProxy`
- `BeanProxyDecider`
- `MethodHandlerDrivenProxyDecider`

### Declaring a handler

Handler class:

- must be a bean, typically by annotating the class with `@RegisterMethodHandler`
- can use DI like any normal component

Handler method:

- must be annotated with `@MethodHandler`
- must accept exactly one `MethodHandlerContext`
- should be public in practice
    - `MethodHandlerProcessor` uses `getDeclaredMethods()` but does not call `setAccessible(true)` before invocation

`@MethodHandler` matching knobs:

- `targetClass`
- `classAnnotatedWith`
- `methodAnnotatedWith`

### Runtime behavior

- `MethodHandlerProcessor` discovers handler classes from scanned packages
- discovered handlers are registered into the static `MethodHandlerRegistry`
- `MethodHandlerDrivenProxyDecider` decides if a bean should be proxied by checking whether any registered handler could
  apply to that bean class
- if proxying is needed, `MethodHandlerProxyBeanPostProcessor` wraps the bean in `ComponentProxy`

### `ComponentProxy`

Two modes:

- normal subclass/interface proxy when no existing target object is being delegated to
- delegating proxy for already-created instances such as `@Bean` results

Important behavior:

- if the proxied target method returns itself, proxy returns the proxy (`self`) instead of leaking the raw object
- `toString()` is special-cased

### Handler dispatch semantics

- `MethodHandlerRegistry.getHandlersFor(...)` returns all matching handlers
- `ComponentProxy.invoke(...)` iterates that list and returns the first handler result
- there is no handler chain
- there is no explicit ordering API for handlers

Important caveat:

- overlapping handlers are dangerous
- first-match behavior depends on registration/discovery order, which can be unstable because handler discovery is based
  on scan results collected into sets

### Proxying caveats

- final classes cannot be proxied unless the definition type is an interface assignable from the instance type
- if a bean should be proxied and final-class fallback cannot use an interface, an exception is thrown
- self-invocation inside the target object bypasses proxy interception
- `MethodHandlerRegistry` is static global state
    - initializing one plugin context clears handlers for all other contexts
    - already-created proxies consult the same global registry at invocation time
    - this is a real multi-plugin hazard

## Built-in async service handler

Files:

- `core/.../service/AsyncMethodHandler.java`
- `core/.../service/configuration/ServiceAsyncConfig.java`

### What exists

- `ServiceAsyncConfig` is a `@Configuration`
    - exposes `ExecutorService serviceAsyncExecutor()`
- `AsyncMethodHandler` is a `@RegisterMethodHandler`
    - uses `Context` to resolve an `ExecutorService` bean directly by qualifier
    - expects intercepted methods to return exactly `CompletableFuture`
    - runs them on the configured executor

### Very important current behavior

The handler annotation is:

- `methodAnnotatedWith = Async.class`

That means a method is only intercepted if:

- the method itself is annotated `@Async`

`@Service` is now only a component stereotype. It has no special async behavior.

Default executor behavior:

- `@Async` with no value uses the `serviceAsyncExecutor` bean
- `@Async("myCustomExecutor")` resolves the named `ExecutorService` bean directly from the context

If the named executor bean does not exist, invocation fails fast.

Treat this as the current implementation detail, not an assumption that all `@Service` classes are automatically async.

## Custom injectors

### Main types

- `CustomInjector`
- `InjectionPoint`
- `InjectionResult`
- `CustomInjectorRegistry`
- `DefaultCustomInjectorRegistry`
- `CustomInjectorRegistryCustomizer`
- `CustomInjectorRegistryFactory`

### Behavior

- Custom injectors are consulted before default bean lookup.
- Lower `getOrder()` values run first.
- An injector can:
    - return `InjectionResult.handled(value)`
    - return `InjectionResult.handled(null)`
    - return `InjectionResult.notHandled()`

### Injection without `@Inject`

Field/setter injection occurs if either:

- the member has `@Inject`
- or a custom injector supports that injection point

This means custom injectors can enable annotation-driven injection without `@Inject`.

Real example:

- `platform-spigot/config-spigot/.../FolderConfigInjector.java`
    - supports `FolderConfigRef<T>` and `FolderConfigSnapshot<T>`
    - can resolve from `@FolderConfigName`
    - does not require `@Inject` on the field/setter when the raw type matches

### Registry customization pattern

Best way to add custom injectors from normal user code:

- provide a `@Bean` returning `CustomInjectorRegistryCustomizer`

Real example:

- `platform-spigot/config-spigot/.../ConfigConfiguration.java`
    - returns a customizer bean that registers:
        - `FolderConfigInjector`
        - `ConfigRefInjector`

This customizer is applied during `DEFINITIONS_READY` by `CustomInjectorRegistryFactory`.

## Module system

### `Module`

- simple interface: `void onInitialize(Context context) throws Exception`

### Discovery

`ModuleDiscovery` looks for marker resources at:

- `META-INF/spigot-boot/modules`

The marker file name itself is the module FQCN.

Examples in repo:

-
`modules/data-orm-lite/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.data.ormLite.DataOrmLiteModule`
-
`platform-spigot/core-spigot/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.core.spigot.SpigotCoreModule`
-
`platform-spigot/placeholder/src/main/resources/META-INF/spigot-boot/modules/tech.guilhermekaua.spigotboot.placeholder.PlaceholderModule`

### Relocation support

`ModuleDiscovery` can rewrite discovered FQCNs if shading changes the base package.

It compares:

- original base package from `discovery.properties`
- current runtime base package inferred from the `ModuleDiscovery` class package

### Platform module examples

- `SpigotCoreModule`
    - ordered `-1000`
    - registers:
        - `Plugin`
        - `JavaPlugin`
        - real main plugin class
- `PlaceholderModule`
    - uses `@ConditionalOnClass`
    - checks Bukkit plugin manager at runtime as well
    - registers shutdown hook to unregister expansion

## Core annotations summary

### Container annotations

- `@Component`
    - class stereotype
- `@Service`
    - meta-stereotype
    - can annotate class or method
- `@Configuration`
    - marks configuration classes
- `@Bean`
    - marks factory methods inside configuration classes
- `@Inject`
    - constructor, method, field injection
- `@Qualifier`
    - type, parameter, method, field
- `@Primary`
    - type or method
- `@Order`
    - type-level ordering for modules
- `@OnEnable`
    - method invoked during context startup after instantiation
- `@OnDisable`
    - method invoked during context destroy before shutdown hooks
- `@RegisterMethodHandler`
    - meta-stereotype for method-handler beans
- `@Conditional`
    - low-level condition hook
- `@ConditionalOnBean`
- `@ConditionalOnMissingBean`
- `@ConditionalOnClass`

### Present but currently not wired in core runtime

- `@ListenerRegister`
    - declared in core
    - no core code currently checks it
- `DependencyInjectType`
    - declared enum
    - no runtime code in this repository uses it

## Lifecycle listeners and shutdown behavior

### `BeanDefinitionsReadyListener`

- Runs after all definitions are known, before instantiation.
- Can still register new definitions through provided `BeanRegistrar`.
- Best extension point when setup depends on the full definition set.

### `ContextReadyListener`

- Runs after all beans are instantiated, native plugin injection is complete, and `@OnEnable` callbacks have run.
- Best extension point for:
    - listener registration
    - final integration setup
    - code that needs actual instances rather than definitions

### Shutdown hooks

- Registered per context with `Context.registerShutdownHook(...)`
- Executed after `@OnDisable` callbacks and before pre-destroy processors

### Pre-destroy processors

- Any instantiated `ContextPreDestroyProcessor` bean is invoked on destroy after shutdown hooks.
- Built-in processor:
    - `ShutdownExecutorServicesContextPreDestroyProcessor`
    - shuts down all `ExecutorService` beans in the context

Important caveat:

- it shuts down every `ExecutorService` bean, not just the service async executor

## Reflection and scanning utilities

### `ClassPathScanner`

Supports:

- classpath package scanning for directories and jars
- annotated type discovery, including one-level meta-annotation support
- subtype discovery

Notes:

- it loads classes with `Class.forName(className, false, classLoader)`
- linkage errors and missing classes are ignored during scan
- package results are cached only inside scanner instance, not globally

### `ReflectionUtils`

Used heavily by:

- `ConfigurationProcessor`
- `MethodHandlerProcessor`

Utility methods:

- `getClassesAnnotatedWith(...)`
- `getSubClassesOf(...)`
- `getFieldsAnnotatedWith(...)`
- `getMethodsAnnotatedWith(...)`
- `getSuperInterfaces(...)`

Important caveat:

- `getSuperInterfaces(...)` only returns direct interfaces of the class, not transitive interface hierarchy and not
  superclasses

### `ResourceScanUtils`

General helpers for:

- normalizing resource paths
- scanning jars
- scanning directories recursively

## Validation subsystem in `core`

This is standalone utility code, not automatically integrated into context startup.

### Main types

- `Validator`
- `DefaultValidator`
- `ValidationResult`
- `ValidationError`
- `Constraint`
- `ConstraintFactory`
- `PropertyPath`
- `ConfigPath`

### Built-in validation annotations

- `@NotNull`
    - includes `failFast`, default `true`
- `@Min`
- `@Max`
- `@Range`
- `@Pattern`
- `@OneOf`
- `@Size`
- `@Valid`

### Behavior

- Validation is field-based.
- Superclass fields are included.
- Recursive validation happens only on fields annotated `@Valid`.
- Cycles are avoided by an identity-based visited set.
- `validateOrThrow(...)` throws only for fail-fast errors.

Important caveat:

- This validator is not automatically run for beans or configs by the core container.
- If a module wants validation, it must call it explicitly or build integration around it.

## Misc core utilities

### `BeanUtils`

Provides:

- qualifier lookup
- primary lookup
- circular dependency detection

### `ClassUtils`

- cached `isPresent(className, classLoader)` checks
- cache is static and keyed by classloader using `WeakHashMap`

### `CollectionTypeUtils`

- collection generic extraction
- map generic extraction
- raw class extraction from `Type`

### `DependencyGraph`

- generic directed graph
- cycle detection
- full or subset topological ordering
- reverse dependency lookup

### `Timestring`

- parses human-readable durations like `1h30m`, `5m 30s`, `2.5d`
- returns milliseconds by default or converts into requested unit

### `NumberUtils`

- decimal formatting/parsing with US locale formatting
- negative/NaN/infinite values are considered invalid

### `Cooldown`

- singleton-style helper using in-memory timestamps

### `MethodFormatUtils`

- simple method formatter: `declaringClass#methodName`

## Real usage examples in this repository

### Plugin bootstrap

- `test-plugin/.../Main.java`
    - wraps Bukkit plugin with `SpigotBootPlugin`
    - calls `SpigotBoot.initialize(bootPlugin)`
    - calls `SpigotBoot.onDisable(bootPlugin)` on shutdown

### Simple configuration bean

- `test-plugin/.../MyConfiguration.java`
    - returns `JdbcSchemaOptions` from a `@Bean`

### Component with constructor injection

- `test-plugin/.../JoinListener.java`
    - `@Component`
    - Lombok `@RequiredArgsConstructor`
    - auto-registered as Bukkit listener by `BukkitListenerAutoRegistrar`

### Service example

- `test-plugin/.../UserService.java`
    - class-level `@Service`
    - async methods annotated with `@Async`
    - returns `CompletableFuture`

### Module example with field injection

- `modules/data-orm-lite/.../DataOrmLiteModule.java`
    - field-injected registries
    - registers a runtime-created bean into context during module initialization

### Conditional module example

- `platform-spigot/placeholder/.../PlaceholderModule.java`
    - `@ConditionalOnClass(...)`
    - runtime Bukkit check
    - shutdown hook

### Custom injector example

- `platform-spigot/config-spigot/.../ConfigConfiguration.java`
    - exposes `CustomInjectorRegistryCustomizer`

## Tests that act as living spec

If behavior looks surprising, read these tests first:

- Builder and module ordering:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/SpigotBootBuilderTest.java`
- Module discovery:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/module/ModuleDiscoveryTest.java`
- Package root minimization:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/context/lifecycle/ContextLifecyclePackageScanTest.java`
- Configuration processing and `@Bean` proxy semantics:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/configuration/ConfigurationProcessorTest.java`
- Dependency resolution and reload:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/dependency/manager/DependencyManagerTest.java`
- Collection injection:
    -
    `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/dependency/manager/DependencyManagerCollectionInjectionTest.java`
- Selective proxying:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/component/proxy/SelectiveProxyingTest.java`
- Proxy decider behavior:
    -
    `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/component/proxy/decider/MethodHandlerDrivenProxyDeciderTest.java`
- Condition behavior:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/condition/OnBeanConditionTest.java`
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/condition/OnMissingBeanConditionTest.java`
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/condition/OnClassConditionTest.java`
    -
    `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/condition/BeanAwareConditionIntegrationTest.java`
    -
    `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/condition/ConditionLifecycleIntegrationTest.java`
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/condition/ConditionLoggingTest.java`
- Custom injectors:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/dependency/injector/CustomInjectorTest.java`
    -
    `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/context/dependency/injector/CustomInjectorRegistryFactoryTest.java`
- Utility and reload callback behavior:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/utils/BeanUtilsTest.java`
- Validation:
    - `core/src/test/java/tech/guilhermekaua/spigotboot/core/test/validation/ValidatorTest.java`

## Practical rules for future changes

### If you are adding a new module

- implement `Module`
- avoid component stereotypes on the module class
- add a descriptor file under `META-INF/spigot-boot/modules/<module-fqcn>`
- use `@Order` if load order matters

### If you are adding a new component stereotype

- annotate the annotation type with `@Component`
- remember component discovery is scan-based, not explicit registration

### If you are adding a new `@Bean`

- do not rely on sibling `@Bean` definition order for conditions
- choose explicit qualifiers when ambiguity matters
- do not return `null`

### If you are adding a new method handler

- annotate the class with `@RegisterMethodHandler`
- use a public handler method taking exactly `MethodHandlerContext`
- avoid overlapping handler metadata because first-match wins and ordering is not explicit
- remember final classes may not proxy cleanly

### If you are adding a custom injector

- prefer exposing a `CustomInjectorRegistryCustomizer` bean from a `@Configuration`
- keep `supports(...)` cheap
- return `notHandled()` when you want default DI to continue

### If you are changing DI or proxy behavior

Read these first:

- `DependencyManager`
- `ConfigurationClassProxy`
- `MethodHandlerProxyBeanPostProcessor`
- `MethodHandlerDrivenProxyDecider`
- `ComponentProxy`
- `ConfigurationProcessorTest`

### If you are changing condition behavior

Read these first:

- `ConditionEvaluator`
- `OnBeanCondition`
- `OnMissingBeanCondition`
- `OnClassCondition`
- `BeanAwareConditionIntegrationTest`
- `ConditionLoggingTest`

## Highest-risk current sharp edges

These are the things most likely to matter during refactors:

- `MethodHandlerRegistry` is static global state across contexts and plugins.
- `ConditionEvaluator.debugReport` is also static global state.
- `@ConditionalOnBean` / `@ConditionalOnMissingBean` are definition-order-dependent.
- `@Bean` method iteration order is not stable because methods are collected into a plain set.
- Handler dispatch is first-match only and has no explicit ordering mechanism.
- `Context.registerBean(...)` is effectively pre-instantiation only, not a post-startup dynamic registration API.
- Field and setter injection do not traverse inherited members.
- `ListenerRegister` exists but is currently unused.
- Built-in async interception is controlled by method-level `@Async`; `@Service` is only a stereotype.
- Multi-constructor classes must use `@Inject` on exactly one constructor.

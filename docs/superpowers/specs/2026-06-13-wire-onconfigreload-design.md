# Wire `@OnConfigReload` — Design

**Date:** 2026-06-13
**Branch:** `feat/wire-onconfigreload` (off `dev`)
**Modules touched:** `core` (new `BeanPostProcessor` extension point), `platform-spigot/config-spigot`
(implementation + tests), `config` (docs only), `wiki` submodule (docs)

## Problem

`@OnConfigReload` (`config/.../annotation/OnConfigReload.java`) is a declared-but-unwired API. The
annotation is `@Retention(RUNTIME) @Target(METHOD)` with a `Class<?>[] value()` filter and full
Javadoc ("Method called after a config is reloaded. Method can have zero params or accept the new
config instance"), but **no code path scans for it** — a whole-repo grep finds the symbol only in its
own definition and in `config-spigot-unwired-api-notes.md`, which lists it under "declared-but-unwired
API … decide whether to implement, deprecate, or remove."

It was never wired because the framework already ships working *programmatic* equivalents, so the
declarative form was left as a TODO:

- `ConfigRef.addListener(Consumer<T>)` — fires the **new instance** on every reload
  (`DefaultConfigRef.reload()`/`update()` at `DefaultConfigRef.java:92-128`). Propagated reloads route
  through `ConfigBindingCoordinator.bindKey` → `ConfigEntry.setInstance` → `ref.update()`.
- `FolderConfigRef.addListener(FolderConfigChangeListener<T>)` — fires per item add/modify/remove with
  a `FolderConfigItemChange<T>` (`DefaultFolderConfigRef.java:99-134`).

This design wires the annotation to those existing mechanisms.

## Goal

A bean (DI-managed) can declare a method annotated `@OnConfigReload`; after the relevant config is
reloaded, the framework invokes that method, optionally passing the new value. Covers **both** simple
`@Config` classes and **folder** configs.

## Approach (decided during brainstorming)

- **Discovery:** a per-bean `BeanPostProcessor`. The framework had **no module-facing way to register a
  `BeanPostProcessor`** (only injectors had a customizer; the sole post-processor was the core-internal
  `MethodHandlerProxyBeanPostProcessor`). So this adds a **new core extension point** —
  `BeanPostProcessorRegistryCustomizer` + factory — mirroring `CustomInjectorRegistryCustomizer` /
  `CustomInjectorRegistryFactory`. (A `ContextReadyListener` would have covered every bean too, since
  all components are eagerly instantiated before `READY` and there is no `@Lazy`; the post-processor
  route was chosen to give a reusable per-bean hook and future-proof against lazy/on-demand beans.)
- The config registry is initialized before bean instantiation (proven by `@ConfigValue` injection
  working at instantiation time), so all `ConfigRef`/`FolderConfigRef` instances exist when a bean is
  post-processed.
- **No javassist / pom changes.** Pure reflection (no javassist proxy API), so the shading rules in
  `CLAUDE.md` do not apply and no new shade execution is needed.
- **Placement mirrors `@ConfigValue`:** annotation stays in `config`; the dispatcher lives in
  `config-spigot`; registration happens in `ConfigConfiguration`.

## Component design

### Core: new `BeanPostProcessor` extension point (mirrors the injector factory)
- **`BeanPostProcessorRegistry`** (new interface, `core…dependency.postprocessor`): `void register(BeanPostProcessor)`.
  `DependencyManager implements BeanPostProcessorRegistry` (delegating to the existing
  `registerBeanPostProcessor`) and exposes `getBeanPostProcessorRegistry()` — paralleling
  `getCustomInjectorRegistry()`. Interface Segregation: customizers get a narrow registry, not the whole
  `DependencyManager`.
- **`BeanPostProcessorRegistryCustomizer`** (new `@FunctionalInterface extends Ordered`):
  `void customize(BeanPostProcessorRegistry registry)` — the module-facing hook, exactly like
  `CustomInjectorRegistryCustomizer`.
- **`BeanPostProcessorRegistryFactory`** (new `@Component implements BeanDefinitionsReadyListener, Ordered`):
  in the **DEFINITIONS_READY** phase (before `INSTANTIATE`), resolves all
  `BeanPostProcessorRegistryCustomizer` bean definitions, sorts by order, applies each to the registry.
  A near-copy of `CustomInjectorRegistryFactory`.

### `OnConfigReloadProcessor implements BeanPostProcessor` (config-spigot)
Depends on `SpigotConfigManager`. `postProcess` returns the `instance` unchanged (it only registers
listeners — never wraps). For each bean instance:
1. Resolve the bean's **real class** via `ProxyUtils.getRealClass(instance)` (proxy-safe — beans may be
   javassist proxies; mirror `BeanLifecycleInvoker`, which scans `realClass` and invokes on `instance`).
2. Enumerate methods across the class hierarchy (include inherited; dedupe — like
   `ProxiedListenerEventBinder` combining `getMethods()` + `getDeclaredMethods()`).
3. For each `@OnConfigReload` method: validate (below), resolve target config(s) + payload, and
   register a listener per target, **bound to `instance`**:
   - simple → `configManager.getRef(C).addListener(newInstance -> invoker.invoke(bean, method, payload))`
   - folder → for every folder config of item type `F`,
     `configManager.getFolderConfigRef(F, name).addListener(change -> invoker.invoke(...))`

**Order:** runs **after** `MethodHandlerProxyBeanPostProcessor` (which uses the default `Ordered`
order and may swap the bean for a proxy), so we receive and bind to the final (proxy) `instance`.
Pick a positive `getOrder()` (e.g. `100`) — confirm `Ordered`'s default during the plan.

### `OnConfigReloadInvoker`
`method.setAccessible(true)` + `Method.invoke(bean, args)`; unwrap `InvocationTargetException`; log
failures via the plugin `Logger`. (`DefaultConfigRef`/`DefaultFolderConfigRef` already isolate
listener exceptions in their notify loops, but we still unwrap+log for a clear diagnostic.)

### Registration
In `ConfigConfiguration`, add a `@Bean` returning a `BeanPostProcessorRegistryCustomizer` that
registers the processor — mirroring the existing `configInjectors` bean:

```java
@Bean
public BeanPostProcessorRegistryCustomizer onConfigReloadProcessor(SpigotConfigManager configManager) {
    return registry -> registry.register(new OnConfigReloadProcessor(configManager));
}
```

## Annotation semantics

### Target resolution — which config(s) a method listens to
1. `value()` non-empty → those classes; each must be a registered `@Config` class **or** a registered
   folder-config item type (else fail-fast).
2. `value()` empty + exactly one typed parameter → inferred from the parameter type.
3. `value()` empty + zero parameters → **all** registered configs (simple + folder), per the
   annotation's documented "Empty = any config."

### Supported signatures (`void`, instance method, ≤ 1 parameter)
**Simple `@Config` target `C`:**
- `void m()` — fires on reload of `C`.
- `void m(C cfg)` — receives the new instance (mirrors `Consumer<T>`). Parameter type must be
  assignable from `C`.

**Folder-config target (item type `F`):**
- `void m()` — fires per item change.
- `void m(FolderConfigItemChange<F> change)` — receives the change (native granularity).
- `void m(FolderConfigSnapshot<F> snapshot)` — receives the post-change snapshot.

Parameter generics (`FolderConfigItemChange<F>` / `FolderConfigSnapshot<F>`) are extracted the same
way `ConfigRefInjector`/`FolderConfigInjector` already extract type arguments from `ParameterizedType`.

### Validation (fail-fast at scan time, in the style of the recent `@ConfigValue` guards)
Reject with a clear `ConfigException` (naming the bean class + method) when:
- more than one parameter;
- non-`void` return type;
- `static` method;
- the single parameter type matches no supported payload for the resolved target kind;
- a simple-config target is paired with a folder-only payload (`FolderConfigItemChange`/`Snapshot`) or
  a folder target is paired with a simple-instance payload;
- a class in `value()` is not a registered config of either kind.

Additionally, **reject `@OnConfigReload` declared on `@Config`/`@FolderConfig` POJO classes** in
`ConfigRegistry` (next to the existing `rejectConfigValueUsage`), since those are instantiated by the
binder, not the DI container, so the callback would silently never fire. Scan the full inheritance
chain, consistent with the `@ConfigValue` guard hardening (`ConfigRegistry.rejectConfigValueUsage`).

## Deliberate behaviors / edge cases
- **Does not fire on initial load** — only on subsequent reloads. `addListener` does not replay the
  current value; this matches "after a config is reloaded."
- Folder zero-arg / empty-`value()` methods fire **once per item change** (the only granularity folder
  refs expose). Documented as a sharp edge.
- **Multiple** `@OnConfigReload` methods per bean are all registered (like `@EventHandler`; unlike the
  single-method `@OnEnable`/`@OnDisable`).
- A folder item type may back several named folder configs — register on **all** of them.

## Testing

### Core (mirroring `CustomInjectorRegistryFactoryTest`)
- `BeanPostProcessorRegistryFactory` resolves all `BeanPostProcessorRegistryCustomizer` beans and
  applies them in `getOrder()` order; empty case is a no-op; a throwing customizer is logged and does
  not abort the others.
- `DependencyManager.register(...)` / `getBeanPostProcessors()` round-trip; new processors sort by order.

### config-spigot (plain Mockito + temp-file `SpigotConfigManager`, mirroring `ConfigValueInjectorTest`)
- Reload fires zero-arg and instance methods with the correct new-instance argument.
- Folder config: methods fire with `FolderConfigItemChange` and with `FolderConfigSnapshot` payloads.
- `value()` filtering: a method only fires for the listed configs.
- empty-`value()` zero-arg fires for all registered configs.
- Fail-fast on each invalid signature (2 params; non-void; static; wrong payload type;
  simple/folder payload mismatch; unregistered `value()` class).
- Multiple `@OnConfigReload` methods in one bean all fire.
- A throwing callback is isolated (other listeners still run) and the error is logged.
- `ConfigRegistry` rejects `@OnConfigReload` on a `@Config`/`@FolderConfig` POJO.
- A `ConfigConfiguration`-level test asserting the `BeanPostProcessorRegistryCustomizer` bean registers
  an `OnConfigReloadProcessor` (mirrors `ConfigConfigurationTest` asserting the three injectors).

## Documentation (GitHub wiki — `wiki` submodule → `spigot-boot.wiki.git`)
- **`Config_ReloadingExplained.md`** (primary): in *"Reacting to a reload"* (currently documents
  `ConfigRef.addListener` at lines 43-58), add `@OnConfigReload` as the **declarative alternative** —
  supported signatures, `value()` targeting, and the "fires on reload, not initial load" + "reads must
  go through getters/proxy" caveats. One simple-config example and one folder-config example.
- **`Config_FolderConfigsExplained.md`**: in *"Reacting to changes"* (cross-linked from the reloading
  page), note `@OnConfigReload` can target a folder item type and receive
  `FolderConfigItemChange`/`FolderConfigSnapshot`; cross-link back.
- **`Config_ConfigSharpEdgesAndTips.md`**: add the gotchas (no initial-load fire; folder zero-arg
  fires per item change; must live on a DI bean; rejected on `@Config`/`@FolderConfig` POJOs).
- Match the wiki's code-first "Explained" voice; every claim maps to real code.
- **`config-spigot-unwired-api-notes.md`** (repo dev-note): remove the now-wired `@OnConfigReload`
  entry.

### Delivery (wiki is a separate repo)
`wiki/` is a git submodule. Doc edits are commits in that repo; the `dev` PR carries a
submodule-pointer bump. The wiki is **not** pushed to its live remote without explicit go-ahead
(pushing publishes immediately).

## Out of scope
- Async/threading changes to reload dispatch.
- A "reload complete" folder event (folder refs only expose per-item changes).
- Firing on initial load.

## Deliverable
A **PR targeting `dev`** for the code + repo docs; wiki commits staged/ready, pushed to the wiki repo
only on confirmation.

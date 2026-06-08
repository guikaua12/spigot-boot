# spigot-boot-inventory-api

A view-based inventory framework for spigot-boot: discovered `View` singletons opened through
an injected `ViewService`, with typed per-phase contexts, reactive state, and declarative
pagination.

## Quick start

```java
@RegisterView
public final class HelloView extends View {
    @Override protected void onInit(ViewConfigBuilder config) {
        config.title("&aHello").rows(3);
    }
    @Override protected void onFirstRender(RenderContext render) {
        render.slot(13, new ItemStack(Material.DIAMOND))
              .onClick(ctx -> ctx.player().sendMessage("clicked!"));
    }
}
```

```java
@Component
public final class Opener {
    private final ViewService views;
    Opener(ViewService views) { this.views = views; }
    void open(Player player) { views.open(player, HelloView.class); }
}
```

## Migration from 2.x

The 3.0.0 API is a clean break — the 2.x template-method surface (`CustomInventory`,
`Viewer`, `InventoryEditor`, `ViewerPropertyMap`, `@Inventory`) is removed with no
deprecation layer.

| 2.x | 3.0 |
|---|---|
| `@Inventory` | `@RegisterView` |
| `CustomInventoryImpl.configure(...)` | `View.onInit(ViewConfigBuilder)` |
| `firstOpen(viewer, editor)` | `View.onOpen(OpenContext)` / `onFirstRender(RenderContext)` |
| `configureInventory(...)` + `update(...)` | `onFirstRender(...)` + reactive state (`mutableState`/`sharedState`/`lazyState`) |
| `InventoryService.open(player, class)` (returned `null` for unknown) | `ViewService.open(player, class)` (throws `UnknownViewException`) |
| `InventoryItem.of(item).callback(...)` | `RenderContext.slot(int, ItemStack).onClick(...)` (or `slot(int).item(...).onClick(...)` for a dynamic item) |
| `ViewerPropertyMap` (stringly-typed per-viewer state) | state tokens: `mutableState`, `initialState(key, type)`, `lazyState`, `sharedState` |
| `NormalPaginationBuilder` / `ScrollPaginationBuilder` / `PatternPaginationBuilder` + `init`/`apply` | `View.paginate(...)` / `paginateAsync(...)` / `paginateSource(...)` returning a `PaginationBuilder` |
| `InventoryLayout` (with reserved `<`/`>` back/next slots) | `Layout` (navigation is ordinary components bound with `displayIf(pagination::canBack)`) |
| `Pagination.setSource(list)` | a lazy source (`paginate(ctx -> list)`) + `pagination.refresh(ctx)` |
| `Pagination.getPageOfIndex(i)` | removed (no public replacement) |
| `InventoryConfiguration.tickAsync()` / async scheduled updates | removed — async pagination + reactive settle repaint cover the live-data use case |

## Attribution

The public API design is **inspired by [devnatan/inventory-framework](https://github.com/devnatan/inventory-framework)** (MIT) — its
view/state/declarative-pagination vocabulary and ergonomics. The implementation is
clean-room: no code or documentation text was copied, and the page-source pagination engine
is original spigot-boot code carried over from the 2.x module.

## Known limitations (3.1 candidates)

- A view opened while another plugin cancels `InventoryOpenEvent` can leave an ACTIVE ghost
  session until the player quits or replaces it (inherited 2.x behavior).
- Two pagination tokens targeting the same slot are not validated (paint/click would mismatch).
- The unbound-layout-char boot warning only treats `layoutSlot(char)` declarations as
  "binding" a char; covering the same slots with absolute `slot(int)` components still warns.

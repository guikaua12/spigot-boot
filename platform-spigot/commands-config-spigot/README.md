# Commands Config Bridge

`spigot-boot-commands-config-spigot` is an optional bridge module that lets command annotations resolve text from
Spigot Boot configs.

It keeps both `spigot-boot-commands` and `spigot-boot-commands-spigot` syntax-agnostic by owning `${...}` entirely in
this module.

Example:

```java
@Command("coin ${main:commands.coinSet} <player>")
@Permission("${main:permissions.coinSet}")
public void setCoin(String player) {
}
```

This artifact is opt-in. Without it, `${...}` is treated as ordinary text by the commands modules.

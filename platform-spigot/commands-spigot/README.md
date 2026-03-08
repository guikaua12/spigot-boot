# Commands Spigot

`spigot-boot-commands-spigot` is the Spigot adapter for the shared `spigot-boot-commands` module.

It keeps the Bukkit ergonomics of the old module, but the command engine itself now lives in the shared `commands/`
module.

## What Changed With The Refactor

Before this refactor, commands were effectively Spigot-only.

Now:

- Spigot still gets annotation-driven registration and Bukkit sender support
- the parser, router, interceptor chain, cooldown system, and message SPI are shared
- future platforms can reuse the same command engine
- you can write portable command extensions in plain `spigot-boot-commands`
- you can choose between platform-neutral command logic and Spigot-specific command logic in the same plugin

## What Stays Spigot-Specific Here

This module adds the Spigot adapter pieces:

- Bukkit command registration
- `BukkitCommandSender`
- `BukkitCommandPlatformSupport`
- Bukkit sender detection for `@Sender`
- Bukkit argument resolvers for `Player`, `OfflinePlayer`, and `World`
- Bukkit completion IDs such as `onlinePlayers`, `offlinePlayers`, and `worlds`

Spigot plugins should depend on this module, not on `spigot-boot-commands` directly.

## Setup

Add the dependency:

```xml
<dependency>
    <groupId>tech.guilhermekaua.spigot-boot</groupId>
    <artifactId>spigot-boot-commands-spigot</artifactId>
    <version>2.0.2</version>
</dependency>
```

No `plugin.yml` command entries are required. The module is discovered through the normal Spigot Boot module loader.

## Choosing Between Portable And Bukkit-Specific Handlers

After the split, a Spigot plugin can write handlers in two styles.

Portable style:

- use `CommandExecutionContext`
- use `context.getSender()`
- avoid Bukkit-only sender types in command logic

Spigot-specific style:

- use `@Sender CommandSender`
- use `@Sender Player`
- use Bukkit argument types such as `Player` and `World`

This means the same plugin can keep business rules portable while still exposing a Bukkit-friendly entrypoint.

## Shared Execution Context

`CommandExecutionContext#getSender()` now returns the shared `CommandSenderHandle`.

Use `@Sender` when you want Bukkit injection into the handler method. Use `getSender()` when you want code that is easy
to reuse on a future adapter.

Portable example:

```java
@Command("whoami")
public void whoAmI(CommandExecutionContext context) {
  context.sendMessage("You are " + context.getSender().getName());
}
```

Spigot-specific example:

```java
@Command("where")
public void where(@Sender Player player) {
    player.sendMessage("You are in " + player.getWorld().getName());
}
```

## Full Example

```java
@CommandHandler
@RootCommand(value = "admin", aliases = {"adm"}, description = "Administrative commands")
public class AdminCommands {

    @DefaultCommand
    public void root(@Sender CommandSender sender, CommandExecutionContext context) {
        sender.sendMessage("Usage: " + context.getUsage());
    }

    @Command(value = "greet <player> [reason]", usage = "/admin greet <player> [reason]")
    @Permission("plugin.admin.greet")
    public void greet(
            @Sender CommandSender sender,
            @Completion("onlinePlayers") Player player,
            @DefaultValue("No reason provided") String reason,
            AuditService auditService,
            CommandExecutionContext context
    ) {
        auditService.recordGreeting(sender.getName(), player.getName(), reason);
        context.sendMessage("Greeting sent.");
    }

    @CatchUnknown
    public void unknown(CommandExecutionContext context) {
        context.sendMessage("Unknown command: " + context.getInput());
    }
}
```

## What Is New For Spigot Users

Even if you only target Spigot today, the split enables things that were awkward before:

- you can move shared command interceptors, cooldown policies, message providers, and replacement/completion customizers
  into a plain shared module
- you can test shared command behavior without Bukkit
- you can prepare code for a second platform without rewriting command parsing or interception
- you can keep most command logic independent from Bukkit and only unwrap `CommandSender` when needed

## Nested Command Groups

`@Command` can also annotate classes nested inside a `@CommandHandler`. The nested class path is treated as a prefix,
and its command methods are flattened into the same root namespace.

```java
@CommandHandler
@RootCommand("admin|adm")
public class AdminCommands {

    @Command("coin")
    public static class CoinCommands {
        @Command("<player> set <amount>")
        public void setCoin(@Sender CommandSender sender, String player, Integer amount) {
        }

        @DefaultCommand
        public void rootCoinMenu(@Sender CommandSender sender) {
        }
    }
}
```

This registers:

- `/admin coin <player> set <amount>`
- `/admin coin`
- the same routes under `/adm`

Both `static` nested classes and non-static inner classes are supported. Nested command groups may declare
`@DefaultCommand`, but nested `@CatchUnknown` handlers are not supported; only the root handler may define
`@CatchUnknown`.

## Built-In Bukkit Argument Support

This module keeps the Spigot-focused resolver experience:

- `Player`
- `OfflinePlayer`
- `World`

Example:

```java
@Command("teleport <player> <world>")
public void teleport(Player player, World world) {
    player.teleport(world.getSpawnLocation());
}
```

## Built-In Bukkit Completion IDs

The shared module only ships generic completions such as `booleans`.

This Spigot adapter adds Bukkit-specific IDs:

- `onlinePlayers`
- `offlinePlayers`
- `worlds`

Example:

```java
@Command("visit <player>")
public void visit(@Completion("offlinePlayers") OfflinePlayer player) {
}
```

## Custom Argument Resolvers

Register a resolver as a normal bean:

```java
@Component
public class WarpResolver implements CommandArgumentResolver<Warp> {
    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return Warp.class.equals(parameter.getValueType());
    }

    @Override
    public Warp resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
        return context.getContext().getBean(WarpService.class).findByName(input);
    }
}
```

If this resolver has no Bukkit dependency, it can live in a shared module and be reused by future adapters.

## Custom Messages

Provide a primary `CommandMessages` bean:

```java
@Component
@Primary
public class CustomCommandMessages implements CommandMessages {
    // implement the message methods you want to customize
}
```

If you only use `CommandExecutionContext` and `CommandSenderHandle`, this is now portable across platforms.

## Global Interceptors

Register a `CommandInterceptor` bean to run logic for every command.

If a `CommandInterceptor` bean exists in the context, it is automatically applied to all commands, including default
and unknown handlers.

```java
@Component
public class AuditInterceptor implements CommandInterceptor {
    @Override
    public CommandExecutionDecision before(CommandExecutionContext context, CommandInvocationPlan invocation) {
        context.getPlugin().getLogger().info("Executing " + context.getInput());
        return CommandExecutionDecision.continueExecution();
    }
}
```

If that interceptor only uses shared APIs, it can now be moved out of the Spigot-specific module.

## Built-In Cooldowns

`@Cooldown` is built in. It can be placed on a command method, a nested command group, or a root handler type.

The cooldown key is per sender and per handler method, so aliases share the same cooldown but different methods do not.

Static cooldown:

```java
@CommandHandler
@RootCommand("kit")
public class KitCommands {
    @Cooldown(time = "30s")
    @Command("daily")
    public void daily(@Sender Player sender) {
    }
}
```

The `time` value is parsed with `Timestring`, so values such as `30s`, `5m`, and `1h30m` are valid.

For context-sensitive cooldowns, provide a `CommandCooldownPolicy` bean and reference it from the annotation:

```java
@Component
public class VipCooldownPolicy implements CommandCooldownPolicy {
    @Override
    public Duration resolve(Cooldown annotation,
                            CommandExecutionContext context,
                            CommandInvocationPlan invocation) {
      if (context.getSender().hasPermission("plugin.vip")) {
            return Duration.ofSeconds(5);
        }

        return Duration.ofMillis(Timestring.durationLong(annotation.time(), "ms"));
    }
}

@CommandHandler
@RootCommand("kit")
public class KitCommands {
    @Cooldown(time = "30s", policy = VipCooldownPolicy.class)
    @Command("daily")
    public void daily(@Sender Player sender) {
    }
}
```

Returning `Duration.ZERO` or a negative duration from a policy disables the cooldown for that execution.

Using `CommandSenderHandle` here means the policy can be reused by future non-Spigot adapters.

## Annotation-Bound Interceptors

For behavior other than cooldowns, create a custom annotation and bind it to a `CommandAnnotationInterceptor`.

`CommandAnnotationInterceptor` does not extend `CommandInterceptor`. Use `CommandInterceptor` for global behavior and
`CommandAnnotationInterceptor` for annotation-scoped behavior.

## Completions And Replacements

Use the registry customizers to add named providers or replacement tokens:

```java
@Configuration
public class CommandsCustomization {
    @Bean
    public CommandCompletionRegistryCustomizer commandCompletionRegistryCustomizer() {
        return registry -> registry.register("warps", (context, parameter, input) -> context.getContext()
                .getBean(WarpService.class)
                .allNames());
    }

    @Bean
    public CommandReplacementRegistryCustomizer commandReplacementRegistryCustomizer() {
        return registry -> registry.register("plugin.name", "ExamplePlugin");
    }
}
```

If those customizers only depend on shared APIs, they can now live in a platform-neutral module and still work on
Spigot.

# Commands Spigot

`spigot-boot-commands-spigot` lets you declare Spigot commands as beans, inject Bukkit senders and Bukkit argument
types, and register commands automatically when your Spigot Boot context starts.

## How do I install it?

Add the module to your plugin:

```xml
<dependency>
    <groupId>tech.guilhermekaua.spigot-boot</groupId>
    <artifactId>spigot-boot-commands-spigot</artifactId>
    <version>3.1.0</version>
</dependency>
```

You do not need `plugin.yml` command entries for commands managed by this module. Spigot Boot discovers the module and
registers your command handlers during context startup.

## How do I create my first command?

Create a bean annotated with `@CommandHandler` and `@RootCommand`. Use `@DefaultCommand` for the action that should run
when the player or console executes the root command with no extra path.

```java

@CommandHandler
@RootCommand(value = "admin", aliases = {"adm"}, description = "Administrative commands")
public class AdminCommands {

    @DefaultCommand
    public void root(CommandExecutionContext context) {
        context.sendMessage("Try /admin greet <player> [reason]");
    }
}
```

This registers `/admin` and `/adm`.

`@CommandHandler` is a component stereotype, so the handler is picked up by normal Spigot Boot scanning.

## How do I access the sender and command context?

Inside your existing handler, use `@Sender` when you need the Bukkit sender. Add `CommandExecutionContext` when you
want command metadata or a simple reply helper.

```java

@DefaultCommand
public void root(@Sender CommandSender sender, CommandExecutionContext context) {
    sender.sendMessage("You ran /" + context.getCommandLabel());
    sender.sendMessage("Usage: " + context.getUsage());
}
```

Use `@Sender Player` if the command should only run for players. If the sender type does not match, the command uses
the configured `CommandMessages` response.

## How do I add subcommands?

Inside the same handler, add only the subcommand methods you need. Parsed arguments come from the command pattern, and
normal beans can still be injected as method parameters.

```java

@Command(value = "greet <player> [reason]", usage = "/admin greet <player> [reason]")
@Permission("plugin.admin.greet")
public void greet(
        @Sender CommandSender sender,
        @Completion("onlinePlayers") Player player,
        @DefaultValue("No reason provided") String reason,
        CommandExecutionContext context
) {
    sender.sendMessage("Greeting " + player.getName() + ": " + reason);
    sender.sendMessage("Handled by /" + context.getCommandLabel());
}

@CatchUnknown
public void unknown(@Sender CommandSender sender, CommandExecutionContext context) {
    sender.sendMessage("Unknown subcommand: " + context.getInput());
}
```

Use `@DefaultValue` when you want an optional parameter to fall back to a fixed value.

Use `@CatchUnknown` when you want to handle `/admin something-else` yourself instead of relying on the default unknown
message.

## How do I group subcommands under a prefix?

Inside your root handler, add a nested class annotated with `@Command`. The class path becomes a prefix for every
command inside that group.

```java
@CommandHandler
@RootCommand(value = "admin", aliases = {"adm"}, description = "Administrative commands")
public class AdminCommands {

    @Command("coin")
    public static class CoinCommands {

        @DefaultCommand
        public void root(@Sender CommandSender sender) {
            sender.sendMessage("Try /admin coin <player> set <amount>");
        }

        @Command("<player> set <amount>")
        public void setCoin(
                @Sender CommandSender sender,
                @Completion("onlinePlayers") Player player,
                Integer amount
        ) {
            sender.sendMessage("Setting " + player.getName() + "'s coins to " + amount);
        }
    }

}
```

This gives you:

- `/admin coin`
- `/admin coin <player> set <amount>`

Nested groups may define `@DefaultCommand`, but keep `@CatchUnknown` on the root handler. Nested groups do not support
`@CatchUnknown`.

## How do I use Bukkit arguments and completions?

This adapter ships built-in argument resolvers for:

- `Player`
- `OfflinePlayer`
- `World`

It also ships these named completion IDs:

- `onlinePlayers`
- `offlinePlayers`
- `worlds`
- `materials`

`Player`, `OfflinePlayer`, and `World` already provide default tab completion through their argument resolvers. Add
`@Completion(...)` when you want to force a specific named provider or when the parameter type would not otherwise have
one.

Inside a handler, these focused command methods are enough:

```java

@Command("<player> <world>")
public void teleport(
        @Completion("onlinePlayers") Player player,
        @Completion("worlds") World world
) {
    player.teleport(world.getSpawnLocation());
}

@Command("visit <player>")
public void visit(OfflinePlayer player) {
}

@Command("give <player> <material>")
public void give(
        @Completion("onlinePlayers") Player player,
        @Completion("materials") String materialName
) {
}
```

In this example, `visit` can omit `@Completion` because `OfflinePlayer` already has a default completion provider.

## How do I add a cooldown?

Inside a handler, the smallest method example looks like this:

```java

@Cooldown(time = "30s")
@Command("daily")
public void daily(@Sender Player player) {
    player.sendMessage("Daily kit claimed.");
}
```

The `time` value accept any time format, so values like `30s`, `5m`, and `1h30m` just works.

If you need per-execution logic, register a `CommandCooldownPolicy` bean:

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

        return Duration.ofSeconds(30);
    }
}
```

Then reference the policy from your command method:

```java

@Cooldown(time = "30s", policy = VipCooldownPolicy.class)
@Command("daily")
public void daily(@Sender Player player) {
    player.sendMessage("Daily kit claimed.");
}
```

Return `Duration.ZERO` or a negative duration when you want to skip the cooldown for that execution.

## How do I add a custom argument type?

Register a normal bean that implements `CommandArgumentResolver<T>`. If your type should also tab-complete by default,
override `defaultCompletionProvider()` in the same resolver:

```java
@Component
public class WarpResolver implements CommandArgumentResolver<Warp> {
    private final WarpService warpService;

    public WarpResolver(WarpService warpService) {
        this.warpService = warpService;
    }

    @Override
    public boolean supports(CommandParameterMetadata parameter) {
        return Warp.class.equals(parameter.getValueType());
    }

    @Override
    public Warp resolve(CommandExecutionContext context,
                        CommandParameterMetadata parameter,
                        String input) {
        Warp warp = warpService.findByName(input);
        if (warp == null) {
            throw new IllegalArgumentException("Warp not found: " + input);
        }
        return warp;
    }

    @Override
    public CommandCompletionProvider defaultCompletionProvider() {
        return (context, parameter, input) -> warpService.allNames();
    }
}
```

Inside a handler, use the custom type directly:

```java

@Command("warp <warp>")
public void warp(@Sender Player player, Warp warp) {
    player.teleport(warp.getLocation());
}
```

Use this when you want command methods to receive a domain object instead of a raw `String`. Override
`defaultCompletionProvider()` when every parameter of that type should get the same completion behavior without needing
`@Completion(...)` on each command method.

## How do I customize command messages?

Registering a bean extending `DefaultCommandMessages` is the shortest way to override only the messages you care
about.

```java
@Component
@Primary
public class PluginCommandMessages extends DefaultCommandMessages {

    @Override
    public String noPermission(CommandExecutionContext context, String permission) {
        return "You need " + permission + " to run this command.";
    }

    @Override
    public String unknownSubcommand(CommandExecutionContext context) {
        return "That subcommand does not exist.";
    }
}
```

Use this when you want to change validation, permission, error, or cooldown text for every command.

## How do I run logic before or after every command?

Register a bean implementing `CommandInterceptor`. You can hook into three moments:

- `before()` runs before the command method
- `after()` runs after a successful command execution
- `onError()` runs when the command throws an exception

Inside `before()`, return `CommandExecutionDecision.continueExecution()` to let the command run, or
`CommandExecutionDecision.stopExecution()` if you want to block it yourself.

```java
@Component
public class AuditInterceptor implements CommandInterceptor {

    @Override
    public CommandExecutionDecision before(CommandExecutionContext context,
                                           CommandInvocationPlan invocation) {
        if (!context.getSender().hasPermission("plugin.allowed")) {
            context.sendMessage("You cannot use commands right now.");
            return CommandExecutionDecision.stopExecution();
        }

        context.getPlugin().getLogger().info(
                "Starting command: " + context.getInput()
        );
        return CommandExecutionDecision.continueExecution();
    }

    @Override
    public void after(CommandExecutionContext context,
                      CommandInvocationPlan invocation,
                      Object result) {
        context.getPlugin().getLogger().info(
                "Finished command: " + context.getInput()
        );
    }

    @Override
    public void onError(CommandExecutionContext context,
                        CommandInvocationPlan invocation,
                        Throwable throwable) {
        context.getPlugin().getLogger().warning(
                "Command failed: " + context.getInput() + " because " + throwable.getMessage()
        );
    }
}
```

Use this when the behavior should apply to every command, including default and unknown handlers. Use `before()` to
gate or cancel execution, `after()` for success-only follow-up logic, and `onError()` for failure reporting or cleanup.

## How do I bind logic to a custom annotation?

Sometimes you may want to run behavior only on some commands instead of every command. For example, you may want to
require an extra confirmation step for destructive commands such as deleting a warp, resetting data, or removing a
player's home.

In that case, create your own annotation, bind it with `@CommandInterceptedBy`, and implement a matching
`CommandAnnotationInterceptor`. This is the opt-in version of an interceptor: it only runs where you place the
annotation.

Create the annotation and interceptor bean:

```java

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@CommandInterceptedBy(RequireConfirmationInterceptor.class)
public @interface RequireConfirmation {
    String value();
}

@Component
public class RequireConfirmationInterceptor implements CommandAnnotationInterceptor<RequireConfirmation> {
    private final ConfirmationService confirmationService;

    public RequireConfirmationInterceptor(ConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @Override
    public CommandExecutionDecision before(RequireConfirmation annotation,
                                           CommandExecutionContext context,
                                           CommandInvocationPlan invocation) {
        if (!confirmationService.consume(context.getSender().getIdentity(), annotation.value())) {
            context.sendMessage("Run /confirm " + annotation.value() + " and then try again.");
            return CommandExecutionDecision.stopExecution();
        }

        return CommandExecutionDecision.continueExecution();
    }
}
```

Inside a handler, apply the annotation to the command method:

```java

@RequireConfirmation("warp-delete")
@Command("delete <warp>")
public void deleteWarp(@Sender Player player, Warp warp) {
    player.sendMessage("Deleted warp " + warp.getName());
}
```

Just like a global interceptor, an annotation-bound interceptor can also use `after()` and `onError()` when you need
success-only follow-up logic or error handling, but only for commands marked with that annotation.

Use this when the behavior should be opt-in instead of global.

## How do I register custom completions and replacements?

Use this when the same tab completion or text fragment shows up in more than one command.

Instead of repeating the same player list, warp list, permission string, or usage fragment across handlers, register it
once and give it a name. The commands module exposes two small extension points for that:

- `CommandCompletionRegistryCustomizer` for reusable named completions such as `warps`
- `CommandReplacementRegistryCustomizer` for reusable `%key%` tokens in command metadata

These are ordinary Spigot Boot beans, so a small `@Configuration` class is enough:

```java
@Configuration
public class CommandsCustomization {

    @Bean
    public CommandCompletionRegistryCustomizer commandCompletionRegistryCustomizer(WarpService warpService) {
        return registry -> registry.register("warps", (context, parameter, input) -> warpService.allNames());
    }

    @Bean
    public CommandReplacementRegistryCustomizer commandReplacementRegistryCustomizer() {
        return registry -> registry.register("admin.permission", "plugin.admin");
    }
}
```

Inside a handler, reference the named completion and replacement token:

```java

@Command(value = "warp <warp>", usage = "/warp <warp>")
@Permission("%admin.permission%")
public void warp(@Completion("warps") Warp warp) {
}
```

Use the completion customizer when you want reusable named tab completions. Use the replacement customizer when you
want reusable `%key%` tokens in command path, description, usage, or permission text.

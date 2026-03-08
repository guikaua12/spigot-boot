# Commands Spigot

`spigot-boot-commands-spigot` adds annotation-driven Bukkit command registration to Spigot Boot.

## Setup

Add the module dependency:

```xml
<dependency>
    <groupId>tech.guilhermekaua.spigot-boot</groupId>
    <artifactId>spigot-boot-commands-spigot</artifactId>
    <version>2.0.2</version>
</dependency>
```

No `plugin.yml` command entries are required. The module is discovered through the normal Spigot Boot module loader.

## Example

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
    }

    @CatchUnknown
    public void unknown(@Sender CommandSender sender, CommandExecutionContext context) {
        sender.sendMessage("Unknown command: " + context.getInput());
    }
}
```

## Nested Command Groups

`@Command` can also annotate classes nested inside a `@CommandHandler`. The nested class path is treated as a prefix,
and
its command methods are flattened into the same root namespace.

```java

@CommandHandler
@RootCommand("admin|adm")
public class AdminCommands {

    @Command("coin")
    public static class CoinCommands {
        @Command("<player> set <amount>")
        public void setCoin(
                @Sender CommandSender sender,
                String player,
                Integer amount
        ) {
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

## Custom Messages

Provide a primary `CommandMessages` bean:

```java
@Component
@Primary
public class CustomCommandMessages implements CommandMessages {
    // implement the message methods you want to customize
}
```

## Global Interceptors

Register a `CommandInterceptor` bean to run logic for every command.

If a `CommandInterceptor` bean exists in the context, it is automatically applied to all commands, including default
and unknown command handlers.

Return `CommandExecutionDecision.stopExecution()` to block the command before arguments are bound or the handler method
is
invoked:

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

## Annotation-Bound Interceptors

For opt-in behavior, create a custom annotation and bind it to a `CommandAnnotationInterceptor`.

`CommandAnnotationInterceptor` does not extend `CommandInterceptor`. Use `CommandInterceptor` for global behavior and
`CommandAnnotationInterceptor` for annotation-scoped behavior.

```java

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@CommandInterceptedBy(CooldownInterceptor.class)
public @interface Cooldown {
    String time();
}

@Component
public class CooldownInterceptor implements CommandAnnotationInterceptor<Cooldown> {
    @Override
    public CommandExecutionDecision before(Cooldown annotation,
                                           CommandExecutionContext context,
                                           CommandInvocationPlan invocation) {
        if (isOnCooldown(context.getSender(), annotation.time())) {
            context.sendMessage("Wait before using this command again.");
            return CommandExecutionDecision.stopExecution();
        }
        return CommandExecutionDecision.continueExecution();
    }
}

@CommandHandler
@RootCommand("kit")
public class KitCommands {
    @Cooldown(time = "30s")
    @Command("daily")
    public void daily(@Sender Player sender) {
    }
}
```

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

# Commands

`spigot-boot-commands` is the shared, platform-neutral command engine for Spigot Boot.

It contains the parts of the command system that do not need Bukkit:

- annotations and metadata parsing
- route compilation and validation
- argument binding
- interceptor execution
- messages, replacements, completions, and cooldown SPIs
- `CommandSenderHandle`
- `CommandPlatformSupport`
- `CommandRootCompiler`

It does not register commands into any game platform by itself.

## What Changed With The Refactor

Before this split, the command engine lived inside the Spigot module, so building another platform adapter meant
copying or reworking Spigot-specific code.

Now you can:

- build a new adapter such as Sponge on top of the same parser, route compiler, binder, interceptor chain, and cooldown
  system
- write command extensions once and reuse them across multiple platforms
- unit test shared command behavior without MockBukkit or Bukkit classes
- keep handler logic platform-neutral by targeting `CommandSenderHandle` through `CommandExecutionContext#getSender()`

## When To Depend On This Module

Depend on `spigot-boot-commands` if you are:

- building a new platform adapter
- publishing shared command infrastructure
- writing platform-neutral command libraries, policies, or interceptors
- testing command parsing, routing, messages, or interception outside Bukkit

If you are writing a normal Spigot plugin, depend on `spigot-boot-commands-spigot` instead. It brings this module
transitively.

## Shared Extension Points

The main reusable SPIs now live here:

- `CommandArgumentResolver`
- `CommandCompletionProvider`
- `CommandCompletionRegistryCustomizer`
- `CommandReplacementRegistryCustomizer`
- `CommandInterceptor`
- `CommandAnnotationInterceptor`
- `CommandMessages`
- `CommandCooldownPolicy`

If your implementation only needs `CommandExecutionContext`, parsed arguments, injected services, or
`CommandSenderHandle`,
it can be reused by any future platform adapter.

## Portable Command Logic

Use `context.getSender()` when you want logic that does not care whether the current platform is Spigot, Sponge, or
something else.

```java

@Component
public class AuditInterceptor implements CommandInterceptor {
   @Override
   public CommandExecutionDecision before(CommandExecutionContext context, CommandInvocationPlan invocation) {
      context.getPlugin().getLogger().info(
              "Sender " + context.getSender().getIdentity() + " executed " + context.getInput()
      );
      return CommandExecutionDecision.continueExecution();
   }
}
```

This code is portable because it only uses the shared API.

If you need a native platform sender type, use `context.getSender().unwrap(...)` and treat that as adapter-specific
logic:

```java
CommandSender sender = context.getSender().unwrap(CommandSender.class).orElse(null);
```

## Portable Cooldowns And Messages

The cooldown and message SPIs are now reusable outside Spigot-specific code.

```java

@Component
public class PremiumCooldownPolicy implements CommandCooldownPolicy {
   @Override
   public Duration resolve(Cooldown annotation,
                           CommandExecutionContext context,
                           CommandInvocationPlan invocation) {
      if (context.getSender().hasPermission("plugin.premium")) {
         return Duration.ofSeconds(5);
      }

      return Duration.ofSeconds(30);
   }
}
```

That policy can be reused by any adapter that honors the shared command engine.

## How To Build A New Platform Adapter

The split was designed so a new platform module can stay thin.

Typical adapter responsibilities are:

1. Depend on `spigot-boot-commands`.
2. Reuse the shared `CommandsConfiguration`.
3. Implement `CommandPlatformSupport`.
4. Wrap the native sender in a `CommandSenderHandle`.
5. Register platform-specific argument resolvers and completion customizers.
6. Build platform-aware beans such as:
   `CommandParameterRoleResolver`, `CommandInvocationFactory`, `CommandRouteFactory`, `CommandDispatcher`.
7. Use `CommandRootCompiler` to compile and validate discovered `@CommandHandler` beans.
8. Register the compiled roots into the target platform.

The shared `CommandsConfiguration` already provides the generic pieces:

- parser
- shared resolver registry
- shared completion registry
- messages provider
- interceptor chain
- invocation executor

The adapter supplies the rest.

## Minimal Adapter Example

```java

@Configuration
public class ExamplePlatformCommandsConfiguration {
   @Bean
   public CommandPlatformSupport commandPlatformSupport() {
      return new ExamplePlatformSupport();
   }

   @Bean
   public CommandParameterRoleResolver commandParameterRoleResolver(CommandPlatformSupport platformSupport) {
      return new CommandParameterRoleResolver(platformSupport);
   }

   @Bean
   public CommandInvocationFactory commandInvocationFactory(CommandParameterRoleResolver roleResolver) {
      return new CommandInvocationFactory(roleResolver);
   }
}
```

Then a platform listener or registrar can call `CommandRootCompiler.compile(context)` and register the resulting
`CompiledRootCommand` objects into the target platform.

## Shared Built-Ins

This module intentionally keeps only generic built-ins:

- argument resolvers for `String`, numeric types, `boolean`, `enum`, and `UUID`
- completion provider for `booleans`

Everything platform-specific belongs in the adapter module.

## Testing Without Bukkit

Another new benefit of the split is that command behavior can be tested with fake senders instead of Bukkit mocks.

That is now how the shared module tests parser, dispatcher, messages, interception, and cooldown behavior.

This makes shared command logic faster to test and easier to reuse in non-Spigot environments.

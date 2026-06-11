package tech.guilhermekaua.spigotboot.commands.cooldown;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.annotations.Cooldown;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.time.Duration;

public interface CommandCooldownPolicy {
    default void validate(Cooldown annotation,
                          Context context,
                          CommandInvocationPlan invocation) {
    }

    Duration resolve(Cooldown annotation,
                     CommandExecutionContext context,
                     CommandInvocationPlan invocation);
}

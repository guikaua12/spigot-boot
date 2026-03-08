package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

public interface CommandInterceptor extends Ordered {
    default CommandExecutionDecision before(CommandExecutionContext context, CommandInvocationPlan invocation) {
        return CommandExecutionDecision.continueExecution();
    }

    default void after(CommandExecutionContext context, CommandInvocationPlan invocation, Object result) {
    }

    default void onError(CommandExecutionContext context, CommandInvocationPlan invocation, Throwable throwable) {
    }
}

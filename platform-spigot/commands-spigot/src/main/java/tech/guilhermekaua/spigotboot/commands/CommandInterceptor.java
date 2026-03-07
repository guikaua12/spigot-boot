package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

public interface CommandInterceptor extends Ordered {
    void before(CommandExecutionContext context, CommandInvocationPlan invocation);

    void after(CommandExecutionContext context, CommandInvocationPlan invocation, Object result);

    void onError(CommandExecutionContext context, CommandInvocationPlan invocation, Throwable throwable);
}

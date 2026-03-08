package tech.guilhermekaua.spigotboot.commands;

import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.lang.annotation.Annotation;

public interface CommandAnnotationInterceptor<A extends Annotation> extends Ordered {
    default CommandExecutionDecision before(A annotation,
                                            CommandExecutionContext context,
                                            CommandInvocationPlan invocation) {
        return CommandExecutionDecision.continueExecution();
    }

    default void after(A annotation,
                       CommandExecutionContext context,
                       CommandInvocationPlan invocation,
                       Object result) {
    }

    default void onError(A annotation,
                         CommandExecutionContext context,
                         CommandInvocationPlan invocation,
                         Throwable throwable) {
    }
}

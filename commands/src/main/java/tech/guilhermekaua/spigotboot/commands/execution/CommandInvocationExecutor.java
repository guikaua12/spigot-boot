package tech.guilhermekaua.spigotboot.commands.execution;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionDecision;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public class CommandInvocationExecutor {
    private final CommandInterceptorChain interceptorChain;

    public CommandInvocationExecutor() {
        this(new CommandInterceptorChain());
    }

    public CommandInvocationExecutor(CommandInterceptorChain interceptorChain) {
        this.interceptorChain = interceptorChain;
    }

    public Object execute(CommandExecutionContext context, CommandInvocationPlan plan, Object[] arguments) throws Throwable {
        CommandInterceptorChain.ResolvedChain resolvedChain = interceptorChain.resolve(context, plan);
        CommandExecutionDecision decision = resolvedChain.before(context, plan);
        if (!decision.shouldContinue()) {
            return null;
        }
        return execute(context, plan, arguments, resolvedChain);
    }

    public Object execute(CommandExecutionContext context,
                          CommandInvocationPlan plan,
                          Object[] arguments,
                          CommandInterceptorChain.ResolvedChain resolvedChain) throws Throwable {
        Objects.requireNonNull(resolvedChain, "resolvedChain cannot be null.");

        try {
            Object result = plan.getMethod().invoke(plan.getHandlerBean(), arguments);
            resolvedChain.after(context, plan, result);
            return result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            resolvedChain.onError(context, plan, cause);
            throw cause;
        } catch (Throwable throwable) {
            resolvedChain.onError(context, plan, throwable);
            throw throwable;
        }
    }
}

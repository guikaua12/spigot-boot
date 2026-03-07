package tech.guilhermekaua.spigotboot.commands.execution;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;

import java.lang.reflect.InvocationTargetException;

public class CommandInvocationExecutor {
    private final CommandInterceptorChain interceptorChain;

    public CommandInvocationExecutor(CommandInterceptorChain interceptorChain) {
        this.interceptorChain = interceptorChain;
    }

    public Object execute(CommandExecutionContext context, CommandInvocationPlan plan, Object[] arguments) throws Throwable {
        interceptorChain.before(context, plan);
        try {
            plan.getMethod().setAccessible(true);
            Object result = plan.getMethod().invoke(plan.getHandlerBean(), arguments);
            interceptorChain.after(context, plan, result);
            return result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            interceptorChain.onError(context, plan, cause);
            throw cause;
        } catch (Throwable throwable) {
            interceptorChain.onError(context, plan, throwable);
            throw throwable;
        }
    }
}

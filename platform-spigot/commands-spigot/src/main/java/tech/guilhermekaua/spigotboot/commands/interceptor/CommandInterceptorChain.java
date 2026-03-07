package tech.guilhermekaua.spigotboot.commands.interceptor;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandInterceptor;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CommandInterceptorChain {
    public void before(CommandExecutionContext context, CommandInvocationPlan invocation) {
        for (CommandInterceptor interceptor : resolveInterceptors(context)) {
            interceptor.before(context, invocation);
        }
    }

    public void after(CommandExecutionContext context, CommandInvocationPlan invocation, Object result) {
        for (CommandInterceptor interceptor : resolveInterceptors(context)) {
            interceptor.after(context, invocation, result);
        }
    }

    public void onError(CommandExecutionContext context, CommandInvocationPlan invocation, Throwable throwable) {
        for (CommandInterceptor interceptor : resolveInterceptors(context)) {
            interceptor.onError(context, invocation, throwable);
        }
    }

    private List<CommandInterceptor> resolveInterceptors(CommandExecutionContext context) {
        Collection<Object> instances = context.getContext()
                .getDependencyManager()
                .getBeanInstanceRegistry()
                .asMapView()
                .values();

        List<CommandInterceptor> interceptors = new ArrayList<>();
        for (Object instance : instances) {
            if (instance instanceof CommandInterceptor) {
                interceptors.add((CommandInterceptor) instance);
            }
        }
        return CommandSupport.sortBeans(interceptors);
    }
}

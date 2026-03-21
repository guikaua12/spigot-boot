package tech.guilhermekaua.spigotboot.commands.execution;

import tech.guilhermekaua.spigotboot.commands.CommandInterceptorAnnotationBinding;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinding;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterRole;

import java.lang.reflect.Method;
import java.util.*;

public final class CommandInvocationPlan {
    private final Object handlerBean;
    private final Method method;
    private final List<CommandParameterBinding> bindings;
    private final List<CommandInterceptorAnnotationBinding> interceptorBindings;
    private final Map<String, CommandParameterBinding> parsedBindings;

    public CommandInvocationPlan(Object handlerBean,
                                 Method method,
                                 List<CommandParameterBinding> bindings,
                                 List<CommandInterceptorAnnotationBinding> interceptorBindings) {
        this.handlerBean = handlerBean;
        this.method = method;
        this.method.setAccessible(true);
        this.bindings = Collections.unmodifiableList(new ArrayList<>(bindings));
        this.interceptorBindings = interceptorBindings == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(interceptorBindings));

        Map<String, CommandParameterBinding> byToken = new HashMap<>();
        for (CommandParameterBinding binding : bindings) {
            if (binding.getRole() == CommandParameterRole.PARSED && binding.getTokenName() != null) {
                byToken.put(binding.getTokenName(), binding);
            }
        }
        this.parsedBindings = Collections.unmodifiableMap(byToken);
    }

    public Object getHandlerBean() {
        return handlerBean;
    }

    public Method getMethod() {
        return method;
    }

    public List<CommandParameterBinding> getBindings() {
        return bindings;
    }

    public List<CommandInterceptorAnnotationBinding> getInterceptorBindings() {
        return interceptorBindings;
    }

    public CommandParameterBinding getParsedBinding(String tokenName) {
        return parsedBindings.get(tokenName);
    }
}

package tech.guilhermekaua.spigotboot.commands.binding;

import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolver;
import tech.guilhermekaua.spigotboot.commands.CommandArgumentResolverRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.dependency.injector.InjectionPoint;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CommandParameterBinder {
    private final CommandArgumentResolverRegistry resolverRegistry;

    public CommandParameterBinder(CommandArgumentResolverRegistry resolverRegistry) {
        this.resolverRegistry = Objects.requireNonNull(resolverRegistry, "resolverRegistry must not be null");
    }

    public Object[] bind(CommandExecutionContext context, CommandInvocationPlan plan, Map<String, String> parsedArguments) {
        Object[] arguments = new Object[plan.getBindings().size()];
        for (int i = 0; i < plan.getBindings().size(); i++) {
            CommandParameterBinding binding = plan.getBindings().get(i);
            arguments[i] = bind(context, binding, parsedArguments);
        }
        return arguments;
    }

    private Object bind(CommandExecutionContext context,
                        CommandParameterBinding binding,
                        Map<String, String> parsedArguments) {
        switch (binding.getRole()) {
            case CONTEXT:
                return context;
            case SENDER:
                return bindSender(context, binding.getMetadata());
            case INJECTED:
                return context.getContext()
                        .getDependencyManager()
                        .resolveDependency(InjectionPoint.fromParameter(binding.getMetadata().getParameter()));
            case PARSED:
                return bindParsed(context, binding, parsedArguments.get(binding.getTokenName()));
            default:
                throw new IllegalStateException("Unsupported parameter binding role: " + binding.getRole());
        }
    }

    private Object bindSender(CommandExecutionContext context, CommandParameterMetadata parameter) {
        Optional<?> resolved = context.getSender().unwrap(parameter.getValueType());
        if (!resolved.isPresent()) {
            throw CommandBindingException.senderMismatch(parameter, parameter.getValueType());
        }
        return resolved.get();
    }

    private Object bindParsed(CommandExecutionContext context,
                              CommandParameterBinding binding,
                              String input) {
        CommandParameterMetadata parameter = binding.getMetadata();
        String resolvedInput = input;
        if (resolvedInput == null || resolvedInput.trim().isEmpty()) {
            if (parameter.hasDefaultValue()) {
                resolvedInput = parameter.getDefaultValue();
            } else if (parameter.isOptionalWrapper()) {
                return Optional.empty();
            } else if (binding.isOptional()) {
                if (parameter.isPrimitive()) {
                    throw CommandBindingException.missing(parameter);
                }
                return null;
            } else {
                throw CommandBindingException.missing(parameter);
            }
        }

        CommandArgumentResolver<?> resolver = resolverRegistry.resolve(parameter)
                .orElseThrow(() -> new IllegalStateException("No command argument resolver found for type " + parameter.getValueType().getName()));

        try {
            Object resolved = resolver.resolve(context, parameter, resolvedInput);
            if (parameter.isOptionalWrapper()) {
                return Optional.ofNullable(resolved);
            }
            return resolved;
        } catch (CommandBindingException e) {
            throw e;
        } catch (Exception e) {
            throw CommandBindingException.invalid(parameter, resolvedInput, e);
        }
    }
}

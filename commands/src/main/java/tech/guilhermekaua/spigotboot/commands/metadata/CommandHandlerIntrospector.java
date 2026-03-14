package tech.guilhermekaua.spigotboot.commands.metadata;

import tech.guilhermekaua.spigotboot.commands.CommandAnnotationInterceptor;
import tech.guilhermekaua.spigotboot.commands.CommandInterceptorAnnotationBinding;
import tech.guilhermekaua.spigotboot.commands.annotations.*;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

public class CommandHandlerIntrospector {
    private final NestedCommandHandlerInstantiator nestedCommandHandlerInstantiator = new NestedCommandHandlerInstantiator();

    public RootCommandMetadata introspect(Object handlerBean) {
        return introspect(handlerBean, null);
    }

    public RootCommandMetadata introspect(Object handlerBean, DependencyManager dependencyManager) {
        Objects.requireNonNull(handlerBean, "handlerBean cannot be null.");

        Class<?> handlerType = ProxyUtils.getRealClass(handlerBean);
        CommandHandler commandHandler = handlerType.getAnnotation(CommandHandler.class);
        if (commandHandler == null) {
            throw new IllegalStateException("Handler bean is not annotated with @CommandHandler: " + handlerType.getName());
        }
        if (handlerType.isAnnotationPresent(Command.class)) {
            throw new IllegalStateException("Root command handler cannot also declare type-level @Command: " + handlerType.getName());
        }

        RootCommand rootCommand = handlerType.getAnnotation(RootCommand.class);
        if (rootCommand == null) {
            throw new IllegalStateException("Command handler must declare @RootCommand: " + handlerType.getName());
        }

        List<CommandMethodMetadata> methods = new ArrayList<>();
        collectMethods(handlerBean, handlerType, null, true, dependencyManager, new LinkedHashMap<>(), methods);

        if (methods.isEmpty()) {
            throw new IllegalStateException("Command handler declares no command methods: " + handlerType.getName());
        }

        return new RootCommandMetadata(
                handlerBean,
                handlerType,
                CommandAliasSet.of(rootCommand.value(), rootCommand.aliases()),
                rootCommand.description(),
                rootCommand.usage(),
                methods
        );
    }

    private void collectMethods(Object handlerBean,
                                Class<?> handlerType,
                                CommandAliasSet pathAliases,
                                boolean rootScope,
                                DependencyManager dependencyManager,
                                Map<Class<? extends Annotation>, CommandInterceptorAnnotationBinding> inheritedInterceptorBindings,
                                List<CommandMethodMetadata> methods) {
        Map<Class<? extends Annotation>, CommandInterceptorAnnotationBinding> typeInterceptorBindings =
                overlayInterceptorBindings(inheritedInterceptorBindings, getDirectInterceptorBindings(handlerType));

        for (Method method : handlerType.getDeclaredMethods()) {
            CommandMethodMetadata methodMetadata = introspectMethod(
                    handlerBean,
                    handlerType,
                    method,
                    pathAliases,
                    rootScope,
                    typeInterceptorBindings
            );
            if (methodMetadata != null) {
                methods.add(methodMetadata);
            }
        }

        for (Class<?> nestedType : handlerType.getDeclaredClasses()) {
            Command nestedCommand = nestedType.getAnnotation(Command.class);
            if (nestedCommand == null) {
                continue;
            }

            validateNestedHandlerType(nestedType);
            CommandAliasSet nestedAliases = combineAliases(pathAliases, CommandAliasSet.of(nestedCommand.value(), nestedCommand.aliases()));
            if (nestedAliases.allValues().isEmpty()) {
                throw new IllegalStateException("Nested command group must declare at least one path: " + nestedType.getName());
            }

            Object nestedHandler = nestedCommandHandlerInstantiator.instantiate(handlerBean, nestedType, dependencyManager);
            int sizeBefore = methods.size();
            collectMethods(nestedHandler, nestedType, nestedAliases, false, dependencyManager, typeInterceptorBindings, methods);
            if (methods.size() == sizeBefore) {
                throw new IllegalStateException("Nested command group declares no executable command methods: " + nestedType.getName());
            }
        }
    }

    private CommandMethodMetadata introspectMethod(Object handlerBean,
                                                   Class<?> handlerType,
                                                   Method method,
                                                   CommandAliasSet pathAliases,
                                                   boolean rootScope,
                                                   Map<Class<? extends Annotation>, CommandInterceptorAnnotationBinding> inheritedInterceptorBindings) {
        Command command = method.getAnnotation(Command.class);
        DefaultCommand defaultCommand = method.getAnnotation(DefaultCommand.class);
        CatchUnknown catchUnknown = method.getAnnotation(CatchUnknown.class);

        int annotations = (command != null ? 1 : 0) + (defaultCommand != null ? 1 : 0) + (catchUnknown != null ? 1 : 0);
        if (annotations == 0) {
            return null;
        }
        if (annotations > 1) {
            throw new IllegalStateException("Command method declares multiple command role annotations: " + method);
        }

        Permission permission = method.getAnnotation(Permission.class);
        Completion completion = method.getAnnotation(Completion.class);

        CommandMethodKind kind;
        CommandAliasSet aliases;
        String description = "";
        String usage = "";
        if (command != null) {
            kind = CommandMethodKind.COMMAND;
            aliases = combineAliases(pathAliases, CommandAliasSet.of(command.value(), command.aliases()));
            description = command.description();
            usage = command.usage();
        } else if (defaultCommand != null) {
            if (!rootScope) {
                if (pathAliases == null || pathAliases.allValues().isEmpty()) {
                    throw new IllegalStateException("Nested @DefaultCommand requires a type-level @Command path: " + method);
                }
                kind = CommandMethodKind.COMMAND;
                aliases = pathAliases;
            } else {
                kind = CommandMethodKind.DEFAULT;
                aliases = CommandAliasSet.of("", new String[0]);
            }
        } else {
            if (!rootScope) {
                throw new IllegalStateException("Nested command groups do not support @CatchUnknown: " + method);
            }
            kind = CommandMethodKind.UNKNOWN;
            aliases = CommandAliasSet.of("", new String[0]);
        }

        List<CommandParameterMetadata> parameters = new ArrayList<>();
        Parameter[] reflectedParameters = method.getParameters();
        for (int index = 0; index < reflectedParameters.length; index++) {
            parameters.add(introspectParameter(reflectedParameters[index], index));
        }

        Map<Class<? extends Annotation>, CommandInterceptorAnnotationBinding> methodInterceptorBindings =
                overlayInterceptorBindings(inheritedInterceptorBindings, getDirectInterceptorBindings(method));

        return new CommandMethodMetadata(
                handlerBean,
                handlerType,
                method,
                kind,
                aliases,
                description,
                usage,
                permission == null ? "" : permission.value(),
                splitCompletionIds(completion == null ? "" : completion.value()),
                new ArrayList<>(methodInterceptorBindings.values()),
                parameters
        );
    }

    private CommandParameterMetadata introspectParameter(Parameter parameter, int index) {
        Name name = parameter.getAnnotation(Name.class);
        Completion completion = parameter.getAnnotation(Completion.class);
        DefaultValue defaultValue = parameter.getAnnotation(DefaultValue.class);
        String lookupName = name == null ? parameter.getName() : name.value().trim();

        Class<?> rawType = parameter.getType();
        Class<?> valueType = rawType;
        boolean optionalWrapper = false;
        if (Optional.class.equals(rawType)) {
            optionalWrapper = true;
            valueType = extractOptionalValueType(parameter.getParameterizedType());
        }

        return new CommandParameterMetadata(
                parameter,
                index,
                parameter.getName(),
                lookupName,
                rawType,
                valueType,
                optionalWrapper,
                rawType.isPrimitive(),
                parameter.isAnnotationPresent(Sender.class),
                completion == null ? "" : completion.value(),
                defaultValue == null ? null : defaultValue.value()
        );
    }

    private Class<?> extractOptionalValueType(Type type) {
        if (!(type instanceof ParameterizedType)) {
            throw new IllegalStateException("Optional command parameter is missing generic type information.");
        }

        Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
        if (arguments.length != 1 || !(arguments[0] instanceof Class)) {
            throw new IllegalStateException("Unsupported Optional command parameter type: " + type);
        }
        return (Class<?>) arguments[0];
    }

    private List<String> splitCompletionIds(String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }

        String[] parts = trimmed.split("\\s+");
        List<String> ids = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                ids.add(part);
            }
        }
        return ids;
    }

    private void validateNestedHandlerType(Class<?> nestedType) {
        if (nestedType.isAnnotationPresent(CommandHandler.class)) {
            throw new IllegalStateException("Nested command group cannot declare @CommandHandler: " + nestedType.getName());
        }
        if (nestedType.isAnnotationPresent(RootCommand.class)) {
            throw new IllegalStateException("Nested command group cannot declare @RootCommand: " + nestedType.getName());
        }

        int modifiers = nestedType.getModifiers();
        if (Modifier.isAbstract(modifiers) || nestedType.isInterface() || nestedType.isEnum() || nestedType.isAnnotation()) {
            throw new IllegalStateException("Nested command group must be a concrete class: " + nestedType.getName());
        }
    }

    private CommandAliasSet combineAliases(CommandAliasSet parent, CommandAliasSet child) {
        if (parent == null || parent.allValues().isEmpty()) {
            return child;
        }
        if (child == null || child.allValues().isEmpty()) {
            return parent;
        }

        Map<String, String> combined = new LinkedHashMap<>();
        for (String parentValue : parent.allValues()) {
            for (String childValue : child.allValues()) {
                String value = joinCommandPath(parentValue, childValue);
                if (!value.isEmpty()) {
                    combined.put(normalizeCommandPath(value), value);
                }
            }
        }

        if (combined.isEmpty()) {
            return new CommandAliasSet("", Collections.emptyList());
        }

        List<String> values = new ArrayList<>(combined.values());
        return new CommandAliasSet(values.get(0), values.subList(1, values.size()));
    }

    private String joinCommandPath(String left, String right) {
        String leftTrimmed = left == null ? "" : left.trim();
        String rightTrimmed = right == null ? "" : right.trim();
        if (leftTrimmed.isEmpty()) {
            return rightTrimmed;
        }
        if (rightTrimmed.isEmpty()) {
            return leftTrimmed;
        }
        return leftTrimmed + " " + rightTrimmed;
    }

    private String normalizeCommandPath(String value) {
        String[] parts = value.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(CommandSupport.normalizeLabel(part));
        }
        return builder.toString();
    }

    private Map<Class<? extends Annotation>, CommandInterceptorAnnotationBinding> overlayInterceptorBindings(
            Map<Class<? extends Annotation>, CommandInterceptorAnnotationBinding> inheritedBindings,
            List<CommandInterceptorAnnotationBinding> directBindings) {
        Map<Class<? extends Annotation>, CommandInterceptorAnnotationBinding> resolved =
                new LinkedHashMap<>(inheritedBindings);
        for (CommandInterceptorAnnotationBinding binding : directBindings) {
            resolved.put(binding.getAnnotationType(), binding);
        }
        return resolved;
    }

    private List<CommandInterceptorAnnotationBinding> getDirectInterceptorBindings(AnnotatedElement element) {
        List<CommandInterceptorAnnotationBinding> bindings = new ArrayList<>();
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            CommandInterceptedBy interceptedBy = annotation.annotationType().getAnnotation(CommandInterceptedBy.class);
            if (interceptedBy == null) {
                continue;
            }

            List<Class<? extends CommandAnnotationInterceptor<?>>> interceptorTypes = Arrays.asList(interceptedBy.value());
            bindings.add(new CommandInterceptorAnnotationBinding(annotation, interceptorTypes));
        }
        return bindings;
    }
}

package tech.guilhermekaua.spigotboot.commands.metadata;

import tech.guilhermekaua.spigotboot.commands.annotations.*;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CommandHandlerIntrospector {
    public RootCommandMetadata introspect(Object handlerBean) {
        Class<?> handlerType = ProxyUtils.getRealClass(handlerBean);
        CommandHandler commandHandler = handlerType.getAnnotation(CommandHandler.class);
        if (commandHandler == null) {
            throw new IllegalStateException("Handler bean is not annotated with @CommandHandler: " + handlerType.getName());
        }

        RootCommand rootCommand = handlerType.getAnnotation(RootCommand.class);
        if (rootCommand == null) {
            throw new IllegalStateException("Command handler must declare @RootCommand: " + handlerType.getName());
        }

        List<CommandMethodMetadata> methods = new ArrayList<>();
        for (Method method : handlerType.getDeclaredMethods()) {
            CommandMethodMetadata methodMetadata = introspectMethod(handlerBean, handlerType, method);
            if (methodMetadata != null) {
                methods.add(methodMetadata);
            }
        }

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

    private CommandMethodMetadata introspectMethod(Object handlerBean, Class<?> handlerType, Method method) {
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
            aliases = CommandAliasSet.of(command.value(), command.aliases());
            description = command.description();
            usage = command.usage();
        } else if (defaultCommand != null) {
            kind = CommandMethodKind.DEFAULT;
            aliases = CommandAliasSet.of("", new String[0]);
        } else {
            kind = CommandMethodKind.UNKNOWN;
            aliases = CommandAliasSet.of("", new String[0]);
        }

        List<CommandParameterMetadata> parameters = new ArrayList<>();
        Parameter[] reflectedParameters = method.getParameters();
        for (int index = 0; index < reflectedParameters.length; index++) {
            parameters.add(introspectParameter(reflectedParameters[index], index));
        }

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
        List<String> ids = new ArrayList<>();
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                ids.add(part.trim());
            }
        }
        return ids;
    }
}

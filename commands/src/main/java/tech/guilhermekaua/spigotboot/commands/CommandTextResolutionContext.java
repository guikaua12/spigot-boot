package tech.guilhermekaua.spigotboot.commands;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.lang.reflect.Method;
import java.util.Objects;

public final class CommandTextResolutionContext {
    @Getter
    public enum TargetType {
        ROOT_PATH("root command path"),
        ROOT_DESCRIPTION("root command description"),
        ROOT_USAGE("root command usage"),
        COMMAND_PATH("command path"),
        COMMAND_DESCRIPTION("command description"),
        COMMAND_USAGE("command usage"),
        COMMAND_PERMISSION("command permission");

        private final String displayName;

        TargetType(String displayName) {
            this.displayName = displayName;
        }

    }

    private final @Nullable Context context;
    private final Class<?> handlerType;
    private final @Nullable Method method;
    private final TargetType targetType;
    private final String value;

    private CommandTextResolutionContext(@Nullable Context context,
                                         Class<?> handlerType,
                                         @Nullable Method method,
                                         TargetType targetType,
                                         String value) {
        this.context = context;
        this.handlerType = Objects.requireNonNull(handlerType, "handlerType cannot be null.");
        this.method = method;
        this.targetType = Objects.requireNonNull(targetType, "targetType cannot be null.");
        this.value = value == null ? "" : value;
    }

    public static CommandTextResolutionContext forRoot(@Nullable Context context,
                                                       Class<?> handlerType,
                                                       TargetType targetType,
                                                       String value) {
        return new CommandTextResolutionContext(context, handlerType, null, targetType, value);
    }

    public static CommandTextResolutionContext forMethod(@Nullable Context context,
                                                         Class<?> handlerType,
                                                         Method method,
                                                         TargetType targetType,
                                                         String value) {
        return new CommandTextResolutionContext(context, handlerType, Objects.requireNonNull(method, "method cannot be null."), targetType, value);
    }

    public @Nullable Context getContext() {
        return context;
    }

    public Class<?> getHandlerType() {
        return handlerType;
    }

    public @Nullable Method getMethod() {
        return method;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public String getValue() {
        return value;
    }

    public String describeLocation() {
        StringBuilder builder = new StringBuilder(targetType.getDisplayName())
                .append(" on ")
                .append(handlerType.getName());
        if (method != null) {
            builder.append('#').append(method.getName());
        }
        return builder.toString();
    }
}

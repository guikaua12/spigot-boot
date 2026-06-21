package tech.guilhermekaua.spigotboot.commands.resolve;

import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.*;

import static java.util.Optional.empty;

public class DefaultCommandArgumentResolverRegistry implements CommandArgumentResolverRegistry {
    private final List<CommandArgumentResolver<?>> resolvers = new ArrayList<>();
    private boolean needsSort;

    public DefaultCommandArgumentResolverRegistry(List<CommandArgumentResolver<?>> customResolvers,
                                                  List<CommandArgumentResolverRegistryCustomizer> customizers) {
        registerBuiltIns();
        for (CommandArgumentResolver<?> resolver : CommandSupport.sortBeans(customResolvers)) {
            register(resolver);
        }
        for (CommandArgumentResolverRegistryCustomizer customizer : CommandSupport.sortBeans(customizers)) {
            customizer.customize(this);
        }
    }

    @Override
    public void register(CommandArgumentResolver<?> resolver) {
        if (resolver == null) {
            return;
        }
        resolvers.add(resolver);
        needsSort = true;
    }

    @Override
    public Optional<CommandArgumentResolver<?>> resolve(CommandParameterMetadata parameter) {
        ensureSorted();
        for (CommandArgumentResolver<?> resolver : resolvers) {
            if (resolver.supports(parameter)) {
                return Optional.of(resolver);
            }
        }
        return empty();
    }

    @Override
    public Collection<CommandArgumentResolver<?>> all() {
        ensureSorted();
        return Collections.unmodifiableList(new ArrayList<>(resolvers));
    }

    private void ensureSorted() {
        if (needsSort) {
            List<CommandArgumentResolver<?>> sorted = CommandSupport.sortBeans(resolvers);
            resolvers.clear();
            resolvers.addAll(sorted);
            needsSort = false;
        }
    }

    private void registerBuiltIns() {
        register(new StringArgumentResolver());
        register(new BooleanArgumentResolver());
        register(new NumericArgumentResolver());
        register(new EnumArgumentResolver());
        register(new UuidArgumentResolver());
    }

    private abstract static class BuiltInResolver<T> implements CommandArgumentResolver<T>, Ordered {
        @Override
        public int getOrder() {
            return 1000;
        }
    }

    private static final class StringArgumentResolver extends BuiltInResolver<String> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return String.class.equals(parameter.getValueType());
        }

        @Override
        public String resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            return input;
        }
    }

    private static final class BooleanArgumentResolver extends BuiltInResolver<Boolean> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            Class<?> type = parameter.getValueType();
            return Boolean.class.equals(type) || boolean.class.equals(type);
        }

        @Override
        public Boolean resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            String normalized = input.toLowerCase(Locale.ROOT);
            if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
                return true;
            }
            if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
                return false;
            }
            throw CommandMessageException.of(CoreCommandMessages.BOOLEAN_INVALID).with("input", input);
        }

        @Override
        public Collection<CommandMessageKey> messageKeys() {
            return Collections.singletonList(CoreCommandMessages.BOOLEAN_INVALID);
        }

        @Override
        public CommandCompletionProvider defaultCompletionProvider() {
            return (context, parameter, input) -> Arrays.asList("true", "false");
        }
    }

    private static final class NumericArgumentResolver extends BuiltInResolver<Number> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            Class<?> type = parameter.getValueType();
            return byte.class.equals(type) || Byte.class.equals(type)
                    || short.class.equals(type) || Short.class.equals(type)
                    || int.class.equals(type) || Integer.class.equals(type)
                    || long.class.equals(type) || Long.class.equals(type)
                    || float.class.equals(type) || Float.class.equals(type)
                    || double.class.equals(type) || Double.class.equals(type);
        }

        @Override
        public Number resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            Class<?> type = parameter.getValueType();
            try {
                if (byte.class.equals(type) || Byte.class.equals(type)) {
                    return Byte.parseByte(input);
                }
                if (short.class.equals(type) || Short.class.equals(type)) {
                    return Short.parseShort(input);
                }
                if (int.class.equals(type) || Integer.class.equals(type)) {
                    return Integer.parseInt(input);
                }
                if (long.class.equals(type) || Long.class.equals(type)) {
                    return Long.parseLong(input);
                }
                if (float.class.equals(type) || Float.class.equals(type)) {
                    return Float.parseFloat(input);
                }
                if (double.class.equals(type) || Double.class.equals(type)) {
                    return Double.parseDouble(input);
                }
            } catch (NumberFormatException e) {
                throw CommandMessageException.of(CoreCommandMessages.NUMBER_INVALID).with("input", input);
            }
            throw new IllegalArgumentException("Unsupported numeric type: " + type.getName());
        }

        @Override
        public Collection<CommandMessageKey> messageKeys() {
            return Collections.singletonList(CoreCommandMessages.NUMBER_INVALID);
        }
    }

    private static final class EnumArgumentResolver extends BuiltInResolver<Enum<?>> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return parameter.getValueType().isEnum();
        }

        @SuppressWarnings({"rawtypes"})
        @Override
        public Enum<?> resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            List<String> validNames = new ArrayList<>();
            for (Object constant : parameter.getValueType().getEnumConstants()) {
                Enum value = (Enum) constant;
                if (value.name().equalsIgnoreCase(input)) {
                    return value;
                }
                validNames.add(value.name());
            }
            throw CommandMessageException.of(CoreCommandMessages.ENUM_INVALID)
                    .with("input", input)
                    .with("options", String.join(", ", validNames));
        }

        @Override
        public Collection<CommandMessageKey> messageKeys() {
            return Collections.singletonList(CoreCommandMessages.ENUM_INVALID);
        }

        @Override
        public CommandCompletionProvider defaultCompletionProvider() {
            return (context, parameter, input) -> {
                List<String> values = new ArrayList<>();
                for (Object constant : parameter.getValueType().getEnumConstants()) {
                    values.add(((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
                }
                return values;
            };
        }
    }

    private static final class UuidArgumentResolver extends BuiltInResolver<UUID> {
        @Override
        public boolean supports(CommandParameterMetadata parameter) {
            return UUID.class.equals(parameter.getValueType());
        }

        @Override
        public UUID resolve(CommandExecutionContext context, CommandParameterMetadata parameter, String input) {
            try {
                return UUID.fromString(input);
            } catch (IllegalArgumentException e) {
                throw CommandMessageException.of(CoreCommandMessages.UUID_INVALID).with("input", input);
            }
        }

        @Override
        public Collection<CommandMessageKey> messageKeys() {
            return Collections.singletonList(CoreCommandMessages.UUID_INVALID);
        }
    }
}

package me.approximations.spigotboot.placeholder.metadata;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.approximations.spigotboot.placeholder.annotations.Param;
import me.approximations.spigotboot.placeholder.converter.TypeConverterManager;
import me.approximations.spigotboot.placeholder.metadata.parser.PlaceholderParameterParser;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

@RequiredArgsConstructor
@Getter
@ToString
public class PlaceholderMetadata {
    private final Object handlerObject;
    private final Method handlerMethod;

    private final String placeholder;
    private final String description;
    private final boolean placeholderApi;

    public String getValue(Player player, @NotNull String params, TypeConverterManager typeConverterManager) {
        try {
            Object[] paramValues = resolveHandlerMethodParams(player, params, typeConverterManager);

            handlerMethod.setAccessible(true);
            return (String) handlerMethod.invoke(handlerObject, paramValues);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke placeholder method: " + handlerMethod.getName() + " for placeholder: " + placeholder, e);
        }
    }

    private Object @NotNull [] resolveHandlerMethodParams(Player player, @NotNull String params, TypeConverterManager typeConverterManager) {
        Map<String, String> placeholderParams = PlaceholderParameterParser.parse(this.placeholder, params);

        return Arrays.stream(handlerMethod.getParameters())
                .map(parameter -> {
                    if (parameter.getType().equals(Player.class)) {
                        return player;
                    }

                    String mappedParamName = parameter.isAnnotationPresent(Param.class) ?
                            parameter.getAnnotation(Param.class).value() :
                            parameter.getName();

                    String rawParamValue = placeholderParams.get(mappedParamName);
                    return typeConverterManager.convert(parameter.getType(), rawParamValue);
                }).toArray(Object[]::new);
    }
}

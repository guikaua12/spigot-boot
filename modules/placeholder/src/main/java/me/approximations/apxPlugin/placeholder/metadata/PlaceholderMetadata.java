package me.approximations.apxPlugin.placeholder.metadata;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

@RequiredArgsConstructor
@Getter
@ToString
public class PlaceholderMetadata {
    private final Object handlerObject;
    private final Method handlerMethod;

    private final String placeholder;
    private final String description;
    private final boolean placeholderApi;

    public String getValue(Player player, @NotNull String params) {
        try {
            handlerMethod.setAccessible(true);
            return (String) handlerMethod.invoke(handlerObject, player, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke placeholder method: " + handlerMethod.getName() + " for placeholder: " + placeholder, e);
        }
    }
}

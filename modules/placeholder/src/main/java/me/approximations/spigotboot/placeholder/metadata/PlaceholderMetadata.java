/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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

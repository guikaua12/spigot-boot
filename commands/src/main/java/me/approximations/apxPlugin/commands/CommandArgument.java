package me.approximations.apxPlugin.commands;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Parameter;

@RequiredArgsConstructor
@Getter
public class CommandArgument {
    private final String id;
    private final Parameter parameter;
    private final Class<?> type;
    private final String completionId;
}

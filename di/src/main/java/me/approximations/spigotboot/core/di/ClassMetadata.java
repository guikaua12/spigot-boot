package me.approximations.spigotboot.core.di;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Constructor;

@Getter
@RequiredArgsConstructor
public class ClassMetadata<T> {
    private final Class<T> clazz;
    private final Constructor<T> injectConstructor;
}

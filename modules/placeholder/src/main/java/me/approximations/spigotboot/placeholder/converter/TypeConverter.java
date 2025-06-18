package me.approximations.spigotboot.placeholder.converter;

import org.jetbrains.annotations.Nullable;

public interface TypeConverter<T> {
    @Nullable T convert(@Nullable String value) throws IllegalArgumentException;
}

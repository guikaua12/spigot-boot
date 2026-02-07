package tech.guilhermekaua.spigotboot.config.reference.key;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@EqualsAndHashCode
@Getter
public final class ResolutionTarget {
    private final ReferenceKey key;
    private final @Nullable String path;

    private ResolutionTarget(@NotNull ReferenceKey key, @Nullable String path) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.path = path;
    }

    public static @NotNull ResolutionTarget of(@NotNull ReferenceKey key, @Nullable String path) {
        return new ResolutionTarget(key, path);
    }
}
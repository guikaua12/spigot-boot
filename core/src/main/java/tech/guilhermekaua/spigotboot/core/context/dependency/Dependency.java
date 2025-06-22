package tech.guilhermekaua.spigotboot.core.context.dependency;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@AllArgsConstructor
public class Dependency {
    private final Class<?> type;
    private final String qualifierName;
    private final boolean isPrimary;
    @Nullable
    private Object instance;
    private final DependencyResolveResolver resolver;
}
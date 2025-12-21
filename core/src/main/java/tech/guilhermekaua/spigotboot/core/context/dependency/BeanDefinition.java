package tech.guilhermekaua.spigotboot.core.context.dependency;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
@AllArgsConstructor
@EqualsAndHashCode(of = {"type", "qualifierName"})
public class BeanDefinition {
    private final Class<?> requestedType;
    private final Class<?> type;
    private final String qualifierName;
    private final boolean isPrimary;
    @Nullable
    private final DependencyResolveResolver<?> resolver;
    @Nullable
    private final DependencyReloadCallback reloadCallback;

    public String identifier() {
        return (qualifierName == null || qualifierName.isEmpty()) ?
                type.getName() :
                type.getName() + "@" + qualifierName;
    }

    public boolean isReloadable() {
        return reloadCallback != null;
    }
}


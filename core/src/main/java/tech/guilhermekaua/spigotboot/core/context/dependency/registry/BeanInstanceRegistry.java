package tech.guilhermekaua.spigotboot.core.context.dependency.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;

import java.util.*;

public class BeanInstanceRegistry {
    private final Map<BeanDefinition, Object> instances = new HashMap<>();
    private final Map<Class<?>, List<Object>> instancesByType = new HashMap<>();

    public boolean contains(@NotNull BeanDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null.");
        return instances.containsKey(definition);
    }

    public @Nullable Object get(@NotNull BeanDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null.");
        return instances.get(definition);
    }

    public <T> @Nullable T get(@NotNull BeanDefinition definition, @NotNull Class<T> castTo) {
        Objects.requireNonNull(definition, "definition cannot be null.");
        Objects.requireNonNull(castTo, "castTo cannot be null.");

        Object instance = instances.get(definition);
        if (instance == null) {
            return null;
        }
        return castTo.cast(instance);
    }

    public void put(@NotNull BeanDefinition definition, @NotNull Object instance) {
        Objects.requireNonNull(definition, "definition cannot be null.");
        Objects.requireNonNull(instance, "instance cannot be null.");
        instances.put(definition, instance);

        instancesByType.computeIfAbsent(definition.getRequestedType(), (type) -> new ArrayList<>())
                .add(instance);
        instancesByType.computeIfAbsent(definition.getType(), (type) -> new ArrayList<>())
                .add(instance);
    }

    public @NotNull Map<BeanDefinition, Object> asMapView() {
        return Collections.unmodifiableMap(instances);
    }

    @SuppressWarnings("unchecked")
    public <T> @NotNull List<T> getInstancesByType(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null.");
        return (List<T>) Collections.unmodifiableList(instancesByType.getOrDefault(type, new ArrayList<>()));
    }

    public void clear() {
        instances.clear();
    }
}


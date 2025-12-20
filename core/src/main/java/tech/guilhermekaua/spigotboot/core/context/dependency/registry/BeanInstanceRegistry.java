package tech.guilhermekaua.spigotboot.core.context.dependency.registry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;

import java.util.*;

public class BeanInstanceRegistry {
    private final Map<BeanDefinition, Object> instances = new HashMap<>();

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
    }

    public @NotNull Map<BeanDefinition, Object> asMapView() {
        return Collections.unmodifiableMap(instances);
    }

    @SuppressWarnings("unchecked")
    public <T> @NotNull List<T> getInstancesByType(@NotNull Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null.");

        List<T> result = new ArrayList<>();
        for (Object instance : instances.values()) {
            if (type.isInstance(instance)) {
                result.add((T) instance);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void clear() {
        instances.clear();
    }
}


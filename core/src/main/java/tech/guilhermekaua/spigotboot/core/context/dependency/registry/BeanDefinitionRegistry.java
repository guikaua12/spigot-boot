package tech.guilhermekaua.spigotboot.core.context.dependency.registry;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;

import java.util.*;
import java.util.stream.Stream;

public class BeanDefinitionRegistry {
    private final Map<Class<?>, List<BeanDefinition>> definitionMap = new HashMap<>();

    public @NotNull List<BeanDefinition> getDefinitions(@NotNull Class<?> requestedType) {
        Objects.requireNonNull(requestedType, "requestedType cannot be null.");

        List<BeanDefinition> definitions = definitionMap.get(requestedType);
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(definitions);
    }

    public @NotNull BeanDefinition register(@NotNull Class<?> requestedType, @NotNull BeanDefinition definition) {
        Objects.requireNonNull(requestedType, "requestedType cannot be null.");
        Objects.requireNonNull(definition, "definition cannot be null.");

        List<BeanDefinition> definitions = definitionMap.computeIfAbsent(requestedType, k -> new ArrayList<>());

        boolean duplicateDefinition = definitions.stream()
                .anyMatch(existing ->
                        existing.getType().equals(definition.getType()) &&
                                ((definition.getQualifierName() != null && definition.getQualifierName().equals(existing.getQualifierName())) ||
                                        (definition.getQualifierName() == null && existing.getQualifierName() == null))
                );

        if (duplicateDefinition) {
            throw new IllegalStateException(
                    "BeanDefinition with qualifier '" + definition.getQualifierName() + "' already exists for class: " + requestedType
            );
        }

        definitions.add(definition);
        return definition;
    }

    public @NotNull Map<Class<?>, List<BeanDefinition>> asMapView() {
        Map<Class<?>, List<BeanDefinition>> copy = new HashMap<>();
        for (Map.Entry<Class<?>, List<BeanDefinition>> entry : definitionMap.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    public @NotNull Set<Class<?>> getRegisteredTypes() {
        return Collections.unmodifiableSet(definitionMap.keySet());
    }

    public @NotNull Stream<Map.Entry<Class<?>, BeanDefinition>> streamEntries() {
        return definitionMap.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(definition -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), definition)));
    }

    public void clear() {
        definitionMap.clear();
    }
}


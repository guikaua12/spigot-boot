package tech.guilhermekaua.spigotboot.core.exceptions;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CircularDependencyException extends CycleDetectedException {
    public CircularDependencyException(@NotNull List<Class<?>> cycle) {
        super(cycle, Class::getSimpleName, "Circular dependency detected: ");
    }
}
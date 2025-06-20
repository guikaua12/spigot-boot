package tech.guilhermekaua.spigotboot.core.context.di.exceptions;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Getter
public class CircularDependencyException extends RuntimeException {
    private final List<Class<?>> dependencyPath;
    private final Class<?> currentType;

    public CircularDependencyException(Class<?> currentType, LinkedList<Class<?>> resolutionPath, int startIndex) {
        super(buildCircularDependencyMessage(currentType, startIndex, resolutionPath));
        this.currentType = currentType;
        this.dependencyPath = new ArrayList<>(resolutionPath);
    }

    private static String buildCircularDependencyMessage(Class<?> currentType, int startIndex, LinkedList<Class<?>> resolutionPath) {
        StringBuilder message = new StringBuilder("Circular dependency detected: ");

        for (int i = startIndex; i < resolutionPath.size(); i++) {
            message.append(resolutionPath.get(i).getSimpleName()).append(" -> ");
        }

        // complete the cycle
        message.append(currentType.getSimpleName());

        return message.toString();
    }
}
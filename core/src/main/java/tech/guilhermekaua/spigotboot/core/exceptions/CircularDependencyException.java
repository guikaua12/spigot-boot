package tech.guilhermekaua.spigotboot.core.exceptions;

import lombok.Getter;

@Getter
public class CircularDependencyException extends RuntimeException {
    public CircularDependencyException(String message) {
        super(message);
    }
}
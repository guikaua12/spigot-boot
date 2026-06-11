package tech.guilhermekaua.spigotboot.core.exceptions;

public class ModuleInitializationException extends RuntimeException {
    public ModuleInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

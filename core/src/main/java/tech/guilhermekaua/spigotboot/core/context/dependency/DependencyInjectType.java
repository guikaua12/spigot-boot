package tech.guilhermekaua.spigotboot.core.context.dependency;

public enum DependencyInjectType {
    CONSTRUCTOR,
    METHOD,
    FIELD,
    UNKNOWN;

    public boolean isConstructor() {
        return this == CONSTRUCTOR;
    }

    public boolean isMethod() {
        return this == METHOD;
    }

    public boolean isField() {
        return this == FIELD;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }
}

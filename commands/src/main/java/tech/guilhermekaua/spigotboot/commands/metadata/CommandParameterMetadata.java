package tech.guilhermekaua.spigotboot.commands.metadata;

import java.lang.reflect.Parameter;

public final class CommandParameterMetadata {
    private final Parameter parameter;
    private final int index;
    private final String parameterName;
    private final String lookupName;
    private final Class<?> rawType;
    private final Class<?> valueType;
    private final boolean optionalWrapper;
    private final boolean primitive;
    private final boolean senderMarked;
    private final String explicitCompletion;
    private final String defaultValue;

    public CommandParameterMetadata(Parameter parameter,
                                    int index,
                                    String parameterName,
                                    String lookupName,
                                    Class<?> rawType,
                                    Class<?> valueType,
                                    boolean optionalWrapper,
                                    boolean primitive,
                                    boolean senderMarked,
                                    String explicitCompletion,
                                    String defaultValue) {
        this.parameter = parameter;
        this.index = index;
        this.parameterName = parameterName;
        this.lookupName = lookupName;
        this.rawType = rawType;
        this.valueType = valueType;
        this.optionalWrapper = optionalWrapper;
        this.primitive = primitive;
        this.senderMarked = senderMarked;
        this.explicitCompletion = explicitCompletion;
        this.defaultValue = defaultValue;
    }

    public Parameter getParameter() {
        return parameter;
    }

    public int getIndex() {
        return index;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getLookupName() {
        return lookupName;
    }

    public Class<?> getRawType() {
        return rawType;
    }

    public Class<?> getValueType() {
        return valueType;
    }

    public boolean isOptionalWrapper() {
        return optionalWrapper;
    }

    public boolean isPrimitive() {
        return primitive;
    }

    public boolean isSenderMarked() {
        return senderMarked;
    }

    public String getExplicitCompletion() {
        return explicitCompletion;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean hasDefaultValue() {
        return defaultValue != null && !defaultValue.trim().isEmpty();
    }
}

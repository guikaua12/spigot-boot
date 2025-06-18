package me.approximations.spigotboot.placeholder.converter;

import me.approximations.spigotboot.core.di.annotations.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TypeConverterManager {
    private final Map<Class<?>, TypeConverter<?>> converters = new HashMap<>();

    public TypeConverterManager() {
        registerConverter(String.class, DefaultTypeConverters.STRING);
        registerConverter(Integer.class, DefaultTypeConverters.INTEGER);
        registerConverter(Boolean.class, DefaultTypeConverters.BOOLEAN);
        registerConverter(Double.class, DefaultTypeConverters.DOUBLE);
        registerConverter(Long.class, DefaultTypeConverters.LONG);
        registerConverter(Float.class, DefaultTypeConverters.FLOAT);
        registerConverter(Short.class, DefaultTypeConverters.SHORT);
        registerConverter(Byte.class, DefaultTypeConverters.BYTE);
        registerConverter(Character.class, DefaultTypeConverters.CHARACTER);

        registerConverter(String[].class, DefaultTypeConverters.STRING_ARRAY);
        registerConverter(Integer[].class, DefaultTypeConverters.INTEGER_ARRAY);
        registerConverter(Boolean[].class, DefaultTypeConverters.BOOLEAN_ARRAY);
        registerConverter(Double[].class, DefaultTypeConverters.DOUBLE_ARRAY);
        registerConverter(Long[].class, DefaultTypeConverters.LONG_ARRAY);
        registerConverter(Float[].class, DefaultTypeConverters.FLOAT_ARRAY);
        registerConverter(Short[].class, DefaultTypeConverters.SHORT_ARRAY);
        registerConverter(Byte[].class, DefaultTypeConverters.BYTE_ARRAY);
        registerConverter(Character[].class, DefaultTypeConverters.CHARACTER_ARRAY);
    }

    @SuppressWarnings("unchecked")
    public <T> T convert(Class<T> targetType, String value) throws IllegalArgumentException {
        if (!converters.containsKey(targetType)) {
            throw new IllegalArgumentException("No converter registered for type: " + targetType.getName());
        }
        return (T) converters.get(targetType).convert(value);
    }

    public <T> void registerConverter(Class<T> type, TypeConverter<T> converter) {
        if (converters.containsKey(type)) {
            throw new IllegalArgumentException("Converter for type " + type.getName() + " is already registered.");
        }
        converters.put(type, converter);
    }
}

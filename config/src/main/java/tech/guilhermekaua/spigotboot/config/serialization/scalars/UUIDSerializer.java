package tech.guilhermekaua.spigotboot.config.serialization.scalars;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;

import java.util.UUID;

public class UUIDSerializer implements TypeSerializer<UUID> {

    @Override
    public UUID deserialize(@NotNull ConfigNode node, @NotNull Class<UUID> type) throws SerializationException {
        String value = node.get(String.class);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Invalid UUID format: " + value, e);
        }
    }

    @Override
    public void serialize(@NotNull UUID value, @NotNull MutableConfigNode node) throws SerializationException {
        node.set(value.toString());
    }

    @Override
    public boolean canHandle(@NotNull Class<?> type) {
        return UUID.class.equals(type);
    }
}
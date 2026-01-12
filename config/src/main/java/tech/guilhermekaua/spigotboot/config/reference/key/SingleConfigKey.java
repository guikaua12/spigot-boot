package tech.guilhermekaua.spigotboot.config.reference.key;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.annotation.Config;

import java.util.Objects;

/**
 * Key for a single {@link Config} configuration.
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public final class SingleConfigKey extends ReferenceKey {
    private final String configName;

    public SingleConfigKey(@NotNull String configName) {
        this.configName = Objects.requireNonNull(configName, "configName cannot be null");
    }

    /**
     * Gets the config name.
     *
     * @return the config name
     */
    public @NotNull String getConfigName() {
        return configName;
    }

    @Override
    public boolean isSingleConfig() {
        return true;
    }

    @Override
    public boolean isCollectionItem() {
        return false;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "config:" + configName;
    }
}
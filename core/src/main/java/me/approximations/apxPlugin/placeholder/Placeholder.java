package me.approximations.apxPlugin.placeholder;

import lombok.Data;
import me.approximations.apxPlugin.placeholder.annotations.PlaceholderValue;

@Data
@PlaceholderValue
public abstract class Placeholder {
    private final String placeholder;
}

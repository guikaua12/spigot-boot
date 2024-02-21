package me.approximations.apxPlugin.placeholder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
@Getter
@ToString(callSuper=true)
public abstract class Placeholder {
    protected final String placeholder;
    protected final char delimiter;

    public String getPlaceholderWithDelimiter() {
        return String.format("%s%s%s", delimiter, placeholder, delimiter);
    }

    public abstract String getValue(Player player);

    public abstract boolean shouldRegisterPAPI();
}

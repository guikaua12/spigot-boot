package me.approximations.apxPlugin.placeholder.manager;

import me.approximations.apxPlugin.placeholder.Placeholder;

import java.util.HashMap;
import java.util.Map;

public class PlaceholderManager {
    private final Map<String, Placeholder> placeholders = new HashMap<>();

    public void register(Placeholder placeholder) {
        placeholders.put(placeholder.getPlaceholder(), placeholder);
    }

    public void unregister(Placeholder placeholder) {
        placeholders.remove(placeholder.getPlaceholder());
    }

    public Placeholder getPlaceholder(String placeholder) {
        return placeholders.get(placeholder);
    }

    public void clear() {
        placeholders.clear();
    }
}

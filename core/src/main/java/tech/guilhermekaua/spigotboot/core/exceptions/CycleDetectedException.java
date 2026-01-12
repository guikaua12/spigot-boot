package tech.guilhermekaua.spigotboot.core.exceptions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class CycleDetectedException extends RuntimeException {
    private final List<?> cycle;
    private final Function<Object, String> nodeLabeler;
    private final String initialPrefix;

    @SuppressWarnings("unchecked")
    public <T> CycleDetectedException(@NotNull List<T> cycle, @NotNull Function<T, String> nodeLabeler, @Nullable String initialPrefix) {
        super(formatCycleMessage(cycle, (Function<Object, String>) nodeLabeler, initialPrefix));
        this.cycle = Collections.unmodifiableList(new ArrayList<>(cycle));
        this.nodeLabeler = (Function<Object, String>) nodeLabeler;
        this.initialPrefix = initialPrefix;
    }

    public <T> CycleDetectedException(@NotNull List<T> cycle, @NotNull Function<T, String> nodeLabeler) {
        this(cycle, nodeLabeler, "Circular reference detected: ");
    }

    public @NotNull List<?> getCycle() {
        return cycle;
    }

    public @NotNull String formatCycle() {
        return formatCycleMessage(cycle, nodeLabeler, initialPrefix);
    }

    private static String formatCycleMessage(List<?> cycle, Function<Object, String> nodeLabeler, String initialPrefix) {
        if (cycle.isEmpty()) {
            return "Empty cycle";
        }

        StringBuilder sb = new StringBuilder(initialPrefix);
        for (int i = 0; i < cycle.size(); i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append(nodeLabeler.apply(cycle.get(i)));
        }
        return sb.toString();
    }
}
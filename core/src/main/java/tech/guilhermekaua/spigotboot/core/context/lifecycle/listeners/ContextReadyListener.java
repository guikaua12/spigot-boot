package tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;

public interface ContextReadyListener {
    void onContextReady(@NotNull Context context);
}



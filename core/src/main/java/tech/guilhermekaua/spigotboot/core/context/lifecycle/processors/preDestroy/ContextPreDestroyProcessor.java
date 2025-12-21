package tech.guilhermekaua.spigotboot.core.context.lifecycle.processors.preDestroy;

import tech.guilhermekaua.spigotboot.core.context.Context;

public interface ContextPreDestroyProcessor {
    void onPreDestroy(Context context);
}

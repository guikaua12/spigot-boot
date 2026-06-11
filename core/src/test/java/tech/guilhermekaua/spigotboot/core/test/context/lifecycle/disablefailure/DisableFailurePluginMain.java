package tech.guilhermekaua.spigotboot.core.test.context.lifecycle.disablefailure;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.processors.preDestroy.ContextPreDestroyProcessor;
import tech.guilhermekaua.spigotboot.core.module.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DisableFailurePluginMain {
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private DisableFailurePluginMain() {
    }

    public static void reset() {
        EVENTS.clear();
    }

    public static List<String> snapshot() {
        return new ArrayList<>(EVENTS);
    }

    static void record(String event) {
        EVENTS.add(event);
    }

    public static class DisableFailureModule implements Module {
        @Override
        public void onInitialize(Context context) {
            context.registerShutdownHook(() -> record("shutdownHook"));
        }
    }
}

@Component
class FailingOnDisableBean {
    @OnDisable
    void onDisable() {
        DisableFailurePluginMain.record("failingDisable");
        throw new IllegalStateException("disable failure");
    }
}

@Component
class HealthyOnDisableBean {
    @OnDisable
    void onDisable() {
        DisableFailurePluginMain.record("healthyDisable");
    }
}

@Component
class DisableFailurePreDestroyProcessor implements ContextPreDestroyProcessor {
    @Override
    public void onPreDestroy(Context context) {
        DisableFailurePluginMain.record("preDestroy");
    }
}

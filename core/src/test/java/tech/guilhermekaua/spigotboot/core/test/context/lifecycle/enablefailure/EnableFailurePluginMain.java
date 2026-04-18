package tech.guilhermekaua.spigotboot.core.test.context.lifecycle.enablefailure;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnEnable;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EnableFailurePluginMain {
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private EnableFailurePluginMain() {
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
}

@Component
class FailingOnEnableBean {
    @OnEnable
    void onEnable() {
        throw new IllegalStateException("enable failure");
    }
}

@Component
class EnableFailureReadyListener implements ContextReadyListener {
    @Override
    public void onContextReady(Context context) {
        EnableFailurePluginMain.record("contextReady");
    }
}

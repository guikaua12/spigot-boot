package tech.guilhermekaua.spigotboot.core.test.context.lifecycle.happy;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnEnable;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.processors.preDestroy.ContextPreDestroyProcessor;
import tech.guilhermekaua.spigotboot.core.module.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HappyPluginMain {
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    private static volatile MultiRegisteredLifecycleBean sharedBean;

    private HappyPluginMain() {
    }

    public static void reset() {
        EVENTS.clear();
        sharedBean = null;
    }

    public static List<String> snapshot() {
        return new ArrayList<>(EVENTS);
    }

    public static int sharedEnableCalls() {
        return sharedBean == null ? 0 : sharedBean.enableCalls;
    }

    public static int sharedDisableCalls() {
        return sharedBean == null ? 0 : sharedBean.disableCalls;
    }

    static void record(String event) {
        EVENTS.add(event);
    }

    public static class HappyLifecycleModule implements Module {
        @Override
        public void onInitialize(Context context) {
            context.registerBean(new ModuleRegisteredLifecycleBean());

            MultiRegisteredLifecycleBean bean = new MultiRegisteredLifecycleBean();
            sharedBean = bean;
            context.getDependencyManager().registerDependency(MultiRegisteredMarker.class, bean, "sharedMarker", false);
            context.getDependencyManager().registerDependency(MultiRegisteredLifecycleBean.class, bean, "sharedImpl", false);

            context.registerShutdownHook(() -> record("shutdownHook"));
        }
    }
}

@Configuration
class LifecycleConfiguration {
    @Bean
    String lifecycleMessage() {
        return "lifecycle-message";
    }

    @Bean
    Integer lifecycleNumber() {
        return 7;
    }

    @Bean
    LifecycleProduct lifecycleProduct(String lifecycleMessage) {
        return new LifecycleProduct(lifecycleMessage);
    }

    @OnEnable
    void onEnable(Integer lifecycleNumber) {
        HappyPluginMain.record("config:onEnable:" + lifecycleNumber);
    }

    @OnDisable
    void onDisable() {
        HappyPluginMain.record("config:onDisable");
    }
}

class LifecycleProduct {
    private final String message;

    LifecycleProduct(String message) {
        this.message = message;
    }

    @OnEnable
    void onEnable(Integer lifecycleNumber) {
        HappyPluginMain.record("product:onEnable:" + message + ":" + lifecycleNumber);
    }

    @OnDisable
    void onDisable() {
        HappyPluginMain.record("product:onDisable");
    }
}

@Component
class ScannedLifecycleComponent {
    @OnEnable
    void onEnable(String lifecycleMessage) {
        HappyPluginMain.record("scanned:onEnable:" + lifecycleMessage);
    }

    @OnDisable
    void onDisable() {
        HappyPluginMain.record("scanned:onDisable");
    }
}

@Component
class OrderedByInterfaceBean implements Ordered {
    @Override
    public int getOrder() {
        return -10;
    }

    @OnEnable
    void onEnable() {
        HappyPluginMain.record("order:interface");
    }
}

@Component
class DefaultOrderBean {
    @OnEnable
    void onEnable() {
        HappyPluginMain.record("order:default");
    }
}

@Component
@Order(5)
class OrderedByAnnotationBean {
    @OnEnable
    void onEnable() {
        HappyPluginMain.record("order:annotation");
    }
}

class ModuleRegisteredLifecycleBean {
    @OnEnable
    void onEnable() {
        HappyPluginMain.record("module:onEnable");
    }

    @OnDisable
    void onDisable() {
        HappyPluginMain.record("module:onDisable");
    }
}

interface MultiRegisteredMarker {
}

class MultiRegisteredLifecycleBean implements MultiRegisteredMarker {
    int enableCalls;
    int disableCalls;

    @OnEnable
    void onEnable() {
        enableCalls++;
    }

    @OnDisable
    void onDisable() {
        disableCalls++;
    }
}

@Component
class TrackingContextReadyListener implements ContextReadyListener {
    @Override
    public void onContextReady(Context context) {
        HappyPluginMain.record("contextReady");
    }
}

@Component
class TrackingPreDestroyProcessor implements ContextPreDestroyProcessor {
    @Override
    public void onPreDestroy(Context context) {
        HappyPluginMain.record("preDestroy");
    }
}

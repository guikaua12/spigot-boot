package me.approximations.apxPlugin.core.context.component.proxy;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.processor.MethodHandlerProcessResult;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;

@RequiredArgsConstructor
public class ComponentProxy implements MethodHandler {
    private final Plugin plugin;

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> clazz, Class<?>[] ctorArgs, Object[] ctorValues, Plugin plugin) {
        ProxyFactory factory = new ProxyFactory();
        if (clazz.isInterface()) {
            factory.setInterfaces(new Class<?>[]{clazz});
        } else {
            factory.setSuperclass(clazz);
        }

        try {
            return (T) factory.create(ctorArgs, ctorValues, new ComponentProxy(plugin));
        } catch (Throwable e) {
            throw new RuntimeException("Proxy creation failed for " + clazz.getName(), e);
        }
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (thisMethod.getName().equals("toString")) {
            return self.getClass().getSimpleName() + "@" + Integer.toHexString(self.hashCode());
        }

        List<MethodHandlerProcessResult> handlersResult = MethodHandlerRegistry.getHandlersFor(self.getClass());
        for (MethodHandlerProcessResult r : handlersResult) {
            @SuppressWarnings("unchecked")
            me.approximations.apxPlugin.core.context.component.proxy.methodHandler.MethodHandler<Object> handler =
                    (me.approximations.apxPlugin.core.context.component.proxy.methodHandler.MethodHandler<Object>) r.getHandlerInstance();

            if (handler.canHandle(self, thisMethod, proceed, args)) {
                try {
                    return handler.handle(self, thisMethod, proceed, args);
                } catch (Throwable t) {
                    throw new RuntimeException("Error handling method " + thisMethod.getName() + " in " + self.getClass().getName(), t);
                }
            }
        }

        return proceed.invoke(self, args);
    }
}
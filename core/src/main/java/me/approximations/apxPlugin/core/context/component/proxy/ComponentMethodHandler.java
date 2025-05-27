package me.approximations.apxPlugin.core.context.component.proxy;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Method;

@RequiredArgsConstructor
public class ComponentMethodHandler implements MethodHandler {

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> clazz, Class<?>[] args, Object[] values) {
        final ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setSuperclass(clazz);
        proxyFactory.setHandler(new ComponentMethodHandler());

        try {
            return (T) proxyFactory.createClass().getConstructor(args).newInstance(values);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create proxy for class: " + clazz.getName(), e);
        }
    }

    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        return proceed.invoke(self, args);
    }
}

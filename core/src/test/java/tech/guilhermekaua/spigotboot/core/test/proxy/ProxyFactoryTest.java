package tech.guilhermekaua.spigotboot.core.test.proxy;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.proxy.MethodInterceptor;
import tech.guilhermekaua.spigotboot.core.proxy.ProxyFactory;
import tech.guilhermekaua.spigotboot.core.proxy.SpigotBootProxy;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyFactoryTest {

    public static class Greeter {
        public String greet(String name) {
            return "Hello, " + name;
        }

        public int add(int a, int b) {
            return a + b;
        }

        protected String protectedMethod() {
            return "protected";
        }
    }

    public static class WithConstructor {
        private final String prefix;

        public WithConstructor(String prefix) {
            this.prefix = prefix;
        }

        public String greet(String name) {
            return prefix + " " + name;
        }
    }

    @Test
    void proxyClassImplementsSpigotBootProxy() {
        Class<? extends Greeter> proxyClass = ProxyFactory.createProxyClass(Greeter.class);
        assertTrue(SpigotBootProxy.class.isAssignableFrom(proxyClass));
        assertTrue(Greeter.class.isAssignableFrom(proxyClass));
    }

    @Test
    void proxyDelegatesToSuperWhenNoHandler() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> proceed.invoke(self, args));

        assertEquals("Hello, World", proxy.greet("World"));
        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void handlerCanInterceptAndModifyReturn() {
        AtomicReference<String> capturedMethodName = new AtomicReference<>();

        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    capturedMethodName.set(thisMethod.getName());
                    if ("greet".equals(thisMethod.getName())) {
                        return "Intercepted: " + args[0];
                    }
                    return proceed.invoke(self, args);
                });

        assertEquals("Intercepted: World", proxy.greet("World"));
        assertEquals("greet", capturedMethodName.get());
        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void handlerCanBeSwapped() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> proceed.invoke(self, args));

        assertEquals("Hello, World", proxy.greet("World"));

        ((SpigotBootProxy) proxy).setHandler(
                (self, thisMethod, proceed, args) -> "always this");

        assertEquals("always this", proxy.greet("World"));
    }

    @Test
    void proxyWithConstructorArgs() {
        WithConstructor proxy = ProxyFactory.createProxy(
                WithConstructor.class,
                new Class<?>[]{String.class},
                new Object[]{"Hi"},
                (self, thisMethod, proceed, args) -> proceed.invoke(self, args));

        assertEquals("Hi World", proxy.greet("World"));
    }

    @Test
    void proxyClassIsCached() {
        Class<? extends Greeter> a = ProxyFactory.createProxyClass(Greeter.class);
        Class<? extends Greeter> b = ProxyFactory.createProxyClass(Greeter.class);
        assertSame(a, b);
    }

    @Test
    void allocateWithoutConstructorWorks() {
        Class<? extends WithConstructor> proxyClass = ProxyFactory.createProxyClass(WithConstructor.class);
        Object instance = ProxyFactory.allocateWithoutConstructor(proxyClass);
        assertNotNull(instance);
        assertInstanceOf(SpigotBootProxy.class, instance);
    }

    @Test
    void proceedCallsSuperMethod() {
        AtomicReference<Object> superResult = new AtomicReference<>();

        ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    Object result = proceed.invoke(self, args);
                    superResult.set(result);
                    return result;
                }).greet("Test");

        assertEquals("Hello, Test", superResult.get());
    }

    @Test
    void primitiveReturnTypesHandledCorrectly() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> proceed.invoke(self, args));

        assertEquals(42, proxy.add(40, 2));
        assertEquals(-1, proxy.add(0, -1));
    }
}

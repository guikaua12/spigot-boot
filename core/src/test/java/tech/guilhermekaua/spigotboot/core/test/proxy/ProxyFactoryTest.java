package tech.guilhermekaua.spigotboot.core.test.proxy;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.proxy.MethodInterceptor;
import tech.guilhermekaua.spigotboot.core.proxy.ProxyFactory;
import tech.guilhermekaua.spigotboot.core.proxy.SpigotBootProxy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
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

    public static class AllPrimitives {
        public void doNothing() {}
        public boolean boolMethod() { return true; }
        public byte byteMethod() { return 42; }
        public char charMethod() { return 'X'; }
        public short shortMethod() { return 100; }
        public int intMethod() { return 1; }
        public long longMethod() { return 999L; }
        public float floatMethod() { return 3.14f; }
        public double doubleMethod() { return 2.718; }
    }

    public static class TwoSlotParams {
        public long addLongs(long a, long b) { return a + b; }
        public double addDoubles(double a, double b) { return a + b; }
        public String mixedParams(int a, long b, String c, double d) {
            return a + ":" + b + ":" + c + ":" + d;
        }
    }

    public static class ManyParams {
        public int sum(int a, int b, int c, int d, int e) {
            return a + b + c + d + e;
        }
    }

    public static class TenSlotParams {
        public int sumInts(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
            return a + b + c + d + e + f + g + h + i + j;
        }

        public long sumLongs(long a, long b, long c, long d, long e) {
            return a + b + c + d + e;
        }
    }

    public interface CalcInterface {
        int twice(int x);
    }

    public interface WithDefaults {
        int twice(int x);

        default String label() { return "calc"; }

        default long scale(long value, int factor) { return value * factor; }
    }

    public interface SubWithDefaults extends WithDefaults {}

    public static class SyncMethod {
        public synchronized String locked() { return "locked"; }
    }

    public static abstract class GenericBase<T> {
        public abstract T process(T input);
    }

    public static class StringProcessor extends GenericBase<String> {
        @Override
        public String process(String input) { return "processed:" + input; }
    }

    public static abstract class AbstractService {
        public abstract String execute(String input);
        public String concrete() { return "concrete"; }
    }

    private static final MethodInterceptor PROCEED = (self, thisMethod, proceed, args) -> proceed.invoke(self, args);

    // ================================================================
    //  BASIC PROXY BEHAVIOR
    // ================================================================

    @Test
    void proxyClassImplementsSpigotBootProxy() {
        Class<? extends Greeter> proxyClass = ProxyFactory.createProxyClass(Greeter.class);
        assertTrue(SpigotBootProxy.class.isAssignableFrom(proxyClass));
        assertTrue(Greeter.class.isAssignableFrom(proxyClass));
    }

    @Test
    void proxyDelegatesToSuperWhenNoHandler() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null, PROCEED);
        assertEquals("Hello, World", proxy.greet("World"));
        assertEquals(7, proxy.add(3, 4));

        ((SpigotBootProxy) proxy).setHandler(null);
        assertEquals("Hello, Again", proxy.greet("Again"));
        assertEquals(11, proxy.add(5, 6));
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
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null, PROCEED);
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
                PROCEED);
        assertEquals("Hi World", proxy.greet("World"));
    }

    @Test
    void createProxyRejectsArgLengthMismatch() {
        assertThrows(IllegalArgumentException.class, () ->
                ProxyFactory.createProxy(Greeter.class,
                        new Class<?>[]{String.class},
                        new Object[0],
                        PROCEED));
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

    // ================================================================
    //  ALL PRIMITIVE RETURN TYPES
    // ================================================================

    @Test
    void voidReturnType() {
        List<String> calls = new ArrayList<>();
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    calls.add(thisMethod.getName());
                    return proceed.invoke(self, args);
                });
        proxy.doNothing();
        assertTrue(calls.contains("doNothing"));
    }

    @Test
    void booleanReturnType() {
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null, PROCEED);
        assertTrue(proxy.boolMethod());
    }

    @Test
    void byteReturnType() {
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null, PROCEED);
        assertEquals((byte) 42, proxy.byteMethod());
    }

    @Test
    void charReturnType() {
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null, PROCEED);
        assertEquals('X', proxy.charMethod());
    }

    @Test
    void shortReturnType() {
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null, PROCEED);
        assertEquals((short) 100, proxy.shortMethod());
    }

    @Test
    void longReturnType() {
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null, PROCEED);
        assertEquals(999L, proxy.longMethod());
    }

    @Test
    void floatReturnType() {
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null, PROCEED);
        assertEquals(3.14f, proxy.floatMethod(), 0.001f);
    }

    @Test
    void doubleReturnType() {
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null, PROCEED);
        assertEquals(2.718, proxy.doubleMethod(), 0.001);
    }

    // ================================================================
    //  TWO-SLOT PARAMETERS (long, double)
    // ================================================================

    @Test
    void longParameters() {
        TwoSlotParams proxy = ProxyFactory.createProxy(TwoSlotParams.class, null, null, PROCEED);
        assertEquals(30L, proxy.addLongs(10L, 20L));
    }

    @Test
    void doubleParameters() {
        TwoSlotParams proxy = ProxyFactory.createProxy(TwoSlotParams.class, null, null, PROCEED);
        assertEquals(3.0, proxy.addDoubles(1.0, 2.0), 0.001);
    }

    @Test
    void mixedPrimitiveAndObjectParams() {
        TwoSlotParams proxy = ProxyFactory.createProxy(TwoSlotParams.class, null, null, PROCEED);
        assertEquals("1:2:three:4.0", proxy.mixedParams(1, 2L, "three", 4.0));
    }

    // ================================================================
    //  METHODS WITH >3 PARAMS (slot >3 opcode path)
    // ================================================================

    @Test
    void manyParametersUsesGeneralLoadOpcodes() {
        ManyParams proxy = ProxyFactory.createProxy(ManyParams.class, null, null, PROCEED);
        assertEquals(15, proxy.sum(1, 2, 3, 4, 5));
    }

    // regression: maxStack was hardcoded to 10, so the super-call path (this + params)
    // overflowed the operand stack for methods with >= 10 parameter slots -> VerifyError
    @Test
    void tenIntParametersDoNotOverflowOperandStack() {
        TenSlotParams proxy = ProxyFactory.createProxy(TenSlotParams.class, null, null, PROCEED);
        assertEquals(55, proxy.sumInts(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

        ((SpigotBootProxy) proxy).setHandler(null);
        assertEquals(55, proxy.sumInts(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
    }

    @Test
    void fiveLongParametersDoNotOverflowOperandStack() {
        TenSlotParams proxy = ProxyFactory.createProxy(TenSlotParams.class, null, null, PROCEED);
        assertEquals(15L, proxy.sumLongs(1L, 2L, 3L, 4L, 5L));

        ((SpigotBootProxy) proxy).setHandler(null);
        assertEquals(15L, proxy.sumLongs(1L, 2L, 3L, 4L, 5L));
    }

    // ================================================================
    //  INTERFACE TARGETS
    // ================================================================

    @Test
    void interfaceTargetIsProxied() {
        CalcInterface proxy = ProxyFactory.createProxy(CalcInterface.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("twice".equals(thisMethod.getName())) return ((Integer) args[0]) * 2;
                    if ("toString".equals(thisMethod.getName())) return "calc-proxy";
                    return proceed.invoke(self, args);
                });
        assertEquals(4, proxy.twice(2));
        assertEquals("calc-proxy", proxy.toString());
        assertInstanceOf(SpigotBootProxy.class, proxy);
    }

    @Test
    void abstractInterfaceMethodProceedIsNull() {
        CalcInterface proxy = ProxyFactory.createProxy(CalcInterface.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("twice".equals(thisMethod.getName())) {
                        assertNull(proceed, "abstract interface methods have no proceed method");
                        return 7;
                    }
                    return proceed.invoke(self, args);
                });
        assertEquals(7, proxy.twice(1));
    }

    @Test
    void interfaceProxyObjectMethodsProceedToObject() {
        CalcInterface proxy = ProxyFactory.createProxy(CalcInterface.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("twice".equals(thisMethod.getName())) return 0;
                    return proceed.invoke(self, args);
                });
        assertTrue(proxy.toString().contains("SBProxy"),
                "toString should proceed to Object.toString, not return null");
        assertEquals(System.identityHashCode(proxy), proxy.hashCode(),
                "hashCode should proceed to Object identity hashCode");
        assertTrue(proxy.equals(proxy), "equals should proceed to Object.equals identity semantics");
        assertFalse(proxy.equals(new Object()));
    }

    // ================================================================
    //  INTERFACE DEFAULT METHODS
    // ================================================================

    @Test
    void defaultMethodProceedRunsDefaultBody() {
        WithDefaults proxy = ProxyFactory.createProxy(WithDefaults.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("twice".equals(thisMethod.getName())) return ((Integer) args[0]) * 2;
                    assertNotNull(proceed, "default methods must have a proceed method");
                    return proceed.invoke(self, args);
                });
        assertEquals("calc", proxy.label(), "proceed on a default method must run the default body");
        assertEquals(15L, proxy.scale(5L, 3), "default body with primitive params must work through proceed");
        assertEquals(4, proxy.twice(2));
    }

    @Test
    void inheritedDefaultMethodProceedRunsDefaultBody() {
        SubWithDefaults proxy = ProxyFactory.createProxy(SubWithDefaults.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("twice".equals(thisMethod.getName())) return 0;
                    return proceed.invoke(self, args);
                });
        assertEquals("calc", proxy.label(),
                "default method declared on a superinterface must run through proceed");
    }

    @Test
    void defaultMethodNullHandlerRunsDefaultBody() {
        WithDefaults proxy = ProxyFactory.createProxy(WithDefaults.class, null, null,
                (self, thisMethod, proceed, args) -> 0);
        ((SpigotBootProxy) proxy).setHandler(null);
        assertEquals("calc", proxy.label(),
                "with no handler installed, calling a default method must run its body");
        assertEquals(6L, proxy.scale(2L, 3));
        assertEquals(0, proxy.twice(3), "abstract method with null handler still returns the type default");
    }

    // ================================================================
    //  equals / hashCode / toString INTERCEPTION
    // ================================================================

    @Test
    void toStringIsIntercepted() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("toString".equals(thisMethod.getName())) return "proxy-toString";
                    return proceed.invoke(self, args);
                });
        assertEquals("proxy-toString", proxy.toString());
    }

    @Test
    void hashCodeIsIntercepted() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("hashCode".equals(thisMethod.getName())) return 42;
                    return proceed.invoke(self, args);
                });
        assertEquals(42, proxy.hashCode());
    }

    @Test
    void equalsIsIntercepted() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("equals".equals(thisMethod.getName())) return true;
                    return proceed.invoke(self, args);
                });
        assertTrue(proxy.equals("anything"));
    }

    // ================================================================
    //  SYNCHRONIZED MODIFIER PRESERVED
    // ================================================================

    @Test
    void synchronizedModifierPreserved() throws Exception {
        Class<? extends SyncMethod> proxyClass = ProxyFactory.createProxyClass(SyncMethod.class);
        Method locked = proxyClass.getMethod("locked");
        assertTrue(Modifier.isSynchronized(locked.getModifiers()),
                "proxy should preserve synchronized modifier");
    }

    // ================================================================
    //  BRIDGE METHOD FILTERING
    // ================================================================

    @Test
    void bridgeMethodsAreNotProxied() {
        List<String> intercepted = new ArrayList<>();
        StringProcessor proxy = ProxyFactory.createProxy(StringProcessor.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    intercepted.add(thisMethod.getName() + "(" + thisMethod.getParameterTypes()[0].getSimpleName() + ")");
                    return proceed.invoke(self, args);
                });

        assertEquals("processed:hello", proxy.process("hello"));
        assertEquals(1, intercepted.size(), "bridge method should not cause double interception");
        assertEquals("process(String)", intercepted.get(0));

        intercepted.clear();
        GenericBase<String> asBase = proxy;
        assertEquals("processed:world", asBase.process("world"));
        assertEquals(1, intercepted.size(), "bridge method should not cause double interception via erased type");
    }

    // ================================================================
    //  ERROR PATHS
    // ================================================================

    @Test
    void handlerExceptionPropagates() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    throw new IllegalStateException("boom");
                });

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> proxy.greet("x"));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void handlerReturningNullForPrimitiveThrowsNPE() {
        AllPrimitives proxy = ProxyFactory.createProxy(AllPrimitives.class, null, null,
                (self, thisMethod, proceed, args) -> null);

        assertThrows(NullPointerException.class, proxy::intMethod);
        assertThrows(NullPointerException.class, proxy::longMethod);
        assertThrows(NullPointerException.class, proxy::doubleMethod);
        assertThrows(NullPointerException.class, proxy::boolMethod);
    }

    @Test
    void proxyInstanceOf() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null, PROCEED);
        assertInstanceOf(Greeter.class, proxy);
        assertInstanceOf(SpigotBootProxy.class, proxy);
    }

    @Test
    void protectedMethodCanBeIntercepted() {
        Greeter proxy = ProxyFactory.createProxy(Greeter.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("protectedMethod".equals(thisMethod.getName())) return "intercepted-protected";
                    return proceed.invoke(self, args);
                });
        assertEquals("intercepted-protected", proxy.protectedMethod());
    }

    // ================================================================
    //  ABSTRACT CLASS METHOD PROXYING
    // ================================================================

    @Test
    void abstractMethodProceedIsNull() {
        AbstractService proxy = ProxyFactory.createProxy(AbstractService.class, null, null,
                (self, thisMethod, proceed, args) -> {
                    if ("execute".equals(thisMethod.getName())) {
                        assertNull(proceed, "abstract methods have no proceed method");
                        return "intercepted:" + args[0];
                    }
                    assertNotNull(proceed, "concrete methods must have a proceed method");
                    return proceed.invoke(self, args);
                });

        assertEquals("intercepted:test", proxy.execute("test"));
        assertEquals("concrete", proxy.concrete());
    }

    @Test
    void abstractMethodNullHandlerReturnsDefault() {
        AbstractService proxy = ProxyFactory.createProxy(AbstractService.class, null, null, PROCEED);
        ((SpigotBootProxy) proxy).setHandler(null);
        assertNull(proxy.execute("test"), "abstract method with null handler should return type default");
        assertEquals("concrete", proxy.concrete());
    }

    // ================================================================
    //  INPUT VALIDATION
    // ================================================================

    @Test
    void rejectsFinalClass() {
        assertThrows(IllegalArgumentException.class, () -> ProxyFactory.createProxyClass(String.class));
    }

    @Test
    void rejectsPrimitive() {
        assertThrows(IllegalArgumentException.class, () -> ProxyFactory.createProxyClass(int.class));
    }

    @Test
    void rejectsArray() {
        assertThrows(IllegalArgumentException.class, () -> ProxyFactory.createProxyClass(Object[].class));
    }
}

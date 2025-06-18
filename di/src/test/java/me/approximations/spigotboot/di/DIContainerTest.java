package me.approximations.spigotboot.di;

import lombok.Getter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field injection
 */
@Getter
class ServiceA {
    @Inject
    ServiceB serviceB;
}

/**
 * Class to be injected
 */
class ServiceB {
    public String hello() {
        return "hello";
    }
}

/**
 * Constructor injection
 */
@Getter
class ServiceC {
    private final ServiceB serviceB;

    public ServiceC(ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}

/**
 * Setter injection
 */
@Getter
class ServiceD {
    private ServiceB serviceB;

    @Inject
    public void setServiceB(ServiceB serviceB) {
        this.serviceB = serviceB;
    }

}

public class DIContainerTest {
    @Test
    void testFieldInjection() {
        DIContainer container = new DIContainer();
        ServiceA a = container.resolve(ServiceA.class);
        assertNotNull(a.getServiceB());
        assertEquals("hello", a.getServiceB().hello());
    }

    @Test
    void testConstructorInjection() {
        DIContainer container = new DIContainer();
        ServiceC c = container.resolve(ServiceC.class);
        assertNotNull(c.getServiceB());
        assertEquals("hello", c.getServiceB().hello());
    }

    @Test
    void testSetterInjection() {
        DIContainer container = new DIContainer();
        ServiceD d = container.resolve(ServiceD.class);
        assertNotNull(d.getServiceB());
        assertEquals("hello", d.getServiceB().hello());
    }

    @Test
    void testSingletonBehavior() {
        DIContainer container = new DIContainer();
        ServiceA a1 = container.resolve(ServiceA.class);
        ServiceA a2 = container.resolve(ServiceA.class);
        assertSame(a1, a2);
    }
}

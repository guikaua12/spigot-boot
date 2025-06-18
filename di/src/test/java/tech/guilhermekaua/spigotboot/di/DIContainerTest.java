/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.di;

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

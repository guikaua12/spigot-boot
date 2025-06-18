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
package me.approximations.spigotboot.core.di;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DIContainerTest {

    interface Service {
        String getValue();
    }

    static class ServiceImpl implements Service {
        @Override
        public String getValue() {
            return "service";
        }
    }

    static class ConstructorInjected {
        private final Service service;

        @Inject
        public ConstructorInjected(Service service) {
            this.service = service;
        }

        public Service getService() {
            return service;
        }
    }

    static class FieldInjected {
        @Inject
        Service service;
    }

    static class SetterInjected {
        private Service service;

        @Inject
        public void setService(Service service) {
            this.service = service;
        }

        public Service getService() {
            return service;
        }
    }

    @Test
    void testRegisterAndResolve() {
        DIContainer container = new DIContainer();
        container.register(Service.class, ServiceImpl.class);

        Service service = container.resolve(Service.class);
        assertNotNull(service);
        assertEquals("service", service.getValue());
    }

    @Test
    void testSingletonBehavior() {
        DIContainer container = new DIContainer();
        container.register(Service.class, ServiceImpl.class);

        Service s1 = container.resolve(Service.class);
        Service s2 = container.resolve(Service.class);

        assertSame(s1, s2);
    }

    @Test
    void testRegisterInstance() {
        DIContainer container = new DIContainer();
        ServiceImpl impl = new ServiceImpl();
        container.register(Service.class, impl);

        Service resolved = container.resolve(Service.class);
        assertSame(impl, resolved);
    }

    @Test
    void testConstructorInjection() {
        DIContainer container = new DIContainer();
        container.register(Service.class, ServiceImpl.class);
        container.register(ConstructorInjected.class, ConstructorInjected.class);

        ConstructorInjected obj = container.resolve(ConstructorInjected.class);
        assertNotNull(obj);
        assertNotNull(obj.getService());
        assertEquals("service", obj.getService().getValue());
    }

    @Test
    void testFieldInjection() {
        DIContainer container = new DIContainer();
        container.register(Service.class, ServiceImpl.class);
        FieldInjected obj = new FieldInjected();
        container.injectDependencies(obj);

        assertNotNull(obj.service);
        assertEquals("service", obj.service.getValue());
    }

    @Test
    void testSetterInjection() {
        DIContainer container = new DIContainer();
        container.register(Service.class, ServiceImpl.class);
        SetterInjected obj = new SetterInjected();
        container.injectDependencies(obj);

        assertNotNull(obj.getService());
        assertEquals("service", obj.getService().getValue());
    }

    @Test
    void testResolveUnregisteredTypeReturnsNull() {
        DIContainer container = new DIContainer();
        assertNull(container.resolve(Service.class));
    }
}

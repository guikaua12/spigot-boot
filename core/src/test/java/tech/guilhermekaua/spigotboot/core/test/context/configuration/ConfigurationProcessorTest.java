package tech.guilhermekaua.spigotboot.core.test.context.configuration;

import javassist.util.proxy.ProxyObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.*;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.decider.impl.MethodHandlerDrivenProxyDecider;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.configuration.processor.ConfigurationProcessor;
import tech.guilhermekaua.spigotboot.core.context.configuration.proxy.ConfigurationClassProxy;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationProcessorTest {
    private DependencyManager dependencyManager;
    private ConfigurationProcessor processor;

    private static final AtomicInteger testServiceCreationCount = new AtomicInteger(0);
    private static final AtomicInteger anotherServiceCreationCount = new AtomicInteger(0);
    private static final AtomicInteger interceptedMethodInvocationCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        MethodHandlerRegistry.clear();
        dependencyManager = new DependencyManager();
        processor = new ConfigurationProcessor();
        testServiceCreationCount.set(0);
        anotherServiceCreationCount.set(0);
        interceptedMethodInvocationCount.set(0);
    }

    @AfterEach
    void tearDown() {
        MethodHandlerRegistry.clear();
    }

    public interface TestService {
        String getValue();
    }

    public static class TestServiceImpl implements TestService {
        private final String value;

        public TestServiceImpl(String value) {
            this.value = value;
        }

        @Override
        public String getValue() {
            return value;
        }
    }

    public static class AnotherService {
        private final TestService testService;

        public AnotherService(TestService testService) {
            this.testService = testService;
        }

        public TestService getTestService() {
            return testService;
        }
    }

    @Configuration
    public static class TestConfigurationInternalCall {
        @Bean
        @Primary
        public TestService testService() {
            testServiceCreationCount.incrementAndGet();
            return new TestServiceImpl("test-value");
        }

        @Bean
        public AnotherService anotherService() {
            anotherServiceCreationCount.incrementAndGet();
            return new AnotherService(testService());
        }

        public String nonBeanMethod() {
            return "non-bean-result";
        }
    }

    @Configuration
    public static class TestConfigurationParameter {
        @Bean
        @Primary
        public TestService testService() {
            testServiceCreationCount.incrementAndGet();
            return new TestServiceImpl("test-value");
        }

        @Bean
        public AnotherService anotherService(TestService testService) {
            anotherServiceCreationCount.incrementAndGet();
            return new AnotherService(testService);
        }

        public String nonBeanMethod() {
            return "non-bean-result";
        }
    }

    // Configuration with qualified beans
    @Configuration
    public static class QualifiedConfiguration {
        @Bean
        @Qualifier("primary")
        @Primary
        public TestService primaryService() {
            return new TestServiceImpl("primary");
        }

        @Bean
        @Qualifier("secondary")
        public TestService secondaryService() {
            return new TestServiceImpl("secondary");
        }
    }

    public static class CtorDependency {
        private final String value;

        public CtorDependency(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class CtorBean {
        private final String value;

        public CtorBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Configuration
    public static class CtorInjectedConfiguration {
        private final CtorDependency dep;

        public CtorInjectedConfiguration(CtorDependency dep) {
            this.dep = dep;
        }

        @Bean
        public CtorBean ctorBean() {
            return new CtorBean(dep.getValue());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Intercept {
    }

    public static class BeanWithInjection {
        @Inject
        private TestService testService;

        public TestService getTestService() {
            return testService;
        }
    }

    @Configuration
    public static class BeanWithInjectionConfiguration {
        @Bean
        @Primary
        public TestService testService() {
            return new TestServiceImpl("injected-test-service");
        }

        @Bean
        public BeanWithInjection beanWithInjection() {
            return new BeanWithInjection();
        }
    }

    public static class InterceptedBean {
        @Intercept
        public String run() {
            return "intercepted-bean";
        }
    }

    @Configuration
    public static class InterceptedBeanConfiguration {
        @Bean
        public InterceptedBean interceptedBean() {
            return new InterceptedBean();
        }
    }

    public static class InterceptedDependency {
        @Intercept
        public String run() {
            return "intercepted-dependency";
        }
    }

    public static class InterceptedDependencyHolder {
        private final InterceptedDependency dependency;

        public InterceptedDependencyHolder(InterceptedDependency dependency) {
            this.dependency = dependency;
        }

        public InterceptedDependency getDependency() {
            return dependency;
        }
    }

    @Configuration
    public static class InternalBeanCallConfiguration {
        @Bean
        public InterceptedDependencyHolder holder() {
            return new InterceptedDependencyHolder(interceptedDependency());
        }

        @Bean
        public InterceptedDependency interceptedDependency() {
            return new InterceptedDependency();
        }
    }

    private void enableMethodHandlerProxying() {
        dependencyManager.registerDependency(new MethodHandlerDrivenProxyDecider(), null, true);
        MethodHandlerRegistry.registerAll(Collections.singletonList(new RegisteredMethodHandler(
                context -> {
                    interceptedMethodInvocationCount.incrementAndGet();
                    return context.proceed().invoke(context.self(), context.args());
                },
                void.class,
                Annotation.class,
                Intercept.class
        )));
    }

    @Test
    void testBeanMethodRegisteredAsLazyDefinition() {
        processor.processClass(TestConfigurationInternalCall.class, dependencyManager);

        List<BeanDefinition> definitions = dependencyManager.getBeanDefinitionRegistry()
                .getDefinitions(TestService.class);
        assertFalse(definitions.isEmpty(), "Bean definition should be registered");

        assertEquals(0, testServiceCreationCount.get(), "Bean should not be instantiated during registration");
    }

    @Test
    void testBeanOnlyCreatedOnFirstAccess() {
        processor.processClass(TestConfigurationInternalCall.class, dependencyManager);

        assertEquals(0, testServiceCreationCount.get(), "Bean should not be created during processing");

        TestService service = dependencyManager.resolveDependency(TestService.class, null);

        assertEquals(1, testServiceCreationCount.get(), "Bean should be created on first access");
        assertNotNull(service, "Resolved service should not be null");
        assertEquals("test-value", service.getValue(), "Service should return correct value");
    }

    @Test
    void testBeanCachedAfterFirstAccess() {
        processor.processClass(TestConfigurationInternalCall.class, dependencyManager);

        TestService service1 = dependencyManager.resolveDependency(TestService.class, null);
        TestService service2 = dependencyManager.resolveDependency(TestService.class, null);

        assertEquals(1, testServiceCreationCount.get(), "Bean should only be created once");
        assertSame(service1, service2, "Both resolutions should return the same instance");
    }

    @Test
    void testConfigProxyRoutesBeanMethodsToDependencyManager() throws Exception {
        processor.processClass(TestConfigurationInternalCall.class, dependencyManager);

        TestConfigurationInternalCall configProxy = dependencyManager.resolveDependency(TestConfigurationInternalCall.class, null);
        assertNotNull(configProxy, "Config proxy should not be null");

        assertTrue(configProxy.getClass().getName().contains("$"), "Config should be a proxy class");

        TestService service1 = configProxy.testService();
        TestService service2 = configProxy.testService();

        assertSame(service1, service2, "Proxy bean method calls should return cached instances");

        assertEquals(1, testServiceCreationCount.get(),
                "Bean should only be instantiated once even when called via proxy");
    }

    @Test
    void testConfigProxyPassesNonBeanMethodsToRealObject() {
        processor.processClass(TestConfigurationInternalCall.class, dependencyManager);

        TestConfigurationInternalCall configProxy = dependencyManager.resolveDependency(TestConfigurationInternalCall.class, null);

        String result = configProxy.nonBeanMethod();

        assertEquals("non-bean-result", result, "Non-bean method should be delegated to real object");
    }

    @Test
    void testBeanMethodWithDependenciesCallingInternalBean() {
        processor.processClass(TestConfigurationInternalCall.class, dependencyManager);

        AnotherService anotherService = dependencyManager.resolveDependency(AnotherService.class, null);

        assertEquals(1, testServiceCreationCount.get(), "TestService should be created");
        assertEquals(1, anotherServiceCreationCount.get(), "AnotherService should be created");

        assertNotNull(anotherService, "AnotherService should not be null");
        assertNotNull(anotherService.getTestService(), "Injected TestService should not be null");
        assertEquals("test-value", anotherService.getTestService().getValue(),
                "Injected TestService should have correct value");
    }

    @Test
    void testBeanMethodWithDependencies() {
        processor.processClass(TestConfigurationParameter.class, dependencyManager);

        AnotherService anotherService = dependencyManager.resolveDependency(AnotherService.class, null);

        assertEquals(1, testServiceCreationCount.get(), "TestService should be created");
        assertEquals(1, anotherServiceCreationCount.get(), "AnotherService should be created");

        assertNotNull(anotherService, "AnotherService should not be null");
        assertNotNull(anotherService.getTestService(), "Injected TestService should not be null");
        assertEquals("test-value", anotherService.getTestService().getValue(),
                "Injected TestService should have correct value");
    }

    @Test
    void testQualifiedBeans() {
        processor.processClass(QualifiedConfiguration.class, dependencyManager);

        TestService primary = dependencyManager.resolveDependency(TestService.class, "primary");
        TestService secondary = dependencyManager.resolveDependency(TestService.class, "secondary");

        assertNotNull(primary, "Primary service should not be null");
        assertNotNull(secondary, "Secondary service should not be null");
        assertEquals("primary", primary.getValue());
        assertEquals("secondary", secondary.getValue());
        assertNotSame(primary, secondary, "Different qualifiers should yield different instances");
    }

    @Test
    void testConfigurationClassProxyDirectCreation() throws Exception {
        Set<Method> beanMethods = new HashSet<>();
        beanMethods.add(TestConfigurationInternalCall.class.getMethod("testService"));

        dependencyManager.registerDependency(
                TestService.class,
                "testService",
                true,
                (type) -> null);

        TestConfigurationInternalCall proxy = ConfigurationClassProxy.createProxy(
                TestConfigurationInternalCall.class,
                beanMethods,
                dependencyManager);

        TestService service1 = proxy.testService();
        TestService service2 = proxy.testService();

        assertSame(service1, service2, "Proxy should return cached bean instances");
    }

    @Test
    void testConfigurationProxyConstructorInjection() {
        dependencyManager.registerDependency(new CtorDependency("ctor-value"), null, false);

        processor.processClass(CtorInjectedConfiguration.class, dependencyManager);

        CtorBean bean = dependencyManager.resolveDependency(CtorBean.class, null);
        assertNotNull(bean, "CtorBean should not be null");
        assertEquals("ctor-value", bean.getValue(), "CtorBean should be created using constructor-injected dependency");
    }

    @Test
    void testInterBeanMethodCallUsesSameInstanceCallingInternalBean() {
        processor.processClass(TestConfigurationInternalCall.class, dependencyManager);

        AnotherService anotherService = dependencyManager.resolveDependency(AnotherService.class, null);

        TestService directService = dependencyManager.resolveDependency(TestService.class, null);

        assertSame(anotherService.getTestService(), directService,
                "Inter-bean method call should return the same singleton instance as direct resolution");

        assertEquals(1, testServiceCreationCount.get(),
                "TestService should only be instantiated once even when called from another @Bean method");
    }

    @Test
    void testInterBeanMethodCallUsesSameInstance() {
        processor.processClass(TestConfigurationParameter.class, dependencyManager);

        AnotherService anotherService = dependencyManager.resolveDependency(AnotherService.class, null);

        TestService directService = dependencyManager.resolveDependency(TestService.class, null);

        assertSame(anotherService.getTestService(), directService,
                "Inter-bean method call should return the same singleton instance as direct resolution");

        assertEquals(1, testServiceCreationCount.get(),
                "TestService should only be instantiated once even when called from another @Bean method");
    }

    @Test
    void testBeanMethodResultReceivesFieldInjection() {
        processor.processClass(BeanWithInjectionConfiguration.class, dependencyManager);

        BeanWithInjection bean = dependencyManager.resolveDependency(BeanWithInjection.class, null);

        assertNotNull(bean, "BeanWithInjection should not be null");
        assertNotNull(bean.getTestService(), "@Inject field on @Bean result should be resolved");
        assertEquals("injected-test-service", bean.getTestService().getValue());
    }

    @Test
    void testBeanMethodResultRunsMethodHandlerInterception() {
        enableMethodHandlerProxying();
        processor.processClass(InterceptedBeanConfiguration.class, dependencyManager);

        InterceptedBean bean = dependencyManager.resolveDependency(InterceptedBean.class, null);
        assertNotNull(bean, "InterceptedBean should not be null");
        assertInstanceOf(ProxyObject.class, bean, "@Bean result should be proxied when a handler can apply");
        assertEquals("intercepted-bean", bean.run());
        assertEquals(1, interceptedMethodInvocationCount.get(), "Method handler should intercept @Bean method calls");
    }

    @Test
    void testInternalBeanMethodCallCachesAndReturnsProcessedInstance() {
        enableMethodHandlerProxying();
        processor.processClass(InternalBeanCallConfiguration.class, dependencyManager);

        InterceptedDependencyHolder holder = dependencyManager.resolveDependency(InterceptedDependencyHolder.class, null);
        assertNotNull(holder, "InterceptedDependencyHolder should not be null");
        assertTrue(holder.getDependency() instanceof ProxyObject,
                "Internal @Bean call should not leak raw instance before processing");
        assertEquals("intercepted-dependency", holder.getDependency().run());
        assertEquals(1, interceptedMethodInvocationCount.get(), "Method handler should intercept the internally wired bean");

        InterceptedDependency direct = dependencyManager.resolveDependency(InterceptedDependency.class, null);
        assertSame(holder.getDependency(), direct,
                "Internal @Bean wiring and direct resolution should return the same processed singleton");
    }
}

package tech.guilhermekaua.spigotboot.core.test.context.condition;

import tech.guilhermekaua.spigotboot.core.proxy.ProxyFactory;
import tech.guilhermekaua.spigotboot.core.proxy.SpigotBootProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnBean;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.OnBeanCondition;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OnBeanConditionTest {

    private BeanDefinitionRegistry registry;
    private ConditionContext context;
    private OnBeanCondition condition;

    @BeforeEach
    void setUp() {
        registry = new BeanDefinitionRegistry();
        context = new SimpleConditionContext(registry, null, getClass().getClassLoader());
        condition = new OnBeanCondition();
    }

    private void registerBean(Class<?> type, String qualifierName) {
        registry.register(type, new BeanDefinition(type, type, qualifierName, false, null, null));
    }

    private void registerBean(Class<?> requestedType, Class<?> implType, String qualifierName) {
        registry.register(requestedType, new BeanDefinition(requestedType, implType, qualifierName, false, null, null));
    }

    @Test
    void matches_noAnnotation_returnsTrue() {
        boolean result = condition.matches(context, UnannotatedClass.class);
        assertTrue(result, "Should return true when no @ConditionalOnBean annotation present");
    }

    @Test
    void matches_emptyValueAndName_returnsTrue() {
        boolean result = condition.matches(context, EmptyAnnotationClass.class);
        assertTrue(result, "Should return true when value() and name() are both empty");
    }

    @Test
    void matches_singleTypePresent_returnsTrue() {
        registerBean(ServiceA.class, null);
        boolean result = condition.matches(context, SingleTypeClass.class);
        assertTrue(result, "Should return true when required bean type is present");
    }

    @Test
    void matches_singleTypeAbsent_returnsFalse() {
        boolean result = condition.matches(context, SingleTypeClass.class);
        assertFalse(result, "Should return false when required bean type is absent");
    }

    @Test
    void matches_multipleTypesAllPresent_returnsTrue() {
        registerBean(ServiceA.class, null);
        registerBean(ServiceB.class, null);
        boolean result = condition.matches(context, MultipleTypesClass.class);
        assertTrue(result, "Should return true when all required bean types are present");
    }

    @Test
    void matches_multipleTypesOneAbsent_returnsFalse() {
        registerBean(ServiceA.class, null);
        boolean result = condition.matches(context, MultipleTypesClass.class);
        assertFalse(result, "Should return false when any required bean type is absent (AND logic)");
    }

    @Test
    void matches_assignableType_returnsTrue() {
        registerBean(MyServiceImpl.class, null);
        boolean result = condition.matches(context, InterfaceTypeClass.class);
        assertTrue(result, "Should return true when registry has implementation of required interface");
    }

    @Test
    void matches_proxyClassInRegistry_unwrapsAndMatches() throws Exception {
        Class<?> proxyClass = ProxyFactory.createProxyClass(ServiceA.class);

        assertTrue(SpigotBootProxy.class.isAssignableFrom(proxyClass),
                "Test setup: proxy class should implement SpigotBootProxy");

        registry.register(proxyClass, new BeanDefinition(proxyClass, proxyClass, null, false, null, null));

        boolean result = condition.matches(context, SingleTypeClass.class);
        assertTrue(result, "Should unwrap proxy class and match against superclass (ServiceA)");
    }

    @Test
    void matches_namePresent_returnsTrue() {
        registerBean(ServiceA.class, "myService");
        boolean result = condition.matches(context, NameMatchClass.class);
        assertTrue(result, "Should return true when bean with matching qualifier name is present");
    }

    @Test
    void matches_nameAbsent_returnsFalse() {
        registerBean(ServiceA.class, "differentName");
        boolean result = condition.matches(context, NameMatchClass.class);
        assertFalse(result, "Should return false when no bean has matching qualifier name");
    }

    @Test
    void matches_bothSpecified_typePresentNameAbsent_returnsTrue() {
        registerBean(ServiceA.class, "differentName");
        boolean result = condition.matches(context, BothTypeAndNameClass.class);
        assertTrue(result, "Should return true when type matches even if name doesn't (OR logic)");
    }

    @Test
    void matches_bothSpecified_typeAbsentNamePresent_returnsTrue() {
        registerBean(ServiceB.class, "myService");
        boolean result = condition.matches(context, BothTypeAndNameClass.class);
        assertTrue(result, "Should return true when name matches even if type doesn't (OR logic)");
    }

    @Test
    void matches_bothSpecified_bothAbsent_returnsFalse() {
        boolean result = condition.matches(context, BothTypeAndNameClass.class);
        assertFalse(result, "Should return false when neither type nor name requirements are met");
    }

    static class UnannotatedClass {
    }

    @ConditionalOnBean
    static class EmptyAnnotationClass {
    }

    @ConditionalOnBean(ServiceA.class)
    static class SingleTypeClass {
    }

    @ConditionalOnBean({ServiceA.class, ServiceB.class})
    static class MultipleTypesClass {
    }

    @ConditionalOnBean(MyService.class)
    static class InterfaceTypeClass {
    }

    @ConditionalOnBean(name = "myService")
    static class NameMatchClass {
    }

    @ConditionalOnBean(value = ServiceA.class, name = "myService")
    static class BothTypeAndNameClass {
    }

    static class ServiceA {
    }

    static class ServiceB {
    }

    interface MyService {
    }

    static class MyServiceImpl implements MyService {
    }
}

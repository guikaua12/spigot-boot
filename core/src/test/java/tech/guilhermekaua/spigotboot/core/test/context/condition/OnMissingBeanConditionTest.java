package tech.guilhermekaua.spigotboot.core.test.context.condition;

import tech.guilhermekaua.spigotboot.core.proxy.ProxyFactory;
import tech.guilhermekaua.spigotboot.core.proxy.SpigotBootProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnMissingBean;
import tech.guilhermekaua.spigotboot.core.context.condition.ConditionContext;
import tech.guilhermekaua.spigotboot.core.context.condition.OnMissingBeanCondition;
import tech.guilhermekaua.spigotboot.core.context.condition.SimpleConditionContext;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanDefinitionRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OnMissingBeanConditionTest {

    private BeanDefinitionRegistry registry;
    private ConditionContext context;
    private OnMissingBeanCondition condition;

    @BeforeEach
    void setUp() {
        registry = new BeanDefinitionRegistry();
        context = new SimpleConditionContext(registry, null, getClass().getClassLoader());
        condition = new OnMissingBeanCondition();
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
        assertTrue(result, "Should return true when no @ConditionalOnMissingBean annotation present");
    }

    @Test
    void matches_emptyValueAndName_returnsTrue() {
        boolean result = condition.matches(context, EmptyAnnotationClass.class);
        assertTrue(result, "Should return true when value() and name() are both empty");
    }

    @Test
    void matches_singleTypeAbsent_returnsTrue() {
        boolean result = condition.matches(context, SingleTypeClass.class);
        assertTrue(result, "Should return true when required bean type is absent (missing = good)");
    }

    @Test
    void matches_singleTypePresent_returnsFalse() {
        registerBean(ServiceA.class, null);
        boolean result = condition.matches(context, SingleTypeClass.class);
        assertFalse(result, "Should return false when required bean type is present (not missing)");
    }

    @Test
    void matches_multipleTypesAllAbsent_returnsTrue() {
        boolean result = condition.matches(context, MultipleTypesClass.class);
        assertTrue(result, "Should return true when all specified bean types are absent");
    }

    @Test
    void matches_multipleTypesOnePresent_returnsFalse() {
        registerBean(ServiceA.class, null);
        boolean result = condition.matches(context, MultipleTypesClass.class);
        assertFalse(result, "Should return false when any specified bean type is present (AND: ALL must be absent)");
    }

    @Test
    void matches_assignableTypePresent_returnsFalse() {
        registerBean(MyServiceImpl.class, null);
        boolean result = condition.matches(context, InterfaceTypeClass.class);
        assertFalse(result, "Should return false when registry has implementation of checked interface");
    }

    @Test
    void matches_proxyClassInRegistry_unwrapsAndMatches_returnsFalse() throws Exception {
        Class<?> proxyClass = ProxyFactory.createProxyClass(ServiceA.class);

        assertTrue(SpigotBootProxy.class.isAssignableFrom(proxyClass),
                "Test setup: proxy class should implement SpigotBootProxy");

        registry.register(proxyClass, new BeanDefinition(proxyClass, proxyClass, null, false, null, null));

        boolean result = condition.matches(context, SingleTypeClass.class);
        assertFalse(result, "Should unwrap proxy and detect that ServiceA exists (not missing)");
    }

    @Test
    void matches_nameAbsent_returnsTrue() {
        registerBean(ServiceA.class, "differentName");
        boolean result = condition.matches(context, NameMatchClass.class);
        assertTrue(result, "Should return true when no bean has matching qualifier name (missing = good)");
    }

    @Test
    void matches_namePresent_returnsFalse() {
        registerBean(ServiceA.class, "myService");
        boolean result = condition.matches(context, NameMatchClass.class);
        assertFalse(result, "Should return false when bean with matching qualifier name exists");
    }

    @Test
    void matches_bothSpecified_typeAbsentNamePresent_returnsTrue() {
        registerBean(ServiceB.class, "myService");
        boolean result = condition.matches(context, BothTypeAndNameClass.class);
        assertTrue(result, "Should return true when types are absent even if names present (OR: type requirement passes)");
    }

    @Test
    void matches_bothSpecified_typePresentNameAbsent_returnsTrue() {
        registerBean(ServiceA.class, "differentName");
        boolean result = condition.matches(context, BothTypeAndNameClass.class);
        assertTrue(result, "Should return true when names are absent even if types present (OR: name requirement passes)");
    }

    @Test
    void matches_bothSpecified_bothPresent_returnsFalse() {
        registerBean(ServiceA.class, null);
        registerBean(ServiceB.class, "myService");
        boolean result = condition.matches(context, BothTypeAndNameClass.class);
        assertFalse(result, "Should return false when both type and name requirements fail (both present)");
    }

    static class UnannotatedClass {
    }

    @ConditionalOnMissingBean
    static class EmptyAnnotationClass {
    }

    @ConditionalOnMissingBean(ServiceA.class)
    static class SingleTypeClass {
    }

    @ConditionalOnMissingBean({ServiceA.class, ServiceB.class})
    static class MultipleTypesClass {
    }

    @ConditionalOnMissingBean(MyService.class)
    static class InterfaceTypeClass {
    }

    @ConditionalOnMissingBean(name = "myService")
    static class NameMatchClass {
    }

    @ConditionalOnMissingBean(value = ServiceA.class, name = "myService")
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

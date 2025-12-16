package tech.guilhermekaua.spigotboot.core.test.context.dependency.registry;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.registry.BeanInstanceRegistry;

import static org.junit.jupiter.api.Assertions.*;

class BeanInstanceRegistryTest {

    static class ServiceImpl {
    }

    @Test
    void shouldStoreAndRetrieveInstancesByDefinitionEquality() {
        BeanInstanceRegistry registry = new BeanInstanceRegistry();

        BeanDefinition defA = new BeanDefinition(ServiceImpl.class, ServiceImpl.class, null, false, null, null);
        ServiceImpl instance = new ServiceImpl();
        registry.put(defA, instance);

        assertTrue(registry.contains(defA));
        assertSame(instance, registry.get(defA));
        assertSame(instance, registry.get(defA, ServiceImpl.class));

        // equality is based on (type, qualifierName) only
        BeanDefinition equalKey = new BeanDefinition(ServiceImpl.class, ServiceImpl.class, null, true, null, null);
        assertTrue(registry.contains(equalKey));
        assertSame(instance, registry.get(equalKey));
    }

    @Test
    void clearShouldRemoveAllInstances() {
        BeanInstanceRegistry registry = new BeanInstanceRegistry();

        BeanDefinition definition = new BeanDefinition(ServiceImpl.class, ServiceImpl.class, null, false, null, null);
        registry.put(definition, new ServiceImpl());
        assertTrue(registry.contains(definition));

        registry.clear();
        assertFalse(registry.contains(definition));
        assertNull(registry.get(definition));
        assertTrue(registry.getInstancesByType(ServiceImpl.class).isEmpty(),
                "instancesByType should also be cleared");
    }
}



/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.v1_8_8.zombie;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityDefinition;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntitySpawnRequest;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.GeneratedNativeEntityClassFactory;
import tech.guilhermekaua.spigotboot.entity.runtime.nativebridge.ReflectionSupport;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spawns real 1.8.8 native zombie subclasses and binds them to the shared runtime lifecycle.
 *
 * @since 2.0.2
 */
public final class ZombieFactoryV1_8_8 {
    private static final String GENERATED_CLASS_NAME =
            "tech.guilhermekaua.spigotboot.entity.generated.v1_8_8.SpigotBootZombieV1_8_8";

    private final GeneratedNativeEntityClassFactory classFactory = new GeneratedNativeEntityClassFactory();

    private volatile Class<?> generatedZombieClass;

    /**
     * Spawns a real native custom zombie.
     *
     * @param definition the logical zombie definition
     * @param spawnRequest the spawn request
     * @param lifecycle the runtime lifecycle bridge
     * @return the live zombie handle
     */
    public @NotNull CustomEntityHandle<Zombie> spawn(
            @NotNull CustomEntityDefinition<?> definition,
            @NotNull CustomEntitySpawnRequest spawnRequest,
            @NotNull NativeEntityLifecycle<Zombie> lifecycle
    ) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(spawnRequest, "spawnRequest cannot be null");
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");

        Location location = spawnRequest.location();
        World bukkitWorld = Objects.requireNonNull(location.getWorld(), "location world cannot be null");

        Method getHandleMethod = ReflectionSupport.requireNamedMethod(
                bukkitWorld.getClass(),
                new String[]{"getHandle"}
        );
        Object worldHandle = ReflectionSupport.invoke(getHandleMethod, bukkitWorld);

        Class<?> zombieSuperclass = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.EntityZombie");
        Method tickMethod = ReflectionSupport.requireNamedMethod(zombieSuperclass, new String[]{"m"});
        Collection<Method> removalMethods = resolveRemovalMethods(zombieSuperclass);
        Class<?> zombieClass = generatedZombieClass(zombieSuperclass, tickMethod, removalMethods);
        Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(zombieClass, worldHandle.getClass());
        Object nmsZombie = ReflectionSupport.instantiate(constructor, worldHandle);

        classFactory.installInterceptor(nmsZombie, tickMethod, removalMethods);
        classFactory.bindLifecycle(nmsZombie, lifecycle);

        Method setLocationMethod = ReflectionSupport.requireMethodBySignature(
                zombieSuperclass,
                void.class,
                double.class,
                double.class,
                double.class,
                float.class,
                float.class
        );
        ReflectionSupport.invoke(
                setLocationMethod,
                nmsZombie,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );

        Method addEntityMethod = resolveAddEntityMethod(worldHandle.getClass(), zombieSuperclass);
        ReflectionSupport.invoke(addEntityMethod, worldHandle, nmsZombie);

        Zombie zombie = (Zombie) resolveBukkitEntity(nmsZombie);
        lifecycle.bind(zombie);
        lifecycle.onSpawn();
        return lifecycle.handle();
    }

    private synchronized @NotNull Class<?> generatedZombieClass(
            @NotNull Class<?> zombieSuperclass,
            @NotNull Method tickMethod,
            @NotNull Collection<Method> removalMethods
    ) {
        if (generatedZombieClass != null) {
            return generatedZombieClass;
        }

        generatedZombieClass = classFactory.createSubclass(
                zombieSuperclass,
                GENERATED_CLASS_NAME,
                tickMethod,
                removalMethods
        );
        ensureTrackerMappings(zombieSuperclass, generatedZombieClass);
        return generatedZombieClass;
    }

    private static @NotNull Collection<Method> resolveRemovalMethods(@NotNull Class<?> zombieSuperclass) {
        Collection<Method> removalMethods = new ArrayList<Method>();
        removalMethods.addAll(ReflectionSupport.collectNamedMethods(zombieSuperclass, new String[]{"die"}));

        Method damageDeathMethod = resolveDamageDeathMethod(zombieSuperclass);
        if (damageDeathMethod != null) {
            removalMethods.add(damageDeathMethod);
        }
        return removalMethods;
    }

    /**
     * Mirrors the base zombie id/name onto the generated subclass so 1.8.8 can serialize it
     * through {@code PacketPlayOutSpawnEntityLiving}.
     *
     * @param zombieSuperclass the vanilla zombie superclass
     * @param generatedType the generated runtime subclass
     */
    private static void ensureTrackerMappings(@NotNull Class<?> zombieSuperclass, @NotNull Class<?> generatedType) {
        Map<Class<?>, String> classToName = resolveEntityTypesMap("d");
        Map<Class<?>, Integer> classToId = resolveEntityTypesMap("f");

        String entityName = classToName.get(zombieSuperclass);
        Integer entityId = classToId.get(zombieSuperclass);
        if (entityName == null || entityId == null) {
            throw new IllegalStateException("Could not resolve the legacy zombie EntityTypes mapping for 1.8.8.");
        }

        classToName.put(generatedType, entityName);
        classToId.put(generatedType, entityId);
    }

    @SuppressWarnings("unchecked")
    private static <T> @NotNull Map<Class<?>, T> resolveEntityTypesMap(@NotNull String fieldName) {
        Class<?> entityTypesClass = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.EntityTypes");

        try {
            Field field = entityTypesClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof Map)) {
                throw new IllegalStateException(
                        "Expected EntityTypes." + fieldName + " to be a Map for Minecraft 1.8.8."
                );
            }
            return (Map<Class<?>, T>) value;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not access EntityTypes." + fieldName + " for Minecraft 1.8.8.",
                    exception
            );
        }
    }

    private static @NotNull Method resolveAddEntityMethod(@NotNull Class<?> worldType, @NotNull Class<?> entityType) {
        List<String> candidateNames = new ArrayList<String>();
        candidateNames.add("addEntity");
        candidateNames.add("d");

        Class<?> current = worldType;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!candidateNames.contains(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1) {
                    continue;
                }
                if (!parameterTypes[0].isAssignableFrom(entityType)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException("Could not resolve World#addEntity(Entity) for 1.8.8.");
    }

    private static Method resolveDamageDeathMethod(@NotNull Class<?> zombieSuperclass) {
        Class<?> damageSourceClass = ReflectionSupport.requireClass("net.minecraft.server.v1_8_R3.DamageSource");
        return ReflectionSupport.findNamedMethod(
                zombieSuperclass,
                new String[]{"die", "a"},
                damageSourceClass
        );
    }

    private static @NotNull Entity resolveBukkitEntity(@NotNull Object nmsEntity) {
        Method getBukkitEntityMethod = ReflectionSupport.requireNamedMethod(
                nmsEntity.getClass(),
                new String[]{"getBukkitEntity"}
        );
        return (Entity) ReflectionSupport.invoke(getBukkitEntityMethod, nmsEntity);
    }
}

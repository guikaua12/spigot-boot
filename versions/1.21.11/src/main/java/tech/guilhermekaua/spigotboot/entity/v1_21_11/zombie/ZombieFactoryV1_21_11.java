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
package tech.guilhermekaua.spigotboot.entity.v1_21_11.zombie;

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
import java.util.Objects;

/**
 * Spawns real 1.21.11 native zombie subclasses and binds them to the shared runtime lifecycle.
 *
 * @since 2.0.2
 */
public final class ZombieFactoryV1_21_11 {
    private static final String GENERATED_CLASS_NAME =
            "tech.guilhermekaua.spigotboot.entity.generated.v1_21_11.SpigotBootZombieV1_21_11";

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

        Class<?> zombieSuperclass = ReflectionSupport.requireClass(
                "net.minecraft.world.entity.monster.zombie.Zombie",
                "net.minecraft.world.entity.monster.Zombie",
                "net.minecraft.world.entity.monster.EntityZombie"
        );
        Method tickMethod = ReflectionSupport.requireNamedMethod(zombieSuperclass, new String[]{"aiStep", "tick"});
        Collection<Method> removalMethods = resolveRemovalMethods(zombieSuperclass);
        Class<?> zombieClass = generatedZombieClass(zombieSuperclass, tickMethod, removalMethods);
        Object nmsZombie = instantiateZombie(zombieClass, worldHandle);

        classFactory.installInterceptor(nmsZombie, tickMethod, removalMethods);
        classFactory.bindLifecycle(nmsZombie, lifecycle);

        Method moveToMethod = ReflectionSupport.requireMethodBySignature(
                zombieSuperclass,
                void.class,
                double.class,
                double.class,
                double.class,
                float.class,
                float.class
        );
        ReflectionSupport.invoke(
                moveToMethod,
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
        return generatedZombieClass;
    }

    private static @NotNull Collection<Method> resolveRemovalMethods(@NotNull Class<?> zombieSuperclass) {
        Class<?> removalReasonClass = ReflectionSupport.requireClass("net.minecraft.world.entity.Entity$RemovalReason");
        return ReflectionSupport.collectNamedMethods(
                zombieSuperclass,
                new String[]{"remove"},
                removalReasonClass
        );
    }

    private static @NotNull Object instantiateZombie(@NotNull Class<?> zombieType, @NotNull Object worldHandle) {
        try {
            Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(zombieType, worldHandle.getClass());
            return ReflectionSupport.instantiate(constructor, worldHandle);
        } catch (IllegalStateException ignored) {
            Class<?> entityTypeClass = ReflectionSupport.requireClass("net.minecraft.world.entity.EntityType");
            Constructor<?> constructor = ReflectionSupport.requireCompatibleConstructor(
                    zombieType,
                    entityTypeClass,
                    worldHandle.getClass()
            );
            return ReflectionSupport.instantiate(constructor, resolveZombieEntityType(entityTypeClass), worldHandle);
        }
    }

    private static @NotNull Object resolveZombieEntityType(@NotNull Class<?> entityTypeClass) {
        try {
            Field field = entityTypeClass.getField("ZOMBIE");
            return field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not resolve EntityType.ZOMBIE for 1.21.11.", exception);
        }
    }

    private static @NotNull Method resolveAddEntityMethod(@NotNull Class<?> worldType, @NotNull Class<?> entityType) {
        List<String> candidateNames = new ArrayList<String>();
        candidateNames.add("addFreshEntity");
        candidateNames.add("addEntity");

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
        throw new IllegalStateException("Could not resolve ServerLevel#addFreshEntity(Entity) for 1.21.11.");
    }

    private static @NotNull Entity resolveBukkitEntity(@NotNull Object nmsEntity) {
        Method getBukkitEntityMethod = ReflectionSupport.requireNamedMethod(
                nmsEntity.getClass(),
                new String[]{"getBukkitEntity"}
        );
        return (Entity) ReflectionSupport.invoke(getBukkitEntityMethod, nmsEntity);
    }
}

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
package tech.guilhermekaua.spigotboot.entity.runtime.nativebridge;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntityHandle;
import tech.guilhermekaua.spigotboot.entity.api.spi.NativeEntityLifecycle;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedNativeEntityClassFactoryTest {

    @Test
    void shouldInvokeLifecycleCallbacksAroundGeneratedSuperclassMethods() throws Exception {
        GeneratedNativeEntityClassFactory factory = new GeneratedNativeEntityClassFactory();
        Method tickMethod = StubNativeEntity.class.getDeclaredMethod("tick");
        Method removeMethod = StubNativeEntity.class.getDeclaredMethod("remove");

        Class<?> generatedType = factory.createSubclass(
                StubNativeEntity.class,
                "tech.guilhermekaua.spigotboot.entity.generated.test.StubNativeEntityProxy",
                tickMethod,
                Collections.singletonList(removeMethod)
        );

        Object generatedEntity = generatedType.getDeclaredConstructor().newInstance();
        factory.installInterceptor(generatedEntity, tickMethod, Collections.singletonList(removeMethod));

        RecordingLifecycle lifecycle = new RecordingLifecycle();
        factory.bindLifecycle(generatedEntity, lifecycle);

        StubNativeEntity nativeEntity = (StubNativeEntity) generatedEntity;
        nativeEntity.tick();
        nativeEntity.remove();

        assertEquals(1, nativeEntity.tickInvocations);
        assertEquals(1, nativeEntity.removeInvocations);
        assertEquals(1, lifecycle.tickInvocations);
        assertEquals(1, lifecycle.removeInvocations);
    }

    public static class StubNativeEntity {
        private int tickInvocations;
        private int removeInvocations;

        public void tick() {
            tickInvocations++;
        }

        public void remove() {
            removeInvocations++;
        }
    }

    private static final class RecordingLifecycle implements NativeEntityLifecycle<LivingEntity> {
        private int tickInvocations;
        private int removeInvocations;

        @Override
        public void bind(LivingEntity bukkitEntity) {
        }

        @Override
        public void onSpawn() {
        }

        @Override
        public void onNativeTick() {
            tickInvocations++;
        }

        @Override
        public void onNativeRemove() {
            removeInvocations++;
        }

        @Override
        public CustomEntityHandle<LivingEntity> handle() {
            throw new UnsupportedOperationException("The generated subclass test does not expose a public handle.");
        }
    }
}

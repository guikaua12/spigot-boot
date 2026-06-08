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
package tech.guilhermekaua.spigotboot.inventoryapi;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.annotations.OnDisable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryApiModuleDisableTest {

    @Test
    void onDisable_isAPublicVoidOnDisableHook() throws Exception {
        Method method = InventoryApiModule.class.getDeclaredMethod("onDisable");

        assertTrue(Modifier.isPublic(method.getModifiers()),
                "the context invokes disable hooks reflectively; the method must be public");
        assertEquals(void.class, method.getReturnType());
        assertNotNull(method.getAnnotation(OnDisable.class), "the hook must carry @OnDisable");
    }

    @Test
    void onDisable_runsWithoutBootstrapAndIsRepeatable() {
        InventoryApiModule module = new InventoryApiModule();

        assertDoesNotThrow(module::onDisable);
        assertDoesNotThrow(module::onDisable);
    }
}

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
package tech.guilhermekaua.spigotboot.config.binding;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;
import tech.guilhermekaua.spigotboot.core.validation.ValidationError;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class BindingResultTest {

    @Test
    void withValidationErrors_isNotSuccessful() {
        BindingResult<String> result = BindingResult.withValidationErrors(
                "value",
                Collections.singletonList(validationError("name", "must not be null"))
        );

        assertTrue(result.hasValidationErrors());
        assertFalse(result.hasErrors());
        assertFalse(result.isSuccess());
    }

    @Test
    void withValidationErrors_getThrowsConfigException() {
        BindingResult<String> result = BindingResult.withValidationErrors(
                "value",
                Collections.singletonList(validationError("name", "must not be null"))
        );

        assertThrows(ConfigException.class, result::get);
    }

    private static ValidationError validationError(String fieldName, String message) {
        return new ValidationError(PropertyPath.of(fieldName), fieldName, "invalid", message, false, null);
    }
}

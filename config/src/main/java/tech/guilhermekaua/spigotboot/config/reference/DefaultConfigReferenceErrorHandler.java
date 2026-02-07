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
package tech.guilhermekaua.spigotboot.config.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigCircularReferenceContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigReferenceNotFoundContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Default implementation of {@link ConfigReferenceErrorHandler}.
 * <p>
 * This handler:
 * <ul>
 *   <li>Logs warnings for missing references and type mismatches, returning null</li>
 *   <li>Throws {@link ConfigException} for circular references</li>
 * </ul>
 */
public class DefaultConfigReferenceErrorHandler implements ConfigReferenceErrorHandler {

    private final Logger logger;

    /**
     * Creates a new handler with the given logger.
     *
     * @param logger the logger to use for warnings
     */
    public DefaultConfigReferenceErrorHandler(@NotNull Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    @Override
    public @Nullable Object onReferenceNotFound(@NotNull ConfigReferenceNotFoundContext context) {
        StringBuilder message = new StringBuilder();
        message.append("Config reference not found: ").append(context.getFullReference());
        message.append(" in ").append(context.getSourceKey().getDisplayName());

        if (context.getSourceField() != null) {
            message.append(" (field: ").append(context.getSourceField().getName()).append(")");
        }

        if (!context.getAvailableAlternatives().isEmpty()) {
            message.append(". Available: ").append(context.getAvailableAlternatives());
        }

        logger.warning(message.toString());
        return null;
    }

    @Override
    public void onCircularReference(@NotNull ConfigCircularReferenceContext context) {
        StringBuilder message = new StringBuilder();
        message.append("Circular config reference detected: ").append(context.getFullReference());
        message.append(" in ").append(context.getSourceKey().getDisplayName());
        message.append(". Resolution chain: ").append(context.formatChain());

        throw new ConfigException(message.toString());
    }

    @Override
    public @Nullable Object onTypeMismatch(@NotNull ConfigTypeMismatchContext context) {
        StringBuilder message = new StringBuilder();
        message.append("Config reference type mismatch: ").append(context.getFullReference());
        message.append(" in ").append(context.getSourceKey().getDisplayName());

        if (context.getSourceField() != null) {
            message.append(" (field: ").append(context.getSourceField().getName()).append(")");
        }

        message.append(". Expected ").append(context.getExpectedType());
        message.append(", got ").append(context.getActualType().getName());

        logger.warning(message.toString());
        return null;
    }
}

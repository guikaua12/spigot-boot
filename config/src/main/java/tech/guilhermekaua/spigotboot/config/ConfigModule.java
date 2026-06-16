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
package tech.guilhermekaua.spigotboot.config;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.registry.ConfigRegistry;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.module.Module;

import java.util.logging.Logger;

/**
 * Spigot Boot module providing configuration management.
 * <p>
 * Scans for @Config and @FolderConfig annotated classes,
 * loads configs, and registers them as beans.
 * <p>
 * Runs early (after {@code SpigotCoreModule}, which registers the plugin at {@code @Order(-1000)},
 * but before default-order modules) so that {@code @Config} beans are registered before any later
 * module resolves a component that depends on them. Without this, a module such as data-jdbc could
 * be initialized first and instantiate a config-dependent component with a not-yet-registered
 * config, reintroducing the null-injection bug this ordering guards against.
 */
@Order(-500)
public class ConfigModule implements Module {

    @Override
    public void onInitialize(@NotNull Context context) throws Exception {
        Logger logger = context.getPlugin().getLogger();

        logger.info("Initializing Config Module...");

        context.getBean(ConfigRegistry.class)
                .registerConfigs(context);

        logger.info("Config Module initialized.");

        context.registerShutdownHook(() -> {
            logger.info("Config Module shutting down...");
        });
    }
}

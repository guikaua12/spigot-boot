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
package tech.guilhermekaua.spigotboot.inventoryapi.config;

import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.ConditionalOnMissingBean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.InventoryApiNMS;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.NoopPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PapiPlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.placeholder.PlaceholderApplier;
import tech.guilhermekaua.spigotboot.inventoryapi.title.TitleUpdater;

/**
 * Default beans for the inventory module. Each factory is guarded by
 * {@code @ConditionalOnMissingBean} so users can plug in their own implementation by simply
 * registering a bean of the same type in their plugin.
 */
@Configuration
public class InventoryApiAutoConfiguration {

    private static final String PAPI_CLASS = "me.clip.placeholderapi.PlaceholderAPI";

    @Bean
    @ConditionalOnMissingBean(TitleUpdater.class)
    public TitleUpdater titleUpdater() {
        return InventoryApiNMS.getTitleUpdater()::update;
    }

    @Bean
    @ConditionalOnMissingBean(PlaceholderApplier.class)
    public PlaceholderApplier placeholderApplier() {
        if (isClassPresent(PAPI_CLASS)) {
            return new PapiPlaceholderApplier();
        }
        return new NoopPlaceholderApplier();
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, InventoryApiAutoConfiguration.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }
}

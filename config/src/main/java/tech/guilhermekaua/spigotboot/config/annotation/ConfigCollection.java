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
package tech.guilhermekaua.spigotboot.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Watch mode for config collections.
 */
enum CollectionWatchMode {
    /**
     * Reload entire collection on any file change.
     */
    FULL,
    /**
     * Track individual file changes, reload only affected.
     */
    GRANULAR,
    /**
     * Support both modes, configurable at runtime.
     */
    BOTH
}

/**
 * Marks a class as a folder-based config collection.
 * Each file in the folder becomes an instance of this class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigCollection {

    /**
     * Folder path relative to plugin data folder.
     * Example: "boosters/"
     *
     * @return the folder path
     */
    String folder();

    /**
     * File pattern to match.
     *
     * @return the file pattern
     */
    String pattern() default "*.yml";

    /**
     * Field to inject the instance ID (filename without extension).
     * If empty, filename is used as-is.
     *
     * @return the ID field name
     */
    String idField() default "";

    /**
     * Field to use for ordering instances.
     * Use "filename" for alphabetical by filename.
     *
     * @return the order field name
     */
    String orderBy() default "";

    /**
     * Field that controls whether instance is enabled.
     *
     * @return the enabled field name
     */
    String enabledField() default "enabled";

    /**
     * Watch mode for reloading.
     *
     * @return the watch mode
     */
    CollectionWatchMode watchMode() default CollectionWatchMode.FULL;

    /**
     * Resource folder path (inside JAR) to copy defaults from.
     * Copied only if destination folder is empty or missing.
     * Example: "defaults/kits/"
     *
     * @return the resource folder path
     */
    String resource() default "";
}

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

import tech.guilhermekaua.spigotboot.config.binding.NamingStrategy;

import java.lang.annotation.*;

/**
 * Marks a class as a folder-based config collection.
 * Each file in the folder becomes an instance of this class.
 * <p>
 * This annotation is repeatable, allowing one item class to be used
 * for multiple collections from different folders.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(ConfigCollections.class)
public @interface ConfigCollection {

    /**
     * Collection name used for lookup and injection.
     * If empty, the name is derived from the folder path
     * (last segment after normalizing slashes).
     *
     * @return the collection name
     */
    String name() default "";

    /**
     * Folder path relative to plugin data folder.
     * Example: "boosters/"
     *
     * @return the folder path
     */
    String folder();

    /**
     * Field to inject the instance ID (filename without extension).
     * If empty, looks for a field annotated with @NodeKey.
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
    String orderBy() default "filename";

    /**
     * Field that controls whether instance is enabled.
     * Set to empty string to disable enabled filtering.
     * If the specified field does not exist on the class,
     * enabled filtering is automatically disabled.
     *
     * @return the enabled field name
     */
    String enabledField() default "";

    /**
     * Resource folder path (inside JAR) to copy defaults from.
     * Copied only if destination folder is empty or missing.
     * Example: "defaults/kits/"
     *
     * @return the resource folder path
     */
    String resource() default "";

    /**
     * Prefix for files to exclude from loading.
     * Files starting with this prefix will be ignored.
     *
     * @return the exclude prefix
     */
    String excludePrefix() default "_";

    /**
     * Naming strategy for field to YAML key conversion.
     *
     * @return the naming strategy
     */
    NamingStrategy naming() default NamingStrategy.SNAKE_CASE;
}

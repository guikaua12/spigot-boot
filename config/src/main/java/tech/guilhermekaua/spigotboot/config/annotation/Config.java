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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a configuration POJO that will be bound to a YAML file.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {

    /**
     * Path to the config file, relative to plugin data folder.
     * Examples: "config.yml", "configs/database.yml"
     *
     * @return the config file path
     */
    String value() default "";

    /**
     * Named config root for programmatic access.
     * Examples: "main", "messages", "database"
     *
     * @return the config name
     */
    String name() default "";

    /**
     * Naming strategy for field to YAML key conversion.
     *
     * @return the naming strategy
     */
    NamingStrategy naming() default NamingStrategy.SNAKE_CASE;

    /**
     * Whether to generate default config file with comments.
     *
     * @return true to generate defaults
     */
    boolean generateDefaults() default true;

    /**
     * Resource path for bundled defaults (inside JAR).
     *
     * @return the resource path
     */
    String resource() default "";

    /**
     * Active profile name (e.g., "dev", "prod").
     * If set, will also load config-{profile}.yml and merge.
     *
     * @return the profile name
     */
    String profile() default "";
}

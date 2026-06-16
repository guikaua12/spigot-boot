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
package tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the BungeeCord plugin descriptor on the plugin's main class (the class that extends
 * {@code net.md_5.bungee.api.plugin.Plugin}). {@code BungeePluginAnnotationProcessor} reads this
 * annotation at compile time and emits a {@code bungee.yml} into the jar root, mirroring how the
 * Spigot {@code @Plugin} annotation drives {@code plugin.yml}.
 *
 * <p>The annotated class itself becomes the descriptor's {@code main} entry. {@code name} and
 * {@code version} are required; every other element is optional and is omitted from the generated
 * descriptor when left at its blank/empty default.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface BungeePlugin {
    /**
     * The plugin name BungeeCord registers the plugin under.
     *
     * @return the plugin name (required)
     */
    String name();

    /**
     * The plugin version.
     *
     * @return the plugin version (required)
     */
    String version();

    /**
     * The single plugin author. BungeeCord's descriptor has no plural {@code authors} key, unlike
     * Spigot's.
     *
     * @return the author, or an empty string to omit the key
     */
    String author() default "";

    /**
     * Hard dependencies that must be present and load before this plugin. Emitted under the
     * BungeeCord {@code depends} key (camelCase, distinct from Spigot's {@code depend}).
     *
     * @return the hard-dependency plugin names
     */
    String[] depends() default {};

    /**
     * Soft dependencies loaded before this plugin when present. Emitted under the BungeeCord
     * {@code softDepends} key (camelCase, distinct from Spigot's {@code softdepend}).
     *
     * @return the soft-dependency plugin names
     */
    String[] softDepends() default {};

    /**
     * A human-readable description.
     *
     * @return the description, or an empty string to omit the key
     */
    String description() default "";

    /**
     * Runtime Maven library coordinates ({@code group:artifact:version}) that BungeeCord 1.21+
     * downloads via its library loader. Emitted under the {@code libraries} key.
     *
     * @return the library coordinates
     */
    String[] libraries() default {};
}
